package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.search.knn.KnnSearchStrategy.Hnsw.Companion.DEFAULT
import org.gnit.lucenekmp.index.ByteVectorValues
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.search.knn.KnnCollectorManager
import org.gnit.lucenekmp.search.knn.KnnSearchStrategy
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.Bits
import okio.IOException
import org.gnit.lucenekmp.jdkport.Objects
import kotlin.jvm.JvmOverloads
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
open class KnnByteVectorQuery @JvmOverloads constructor(
    field: String,
    protected val target: ByteArray,
    k: Int,
    filter: Query? = null,
    searchStrategy: KnnSearchStrategy = DEFAULT
) : AbstractKnnVectorQuery(field, k, filter, searchStrategy) {

    /**
     * Find the `k` nearest documents to the target vector according to the vectors in the
     * given field. `target` vector.
     *
     * @param field a field that has been indexed as a [KnnByteVectorField].
     * @param target the target of the search
     * @param k the number of documents to find
     * @param filter a filter applied before the vector search
     * @param searchStrategy the search strategy to use. If null, the default strategy will be used.
     * The underlying format may not support all strategies and is free to ignore the requested
     * strategy.
     * @lucene.experimental
     */
    /**
     * Find the `k` nearest documents to the target vector according to the vectors in the
     * given field. `target` vector.
     *
     * @param field a field that has been indexed as a [KnnByteVectorField].
     * @param target the target of the search
     * @param k the number of documents to find
     * @param filter a filter applied before the vector search
     * @throws IllegalArgumentException if `k` is less than 1
     */

    @Throws(IOException::class)
    protected override fun approximateSearch(
        context: LeafReaderContext,
        acceptDocs: Bits,
        visitedLimit: Int,
        knnCollectorManager: KnnCollectorManager
    ): TopDocs {
        val knnCollector: KnnCollector =
            knnCollectorManager.newCollector(visitedLimit, searchStrategy, context)
        val reader: LeafReader = context.reader()
        val byteVectorValues: ByteVectorValues? = reader.getByteVectorValues(field)
        if (byteVectorValues == null) {
            ByteVectorValues.checkField(reader, field)
            return NO_RESULTS
        }
        if (min(knnCollector.k(), byteVectorValues.size()) == 0) {
            return NO_RESULTS
        }
        reader.searchNearestVectors(field, target, knnCollector, acceptDocs)
        val results: TopDocs? = knnCollector.topDocs()
        return if (results != null) results else NO_RESULTS
    }

    @Throws(IOException::class)
    override fun createVectorScorer(
        context: LeafReaderContext,
        fi: FieldInfo
    ): VectorScorer? {
        val reader: LeafReader = context.reader()
        val vectorValues: ByteVectorValues? = reader.getByteVectorValues(field)
        if (vectorValues == null) {
            ByteVectorValues.checkField(reader, field)
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
        if (!super.equals(o)) return false
        val that = o as KnnByteVectorQuery
        return target.contentEquals(that.target)
    }

    override fun hashCode(): Int {
        return Objects.hash(super.hashCode(), target.contentHashCode())
    }

    val targetCopy: ByteArray
        /**
         * @return the target query vector of the search. Each vector element is a byte.
         */
        get() = ArrayUtil.copyArray(target)

    companion object {
        private val NO_RESULTS: TopDocs =
            TopDocsCollector.EMPTY_TOPDOCS
    }
}
