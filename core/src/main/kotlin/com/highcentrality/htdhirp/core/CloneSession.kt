package com.highcentrality.htdhirp.core

// Handshake and block protocol as documented by CHIRP's baofeng_uv17Pro.py (https://chirpmyradio.com, GPLv3) and
// verified with an independent Go tool. No CHIRP source is copied.
/** Identification bytes the radio returns during the handshake. */
data class DeviceInfo(val info: ByteArray, val model: ByteArray) {
    val infoHex: String get() = info.joinToString(" ") { "%02X".format(it) }
    val modelText: String get() = String(model, Charsets.ISO_8859_1)
}

/**
 * One clone-mode conversation with a UV-5RM (115200 8N1, set by the transport).
 *
 * Sequence: handshake, then 64-byte read/write blocks addressed by 16-bit address,
 * obfuscated with [WireCrypt]. Only the regions in [Uv5rmMemory.regions] are touched.
 */
class CloneSession(
    private val io: SerialTransport,
    private val timeoutMs: Long = 1000,
) {
    var keyIndex: Int = -1
        private set

    fun handshake(): DeviceInfo {
        io.discardInput()
        expectAck("handshake", exchange(MAGIC_STRING, 1))
        val info = exchange(byteArrayOf('F'.code.toByte()), 16, "device info")
        val model = exchange(byteArrayOf('M'.code.toByte()), 15, "model string")
        expectAck("key selection", exchange(KEY_SELECT, 1))
        keyIndex = WireCrypt.keyIndexFromSendPayload(KEY_SELECT.copyOfRange(4, KEY_SELECT.size))
        if (keyIndex < 0) throw ProtocolException("key selection produced no usable key index")
        io.discardInput()
        return DeviceInfo(info, model)
    }

    /** Reads one block and returns it decrypted. */
    fun readBlock(addr: Int): ByteArray {
        checkReady(addr)
        val res = exchange(byteArrayOf('R'.code.toByte(), (addr shr 8).toByte(), addr.toByte(), BLOCK.toByte()), 4 + BLOCK, "read 0x%04X".format(addr))
        if (res[0] != 'R'.code.toByte() || res[1] != (addr shr 8).toByte() || res[2] != addr.toByte()) {
            throw ProtocolException("read 0x%04X: unexpected header %s".format(addr, res.copyOf(4).joinToString(" ") { "%02X".format(it) }))
        }
        return WireCrypt.crypt(res.copyOfRange(4, res.size), keyIndex)
    }

    /** Encrypts and writes one block of exactly [Uv5rmMemory.BLOCK_SIZE] bytes. */
    fun writeBlock(addr: Int, plain: ByteArray) {
        checkReady(addr)
        require(plain.size == BLOCK) { "block must be $BLOCK bytes, got ${plain.size}" }
        val cmd = byteArrayOf('W'.code.toByte(), (addr shr 8).toByte(), addr.toByte(), BLOCK.toByte()) +
            WireCrypt.crypt(plain, keyIndex)
        expectAck("write 0x%04X".format(addr), exchange(cmd, 1))
    }

    /** Reads all four regions into a packed image in CHIRP's layout. */
    fun readImage(onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): ByteArray {
        val image = ByteArray(Uv5rmMemory.IMAGE_SIZE)
        val total = Uv5rmMemory.IMAGE_SIZE / BLOCK
        var done = 0
        for (r in Uv5rmMemory.regions) {
            for (delta in 0 until r.size step BLOCK) {
                readBlock(r.radioAddr + delta).copyInto(image, r.imageOffset + delta)
                onProgress(++done, total)
            }
        }
        return image
    }

    /**
     * Writes the whole image, region by region, in address order. Whole regions are
     * always written (never just changed blocks): the flash may erase a sector when a
     * block at its start is written, so partial writes could wipe neighbours.
     */
    fun writeImage(image: ByteArray, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }) {
        require(image.size == Uv5rmMemory.IMAGE_SIZE) { "image must be ${Uv5rmMemory.IMAGE_SIZE} bytes, got ${image.size}" }
        val total = Uv5rmMemory.IMAGE_SIZE / BLOCK
        var done = 0
        for (r in Uv5rmMemory.regions) {
            for (delta in 0 until r.size step BLOCK) {
                val from = r.imageOffset + delta
                writeBlock(r.radioAddr + delta, image.copyOfRange(from, from + BLOCK))
                onProgress(++done, total)
            }
        }
    }

    private fun checkReady(addr: Int) {
        if (keyIndex < 0) throw ProtocolException("handshake not completed")
        // Guard rail: stay inside the regions we know. Never touch 0xF000 and up.
        val inside = Uv5rmMemory.regions.any { addr >= it.radioAddr && addr + BLOCK <= it.radioAddr + it.size }
        if (!inside) throw ProtocolException("address 0x%04X is outside the permitted regions".format(addr))
    }

    private fun exchange(tx: ByteArray, rxCount: Int, what: String = "response"): ByteArray {
        io.write(tx)
        val rx = io.read(rxCount, timeoutMs)
        if (rx.size < rxCount) {
            throw ProtocolException("timeout waiting for $what: got ${rx.size} of $rxCount bytes (check cable, baud rate, radio state)")
        }
        return rx
    }

    private fun expectAck(step: String, res: ByteArray) {
        if (res.size != 1 || res[0] != ACK) {
            throw ProtocolException("$step: expected ACK 0x06, got ${res.joinToString(" ") { "%02X".format(it) }}")
        }
    }

    private companion object {
        const val BLOCK = Uv5rmMemory.BLOCK_SIZE
        const val ACK: Byte = 0x06
        val MAGIC_STRING = "PROGRAMBFNORMALU".toByteArray(Charsets.US_ASCII)

        /** "SEND" + 21 bytes selecting key index 1; copied from the working bfctrl.go. */
        val KEY_SELECT: ByteArray = "SEND".toByteArray(Charsets.US_ASCII) + intArrayOf(
            0x21, 0x05, 0x0D, 0x01, 0x01, 0x01, 0x04, 0x11, 0x08, 0x05, 0x0D,
            0x0D, 0x01, 0x11, 0x0F, 0x09, 0x12, 0x09, 0x10, 0x04, 0x00,
        ).map { it.toByte() }.toByteArray()
    }
}
