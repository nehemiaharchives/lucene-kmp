package org.gnit.lucenekmp.internal.vectorization

/**
 * Interface for implementations of VectorUtil support.
 *
 * @lucene.internal
 */
interface VectorUtilSupport {
    /** Calculates the dot product of the given float arrays.  */
    fun dotProduct(a: FloatArray, b: FloatArray): Float

    /** Returns the cosine similarity between the two vectors.  */
    fun cosine(v1: FloatArray, v2: FloatArray): Float

    /** Returns the sum of squared differences of the two vectors.  */
    fun squareDistance(a: FloatArray, b: FloatArray): Float

    /** Returns the dot product computed over signed bytes.  */
    fun dotProduct(a: ByteArray, b: ByteArray): Int

    /** Returns the dot product over the computed bytes, assuming the values are int4 encoded.  */
    fun int4DotProduct(a: ByteArray, apacked: Boolean, b: ByteArray, bpacked: Boolean): Int

    /** Returns the cosine similarity between the two byte vectors.  */
    fun cosine(a: ByteArray, b: ByteArray): Float

    /** Returns the sum of squared differences of the two byte vectors.  */
    fun squareDistance(a: ByteArray, b: ByteArray): Int

    /**
     * Given an array `buffer` that is sorted between indexes `0` inclusive and `to`
     * exclusive, find the first array index whose value is greater than or equal to `target`.
     * This index is guaranteed to be at least `from`. If there is no such array index, `to` is returned.
     */
    fun findNextGEQ(buffer: IntArray, target: Int, from: Int, to: Int): Int

    /**
     * Compute the dot product between a quantized int4 vector and a binary quantized vector. It is
     * assumed that the int4 quantized bits are packed in the byte array in the same way as the [ ][org.apache.lucene.util.quantization.OptimizedScalarQuantizer.transposeHalfByte]
     * and that the binary bits are packed the same way as [ ][org.apache.lucene.util.quantization.OptimizedScalarQuantizer.packAsBinary].
     *
     * @param int4Quantized half byte packed int4 quantized vector
     * @param binaryQuantized byte packed binary quantized vector
     * @return the dot product
     */
    fun int4BitDotProduct(int4Quantized: ByteArray, binaryQuantized: ByteArray): Long
}
