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

    data class Dcs(val index: Int, val reversed: Boolean) : Tone

    /** A raw value we don't understand; preserved verbatim so edits don't lose it. */
    data class Unknown(val raw: Int) : Tone
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
