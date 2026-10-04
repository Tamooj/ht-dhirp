package com.highcentrality.htdhirp.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CommLogTest {
    /** A transport whose replies take a scripted amount of fake time. */
    private class SlowRadio(private val clock: FakeClock, private val inner: FakeRadio, private val replyMs: () -> Long) :
        SerialTransport {
        override fun write(data: ByteArray) = inner.write(data)
        override fun read(count: Int, timeoutMs: Long): ByteArray {
            clock.advanceMs(replyMs())
            return inner.read(count, timeoutMs)
        }

        override fun discardInput() = inner.discardInput()
    }

    private class FakeClock {
        private var nanos = 0L
        fun advanceMs(ms: Long) {
            nanos += ms * 1_000_000
        }

        val source: () -> Long = { nanos }
    }

    @Test
    fun `records transmit and receive with hex, ascii and wait time`() {
        val clock = FakeClock()
        val log = CommLog(clock.source)
        val io = LoggingTransport(SlowRadio(clock, FakeRadio(), { 34 }), log)

        io.write("PROGRAMBFNORMALU".toByteArray())
        val ack = io.read(1, 1500)

        assertEquals(1, ack.size)
        val text = log.toText()
        assertTrue(text.contains("TX        16 B  50 52 4F 47"), text)
        assertTrue(text.contains("|PROGRAMBFNORMALU|"), text)
        assertTrue(text.contains("RX         1 B  06"), text)
        assertTrue(text.contains("(waited 34.0 ms)"), text)
    }

    @Test
    fun `a silent radio shows up as a timeout, a partial reply as short`() {
        val clock = FakeClock()
        val log = CommLog(clock.source)
        val silent = object : SerialTransport {
            override fun write(data: ByteArray) {}
            override fun read(count: Int, timeoutMs: Long): ByteArray {
                clock.advanceMs(timeoutMs)
                return ByteArray(0)
            }

            override fun discardInput() {}
        }
        LoggingTransport(silent, log).read(16, 1500)
        log.rx(68, ByteArray(12), 1_500_000_000)

        val text = log.toText()
        assertTrue(text.contains("TIMEOUT  wanted 16 B, got 0 B  (waited 1500.0 ms)"), text)
        assertTrue(text.contains("SHORT    wanted 68 B, got 12 B"), text)
    }

    @Test
    fun `payloads are truncated by default and complete when asked`() {
        val big = ByteArray(68) { it.toByte() }
        val short = CommLog(payloadLimit = 16).also { it.tx(big) }.toText()
        assertTrue(short.contains("68 B") && short.contains("... (+52 B)"), short)
        val full = CommLog(payloadLimit = 1000).also { it.tx(big) }.toText()
        assertTrue(!full.contains("(+"), full)
        assertTrue(full.contains("43"), full) // byte 0x43 present in the untruncated hex
    }

    @Test
    fun `a session logs its phases and the wire traffic`() {
        val log = CommLog()
        val session = CloneSession(LoggingTransport(FakeRadio(), log), log = log)
        session.handshake()
        session.readBlock(0x0000)

        val text = log.toText()
        for (expected in listOf("handshake start", "PROGRAMBFNORMALU", "handshake done, key index 1", "read block 0x0000", "DISCARD")) {
            assertTrue(text.contains(expected), "missing '$expected' in:\n$text")
        }
    }

    @Test
    fun `summary reports the slowest reply`() {
        val clock = FakeClock()
        val log = CommLog(clock.source)
        var delay = 10L
        val radio = FakeRadio()
        val session = CloneSession(LoggingTransport(SlowRadio(clock, radio, { delay }), log), log = log)
        session.handshake()
        delay = 450
        session.readBlock(0x0040)

        val summary = log.summary()
        assertTrue(summary.contains("max 450.0 ms"), summary)
        assertTrue(summary.contains("Slowest replies:"), summary)
        assertTrue(summary.contains("waited 450.0 ms"), summary)
    }

    @Test
    fun `transport errors are logged and rethrown`() {
        val log = CommLog()
        val broken = object : SerialTransport {
            override fun write(data: ByteArray) = throw java.io.IOException("cable pulled")
            override fun read(count: Int, timeoutMs: Long): ByteArray = ByteArray(0)
            override fun discardInput() {}
        }
        assertFailsWith<java.io.IOException> { LoggingTransport(broken, log).write(byteArrayOf(1)) }
        assertTrue(log.toText().contains("write failed: IOException: cable pulled"))
    }
}
