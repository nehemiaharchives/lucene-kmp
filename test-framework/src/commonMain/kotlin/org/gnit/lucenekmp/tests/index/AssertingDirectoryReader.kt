package org.gnit.lucenekmp.tests.index

import okio.IOException
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.FilterDirectoryReader
import org.gnit.lucenekmp.index.LeafReader

/** A [DirectoryReader] that wraps all its subreaders with [AssertingLeafReader]  */
class AssertingDirectoryReader(`in`: DirectoryReader) :
    FilterDirectoryReader(`in`, AssertingSubReaderWrapper()) {
    internal class AssertingSubReaderWrapper :
        SubReaderWrapper() {
        override fun wrap(reader: LeafReader): LeafReader {
            return AssertingLeafReader(reader)
        }
    }

    @Throws(IOException::class)
    override fun doWrapDirectoryReader(`in`: DirectoryReader): DirectoryReader {
        return AssertingDirectoryReader(`in`)
    }

    override val readerCacheHelper: CacheHelper?
        get() = `in`.readerCacheHelper
}
