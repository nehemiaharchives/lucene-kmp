package org.gnit.lucenekmp.codecs.lucene101

import org.gnit.lucenekmp.codecs.lucene101.ForUtil.Companion.BLOCK_SIZE
import org.gnit.lucenekmp.codecs.lucene101.ForUtil.Companion.MASK16_1
import org.gnit.lucenekmp.codecs.lucene101.ForUtil.Companion.MASK16_2
import org.gnit.lucenekmp.codecs.lucene101.ForUtil.Companion.MASK16_4
import org.gnit.lucenekmp.codecs.lucene101.ForUtil.Companion.MASK16_5
import org.gnit.lucenekmp.codecs.lucene101.ForUtil.Companion.MASK16_6
import org.gnit.lucenekmp.codecs.lucene101.ForUtil.Companion.MASK16_7
import org.gnit.lucenekmp.codecs.lucene101.ForUtil.Companion.MASK16_8
import org.gnit.lucenekmp.codecs.lucene101.ForUtil.Companion.MASK32_1
import org.gnit.lucenekmp.codecs.lucene101.ForUtil.Companion.MASK32_2
import org.gnit.lucenekmp.codecs.lucene101.ForUtil.Companion.MASK32_3
import org.gnit.lucenekmp.codecs.lucene101.ForUtil.Companion.MASK32_4
import org.gnit.lucenekmp.codecs.lucene101.ForUtil.Companion.MASK32_5
import org.gnit.lucenekmp.codecs.lucene101.ForUtil.Companion.MASK32_6
import org.gnit.lucenekmp.codecs.lucene101.ForUtil.Companion.MASK32_7
import org.gnit.lucenekmp.codecs.lucene101.ForUtil.Companion.MASK32_8
import org.gnit.lucenekmp.codecs.lucene101.ForUtil.Companion.MASK32_9
import org.gnit.lucenekmp.codecs.lucene101.ForUtil.Companion.MASK32_10
import org.gnit.lucenekmp.codecs.lucene101.ForUtil.Companion.MASK32_11
import org.gnit.lucenekmp.codecs.lucene101.ForUtil.Companion.MASK32_12
import org.gnit.lucenekmp.codecs.lucene101.ForUtil.Companion.MASK32_13
import org.gnit.lucenekmp.codecs.lucene101.ForUtil.Companion.MASK32_14
import org.gnit.lucenekmp.codecs.lucene101.ForUtil.Companion.MASK32_15
import org.gnit.lucenekmp.codecs.lucene101.ForUtil.Companion.MASK32_16
import org.gnit.lucenekmp.codecs.lucene101.ForUtil.Companion.collapse16
import org.gnit.lucenekmp.codecs.lucene101.ForUtil.Companion.collapse8
import org.gnit.lucenekmp.codecs.lucene101.ForUtil.Companion.encode
import org.gnit.lucenekmp.codecs.lucene101.ForUtil.Companion.decode1
import org.gnit.lucenekmp.codecs.lucene101.ForUtil.Companion.decode2
import org.gnit.lucenekmp.codecs.lucene101.ForUtil.Companion.decode3
import org.gnit.lucenekmp.codecs.lucene101.ForUtil.Companion.decode9
import org.gnit.lucenekmp.codecs.lucene101.ForUtil.Companion.decodeSlow
import org.gnit.lucenekmp.codecs.lucene101.ForUtil.Companion.decode10
import org.gnit.lucenekmp.codecs.lucene101.ForUtil.Companion.expand8
import org.gnit.lucenekmp.codecs.lucene101.ForUtil.Companion.expand16
import org.gnit.lucenekmp.internal.vectorization.PostingDecodingUtil
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.util.packed.PackedInts
import okio.IOException

/**
 * Inspired from https://fulmicoton.com/posts/bitpacking/ Encodes multiple integers in a Java int to
 * get SIMD-like speedups. If bitsPerValue &lt;= 4 then we pack 4 ints per Java int else if
 * bitsPerValue &lt;= 11 we pack 2 ints per Java int else we use scalar operations.
 */
class ForDeltaUtil {
    private val tmp = IntArray(BLOCK_SIZE)

    /**
     * Return the number of bits per value required to store the given array containing strictly
     * positive numbers.
     */
    fun bitsRequired(ints: IntArray): Int {
        var or = 0
        for (l in ints) {
            or = or or l
        }
        // Deltas should be strictly positive since the delta between consecutive doc IDs is at least 1
        require(or != 0)
        return PackedInts.bitsRequired(or.toLong())
    }

    /**
     * Encode deltas of a strictly monotonically increasing sequence of integers. The provided `ints` are expected to be deltas between consecutive values.
     */
    @Throws(IOException::class)
    fun encodeDeltas(bitsPerValue: Int, ints: IntArray, out: DataOutput) {
        val primitiveSize: Int
        if (bitsPerValue <= 3) {
            primitiveSize = 8
            collapse8(ints)
        } else if (bitsPerValue <= 10) {
            primitiveSize = 16
            collapse16(ints)
        } else {
            primitiveSize = 32
        }
        encode(ints, bitsPerValue, primitiveSize, out, tmp)
    }

    /** Delta-decode 128 integers into `ints`.  */
    @Throws(IOException::class)
    fun decodeAndPrefixSum(bitsPerValue: Int, pdu: PostingDecodingUtil, base: Int, ints: IntArray) {
        when (bitsPerValue) {
            1 -> {
                decode1(pdu, ints)
                prefixSum8(ints, base)
            }

            2 -> {
                decode2(pdu, ints)
                prefixSum8(ints, base)
            }

            3 -> {
                decode3(pdu, tmp, ints)
                prefixSum8(ints, base)
            }

            4 -> {
                decode4To16(pdu, ints)
                prefixSum16(ints, base)
            }

            5 -> {
                decode5To16(pdu, tmp, ints)
                prefixSum16(ints, base)
            }

            6 -> {
                decode6To16(pdu, tmp, ints)
                prefixSum16(ints, base)
            }

            7 -> {
                decode7To16(pdu, tmp, ints)
                prefixSum16(ints, base)
            }

            8 -> {
                decode8To16(pdu, ints)
                prefixSum16(ints, base)
            }

            9 -> {
                decode9(pdu, tmp, ints)
                prefixSum16(ints, base)
            }

            10 -> {
                decode10(pdu, tmp, ints)
                prefixSum16(ints, base)
            }

            11 -> {
                decode11To32(pdu, tmp, ints)
                prefixSum32(ints, base)
            }

            12 -> {
                decode12To32(pdu, tmp, ints)
                prefixSum32(ints, base)
            }

            13 -> {
                decode13To32(pdu, tmp, ints)
                prefixSum32(ints, base)
            }

            14 -> {
                decode14To32(pdu, tmp, ints)
                prefixSum32(ints, base)
            }

            15 -> {
                decode15To32(pdu, tmp, ints)
                prefixSum32(ints, base)
            }

            16 -> {
                decode16To32(pdu, ints)
                prefixSum32(ints, base)
            }

            else -> {
                check(!(bitsPerValue < 1 || bitsPerValue > Int.SIZE_BITS)) { "Illegal number of bits per value: $bitsPerValue" }
                decodeSlow(bitsPerValue, pdu, tmp, ints)
                prefixSum32(ints, base)
            }
        }
    }

    companion object {
        private const val HALF_BLOCK_SIZE: Int = BLOCK_SIZE / 2
        private const val ONE_BLOCK_SIZE_FOURTH: Int = BLOCK_SIZE / 4
        private const val TWO_BLOCK_SIZE_FOURTHS: Int = BLOCK_SIZE / 2
        private const val THREE_BLOCK_SIZE_FOURTHS: Int = 3 * BLOCK_SIZE / 4

        private fun prefixSum8(arr: IntArray, base: Int) {
            // When the number of bits per value is 4 or less, we can sum up all values in a block without
            // risking overflowing an 8-bits integer. This allows computing the prefix sum by summing up 4
            // values at once.
            innerPrefixSum8(arr)
            expand8(arr)
            val l0 = base
            val l1 = l0 + arr[ONE_BLOCK_SIZE_FOURTH - 1]
            val l2 = l1 + arr[TWO_BLOCK_SIZE_FOURTHS - 1]
            val l3 = l2 + arr[THREE_BLOCK_SIZE_FOURTHS - 1]

            for (i in 0..<ONE_BLOCK_SIZE_FOURTH) {
                arr[i] += l0
                arr[ONE_BLOCK_SIZE_FOURTH + i] += l1
                arr[TWO_BLOCK_SIZE_FOURTHS + i] += l2
                arr[THREE_BLOCK_SIZE_FOURTHS + i] += l3
            }
        }

        private fun prefixSum16(arr: IntArray, base: Int) {
            // When the number of bits per value is 11 or less, we can sum up all values in a block without
            // risking overflowing an 16-bits integer. This allows computing the prefix sum by summing up 2
            // values at once.
            innerPrefixSum16(arr)
            expand16(arr)
            val l0 = base
            val l1 = base + arr[HALF_BLOCK_SIZE - 1]
            for (i in 0..<HALF_BLOCK_SIZE) {
                arr[i] += l0
                arr[HALF_BLOCK_SIZE + i] += l1
            }
        }

        private fun prefixSum32(arr: IntArray, base: Int) {
            arr[0] += base
            for (i in 1..<BLOCK_SIZE) {
                arr[i] += arr[i - 1]
            }
        }

        // For some reason unrolling seems to help
        private fun innerPrefixSum8(arr: IntArray) {
            arr[1] += arr[0]
            arr[2] += arr[1]
            arr[3] += arr[2]
            arr[4] += arr[3]
            arr[5] += arr[4]
            arr[6] += arr[5]
            arr[7] += arr[6]
            arr[8] += arr[7]
            arr[9] += arr[8]
            arr[10] += arr[9]
            arr[11] += arr[10]
            arr[12] += arr[11]
            arr[13] += arr[12]
            arr[14] += arr[13]
            arr[15] += arr[14]
            arr[16] += arr[15]
            arr[17] += arr[16]
            arr[18] += arr[17]
            arr[19] += arr[18]
            arr[20] += arr[19]
            arr[21] += arr[20]
            arr[22] += arr[21]
            arr[23] += arr[22]
            arr[24] += arr[23]
            arr[25] += arr[24]
            arr[26] += arr[25]
            arr[27] += arr[26]
            arr[28] += arr[27]
            arr[29] += arr[28]
            arr[30] += arr[29]
            arr[31] += arr[30]
        }

        // For some reason unrolling seems to help
        private fun innerPrefixSum16(arr: IntArray) {
            arr[1] += arr[0]
            arr[2] += arr[1]
            arr[3] += arr[2]
            arr[4] += arr[3]
            arr[5] += arr[4]
            arr[6] += arr[5]
            arr[7] += arr[6]
            arr[8] += arr[7]
            arr[9] += arr[8]
            arr[10] += arr[9]
            arr[11] += arr[10]
            arr[12] += arr[11]
            arr[13] += arr[12]
            arr[14] += arr[13]
            arr[15] += arr[14]
            arr[16] += arr[15]
            arr[17] += arr[16]
            arr[18] += arr[17]
            arr[19] += arr[18]
            arr[20] += arr[19]
            arr[21] += arr[20]
            arr[22] += arr[21]
            arr[23] += arr[22]
            arr[24] += arr[23]
            arr[25] += arr[24]
            arr[26] += arr[25]
            arr[27] += arr[26]
            arr[28] += arr[27]
            arr[29] += arr[28]
            arr[30] += arr[29]
            arr[31] += arr[30]
            arr[32] += arr[31]
            arr[33] += arr[32]
            arr[34] += arr[33]
            arr[35] += arr[34]
            arr[36] += arr[35]
            arr[37] += arr[36]
            arr[38] += arr[37]
            arr[39] += arr[38]
            arr[40] += arr[39]
            arr[41] += arr[40]
            arr[42] += arr[41]
            arr[43] += arr[42]
            arr[44] += arr[43]
            arr[45] += arr[44]
            arr[46] += arr[45]
            arr[47] += arr[46]
            arr[48] += arr[47]
            arr[49] += arr[48]
            arr[50] += arr[49]
            arr[51] += arr[50]
            arr[52] += arr[51]
            arr[53] += arr[52]
            arr[54] += arr[53]
            arr[55] += arr[54]
            arr[56] += arr[55]
            arr[57] += arr[56]
            arr[58] += arr[57]
            arr[59] += arr[58]
            arr[60] += arr[59]
            arr[61] += arr[60]
            arr[62] += arr[61]
            arr[63] += arr[62]
        }

        @Throws(IOException::class)
        private fun decode4To16(pdu: PostingDecodingUtil, ints: IntArray) {
            pdu.splitInts(16, ints, 12, 4, MASK16_4, ints, 48, MASK16_4)
        }

        @Throws(IOException::class)
        private fun decode5To16(pdu: PostingDecodingUtil, tmp: IntArray, ints: IntArray) {
            pdu.splitInts(20, ints, 11, 5, MASK16_5, tmp, 0, MASK16_1)
            var iter = 0
            var tmpIdx = 0
            var intsIdx = 60
            while (iter < 4) {
                var l0 = tmp[tmpIdx + 0] shl 4
                l0 = l0 or (tmp[tmpIdx + 1] shl 3)
                l0 = l0 or (tmp[tmpIdx + 2] shl 2)
                l0 = l0 or (tmp[tmpIdx + 3] shl 1)
                l0 = l0 or (tmp[tmpIdx + 4] shl 0)
                ints[intsIdx + 0] = l0
                ++iter
                tmpIdx += 5
                intsIdx += 1
            }
        }

        @Throws(IOException::class)
        private fun decode6To16(pdu: PostingDecodingUtil, tmp: IntArray, ints: IntArray) {
            pdu.splitInts(24, ints, 10, 6, MASK16_6, tmp, 0, MASK16_4)
            var iter = 0
            var tmpIdx = 0
            var intsIdx = 48
            while (iter < 8) {
                var l0 = tmp[tmpIdx + 0] shl 2
                l0 = l0 or ((tmp[tmpIdx + 1] ushr 2) and MASK16_2)
                ints[intsIdx + 0] = l0
                var l1 = (tmp[tmpIdx + 1] and MASK16_2) shl 4
                l1 = l1 or (tmp[tmpIdx + 2] shl 0)
                ints[intsIdx + 1] = l1
                ++iter
                tmpIdx += 3
                intsIdx += 2
            }
        }

        @Throws(IOException::class)
        private fun decode7To16(pdu: PostingDecodingUtil, tmp: IntArray, ints: IntArray) {
            pdu.splitInts(28, ints, 9, 7, MASK16_7, tmp, 0, MASK16_2)
            var iter = 0
            var tmpIdx = 0
            var intsIdx = 56
            while (iter < 4) {
                var l0 = tmp[tmpIdx + 0] shl 5
                l0 = l0 or (tmp[tmpIdx + 1] shl 3)
                l0 = l0 or (tmp[tmpIdx + 2] shl 1)
                l0 = l0 or ((tmp[tmpIdx + 3] ushr 1) and MASK16_1)
                ints[intsIdx + 0] = l0
                var l1 = (tmp[tmpIdx + 3] and MASK16_1) shl 6
                l1 = l1 or (tmp[tmpIdx + 4] shl 4)
                l1 = l1 or (tmp[tmpIdx + 5] shl 2)
                l1 = l1 or (tmp[tmpIdx + 6] shl 0)
                ints[intsIdx + 1] = l1
                ++iter
                tmpIdx += 7
                intsIdx += 2
            }
        }

        @Throws(IOException::class)
        private fun decode8To16(pdu: PostingDecodingUtil, ints: IntArray) {
            pdu.splitInts(32, ints, 8, 8, MASK16_8, ints, 32, MASK16_8)
        }

        @Throws(IOException::class)
        private fun decode11To32(pdu: PostingDecodingUtil, tmp: IntArray, ints: IntArray) {
            pdu.splitInts(44, ints, 21, 11, MASK32_11, tmp, 0, MASK32_10)
            var iter = 0
            var tmpIdx = 0
            var intsIdx = 88
            while (iter < 4) {
                var l0 = tmp[tmpIdx + 0] shl 1
                l0 = l0 or ((tmp[tmpIdx + 1] ushr 9) and MASK32_1)
                ints[intsIdx + 0] = l0
                var l1 = (tmp[tmpIdx + 1] and MASK32_9) shl 2
                l1 = l1 or ((tmp[tmpIdx + 2] ushr 8) and MASK32_2)
                ints[intsIdx + 1] = l1
                var l2 = (tmp[tmpIdx + 2] and MASK32_8) shl 3
                l2 = l2 or ((tmp[tmpIdx + 3] ushr 7) and MASK32_3)
                ints[intsIdx + 2] = l2
                var l3 = (tmp[tmpIdx + 3] and MASK32_7) shl 4
                l3 = l3 or ((tmp[tmpIdx + 4] ushr 6) and MASK32_4)
                ints[intsIdx + 3] = l3
                var l4 = (tmp[tmpIdx + 4] and MASK32_6) shl 5
                l4 = l4 or ((tmp[tmpIdx + 5] ushr 5) and MASK32_5)
                ints[intsIdx + 4] = l4
                var l5 = (tmp[tmpIdx + 5] and MASK32_5) shl 6
                l5 = l5 or ((tmp[tmpIdx + 6] ushr 4) and MASK32_6)
                ints[intsIdx + 5] = l5
                var l6 = (tmp[tmpIdx + 6] and MASK32_4) shl 7
                l6 = l6 or ((tmp[tmpIdx + 7] ushr 3) and MASK32_7)
                ints[intsIdx + 6] = l6
                var l7 = (tmp[tmpIdx + 7] and MASK32_3) shl 8
                l7 = l7 or ((tmp[tmpIdx + 8] ushr 2) and MASK32_8)
                ints[intsIdx + 7] = l7
                var l8 = (tmp[tmpIdx + 8] and MASK32_2) shl 9
                l8 = l8 or ((tmp[tmpIdx + 9] ushr 1) and MASK32_9)
                ints[intsIdx + 8] = l8
                var l9 = (tmp[tmpIdx + 9] and MASK32_1) shl 10
                l9 = l9 or (tmp[tmpIdx + 10] shl 0)
                ints[intsIdx + 9] = l9
                ++iter
                tmpIdx += 11
                intsIdx += 10
            }
        }

        @Throws(IOException::class)
        private fun decode12To32(pdu: PostingDecodingUtil, tmp: IntArray, ints: IntArray) {
            pdu.splitInts(48, ints, 20, 12, MASK32_12, tmp, 0, MASK32_8)
            var iter = 0
            var tmpIdx = 0
            var intsIdx = 96
            while (iter < 16) {
                var l0 = tmp[tmpIdx + 0] shl 4
                l0 = l0 or ((tmp[tmpIdx + 1] ushr 4) and MASK32_4)
                ints[intsIdx + 0] = l0
                var l1 = (tmp[tmpIdx + 1] and MASK32_4) shl 8
                l1 = l1 or (tmp[tmpIdx + 2] shl 0)
                ints[intsIdx + 1] = l1
                ++iter
                tmpIdx += 3
                intsIdx += 2
            }
        }

        @Throws(IOException::class)
        private fun decode13To32(pdu: PostingDecodingUtil, tmp: IntArray, ints: IntArray) {
            pdu.splitInts(52, ints, 19, 13, MASK32_13, tmp, 0, MASK32_6)
            var iter = 0
            var tmpIdx = 0
            var intsIdx = 104
            while (iter < 4) {
                var l0 = tmp[tmpIdx + 0] shl 7
                l0 = l0 or (tmp[tmpIdx + 1] shl 1)
                l0 = l0 or ((tmp[tmpIdx + 2] ushr 5) and MASK32_1)
                ints[intsIdx + 0] = l0
                var l1 = (tmp[tmpIdx + 2] and MASK32_5) shl 8
                l1 = l1 or (tmp[tmpIdx + 3] shl 2)
                l1 = l1 or ((tmp[tmpIdx + 4] ushr 4) and MASK32_2)
                ints[intsIdx + 1] = l1
                var l2 = (tmp[tmpIdx + 4] and MASK32_4) shl 9
                l2 = l2 or (tmp[tmpIdx + 5] shl 3)
                l2 = l2 or ((tmp[tmpIdx + 6] ushr 3) and MASK32_3)
                ints[intsIdx + 2] = l2
                var l3 = (tmp[tmpIdx + 6] and MASK32_3) shl 10
                l3 = l3 or (tmp[tmpIdx + 7] shl 4)
                l3 = l3 or ((tmp[tmpIdx + 8] ushr 2) and MASK32_4)
                ints[intsIdx + 3] = l3
                var l4 = (tmp[tmpIdx + 8] and MASK32_2) shl 11
                l4 = l4 or (tmp[tmpIdx + 9] shl 5)
                l4 = l4 or ((tmp[tmpIdx + 10] ushr 1) and MASK32_5)
                ints[intsIdx + 4] = l4
                var l5 = (tmp[tmpIdx + 10] and MASK32_1) shl 12
                l5 = l5 or (tmp[tmpIdx + 11] shl 6)
                l5 = l5 or (tmp[tmpIdx + 12] shl 0)
                ints[intsIdx + 5] = l5
                ++iter
                tmpIdx += 13
                intsIdx += 6
            }
        }

        @Throws(IOException::class)
        private fun decode14To32(pdu: PostingDecodingUtil, tmp: IntArray, ints: IntArray) {
            pdu.splitInts(56, ints, 18, 14, MASK32_14, tmp, 0, MASK32_4)
            var iter = 0
            var tmpIdx = 0
            var intsIdx = 112
            while (iter < 8) {
                var l0 = tmp[tmpIdx + 0] shl 10
                l0 = l0 or (tmp[tmpIdx + 1] shl 6)
                l0 = l0 or (tmp[tmpIdx + 2] shl 2)
                l0 = l0 or ((tmp[tmpIdx + 3] ushr 2) and MASK32_2)
                ints[intsIdx + 0] = l0
                var l1 = (tmp[tmpIdx + 3] and MASK32_2) shl 12
                l1 = l1 or (tmp[tmpIdx + 4] shl 8)
                l1 = l1 or (tmp[tmpIdx + 5] shl 4)
                l1 = l1 or (tmp[tmpIdx + 6] shl 0)
                ints[intsIdx + 1] = l1
                ++iter
                tmpIdx += 7
                intsIdx += 2
            }
        }

        @Throws(IOException::class)
        private fun decode15To32(pdu: PostingDecodingUtil, tmp: IntArray, ints: IntArray) {
            pdu.splitInts(60, ints, 17, 15, MASK32_15, tmp, 0, MASK32_2)
            var iter = 0
            var tmpIdx = 0
            var intsIdx = 120
            while (iter < 4) {
                var l0 = tmp[tmpIdx + 0] shl 13
                l0 = l0 or (tmp[tmpIdx + 1] shl 11)
                l0 = l0 or (tmp[tmpIdx + 2] shl 9)
                l0 = l0 or (tmp[tmpIdx + 3] shl 7)
                l0 = l0 or (tmp[tmpIdx + 4] shl 5)
                l0 = l0 or (tmp[tmpIdx + 5] shl 3)
                l0 = l0 or (tmp[tmpIdx + 6] shl 1)
                l0 = l0 or ((tmp[tmpIdx + 7] ushr 1) and MASK32_1)
                ints[intsIdx + 0] = l0
                var l1 = (tmp[tmpIdx + 7] and MASK32_1) shl 14
                l1 = l1 or (tmp[tmpIdx + 8] shl 12)
                l1 = l1 or (tmp[tmpIdx + 9] shl 10)
                l1 = l1 or (tmp[tmpIdx + 10] shl 8)
                l1 = l1 or (tmp[tmpIdx + 11] shl 6)
                l1 = l1 or (tmp[tmpIdx + 12] shl 4)
                l1 = l1 or (tmp[tmpIdx + 13] shl 2)
                l1 = l1 or (tmp[tmpIdx + 14] shl 0)
                ints[intsIdx + 1] = l1
                ++iter
                tmpIdx += 15
                intsIdx += 2
            }
        }

        @Throws(IOException::class)
        private fun decode16To32(pdu: PostingDecodingUtil, ints: IntArray) {
            pdu.splitInts(64, ints, 16, 16, MASK32_16, ints, 64, MASK32_16)
        }
    }
}
