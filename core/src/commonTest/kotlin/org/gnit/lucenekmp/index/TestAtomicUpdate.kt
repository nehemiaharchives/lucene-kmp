package org.gnit.lucenekmp.index

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.IntPoint
import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper
import org.gnit.lucenekmp.tests.util.English
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals

class TestAtomicUpdate : LuceneTestCase() {

    private abstract class TimedThread(var numIterations: Int) {
        var failure: Throwable? = null
        private var job: Job? = null

        @Throws(IOException::class)
        abstract fun doWork(currentIteration: Int)

        fun run() {
            try {
                for (count in 0 until numIterations) {
                    doWork(count)
                }
            } catch (e: Throwable) {
                failure = e
                e.printStackTrace()
                throw RuntimeException(e)
            }
        }

        fun start() {
            job = CoroutineScope(Dispatchers.Default).launch {
                run()
            }
        }

        fun join() {
            runBlocking {
                job?.join()
            }
        }
    }

    private class IndexerThread(
        private var writer: IndexWriter,
        numIterations: Int
    ) : TimedThread(numIterations) {

        @Throws(IOException::class)
        override fun doWork(currentIteration: Int) {
            // Update all 100 docs...
            for (i in 0 until 100) {
                val d = Document()
                d.add(newStringField("id", i.toString(), Field.Store.YES))
                d.add(
                    newTextField(
                        "contents",
                        English.intToEnglish(i + 10 * currentIteration),
                        Field.Store.NO
                    )
                )
                d.add(IntPoint("doc", i))
                d.add(IntPoint("doc2d", i, i))
                writer.updateDocument(Term("id", i.toString()), d)
            }
        }
    }

    private class SearcherThread(
        private var directory: Directory,
        numIterations: Int
    ) : TimedThread(numIterations) {

        @Throws(IOException::class)
        override fun doWork(currentIteration: Int) {
            val r = DirectoryReader.open(directory)
            assertEquals(100, r.numDocs())
            r.close()
        }
    }

    /*
     * Run N indexer and N searchers against single index as
     * stress test.
     */
    @Throws(Exception::class)
    fun runTest(directory: Directory) {
        val indexThreads = if (TEST_NIGHTLY) 2 else 1
        val searchThreads = if (TEST_NIGHTLY) 2 else 1
        val indexIterations = if (TEST_NIGHTLY) 10 else 1
        val searchIterations = if (TEST_NIGHTLY) 10 else 1

        val conf = IndexWriterConfig(MockAnalyzer(random())).setMaxBufferedDocs(7)
        (conf.mergePolicy as TieredMergePolicy).setSegmentsPerTier(3.0)
        val writer = RandomIndexWriter.mockIndexWriter(directory, conf, random())

        // Establish a base index of 100 docs:
        for (i in 0 until 100) {
            val d = Document()
            d.add(newStringField("id", i.toString(), Field.Store.YES))
            d.add(newTextField("contents", English.intToEnglish(i), Field.Store.NO))
            if ((i - 1) % 7 == 0) {
                writer.commit()
            }
            writer.addDocument(d)
        }
        writer.commit()

        val r: IndexReader = DirectoryReader.open(directory)
        assertEquals(100, r.numDocs())
        r.close()

        val threads = ArrayList<TimedThread>()
        repeat(indexThreads) {
            threads.add(IndexerThread(writer, indexIterations))
        }
        repeat(searchThreads) {
            threads.add(SearcherThread(directory, searchIterations))
        }
        for (thread in threads) {
            thread.start()
        }
        for (thread in threads) {
            thread.join()
        }

        writer.close()

        for (thread in threads) {
            if (thread.failure != null) {
                throw RuntimeException("hit exception from $thread", thread.failure)
            }
        }
    }

    /* */
    @Test
    @Throws(Exception::class)
    fun testAtomicUpdates() {
        // run against a random directory.
        var directory: Directory = MockDirectoryWrapper(random(), ByteBuffersDirectory())
        runTest(directory)
        directory.close()

        // then against an FSDirectory.
        val dirPath = createTempDir("lucene.test.atomic")
        directory = newFSDirectory(dirPath)
        runTest(directory)
        directory.close()
    }
}
