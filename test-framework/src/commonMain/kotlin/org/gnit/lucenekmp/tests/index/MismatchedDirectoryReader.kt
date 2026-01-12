package org.gnit.lucenekmp.tests.index

import okio.IOException
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.FilterDirectoryReader
import org.gnit.lucenekmp.index.LeafReader
import kotlin.random.Random

/** A [DirectoryReader] that wraps all its subreaders with [MismatchedLeafReader]  */
class MismatchedDirectoryReader(
    `in`: DirectoryReader,
    random: Random
) : FilterDirectoryReader(`in`, MismatchedSubReaderWrapper(random)) {
    internal class MismatchedSubReaderWrapper(val random: Random) :
        SubReaderWrapper() {

        override fun wrap(reader: LeafReader): LeafReader {
            return MismatchedLeafReader(reader, random)
        }
    }

    @Throws(IOException::class)
    override fun doWrapDirectoryReader(`in`: DirectoryReader): DirectoryReader {
        return AssertingDirectoryReader(`in`)
    }

    override val readerCacheHelper: CacheHelper?
        get() = `in`.readerCacheHelper
}
