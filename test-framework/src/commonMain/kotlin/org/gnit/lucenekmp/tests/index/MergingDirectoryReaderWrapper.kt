package org.gnit.lucenekmp.tests.index

import okio.IOException
import org.gnit.lucenekmp.index.CodecReader
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.FilterDirectoryReader
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.LeafReader

/**
 * [DirectoryReader] wrapper that uses the merge instances of the wrapped [ ]s. NOTE: This class will fail to work if the leaves of the wrapped directory are not
 * codec readers.
 */
class MergingDirectoryReaderWrapper
/** Wrap the given directory.  */
    (`in`: DirectoryReader) : FilterDirectoryReader(
    `in`,
    object : SubReaderWrapper() {
        override fun wrap(reader: LeafReader): LeafReader {
            return MergingCodecReader(reader as CodecReader)
        }
    }) {
    @Throws(IOException::class)
    override fun doWrapDirectoryReader(`in`: DirectoryReader): DirectoryReader {
        return MergingDirectoryReaderWrapper(`in`)
    }

    override val readerCacheHelper: CacheHelper?
        get() =// doesn't change the content: can delegate
            `in`.readerCacheHelper
}
