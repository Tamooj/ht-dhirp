package com.highcentrality.htdhirp.core

/** Little-endian packed BCD, as used for channel frequencies (units of 10 Hz). */
object Bcd {
    /** Decodes [len] bytes at [off]; returns null if any nibble is not 0..9. */
    fun decodeLe(buf: ByteArray, off: Int, len: Int): Long? {
        var value = 0L
        for (i in len - 1 downTo 0) {
            val b = buf[off + i].toInt() and 0xFF
            val hi = b ushr 4
            val lo = b and 0x0F
            if (hi > 9 || lo > 9) return null
            value = value * 100 + hi * 10 + lo
        }
        return value
    }

    fun encodeLe(value: Long, buf: ByteArray, off: Int, len: Int) {
        require(value >= 0) { "negative BCD value: $value" }
        var rest = value
        for (i in 0 until len) {
            val pair = (rest % 100).toInt()
            buf[off + i] = (((pair / 10) shl 4) or (pair % 10)).toByte()
            rest /= 100
        }
        require(rest == 0L) { "$value does not fit in $len BCD bytes" }
    }
}
