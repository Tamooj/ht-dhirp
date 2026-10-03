package com.highcentrality.htdhirp.core

import java.io.File
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs against a real CHIRP image. Looks for HT_FIXTURE_IMG, else the first *.img in
 * fixtures/local (gitignored). Skipped when neither exists, so CI and fresh clones stay green.
 */
class FixtureImageTest {
    private fun fixture(): ByteArray? {
        System.getenv("HT_FIXTURE_IMG")?.let { return File(it).takeIf(File::exists)?.readBytes() }
        val dir = File(System.getProperty("ht.fixtures") ?: "../fixtures/local")
        return dir.listFiles { f -> f.extension == "img" }?.minByOrNull { it.name }?.readBytes()
    }

    @Test
    fun `container splits into a 0x8380 data block and round-trips byte for byte`() {
        val raw = fixture()
        assumeTrue(raw != null, "no fixture image present")
        val file = ChirpImageFile.parse(raw!!)
        assertEquals(Uv5rmMemory.IMAGE_SIZE, file.data.size)
        assertContentEquals(raw, file.serialize())
        assertTrue(file.metadataJson()!!.contains("5RM"))
    }

    @Test
    fun `every used channel decodes and re-encodes to identical bytes`() {
        val raw = fixture()
        assumeTrue(raw != null, "no fixture image present")
        val image = RadioImage(ChirpImageFile.parse(raw!!).data)
        val used = image.usedSlots()
        assertTrue(used.isNotEmpty(), "fixture has no channels")
        for (slot in used) {
            val off = slot * ChannelCodec.RECORD_SIZE
            val original = image.bytes.copyOfRange(off, off + ChannelCodec.RECORD_SIZE)
            val copy = original.copyOf()
            ChannelCodec.encodeInto(copy, 0, ChannelCodec.decode(copy, 0))
            assertContentEquals(original, copy, "slot $slot")
        }
    }
}
