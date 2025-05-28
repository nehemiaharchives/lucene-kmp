package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.search.VectorScorer


/**
 * This class provides access to per-document floating point vector values indexed as [ ].
 *
 * @lucene.experimental
 */
abstract class ByteVectorValues
/** Sole constructor  */
protected constructor() : KnnVectorValues() {
    /**
     * Return the vector value for the given vector ordinal which must be in [0, size() - 1],
     * otherwise IndexOutOfBoundsException is thrown. The returned array may be shared across calls.
     *
     * @return the vector value
     */
    @Throws(IOException::class)
    abstract fun vectorValue(ord: Int): ByteArray

    @Throws(IOException::class)
    public abstract override fun copy(): ByteVectorValues

    /**
     * Return a [VectorScorer] for the given query vector.
     *
     * @param query the query vector
     * @return a [VectorScorer] instance or null
     */
    @Throws(IOException::class)
    open fun scorer(query: ByteArray): VectorScorer? {
        throw UnsupportedOperationException()
    }

    override val encoding: VectorEncoding
        get() = VectorEncoding.BYTE

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
            check(!(fi != null && fi.hasVectorValues() && fi.vectorEncoding != VectorEncoding.BYTE)) {
                ("Unexpected vector encoding ("
                        + fi!!.vectorEncoding
                        + ") for field "
                        + field
                        + "(expected="
                        + VectorEncoding.BYTE
                        + ")")
            }
        }

        /**
         * Creates a [ByteVectorValues] from a list of byte arrays.
         *
         * @param vectors the list of byte arrays
         * @param dim the dimension of the vectors
         * @return a [ByteVectorValues] instancec
         */
        fun fromBytes(vectors: MutableList<ByteArray>, dim: Int): ByteVectorValues {
            return object : ByteVectorValues() {
                override fun size(): Int {
                    return vectors.size
                }

                override fun dimension(): Int {
                    return dim
                }

                override fun vectorValue(targetOrd: Int): ByteArray {
                    return vectors.get(targetOrd)
                }

                override fun copy(): ByteVectorValues {
                    return this
                }

                override fun iterator(): DocIndexIterator {
                    return createDenseIterator()
                }
            }
        }
    }
}
