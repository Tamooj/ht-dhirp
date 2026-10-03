package com.highcentrality.htdhirp.core

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class BcdAndToneTest {
    @Test
    fun `decodes little-endian BCD`() {
        // 00 20 65 14 -> digits 14652000 -> 146.520 MHz in 10 Hz units
        assertEquals(14_652_000L, Bcd.decodeLe(hex("00 20 65 14"), 0, 4))
    }

    @Test
    fun `rejects non-BCD nibbles`() {
        assertNull(Bcd.decodeLe(hex("0A 00 00 00"), 0, 4))
    }

    @Test
    fun `encode inverts decode`() {
        val buf = ByteArray(4)
        Bcd.encodeLe(14_652_000L, buf, 0, 4)
        assertContentEquals(hex("00 20 65 14"), buf)
    }

    @Test
    fun `encode rejects overflow`() {
        assertFailsWith<IllegalArgumentException> { Bcd.encodeLe(100_000_000L, ByteArray(4), 0, 4) }
    }

    @Test
    fun `tone decode`() {
        assertEquals(Tone.None, ToneCodec.decode(0))
        assertEquals(Tone.None, ToneCodec.decode(0xFFFF))
        assertEquals(Tone.Ctcss(1148), ToneCodec.decode(0x047C))
        assertEquals(Tone.Dcs(0, reversed = false), ToneCodec.decode(1))
        assertEquals(Tone.Dcs(0, reversed = true), ToneCodec.decode(0x6A))
    }

    @Test
    fun `tone encode inverts decode for every 16-bit value except aliases of none`() {
        for (raw in 1 until 0xFFFF) {
            assertEquals(raw, ToneCodec.encode(ToneCodec.decode(raw)), "raw=$raw")
        }
    }
}
