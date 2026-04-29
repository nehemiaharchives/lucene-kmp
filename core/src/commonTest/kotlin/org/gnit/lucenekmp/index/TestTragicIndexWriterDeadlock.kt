package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.jdkport.CountDownLatch
import org.gnit.lucenekmp.jdkport.InterruptedException
import org.gnit.lucenekmp.jdkport.Thread
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.SuppressingConcurrentMergeScheduler
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertTrue

class TestTragicIndexWriterDeadlock : LuceneTestCase() {

    @Test
    @OptIn(ExperimentalAtomicApi::class)
    @Throws(Exception::class)
    fun testDeadlockExcNRTReaderCommit() {
        val dir = newMockDirectory()
        val iwc: IndexWriterConfig = newIndexWriterConfig()
        if (iwc.mergeScheduler is ConcurrentMergeScheduler) {
            iwc.setMergeScheduler(
                object : SuppressingConcurrentMergeScheduler() {
                    override fun isOK(th: Throwable): Boolean {
                        return true
                    }
                })
        }
        val w = IndexWriter(dir, iwc)
        val startingGun = CountDownLatch(1)
        val done = AtomicBoolean(false)
        val commitThread: Thread =
            object : Thread() {
                override fun run() {
                    try {
                        startingGun.await()
                        while (done.load() == false) {
                            w.addDocument(Document())
                            w.commit()
                        }
                    } catch (t: Throwable) {
                        done.store(true)
                        // System.out.println("commit exc:");
                        // t.printStackTrace(System.out);
                    }
                }
            }
        commitThread.start()
        val r0: DirectoryReader = DirectoryReader.open(w)
        val nrtThread: Thread =
            object : Thread() {
                override fun run() {
                    var r: DirectoryReader = r0
                    try {
                        try {
                            startingGun.await()
                            while (done.load() == false) {
                                val oldReader: DirectoryReader = r
                                val r2: DirectoryReader? = DirectoryReader.openIfChanged(oldReader)
                                if (r2 != null) {
                                    r = r2
                                    oldReader.decRef()
                                }
                            }
                        } finally {
                            r.close()
                        }
                    } catch (t: Throwable) {
                        done.store(true)
                        // System.out.println("nrt exc:");
                        // t.printStackTrace(System.out);
                    }
                }
            }
        nrtThread.start()
        dir.randomIOExceptionRate = .1
        startingGun.countDown()
        commitThread.join()
        nrtThread.join()
        dir.randomIOExceptionRate = 0.0
        w.close()
        dir.close()
    }

    // LUCENE-7570
    @Test
    @Throws(Exception::class)
    fun testDeadlockStalledMerges() {
        doTestDeadlockStalledMerges(false)
    }

    @Test
    @Throws(Exception::class)
    fun testDeadlockStalledFullFlushMerges() {
        doTestDeadlockStalledMerges(true)
    }

    @Throws(Exception::class)
    private fun doTestDeadlockStalledMerges(mergeOnFlush: Boolean) {
        val dir: Directory = newDirectory()
        val iwc: IndexWriterConfig =
            IndexWriterConfig().setMaxFullFlushMergeWaitMillis((if (mergeOnFlush) 1000 else 0).toLong())

        // so we merge every 2 segments:
        val mp: LogMergePolicy = LogDocMergePolicy()
        mp.mergeFactor = 2
        iwc.setMergePolicy(mp)
        val done = CountDownLatch(1)
        val cms: ConcurrentMergeScheduler =
            object : ConcurrentMergeScheduler() {
                @Throws(IOException::class)
                override fun doMerge(mergeSource: MergeSource, merge: MergePolicy.OneMerge) {
                    // let merge takes forever, until commit thread is stalled
                    try {
                        done.await()
                    } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw RuntimeException(ie)
                    }
                    super.doMerge(mergeSource, merge)
                }

                /*@Synchronized*/
                override suspend fun doStall() {
                    done.countDown()
                    super.doStall()
                }

                override fun handleMergeException(exc: Throwable) {}
            }

        // so we stall once the 2nd merge wants to run:
        cms.setMaxMergesAndThreads(1, 1)
        iwc.setMergeScheduler(cms)

        // so we write a segment every 2 indexed docs:
        iwc.setMaxBufferedDocs(2)

        val w: IndexWriter =
            object : IndexWriter(dir, iwc) {
                override fun mergeSuccess(merge: MergePolicy.OneMerge) {
                    // tragedy strikes!
                    throw /*java.lang.OutOfMemory*/Error()
                }
            }

        w.addDocument(Document())
        w.addDocument(Document())
        // w writes first segment
        w.addDocument(Document())
        w.addDocument(Document())
        // w writes second segment, and kicks off merge, that takes forever (done.await)
        w.addDocument(Document())
        w.addDocument(Document())
        // w writes third segment
        w.addDocument(Document())
        val e: IllegalStateException = expectThrows(IllegalStateException::class) { w.commit() }
        assertTrue(e.message!!.startsWith("this writer hit an unrecoverable error"), e.message)
        // w writes fourth segment, and commit flushes and kicks off merge that stalls
        w.close()
        dir.close()
    }
}
