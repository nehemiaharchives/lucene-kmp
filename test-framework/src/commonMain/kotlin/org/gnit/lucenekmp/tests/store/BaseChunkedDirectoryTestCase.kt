package org.gnit.lucenekmp.tests.store

import okio.Path
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.store.BaseDirectoryTestCase
import org.gnit.lucenekmp.tests.util.TestUtil

/**
 * Ported from Lucene's BaseChunkedDirectoryTestCase.
 * This is currently a skeleton to satisfy compilation.
 */
abstract class BaseChunkedDirectoryTestCase : BaseDirectoryTestCase() {
    /** Returns a new directory instance for the given path and chunk size. */
    @Throws(Exception::class)
    protected abstract fun getDirectory(path: Path, maxChunkSize: Int): Directory

    /** Returns a directory with a random chunk size. */
    @Throws(Exception::class)
    override fun getDirectory(path: Path): Directory {
        val chunk = 1 shl TestUtil.nextInt(random(), 10, 20)
        return getDirectory(path, chunk)
    }

    // The following test methods are placeholders for now.
    @Throws(Exception::class)
    open fun testGroupVIntMultiBlocks() {
        TODO("Not yet implemented")
    }

    @Throws(Exception::class)
    open fun testCloneClose() {
        TODO("Not yet implemented")
    }

    @Throws(Exception::class)
    open fun testCloneSliceClose() {
        TODO("Not yet implemented")
    }

    @Throws(Exception::class)
    open fun testSeekZero() {
        TODO("Not yet implemented")
    }

    @Throws(Exception::class)
    open fun testSeekSliceZero() {
        TODO("Not yet implemented")
    }

    @Throws(Exception::class)
    open fun testSeekEnd() {
        TODO("Not yet implemented")
    }

    @Throws(Exception::class)
    open fun testSeekSliceEnd() {
        TODO("Not yet implemented")
    }

    @Throws(Exception::class)
    open fun testSeeking() {
        TODO("Not yet implemented")
    }

    @Throws(Exception::class)
    open fun testSlicedSeeking() {
        TODO("Not yet implemented")
    }


    @Throws(Exception::class)
    open fun testRandomChunkSizes() {
        TODO("Not yet implemented")
    }

    @Throws(Exception::class)
    open fun testBytesCrossBoundary() {
        TODO("Not yet implemented")
    }

    @Throws(Exception::class)
    open fun testLittleEndianLongsCrossBoundary() {
        TODO("Not yet implemented")
    }

    @Throws(Exception::class)
    open fun testLittleEndianFloatsCrossBoundary() {
        TODO("Not yet implemented")
    }
}
