package com.highcentrality.htdhirp.core

/**
 * One memory channel of a UV-5RM-family radio.
 *
 * Frequencies are in hertz and must be multiples of 10 Hz (the radio's resolution).
 * [txHz] is null when transmit is inhibited (receive-only channel).
 * Fields not modelled here are preserved in place by [ChannelCodec.encodeInto].
 */
data class Channel(
    val rxHz: Long,
    val txHz: Long?,
    val rxTone: Tone = Tone.None,
    val txTone: Tone = Tone.None,
    val name: String = "",
    /** Raw 2-bit power field: 0 = high, 1 = low. */
    val powerRaw: Int = 0,
    /** True for narrow FM (12.5 kHz). */
    val narrow: Boolean = false,
    val busyLockout: Boolean = false,
    /** False means the channel is skipped when scanning. */
    val scan: Boolean = true,
    val scramble: Int = 0,
    val sqMode: Int = 0,
    val fhss: Boolean = false,
) {
    val offsetHz: Long? get() = txHz?.minus(rxHz)
}
