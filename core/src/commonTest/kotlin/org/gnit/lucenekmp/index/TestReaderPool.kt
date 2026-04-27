package org.gnit.lucenekmp.index

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Runnable
import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.jdkport.CountDownLatch
import org.gnit.lucenekmp.jdkport.Thread
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.NullInfoStream
import org.gnit.lucenekmp.tests.util.RandomPicks
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOSupplier
import org.gnit.lucenekmp.util.IOUtils
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TestReaderPool: LuceneTestCase() {

    @Test
    @Throws(IOException::class)
    fun testDrop() {
        val directory: Directory = newDirectory()
        val fieldNumbers: FieldInfos.FieldNumbers = buildIndex(directory)
        val reader: StandardDirectoryReader = DirectoryReader.open(directory) as StandardDirectoryReader
        val segmentInfos: SegmentInfos = reader.segmentInfos.clone()

        val pool =
            ReaderPool(
                directory, directory, segmentInfos, fieldNumbers, { 0L }, NullInfoStream(), null, null
            )
        val commitInfo: SegmentCommitInfo = RandomPicks.randomFrom(random(), segmentInfos.asList())
        val readersAndUpdates: ReadersAndUpdates = checkNotNull(pool.get(commitInfo, true))
        assertSame(readersAndUpdates, pool.get(commitInfo, false))
        assertTrue(pool.drop(commitInfo))
        if (random().nextBoolean()) {
            assertFalse(pool.drop(commitInfo))
        }
        assertNull(pool.get(commitInfo, false))
        pool.release(readersAndUpdates, random().nextBoolean())
        IOUtils.close(pool, reader, directory)
    }

    @Test
    @Throws(IOException::class)
    fun testPoolReaders() {
        val directory: Directory = newDirectory()
        val fieldNumbers: FieldInfos.FieldNumbers = buildIndex(directory)
        val reader: StandardDirectoryReader = DirectoryReader.open(directory) as StandardDirectoryReader
        val segmentInfos: SegmentInfos = reader.segmentInfos.clone()

        val pool =
            ReaderPool(
                directory, directory, segmentInfos, fieldNumbers,  { 0L }, NullInfoStream(), null, null
            )
        val commitInfo: SegmentCommitInfo = RandomPicks.randomFrom(random(), segmentInfos.asList())
        assertFalse(pool.isReaderPoolingEnabled)
        pool.release(checkNotNull(pool.get(commitInfo, true)), random().nextBoolean())
        assertNull(pool.get(commitInfo, false))
        // now start pooling
        pool.enableReaderPooling()
        assertTrue(pool.isReaderPoolingEnabled)
        pool.release(checkNotNull(pool.get(commitInfo, true)), random().nextBoolean())
        assertNotNull(pool.get(commitInfo, false))
        assertSame(pool.get(commitInfo, false), pool.get(commitInfo, false))
        pool.drop(commitInfo)
        var ramBytesUsed: Long = 0
        assertEquals(0, pool.ramBytesUsed())
        for (info in segmentInfos) {
            pool.release(checkNotNull(pool.get(info, true)), random().nextBoolean())
            assertEquals(0, pool.ramBytesUsed(), " used: " + ramBytesUsed + " actual: " + pool.ramBytesUsed())
            ramBytesUsed = pool.ramBytesUsed()
            assertSame(pool.get(info, false), pool.get(info, false))
        }
        // Preserve the upstream Java boxed-identity check: Integer(0) is never the same object as Long ramBytesUsed.
        assertTrue((0 as Any) !== (pool.ramBytesUsed() as Any))
        pool.dropAll()
        for (info in segmentInfos) {
            assertNull(pool.get(info, false))
        }
        assertEquals(0, pool.ramBytesUsed())
        IOUtils.close(pool, reader, directory)
    }

    @Test
    @Throws(IOException::class)
    fun testUpdate() {
        val directory: Directory = newDirectory()
        val fieldNumbers: FieldInfos.FieldNumbers = buildIndex(directory)
        val reader: StandardDirectoryReader = DirectoryReader.open(directory) as StandardDirectoryReader
        val segmentInfos: SegmentInfos = reader.segmentInfos.clone()
        val pool =
            ReaderPool(
                directory,
                directory,
                segmentInfos,
                fieldNumbers,
                 { 0L },
                NullInfoStream(),
                null,
                null
            )
        val id: Int = random().nextInt(10)
        if (random().nextBoolean()) {
            pool.enableReaderPooling()
        }
        for (commitInfo in segmentInfos) {
            var readersAndUpdates: ReadersAndUpdates = checkNotNull(pool.get(commitInfo, true))
            val readOnlyClone: SegmentReader = readersAndUpdates.getReadOnlyClone(IOContext.DEFAULT)
            val postings: PostingsEnum? = readOnlyClone.postings(Term("id", "" + id))
            var expectUpdate = false
            var doc = -1
            if (postings != null && postings.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                val number =
                    NumericDocValuesFieldUpdates(0, "number", commitInfo.info.maxDoc())
                number.add(postings.docID().also { doc = it }, 1000L)
                number.finish()
                readersAndUpdates.addDVUpdate(number)
                expectUpdate = true
                assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), postings.nextDoc().toLong())
                assertTrue(pool.anyDocValuesChanges())
            } else {
                assertFalse(pool.anyDocValuesChanges())
            }
            readOnlyClone.close()
            val writtenToDisk: Boolean
            if (pool.isReaderPoolingEnabled) {
                if (random().nextBoolean()) {
                    writtenToDisk = pool.writeAllDocValuesUpdates()
                    assertFalse(readersAndUpdates.isMerging)
                } else if (random().nextBoolean()) {
                    writtenToDisk = pool.commit(segmentInfos)
                    assertFalse(readersAndUpdates.isMerging)
                } else {
                    writtenToDisk = runBlocking { pool.writeDocValuesUpdatesForMerge(mutableListOf(commitInfo)) }
                    assertTrue(readersAndUpdates.isMerging)
                }
                assertFalse(pool.release(readersAndUpdates, random().nextBoolean()))
            } else {
                if (random().nextBoolean()) {
                    writtenToDisk = pool.release(readersAndUpdates, random().nextBoolean())
                    assertFalse(readersAndUpdates.isMerging)
                } else {
                    writtenToDisk = runBlocking { pool.writeDocValuesUpdatesForMerge(mutableListOf(commitInfo)) }
                    assertTrue(readersAndUpdates.isMerging)
                    assertFalse(pool.release(readersAndUpdates, random().nextBoolean()))
                }
            }
            assertFalse(pool.anyDocValuesChanges())
            assertEquals(expectUpdate, writtenToDisk)
            if (expectUpdate) {
                readersAndUpdates = checkNotNull(pool.get(commitInfo, true))
                val updatedReader: SegmentReader = readersAndUpdates.getReadOnlyClone(IOContext.DEFAULT)
                assertNotEquals(-1, doc)
                val number: NumericDocValues = checkNotNull(updatedReader.getNumericDocValues("number"))
                assertEquals(doc.toLong(), number.advance(doc).toLong())
                assertEquals(1000L, number.longValue())
                readersAndUpdates.release(updatedReader)
                assertFalse(pool.release(readersAndUpdates, random().nextBoolean()))
            }
        }
        IOUtils.close(pool, reader, directory)
    }

    @Test
    @Throws(IOException::class)
    fun testDeletes() {
        val directory: Directory = newDirectory()
        val fieldNumbers: FieldInfos.FieldNumbers = buildIndex(directory)
        val reader: StandardDirectoryReader = DirectoryReader.open(directory) as StandardDirectoryReader
        val segmentInfos: SegmentInfos = reader.segmentInfos.clone()
        val pool =
            ReaderPool(
                directory,
                directory,
                segmentInfos,
                fieldNumbers,
                { 0L },
                NullInfoStream(),
                null,
                null
            )
        val id: Int = random().nextInt(10)
        if (random().nextBoolean()) {
            pool.enableReaderPooling()
        }
        for (commitInfo in segmentInfos) {
            var readersAndUpdates: ReadersAndUpdates = checkNotNull(pool.get(commitInfo, true))
            val readOnlyClone: SegmentReader = readersAndUpdates.getReadOnlyClone(IOContext.DEFAULT)
            val postings: PostingsEnum? = readOnlyClone.postings(Term("id", "" + id))
            var expectUpdate = false
            var doc = -1
            if (postings != null && postings.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                assertTrue(readersAndUpdates.delete(postings.docID().also { doc = it }))
                expectUpdate = true
                assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), postings.nextDoc().toLong())
            }
            assertFalse(pool.anyDocValuesChanges()) // deletes are not accounted here
            readOnlyClone.close()
            val writtenToDisk: Boolean
            if (pool.isReaderPoolingEnabled) {
                writtenToDisk = pool.commit(segmentInfos)
                assertFalse(pool.release(readersAndUpdates, random().nextBoolean()))
            } else {
                writtenToDisk = pool.release(readersAndUpdates, random().nextBoolean())
            }
            assertFalse(pool.anyDocValuesChanges())
            assertEquals(expectUpdate, writtenToDisk)
            if (expectUpdate) {
                readersAndUpdates = checkNotNull(pool.get(commitInfo, true))
                val updatedReader: SegmentReader = readersAndUpdates.getReadOnlyClone(IOContext.DEFAULT)
                assertNotEquals(-1, doc)
                assertFalse(checkNotNull(updatedReader.liveDocs).get(doc))
                readersAndUpdates.release(updatedReader)
                assertFalse(pool.release(readersAndUpdates, random().nextBoolean()))
            }
        }
        IOUtils.close(pool, reader, directory)
    }

    @Test
    @OptIn(ExperimentalAtomicApi::class)
    @Throws(Exception::class)
    fun testPassReaderToMergePolicyConcurrently() {
        val directory: Directory = newDirectory()
        val fieldNumbers: FieldInfos.FieldNumbers = buildIndex(directory)
        val reader: StandardDirectoryReader = DirectoryReader.open(directory) as StandardDirectoryReader
        val segmentInfos: SegmentInfos = reader.segmentInfos.clone()
        val pool =
            ReaderPool(
                directory,
                directory,
                segmentInfos,
                fieldNumbers,
                { 0L },
                NullInfoStream(),
                null,
                null
            )
        if (random().nextBoolean()) {
            pool.enableReaderPooling()
        }
        val isDone = AtomicBoolean(false)
        val latch = CountDownLatch(1)
        val refresher =
            Thread(
                Runnable {
                    try {
                        latch.countDown()
                        while (isDone.load() == false) {
                            for (commitInfo in segmentInfos) {
                                val readersAndUpdates: ReadersAndUpdates = checkNotNull(pool.get(commitInfo, true))
                                val segmentReader: SegmentReader = readersAndUpdates.getReader(IOContext.DEFAULT)
                                readersAndUpdates.release(segmentReader)
                                pool.release(readersAndUpdates, random().nextBoolean())
                            }
                        }
                    } catch (ex: Exception) {
                        throw AssertionError(ex)
                    }
                })
        refresher.start()
        val mergePolicy: MergePolicy =
            object : FilterMergePolicy(newMergePolicy()) {
                @Throws(IOException::class)
                override fun keepFullyDeletedSegment(readerIOSupplier: IOSupplier<CodecReader>): Boolean {
                    val reader: CodecReader = readerIOSupplier.get()
                    assert(
                        reader.maxDoc() > 0 // just try to access the reader
                    )
                    return true
                }
            }
        latch.await()
        for (i in 0..<reader.maxDoc()) {
            for (commitInfo in segmentInfos) {
                val readersAndUpdates: ReadersAndUpdates = checkNotNull(pool.get(commitInfo, true))
                val sr: SegmentReader = readersAndUpdates.getReadOnlyClone(IOContext.DEFAULT)
                val postings: PostingsEnum? = sr.postings(Term("id", "" + i))
                sr.decRef()
                if (postings != null) {
                    var docId: Int = postings.nextDoc()
                    while (docId != DocIdSetIterator.NO_MORE_DOCS
                    ) {
                        readersAndUpdates.delete(docId)
                        assertTrue(readersAndUpdates.keepFullyDeletedSegment(mergePolicy))
                        docId = postings.nextDoc()
                    }
                }
                assertTrue(readersAndUpdates.keepFullyDeletedSegment(mergePolicy))
                pool.release(readersAndUpdates, random().nextBoolean())
            }
        }
        isDone.store(true)
        refresher.join()
        IOUtils.close(pool, reader, directory)
    }

    @Throws(IOException::class)
    private fun buildIndex(directory: Directory): FieldInfos.FieldNumbers {
        val writer = IndexWriter(directory, newIndexWriterConfig())
        for (i in 0..9) {
            val document = Document()
            document.add(StringField("id", "" + i, Field.Store.YES))
            document.add(NumericDocValuesField("number", i.toLong()))
            writer.addDocument(document)
            if (random().nextBoolean()) {
                writer.flush()
            }
        }
        writer.commit()
        writer.close()
        return writer.globalFieldNumberMap
    }

    @Test
    @OptIn(ExperimentalAtomicApi::class)
    @Throws(IOException::class)
    fun testGetReaderByRam() {
        val directory: Directory = newDirectory()
        val fieldNumbers: FieldInfos.FieldNumbers = buildIndex(directory)
        val reader: StandardDirectoryReader = DirectoryReader.open(directory) as StandardDirectoryReader
        val segmentInfos: SegmentInfos = reader.segmentInfos.clone()
        val pool =
            ReaderPool(
                directory,
                directory,
                segmentInfos,
                fieldNumbers,
                { 0L },
                NullInfoStream(),
                null,
                null
            )
        assertEquals(0, pool.readersByRam.size.toLong())

        var ord = 0
        for (commitInfo in segmentInfos) {
            val readersAndUpdates: ReadersAndUpdates = checkNotNull(pool.get(commitInfo, true))
            val test = BinaryDocValuesFieldUpdates(0, "test", commitInfo.info.maxDoc())
            test.add(0, BytesRef(ByteArray(ord++)))
            test.finish()
            readersAndUpdates.addDVUpdate(test)
        }

        val readersByRam: MutableList<ReadersAndUpdates> = pool.readersByRam
        assertEquals(segmentInfos.size().toLong(), readersByRam.size.toLong())
        var previousRam = Long.MAX_VALUE
        for (rld in readersByRam) {
            assertTrue(
                previousRam >= rld.ramBytesUsed.load(), "previous: " + previousRam + " now: " + rld.ramBytesUsed.load()
            )
            previousRam = rld.ramBytesUsed.load()
            rld.dropChanges()
            pool.drop(rld.info)
        }
        IOUtils.close(pool, reader, directory)
    }
}
