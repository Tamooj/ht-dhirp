package com.highcentrality.htdhirp.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class WireCryptTest {
    @Test
    fun `known vector for key index 1`() {
        // key "CO 7": 'A' ^ 'C' = 02, 'A' ^ 'O' = 0E, space key leaves byte alone, 'A' ^ '7' = 76
        assertContentEquals(hex("02 0E 41 76"), WireCrypt.crypt(hex("41 41 41 41"), 1))
    }

    @Test
    fun `00 and FF bytes pass through`() {
        val data = hex("00 FF 00 FF")
        assertContentEquals(data, WireCrypt.crypt(data, 1))
    }

    @Test
    fun `crypt is its own inverse for every key`() {
        val rnd = Random(1234)
        val data = rnd.nextBytes(4096)
        for (key in 0..0x13) {
            assertContentEquals(data, WireCrypt.crypt(WireCrypt.crypt(data, key), key), "key=$key")
        }
    }

    @Test
    fun `key selection payload from the working tool yields key 1`() {
        val payload = intArrayOf(0x21, 0x05, 0x0D, 0x01, 0x01, 0x01, 0x04, 0x11).map { it.toByte() }.toByteArray()
        assertEquals(1, WireCrypt.keyIndexFromSendPayload(payload))
    }

    @Test
    fun `key selection rejects out-of-range selectors`() {
        assertEquals(-1, WireCrypt.keyIndexFromSendPayload(hex("30 00 00 00 00 00 00 00 00 00 00")))
        assertEquals(-1, WireCrypt.keyIndexFromSendPayload(hex("21 05 0D 01 7F 01")))
        assertEquals(-1, WireCrypt.keyIndexFromSendPayload(ByteArray(0)))
    }
}
