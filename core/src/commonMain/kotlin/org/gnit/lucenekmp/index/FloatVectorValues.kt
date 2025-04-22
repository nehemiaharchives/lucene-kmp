package org.gnit.lucenekmp.index

import kotlinx.io.IOException
import org.gnit.lucenekmp.search.VectorScorer


/**
 * This class provides access to per-document floating point vector values indexed as [ ].
 *
 * @lucene.experimental
 */
abstract class FloatVectorValues
/** Sole constructor  */
protected constructor() : KnnVectorValues() {
    /**
     * Return the vector value for the given vector ordinal which must be in [0, size() - 1],
     * otherwise IndexOutOfBoundsException is thrown. The returned array may be shared across calls.
     *
     * @return the vector value
     */
    @Throws(IOException::class)
    abstract fun vectorValue(ord: Int): FloatArray

    @Throws(IOException::class)
    abstract override fun copy(): FloatVectorValues

    /**
     * Return a [VectorScorer] for the given query vector and the current [ ].
     *
     * @param target the query vector
     * @return a [VectorScorer] instance or null
     */
    @Throws(IOException::class)
    open fun scorer(target: FloatArray): VectorScorer? {
        throw UnsupportedOperationException()
    }

    override val encoding: VectorEncoding
        get() = VectorEncoding.FLOAT32

    companion object {
        /**
         * Checks the Vector Encoding of a field
         *
         * @throws IllegalStateException if `field` has vectors, but using a different encoding
         * @lucene.internal
         * @lucene.experimental
         */
        fun checkField(`in`: LeafReader, field: String) {
            val fi: FieldInfo? = `in`.fieldInfos.fieldInfo(field)
            check(!(fi != null && fi.hasVectorValues() && fi.vectorEncoding !== VectorEncoding.FLOAT32)) {
                ("Unexpected vector encoding ("
                        + fi!!.vectorEncoding
                        + ") for field "
                        + field
                        + "(expected="
                        + VectorEncoding.FLOAT32
                        + ")")
            }
        }

        /**
         * Creates a [FloatVectorValues] from a list of float arrays.
         *
         * @param vectors the list of float arrays
         * @param dim the dimension of the vectors
         * @return a [FloatVectorValues] instance
         */
        fun fromFloats(vectors: MutableList<FloatArray>, dim: Int): FloatVectorValues {
            return object : FloatVectorValues() {
                override fun size(): Int {
                    return vectors.size
                }

                override fun dimension(): Int {
                    return dim
                }

                override fun vectorValue(targetOrd: Int): FloatArray {
                    return vectors.get(targetOrd)
                }

                override fun copy(): FloatVectorValues {
                    return this
                }

                override fun iterator(): DocIndexIterator {
                    return createDenseIterator()
                }
            }
        }
    }
}
