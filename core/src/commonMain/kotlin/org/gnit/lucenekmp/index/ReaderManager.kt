package org.gnit.lucenekmp.index

import kotlinx.coroutines.runBlocking
import okio.IOException
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.ReferenceManager
import org.gnit.lucenekmp.store.Directory

/**
 * Utility class to safely share [DirectoryReader] instances across multiple threads, while
 * periodically reopening. This class ensures each reader is closed only once all threads have
 * finished using it.
 *
 * @lucene.experimental
 */
class ReaderManager : ReferenceManager<DirectoryReader> {
    @Throws(IOException::class)
    constructor(writer: IndexWriter) : this(writer, true, false)

    /**
     * Creates and returns a new ReaderManager from the given [IndexWriter], controlling
     * whether past deletions should be applied.
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
     * @throws IOException If there is a low-level I/O error
     */
    @Throws(IOException::class)
    constructor(writer: IndexWriter, applyAllDeletes: Boolean, writeAllDeletes: Boolean) {
        current = DirectoryReader.open(writer, applyAllDeletes, writeAllDeletes)
    }

    /**
     * Creates and returns a new ReaderManager from the given [Directory].
     *
     * @param dir the directory to open the DirectoryReader on.
     * @throws IOException If there is a low-level I/O error
     */
    @Throws(IOException::class)
    constructor(dir: Directory) {
        current = DirectoryReader.open(dir)
    }

    /**
     * Creates and returns a new ReaderManager from the given already-opened [DirectoryReader],
     * stealing the incoming reference.
     *
     * @param reader the directoryReader to use for future reopens
     * @throws IOException If there is a low-level I/O error
     */
    @Throws(IOException::class)
    constructor(reader: DirectoryReader) {
        current = reader
    }

    @Throws(IOException::class)
    override fun decRef(reference: DirectoryReader) {
        runBlocking {
            reference.decRef()
        }
    }

    @Throws(IOException::class)
    override fun refreshIfNeeded(referenceToRefresh: DirectoryReader): DirectoryReader? {
        return DirectoryReader.openIfChanged(referenceToRefresh)
    }

    @Throws(IOException::class)
    override fun tryIncRef(reference: DirectoryReader): Boolean {
        return reference.tryIncRef()
    }

    override fun getRefCount(reference: DirectoryReader): Int {
        return reference.getRefCount()
    }
}
