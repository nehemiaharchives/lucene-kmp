package org.gnit.lucenekmp.codecs.lucene90.blocktree

import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef
//import java.io.PrintStream

/**
 * BlockTree statistics for a single field returned by [FieldReader.getStats].
 *
 * @lucene.internal
 */
class Stats internal constructor(
    /** Segment name.  */
    val segment: String,
    /** Field name.  */
    val field: String
) {
    /** Byte size of the index.  */
    var indexNumBytes: Long = 0

    /** Total number of terms in the field.  */
    var totalTermCount: Long = 0

    /** Total number of bytes (sum of term lengths) across all terms in the field.  */
    var totalTermBytes: Long = 0

    /** The number of normal (non-floor) blocks in the terms file.  */
    var nonFloorBlockCount: Int = 0

    /**
     * The number of floor blocks (meta-blocks larger than the allowed `maxItemsPerBlock`) in
     * the terms file.
     */
    var floorBlockCount: Int = 0

    /** The number of sub-blocks within the floor blocks.  */
    var floorSubBlockCount: Int = 0

    /** The number of "internal" blocks (that have both terms and sub-blocks).  */
    var mixedBlockCount: Int = 0

    /** The number of "leaf" blocks (blocks that have only terms).  */
    var termsOnlyBlockCount: Int = 0

    /** The number of "internal" blocks that do not contain terms (have only sub-blocks).  */
    var subBlocksOnlyBlockCount: Int = 0

    /** Total number of blocks.  */
    var totalBlockCount: Int = 0

    /** Number of blocks at each prefix depth.  */
    var blockCountByPrefixLen: IntArray = IntArray(10)

    private var startBlockCount = 0
    private var endBlockCount = 0

    /** Total number of bytes used to store term suffixes.  */
    var totalBlockSuffixBytes: Long = 0

    /**
     * Number of times each compression method has been used. 0 = uncompressed 1 = lowercase_ascii 2 =
     * LZ4
     */
    val compressionAlgorithms: LongArray = LongArray(3)

    /** Total number of suffix bytes before compression.  */
    var totalUncompressedBlockSuffixBytes: Long = 0

    /**
     * Total number of bytes used to store term stats (not including what the [ ] stores).
     */
    var totalBlockStatsBytes: Long = 0

    /**
     * Total bytes stored by the [PostingsReaderBase], plus the other few vInts stored in the
     * frame.
     */
    var totalBlockOtherBytes: Long = 0

    fun startBlock(frame: SegmentTermsEnumFrame, isFloor: Boolean) {
        totalBlockCount++
        if (isFloor) {
            if (frame.fp == frame.fpOrig) {
                floorBlockCount++
            }
            floorSubBlockCount++
        } else {
            nonFloorBlockCount++
        }

        if (blockCountByPrefixLen.size <= frame.prefixLength) {
            blockCountByPrefixLen = ArrayUtil.grow(blockCountByPrefixLen, 1 + frame.prefixLength)
        }
        blockCountByPrefixLen[frame.prefixLength]++
        startBlockCount++
        totalBlockSuffixBytes += frame.totalSuffixBytes
        totalUncompressedBlockSuffixBytes += frame.suffixesReader.length()
        if (frame.suffixesReader !== frame.suffixLengthsReader) {
            totalUncompressedBlockSuffixBytes += frame.suffixLengthsReader.length()
        }
        totalBlockStatsBytes += frame.statsReader.length()
        compressionAlgorithms[frame.compressionAlg.code]++
    }

    fun endBlock(frame: SegmentTermsEnumFrame) {
        val termCount: Int = if (frame.isLeafBlock) frame.entCount else frame.state.termBlockOrd
        val subBlockCount: Int = frame.entCount - termCount
        totalTermCount += termCount.toLong()
        if (termCount != 0 && subBlockCount != 0) {
            mixedBlockCount++
        } else if (termCount != 0) {
            termsOnlyBlockCount++
        } else if (subBlockCount != 0) {
            subBlocksOnlyBlockCount++
        } else {
            throw IllegalStateException()
        }
        endBlockCount++
        val otherBytes: Long =
            frame.fpEnd - frame.fp - frame.totalSuffixBytes - frame.statsReader.length()
        require(
            otherBytes > 0
        ) { "otherBytes=" + otherBytes + " frame.fp=" + frame.fp + " frame.fpEnd=" + frame.fpEnd }
        totalBlockOtherBytes += otherBytes
    }

    fun term(term: BytesRef) {
        totalTermBytes += term.length
    }

    fun finish() {
        require(
            startBlockCount == endBlockCount
        ) { "startBlockCount=$startBlockCount endBlockCount=$endBlockCount" }
        require(
            totalBlockCount == floorSubBlockCount + nonFloorBlockCount
        ) {
            ("floorSubBlockCount="
                    + floorSubBlockCount
                    + " nonFloorBlockCount="
                    + nonFloorBlockCount
                    + " totalBlockCount="
                    + totalBlockCount)
        }
        require(
            totalBlockCount == mixedBlockCount + termsOnlyBlockCount + subBlocksOnlyBlockCount
        ) {
            ("totalBlockCount="
                    + totalBlockCount
                    + " mixedBlockCount="
                    + mixedBlockCount
                    + " subBlocksOnlyBlockCount="
                    + subBlocksOnlyBlockCount
                    + " termsOnlyBlockCount="
                    + termsOnlyBlockCount)
        }
    }

    private class PrintStream(initialCap: Int = 1024) {
        private val sb = StringBuilder(initialCap)

        fun println(obj: Any? = "") {
            sb.append(obj).appendLine()   // appendLine is multiplatform
        }
        override fun toString(): String = sb.toString()
    }

    override fun toString(): String {
        val out = PrintStream()

        out.println("  index FST:")
        out.println("    $indexNumBytes bytes")
        out.println("  terms:")
        out.println("    $totalTermCount terms")
        out.println("    $totalTermBytes bytes${if (totalTermCount != 0L) " (${(kotlin.math.floor(totalTermBytes.toDouble() / totalTermCount * 10) / 10)} bytes/term)" else ""}")
        out.println("  blocks:")
        out.println("    $totalBlockCount blocks")
        out.println("    $termsOnlyBlockCount terms-only blocks")
        out.println("    $subBlocksOnlyBlockCount sub-block-only blocks")
        out.println("    $mixedBlockCount mixed blocks")
        out.println("    $floorBlockCount floor blocks")
        out.println("    " + (totalBlockCount - floorSubBlockCount) + " non-floor blocks")
        out.println("    $floorSubBlockCount floor sub-blocks")
       out.println("    $totalUncompressedBlockSuffixBytes term suffix bytes before compression${if (totalBlockCount != 0) " (${(kotlin.math.round(totalBlockSuffixBytes.toDouble() / totalBlockCount * 10) / 10)} suffix-bytes/block)" else ""}")
        val compressionCounts = StringBuilder()
        for (code in compressionAlgorithms.indices) {
            if (compressionAlgorithms[code] == 0L) {
                continue
            }
            if (compressionCounts.isNotEmpty()) {
                compressionCounts.append(", ")
            }
            compressionCounts.append(CompressionAlgorithm.byCode(code))
            compressionCounts.append(": ")
            compressionCounts.append(compressionAlgorithms[code])
        }
        out.println("    $totalBlockSuffixBytes compressed term suffix bytes${if (totalBlockCount != 0) " (${kotlin.math.round((totalBlockSuffixBytes.toDouble() / totalUncompressedBlockSuffixBytes) * 100) / 100} compression ratio - compression count by algorithm: $compressionCounts" else ""})")
        out.println("    $totalBlockStatsBytes term stats bytes " +
            if (totalBlockCount != 0)
                " (${(kotlin.math.round(totalBlockStatsBytes.toDouble() / totalBlockCount * 10) / 10)} stats-bytes/block)"
            else ""
        )
        out.println(
            "    $totalBlockOtherBytes other bytes" +
            if (totalBlockCount != 0)
                " (${kotlin.math.round(totalBlockOtherBytes.toDouble() / totalBlockCount * 10) / 10} other-bytes/block)"
            else ""
        )
        if (totalBlockCount != 0) {
            out.println("    by prefix length:")
            var total = 0
            for (prefix in blockCountByPrefixLen.indices) {
                val blockCount = blockCountByPrefixLen[prefix]
                total += blockCount
                if (blockCount != 0) {
                    out.println("      ${prefix.toString().padStart(2)}: $blockCount")
                }
            }
            require(totalBlockCount == total)
        }

        return out.toString()
    }
}
