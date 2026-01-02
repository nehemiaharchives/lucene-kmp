package org.gnit.lucenekmp.internal.vectorization

import kotlin.math.sqrt
import org.gnit.lucenekmp.util.getIntLE
import org.gnit.lucenekmp.jdkport.bitCount

/**
 * A common Kotlin implementation of the VectorUtilSupport interface.
 */
class DefaultVectorUtilSupport : VectorUtilSupport {

    // --- Float array implementations ---

    override fun dotProduct(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vectors must be the same length" }
        var result = 0f
        for (i in a.indices) {
            result += a[i] * b[i]
        }
        return result
    }

    override fun cosine(v1: FloatArray, v2: FloatArray): Float {
        require(v1.size == v2.size) { "Vectors must be the same length" }
        val dot = dotProduct(v1, v2)
        var norm1 = 0f
        var norm2 = 0f
        for (i in v1.indices) {
            norm1 += v1[i] * v1[i]
            norm2 += v2[i] * v2[i]
        }
        if (norm1 == 0f || norm2 == 0f) return 0f
        return dot / (sqrt(norm1.toDouble()) * sqrt(norm2.toDouble())).toFloat()
    }

    override fun squareDistance(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vectors must be the same length" }
        var sum = 0f
        for (i in a.indices) {
            val diff = a[i] - b[i]
            sum += diff * diff
        }
        return sum
    }

    // --- Byte array implementations ---

    override fun dotProduct(a: ByteArray, b: ByteArray): Int {
        require(a.size == b.size) { "Vectors must be the same length" }
        var result = 0
        for (i in a.indices) {
            result += a[i].toInt() * b[i].toInt()
        }
        return result
    }

    override fun cosine(a: ByteArray, b: ByteArray): Float {
        require(a.size == b.size) { "Vectors must be the same length" }
        var dot = 0
        var normA = 0
        var normB = 0
        for (i in a.indices) {
            val aVal = a[i].toInt()
            val bVal = b[i].toInt()
            dot += aVal * bVal
            normA += aVal * aVal
            normB += bVal * bVal
        }
        if (normA == 0 || normB == 0) return 0f
        return dot / (sqrt(normA.toDouble()) * sqrt(normB.toDouble())).toFloat()
    }

    override fun squareDistance(a: ByteArray, b: ByteArray): Int {
        require(a.size == b.size) { "Vectors must be the same length" }
        var sum = 0
        for (i in a.indices) {
            val diff = a[i].toInt() - b[i].toInt()
            sum += diff * diff
        }
        return sum
    }

    /**
     * Returns the dot product over int4 encoded bytes.
     *
     * The parameters [apacked] and [bpacked] indicate whether each byte array is half-byte
     * packed (two int4 values per byte) or whether each byte holds a single int4 value in its low nibble.
     */
    override fun int4DotProduct(a: ByteArray, apacked: Boolean, b: ByteArray, bpacked: Boolean): Int {
        check(!(apacked && bpacked)) { "Both inputs cannot be packed" }
        if (apacked || bpacked) {
            val packed = if (apacked) a else b
            val unpacked = if (apacked) b else a
            var total = 0
            for (i in packed.indices) {
                val packedByte = packed[i].toInt() and 0xFF
                val unpacked1 = unpacked[i].toInt() and 0xFF
                val unpacked2 = unpacked[i + packed.size].toInt() and 0xFF
                total += (packedByte and 0x0F) * unpacked2
                total += (packedByte ushr 4) * unpacked1
            }
            return total
        }
        return dotProduct(a, b)
    }

    /**
     * Compute the dot product between a quantized int4 vector and a binary quantized vector.
     *
     * The int4 vector is assumed to be half-byte packed (two values per byte) and the binary vector
     * is assumed to be bit-packed (8 bits per byte). For each int4 value, if the corresponding binary bit
     * is set (1) then the int4 value is added to the dot product.
     */
    override fun int4BitDotProduct(int4Quantized: ByteArray, binaryQuantized: ByteArray): Long {
        require(int4Quantized.size == binaryQuantized.size * 4) { "vector dimensions incompatible: ${int4Quantized.size} != 4 x ${binaryQuantized.size}" }
        val size = binaryQuantized.size
        var result = 0L
        for (i in 0 until 4) {
            var sub = 0
            var r = 0
            val upperBound = size and -Int.SIZE_BYTES
            while (r < upperBound) {
                val qInt = int4Quantized.getIntLE(i * size + r)
                val dInt = binaryQuantized.getIntLE(r)
                sub += Int.bitCount(qInt and dInt)
                r += Int.SIZE_BYTES
            }
            while (r < size) {
                sub += Int.bitCount((int4Quantized[i * size + r].toInt() and binaryQuantized[r].toInt()) and 0xFF)
                r++
            }
            result += (sub.toLong() shl i)
        }
        return result
    }

    // --- Helper functions for int4 decoding ---

    /**
     * Decodes a 4-bit nibble into a signed int4 value.
     *
     * Assumes the nibble is in the range 0..15 and converts values >= 8 into negative numbers.
     */
    @Suppress("unused")
    private fun decodeInt4(nibble: Int): Int {
        return if (nibble >= 8) nibble - 16 else nibble
    }

    /**
     * Extracts the int4 value at the given index from a half-byte packed byte array.
     *
     * Each byte in the array holds two int4 values. The first value is stored in the high nibble
     * (bits 4-7) and the second value in the low nibble (bits 0-3).
     */
    @Suppress("unused")
    private fun getInt4FromPacked(array: ByteArray, index: Int): Int {
        val byteIndex = index / 2
        if (byteIndex >= array.size) {
            throw IndexOutOfBoundsException("Index $index is out of bounds for a packed array of size ${array.size * 2}")
        }
        val nibble = if (index % 2 == 0) {
            // Even index: high nibble
            (array[byteIndex].toInt() ushr 4) and 0x0F
        } else {
            // Odd index: low nibble
            array[byteIndex].toInt() and 0x0F
        }
        return decodeInt4(nibble)
    }

    // --- Integer array implementation ---

    override fun findNextGEQ(buffer: IntArray, target: Int, from: Int, to: Int): Int {
        var low = from
        var high = to - 1
        var result = to
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (buffer[mid] >= target) {
                result = mid
                high = mid - 1
            } else {
                low = mid + 1
            }
        }
        return result
    }
}
