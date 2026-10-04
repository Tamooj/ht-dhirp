package com.highcentrality.htdhirp.core

// Handshake and block protocol as documented by CHIRP's baofeng_uv17Pro.py (https://chirpmyradio.com, GPLv3) and
// verified with an independent Go tool. No CHIRP source is copied.
/** Identification bytes the radio returns during the handshake. */
data class DeviceInfo(val info: ByteArray, val model: ByteArray, val keySelectAck: Byte) {
    val infoHex: String get() = info.joinToString(" ") { "%02X".format(it) }
    val modelText: String get() = String(model, Charsets.ISO_8859_1)
}

/**
 * What differs between radios that share this protocol: the ident string and the key-selection
 * message. Other models in the family (e.g. the UV-5RM Plus) use different ones, so never assume.
 */
class RadioVariant(val name: String, val ident: ByteArray, val keySelect: ByteArray) {
    companion object {
        private fun ascii(s: String) = s.toByteArray(Charsets.US_ASCII)

        /** Confirmed on hardware with the owner's UV-5RM. */
        val UV5RM = RadioVariant(
            "Baofeng UV-5RM",
            ascii("PROGRAMBFNORMALU"),
            ascii("SEND") + intArrayOf(
                0x21, 0x05, 0x0D, 0x01, 0x01, 0x01, 0x04, 0x11, 0x08, 0x05, 0x0D,
                0x0D, 0x01, 0x11, 0x0F, 0x09, 0x12, 0x09, 0x10, 0x04, 0x00,
            ).map { it.toByte() }.toByteArray(),
        )
    }
}

/**
 * One clone-mode conversation with a UV-5RM (115200 8N1, set by the transport).
 *
 * Sequence: handshake, then 64-byte read/write blocks addressed by 16-bit address,
 * obfuscated with [WireCrypt]. Only the regions in [Uv5rmMemory.regions] are touched.
 */
class CloneSession(
    private val io: SerialTransport,
    private val timeoutMs: Long = 1500,
    private val variant: RadioVariant = RadioVariant.UV5RM,
    private val log: CommLog? = null,
) {
    var keyIndex: Int = -1
        private set

    fun handshake(): DeviceInfo {
        log?.note("handshake start (${variant.name})")
        io.discardInput()
        expectAck("handshake", exchange(variant.ident, 1))
        log?.note("handshake: device info")
        val info = exchange(byteArrayOf('F'.code.toByte()), 16, "device info")
        log?.note("handshake: model string")
        val model = exchange(byteArrayOf('M'.code.toByte()), 15, "model string")
        log?.note("handshake: key selection")
        // A reply is required, but its value isn't checked: CHIRP ignores it too, and some firmware may differ.
        val keyAck = exchange(variant.keySelect, 1)[0]
        keyIndex = WireCrypt.keyIndexFromSendPayload(variant.keySelect.copyOfRange(4, variant.keySelect.size))
        if (keyIndex < 0) throw ProtocolException("key selection produced no usable key index")
        io.discardInput()
        log?.note("handshake done, key index $keyIndex")
        return DeviceInfo(info, model, keyAck)
    }

    /** Reads one block and returns it decrypted. */
    fun readBlock(addr: Int): ByteArray {
        checkReady(addr)
        log?.note("read block 0x%04X".format(addr))
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
        log?.note("write block 0x%04X".format(addr))
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
    }
}
