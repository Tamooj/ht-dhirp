package com.highcentrality.htdhirp.core

/**
 * In-memory stand-in for a UV-5RM, speaking the clone protocol byte for byte.
 *
 * Pessimistic about flash: writing a block at a 4 KiB sector start erases the whole
 * sector first, so a session that only wrote changed blocks would lose data here.
 */
class FakeRadio(val memory: ByteArray = ByteArray(0x10000) { 0xFF.toByte() }) : SerialTransport {
    private var pending = ByteArray(0)
    private val outbox = ArrayDeque<Byte>()

    /** Addresses of every block written, in order. */
    val writes = mutableListOf<Int>()

    /** Addresses of every block read, in order. */
    val reads = mutableListOf<Int>()

    fun load(image: ByteArray) {
        for (r in Uv5rmMemory.regions) image.copyInto(memory, r.radioAddr, r.imageOffset, r.imageOffset + r.size)
    }

    fun toImage(): ByteArray {
        val image = ByteArray(Uv5rmMemory.IMAGE_SIZE)
        for (r in Uv5rmMemory.regions) memory.copyInto(image, r.imageOffset, r.radioAddr, r.radioAddr + r.size)
        return image
    }

    override fun write(data: ByteArray) {
        pending += data
        while (consumeOne()) { /* keep going while whole commands are buffered */ }
    }

    override fun read(count: Int, timeoutMs: Long): ByteArray {
        val n = minOf(count, outbox.size)
        return ByteArray(n) { outbox.removeFirst() }
    }

    override fun discardInput() = outbox.clear()

    private fun reply(bytes: ByteArray) = bytes.forEach { outbox.addLast(it) }

    private fun consumeOne(): Boolean {
        if (pending.isEmpty()) return false
        val used = when (pending[0].toInt().toChar()) {
            'P' -> if (pending.size >= 16) { reply(byteArrayOf(0x06)); 16 } else 0
            'F' -> { reply(ByteArray(16) { (0xA0 + it).toByte() }); 1 }
            'M' -> { reply("MODEL-STRING-FAKE".toByteArray().copyOf(15)); 1 }
            'S' -> if (pending.size >= 25) { reply(byteArrayOf(0x06)); 25 } else 0
            'R' -> if (pending.size >= 4) { serveRead(); 4 } else 0
            'W' -> if (pending.size >= 4 && pending.size >= 4 + len(pending)) { acceptWrite(); 4 + len(pending) } else 0
            else -> throw IllegalStateException("fake radio: unexpected byte 0x%02X".format(pending[0]))
        }
        if (used == 0) return false
        pending = pending.copyOfRange(used, pending.size)
        return pending.isNotEmpty()
    }

    private fun len(p: ByteArray) = p[3].toInt() and 0xFF
    private fun addr(p: ByteArray) = ((p[1].toInt() and 0xFF) shl 8) or (p[2].toInt() and 0xFF)

    private fun serveRead() {
        val a = addr(pending)
        val n = len(pending)
        reads += a
        val plain = memory.copyOfRange(a, a + n)
        val wire = if (a < 0xF000) WireCrypt.crypt(plain, WireCrypt.UV5RM_KEY_INDEX) else plain
        reply(byteArrayOf('R'.code.toByte(), pending[1], pending[2], pending[3]) + wire)
    }

    private fun acceptWrite() {
        val a = addr(pending)
        val n = len(pending)
        writes += a
        if (a and 0xFFF == 0) memory.fill(0xFF.toByte(), a, a + 0x1000)
        val wire = pending.copyOfRange(4, 4 + n)
        val plain = if (a < 0xF000) WireCrypt.crypt(wire, WireCrypt.UV5RM_KEY_INDEX) else wire
        plain.copyInto(memory, a)
        reply(byteArrayOf(0x06))
    }
}
