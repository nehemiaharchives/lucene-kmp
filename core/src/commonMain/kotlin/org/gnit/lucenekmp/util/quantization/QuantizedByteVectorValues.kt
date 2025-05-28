package org.gnit.lucenekmp.util.quantization


import org.gnit.lucenekmp.codecs.lucene95.HasIndexSlice
import org.gnit.lucenekmp.index.ByteVectorValues
import org.gnit.lucenekmp.search.VectorScorer
import org.gnit.lucenekmp.store.IndexInput
import okio.IOException

/**
 * A version of [ByteVectorValues], but additionally retrieving score correction offset for
 * Scalar quantization scores.
 *
 * @lucene.experimental
 */
abstract class QuantizedByteVectorValues : ByteVectorValues(), HasIndexSlice {
    val scalarQuantizer: ScalarQuantizer
        get() {
            throw UnsupportedOperationException()
        }

    @Throws(IOException::class)
    abstract fun getScoreCorrectionConstant(ord: Int): Float

    /**
     * Return a [VectorScorer] for the given query vector.
     *
     * @param query the query vector
     * @return a [VectorScorer] instance or null
     */
    @Throws(IOException::class)
    fun scorer(query: FloatArray): VectorScorer {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun copy(): QuantizedByteVectorValues {
        return this
    }

    override val slice: IndexInput?
        get() = null
}
