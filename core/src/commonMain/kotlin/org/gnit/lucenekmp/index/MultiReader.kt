package org.gnit.lucenekmp.index

import kotlinx.coroutines.runBlocking
import okio.IOException

/**
 * A [CompositeReader] which reads multiple indexes, appending their content. It can be used
 * to create a view on several sub-readers (like [DirectoryReader]) and execute searches on
 * it.
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
class MultiReader(
    subReaders: Array<out IndexReader>,
    subReadersSorter: Comparator<IndexReader>?,
    private val closeSubReaders: Boolean
) : BaseCompositeReader<IndexReader>(
    subReaders.copyOf(),
    subReadersSorter
) {
    /**
     * Construct a MultiReader aggregating the named set of (sub)readers.
     *
     *
     * Note that all subreaders are closed if this Multireader is closed.
     *
     * @param subReaders set of (sub)readers
     */
    constructor(vararg subReaders: IndexReader) : this(subReaders, null, true)

    /**
     * Construct a MultiReader aggregating the named set of (sub)readers.
     *
     * @param subReaders set of (sub)readers; this array will be cloned.
     * @param closeSubReaders indicates whether the subreaders should be closed when this MultiReader
     * is closed
     */
    constructor(subReaders: Array<IndexReader>, closeSubReaders: Boolean) : this(
        subReaders,
        null,
        closeSubReaders
    )

    /**
     * Construct a MultiReader aggregating the named set of (sub)readers.
     *
     * @param subReaders set of (sub)readers; this array will be cloned.
     * @param subReadersSorter – a comparator, that if not `null` is used for sorting sub
     * readers.
     * @param closeSubReaders indicates whether the subreaders should be closed when this MultiReader
     * is closed
     */
    init {
        if (!closeSubReaders) {
            for (i in subReaders.indices) {
                subReaders[i].incRef()
            }
        }
    }

    override val readerCacheHelper: CacheHelper?
        get() {
            // MultiReader instances can be short-lived, which would make caching trappy
            // so we do not cache on them, unless they wrap a single reader in which
            // case we delegate
            if (sequentialSubReaders.size == 1) {
                return sequentialSubReaders[0].readerCacheHelper
            }
            return null
        }

    /*@Synchronized*/
    @Throws(IOException::class)
    override fun doClose() {
        var ioe: IOException? = null
        for (r in sequentialSubReaders) {
            try {
                if (closeSubReaders) {
                    r.close()
                } else {
                    runBlocking{ r.decRef() }
                }
            } catch (e: IOException) {
                if (ioe == null) ioe = e
            }
        }
        // throw the first exception
        if (ioe != null) throw ioe
    }
}
