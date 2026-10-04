package com.highcentrality.htdhirp.core

/**
 * Timestamped record of everything that crosses the serial link, for diagnosing timing,
 * slow replies, short reads and timeouts. Thread-safe. See [LoggingTransport].
 *
 * Payloads are truncated to [payloadLimit] bytes per event by default: the clone data is
 * only lightly obfuscated, so a full log contains the radio's whole channel list. Pass a
 * large limit when you need every byte.
 */
class CommLog(
    private val nanoTime: () -> Long = System::nanoTime,
    private val payloadLimit: Int = 16,
) {
    enum class Kind { TX, RX, SHORT, TIMEOUT, NOTE, DISCARD }

    private class Event(
        val atNanos: Long,
        val kind: Kind,
        val bytes: ByteArray,
        val total: Int,
        val wanted: Int,
        val waitedNanos: Long,
        val text: String,
    )

    private val start = nanoTime()
    private val headers = mutableListOf<String>()
    private val events = mutableListOf<Event>()

    fun now(): Long = nanoTime()

    @Synchronized fun header(line: String) {
        headers += line
    }

    @Synchronized fun note(text: String) {
        events += Event(nanoTime() - start, Kind.NOTE, EMPTY, 0, 0, 0, text)
    }

    @Synchronized fun tx(data: ByteArray) {
        events += Event(nanoTime() - start, Kind.TX, data.copyOf(minOf(data.size, payloadLimit)), data.size, 0, 0, "")
    }

    /** Records a read of up to [wanted] bytes that returned [got] after [waitedNanos]. */
    @Synchronized fun rx(wanted: Int, got: ByteArray, waitedNanos: Long) {
        val kind = when {
            got.size >= wanted -> Kind.RX
            got.isEmpty() -> Kind.TIMEOUT
            else -> Kind.SHORT
        }
        events += Event(nanoTime() - start, kind, got.copyOf(minOf(got.size, payloadLimit)), got.size, wanted, waitedNanos, "")
    }

    @Synchronized fun discard() {
        events += Event(nanoTime() - start, Kind.DISCARD, EMPTY, 0, 0, 0, "input buffer discarded")
    }

    @Synchronized fun eventCount(): Int = events.size

    @Synchronized fun toText(): String {
        val sb = StringBuilder()
        sb.append("# ht-dhirp communication log\n")
        headers.forEach { sb.append("# ").append(it).append('\n') }
        sb.append("# payloads truncated to $payloadLimit bytes per event; times are seconds since the log started\n\n")
        for (e in events) {
            sb.append("%8.3fs  ".format(e.atNanos / 1e9))
            when (e.kind) {
                Kind.NOTE -> sb.append("NOTE     ").append(e.text)
                Kind.DISCARD -> sb.append("DISCARD  ").append(e.text)
                Kind.TX -> sb.append("TX       ").append(payload(e))
                Kind.RX -> sb.append("RX       ").append(payload(e)).append("  (waited %.1f ms)".format(e.waitedNanos / 1e6))
                Kind.SHORT -> sb.append("SHORT    wanted ${e.wanted} B, got ${e.total} B  ").append(payload(e))
                    .append("  (waited %.1f ms)".format(e.waitedNanos / 1e6))
                Kind.TIMEOUT -> sb.append("TIMEOUT  wanted ${e.wanted} B, got 0 B  (waited %.1f ms)".format(e.waitedNanos / 1e6))
            }
            sb.append('\n')
        }
        return sb.toString()
    }

    /** Counts, byte totals and the slowest replies: the first thing to read when something was slow. */
    @Synchronized fun summary(): String {
        val sb = StringBuilder()
        val rxs = events.filter { it.kind == Kind.RX || it.kind == Kind.SHORT || it.kind == Kind.TIMEOUT }
        sb.append("TX: ${events.count { it.kind == Kind.TX }} frames, ${events.filter { it.kind == Kind.TX }.sumOf { it.total }} bytes\n")
        sb.append("RX: ${rxs.count { it.kind == Kind.RX }} ok, ")
        sb.append("${rxs.count { it.kind == Kind.SHORT }} short, ${rxs.count { it.kind == Kind.TIMEOUT }} timeouts, ")
        sb.append("${rxs.sumOf { it.total }} bytes\n")
        if (rxs.isNotEmpty()) {
            val waits = rxs.map { it.waitedNanos / 1e6 }
            sb.append("Reply wait: avg %.1f ms, max %.1f ms\n".format(waits.average(), waits.max()))
            val slow = rxs.sortedByDescending { it.waitedNanos }.take(5)
            sb.append("Slowest replies:\n")
            slow.forEach {
                sb.append("  at %.3fs  %s  wanted %d B, got %d B, waited %.1f ms\n".format(
                    it.atNanos / 1e9, it.kind, it.wanted, it.total, it.waitedNanos / 1e6))
            }
        }
        return sb.toString()
    }

    private fun payload(e: Event): String {
        val shown = e.bytes
        val hex = shown.joinToString(" ") { "%02X".format(it) }
        val ascii = String(CharArray(shown.size) { i ->
            val b = shown[i].toInt() and 0xFF
            if (b in 0x20..0x7E) b.toChar() else '.'
        })
        val more = if (e.total > shown.size) " ... (+${e.total - shown.size} B)" else ""
        return "%3d B  %s%s  |%s|".format(e.total, hex, more, ascii)
    }

    private companion object {
        val EMPTY = ByteArray(0)
    }
}

/** Wraps any [SerialTransport] and records every call into [log]. Behaviour is otherwise unchanged. */
class LoggingTransport(
    private val inner: SerialTransport,
    val log: CommLog,
) : SerialTransport {
    override fun write(data: ByteArray) {
        log.tx(data)
        try {
            inner.write(data)
        } catch (e: Exception) {
            log.note("write failed: ${e.javaClass.simpleName}: ${e.message}")
            throw e
        }
    }

    override fun read(count: Int, timeoutMs: Long): ByteArray {
        val t0 = log.now()
        val got = try {
            inner.read(count, timeoutMs)
        } catch (e: Exception) {
            log.note("read failed: ${e.javaClass.simpleName}: ${e.message}")
            throw e
        }
        log.rx(count, got, log.now() - t0)
        return got
    }

    override fun discardInput() {
        inner.discardInput()
        log.discard()
    }
}
