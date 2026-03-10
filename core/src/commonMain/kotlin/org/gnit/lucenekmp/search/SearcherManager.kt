package org.gnit.lucenekmp.search

import kotlinx.coroutines.runBlocking
import okio.IOException
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.store.Directory

/**
 * Utility class to safely share [IndexSearcher] instances across multiple threads, while
 * periodically reopening. This class ensures each searcher is closed only once all threads have
 * finished using it.
 *
 * <p>Use [acquire] to obtain the current searcher, and [release] to release it, like this:
 *
 * <pre class="prettyprint">
 * IndexSearcher s = manager.acquire();
 * try {
 *   // Do searching, doc retrieval, etc. with s
 * } finally {
 *   manager.release(s);
 * }
 * // Do not use s after this!
 * s = null;
 * </pre>
 *
 * <p>In addition you should periodically call [maybeRefresh]. While it's possible to call
 * this just before running each query, this is discouraged since it penalizes the unlucky queries
 * that need to refresh. It's better to use a separate background thread, that periodically calls
 * [maybeRefresh]. Finally, be sure to call [close] once you are done.
 *
 * @see SearcherFactory
 * @lucene.experimental
 */
class SearcherManager : ReferenceManager<IndexSearcher> {
    private val searcherFactory: SearcherFactory

    /**
     * Creates and returns a new SearcherManager from the given [IndexWriter].
     *
     * @param writer the IndexWriter to open the IndexReader from.
     * @param searcherFactory An optional [SearcherFactory]. Pass `null` if you don't
     * require the searcher to be warmed before going live or other custom behavior.
     * @throws IOException if there is a low-level I/O error
     */
    @Throws(IOException::class)
    constructor(writer: IndexWriter, searcherFactory: SearcherFactory?) : this(
        writer,
        true,
        false,
        searcherFactory
    )

    /**
     * Expert: creates and returns a new SearcherManager from the given [IndexWriter],
     * controlling whether past deletions should be applied.
     *
     * @param writer the IndexWriter to open the IndexReader from.
     * @param applyAllDeletes If `true`, all buffered deletes will be applied (made
     * visible) in the [IndexSearcher] / [DirectoryReader]. If `false`, the
     * deletes may or may not be applied, but remain buffered (in IndexWriter) so that they will
     * be applied in the future. Applying deletes can be costly, so if your app can tolerate
     * deleted documents being returned you might gain some performance by passing `false`.
     * See [DirectoryReader.openIfChanged].
     * @param writeAllDeletes If `true`, new deletes will be forcefully written to index
     * files.
     * @param searcherFactory An optional [SearcherFactory]. Pass `null` if you don't
     * require the searcher to be warmed before going live or other custom behavior.
     * @throws IOException if there is a low-level I/O error
     */
    @Throws(IOException::class)
    constructor(
        writer: IndexWriter,
        applyAllDeletes: Boolean,
        writeAllDeletes: Boolean,
        searcherFactory: SearcherFactory?
    ) {
        val actualSearcherFactory = searcherFactory ?: SearcherFactory()
        this.searcherFactory = actualSearcherFactory
        current = getSearcher(
            actualSearcherFactory,
            DirectoryReader.open(writer, applyAllDeletes, writeAllDeletes),
            null
        )
    }

    /**
     * Creates and returns a new SearcherManager from the given [Directory].
     *
     * @param dir the directory to open the DirectoryReader on.
     * @param searcherFactory An optional [SearcherFactory]. Pass `null` if you don't
     * require the searcher to be warmed before going live or other custom behavior.
     * @throws IOException if there is a low-level I/O error
     */
    @Throws(IOException::class)
    constructor(dir: Directory, searcherFactory: SearcherFactory?) {
        val actualSearcherFactory = searcherFactory ?: SearcherFactory()
        this.searcherFactory = actualSearcherFactory
        current = getSearcher(actualSearcherFactory, DirectoryReader.open(dir), null)
    }

    /**
     * Creates and returns a new SearcherManager from an existing [DirectoryReader]. Note that
     * this steals the incoming reference.
     *
     * @param reader the DirectoryReader.
     * @param searcherFactory An optional [SearcherFactory]. Pass `null` if you don't
     * require the searcher to be warmed before going live or other custom behavior.
     * @throws IOException if there is a low-level I/O error
     */
    @Throws(IOException::class)
    constructor(reader: DirectoryReader, searcherFactory: SearcherFactory?) {
        val actualSearcherFactory = searcherFactory ?: SearcherFactory()
        this.searcherFactory = actualSearcherFactory
        current = getSearcher(actualSearcherFactory, reader, null)
    }

    @Throws(IOException::class)
    override fun decRef(reference: IndexSearcher) {
        runBlocking {
            reference.indexReader.decRef()
        }
    }

    @Throws(IOException::class)
    override fun refreshIfNeeded(referenceToRefresh: IndexSearcher): IndexSearcher? {
        val r = referenceToRefresh.indexReader
        assert(r is DirectoryReader) { "searcher's IndexReader should be a DirectoryReader, but got $r" }
        val dirReader = r as DirectoryReader
        val newReader = DirectoryReader.openIfChanged(dirReader)
        return if (newReader == null) {
            null
        } else {
            getSearcher(searcherFactory, newReader, r)
        }
    }

    override fun tryIncRef(reference: IndexSearcher): Boolean {
        return reference.indexReader.tryIncRef()
    }

    override fun getRefCount(reference: IndexSearcher): Int {
        return reference.indexReader.getRefCount()
    }

    /**
     * Returns `true` if no changes have occurred since this searcher ie. reader was
     * opened, otherwise `false`.
     *
     * @see DirectoryReader.isCurrent
     */
    @Throws(IOException::class)
    fun isSearcherCurrent(): Boolean {
        val searcher = acquire()
        try {
            val r = searcher.indexReader
            assert(r is DirectoryReader) { "searcher's IndexReader should be a DirectoryReader, but got $r" }
            return (r as DirectoryReader).isCurrent
        } finally {
            release(searcher)
        }
    }

    companion object {
        /**
         * Expert: creates a searcher from the provided [IndexReader] using the provided
         * [SearcherFactory]. NOTE: this decRefs incoming reader on throwing an exception.
         */
        @Throws(IOException::class)
        fun getSearcher(
            searcherFactory: SearcherFactory,
            reader: IndexReader,
            previousReader: IndexReader?
        ): IndexSearcher {
            var success = false
            return try {
                val searcher = searcherFactory.newSearcher(reader, previousReader)
                if (searcher.indexReader !== reader) {
                    throw IllegalStateException(
                        "SearcherFactory must wrap exactly the provided reader (got ${searcher.indexReader} but expected $reader)"
                    )
                }
                success = true
                searcher
            } finally {
                if (!success) {
                    runBlocking {
                        reader.decRef()
                    }
                }
            }
        }
    }
}
