package com.highcentrality.htdhirp.core

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Golden records are taken from a real desktop-CHIRP read of a UV-5RM. */
class ChannelCodecTest {
    private val simplex = hex("00 20 65 14 00 20 65 14 00 00 00 00 00 00 00 04 ff ff ff ff 56 48 46 20 43 61 6c 6c 69 6e 67 ff")
    private val repeater = hex("00 20 72 14 00 20 78 14 00 00 7c 04 00 00 00 04 ff ff ff ff 47 69 64 64 69 6e 67 73 20 4e 45 35")
    private val repeaterBothTones = hex("00 10 70 14 00 10 76 14 7c 04 7c 04 00 00 00 04 ff ff ff ff 53 6d 69 74 68 56 20 4b 43 35 ff ff")

    @Test
    fun `decodes a simplex channel`() {
        val ch = ChannelCodec.decode(simplex, 0)
        assertEquals(146_520_000L, ch.rxHz)
        assertEquals(146_520_000L, ch.txHz)
        assertEquals(Tone.None, ch.rxTone)
        assertEquals(Tone.None, ch.txTone)
        assertEquals("VHF Calling", ch.name)
        assertEquals(0, ch.powerRaw)
        assertFalse(ch.narrow)
        assertTrue(ch.scan)
    }

    @Test
    fun `decodes a repeater with a TX tone and a full-width name`() {
        val ch = ChannelCodec.decode(repeater, 0)
        assertEquals(147_220_000L, ch.rxHz)
        assertEquals(147_820_000L, ch.txHz)
        assertEquals(600_000L, ch.offsetHz)
        assertEquals(Tone.Ctcss(1148), ch.txTone)
        assertEquals("Giddings NE5", ch.name)
    }

    @Test
    fun `decodes a repeater with both tones`() {
        val ch = ChannelCodec.decode(repeaterBothTones, 0)
        assertEquals(147_010_000L, ch.rxHz)
        assertEquals(147_610_000L, ch.txHz)
        assertEquals(Tone.Ctcss(1148), ch.rxTone)
        assertEquals(Tone.Ctcss(1148), ch.txTone)
        assertEquals("SmithV KC5", ch.name)
    }

    @Test
    fun `re-encoding an unchanged channel leaves the record byte-identical`() {
        for (rec in listOf(simplex, repeater, repeaterBothTones)) {
            val copy = rec.copyOf()
            ChannelCodec.encodeInto(copy, 0, ChannelCodec.decode(copy, 0))
            assertContentEquals(rec, copy)
        }
    }

    @Test
    fun `editing one field preserves unmodelled bits`() {
        val rec = simplex.copyOf()
        rec[16] = 0x12 // an "unknown" byte the radio set
        rec[15] = (rec[15].toInt() or 0x82).toByte() // unknown1 + unknown2 bits
        val before = ChannelCodec.decode(rec, 0)
        ChannelCodec.encodeInto(rec, 0, before.copy(narrow = true))
        assertEquals(0x12, rec[16].toInt())
        assertEquals(0xC6, rec[15].toInt() and 0xFF) // 0x04 scan | 0x82 unknowns | 0x40 narrow
        assertTrue(ChannelCodec.decode(rec, 0).narrow)
    }

    @Test
    fun `writes a new channel into an empty slot`() {
        val buf = ByteArray(ChannelCodec.RECORD_SIZE) { 0xFF.toByte() }
        val ch = Channel(
            rxHz = 147_220_000L, txHz = 147_820_000L,
            txTone = Tone.Ctcss(1148), name = "Giddings NE5",
        )
        ChannelCodec.encodeInto(buf, 0, ch)
        assertContentEquals(repeater, buf)
        assertEquals(ch, ChannelCodec.decode(buf, 0))
    }

    @Test
    fun `short names are padded with FF and long names truncated to 12`() {
        val buf = ByteArray(ChannelCodec.RECORD_SIZE) { 0xFF.toByte() }
        ChannelCodec.encodeInto(buf, 0, Channel(146_520_000L, 146_520_000L, name = "VHF Calling"))
        assertContentEquals(simplex, buf)
        ChannelCodec.encodeInto(buf, 0, Channel(146_520_000L, 146_520_000L, name = "ABCDEFGHIJKLMNOP"))
        assertEquals("ABCDEFGHIJKL", ChannelCodec.decode(buf, 0).name)
    }

    @Test
    fun `TX-inhibited channel round-trips`() {
        val buf = ByteArray(ChannelCodec.RECORD_SIZE) { 0xFF.toByte() }
        ChannelCodec.encodeInto(buf, 0, Channel(162_550_000L, null, name = "NOAA WX1"))
        assertNull(ChannelCodec.decode(buf, 0).txHz)
    }

    @Test
    fun `RadioImage finds empty slots and clears channels`() {
        val image = RadioImage(ByteArray(Uv5rmMemory.IMAGE_SIZE) { 0xFF.toByte() })
        assertEquals(RadioImage.CHANNEL_COUNT, image.emptySlots().size)
        image.setChannel(5, ChannelCodec.decode(simplex, 0))
        assertEquals(listOf(5), image.usedSlots())
        image.clearChannel(5)
        assertTrue(image.usedSlots().isEmpty())
    }
}
