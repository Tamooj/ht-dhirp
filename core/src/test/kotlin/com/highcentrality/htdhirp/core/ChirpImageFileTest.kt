package com.highcentrality.htdhirp.core

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChirpImageFileTest {
    @Test
    fun `splits data from the metadata trailer and round-trips`() {
        val data = ByteArray(100) { it.toByte() }
        val json = """{"model": "5RM"}"""
        val trailer = Base64.getEncoder().encode(json.toByteArray())
        val file = data + ChirpImageFile.MAGIC + trailer

        val parsed = ChirpImageFile.parse(file)
        assertContentEquals(data, parsed.data)
        assertEquals(json, parsed.metadataJson())
        assertContentEquals(file, parsed.serialize())
    }

    @Test
    fun `create builds a container that parses back`() {
        val data = ByteArray(50) { (it * 3).toByte() }
        val json = """{"vendor": "Baofeng", "model": "5RM"}"""
        val parsed = ChirpImageFile.parse(ChirpImageFile.create(data, json).serialize())
        assertContentEquals(data, parsed.data)
        assertEquals(json, parsed.metadataJson())
    }

    @Test
    fun `a file without a trailer is all data`() {
        val data = ByteArray(10) { 7 }
        val parsed = ChirpImageFile.parse(data)
        assertContentEquals(data, parsed.data)
        assertNull(parsed.metadataJson())
        assertContentEquals(data, parsed.serialize())
    }
}
