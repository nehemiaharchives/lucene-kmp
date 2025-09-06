package org.gnit.lucenekmp.codecs.lucene101

import okio.IOException
import org.gnit.lucenekmp.internal.vectorization.PostingDecodingUtil
import org.gnit.lucenekmp.store.DataOutput


/**
 * Inspired from https://fulmicoton.com/posts/bitpacking/ Encodes multiple integers in one to get
 * SIMD-like speedups. If bitsPerValue &lt;= 8 then we pack 4 ints per Java int else if bitsPerValue
 * &lt;= 16 we pack 2 ints per Java int else we do scalar operations.
 */
class ForUtil {
    private val tmp = IntArray(BLOCK_SIZE)

    /** Encode 128 integers from `ints` into `out`.  */
    @Throws(IOException::class)
    fun encode(ints: IntArray, bitsPerValue: Int, out: DataOutput) {
        val nextPrimitive: Int
        if (bitsPerValue <= 8) {
            nextPrimitive = 8
            collapse8(ints)
        } else if (bitsPerValue <= 16) {
            nextPrimitive = 16
            collapse16(ints)
        } else {
            nextPrimitive = 32
        }
        encode(ints, bitsPerValue, nextPrimitive, out, tmp)
    }

    /** Decode 128 integers into `ints`.  */
    @Throws(IOException::class)
    fun decode(bitsPerValue: Int, pdu: PostingDecodingUtil, ints: IntArray) {
        when (bitsPerValue) {
            1 -> {
                decode1(pdu, ints)
                expand8(ints)
            }

            2 -> {
                decode2(pdu, ints)
                expand8(ints)
            }

            3 -> {
                decode3(pdu, tmp, ints)
                expand8(ints)
            }

            4 -> {
                decode4(pdu, ints)
                expand8(ints)
            }

            5 -> {
                decode5(pdu, tmp, ints)
                expand8(ints)
            }

            6 -> {
                decode6(pdu, tmp, ints)
                expand8(ints)
            }

            7 -> {
                decode7(pdu, tmp, ints)
                expand8(ints)
            }

            8 -> {
                decode8(pdu, ints)
                expand8(ints)
            }

            9 -> {
                decode9(pdu, tmp, ints)
                expand16(ints)
            }

            10 -> {
                decode10(pdu, tmp, ints)
                expand16(ints)
            }

            11 -> {
                decode11(pdu, tmp, ints)
                expand16(ints)
            }

            12 -> {
                decode12(pdu, tmp, ints)
                expand16(ints)
            }

            13 -> {
                decode13(pdu, tmp, ints)
                expand16(ints)
            }

            14 -> {
                decode14(pdu, tmp, ints)
                expand16(ints)
            }

            15 -> {
                decode15(pdu, tmp, ints)
                expand16(ints)
            }

            16 -> {
                decode16(pdu, ints)
                expand16(ints)
            }

            else -> decodeSlow(bitsPerValue, pdu, tmp, ints)
        }
    }

    companion object {
        const val BLOCK_SIZE: Int = 128
        const val BLOCK_SIZE_LOG2: Int = 7

        fun expandMask16(mask16: Int): Int {
            return mask16 or (mask16 shl 16)
        }

        fun expandMask8(mask8: Int): Int {
            return expandMask16(mask8 or (mask8 shl 8))
        }

        fun mask32(bitsPerValue: Int): Int {
            return (1 shl bitsPerValue) - 1
        }

        fun mask16(bitsPerValue: Int): Int {
            return expandMask16((1 shl bitsPerValue) - 1)
        }

        fun mask8(bitsPerValue: Int): Int {
            return expandMask8((1 shl bitsPerValue) - 1)
        }

        fun expand8(arr: IntArray) {
            for (i in 0..31) {
                val l = arr[i]
                arr[i] = (l ushr 24) and 0xFF
                arr[32 + i] = (l ushr 16) and 0xFF
                arr[64 + i] = (l ushr 8) and 0xFF
                arr[96 + i] = l and 0xFF
            }
        }

        fun collapse8(arr: IntArray) {
            for (i in 0..31) {
                arr[i] = (arr[i] shl 24) or (arr[32 + i] shl 16) or (arr[64 + i] shl 8) or arr[96 + i]
            }
        }

        fun expand16(arr: IntArray) {
            for (i in 0..63) {
                val l = arr[i]
                arr[i] = (l ushr 16) and 0xFFFF
                arr[64 + i] = l and 0xFFFF
            }
        }

        fun collapse16(arr: IntArray) {
            for (i in 0..63) {
                arr[i] = (arr[i] shl 16) or arr[64 + i]
            }
        }

        @Throws(IOException::class)
        fun encode(ints: IntArray, bitsPerValue: Int, primitiveSize: Int, out: DataOutput, tmp: IntArray) {
            val numInts: Int = BLOCK_SIZE * primitiveSize / Int.SIZE_BITS

            val numIntsPerShift = bitsPerValue * 4
            var idx = 0
            var shift = primitiveSize - bitsPerValue
            for (i in 0..<numIntsPerShift) {
                tmp[i] = ints[idx++] shl shift
            }
            shift = shift - bitsPerValue
            while (shift >= 0) {
                for (i in 0..<numIntsPerShift) {
                    tmp[i] = tmp[i] or (ints[idx++] shl shift)
                }
                shift -= bitsPerValue
            }

            val remainingBitsPerInt = shift + bitsPerValue
            val maskRemainingBitsPerInt: Int = if (primitiveSize == 8) {
                MASKS8[remainingBitsPerInt]
            } else if (primitiveSize == 16) {
                MASKS16[remainingBitsPerInt]
            } else {
                MASKS32[remainingBitsPerInt]
            }

            var tmpIdx = 0
            var remainingBitsPerValue = bitsPerValue
            while (idx < numInts) {
                if (remainingBitsPerValue >= remainingBitsPerInt) {
                    remainingBitsPerValue -= remainingBitsPerInt
                    tmp[tmpIdx] =
                        tmp[tmpIdx] or ((ints[idx] ushr remainingBitsPerValue) and maskRemainingBitsPerInt)
                    tmpIdx++
                    if (remainingBitsPerValue == 0) {
                        idx++
                        remainingBitsPerValue = bitsPerValue
                    }
                } else {
                    val mask1: Int
                    val mask2: Int
                    if (primitiveSize == 8) {
                        mask1 = MASKS8[remainingBitsPerValue]
                        mask2 = MASKS8[remainingBitsPerInt - remainingBitsPerValue]
                    } else if (primitiveSize == 16) {
                        mask1 = MASKS16[remainingBitsPerValue]
                        mask2 = MASKS16[remainingBitsPerInt - remainingBitsPerValue]
                    } else {
                        mask1 = MASKS32[remainingBitsPerValue]
                        mask2 = MASKS32[remainingBitsPerInt - remainingBitsPerValue]
                    }
                    tmp[tmpIdx] =
                        tmp[tmpIdx] or ((ints[idx++] and mask1) shl (remainingBitsPerInt - remainingBitsPerValue))
                    remainingBitsPerValue = bitsPerValue - remainingBitsPerInt + remainingBitsPerValue
                    tmp[tmpIdx] =
                        tmp[tmpIdx] or ((ints[idx] ushr remainingBitsPerValue) and mask2)
                    tmpIdx++
                }
            }

            for (i in 0..<numIntsPerShift) {
                out.writeInt(tmp[i])
            }
        }

        /** Number of bytes required to encode 128 integers of `bitsPerValue` bits per value.  */
        fun numBytes(bitsPerValue: Int): Int {
            return bitsPerValue shl (BLOCK_SIZE_LOG2 - 3)
        }

        @Throws(IOException::class)
        fun decodeSlow(bitsPerValue: Int, pdu: PostingDecodingUtil, tmp: IntArray, ints: IntArray) {
            val numInts = bitsPerValue shl 2
            val mask = MASKS32[bitsPerValue]
            pdu.splitInts(numInts, ints, 32 - bitsPerValue, 32, mask, tmp, 0, -1)
            val remainingBitsPerInt = 32 - bitsPerValue
            val mask32RemainingBitsPerInt = MASKS32[remainingBitsPerInt]
            var tmpIdx = 0
            var remainingBits = remainingBitsPerInt
            for (intsIdx in numInts..<BLOCK_SIZE) {
                var b = bitsPerValue - remainingBits
                var l = (tmp[tmpIdx++] and MASKS32[remainingBits]) shl b
                while (b >= remainingBitsPerInt) {
                    b -= remainingBitsPerInt
                    l = l or ((tmp[tmpIdx++] and mask32RemainingBitsPerInt) shl b)
                }
                if (b > 0) {
                    l = l or ((tmp[tmpIdx] ushr (remainingBitsPerInt - b)) and MASKS32[b])
                    remainingBits = remainingBitsPerInt - b
                } else {
                    remainingBits = remainingBitsPerInt
                }
                ints[intsIdx] = l
            }
        }

        val MASKS8: IntArray = IntArray(8)
        val MASKS16: IntArray = IntArray(16)
        val MASKS32: IntArray = IntArray(32)

        init {
            for (i in 0..7) {
                MASKS8[i] = mask8(i)
            }
            for (i in 0..15) {
                MASKS16[i] = mask16(i)
            }
            for (i in 0..31) {
                MASKS32[i] = mask32(i)
            }
        }

        // mark values in array as final ints to avoid the cost of reading array, arrays should only be
        // used when the idx is a variable
        val MASK8_1: Int = MASKS8[1]
        val MASK8_2: Int = MASKS8[2]
        val MASK8_3: Int = MASKS8[3]
        val MASK8_4: Int = MASKS8[4]
        val MASK8_5: Int = MASKS8[5]
        val MASK8_6: Int = MASKS8[6]
        val MASK8_7: Int = MASKS8[7]
        val MASK16_1: Int = MASKS16[1]
        val MASK16_2: Int = MASKS16[2]
        val MASK16_3: Int = MASKS16[3]
        val MASK16_4: Int = MASKS16[4]
        val MASK16_5: Int = MASKS16[5]
        val MASK16_6: Int = MASKS16[6]
        val MASK16_7: Int = MASKS16[7]
        val MASK16_8: Int = MASKS16[8]
        val MASK16_9: Int = MASKS16[9]
        val MASK16_10: Int = MASKS16[10]
        val MASK16_11: Int = MASKS16[11]
        val MASK16_12: Int = MASKS16[12]
        val MASK16_13: Int = MASKS16[13]
        val MASK16_14: Int = MASKS16[14]
        val MASK16_15: Int = MASKS16[15]
        val MASK32_1: Int = MASKS32[1]
        val MASK32_2: Int = MASKS32[2]
        val MASK32_3: Int = MASKS32[3]
        val MASK32_4: Int = MASKS32[4]
        val MASK32_5: Int = MASKS32[5]
        val MASK32_6: Int = MASKS32[6]
        val MASK32_7: Int = MASKS32[7]
        val MASK32_8: Int = MASKS32[8]
        val MASK32_9: Int = MASKS32[9]
        val MASK32_10: Int = MASKS32[10]
        val MASK32_11: Int = MASKS32[11]
        val MASK32_12: Int = MASKS32[12]
        val MASK32_13: Int = MASKS32[13]
        val MASK32_14: Int = MASKS32[14]
        val MASK32_15: Int = MASKS32[15]
        val MASK32_16: Int = MASKS32[16]

        @Throws(IOException::class)
        fun decode1(pdu: PostingDecodingUtil, ints: IntArray) {
            pdu.splitInts(4, ints, 7, 1, MASK8_1, ints, 28, MASK8_1)
        }

        @Throws(IOException::class)
        fun decode2(pdu: PostingDecodingUtil, ints: IntArray) {
            pdu.splitInts(8, ints, 6, 2, MASK8_2, ints, 24, MASK8_2)
        }

        @Throws(IOException::class)
        fun decode3(pdu: PostingDecodingUtil, tmp: IntArray, ints: IntArray) {
            pdu.splitInts(12, ints, 5, 3, MASK8_3, tmp, 0, MASK8_2)
            var iter = 0
            var tmpIdx = 0
            var intsIdx = 24
            while (iter < 4) {
                var l0 = tmp[tmpIdx + 0] shl 1
                l0 = l0 or ((tmp[tmpIdx + 1] ushr 1) and MASK8_1)
                ints[intsIdx + 0] = l0
                var l1 = (tmp[tmpIdx + 1] and MASK8_1) shl 2
                l1 = l1 or (tmp[tmpIdx + 2] shl 0)
                ints[intsIdx + 1] = l1
                ++iter
                tmpIdx += 3
                intsIdx += 2
            }
        }

        @Throws(IOException::class)
        fun decode4(pdu: PostingDecodingUtil, ints: IntArray) {
            pdu.splitInts(16, ints, 4, 4, MASK8_4, ints, 16, MASK8_4)
        }

        @Throws(IOException::class)
        fun decode5(pdu: PostingDecodingUtil, tmp: IntArray, ints: IntArray) {
            pdu.splitInts(20, ints, 3, 5, MASK8_5, tmp, 0, MASK8_3)
            var iter = 0
            var tmpIdx = 0
            var intsIdx = 20
            while (iter < 4) {
                var l0 = tmp[tmpIdx + 0] shl 2
                l0 = l0 or ((tmp[tmpIdx + 1] ushr 1) and MASK8_2)
                ints[intsIdx + 0] = l0
                var l1 = (tmp[tmpIdx + 1] and MASK8_1) shl 4
                l1 = l1 or (tmp[tmpIdx + 2] shl 1)
                l1 = l1 or ((tmp[tmpIdx + 3] ushr 2) and MASK8_1)
                ints[intsIdx + 1] = l1
                var l2 = (tmp[tmpIdx + 3] and MASK8_2) shl 3
                l2 = l2 or (tmp[tmpIdx + 4] shl 0)
                ints[intsIdx + 2] = l2
                ++iter
                tmpIdx += 5
                intsIdx += 3
            }
        }

        @Throws(IOException::class)
        fun decode6(pdu: PostingDecodingUtil, tmp: IntArray, ints: IntArray) {
            pdu.splitInts(24, ints, 2, 6, MASK8_6, tmp, 0, MASK8_2)
            var iter = 0
            var tmpIdx = 0
            var intsIdx = 24
            while (iter < 8) {
                var l0 = tmp[tmpIdx + 0] shl 4
                l0 = l0 or (tmp[tmpIdx + 1] shl 2)
                l0 = l0 or (tmp[tmpIdx + 2] shl 0)
                ints[intsIdx + 0] = l0
                ++iter
                tmpIdx += 3
                intsIdx += 1
            }
        }

        @Throws(IOException::class)
        fun decode7(pdu: PostingDecodingUtil, tmp: IntArray, ints: IntArray) {
            pdu.splitInts(28, ints, 1, 7, MASK8_7, tmp, 0, MASK8_1)
            var iter = 0
            var tmpIdx = 0
            var intsIdx = 28
            while (iter < 4) {
                var l0 = tmp[tmpIdx + 0] shl 6
                l0 = l0 or (tmp[tmpIdx + 1] shl 5)
                l0 = l0 or (tmp[tmpIdx + 2] shl 4)
                l0 = l0 or (tmp[tmpIdx + 3] shl 3)
                l0 = l0 or (tmp[tmpIdx + 4] shl 2)
                l0 = l0 or (tmp[tmpIdx + 5] shl 1)
                l0 = l0 or (tmp[tmpIdx + 6] shl 0)
                ints[intsIdx + 0] = l0
                ++iter
                tmpIdx += 7
                intsIdx += 1
            }
        }

        @Throws(IOException::class)
        fun decode8(pdu: PostingDecodingUtil, ints: IntArray) {
            pdu.`in`.readInts(ints, 0, 32)
        }

        @Throws(IOException::class)
        fun decode9(pdu: PostingDecodingUtil, tmp: IntArray, ints: IntArray) {
            pdu.splitInts(36, ints, 7, 9, MASK16_9, tmp, 0, MASK16_7)
            var iter = 0
            var tmpIdx = 0
            var intsIdx = 36
            while (iter < 4) {
                var l0 = tmp[tmpIdx + 0] shl 2
                l0 = l0 or ((tmp[tmpIdx + 1] ushr 5) and MASK16_2)
                ints[intsIdx + 0] = l0
                var l1 = (tmp[tmpIdx + 1] and MASK16_5) shl 4
                l1 = l1 or ((tmp[tmpIdx + 2] ushr 3) and MASK16_4)
                ints[intsIdx + 1] = l1
                var l2 = (tmp[tmpIdx + 2] and MASK16_3) shl 6
                l2 = l2 or ((tmp[tmpIdx + 3] ushr 1) and MASK16_6)
                ints[intsIdx + 2] = l2
                var l3 = (tmp[tmpIdx + 3] and MASK16_1) shl 8
                l3 = l3 or (tmp[tmpIdx + 4] shl 1)
                l3 = l3 or ((tmp[tmpIdx + 5] ushr 6) and MASK16_1)
                ints[intsIdx + 3] = l3
                var l4 = (tmp[tmpIdx + 5] and MASK16_6) shl 3
                l4 = l4 or ((tmp[tmpIdx + 6] ushr 4) and MASK16_3)
                ints[intsIdx + 4] = l4
                var l5 = (tmp[tmpIdx + 6] and MASK16_4) shl 5
                l5 = l5 or ((tmp[tmpIdx + 7] ushr 2) and MASK16_5)
                ints[intsIdx + 5] = l5
                var l6 = (tmp[tmpIdx + 7] and MASK16_2) shl 7
                l6 = l6 or (tmp[tmpIdx + 8] shl 0)
                ints[intsIdx + 6] = l6
                ++iter
                tmpIdx += 9
                intsIdx += 7
            }
        }

        @Throws(IOException::class)
        fun decode10(pdu: PostingDecodingUtil, tmp: IntArray, ints: IntArray) {
            pdu.splitInts(40, ints, 6, 10, MASK16_10, tmp, 0, MASK16_6)
            var iter = 0
            var tmpIdx = 0
            var intsIdx = 40
            while (iter < 8) {
                var l0 = tmp[tmpIdx + 0] shl 4
                l0 = l0 or ((tmp[tmpIdx + 1] ushr 2) and MASK16_4)
                ints[intsIdx + 0] = l0
                var l1 = (tmp[tmpIdx + 1] and MASK16_2) shl 8
                l1 = l1 or (tmp[tmpIdx + 2] shl 2)
                l1 = l1 or ((tmp[tmpIdx + 3] ushr 4) and MASK16_2)
                ints[intsIdx + 1] = l1
                var l2 = (tmp[tmpIdx + 3] and MASK16_4) shl 6
                l2 = l2 or (tmp[tmpIdx + 4] shl 0)
                ints[intsIdx + 2] = l2
                ++iter
                tmpIdx += 5
                intsIdx += 3
            }
        }

        @Throws(IOException::class)
        fun decode11(pdu: PostingDecodingUtil, tmp: IntArray, ints: IntArray) {
            pdu.splitInts(44, ints, 5, 11, MASK16_11, tmp, 0, MASK16_5)
            var iter = 0
            var tmpIdx = 0
            var intsIdx = 44
            while (iter < 4) {
                var l0 = tmp[tmpIdx + 0] shl 6
                l0 = l0 or (tmp[tmpIdx + 1] shl 1)
                l0 = l0 or ((tmp[tmpIdx + 2] ushr 4) and MASK16_1)
                ints[intsIdx + 0] = l0
                var l1 = (tmp[tmpIdx + 2] and MASK16_4) shl 7
                l1 = l1 or (tmp[tmpIdx + 3] shl 2)
                l1 = l1 or ((tmp[tmpIdx + 4] ushr 3) and MASK16_2)
                ints[intsIdx + 1] = l1
                var l2 = (tmp[tmpIdx + 4] and MASK16_3) shl 8
                l2 = l2 or (tmp[tmpIdx + 5] shl 3)
                l2 = l2 or ((tmp[tmpIdx + 6] ushr 2) and MASK16_3)
                ints[intsIdx + 2] = l2
                var l3 = (tmp[tmpIdx + 6] and MASK16_2) shl 9
                l3 = l3 or (tmp[tmpIdx + 7] shl 4)
                l3 = l3 or ((tmp[tmpIdx + 8] ushr 1) and MASK16_4)
                ints[intsIdx + 3] = l3
                var l4 = (tmp[tmpIdx + 8] and MASK16_1) shl 10
                l4 = l4 or (tmp[tmpIdx + 9] shl 5)
                l4 = l4 or (tmp[tmpIdx + 10] shl 0)
                ints[intsIdx + 4] = l4
                ++iter
                tmpIdx += 11
                intsIdx += 5
            }
        }

        @Throws(IOException::class)
        fun decode12(pdu: PostingDecodingUtil, tmp: IntArray, ints: IntArray) {
            pdu.splitInts(48, ints, 4, 12, MASK16_12, tmp, 0, MASK16_4)
            var iter = 0
            var tmpIdx = 0
            var intsIdx = 48
            while (iter < 16) {
                var l0 = tmp[tmpIdx + 0] shl 8
                l0 = l0 or (tmp[tmpIdx + 1] shl 4)
                l0 = l0 or (tmp[tmpIdx + 2] shl 0)
                ints[intsIdx + 0] = l0
                ++iter
                tmpIdx += 3
                intsIdx += 1
            }
        }

        @Throws(IOException::class)
        fun decode13(pdu: PostingDecodingUtil, tmp: IntArray, ints: IntArray) {
            pdu.splitInts(52, ints, 3, 13, MASK16_13, tmp, 0, MASK16_3)
            var iter = 0
            var tmpIdx = 0
            var intsIdx = 52
            while (iter < 4) {
                var l0 = tmp[tmpIdx + 0] shl 10
                l0 = l0 or (tmp[tmpIdx + 1] shl 7)
                l0 = l0 or (tmp[tmpIdx + 2] shl 4)
                l0 = l0 or (tmp[tmpIdx + 3] shl 1)
                l0 = l0 or ((tmp[tmpIdx + 4] ushr 2) and MASK16_1)
                ints[intsIdx + 0] = l0
                var l1 = (tmp[tmpIdx + 4] and MASK16_2) shl 11
                l1 = l1 or (tmp[tmpIdx + 5] shl 8)
                l1 = l1 or (tmp[tmpIdx + 6] shl 5)
                l1 = l1 or (tmp[tmpIdx + 7] shl 2)
                l1 = l1 or ((tmp[tmpIdx + 8] ushr 1) and MASK16_2)
                ints[intsIdx + 1] = l1
                var l2 = (tmp[tmpIdx + 8] and MASK16_1) shl 12
                l2 = l2 or (tmp[tmpIdx + 9] shl 9)
                l2 = l2 or (tmp[tmpIdx + 10] shl 6)
                l2 = l2 or (tmp[tmpIdx + 11] shl 3)
                l2 = l2 or (tmp[tmpIdx + 12] shl 0)
                ints[intsIdx + 2] = l2
                ++iter
                tmpIdx += 13
                intsIdx += 3
            }
        }

        @Throws(IOException::class)
        fun decode14(pdu: PostingDecodingUtil, tmp: IntArray, ints: IntArray) {
            pdu.splitInts(56, ints, 2, 14, MASK16_14, tmp, 0, MASK16_2)
            var iter = 0
            var tmpIdx = 0
            var intsIdx = 56
            while (iter < 8) {
                var l0 = tmp[tmpIdx + 0] shl 12
                l0 = l0 or (tmp[tmpIdx + 1] shl 10)
                l0 = l0 or (tmp[tmpIdx + 2] shl 8)
                l0 = l0 or (tmp[tmpIdx + 3] shl 6)
                l0 = l0 or (tmp[tmpIdx + 4] shl 4)
                l0 = l0 or (tmp[tmpIdx + 5] shl 2)
                l0 = l0 or (tmp[tmpIdx + 6] shl 0)
                ints[intsIdx + 0] = l0
                ++iter
                tmpIdx += 7
                intsIdx += 1
            }
        }

        @Throws(IOException::class)
        fun decode15(pdu: PostingDecodingUtil, tmp: IntArray, ints: IntArray) {
            pdu.splitInts(60, ints, 1, 15, MASK16_15, tmp, 0, MASK16_1)
            var iter = 0
            var tmpIdx = 0
            var intsIdx = 60
            while (iter < 4) {
                var l0 = tmp[tmpIdx + 0] shl 14
                l0 = l0 or (tmp[tmpIdx + 1] shl 13)
                l0 = l0 or (tmp[tmpIdx + 2] shl 12)
                l0 = l0 or (tmp[tmpIdx + 3] shl 11)
                l0 = l0 or (tmp[tmpIdx + 4] shl 10)
                l0 = l0 or (tmp[tmpIdx + 5] shl 9)
                l0 = l0 or (tmp[tmpIdx + 6] shl 8)
                l0 = l0 or (tmp[tmpIdx + 7] shl 7)
                l0 = l0 or (tmp[tmpIdx + 8] shl 6)
                l0 = l0 or (tmp[tmpIdx + 9] shl 5)
                l0 = l0 or (tmp[tmpIdx + 10] shl 4)
                l0 = l0 or (tmp[tmpIdx + 11] shl 3)
                l0 = l0 or (tmp[tmpIdx + 12] shl 2)
                l0 = l0 or (tmp[tmpIdx + 13] shl 1)
                l0 = l0 or (tmp[tmpIdx + 14] shl 0)
                ints[intsIdx + 0] = l0
                ++iter
                tmpIdx += 15
                intsIdx += 1
            }
        }

        @Throws(IOException::class)
        fun decode16(pdu: PostingDecodingUtil, ints: IntArray) {
            pdu.`in`.readInts(ints, 0, 64)
        }
    }
}
