package com.highcentrality.htdhirp.core

/**
 * 32-byte channel record codec (CHIRP `baofeng_uv17Pro.py`, `memory_obj`).
 *
 * ```
 *  0..3   rx freq, LE BCD, 10 Hz units
 *  4..7   tx freq, same; all 0xFF = TX inhibited
 *  8..9   rx tone  (LE u16)        10..11  tx tone
 * 12      scode        13  pttid
 * 14      [7:6] unk  [5:4] scramble  [3:2] unk  [1:0] power
 * 15      [7] unk [6] narrow [5:4] sqmode [3] bcl [2] scan [1] unk [0] fhss
 * 16..19  unknown      20..31  name, 12 bytes, 0xFF padded
 * ```
 * An unused slot has 0xFF as its first byte.
 *
 * Writes are edit-in-place: unmodelled bits keep whatever the radio had.
 */
object ChannelCodec {
    const val RECORD_SIZE = 32
    private const val NAME_OFFSET = 20
    private const val NAME_LENGTH = 12
    private val FF = 0xFF.toByte()

    fun isEmpty(buf: ByteArray, off: Int): Boolean = buf[off] == FF

    /** Throws [IllegalArgumentException] for an empty or corrupt record. */
    fun decode(buf: ByteArray, off: Int): Channel {
        require(!isEmpty(buf, off)) { "record at $off is empty" }
        val rx = Bcd.decodeLe(buf, off, 4)
            ?: throw IllegalArgumentException("record at $off: RX frequency is not valid BCD")
        val txInhibited = (4..7).all { buf[off + it] == FF }
        val tx = if (txInhibited) {
            null
        } else {
            (Bcd.decodeLe(buf, off + 4, 4)
                ?: throw IllegalArgumentException("record at $off: TX frequency is not valid BCD")) * 10
        }
        val b14 = u8(buf, off + 14)
        val b15 = u8(buf, off + 15)
        return Channel(
            rxHz = rx * 10,
            txHz = tx,
            rxTone = ToneCodec.decode(u16(buf, off + 8)),
            txTone = ToneCodec.decode(u16(buf, off + 10)),
            name = decodeName(buf, off + NAME_OFFSET),
            powerRaw = b14 and 0x03,
            narrow = b15 and 0x40 != 0,
            busyLockout = b15 and 0x08 != 0,
            scan = b15 and 0x04 != 0,
            scramble = (b14 shr 4) and 0x03,
            sqMode = (b15 shr 4) and 0x03,
            fhss = b15 and 0x01 != 0,
        )
    }

    /**
     * Writes [ch] into the record at [off]. Existing unmodelled bits are kept, and
     * tone/name bytes are left alone when they already decode to the requested value.
     */
    fun encodeInto(buf: ByteArray, off: Int, ch: Channel) {
        require(ch.rxHz % 10 == 0L && (ch.txHz ?: 0L) % 10 == 0L) { "frequencies must be multiples of 10 Hz" }
        require(ch.powerRaw in 0..3 && ch.scramble in 0..3 && ch.sqMode in 0..3) { "2-bit field out of range" }

        val cur: Channel? = if (isEmpty(buf, off)) null else runCatching { decode(buf, off) }.getOrNull()
        if (cur == null) writeBlank(buf, off)

        Bcd.encodeLe(ch.rxHz / 10, buf, off, 4)
        if (ch.txHz == null) {
            for (i in 4..7) buf[off + i] = FF
        } else {
            Bcd.encodeLe(ch.txHz / 10, buf, off + 4, 4)
        }
        if (cur == null || cur.rxTone != ch.rxTone) putU16(buf, off + 8, ToneCodec.encode(ch.rxTone))
        if (cur == null || cur.txTone != ch.txTone) putU16(buf, off + 10, ToneCodec.encode(ch.txTone))

        var b14 = u8(buf, off + 14)
        b14 = setField(b14, 0x03, 0, ch.powerRaw)
        b14 = setField(b14, 0x03, 4, ch.scramble)
        buf[off + 14] = b14.toByte()

        var b15 = u8(buf, off + 15)
        b15 = setField(b15, 0x01, 6, if (ch.narrow) 1 else 0)
        b15 = setField(b15, 0x03, 4, ch.sqMode)
        b15 = setField(b15, 0x01, 3, if (ch.busyLockout) 1 else 0)
        b15 = setField(b15, 0x01, 2, if (ch.scan) 1 else 0)
        b15 = setField(b15, 0x01, 0, if (ch.fhss) 1 else 0)
        buf[off + 15] = b15.toByte()

        if (cur == null || cur.name != ch.name) encodeName(buf, off + NAME_OFFSET, ch.name)
    }

    fun clear(buf: ByteArray, off: Int) {
        buf.fill(FF, off, off + RECORD_SIZE)
    }

    /** Defaults seen on factory-fresh records: scode/pttid 0, power high, wide FM, scan on. */
    private fun writeBlank(buf: ByteArray, off: Int) {
        clear(buf, off)
        buf[off + 8] = 0; buf[off + 9] = 0
        buf[off + 10] = 0; buf[off + 11] = 0
        buf[off + 12] = 0
        buf[off + 13] = 0
        buf[off + 14] = 0
        buf[off + 15] = 0x04
    }

    private fun decodeName(buf: ByteArray, off: Int): String {
        val sb = StringBuilder()
        for (i in 0 until NAME_LENGTH) {
            val b = u8(buf, off + i)
            if (b == 0xFF || b == 0x00) break
            sb.append(if (b in 0x20..0x7E) b.toChar() else '?')
        }
        return sb.toString().trimEnd()
    }

    private fun encodeName(buf: ByteArray, off: Int, name: String) {
        val n = name.take(NAME_LENGTH)
        require(n.all { it.code in 0x20..0x7E }) { "name must be printable ASCII: '$name'" }
        for (i in 0 until NAME_LENGTH) buf[off + i] = if (i < n.length) n[i].code.toByte() else FF
    }

    private fun setField(byte: Int, mask: Int, shift: Int, value: Int): Int =
        (byte and (mask shl shift).inv()) or ((value and mask) shl shift)

    private fun u8(buf: ByteArray, i: Int): Int = buf[i].toInt() and 0xFF
    private fun u16(buf: ByteArray, i: Int): Int = u8(buf, i) or (u8(buf, i + 1) shl 8)
    private fun putU16(buf: ByteArray, i: Int, v: Int) {
        buf[i] = (v and 0xFF).toByte()
        buf[i + 1] = ((v shr 8) and 0xFF).toByte()
    }
}
