package org.gnit.lucenekmp.index

import kotlinx.coroutines.runBlocking
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.IOUtils
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

@OptIn(ExperimentalAtomicApi::class)
class TestFilterDirectoryReader : LuceneTestCase() {

    private class DummySubReaderWrapper : FilterDirectoryReader.SubReaderWrapper() {
        override fun wrap(reader: LeafReader): LeafReader {
            return reader
        }
    }

    private class DummyFilterDirectoryReader(inReader: DirectoryReader) : FilterDirectoryReader(
        inReader,
        DummySubReaderWrapper()
    ) {
        override fun doWrapDirectoryReader(`in`: DirectoryReader): DirectoryReader {
            return DummyFilterDirectoryReader(`in`)
        }

        override val readerCacheHelper: IndexReader.CacheHelper?
            get() = `in`.readerCacheHelper
    }

    @Test
    fun testDoubleClose() {
        newDirectory().use { dir ->
            IndexWriter(dir, newIndexWriterConfig()).use { w ->
                w.addDocument(Document())

                val reader = DirectoryReader.open(w)
                val wrapped = DummyFilterDirectoryReader(reader)

                if (random().nextInt(2) == 0) {
                    IOUtils.close(reader, wrapped)
                } else {
                    IOUtils.close(wrapped, reader)
                }
            }
        }
    }

    private class NumDocsCountingSubReaderWrapper(private val numDocsCallCount: AtomicLong) :
        FilterDirectoryReader.SubReaderWrapper() {
        override fun wrap(reader: LeafReader): LeafReader {
            return object : FilterLeafReader(reader) {
                @OptIn(ExperimentalAtomicApi::class)
                override fun numDocs(): Int {
                    numDocsCallCount.incrementAndFetch()
                    return super.numDocs()
                }

                override val coreCacheHelper: IndexReader.CacheHelper?
                    get() = `in`.coreCacheHelper

                override val readerCacheHelper: IndexReader.CacheHelper?
                    get() = `in`.readerCacheHelper
            }
        }
    }

    private class NumDocsCountingFilterDirectoryReader(
        inReader: DirectoryReader,
        private val numDocsCallCount: AtomicLong
    ) : FilterDirectoryReader(inReader, NumDocsCountingSubReaderWrapper(numDocsCallCount)) {
        override fun doWrapDirectoryReader(`in`: DirectoryReader): DirectoryReader {
            return NumDocsCountingFilterDirectoryReader(`in`, numDocsCallCount)
        }

        override val readerCacheHelper: IndexReader.CacheHelper?
            get() = `in`.readerCacheHelper
    }

    @OptIn(ExperimentalAtomicApi::class)
    @Test
    fun testFilterDirectoryReaderNumDocsIsLazy() {
        newDirectory().use { dir ->
            IndexWriter(dir, newIndexWriterConfig()).use { w ->
                w.addDocument(Document())
                val directoryReader = DirectoryReader.open(w)

                val numDocsCallCount = AtomicLong(0L)
                val directoryReaderWrapper =
                    NumDocsCountingFilterDirectoryReader(directoryReader, numDocsCallCount)
                assertEquals(0L, numDocsCallCount.load())
                assertEquals(1, directoryReaderWrapper.numDocs())
                assertEquals(1L, numDocsCallCount.load())
                assertEquals(1, directoryReaderWrapper.numDocs())
                assertEquals(1L, numDocsCallCount.load())

                directoryReader.close()
            }
        }
    }

    private class DummyLastingFilterDirectoryReader(inReader: DirectoryReader) : FilterDirectoryReader(
        inReader,
        DummySubReaderWrapper()
    ) {
        private val cacheHelper: IndexReader.CacheHelper? =
            if (inReader.readerCacheHelper == null) {
                null
            } else {
                DelegatingCacheHelper(inReader.readerCacheHelper!!)
            }

        override fun doWrapDirectoryReader(`in`: DirectoryReader): DirectoryReader {
            return DummyFilterDirectoryReader(`in`)
        }

        override val readerCacheHelper: IndexReader.CacheHelper?
            get() = cacheHelper
    }

    @Test
    fun testDelegatingCacheHelper() {
        newDirectory().use { dir ->
            IndexWriter(dir, newIndexWriterConfig()).use { w ->
                w.addDocument(Document())

                val reader = DirectoryReader.open(w)
                val wrapped = DummyLastingFilterDirectoryReader(reader)

                assertNotEquals(reader.readerCacheHelper, wrapped.readerCacheHelper)
                assertNotEquals(reader.readerCacheHelper!!.key, wrapped.readerCacheHelper!!.key)

                var closeCalledCounter = 0

                runBlocking {
                    wrapped.readerCacheHelper!!.addClosedListener { key ->
                        closeCalledCounter++
                        assertSame(key, wrapped.readerCacheHelper!!.key)
                    }
                }

                reader.close()
                assertEquals(1, closeCalledCounter)
                wrapped.close()
                assertEquals(1, closeCalledCounter)
            }
        }
    }
}
