package org.gnit.lucenekmp.tests.index

import okio.IOException
import org.gnit.lucenekmp.tests.util.NullInfoStream
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.index.CodecReader
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.IndexableField
import org.gnit.lucenekmp.index.LiveIndexWriterConfig
import org.gnit.lucenekmp.index.NoMergePolicy
import org.gnit.lucenekmp.index.SoftDeletesDirectoryReaderWrapper
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.TieredMergePolicy
import org.gnit.lucenekmp.internal.tests.IndexWriterAccess
import org.gnit.lucenekmp.internal.tests.TestSecrets
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.InfoStream
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking


/**
 * Silly class that randomizes the indexing experience. EG it may swap in a different merge
 * policy/scheduler; may commit periodically; may or may not forceMerge in the end, may flush by doc
 * count instead of RAM, etc.
 */
class RandomIndexWriter private constructor(
    r: Random,
    dir: Directory,
    c: IndexWriterConfig,
    closeAnalyzer: Boolean,
    useSoftDeletes: Boolean
) : AutoCloseable {
    val w: IndexWriter
    private val r: Random
    var docCount: Int = 0
    var flushAt: Int
    private var flushAtFactor = 1.0
    private var getReaderCalled = false
    private val analyzer: Analyzer? // only if WE created it (then we close it)
    private val softDeletesRatio: Double
    private val config: LiveIndexWriterConfig

    /** create a RandomIndexWriter with a random config: Uses MockAnalyzer  */
    constructor(r: Random, dir: Directory) : this(
        r,
        dir,
        LuceneTestCase.newIndexWriterConfig(
            r,
            MockAnalyzer(r)
        ),
        true,
        r.nextBoolean()
    )

    /** create a RandomIndexWriter with a random config  */
    constructor(
        r: Random,
        dir: Directory,
        a: Analyzer
    ) : this(r, dir, LuceneTestCase.newIndexWriterConfig(r, a))

    /** create a RandomIndexWriter with the provided config  */
    constructor(
        r: Random,
        dir: Directory,
        c: IndexWriterConfig
    ) : this(r, dir, c, false, r.nextBoolean())

    /** create a RandomIndexWriter with the provided config  */
    constructor(
        r: Random,
        dir: Directory,
        c: IndexWriterConfig,
        useSoftDeletes: Boolean
    ) : this(r, dir, c, false, useSoftDeletes)

    /**
     * Adds a Document.
     *
     * @see IndexWriter.addDocument
     */
    @Throws(IOException::class)
    fun <T : IndexableField> addDocument(doc: Iterable<T>): Long {
        LuceneTestCase.maybeChangeLiveIndexWriterConfig(r, config)
        val seqNo: Long
        if (r.nextInt(5) == 3) {
            // TODO: maybe, we should simply buffer up added docs
            // (but we need to clone them), and only when
            // getReader, commit, etc. are called, we do an
            // addDocuments  Would be better testing.
            seqNo =
                w.addDocuments(
                    object : Iterable<Iterable<T>> {
                        override fun iterator(): MutableIterator<Iterable<T>> {
                            return object : MutableIterator<Iterable<T>> {
                                var done: Boolean = false

                                override fun hasNext(): Boolean {
                                    return !done
                                }

                                override fun remove() {
                                    throw UnsupportedOperationException()
                                }

                                override fun next(): Iterable<T> {
                                    check(!done)
                                    done = true
                                    return doc
                                }
                            }
                        }
                    })
        } else {
            seqNo = w.addDocument(doc)
        }

        maybeFlushOrCommit()

        return seqNo
    }

    @Throws(IOException::class)
    private fun maybeFlushOrCommit() {
        LuceneTestCase.maybeChangeLiveIndexWriterConfig(r, config)
        if (docCount++ == flushAt) {
            if (r.nextBoolean()) {
                flushAllBuffersSequentially()
            } else if (r.nextBoolean()) {
                if (LuceneTestCase.VERBOSE) {
                    println("RIW.add/updateDocument: now doing a flush at docCount=$docCount")
                }
                w.flush()
            } else {
                if (LuceneTestCase.VERBOSE) {
                    println("RIW.add/updateDocument: now doing a commit at docCount=$docCount")
                }
                w.commit()
            }
            flushAt += TestUtil.nextInt(
                r,
                (flushAtFactor * 10).toInt(),
                (flushAtFactor * 1000).toInt()
            )
            if (flushAtFactor < 2e6) {
                // gradually but exponentially increase time b/w flushes
                flushAtFactor *= 1.05
            }
        }
    }

    @Throws(IOException::class)
    private fun flushAllBuffersSequentially() {
        if (LuceneTestCase.VERBOSE) {
            println(
                "RIW.add/updateDocument: now flushing the largest writer at docCount=$docCount"
            )
        }
        val threadPoolSize: Int = INDEX_WRITER_ACCESS.getDocWriterThreadPoolSize(w)
        val numFlushes = min(1, r.nextInt(threadPoolSize + 1))
        for (i in 0..<numFlushes) {
            if (!w.flushNextBuffer()) {
                break // stop once we didn't flush anything
            }
        }
    }

    @Throws(IOException::class)
    fun addDocuments(docs: Iterable<Iterable<IndexableField>>): Long {
        LuceneTestCase.maybeChangeLiveIndexWriterConfig(r, config)
        val seqNo: Long = w.addDocuments(docs)
        maybeFlushOrCommit()
        return seqNo
    }

    @Throws(IOException::class)
    fun updateDocuments(
        delTerm: Term,
        docs: Iterable<Iterable<IndexableField>>
    ): Long {
        LuceneTestCase.maybeChangeLiveIndexWriterConfig(r, config)
        val seqNo: Long
        if (useSoftDeletes()) {
            seqNo =
                w.softUpdateDocuments(
                    delTerm, docs, NumericDocValuesField(config.softDeletesField!!, 1)
                )
        } else {
            if (r.nextInt(10) < 3) {
                // 30% chance
                seqNo = w.updateDocuments(TermQuery(delTerm), docs)
            } else {
                seqNo = w.updateDocuments(delTerm, docs)
            }
        }
        maybeFlushOrCommit()
        return seqNo
    }

    private fun useSoftDeletes(): Boolean {
        return r.nextDouble() < softDeletesRatio
    }

    /**
     * Updates a document.
     *
     * @see IndexWriter.updateDocument
     */
    @Throws(IOException::class)
    fun <T : IndexableField> updateDocument(
        t: Term,
        doc: Iterable<T>
    ): Long {
        LuceneTestCase.maybeChangeLiveIndexWriterConfig(r, config)
        val seqNo: Long
        if (useSoftDeletes()) {
            if (r.nextInt(5) == 3) {
                seqNo =
                    w.softUpdateDocuments(
                        t,
                        listOf(doc),
                        NumericDocValuesField(config.softDeletesField!!, 1)
                    )
            } else {
                seqNo =
                    w.softUpdateDocument(
                        t, doc, NumericDocValuesField(config.softDeletesField!!, 1)
                    )
            }
        } else {
            if (r.nextInt(5) == 3) {
                seqNo = w.updateDocuments(t, listOf(doc))
            } else {
                seqNo = w.updateDocument(t, doc)
            }
        }
        maybeFlushOrCommit()

        return seqNo
    }

    @Throws(IOException::class)
    fun addIndexes(vararg dirs: Directory): Long {
        LuceneTestCase.maybeChangeLiveIndexWriterConfig(r, config)
        return w.addIndexes(*dirs)
    }

    @Throws(IOException::class)
    fun addIndexes(vararg readers: CodecReader): Long {
        LuceneTestCase.maybeChangeLiveIndexWriterConfig(r, config)
        return w.addIndexes(*readers)
    }

    @Throws(IOException::class)
    fun updateNumericDocValue(term: Term, field: String, value: Long): Long {
        LuceneTestCase.maybeChangeLiveIndexWriterConfig(r, config)
        return w.updateNumericDocValue(term, field, value)
    }

    @Throws(IOException::class)
    fun updateBinaryDocValue(
        term: Term,
        field: String,
        value: BytesRef
    ): Long {
        LuceneTestCase.maybeChangeLiveIndexWriterConfig(r, config)
        return w.updateBinaryDocValue(term, field, value)
    }

    @Throws(IOException::class)
    fun updateDocValues(term: Term, vararg updates: Field): Long {
        LuceneTestCase.maybeChangeLiveIndexWriterConfig(r, config)
        return w.updateDocValues(term, *updates)
    }

    @Throws(IOException::class)
    fun deleteDocuments(term: Term): Long {
        LuceneTestCase.maybeChangeLiveIndexWriterConfig(r, config)
        return w.deleteDocuments(term)
    }

    @Throws(IOException::class)
    fun deleteDocuments(q: Query): Long {
        LuceneTestCase.maybeChangeLiveIndexWriterConfig(r, config)
        return w.deleteDocuments(q)
    }

    @Throws(IOException::class)
    fun commit(flushConcurrently: Boolean = r.nextInt(10) == 0): Long {
        LuceneTestCase.maybeChangeLiveIndexWriterConfig(r, config)
        if (flushConcurrently) {
            // Launch flush in background using coroutines, mirroring original Thread behavior
            return runBlocking {
                val throwableList: MutableList<Throwable> = mutableListOf()
                val job = launch(Dispatchers.Default) {
                    try {
                        flushAllBuffersSequentially()
                    } catch (e: Throwable) {
                        // capture background exception
                        throwableList.add(e)
                    }
                }
                try {
                    val result = w.commit()
                    // Ensure background job finishes before returning (same as finally+join)
                    try {
                        job.join()
                    } catch (e: Throwable) {
                        // join shouldn't normally throw, but capture just in case
                        throwableList.add(e)
                    }
                    // IMPORTANT: keep original semantics — ignore background exceptions on success
                    return@runBlocking result
                } catch (t: Throwable) {
                    // capture commit exception to aggregate with background job failures
                    throwableList.add(t)
                } finally {
                    try {
                        job.join()
                    } catch (e: Throwable) {
                        throwableList.add(e)
                    }
                }
                // If we get here, commit threw or join failed: aggregate and rethrow
                if (throwableList.isNotEmpty()) {
                    val primary = throwableList[0]
                    for (i in 1 until throwableList.size) {
                        primary.addSuppressed(throwableList[i])
                    }
                    when (primary) {
                        is IOException -> throw primary
                        is RuntimeException -> throw primary
                        else -> throw AssertionError(primary)
                    }
                }
                // Unreachable, but keeps type system happy
                throw AssertionError("unreachable")
            }
        }
        return w.commit()
    }

    val docStats: IndexWriter.DocStats
        get() = w.getDocStats()

    @Throws(IOException::class)
    fun deleteAll(): Long {
        return w.deleteAll()
    }

    val reader: DirectoryReader
        get() {
            LuceneTestCase.maybeChangeLiveIndexWriterConfig(r, config)
            return getReader(true, false)
        }

    private var doRandomForceMerge: Boolean
    private var doRandomForceMergeAssert = false

    init {
        // TODO: this should be solved in a different way; Random should not be shared (!).
        this.r = Random(r.nextLong())
        if (useSoftDeletes) {
            c.setSoftDeletesField("___soft_deletes")
            softDeletesRatio = 1.0 / 1.0 + r.nextInt(10)
        } else {
            softDeletesRatio = 0.0
        }

        w = mockIndexWriter(dir, c, r)
        config = w.config
        flushAt = TestUtil.nextInt(r, 10, 1000)
        if (closeAnalyzer) {
            analyzer = w.getAnalyzer()
        } else {
            analyzer = null
        }
        if (LuceneTestCase.VERBOSE) {
            println("RIW dir=$dir")
        }

        // Make sure we sometimes test indices that don't get
        // any forced merges:
        doRandomForceMerge = c.mergePolicy !is NoMergePolicy && r.nextBoolean()
    }

    @Throws(IOException::class)
    fun forceMergeDeletes(doWait: Boolean) {
        LuceneTestCase.maybeChangeLiveIndexWriterConfig(r, config)
        w.forceMergeDeletes(doWait)
    }

    @Throws(IOException::class)
    fun forceMergeDeletes() {
        LuceneTestCase.maybeChangeLiveIndexWriterConfig(r, config)
        w.forceMergeDeletes()
    }

    fun setDoRandomForceMerge(v: Boolean) {
        doRandomForceMerge = v
    }

    fun setDoRandomForceMergeAssert(v: Boolean) {
        doRandomForceMergeAssert = v
    }

    @Throws(IOException::class)
    private fun doRandomForceMerge() {
        if (doRandomForceMerge) {
            val segCount: Int = INDEX_WRITER_ACCESS.getSegmentCount(w)
            if (r.nextBoolean() || segCount == 0) {
                // full forceMerge
                if (LuceneTestCase.VERBOSE) {
                    println("RIW: doRandomForceMerge(1)")
                }
                w.forceMerge(1)
            } else if (r.nextBoolean()) {
                // partial forceMerge
                val limit: Int = TestUtil.nextInt(r, 1, segCount)
                if (LuceneTestCase.VERBOSE) {
                    println("RIW: doRandomForceMerge($limit)")
                }
                w.forceMerge(limit)
                if (limit == 1 || (config.mergePolicy is TieredMergePolicy) == false) {
                    assert(
                        !doRandomForceMergeAssert || INDEX_WRITER_ACCESS.getSegmentCount(w) <= limit
                    ) { "limit=" + limit + " actual=" + INDEX_WRITER_ACCESS.getSegmentCount(w) }
                }
            } else {
                if (LuceneTestCase.VERBOSE) {
                    println("RIW: do random forceMergeDeletes()")
                }
                w.forceMergeDeletes()
            }
        }
    }

    @Throws(IOException::class)
    fun getReader(applyDeletions: Boolean, writeAllDeletes: Boolean): DirectoryReader {
        LuceneTestCase.maybeChangeLiveIndexWriterConfig(r, config)
        getReaderCalled = true
        if (r.nextInt(20) == 2) {
            doRandomForceMerge()
        }
        if (!applyDeletions || r.nextBoolean()) {
            // if we have soft deletes we can't open from a directory
            if (LuceneTestCase.VERBOSE) {
                println("RIW.getReader: use NRT reader")
            }
            if (r.nextInt(5) == 1) {
                w.commit()
            }
            return INDEX_WRITER_ACCESS.getReader(w, applyDeletions, writeAllDeletes)
        } else {
            if (LuceneTestCase.VERBOSE) {
                println("RIW.getReader: open new reader")
            }
            w.commit()
            if (r.nextBoolean()) {
                val reader: DirectoryReader =
                    DirectoryReader.open(w.getDirectory())
                if (config.softDeletesField != null) {
                    return SoftDeletesDirectoryReaderWrapper(reader, config.softDeletesField!!)
                } else {
                    return reader
                }
            } else {
                return INDEX_WRITER_ACCESS.getReader(w, applyDeletions, writeAllDeletes)
            }
        }
    }

    /**
     * Close this writer.
     *
     * @see IndexWriter.close
     */
    override fun close() {
        var success = false
        try {
            if (INDEX_WRITER_ACCESS.isClosed(w) == false) {
                LuceneTestCase.maybeChangeLiveIndexWriterConfig(r, config)
            }
            // if someone isn't using getReader() API, we want to be sure to
            // forceMerge since presumably they might open a reader on the dir.
            if (getReaderCalled == false && r.nextInt(8) == 2 && INDEX_WRITER_ACCESS.isClosed(w) == false) {
                doRandomForceMerge()
                if (!config.commitOnClose) {
                    // index may have changed, must commit the changes, or otherwise they are discarded by the
                    // call to close()
                    w.commit()
                }
            }
            success = true
        } finally {
            if (success) {
                IOUtils.close(w, analyzer)
            } else {
                IOUtils.closeWhileHandlingException(w, analyzer)
            }
        }
    }

    /**
     * Forces a forceMerge.
     *
     *
     * NOTE: this should be avoided in tests unless absolutely necessary, as it will result in less
     * test coverage.
     *
     * @see IndexWriter.forceMerge
     */
    @Throws(IOException::class)
    fun forceMerge(maxSegmentCount: Int) {
        LuceneTestCase.maybeChangeLiveIndexWriterConfig(r, config)
        w.forceMerge(maxSegmentCount)
    }

    internal class TestPointInfoStream(delegate: InfoStream, private val testPoint: TestPoint) :
        InfoStream() {
        private val delegate: InfoStream

        init {
            this.delegate = if (delegate == null) NullInfoStream() else delegate
        }

        @Throws(IOException::class)
        override fun close() {
            delegate.close()
        }

        override fun message(component: String, message: String) {
            if ("TP" == component) {
                testPoint.apply(message)
            }
            if (delegate.isEnabled(component)) {
                delegate.message(component, message)
            }
        }

        override fun isEnabled(component: String): Boolean {
            return "TP" == component || delegate.isEnabled(component)
        }
    }

    /** Writes all in-memory segments to the [Directory].  */
    @Throws(IOException::class)
    fun flush() {
        w.flush()
    }

    /**
     * Simple interface that is executed for each `TP` [InfoStream] component
     * message. See also [RandomIndexWriter.mockIndexWriter]
     */
    interface TestPoint {
        fun apply(message: String)
    }

    companion object {
        private val INDEX_WRITER_ACCESS: IndexWriterAccess =
            TestSecrets.getIndexWriterAccess()

        /**
         * Returns an indexwriter that randomly mixes up thread scheduling (by yielding at test points)
         */
        @Throws(IOException::class)
        fun mockIndexWriter(
            dir: Directory,
            conf: IndexWriterConfig,
            r: Random
        ): IndexWriter {
            // Randomly calls Thread.yield so we mixup thread scheduling
            val random: Random = Random(r.nextLong())
            return mockIndexWriter(
                r,
                dir,
                conf,
                object : TestPoint {
                    override fun apply(message: String) {
                        if (random.nextInt(4) == 2) /*java.lang.Thread.yield()*/ {} // TODO implement later
                    }
                })
        }

        /** Returns an indexwriter that enables the specified test point  */
        @Throws(IOException::class)
        fun mockIndexWriter(
            r: Random,
            dir: Directory,
            conf: IndexWriterConfig,
            testPoint: TestPoint
        ): IndexWriter {
            conf.setInfoStream(TestPointInfoStream(conf.infoStream, testPoint))
            var reader: DirectoryReader? = null
            if (r.nextBoolean()
                && DirectoryReader.indexExists(dir)
                && conf.openMode != IndexWriterConfig.OpenMode.CREATE
            ) {
                if (LuceneTestCase.VERBOSE) {
                    println("RIW: open writer from reader")
                }
                reader = DirectoryReader.open(dir)
                conf.setIndexCommit(reader.indexCommit)
            }

            var iw: IndexWriter
            var success = false
            try {
                iw =
                    object : IndexWriter(dir, conf) {
                        override fun isEnableTestPoints(): Boolean {
                            return true
                        }
                    }
                success = true
            } finally {
                if (reader != null) {
                    if (success) {
                        IOUtils.close(reader)
                    } else {
                        IOUtils.closeWhileHandlingException(reader)
                    }
                }
            }
            return iw
        }
    }
}
