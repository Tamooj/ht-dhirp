package com.highcentrality.htdhirp.core

// Tone field encoding follows CHIRP's baofeng_uv17Pro.py (decode_tone/encode_tone) (https://chirpmyradio.com, GPLv3).
// No CHIRP source is copied.
/**
 * Sub-audible tone setting for one direction of a channel.
 *
 * [Dcs.index] is an index into the standard DTCS code table; the table itself is
 * not carried here yet (see docs/PROTOCOL.md, open items).
 */
sealed interface Tone {
    data object None : Tone

    /** CTCSS in tenths of a hertz: 1148 means 114.8 Hz. */
    data class Ctcss(val tenthsHz: Int) : Tone

    data class Dcs(val index: Int, val reversed: Boolean) : Tone {
        /** The three-digit DCS code, or null if [index] is outside the radio's table. */
        val code: Int? get() = DtcsCodes.ALL.getOrNull(index)

        companion object {
            fun of(code: Int, reversed: Boolean = false): Dcs {
                val i = DtcsCodes.ALL.indexOf(code)
                require(i >= 0) { "$code is not a DCS code this radio supports" }
                return Dcs(i, reversed)
            }
        }
    }

    /** A raw value we don't understand; preserved verbatim so edits don't lose it. */
    data class Unknown(val raw: Int) : Tone
}

/**
 * DCS codes in the radio's order: the standard 104 codes plus 645, sorted. The tone field stores
 * an index into this list (CHIRP's baofeng_uv17Pro.py, https://chirpmyradio.com, GPLv3).
 */
object DtcsCodes {
    private val STANDARD = listOf(
        23, 25, 26, 31, 32, 36, 43, 47, 51, 53, 54, 65, 71, 72, 73, 74, 114, 115, 116, 122, 125, 131,
        132, 134, 143, 145, 152, 155, 156, 162, 165, 172, 174, 205, 212, 223, 225, 226, 243, 244, 245,
        246, 251, 252, 255, 261, 263, 265, 266, 271, 274, 306, 311, 315, 325, 331, 332, 343, 346, 351,
        356, 364, 365, 371, 411, 412, 413, 423, 431, 432, 445, 446, 452, 454, 455, 462, 464, 465, 466,
        503, 506, 516, 523, 526, 532, 546, 565, 606, 612, 624, 627, 631, 632, 654, 662, 664, 703, 712,
        723, 731, 732, 734, 743, 754,
    )
    val STANDARD_COUNT = STANDARD.size
    val ALL: List<Int> = (STANDARD + 645).sorted()
}

/** 16-bit tone field codec. Mirrors CHIRP's UV17Pro `decode_tone`/`encode_tone`. */
object ToneCodec {
    private const val CTCSS_MIN = 0x0258
    private const val DCS_NORMAL_MAX = 0x69
    private const val DCS_REVERSED_BASE = 0x6A

    fun decode(raw: Int): Tone = when {
        raw == 0 || raw == 0xFFFF -> Tone.None
        raw >= CTCSS_MIN -> Tone.Ctcss(raw)
        raw in 1..DCS_NORMAL_MAX -> Tone.Dcs(raw - 1, reversed = false)
        raw >= DCS_REVERSED_BASE -> Tone.Dcs(raw - DCS_REVERSED_BASE, reversed = true)
        else -> Tone.Unknown(raw)
    }

    fun encode(tone: Tone): Int = when (tone) {
        Tone.None -> 0
        is Tone.Ctcss -> tone.tenthsHz
        is Tone.Dcs -> if (tone.reversed) tone.index + DCS_REVERSED_BASE else tone.index + 1
        is Tone.Unknown -> tone.raw
    }
}
