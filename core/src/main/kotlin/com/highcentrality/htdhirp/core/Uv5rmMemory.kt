package com.highcentrality.htdhirp.core

/**
 * Memory map of the Baofeng UV-5RM (CHIRP `BF5RM(UV17Pro)`).
 *
 * The radio's address space is sparse. CHIRP reads four regions and packs them
 * back to back into a 0x8380-byte image; [regions] gives that mapping.
 *
 * Deliberately excluded: the 0xF000 sector (calibration, region byte, etc.).
 * This library never reads or writes it.
 */
object Uv5rmMemory {
    const val BLOCK_SIZE = 0x40
    const val CHANNEL_COUNT = 999
    const val IMAGE_SIZE = 0x8380

    /** A contiguous span of radio addresses, stored at [imageOffset] in the packed image. */
    data class Region(val radioAddr: Int, val size: Int, val imageOffset: Int)

    val regions: List<Region> = run {
        val spans = listOf(0x0000 to 0x8040, 0x9000 to 0x0040, 0xA000 to 0x02C0, 0xD000 to 0x0040)
        var offset = 0
        spans.map { (addr, size) -> Region(addr, size, offset).also { offset += size } }
    }

    init {
        check(regions.last().let { it.imageOffset + it.size } == IMAGE_SIZE) { "region table does not sum to IMAGE_SIZE" }
    }
}
