package com.highcentrality.htdhirp.core

/**
 * The radio's memory as CHIRP lays it out in a `.img` (see [Uv5rmMemory]).
 * Wraps the byte array rather than copying it, so edits stay in place.
 * Slots are zero-based; CHIRP's "location" N is slot N-1.
 */
class RadioImage(val bytes: ByteArray) {
    init {
        require(bytes.size >= CHANNEL_COUNT * ChannelCodec.RECORD_SIZE) { "image too small: ${bytes.size} bytes" }
    }

    fun isEmpty(slot: Int): Boolean = ChannelCodec.isEmpty(bytes, offset(slot))

    /** Null for an empty slot; throws if the record is corrupt. */
    fun channel(slot: Int): Channel? = if (isEmpty(slot)) null else ChannelCodec.decode(bytes, offset(slot))

    fun setChannel(slot: Int, ch: Channel) = ChannelCodec.encodeInto(bytes, offset(slot), ch)

    fun clearChannel(slot: Int) = ChannelCodec.clear(bytes, offset(slot))

    fun emptySlots(): List<Int> = (0 until CHANNEL_COUNT).filter { isEmpty(it) }

    fun usedSlots(): List<Int> = (0 until CHANNEL_COUNT).filterNot { isEmpty(it) }

    private fun offset(slot: Int): Int {
        require(slot in 0 until CHANNEL_COUNT) { "slot $slot out of range" }
        return slot * ChannelCodec.RECORD_SIZE
    }

    companion object {
        const val CHANNEL_COUNT = Uv5rmMemory.CHANNEL_COUNT
    }
}
