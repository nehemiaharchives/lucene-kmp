package org.gnit.lucenekmp.internal.tests


import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.SegmentCommitInfo
import okio.IOException

/**
 * Access to [IndexWriter] internals exposed to the test framework.
 *
 * @lucene.internal
 */
interface IndexWriterAccess {
    fun segString(iw: IndexWriter): String

    fun getSegmentCount(iw: IndexWriter): Int

    fun isClosed(iw: IndexWriter): Boolean

    @Throws(IOException::class)
    fun getReader(
        iw: IndexWriter,
        applyDeletions: Boolean,
        writeAllDeletes: Boolean
    ): DirectoryReader

    fun getDocWriterThreadPoolSize(iw: IndexWriter): Int

    fun isDeleterClosed(iw: IndexWriter): Boolean

    fun newestSegment(iw: IndexWriter): SegmentCommitInfo
}
