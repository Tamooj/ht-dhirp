package com.highcentrality.htdhirp.core

/**
 * The per-block XOR obfuscation used on the UV-5RM clone link.
 * Ported from the hardware-proven bfctrl.go `Crypt`, cross-checked with CHIRP's `_crypt`.
 */
object WireCrypt {
    /** 4-byte keys; the radio picks one via the SEND key-selection exchange. */
    private val KEYS = listOf(
        "BHT ", "CO 7", "A ES", " EIY", "M PQ", "XN Y", "RVB ", " HQP", "W RC", "MS N",
        " SAT", "K DH", "ZO R", "C SL", "6RB ", " JCG", "PN V", "J PK", "EK L", "I LZ",
    ).map { it.toByteArray(Charsets.US_ASCII) }

    /** Key index used by the UV-5RM (CHIRP `_encrsym = 1`). */
    const val UV5RM_KEY_INDEX = 1

    /**
     * XORs [data] with the key at [keyIndex]. Bytes that are 0x00, 0xFF, a key byte,
     * or whose XOR would be 0xFF pass through, as does every position keyed by a
     * space. The function is its own inverse.
     */
    fun crypt(data: ByteArray, keyIndex: Int): ByteArray {
        require(keyIndex in KEYS.indices) { "bad key index $keyIndex" }
        val key = KEYS[keyIndex]
        val out = ByteArray(data.size)
        for (i in data.indices) {
            val k = key[i and 3].toInt() and 0xFF
            val d = data[i].toInt() and 0xFF
            out[i] = if (k != 0x20 && d != 0 && d != 0xFF && d != k && (k xor d) != 0xFF) {
                (d xor k).toByte()
            } else {
                data[i]
            }
        }
        return out
    }

    /**
     * Derives the key index from the payload sent after "SEND" in the key-selection
     * step, the same way the radio does. Returns -1 if the payload selects no key.
     */
    fun keyIndexFromSendPayload(payload: ByteArray): Int {
        if (payload.isEmpty()) return -1
        val first = payload[0].toInt() and 0xFF
        val idx = if (first >= 0x20) {
            val a = first - 0x20
            if (a > 4) return -1
            2 * a + 2
        } else {
            val a = first - 0x10
            if (a !in 0..4) return -1
            2 * a + 1
        }
        if (idx >= payload.size) return -1
        val key = payload[idx].toInt() and 0xFF
        return if (key > 0x13) -1 else key
    }
}
