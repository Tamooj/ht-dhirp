package com.highcentrality.htdhirp.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CloneSessionTest {
    private fun randomImage(seed: Int) = Random(seed).nextBytes(Uv5rmMemory.IMAGE_SIZE)

    private fun connected(image: ByteArray): Pair<FakeRadio, CloneSession> {
        val radio = FakeRadio().also { it.load(image) }
        val session = CloneSession(radio)
        session.handshake()
        return radio to session
    }

    @Test
    fun `handshake selects key index 1 and returns device info`() {
        val radio = FakeRadio()
        val session = CloneSession(radio)
        val info = session.handshake()
        assertEquals(1, session.keyIndex)
        assertEquals(16, info.info.size)
        assertEquals(15, info.model.size)
    }

    @Test
    fun `readImage reproduces the radio's memory in CHIRP layout`() {
        val image = randomImage(1)
        val (_, session) = connected(image)
        assertContentEquals(image, session.readImage())
    }

    @Test
    fun `writeImage updates the radio and survives sector erase`() {
        val original = randomImage(2)
        val (radio, session) = connected(original)

        val edited = original.copyOf()
        val ri = RadioImage(edited)
        ri.setChannel(0, Channel(147_220_000L, 147_820_000L, txTone = Tone.Ctcss(1148), name = "TEST"))
        session.writeImage(edited)

        assertContentEquals(edited, radio.toImage())
    }

    @Test
    fun `non-channel memory comes back from the radio exactly as it was sent`() {
        val original = randomImage(4)
        val (radio, session) = connected(original)

        val fromRadio = session.readImage()
        RadioImage(fromRadio).setChannel(7, Channel(147_220_000L, 147_820_000L, name = "TEST"))
        session.writeImage(fromRadio)

        val after = radio.toImage()
        val slot = 7 * ChannelCodec.RECORD_SIZE
        for (i in original.indices) {
            if (i in slot until slot + ChannelCodec.RECORD_SIZE) continue
            assertEquals(original[i], after[i], "byte $i changed outside the edited channel")
        }
    }

    @Test
    fun `never reads or writes outside the permitted regions`() {
        val (radio, session) = connected(randomImage(3))
        session.readImage()
        session.writeImage(session.readImage())
        assertTrue((radio.reads + radio.writes).all { addr -> Uv5rmMemory.regions.any { addr >= it.radioAddr && addr < it.radioAddr + it.size } })
        assertFailsWith<ProtocolException> { session.readBlock(0xF000) }
        assertFailsWith<ProtocolException> { session.writeBlock(0xF240, ByteArray(Uv5rmMemory.BLOCK_SIZE)) }
    }

    @Test
    fun `block access before handshake is refused`() {
        val session = CloneSession(FakeRadio())
        assertFailsWith<ProtocolException> { session.readBlock(0) }
    }

    @Test
    fun `a silent radio produces a clear timeout error`() {
        val silent = object : SerialTransport {
            override fun write(data: ByteArray) {}
            override fun read(count: Int, timeoutMs: Long) = ByteArray(0)
            override fun discardInput() {}
        }
        val e = assertFailsWith<ProtocolException> { CloneSession(silent).handshake() }
        assertTrue(e.message!!.contains("timeout"))
    }
}
