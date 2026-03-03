package org.gnit.lucenekmp.tests.store

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okio.IOException
import okio.Path
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.IndexWriterConfig.OpenMode
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.jdkport.AccessDeniedException
import org.gnit.lucenekmp.jdkport.ByteArrayOutputStream
import org.gnit.lucenekmp.jdkport.PrintStream
import org.gnit.lucenekmp.jdkport.ReentrantLock
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.store.AlreadyClosedException
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.Lock
import org.gnit.lucenekmp.store.LockObtainFailedException
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.Constants
import org.gnit.lucenekmp.util.PrintStreamInfoStream
import org.gnit.lucenekmp.util.configureTestLogging
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.TimeSource

/** Base class for per-LockFactory tests. */
@OptIn(ExperimentalAtomicApi::class)
abstract class BaseLockFactoryTestCase : LuceneTestCase() {

    init {
        configureTestLogging()
    }

    private val logger = KotlinLogging.logger {  }

    /**
     * Subclass returns the Directory to be tested; if it's an FS-based directory it should point to
     * the specified path, else it can ignore it.
     */
    @Throws(Exception::class)
    protected abstract fun getDirectory(path: Path): Directory

    /** Test obtaining and releasing locks, checking validity */
    @Throws(Exception::class)
    open fun testBasics() {
        val tempPath = createTempDir()
        val dir = getDirectory(tempPath)

        var l = dir.obtainLock("commit")
        // shouldn't be able to get the lock twice
        expectThrows(LockObtainFailedException::class) {
            dir.obtainLock("commit")
        }
        l.close()

        // Make sure we can obtain first one again:
        l = dir.obtainLock("commit")
        l.close()

        dir.close()
    }

    /** Test closing locks twice */
    @Throws(Exception::class)
    open fun testDoubleClose() {
        val tempPath = createTempDir()
        val dir = getDirectory(tempPath)

        val l = dir.obtainLock("commit")
        l.close()
        l.close() // close again, should be no exception

        dir.close()
    }

    /** Test ensureValid returns true after acquire */
    @Throws(Exception::class)
    open fun testValidAfterAcquire() {
        val tempPath = createTempDir()
        val dir = getDirectory(tempPath)
        val l = dir.obtainLock("commit")
        l.ensureValid() // no exception
        l.close()
        dir.close()
    }

    /** Test ensureValid throws exception after close */
    @Throws(Exception::class)
    open fun testInvalidAfterClose() {
        val tempPath = createTempDir()
        val dir = getDirectory(tempPath)

        val l = dir.obtainLock("commit")
        l.close()

        expectThrows(AlreadyClosedException::class) {
            l.ensureValid()
        }

        dir.close()
    }

    @Throws(Exception::class)
    open fun testObtainConcurrently() {
        val tempPath = createTempDir()
        val directory = getDirectory(tempPath)
        val running = AtomicBoolean(true)
        val atomicCounter = AtomicInt(0)
        val assertingLock = ReentrantLock()
        val numThreads = 2 + random().nextInt(10)
        val runs = atLeast(1000)
        val startGate = CompletableDeferred<Unit>()

        runBlocking {
            val jobs = mutableListOf<Job>()
            repeat(numThreads) {
                jobs.add(launch(Dispatchers.Default) {
                    startGate.await()
                    while (running.load()) {
                        try {
                            directory.obtainLock("foo.lock").use { lock: Lock ->
                                if (assertingLock.tryLock()) {
                                    assertingLock.unlock()
                                } else {
                                    fail()
                                }
                                assert(lock != null)
                            }
                        } catch (_: IOException) {
                            //
                        }
                        if (atomicCounter.incrementAndFetch() > runs) {
                            running.store(false)
                        }
                    }
                })
            }
            startGate.complete(Unit)
            jobs.forEach { it.join() }
        }

        directory.close()
    }

    // Verify: do stress test, by opening IndexReaders and
    // IndexWriters over & over in 2 threads and making sure
    // no unexpected exceptions are raised:
    @Throws(Exception::class)
    open fun testStressLocks() {
        val totalStart = TimeSource.Monotonic.markNow()
        val tempPath = createTempDir()
        // Path-specific Windows FS detection is not yet ported.
        assumeFalse("cannot handle buggy Files.delete", Constants.WINDOWS)

        val dir = getDirectory(tempPath)

        // First create a 1 doc index:
        var w = IndexWriter(
            dir,
            IndexWriterConfig(MockAnalyzer(random())).setOpenMode(OpenMode.CREATE)
        )
        addDoc(w)
        w.close()

        val numIterations = atLeast(3)
        // TODO reduced numIterations = atLeast(20) to atLeast(3) for dev speed
        logger.debug { "phase=stressLocks.start tempPath=$tempPath numIterations=$numIterations" }
        val writer = WriterThread(numIterations, dir)
        val searcher = SearcherThread(numIterations, dir)

        runBlocking {
            val writerJob = launch(Dispatchers.Default) { writer.run() }
            val searcherJob = launch(Dispatchers.Default) { searcher.run() }
            writerJob.join()
            searcherJob.join()
        }
        logger.debug { "phase=stressLocks.elapsedMs total=${totalStart.elapsedNow().inWholeMilliseconds}" }

        assertTrue(!writer.hitException, "IndexWriter hit unexpected exceptions")
        assertTrue(!searcher.hitException, "IndexSearcher hit unexpected exceptions")

        logger.debug { "phase=stressLocks.finish writerHitException=${writer.hitException} searcherHitException=${searcher.hitException}" }
        dir.close()
    }

    @Throws(IOException::class)
    private fun addDoc(writer: IndexWriter) {
        val doc = Document()
        doc.add(newTextField("content", "aaa", Field.Store.NO))
        writer.addDocument(doc)
    }

    private inner class WriterThread(
        private val numIteration: Int,
        private val dir: Directory
    ) {
        var hitException = false

        private fun toString(baos: ByteArrayOutputStream): String {
            return baos.toString("UTF8")
        }

        suspend fun run() {
            var writer: IndexWriter?
            val totalStart = TimeSource.Monotonic.markNow()
            for (i in 0 until this.numIteration) {
                val iterStart = TimeSource.Monotonic.markNow()
                if (i % 5 == 0) {
                    logger.debug { "phase=stressLocks.writer.iter iter=$i total=$numIteration" }
                }
                if (VERBOSE) {
                    println("TEST: WriterThread iter=$i")
                }
                val baos = ByteArrayOutputStream()

                val iwc = IndexWriterConfig(MockAnalyzer(random()))

                // We only print the IW infoStream output on exc, below:
                val printStream = PrintStream(baos, true, org.gnit.lucenekmp.jdkport.StandardCharsets.UTF_8)

                iwc.setInfoStream(PrintStreamInfoStream(printStream))

                printStream.println("\nTEST: WriterThread iter=$i")
                iwc.setOpenMode(OpenMode.APPEND)
                try {
                    val createStart = TimeSource.Monotonic.markNow()
                    writer = IndexWriter(dir, iwc)
                    if (i % 5 == 0) {
                        logger.debug {
                            "phase=stressLocks.writer.createMs iter=$i elapsed=${createStart.elapsedNow().inWholeMilliseconds}"
                        }
                    }
                } catch (t: Throwable) {
                    if (Constants.WINDOWS && t is AccessDeniedException) {
                        // LUCENE-6684: suppress this on Windows.
                        printStream.println("TEST: AccessDeniedException on init writer")
                        printStream.println(t.toString())
                    } else {
                        hitException = true
                        println("Stress Test Index Writer: creation hit unexpected exception: $t")
                        println(t.toString())
                        println(toString(baos))
                    }
                    break
                }
                try {
                    val addDocStart = TimeSource.Monotonic.markNow()
                    addDoc(writer)
                    if (i % 5 == 0) {
                        logger.debug {
                            "phase=stressLocks.writer.addDocMs iter=$i elapsed=${addDocStart.elapsedNow().inWholeMilliseconds}"
                        }
                    }
                } catch (t: Throwable) {
                    hitException = true
                    println("Stress Test Index Writer: addDoc hit unexpected exception: $t")
                    println(t.toString())
                    println(toString(baos))
                    break
                }
                try {
                    val closeStart = TimeSource.Monotonic.markNow()
                    writer.close()
                    if (i % 5 == 0) {
                        logger.debug {
                            "phase=stressLocks.writer.closeMs iter=$i elapsed=${closeStart.elapsedNow().inWholeMilliseconds}"
                        }
                    }
                } catch (t: Throwable) {
                    hitException = true
                    println("Stress Test Index Writer: close hit unexpected exception: $t")
                    println(t.toString())
                    println(t.stackTraceToString())
                    println(toString(baos))
                    break
                }
                if (i % 5 == 0) {
                    logger.debug {
                        "phase=stressLocks.writer.iterElapsedMs iter=$i elapsed=${iterStart.elapsedNow().inWholeMilliseconds}"
                    }
                }
            }
            logger.debug { "phase=stressLocks.writer.totalElapsedMs value=${totalStart.elapsedNow().inWholeMilliseconds}" }
            logger.debug { "phase=stressLocks.writer.done hitException=$hitException" }
        }
    }

    private class SearcherThread(
        private val numIteration: Int,
        private val dir: Directory
    ) {
        var hitException = false

        suspend fun run() {
            var reader: IndexReader
            var searcher: IndexSearcher
            val query: Query = TermQuery(Term("content", "aaa"))
            for (i in 0 until this.numIteration) {
                if (i % 5 == 0) {
                    logger.debug { "phase=stressLocks.searcher.iter iter=$i total=$numIteration" }
                }
                try {
                    reader = DirectoryReader.open(dir)
                    searcher = newSearcher(reader)
                } catch (e: Exception) {
                    hitException = true
                    println("Stress Test Index Searcher: create hit unexpected exception: $e")
                    println(e.toString())
                    break
                }
                try {
                    searcher.search(query, 1000)
                } catch (e: IOException) {
                    hitException = true
                    println("Stress Test Index Searcher: search hit unexpected exception: $e")
                    println(e.toString())
                    break
                }
                try {
                    reader.close()
                } catch (e: IOException) {
                    hitException = true
                    println("Stress Test Index Searcher: close hit unexpected exception: $e")
                    println(e.toString())
                    break
                }
            }
            logger.debug { "phase=stressLocks.searcher.done hitException=$hitException" }
        }
    }
}
