package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.internal.vectorization.VectorUtilSupport
import org.gnit.lucenekmp.internal.vectorization.VectorizationProvider
import kotlin.math.abs
import kotlin.math.sqrt


/**
 * Utilities for computations with numeric arrays, especially algebraic operations like vector dot
 * products. This class uses SIMD vectorization if the corresponding Java module is available and
 * enabled. To enable vectorized code, pass `--add-modules jdk.incubator.vector` to Java's
 * command line.
 *
 *
 * It will use CPU's [FMA
 * instructions](https://en.wikipedia.org/wiki/Fused_multiply%E2%80%93add) if it is known to perform faster than separate multiply+add. This requires at
 * least Hotspot C2 enabled, which is the default for OpenJDK based JVMs.
 *
 *
 * To explicitly disable or enable FMA usage, pass the following system properties:
 *
 *
 *  * `-Dlucene.useScalarFMA=(auto|true|false)` for scalar operations
 *  * `-Dlucene.useVectorFMA=(auto|true|false)` for vectorized operations (with vector
 * incubator module)
 *
 *
 *
 * The default is `auto`, which enables this for known CPU types and JVM settings. If
 * Hotspot C2 is disabled, FMA and vectorization are **not** used.
 *
 *
 * Vectorization and FMA is only supported for Hotspot-based JVMs; it won't work on OpenJ9-based
 * JVMs unless they provide [com.sun.management.HotSpotDiagnosticMXBean]. Please also make
 * sure that you have the `jdk.management` module enabled in modularized applications.
 */
object VectorUtil {
    private const val EPSILON = 1e-4f

    private val IMPL: VectorUtilSupport = VectorizationProvider.getInstance().getVectorUtilSupport()

    /**
     * Returns the vector dot product of the two vectors.
     *
     * @throws IllegalArgumentException if the vectors' dimensions differ.
     */
    fun dotProduct(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "vector dimensions differ: " + a.size + "!=" + b.size }
        val r: Float = IMPL.dotProduct(a, b)
        require(Float.isFinite(r))
        return r
    }

    /**
     * Returns the cosine similarity between the two vectors.
     *
     * @throws IllegalArgumentException if the vectors' dimensions differ.
     */
    fun cosine(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "vector dimensions differ: " + a.size + "!=" + b.size }
        val r: Float = IMPL.cosine(a, b)
        require(Float.isFinite(r))
        return r
    }

    /** Returns the cosine similarity between the two vectors.  */
    fun cosine(a: ByteArray, b: ByteArray): Float {
        require(a.size == b.size) { "vector dimensions differ: " + a.size + "!=" + b.size }
        return IMPL.cosine(a, b)
    }

    /**
     * Returns the sum of squared differences of the two vectors.
     *
     * @throws IllegalArgumentException if the vectors' dimensions differ.
     */
    fun squareDistance(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "vector dimensions differ: " + a.size + "!=" + b.size }
        val r: Float = IMPL.squareDistance(a, b)
        require(Float.isFinite(r))
        return r
    }

    /** Returns the sum of squared differences of the two vectors.  */
    fun squareDistance(a: ByteArray, b: ByteArray): Int {
        require(a.size == b.size) { "vector dimensions differ: " + a.size + "!=" + b.size }
        return IMPL.squareDistance(a, b)
    }

    /**
     * Modifies the argument to be unit length, dividing by its l2-norm. IllegalArgumentException is
     * thrown for zero vectors.
     *
     * @return the input array after normalization
     */
    fun l2normalize(v: FloatArray): FloatArray {
        l2normalize(v, true)
        return v
    }

    fun isUnitVector(v: FloatArray): Boolean {
        val l1norm: Double = IMPL.dotProduct(v, v).toDouble()
        return abs(l1norm - 1.0) <= EPSILON
    }

    /**
     * Modifies the argument to be unit length, dividing by its l2-norm.
     *
     * @param v the vector to normalize
     * @param throwOnZero whether to throw an exception when `v` has all zeros
     * @return the input array after normalization
     * @throws IllegalArgumentException when the vector is all zero and throwOnZero is true
     */
    fun l2normalize(v: FloatArray, throwOnZero: Boolean): FloatArray {
        val l1norm: Double = IMPL.dotProduct(v, v).toDouble()
        if (l1norm == 0.0) {
            require(!throwOnZero) { "Cannot normalize a zero-length vector" }
            return v
        }
        if (abs(l1norm - 1.0) <= EPSILON) {
            return v
        }
        val dim = v.size
        val l2norm = sqrt(l1norm)
        for (i in 0..<dim) {
            v[i] /= l2norm.toFloat()
        }
        return v
    }

    /**
     * Adds the second argument to the first
     *
     * @param u the destination
     * @param v the vector to add to the destination
     */
    fun add(u: FloatArray, v: FloatArray) {
        for (i in u.indices) {
            u[i] += v[i]
        }
    }

    /**
     * Dot product computed over signed bytes.
     *
     * @param a bytes containing a vector
     * @param b bytes containing another vector, of the same dimension
     * @return the value of the dot product of the two vectors
     */
    fun dotProduct(a: ByteArray, b: ByteArray): Int {
        require(a.size == b.size) { "vector dimensions differ: " + a.size + "!=" + b.size }
        return IMPL.dotProduct(a, b)
    }

    fun int4DotProduct(a: ByteArray, b: ByteArray): Int {
        require(a.size == b.size) { "vector dimensions differ: " + a.size + "!=" + b.size }
        return IMPL.int4DotProduct(a, false, b, false)
    }

    /**
     * Dot product computed over int4 (values between [0,15]) bytes. The second vector is considered
     * "packed" (i.e. every byte representing two values). The following packing is assumed:
     *
     * <pre class="prettyprint lang-java">
     * packed[0] = (raw[0] * 16) | raw[packed.length];
     * packed[1] = (raw[1] * 16) | raw[packed.length + 1];
     * ...
     * packed[packed.length - 1] = (raw[packed.length - 1] * 16) | raw[2 * packed.length - 1];
    </pre> *
     *
     * @param unpacked the unpacked vector, of even length
     * @param packed the packed vector, of length `(unpacked.length + 1) / 2`
     * @return the value of the dot product of the two vectors
     */
    fun int4DotProductPacked(unpacked: ByteArray, packed: ByteArray): Int {
        require(packed.size == ((unpacked.size + 1) shr 1)) { "vector dimensions differ: " + unpacked.size + "!= 2 * " + packed.size }
        return IMPL.int4DotProduct(unpacked, false, packed, true)
    }

    /**
     * Dot product computed over int4 (values between [0,15]) bytes and a binary vector.
     *
     * @param q the int4 query vector
     * @param d the binary document vector
     * @return the dot product
     */
    fun int4BitDotProduct(q: ByteArray, d: ByteArray): Long {
        require(q.size == d.size * 4) { "vector dimensions incompatible: " + q.size + "!= " + 4 + " x " + d.size }
        return IMPL.int4BitDotProduct(q, d)
    }

    /**
     * For xorBitCount we stride over the values as either 64-bits (long) or 32-bits (int) at a time.
     * On ARM Long::bitCount is not vectorized, and therefore produces less than optimal code, when
     * compared to Integer::bitCount. While Long::bitCount is optimal on x64. See
     * https://bugs.openjdk.org/browse/JDK-8336000
     */
    val XOR_BIT_COUNT_STRIDE_AS_INT: Boolean = Constants.OS_ARCH.equals("aarch64")

    /**
     * XOR bit count computed over signed bytes.
     *
     * @param a bytes containing a vector
     * @param b bytes containing another vector, of the same dimension
     * @return the value of the XOR bit count of the two vectors
     */
    fun xorBitCount(a: ByteArray, b: ByteArray): Int {
        require(a.size == b.size) { "vector dimensions differ: " + a.size + "!=" + b.size }
        if (XOR_BIT_COUNT_STRIDE_AS_INT) {
            return xorBitCountInt(a, b)
        } else {
            return xorBitCountLong(a, b)
        }
    }

    /** XOR bit count striding over 4 bytes at a time.  */
    fun xorBitCountInt(a: ByteArray, b: ByteArray): Int {
        var distance = 0
        var i = 0
        val upperBound = a.size and -Int.SIZE_BYTES
        /*while (i < upperBound) {
            distance +=
                Int.bitCount(
                    BitUtil.VH_NATIVE_INT.get(a, i) as Int xor BitUtil.VH_NATIVE_INT.get(b, i) as Int
                )
            i += Int.SIZE_BYTES
        }*/
        while (i < upperBound) {
            val intA = a.getIntLE(i)
            val intB = b.getIntLE(i)
            distance += Int.bitCount(intA xor intB)
            i += Int.SIZE_BYTES
        }

        // tail:
        while (i < a.size) {
            distance += Int.bitCount((a[i].toInt() xor b[i].toInt()) and 0xFF)
            i++
        }
        return distance
    }

    /** XOR bit count striding over 8 bytes at a time.  */
    fun xorBitCountLong(a: ByteArray, b: ByteArray): Int {
        var distance = 0
        var i = 0
        val upperBound = a.size and -Long.SIZE_BYTES
        /*while (i < upperBound) {
            distance +=
                Long.bitCount(
                    BitUtil.VH_NATIVE_LONG.get(a, i) as Long xor BitUtil.VH_NATIVE_LONG.get(b, i) as Long
                )
            i += Long.SIZE_BYTES
        }*/
        while (i < upperBound) {
            // Read a native-endian long from each array using getLongAt.
            val longA = a.getLongLE(i)
            val longB = b.getLongLE(i)
            distance += Long.bitCount(longA xor longB)
            i += 8
        }

        // tail:
        while (i < a.size) {
            distance += Int.bitCount((a[i].toInt() xor b[i].toInt()) and 0xFF)
            i++
        }
        return distance
    }

    /**
     * Dot product score computed over signed bytes, scaled to be in [0, 1].
     *
     * @param a bytes containing a vector
     * @param b bytes containing another vector, of the same dimension
     * @return the value of the similarity function applied to the two vectors
     */
    fun dotProductScore(a: ByteArray, b: ByteArray): Float {
        // divide by 2 * 2^14 (maximum absolute value of product of 2 signed bytes) * len
        val denom = (a.size * (1 shl 15)).toFloat()
        return 0.5f + dotProduct(a, b) / denom
    }

    /**
     * @param vectorDotProductSimilarity the raw similarity between two vectors
     * @return A scaled score preventing negative scores for maximum-inner-product
     */
    fun scaleMaxInnerProductScore(vectorDotProductSimilarity: Float): Float {
        if (vectorDotProductSimilarity < 0) {
            return 1 / (1 + -1 * vectorDotProductSimilarity)
        }
        return vectorDotProductSimilarity + 1
    }

    /**
     * Checks if a float vector only has finite components.
     *
     * @param v bytes containing a vector
     * @return the vector for call-chaining
     * @throws IllegalArgumentException if any component of vector is not finite
     */
    fun checkFinite(v: FloatArray): FloatArray {
        for (i in v.indices) {
            require(Float.isFinite(v[i])) { "non-finite value at vector[" + i + "]=" + v[i] }
        }
        return v
    }

    /**
     * Given an array `buffer` that is sorted between indexes `0` inclusive and `to`
     * exclusive, find the first array index whose value is greater than or equal to `target`.
     * This index is guaranteed to be at least `from`. If there is no such array index, `to` is returned.
     */
    fun findNextGEQ(buffer: IntArray, target: Int, from: Int, to: Int): Int {
        require((0 until (to - 1)).none { i -> buffer[i] > buffer[i + 1] })
        return IMPL.findNextGEQ(buffer, target, from, to)
    }
}
