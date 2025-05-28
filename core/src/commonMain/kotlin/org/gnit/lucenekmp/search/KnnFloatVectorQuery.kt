package org.gnit.lucenekmp.search

import okio.IOException
import kotlin.jvm.JvmOverloads
import org.gnit.lucenekmp.codecs.KnnVectorsReader
import org.gnit.lucenekmp.document.KnnFloatVectorField
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.FloatVectorValues
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.search.knn.KnnCollectorManager
import org.gnit.lucenekmp.search.knn.KnnSearchStrategy
import org.gnit.lucenekmp.search.knn.KnnSearchStrategy.Hnsw.Companion.DEFAULT
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.VectorUtil
import kotlin.math.min

/**
 * Uses [KnnVectorsReader.search] to perform nearest
 * neighbour search.
 *
 *
 * This query also allows for performing a kNN search subject to a filter. In this case, it first
 * executes the filter for each leaf, then chooses a strategy dynamically:
 *
 *
 *  * If the filter cost is less than k, just execute an exact search
 *  * Otherwise run a kNN search subject to the filter
 *  * If the kNN search visits too many vectors without completing, stop and run an exact search
 *
 */
class KnnFloatVectorQuery(
    field: String,
    target: FloatArray,
    k: Int,
    filter: Query?,
    searchStrategy: KnnSearchStrategy
) : AbstractKnnVectorQuery(field, k, filter, searchStrategy) {
    protected val target: FloatArray = VectorUtil.checkFinite(target)

    /**
     * Find the `k` nearest documents to the target vector according to the vectors in the
     * given field. `target` vector.
     *
     * @param field a field that has been indexed as a [KnnFloatVectorField].
     * @param target the target of the search
     * @param k the number of documents to find
     * @param filter a filter applied before the vector search
     * @throws IllegalArgumentException if `k` is less than 1
     */
    /**
     * Find the `k` nearest documents to the target vector according to the vectors in the
     * given field. `target` vector.
     *
     * @param field a field that has been indexed as a [KnnFloatVectorField].
     * @param target the target of the search
     * @param k the number of documents to find
     * @throws IllegalArgumentException if `k` is less than 1
     */
    @JvmOverloads
    constructor(field: String, target: FloatArray, k: Int, filter: Query? = null) : this(
        field,
        target,
        k,
        filter,
        DEFAULT
    )

    @Throws(IOException::class)
    override fun approximateSearch(
        context: LeafReaderContext,
        acceptDocs: Bits,
        visitedLimit: Int,
        knnCollectorManager: KnnCollectorManager
    ): TopDocs {
        val knnCollector: KnnCollector =
            knnCollectorManager.newCollector(visitedLimit, searchStrategy, context)
        val reader: LeafReader = context.reader()
        val floatVectorValues: FloatVectorValues? = reader.getFloatVectorValues(field)
        if (floatVectorValues == null) {
            FloatVectorValues.checkField(reader, field)
            return NO_RESULTS
        }
        if (min(knnCollector.k(), floatVectorValues.size()) == 0) {
            return NO_RESULTS
        }
        reader.searchNearestVectors(field, target, knnCollector, acceptDocs)
        val results: TopDocs? = knnCollector.topDocs()
        return results ?: NO_RESULTS
    }

    @Throws(IOException::class)
    override fun createVectorScorer(context: LeafReaderContext, fi: FieldInfo): VectorScorer? {
        val reader: LeafReader = context.reader()
        val vectorValues: FloatVectorValues? = reader.getFloatVectorValues(field)
        if (vectorValues == null) {
            FloatVectorValues.checkField(reader, field)
            return null
        }
        return vectorValues.scorer(target)
    }

    override fun toString(field: String?): String {
        val buffer = StringBuilder()
        buffer.append(this::class.simpleName + ":")
        buffer.append(this.field + "[" + target[0] + ",...]")
        buffer.append("[$k]")
        if (this.filter != null) {
            buffer.append("[" + this.filter + "]")
        }
        return buffer.toString()
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (super.equals(o) == false) return false
        val that = o as KnnFloatVectorQuery
        return target.contentEquals(that.target)
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + target.contentHashCode()
        return result
    }

    val targetCopy: FloatArray
        /**
         * @return the target query vector of the search. Each vector element is a float.
         */
        get() = ArrayUtil.copyArray(target)

    companion object {
        private val NO_RESULTS: TopDocs = TopDocsCollector.EMPTY_TOPDOCS
    }
}
