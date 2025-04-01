package org.gnit.lucenekmp.index

import kotlinx.io.IOException
import org.gnit.lucenekmp.search.KnnCollector
import org.gnit.lucenekmp.search.TopDocs
import org.gnit.lucenekmp.search.TopDocsCollector
import org.gnit.lucenekmp.search.TopKnnCollector
import org.gnit.lucenekmp.util.Bits

/**
 * `LeafReader` is an abstract class, providing an interface for accessing an index. Search of
 * an index is done entirely through this abstract interface, so that any subclass which implements
 * it is searchable. IndexReaders implemented by this subclass do not consist of several
 * sub-readers, they are atomic. They support retrieval of stored fields, doc values, terms, and
 * postings.
 *
 *
 * For efficiency, in this API documents are often referred to via *document numbers*,
 * non-negative integers which each name a unique document in the index. These document numbers are
 * ephemeral -- they may change as documents are added to and deleted from an index. Clients should
 * thus not rely on a given document having the same number between sessions.
 *
 *
 * <a id="thread-safety"></a>
 *
 *
 * **NOTE**: [IndexReader] instances are completely thread safe, meaning multiple
 * threads can call any of its methods, concurrently. If your application requires external
 * synchronization, you should **not** synchronize on the `IndexReader` instance; use
 * your own (non-Lucene) objects instead.
 */
abstract class LeafReader
/** Sole constructor. (For invocation by subclass constructors, typically implicit.)  */
protected constructor() : IndexReader() {
    private val readerContext: LeafReaderContext = LeafReaderContext(this)

    override val context: LeafReaderContext
        get() {
            ensureOpen()
            return readerContext
        }

    /**
     * Optional method: Return a [IndexReader.CacheHelper] that can be used to cache based on
     * the content of this leaf regardless of deletions. Two readers that have the same data but
     * different sets of deleted documents or doc values updates may be considered equal. Consider
     * using [.getReaderCacheHelper] if you need deletions or dv updates to be taken into
     * account.
     *
     *
     * A return value of `null` indicates that this reader is not suited for caching, which
     * is typically the case for short-lived wrappers that alter the content of the wrapped leaf
     * reader.
     *
     * @lucene.experimental
     */
    abstract val coreCacheHelper: CacheHelper

    @Throws(IOException::class)
    public override fun docFreq(term: Term): Int {
        val terms: Terms = Terms.getTerms(this, term.field())
        val termsEnum: TermsEnum = terms.iterator()
        if (termsEnum.seekExact(term.bytes())) {
            return termsEnum.docFreq()
        } else {
            return 0
        }
    }

    /**
     * Returns the number of documents containing the term `t`. This method returns 0 if
     * the term or field does not exists. This method does not take into account deleted documents
     * that have not yet been merged away.
     */
    @Throws(IOException::class)
    override fun totalTermFreq(term: Term): Long {
        val terms: Terms = Terms.getTerms(this, term.field())
        val termsEnum: TermsEnum = terms.iterator()
        if (termsEnum.seekExact(term.bytes())) {
            return termsEnum.totalTermFreq()
        } else {
            return 0
        }
    }

    @Throws(IOException::class)
    override fun getSumDocFreq(field: String): Long {
        val terms: Terms? = terms(field)
        if (terms == null) {
            return 0
        }
        return terms.getSumDocFreq()
    }

    @Throws(IOException::class)
    override fun getDocCount(field: String): Int {
        val terms: Terms? = terms(field)
        if (terms == null) {
            return 0
        }
        return terms.getDocCount()
    }

    @Throws(IOException::class)
    override fun getSumTotalTermFreq(field: String): Long {
        val terms: Terms? = terms(field)
        if (terms == null) {
            return 0
        }
        return terms.getSumTotalTermFreq()
    }

    /** Returns the [Terms] index for this field, or null if it has none.  */
    @Throws(IOException::class)
    abstract fun terms(field: String): Terms?

    /**
     * Returns [PostingsEnum] for the specified term. This will return null if either the field
     * or term does not exist.
     *
     *
     * **NOTE:** The returned [PostingsEnum] may contain deleted docs.
     *
     * @see TermsEnum.postings
     */
    @Throws(IOException::class)
    fun postings(term: Term, flags: Int): PostingsEnum? {
        checkNotNull(term.field())
        checkNotNull(term.bytes())
        val terms: Terms = Terms.getTerms(this, term.field())
        val termsEnum: TermsEnum = terms.iterator()
        if (termsEnum.seekExact(term.bytes())) {
            return termsEnum.postings(null, flags)
        }
        return null
    }

    /**
     * Returns [PostingsEnum] for the specified term with [PostingsEnum.FREQS].
     *
     *
     * Use this method if you only require documents and frequencies, and do not need any proximity
     * data. This method is equivalent to [postings(term,][.postings]
     *
     *
     * **NOTE:** The returned [PostingsEnum] may contain deleted docs.
     *
     * @see .postings
     */
    @Throws(IOException::class)
    fun postings(term: Term): PostingsEnum? {
        return postings(term, PostingsEnum.FREQS.toInt())
    }

    /**
     * Returns [NumericDocValues] for this field, or null if no numeric doc values were indexed
     * for this field. The returned instance should only be used by a single thread.
     */
    @Throws(IOException::class)
    abstract fun getNumericDocValues(field: String): NumericDocValues

    /**
     * Returns [BinaryDocValues] for this field, or null if no binary doc values were indexed
     * for this field. The returned instance should only be used by a single thread.
     */
    @Throws(IOException::class)
    abstract fun getBinaryDocValues(field: String): BinaryDocValues

    /**
     * Returns [SortedDocValues] for this field, or null if no [SortedDocValues] were
     * indexed for this field. The returned instance should only be used by a single thread.
     */
    @Throws(IOException::class)
    abstract fun getSortedDocValues(field: String): SortedDocValues

    /**
     * Returns [SortedNumericDocValues] for this field, or null if no [ ] were indexed for this field. The returned instance should only be used
     * by a single thread.
     */
    @Throws(IOException::class)
    abstract fun getSortedNumericDocValues(field: String): SortedNumericDocValues

    /**
     * Returns [SortedSetDocValues] for this field, or null if no [SortedSetDocValues]
     * were indexed for this field. The returned instance should only be used by a single thread.
     */
    @Throws(IOException::class)
    abstract fun getSortedSetDocValues(field: String): SortedSetDocValues

    /**
     * Returns [NumericDocValues] representing norms for this field, or null if no [ ] were indexed. The returned instance should only be used by a single thread.
     */
    @Throws(IOException::class)
    abstract fun getNormValues(field: String): NumericDocValues

    /**
     * Returns a [DocValuesSkipper] allowing skipping ranges of doc IDs that are not of
     * interest, or `null` if a skip index was not indexed. The returned instance should be
     * confined to the thread that created it.
     */
    @Throws(IOException::class)
    abstract fun getDocValuesSkipper(field: String): DocValuesSkipper?

    /**
     * Returns [FloatVectorValues] for this field, or null if no [FloatVectorValues] were
     * indexed. The returned instance should only be used by a single thread.
     *
     * @lucene.experimental
     */
    @Throws(IOException::class)
    abstract fun getFloatVectorValues(field: String): FloatVectorValues?

    /**
     * Returns [ByteVectorValues] for this field, or null if no [ByteVectorValues] were
     * indexed. The returned instance should only be used by a single thread.
     *
     * @lucene.experimental
     */
    @Throws(IOException::class)
    abstract fun getByteVectorValues(field: String): ByteVectorValues

    /**
     * Return the k nearest neighbor documents as determined by comparison of their vector values for
     * this field, to the given vector, by the field's similarity function. The score of each document
     * is derived from the vector similarity in a way that ensures scores are positive and that a
     * larger score corresponds to a higher ranking.
     *
     *
     * The search is allowed to be approximate, meaning the results are not guaranteed to be the
     * true k closest neighbors. For large values of k (for example when k is close to the total
     * number of documents), the search may also retrieve fewer than k documents.
     *
     *
     * The returned [TopDocs] will contain a [ScoreDoc] for each nearest neighbor,
     * sorted in order of their similarity to the query vector (decreasing scores). The [ ] contains the number of documents visited during the search. If the search stopped
     * early because it hit `visitedLimit`, it is indicated through the relation `TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO`.
     *
     * @param field the vector field to search
     * @param target the vector-valued query
     * @param k the number of docs to return
     * @param acceptDocs [Bits] that represents the allowed documents to match, or `null`
     * if they are all allowed to match.
     * @param visitedLimit the maximum number of nodes that the search is allowed to visit
     * @return the k nearest neighbor documents, along with their (searchStrategy-specific) scores.
     * @lucene.experimental
     */
    @Throws(IOException::class)
    fun searchNearestVectors(
        field: String, target: FloatArray, k: Int, acceptDocs: Bits, visitedLimit: Int
    ): TopDocs {
        var k = k
        val fi: FieldInfo? = this.fieldInfos.fieldInfo(field)
        if (fi == null || fi.vectorDimension == 0) {
            return TopDocsCollector.EMPTY_TOPDOCS
        }
        val floatVectorValues: FloatVectorValues? = getFloatVectorValues(fi.name)
        if (floatVectorValues == null) {
            return TopDocsCollector.EMPTY_TOPDOCS
        }
        k = kotlin.math.min(k, floatVectorValues.size())
        if (k == 0) {
            return TopDocsCollector.EMPTY_TOPDOCS
        }
        val collector: KnnCollector = TopKnnCollector(k, visitedLimit)
        searchNearestVectors(field, target, collector, acceptDocs)
        return collector.topDocs()
    }

    /**
     * Return the k nearest neighbor documents as determined by comparison of their vector values for
     * this field, to the given vector, by the field's similarity function. The score of each document
     * is derived from the vector similarity in a way that ensures scores are positive and that a
     * larger score corresponds to a higher ranking.
     *
     *
     * The search is allowed to be approximate, meaning the results are not guaranteed to be the
     * true k closest neighbors. For large values of k (for example when k is close to the total
     * number of documents), the search may also retrieve fewer than k documents.
     *
     *
     * The returned [TopDocs] will contain a [ScoreDoc] for each nearest neighbor,
     * sorted in order of their similarity to the query vector (decreasing scores). The [ ] contains the number of documents visited during the search. If the search stopped
     * early because it hit `visitedLimit`, it is indicated through the relation `TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO`.
     *
     * @param field the vector field to search
     * @param target the vector-valued query
     * @param k the number of docs to return
     * @param acceptDocs [Bits] that represents the allowed documents to match, or `null`
     * if they are all allowed to match.
     * @param visitedLimit the maximum number of nodes that the search is allowed to visit
     * @return the k nearest neighbor documents, along with their (searchStrategy-specific) scores.
     * @lucene.experimental
     */
    @Throws(IOException::class)
    fun searchNearestVectors(
        field: String, target: ByteArray, k: Int, acceptDocs: Bits, visitedLimit: Int
    ): TopDocs {
        var k = k
        val fi: FieldInfo? = this.fieldInfos.fieldInfo(field)
        if (fi == null || fi.vectorDimension == 0) {
            return TopDocsCollector.EMPTY_TOPDOCS
        }
        val byteVectorValues: ByteVectorValues = getByteVectorValues(fi.name)
        if (byteVectorValues == null) {
            return TopDocsCollector.EMPTY_TOPDOCS
        }
        k = kotlin.math.min(k, byteVectorValues.size())
        if (k == 0) {
            return TopDocsCollector.EMPTY_TOPDOCS
        }
        val collector: KnnCollector = TopKnnCollector(k, visitedLimit)
        searchNearestVectors(field, target, collector, acceptDocs)
        return collector.topDocs()
    }

    /**
     * Return the k nearest neighbor documents as determined by comparison of their vector values for
     * this field, to the given vector, by the field's similarity function. The score of each document
     * is derived from the vector similarity in a way that ensures scores are positive and that a
     * larger score corresponds to a higher ranking.
     *
     *
     * The search is allowed to be approximate, meaning the results are not guaranteed to be the
     * true k closest neighbors. For large values of k (for example when k is close to the total
     * number of documents), the search may also retrieve fewer than k documents.
     *
     *
     * The returned [TopDocs] will contain a [ScoreDoc] for each nearest neighbor, in
     * order of their similarity to the query vector (decreasing scores). The [TotalHits]
     * contains the number of documents visited during the search. If the search stopped early because
     * it hit `visitedLimit`, it is indicated through the relation `TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO`.
     *
     *
     * The behavior is undefined if the given field doesn't have KNN vectors enabled on its [ ]. The return value is never `null`.
     *
     * @param field the vector field to search
     * @param target the vector-valued query
     * @param knnCollector collector with settings for gathering the vector results.
     * @param acceptDocs [Bits] that represents the allowed documents to match, or `null`
     * if they are all allowed to match.
     * @lucene.experimental
     */
    @Throws(IOException::class)
    abstract fun searchNearestVectors(
        field: String, target: FloatArray, knnCollector: KnnCollector, acceptDocs: Bits
    )

    /**
     * Return the k nearest neighbor documents as determined by comparison of their vector values for
     * this field, to the given vector, by the field's similarity function. The score of each document
     * is derived from the vector similarity in a way that ensures scores are positive and that a
     * larger score corresponds to a higher ranking.
     *
     *
     * The search is allowed to be approximate, meaning the results are not guaranteed to be the
     * true k closest neighbors. For large values of k (for example when k is close to the total
     * number of documents), the search may also retrieve fewer than k documents.
     *
     *
     * The returned [TopDocs] will contain a [ScoreDoc] for each nearest neighbor, in
     * order of their similarity to the query vector (decreasing scores). The [TotalHits]
     * contains the number of documents visited during the search. If the search stopped early because
     * it hit `visitedLimit`, it is indicated through the relation `TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO`.
     *
     *
     * The behavior is undefined if the given field doesn't have KNN vectors enabled on its [ ]. The return value is never `null`.
     *
     * @param field the vector field to search
     * @param target the vector-valued query
     * @param knnCollector collector with settings for gathering the vector results.
     * @param acceptDocs [Bits] that represents the allowed documents to match, or `null`
     * if they are all allowed to match.
     * @lucene.experimental
     */
    @Throws(IOException::class)
    abstract fun searchNearestVectors(
        field: String, target: ByteArray, knnCollector: KnnCollector, acceptDocs: Bits
    )

    /**
     * Get the [FieldInfos] describing all fields in this reader.
     *
     *
     * Note: Implementations should cache the FieldInfos instance returned by this method such that
     * subsequent calls to this method return the same instance.
     *
     * @lucene.experimental
     */
    abstract val fieldInfos: FieldInfos

    /**
     * Returns the [Bits] representing live (not deleted) docs. A set bit indicates the doc ID
     * has not been deleted. If this method returns null it means there are no deleted documents (all
     * documents are live).
     *
     *
     * The returned instance has been safely published for use by multiple threads without
     * additional synchronization.
     */
    abstract val liveDocs: Bits?

    /**
     * Returns the [PointValues] used for numeric or spatial searches for the given field, or
     * null if there are no point fields.
     */
    @Throws(IOException::class)
    abstract fun getPointValues(field: String): PointValues?

    /**
     * Checks consistency of this reader.
     *
     *
     * Note that this may be costly in terms of I/O, e.g. may involve computing a checksum value
     * against large data files.
     *
     * @lucene.internal
     */
    @Throws(IOException::class)
    abstract fun checkIntegrity()

    /**
     * Return metadata about this leaf.
     *
     * @lucene.experimental
     */
    abstract val metaData: LeafMetaData
}
