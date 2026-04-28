package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.index.IndexWriterConfig.OpenMode
import org.gnit.lucenekmp.jdkport.Thread
import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper
import org.gnit.lucenekmp.tests.util.English.intToEnglish
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.concurrent.Volatile
import kotlin.test.Test
import kotlin.test.assertFalse

class TestStressIndexing : LuceneTestCase() {

    private abstract class TimedThread(private val allThreads: Array<TimedThread>) : Thread() {
        @Volatile
        var failed: Boolean = false

        @Throws(Throwable::class)
        abstract fun doWork()

        override fun run() {
            var iterations = 0
            try {
                do {
                    if (anyErrors()) break
                    doWork()
                } while (++iterations < RUN_ITERATIONS)
            } catch (e: Throwable) {
                println(currentThread().toString() + ": exc")
                e.printStackTrace()
                failed = true
            }
        }

        fun anyErrors(): Boolean {
            for (thread in allThreads) {
                if (thread != null && thread.failed) return true
            }
            return false
        }

        companion object {
            private val RUN_ITERATIONS = if (TEST_NIGHTLY) atLeast(100) else atLeast(20)
        }
    }

    private class IndexerThread(var writer: IndexWriter, threads: Array<TimedThread>) : TimedThread(threads) {
        var nextID: Int = 0

        override fun doWork() {
            // Add 10 docs:
            for (j in 0..9) {
                val d = Document()
                val n = random().nextInt()
                d.add(newStringField("id", (nextID++).toString(), Field.Store.YES))
                d.add(newTextField("contents", intToEnglish(n), Field.Store.NO))
                writer.addDocument(d)
            }

            // Delete 5 docs:
            var deleteID = nextID - 1
            for (j in 0..4) {
                writer.deleteDocuments(Term("id", "" + deleteID))
                deleteID -= 2
            }
        }
    }

    private class SearcherThread(private val directory: Directory, threads: Array<TimedThread>) : TimedThread(threads) {
        @Throws(Throwable::class)
        override fun doWork() {
            for (i in 0..99) {
                val ir: IndexReader = DirectoryReader.open(directory)
                newSearcher(ir)
                ir.close()
            }
        }
    }

    /*
     Run one indexer and 2 searchers against single index as
     stress test.
    */
    @Throws(Exception::class)
    fun runStressTest(directory: Directory, mergeScheduler: MergeScheduler) {
        val modifier =
            IndexWriter(
                directory,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setOpenMode(OpenMode.CREATE)
                    .setMaxBufferedDocs(10)
                    .setMergeScheduler(mergeScheduler)
            )
        modifier.commit()

        val threads = arrayOfNulls<TimedThread>(4) as Array<TimedThread>
        var numThread = 0

        // One modifier that writes 10 docs then removes 5, over
        // and over:
        val indexerThread = IndexerThread(modifier, threads)
        threads[numThread++] = indexerThread
        indexerThread.start()

        val indexerThread2 = IndexerThread(modifier, threads)
        threads[numThread++] = indexerThread2
        indexerThread2.start()

        // Two searchers that constantly just re-instantiate the
        // searcher:
        val searcherThread1 = SearcherThread(directory, threads)
        threads[numThread++] = searcherThread1
        searcherThread1.start()

        val searcherThread2 = SearcherThread(directory, threads)
        threads[numThread++] = searcherThread2
        searcherThread2.start()

        for (i in 0..<numThread) threads[i].join()

        modifier.close()

        for (i in 0..<numThread) assertFalse(threads[i].failed)

        // System.out.println("    Writer: " + indexerThread.count + " iterations");
        // System.out.println("Searcher 1: " + searcherThread1.count + " searchers created");
        // System.out.println("Searcher 2: " + searcherThread2.count + " searchers created");
    }

    @Test
    @Throws(Exception::class)
    fun testStressIndexAndSearching() {
        val directory: Directory
        if (TEST_NIGHTLY) {
            directory = newDirectory() /*newMaybeVirusCheckingDirectory()*/
        } else {
            directory = MockDirectoryWrapper(random(), ByteBuffersDirectory())
        }
        if (directory is MockDirectoryWrapper) {
            directory.setAssertNoUnrefencedFilesOnClose(true)
        }

        runStressTest(directory, ConcurrentMergeScheduler())
        directory.close()
    }
}
