package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.jdkport.ReentrantLock
import org.gnit.lucenekmp.jdkport.Thread
import org.gnit.lucenekmp.jdkport.withLock
import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper
import org.gnit.lucenekmp.tests.util.English.intToEnglish
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.IOUtils
import kotlin.concurrent.Volatile
import kotlin.test.Test
import kotlin.test.assertTrue

class TestTransactions : LuceneTestCase() {

    @Volatile
    private var doFail = false

    private inner class RandomFailure : MockDirectoryWrapper.Failure() {
        @Throws(IOException::class)
        override fun eval(dir: MockDirectoryWrapper) {
            if (this@TestTransactions.doFail && random().nextInt(10) <= 3) {
                if (VERBOSE) {
                    println(Thread.currentThread().getName() + " TEST: now fail on purpose")
                    Throwable().printStackTrace()
                }
                throw IOException("now failing randomly but on purpose")
            }
        }
    }

    private abstract class TimedThread(private val allThreads: Array<TimedThread?>) : Thread() {
        @Volatile
        var failed: Boolean = false

        @Throws(Throwable::class)
        abstract fun doWork()

        override fun run() {
            try {
                var iterations = 0
                do {
                    println(++iterations)
                    if (anyErrors()) break
                    doWork()
                } while (iterations < MAX_ITERATIONS)
            } catch (e: Throwable) {
                println("${currentThread()}: exc")
                e.printStackTrace()
                failed = true
            }
        }

        fun anyErrors(): Boolean {
            for (thread in allThreads) {
                if (thread?.failed == true) return true
            }
            return false
        }

        companion object {
            private val MAX_ITERATIONS = atLeast(100)
        }
    }

    private inner class IndexerThread(
        private val lock: ReentrantLock,
        private val dir1: Directory,
        private val dir2: Directory,
        threads: Array<TimedThread?>,
    ) : TimedThread(threads) {
        var nextID: Int = 0

        @Throws(Throwable::class)
        override fun doWork() {
            val writer1 =
                IndexWriter(
                    dir1,
                    newIndexWriterConfig(MockAnalyzer(random()))
                        .setMaxBufferedDocs(3)
                        .setMergeScheduler(ConcurrentMergeScheduler())
                        .setMergePolicy(newLogMergePolicy(2))
                )
            (writer1.config.mergeScheduler as ConcurrentMergeScheduler).setSuppressExceptions()

            // Intentionally use different params so flush/merge
            // happen @ different times
            val writer2 =
                IndexWriter(
                    dir2,
                    newIndexWriterConfig(MockAnalyzer(random()))
                        .setMaxBufferedDocs(2)
                        .setMergeScheduler(ConcurrentMergeScheduler())
                        .setMergePolicy(newLogMergePolicy(3))
                )
            (writer2.config.mergeScheduler as ConcurrentMergeScheduler).setSuppressExceptions()

            update(writer1)
            update(writer2)

            doFail = true
            try {
                lock.withLock {
                    try {
                        writer1.prepareCommit()
                    } catch (_: Throwable) {
                        // release resources
                        try {
                            writer1.rollback()
                        } catch (_: Throwable) {
                        }
                        try {
                            writer2.rollback()
                        } catch (_: Throwable) {
                        }
                        return
                    }
                    try {
                        writer2.prepareCommit()
                    } catch (_: Throwable) {
                        // release resources
                        try {
                            writer1.rollback()
                        } catch (_: Throwable) {
                        }
                        try {
                            writer2.rollback()
                        } catch (_: Throwable) {
                        }
                        return
                    }

                    writer1.commit()
                    writer2.commit()
                }
            } finally {
                doFail = false
            }

            writer1.close()
            writer2.close()
        }

        @Throws(IOException::class)
        fun update(writer: IndexWriter) {
            // Add 10 docs:
            val customType = FieldType(StringField.TYPE_NOT_STORED)
            customType.setStoreTermVectors(true)
            repeat(10) {
                val d = Document()
                val n = random().nextInt()
                d.add(newField("id", (nextID++).toString(), customType))
                d.add(newTextField("contents", intToEnglish(n), Field.Store.NO))
                writer.addDocument(d)
            }

            // Delete 5 docs:
            var deleteID = nextID - 1
            repeat(5) {
                writer.deleteDocuments(Term("id", "" + deleteID))
                deleteID -= 2
            }
        }
    }

    private class SearcherThread(
        private val lock: ReentrantLock,
        private val dir1: Directory,
        private val dir2: Directory,
        threads: Array<TimedThread?>,
    ) : TimedThread(threads) {
        @Throws(Throwable::class)
        override fun doWork() {
            var r1: IndexReader? = null
            var r2: IndexReader? = null
            lock.withLock {
                try {
                    r1 = DirectoryReader.open(dir1)
                    r2 = DirectoryReader.open(dir2)
                } catch (e: Exception) {
                    // can be rethrown as RuntimeException if it happens during a close listener
                    if (e.message?.contains("on purpose") != true) {
                        throw e
                    }
                    // release resources
                    IOUtils.closeWhileHandlingException(r1, r2)
                    return
                }
            }
            val r1NonNull = requireNotNull(r1)
            val r2NonNull = requireNotNull(r2)
            if (r1NonNull.numDocs() != r2NonNull.numDocs()) {
                throw RuntimeException("doc counts differ: r1=" + r1NonNull.numDocs() + " r2=" + r2NonNull.numDocs())
            }
            IOUtils.closeWhileHandlingException(r1NonNull, r2NonNull)
        }
    }

    @Throws(Throwable::class)
    fun initIndex(dir: Directory) {
        val writer = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        repeat(7) {
            val d = Document()
            val n = random().nextInt()
            d.add(newTextField("contents", intToEnglish(n), Field.Store.NO))
            writer.addDocument(d)
        }
        writer.close()
    }

    @Test
    @Throws(Throwable::class)
    fun testTransactions() {
        // we cant use non-ramdir on windows, because this test needs to double-write.
        val dir1 = MockDirectoryWrapper(random(), ByteBuffersDirectory())
        val dir2 = MockDirectoryWrapper(random(), ByteBuffersDirectory())
        dir1.failOn(RandomFailure())
        dir2.failOn(RandomFailure())
        dir1.setFailOnOpenInput(false)
        dir2.setFailOnOpenInput(false)

        // We throw exceptions in deleteFile, which creates
        // leftover files:
        dir1.setAssertNoUnrefencedFilesOnClose(false)
        dir2.setAssertNoUnrefencedFilesOnClose(false)

        initIndex(dir1)
        initIndex(dir2)

        val lock = ReentrantLock()
        val threads = arrayOfNulls<TimedThread>(3)
        var numThread = 0

        val indexerThread = IndexerThread(lock, dir1, dir2, threads)
        threads[numThread++] = indexerThread
        indexerThread.start()

        val searcherThread1 = SearcherThread(lock, dir1, dir2, threads)
        threads[numThread++] = searcherThread1
        searcherThread1.start()

        val searcherThread2 = SearcherThread(lock, dir1, dir2, threads)
        threads[numThread++] = searcherThread2
        searcherThread2.start()

        repeat(numThread) { i -> requireNotNull(threads[i]).join() }

        repeat(numThread) { i -> assertTrue(!requireNotNull(threads[i]).failed) }
        dir1.close()
        dir2.close()
    }
}
