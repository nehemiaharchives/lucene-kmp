package org.gnit.lucenekmp.codecs.lucene102

import okio.IOException
import org.gnit.lucenekmp.index.ByteVectorValues
import org.gnit.lucenekmp.search.VectorScorer
import org.gnit.lucenekmp.util.VectorUtil
import org.gnit.lucenekmp.util.quantization.OptimizedScalarQuantizer

/** Binarized byte vector values  */
abstract class BinarizedByteVectorValues : ByteVectorValues() {
    /**
     * Retrieve the corrective terms for the given vector ordinal. For the dot-product family of
     * distances, the corrective terms are, in order
     *
     *
     *  * the lower optimized interval
     *  * the upper optimized interval
     *  * the dot-product of the non-centered vector with the centroid
     *  * the sum of quantized components
     *
     *
     * For euclidean:
     *
     *
     *  * the lower optimized interval
     *  * the upper optimized interval
     *  * the l2norm of the centered vector
     *  * the sum of quantized components
     *
     *
     * @param vectorOrd the vector ordinal
     * @return the corrective terms
     * @throws IOException if an I/O error occurs
     */
    @Throws(IOException::class)
    abstract fun getCorrectiveTerms(vectorOrd: Int): OptimizedScalarQuantizer.QuantizationResult?

    /**
     * @return the quantizer used to quantize the vectors
     */
    abstract val quantizer: OptimizedScalarQuantizer?

    abstract val centroid: FloatArray?

    fun discretizedDimensions(): Int {
        return OptimizedScalarQuantizer.discretize(dimension(), 64)
    }

    /**
     * Return a [VectorScorer] for the given query vector.
     *
     * @param query the query vector
     * @return a [VectorScorer] instance or null
     */
    @Throws(IOException::class)
    abstract fun scorer(query: FloatArray): VectorScorer?

    @Throws(IOException::class)
    abstract override fun copy(): BinarizedByteVectorValues

    open val centroidDP: Float
        get() {
            // this only gets executed on-merge
            val centroid = this.centroid
            return VectorUtil.dotProduct(centroid!!, centroid)
        }
}
