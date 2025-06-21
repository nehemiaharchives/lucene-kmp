package org.gnit.lucenekmp.index

import kotlinx.coroutines.runBlocking
import okio.IOException
import org.gnit.lucenekmp.store.AlreadyClosedException
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * IndexReader is an abstract class, providing an interface for accessing a point-in-time view of an
 * index. Any changes made to the index via [IndexWriter] will not be visible until a new
 * `IndexReader` is opened. It's best to use [DirectoryReader.open] to
 * obtain an `IndexReader`, if your [IndexWriter] is in-process. When you need to
 * re-open to see changes to the index, it's best to use [ ][DirectoryReader.openIfChanged] since the new reader will share resources with
 * the previous one when possible. Search of an index is done entirely through this abstract
 * interface, so that any subclass which implements it is searchable.
 *
 *
 * There are two different types of IndexReaders:
 *
 *
 *  * [LeafReader]: These indexes do not consist of several sub-readers, they are atomic.
 * They support retrieval of stored fields, doc values, terms, and postings.
 *  * [CompositeReader]: Instances (like [DirectoryReader]) of this reader can only
 * be used to get stored fields from the underlying LeafReaders, but it is not possible to
 * directly retrieve postings. To do that, get the sub-readers via [       ][CompositeReader.getSequentialSubReaders].
 *
 *
 *
 * IndexReader instances for indexes on disk are usually constructed with a call to one of the
 * static `DirectoryReader.open()` methods, e.g. [ ][DirectoryReader.open]. [DirectoryReader] implements the
 * [CompositeReader] interface, it is not possible to directly get postings.
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
abstract class IndexReader internal constructor() : AutoCloseable {
    private var closed = false
    private var closedByChild = false
    @OptIn(ExperimentalAtomicApi::class)
    private val refCount: AtomicInt = AtomicInt(1)

    /**
     * A utility class that gives hooks in order to help build a cache based on the data that is
     * contained in this index.
     *
     *
     * Example: cache the number of documents that match a query per reader.
     *
     * <pre class="prettyprint">
     * public class QueryCountCache {
     *
     * private final Query query;
     * private final Map&lt;IndexReader.CacheKey, Integer&gt; counts = new ConcurrentHashMap&lt;&gt;();
     *
     * // Create a cache of query counts for the given query
     * public QueryCountCache(Query query) {
     * this.query = query;
     * }
     *
     * // Count the number of matches of the query on the given IndexSearcher
     * public int count(IndexSearcher searcher) throws IOException {
     * IndexReader.CacheHelper cacheHelper = searcher.getIndexReader().getReaderCacheHelper();
     * if (cacheHelper == null) {
     * // reader doesn't support caching
     * return searcher.count(query);
     * } else {
     * // make sure the cache entry is cleared when the reader is closed
     * cacheHelper.addClosedListener(counts::remove);
     * return counts.computeIfAbsent(cacheHelper.getKey(), cacheKey -&gt; {
     * try {
     * return searcher.count(query);
     * } catch (IOException e) {
     * throw new UncheckedIOException(e);
     * }
     * });
     * }
     * }
     *
     * }
    </pre> *
     *
     * @lucene.experimental
     */
    interface CacheHelper {
        /**
         * Get a key that the resource can be cached on. The given entry can be compared using identity,
         * ie. [Object.equals] is implemented as `==` and [Object.hashCode] is
         * implemented as [System.identityHashCode].
         */
        val key: CacheKey

        /**
         * Add a [ClosedListener] which will be called when the resource guarded by [ ][.getKey] is closed.
         */
        suspend fun addClosedListener(listener: ClosedListener)
    }

    /** A cache key identifying a resource that is being cached on.  */
    class CacheKey internal constructor() // only instantiable by core impls


    /**
     * A listener that is called when a resource gets closed.
     *
     * @lucene.experimental
     */
    fun interface ClosedListener {
        /**
         * Invoked when the resource (segment core, or index reader) that is being cached on is closed.
         */
        @Throws(IOException::class)
        fun onClose(key: CacheKey)
    }

    @OptIn(ExperimentalAtomicApi::class)
    private val parentReaders = AtomicReference<MutableSet<IndexReader>>(mutableSetOf())

    /**
     * Expert: This method is called by `IndexReader`s which wrap other readers (e.g. [ ] or [FilterLeafReader]) to register the parent at the child (this reader)
     * on construction of the parent. When this reader is closed, it will mark all registered parents
     * as closed, too. The references to parent readers are weak only, so they can be GCed once they
     * are no longer in use.
     *
     * @lucene.experimental
     */
    @OptIn(ExperimentalAtomicApi::class)
    fun registerParentReader(reader: IndexReader) {
        ensureOpen()
        while (true) {
            val current = parentReaders.load()
            val updated = current + reader
            if (parentReaders.compareAndSet(current, updated as MutableSet<IndexReader>)) break
        }
    }

    /**
     * For test framework use only.
     *
     * @lucene.internal
     */
    protected open suspend fun notifyReaderClosedListeners() {
        // nothing to notify in the base impl
    }

    @OptIn(ExperimentalAtomicApi::class)
    @Throws(IOException::class)
    private fun reportCloseToParentReaders() {
        for (parent in parentReaders.load()) {
            parent.closedByChild = true
            // cross memory barrier by a fake write:
            parent.refCount.addAndFetch(0)
            // recurse:
            parent.reportCloseToParentReaders()
        }
    }

    /** Expert: returns the current refCount for this reader  */
    @OptIn(ExperimentalAtomicApi::class)
    fun getRefCount(): Int {
        // NOTE: don't ensureOpen, so that callers can see
        // refCount is 0 (reader is closed)
        return refCount.load()
    }

    /**
     * Expert: increments the refCount of this IndexReader instance. RefCounts are used to determine
     * when a reader can be closed safely, i.e. as soon as there are no more references. Be sure to
     * always call a corresponding [.decRef], in a finally clause; otherwise the reader may
     * never be closed. Note that [.close] simply calls decRef(), which means that the
     * IndexReader will not really be closed until [.decRef] has been called for all outstanding
     * references.
     *
     * @see .decRef
     *
     * @see .tryIncRef
     */
    fun incRef() {
        if (!tryIncRef()) {
            ensureOpen()
        }
    }

    /**
     * Expert: increments the refCount of this IndexReader instance only if the IndexReader has not
     * been closed yet and returns `true` iff the refCount was successfully incremented,
     * otherwise `false`. If this method returns `false` the reader is either
     * already closed or is currently being closed. Either way this reader instance shouldn't be used
     * by an application unless `true` is returned.
     *
     *
     * RefCounts are used to determine when a reader can be closed safely, i.e. as soon as there
     * are no more references. Be sure to always call a corresponding [.decRef], in a finally
     * clause; otherwise the reader may never be closed. Note that [.close] simply calls
     * decRef(), which means that the IndexReader will not really be closed until [.decRef] has
     * been called for all outstanding references.
     *
     * @see .decRef
     *
     * @see .incRef
     */
    @OptIn(ExperimentalAtomicApi::class)
    fun tryIncRef(): Boolean {
        var count: Int
        while ((refCount.load().also { count = it }) > 0) {
            if (refCount.compareAndSet(count, count + 1)) {
                return true
            }
        }
        return false
    }

    /**
     * Expert: decreases the refCount of this IndexReader instance. If the refCount drops to 0, then
     * this reader is closed. If an exception is hit, the refCount is unchanged.
     *
     * @throws IOException in case an IOException occurs in doClose()
     * @see .incRef
     */
    @OptIn(ExperimentalAtomicApi::class)
    suspend fun decRef() {
        // only check refcount here (don't call ensureOpen()), so we can
        // still close the reader if it was made invalid by a child:
        if (refCount.load() <= 0) {
            throw AlreadyClosedException("this IndexReader is closed")
        }

        val rc: Int = refCount.addAndFetch(-1)
        if (rc == 0) {
            closed = true
            this.reportCloseToParentReaders()
            this.notifyReaderClosedListeners()
            doClose()
        } else check(rc >= 0) { "too many decRef calls: refCount is $rc after decrement" }
    }

    /**
     * Throws AlreadyClosedException if this IndexReader or any of its child readers is closed,
     * otherwise returns.
     */
    @OptIn(ExperimentalAtomicApi::class)
    @Throws(AlreadyClosedException::class)
    protected fun ensureOpen() {
        if (refCount.load() <= 0) {
            throw AlreadyClosedException("this IndexReader is closed")
        }
        // the happens before rule on reading the refCount, which must be after the fake write,
        // ensures that we see the value:
        if (closedByChild) {
            throw AlreadyClosedException(
                "this IndexReader cannot be used anymore as one of its child readers was closed"
            )
        }
    }

    /**
     * {@inheritDoc}
     *
     *
     * `IndexReader` subclasses are not allowed to implement equals/hashCode, so methods are
     * declared final.
     */
    override fun equals(obj: Any?): Boolean {
        return (this === obj)
    }

    /**
     * {@inheritDoc}
     *
     *
     * `IndexReader` subclasses are not allowed to implement equals/hashCode, so methods are
     * declared final.
     */
    override fun hashCode(): Int {
        return super.hashCode()
    }

    /**
     * Returns a [TermVectors] reader for the term vectors of this index.
     *
     *
     * This call never returns `null`, even if no term vectors were indexed. The returned
     * instance should only be used by a single thread.
     *
     *
     * Example:
     *
     * <pre class="prettyprint">
     * TopDocs hits = searcher.search(query, 10);
     * TermVectors termVectors = reader.termVectors();
     * for (ScoreDoc hit : hits.scoreDocs) {
     * Fields vector = termVectors.get(hit.doc);
     * }
    </pre> *
     *
     * @throws IOException If there is a low-level IO error
     */
    @Throws(IOException::class)
    abstract fun termVectors(): TermVectors

    /**
     * Returns the number of documents in this index.
     *
     *
     * **NOTE**: This operation may run in O(maxDoc). Implementations that can't return this
     * number in constant-time should cache it.
     */
    abstract fun numDocs(): Int

    /**
     * Returns one greater than the largest possible document number. This may be used to, e.g.,
     * determine how big to allocate an array which will have an element for every document number in
     * an index.
     */
    abstract fun maxDoc(): Int

    /**
     * Returns the number of deleted documents.
     *
     *
     * **NOTE**: This operation may run in O(maxDoc).
     */
    fun numDeletedDocs(): Int {
        return maxDoc() - numDocs()
    }

    /**
     * Returns a [StoredFields] reader for the stored fields of this index.
     *
     *
     * This call never returns `null`, even if no stored fields were indexed. The returned
     * instance should only be used by a single thread.
     *
     *
     * Example:
     *
     * <pre class="prettyprint">
     * TopDocs hits = searcher.search(query, 10);
     * StoredFields storedFields = reader.storedFields();
     * for (ScoreDoc hit : hits.scoreDocs) {
     * Document doc = storedFields.document(hit.doc);
     * }
    </pre> *
     *
     * @throws IOException If there is a low-level IO error
     */
    @Throws(IOException::class)
    abstract fun storedFields(): StoredFields

    /**
     * Returns true if any documents have been deleted. Implementers should consider overriding this
     * method if [.maxDoc] or [.numDocs] are not constant-time operations.
     */
    fun hasDeletions(): Boolean {
        return numDeletedDocs() > 0
    }

    /**
     * Closes files associated with this index. Also saves any new deletions to disk. No other methods
     * should be called after this has been called.
     *
     * @throws IOException if there is a low-level IO error
     */
    override fun close() {
        if (!closed) {
            runBlocking {
                decRef()
            }
            closed = true
        }
    }

    /** Implements close.  */
    @Throws(IOException::class)
    protected abstract fun doClose()

    /**
     * Expert: Returns the root [IndexReaderContext] for this [IndexReader]'s sub-reader
     * tree.
     *
     *
     * Iff this reader is composed of sub readers, i.e. this reader being a composite reader, this
     * method returns a [CompositeReaderContext] holding the reader's direct children as well as
     * a view of the reader tree's atomic leaf contexts. All sub- [IndexReaderContext] instances
     * referenced from this readers top-level context are private to this reader and are not shared
     * with another context tree. For example, IndexSearcher uses this API to drive searching by one
     * atomic leaf reader at a time. If this reader is not composed of child readers, this method
     * returns an [LeafReaderContext].
     *
     *
     * Note: Any of the sub-[CompositeReaderContext] instances referenced from this top-level
     * context do not support [CompositeReaderContext.leaves]. Only the top-level context
     * maintains the convenience leaf-view for performance reasons.
     */
    abstract val context: IndexReaderContext

    /**
     * Returns the reader's leaves, or itself if this reader is atomic. This is a convenience method
     * calling `this.getContext().leaves()`.
     *
     * @see IndexReaderContext.leaves
     */
    fun leaves(): MutableList<LeafReaderContext> {
        return this.context.leaves()
    }

    /**
     * Optional method: Return a [CacheHelper] that can be used to cache based on the content of
     * this reader. Two readers that have different data or different sets of deleted documents will
     * be considered different.
     *
     *
     * A return value of `null` indicates that this reader is not suited for caching, which
     * is typically the case for short-lived wrappers that alter the content of the wrapped reader.
     *
     * @lucene.experimental
     */
    abstract val readerCacheHelper: CacheHelper?

    /**
     * Returns the number of documents containing the `term`. This method returns 0 if the
     * term or field does not exists. This method does not take into account deleted documents that
     * have not yet been merged away.
     *
     * @see TermsEnum.docFreq
     */
    @Throws(IOException::class)
    abstract fun docFreq(term: Term): Int

    /**
     * Returns the total number of occurrences of `term` across all documents (the sum of the
     * freq() for each doc that has this term). Note that, like other term measures, this measure does
     * not take deleted documents into account.
     */
    @Throws(IOException::class)
    abstract fun totalTermFreq(term: Term): Long

    /**
     * Returns the sum of [TermsEnum.docFreq] for all terms in this field. Note that, just
     * like other term measures, this measure does not take deleted documents into account.
     *
     * @see Terms.getSumDocFreq
     */
    @Throws(IOException::class)
    abstract fun getSumDocFreq(field: String): Long

    /**
     * Returns the number of documents that have at least one term for this field. Note that, just
     * like other term measures, this measure does not take deleted documents into account.
     *
     * @see Terms.getDocCount
     */
    @Throws(IOException::class)
    abstract fun getDocCount(field: String): Int

    /**
     * Returns the sum of [TermsEnum.totalTermFreq] for all terms in this field. Note that, just
     * like other term measures, this measure does not take deleted documents into account.
     *
     * @see Terms.getSumTotalTermFreq
     */
    @Throws(IOException::class)
    abstract fun getSumTotalTermFreq(field: String): Long
}
