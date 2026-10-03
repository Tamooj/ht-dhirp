package com.highcentrality.htdhirp.core

import java.util.Base64

/**
 * CHIRP `.img` container: raw memory, then (usually) a magic marker and a base64 JSON
 * metadata trailer. The trailer holds per-channel comments and the radio's model
 * alias, which never reach the radio, so it is kept verbatim for round-tripping.
 */
class ChirpImageFile(val data: ByteArray, val trailer: ByteArray?) {

    fun serialize(): ByteArray = if (trailer == null) data.copyOf() else data + MAGIC + trailer

    /** The decoded JSON metadata, or null if the file had no trailer. */
    fun metadataJson(): String? = trailer?.let { String(Base64.getMimeDecoder().decode(it), Charsets.UTF_8) }

    companion object {
        /** `\x00\xffchirp\xeeimg\x00\x01` */
        val MAGIC: ByteArray = byteArrayOf(
            0x00, 0xFF.toByte(), 'c'.code.toByte(), 'h'.code.toByte(), 'i'.code.toByte(), 'r'.code.toByte(),
            'p'.code.toByte(), 0xEE.toByte(), 'i'.code.toByte(), 'm'.code.toByte(), 'g'.code.toByte(), 0x00, 0x01,
        )

        fun parse(bytes: ByteArray): ChirpImageFile {
            val at = lastIndexOf(bytes, MAGIC)
            return if (at < 0) {
                ChirpImageFile(bytes.copyOf(), null)
            } else {
                ChirpImageFile(bytes.copyOfRange(0, at), bytes.copyOfRange(at + MAGIC.size, bytes.size))
            }
        }

        private fun lastIndexOf(hay: ByteArray, needle: ByteArray): Int {
            for (start in hay.size - needle.size downTo 0) {
                if (needle.indices.all { hay[start + it] == needle[it] }) return start
            }
            return -1
        }
    }
}
