package org.gnit.lucenekmp.index

import kotlinx.coroutines.Runnable
import okio.IOException
import org.gnit.lucenekmp.index.DocumentsWriter.FlushNotifications
import org.gnit.lucenekmp.index.DocumentsWriterDeleteQueue.DeleteSlice
import org.gnit.lucenekmp.index.IndexingChain.ReservedField
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.util.withDocumentsWriterPerThreadFlushCallPathHint
import org.gnit.lucenekmp.util.withIndexingChainCallPathHint
import org.gnit.lucenekmp.jdkport.Condition
import org.gnit.lucenekmp.jdkport.InterruptedException
import org.gnit.lucenekmp.jdkport.Lock
import org.gnit.lucenekmp.jdkport.ReentrantLock
import org.gnit.lucenekmp.jdkport.TimeUnit
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.FailurePathProbe
import org.gnit.lucenekmp.store.FlushInfo
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.TrackingDirectoryWrapper
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.InfoStream
import org.gnit.lucenekmp.util.SetOnce
import org.gnit.lucenekmp.util.StringHelper
import org.gnit.lucenekmp.util.Version
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}


/* Written by GitHub Copilot CLI:
 * Findings from JVM vs Kotlin/Native (linuxX64) profiling for
 * TestIndexWriterWithThreads.testUpdateSingleDocWithThreads:
 *
 * 1. Coarse timing showed the dominant top-level cost was not thread startup or
 *    barrier handling. The slow phase on both targets was the NRT reopen loop:
 *    DirectoryReader.openIfChanged -> StandardDirectoryReader.doOpenFromWriter ->
 *    IndexWriter.getReader.
 *
 * 2. Deeper timing inside getReader showed the main cost was
 *    DocumentsWriter.flushAllThreads(), not nrtIsCurrent(), reader open, or
 *    delete application.
 *
 * 3. Deeper timing inside flushAllThreads showed the dominant substep was
 *    maybeFlush().
 *
 * 4. Deeper timing inside maybeFlush showed the lowest measured hotspot was
 *    DocumentsWriterPerThread.flush(), specifically the flushSegment work
 *    performed by flushingDWPT.flush(flushNotifications).
 *
 * Reproducible fixed-work perf run (testUpdateSingleDocWithThreadsPerfFixed,
 * numThreads=3, itersPerThread=600, forceMerge=false):
 *
 * Target   | Total | openIfChanged | getReader.flushAllThreads | flush_all_threads.maybeFlush | do_flush.flushSegment |
 * -------- | ----- | ------------- | ------------------------- | ---------------------------- | --------------------- |
 * JVM      | 4.15s | 2.80s         | 2.00s                     | 1.95s                        | 1.02s                 |
 * linuxX64 | 9.34s | 8.30s         | 6.82s                     | 6.74s                        | 5.38s                 |
 *
 * So the lowest measured native/JVM gap in this path is concentrated in
 * flushSegment, at roughly 5x slower on linuxX64 than JVM for comparable work.
 *
 * A native-only optimization attempt in posixNativeMain FilesWrite.native.kt
 * replaced the custom pinned/memcpy KmpSink path with direct BufferedSink
 * delegation, but it regressed linuxX64 instead of improving it
 * (~10.17 s -> ~10.78 s on the fixed perf test), so that change was reverted.
 *
 * Current conclusion: further optimization work should focus below this method,
 * in the indexing-chain / codec / file-writing work executed during segment
 * flush, not in test orchestration, barrier setup, or openIfChanged control
 * flow itself.
 */


/* Written by Codex:
 *
 * Additional comment: most likely next speed-up path.
 *
 * Do not restart the investigation from the test-level timing unless the
 * existing numbers become non-reproducible. The important information from
 * the previous run is already:
 *
 *   test main loop
 *     -> DirectoryReader.openIfChanged
 *       -> StandardDirectoryReader.doOpenFromWriter
 *         -> IndexWriter.getReader
 *           -> DocumentsWriter.flushAllThreads
 *             -> DocumentsWriterFlushControl.maybeFlush
 *               -> DocumentsWriter.doFlush
 *                 -> DocumentsWriterPerThread.flush
 *
 * The next target is below DocumentsWriterPerThread.flush, and the first
 * thing to verify is whether the native time is really codec byte writing or
 * synchronous in-memory directory bookkeeping. This test uses newDirectory()
 * and the current test infrastructure normally routes that to ByteBuffers-
 * backed directories, not real POSIX files. That means the reverted
 * FilesWrite.native.kt experiment probably optimized the wrong concrete I/O
 * implementation for this benchmark.
 *
 * Highest-probability native bottleneck:
 *
 *   ByteBuffersDirectory.withFilesLock currently does:
 *
 *     runBlocking { filesMutex.withLock { action() } }
 *
 *   for synchronous Directory APIs such as createOutput, createTempOutput,
 *   openInput, listAll, deleteFile, rename, fileLength, and close. Segment
 *   flush plus NRT reopen call these APIs many times while creating tiny
 *   per-segment files and immediately reopening them. On JVM, the coroutine
 *   bridge cost is mostly hidden by the JIT and by faster coroutine runtime
 *   paths. On Kotlin/Native, repeatedly entering runBlocking from a purely
 *   synchronous hot path is a very plausible source of seconds of overhead.
 *
 *   The most promising expect/actual optimization is therefore:
 *
 *     1. Add timing/counters around ByteBuffersDirectory.withFilesLock.
 *        Track calls and elapsed time by operation name:
 *        createOutput, createTempOutput, openInput, listAll, deleteFile,
 *        rename, fileLength, sync, syncMetaData, and close.
 *
 *     2. Run testUpdateSingleDocWithThreadsPerfFixed on JVM and linuxX64.
 *        If ByteBuffersDirectory lock time is a large part of
 *        do_flush.flushSegment or openIfChanged, stop descending into codecs
 *        and patch this first.
 *
 *     3. Replace the coroutine Mutex in ByteBuffersDirectory with the existing
 *        org.gnit.lucenekmp.jdkport.ReentrantLock and withLock helper. This
 *        already uses expect/actual:
 *
 *          commonMain:
 *            private val filesLock = ReentrantLock()
 *            private inline fun <T> withFilesLock(action: () -> T): T =
 *                filesLock.withLock(action)
 *
 *          jvmAndroidMain actual:
 *            java.util.concurrent.locks.ReentrantLock
 *
 *          posixNativeMain actual:
 *            pthread_mutex_t / pthread_cond_t based lock
 *
 *        This keeps Directory APIs synchronous, removes runBlocking from the
 *        hot path, and stays behaviorally close to Java Lucene's synchronized
 *        in-memory directory bookkeeping.
 *
 *     4. Re-run the same fixed-work perf method and report:
 *        total elapsed, openIfChanged elapsed, getReader.flushAllThreads,
 *        do_flush.flushSegment, ByteBuffersDirectory.withFilesLock elapsed,
 *        and withFilesLock call count.
 *
 *   Acceptance signal:
 *
 *     If linuxX64 fixed perf drops by multiple seconds while JVM stays flat,
 *     keep the patch. This can plausibly move a 9-10 second fixed run toward
 *     the requested 5-10 second range because it removes a native-only
 *     coroutine bridge from every tiny directory metadata operation.
 *
 * If ByteBuffersDirectory is not dominant:
 *
 *   Descend into IndexingChain.flush in this exact order and keep only the
 *   dominant branch:
 *
 *     maybeSortSegment
 *     writeNorms
 *     writeDocValues
 *     writePoints
 *     vectorValuesConsumer.flush
 *     storedFieldsConsumer.finish + storedFieldsConsumer.flush
 *     termsHash.flush
 *     fieldInfosFormat.write
 *
 *   For this specific test document, the document is:
 *
 *     StringField("id", "1", Field.Store.NO)
 *
 *   That means no stored field payload, no points, no vectors, no doc values,
 *   and omitted norms. The likely codec branch after ByteBuffersDirectory is
 *   therefore termsHash.flush, then FreqProxTermsWriter.flush, then
 *   state.segmentInfo.codec.postingsFormat().fieldsConsumer(state).use {
 *       consumer.write(fields, norms)
 *   }
 *
 * If termsHash/postings is dominant:
 *
 *   Descend in this order:
 *
 *     FreqProxTermsWriter.flush:
 *       super.flush
 *       perField.sortTerms
 *       CollectionUtil.introSort(allFields)
 *       applyDeletes
 *       fieldsConsumer construction
 *       consumer.write(fields, norms)
 *       consumer.close
 *
 *     Lucene90BlockTreeTermsWriter.write:
 *       postingsWriter.init
 *       per-field terms iteration
 *       writeBlocks / PendingBlock.compileIndex
 *       suffixLengthsWriter/metaWriter/statsWriter copyTo(termsOut)
 *       write field metadata
 *       CodecUtil.writeFooter
 *
 *     Lucene101PostingsWriter:
 *       startTerm/startDoc/addPosition/finishDoc/finishTerm
 *       writeVInt15/writeVLong15
 *       level0Output.copyTo(docOut)
 *       level1Output.copyTo(docOut)
 *       CodecUtil.writeFooter
 *
 *   Use batch-level timing and counters, not Clock calls around every byte
 *   write. Per-call clocks will distort the native profile.
 *
 * Second-likeliest native bottom step:
 *
 *   DataOutput.writeVInt/writeVLong and DataOutput.writeInt/writeLong feed
 *   ByteBuffersIndexOutput -> ByteBuffersDataOutput -> jdkport.ByteBuffer.
 *   JVM can inline most of that stack. Kotlin/Native often cannot, so the
 *   repeated virtual writeByte calls, ByteBuffer.position/limit checks, and
 *   small ByteArray copies can dominate even though all data is in memory.
 *
 *   If counters prove this path dominates, the next expect/actual patch should
 *   be a native fast path for array-backed ByteBuffersDataOutput, not a POSIX
 *   file sink patch. The minimal shape should be:
 *
 *     commonMain:
 *       Add platform helpers near ByteBufferPlatform:
 *         byteBufferPutShortPlatform(array, index, value, bigEndian)
 *         byteBufferPutIntPlatform(array, index, value, bigEndian)
 *         byteBufferPutLongPlatform(array, index, value, bigEndian)
 *       Change jdkport.ByteBuffer.putShort/putInt/putLong to delegate to
 *       those helpers after a single bounds/writable check.
 *
 *     jvmAndroidMain:
 *       Keep the current pure ByteArray implementation or use the same simple
 *       shifts. JVM is already fast enough and should not take risk.
 *
 *     nativeMain or posixNativeMain:
 *       Implement only if a micro-benchmark proves it faster than simple
 *       ByteArray indexing. Avoid per-primitive usePinned if it regresses.
 *       A pinned block writer is preferable to pinning every int/long.
 *
 *   If variable-length writes are the real bottom step, consider making
 *   DataOutput.writeVInt/writeVLong open and overriding them in
 *   ByteBuffersDataOutput with direct writes into the current backing array.
 *   This is not a Lucene behavior change; it only removes native dispatch and
 *   ByteBuffer method overhead. Keep it only if JVM and native compile/tests
 *   pass and linuxX64 timing improves.
 *
 * Measurement rules for the next agent:
 *
 *   - Keep testUpdateSingleDocWithThreadsPerfFixed as the fixed workload.
 *   - Always capture JVM and linuxX64 numbers from the same commit.
 *   - Use one summary line per layer with stable keys:
 *       phase=byte_buffers_directory op=createOutput calls=... elapsedMs=...
 *       phase=indexing_chain substep=termsHashFlush elapsedMs=...
 *       phase=postings_writer substep=finishDoc elapsedMs=...
 *   - Do not keep temporary logs after the final optimization unless the user
 *     explicitly asks to retain profiling.
 *   - If ByteBuffersDirectory.withFilesLock is confirmed, patch that before
 *     touching codecs. It has a small behavior surface and uses the project's
 *     existing expect/actual ReentrantLock implementation.
 */


@OptIn(ExperimentalAtomicApi::class)
class DocumentsWriterPerThread @OptIn(ExperimentalAtomicApi::class) constructor(
    private val indexMajorVersionCreated: Int,
    segmentName: String,
    directoryOrig: Directory,
    directory: Directory,
    private val indexWriterConfig: LiveIndexWriterConfig,
    val deleteQueue: DocumentsWriterDeleteQueue,
    private val fieldInfos: FieldInfos.Builder,
    @property:OptIn(ExperimentalAtomicApi::class) private val pendingNumDocs: AtomicLong,
    enableTestPoints: Boolean
) : Accountable, Lock {  // TODO Lock is not ported from JDK, need to think what to do here
    private var abortingException: Throwable? = null

    private fun onAbortingException(throwable: Throwable) {
        //checkNotNull(throwable) { "aborting exception must not be null" }
        assert(abortingException == null) { "aborting exception has already been set" }
        abortingException = throwable
    }

    class FlushedSegment(
        infoStream: InfoStream,
        val segmentInfo: SegmentCommitInfo,
        val fieldInfos: FieldInfos,
        segmentUpdates: BufferedUpdates?,
        val liveDocs: FixedBitSet?,
        val delCount: Int,
        val sortMap: Sorter.DocMap?
    ) {
        val segmentUpdates: FrozenBufferedUpdates? = if (segmentUpdates != null && segmentUpdates.any())
            FrozenBufferedUpdates(infoStream, segmentUpdates, segmentInfo)
        else
            null
    }

    /**
     * Called if we hit an exception at a bad time (when updating the index files) and must discard
     * all currently buffered docs. This resets our state, discarding any docs added since last flush.
     */
    @OptIn(ExperimentalAtomicApi::class)
    @Throws(IOException::class)
    fun abort() {
        this.isAborted = true
        pendingNumDocs.addAndFetch(-numDocsInRAM.toLong())
        try {
            if (infoStream.isEnabled("DWPT")) {
                infoStream.message("DWPT", "now abort")
            }
            try {
                indexingChain.abort()
            } finally {
                pendingUpdates.clear()
            }
        } finally {
            if (infoStream.isEnabled("DWPT")) {
                infoStream.message("DWPT", "done abort")
            }
        }
    }

    val codec: Codec = indexWriterConfig.codec
    val directory: TrackingDirectoryWrapper = TrackingDirectoryWrapper(directory)
    private val indexingChain: IndexingChain

    // Updates for our still-in-RAM (to be flushed next) segment
    private val pendingUpdates: BufferedUpdates = BufferedUpdates(segmentName)
    private val segmentInfo: SegmentInfo // Current segment we are working on
    var isAborted: Boolean = false // True if we aborted
        private set
    private val flushPending: SetOnce<Boolean> = SetOnce()

    /**
     * Returns the last committed bytes for this DWPT. This method can be called without acquiring the
     * DWPTs lock.
     */
    @Volatile
    var lastCommittedBytesUsed: Long = 0
        private set
    private val hasFlushed: SetOnce<Boolean> = SetOnce()

    private val infoStream: InfoStream = indexWriterConfig.infoStream// public for FlushPolicy

    /** Returns the number of RAM resident documents in this [DocumentsWriterPerThread]  */
    var numDocsInRAM: Int = 0
        private set
    private val deleteSlice: DeleteSlice

    private val enableTestPoints: Boolean
    private val lock: ReentrantLock = ReentrantLock()
    private var deleteDocIDs = IntArray(0)
    private var numDeletedDocIds = 0
    private val parentField: ReservedField<NumericDocValuesField>?

    fun testPoint(message: String) {
        if (enableTestPoints) {
            assert(
                infoStream.isEnabled("TP") // don't enable unless you need them.
            )
            infoStream.message("TP", message)
        }
    }

    /** Anything that will add N docs to the index should reserve first to make sure it's allowed.  */
    @OptIn(ExperimentalAtomicApi::class)
    private fun reserveOneDoc() {
        if (pendingNumDocs.incrementAndFetch() > IndexWriter.actualMaxDocs) {
            // Reserve failed: put the one doc back and throw exc:
            pendingNumDocs.decrementAndFetch()
            throw IllegalArgumentException(
                "number of documents in the index cannot exceed " + IndexWriter.actualMaxDocs
            )
        }
    }

    @Throws(IOException::class)
    fun updateDocuments(
        docs: Iterable<out Iterable<out IndexableField>>,
        deleteNode: DocumentsWriterDeleteQueue.Node<*>?,
        flushNotifications: FlushNotifications,
        onNewDocOnRAM: Runnable
    ): Long {
        return withIndexingChainCallPathHint {
            try {
                testPoint("DocumentsWriterPerThread addDocuments start")
                assert(abortingException == null) { "DWPT has hit aborting exception but is still indexing" }
                if (INFO_VERBOSE && infoStream.isEnabled("DWPT")) {
                    infoStream.message(
                        "DWPT",
                        (/*java.lang.Thread.currentThread().getName() // TODO Thread is not supported in KMP, need to think what to do here
                                + */" update delTerm="
                                + deleteNode
                                + " docID="
                                + numDocsInRAM
                                + " seg="
                                + segmentInfo.name)
                    )
                }
                val docsInRamBefore = numDocsInRAM
                var allDocsIndexed = false
                try {
                    val iterator: Iterator<Iterable<IndexableField>> = docs.iterator()
                    var docCount = 0
                    while (iterator.hasNext()) {
                        var doc: Iterable<IndexableField> = iterator.next()
                        if (parentField != null) {
                            if (!iterator.hasNext()) {
                                doc = addParentField(doc, parentField)
                            }
                        } else require(
                            !(segmentInfo.indexSort != null && iterator.hasNext()
                                    && indexMajorVersionCreated >= Version.LUCENE_10_0_0.major)
                        ) { "a parent field must be set in order to use document blocks with index sorting; see IndexWriterConfig#setParentField" }

                        // Even on exception, the document is still added (but marked
                        // deleted), so we don't need to un-reserve at that point.
                        // Aborting exceptions will actually "lose" more than one
                        // document, so the counter will be "wrong" in that case, but
                        // it's very hard to fix (we can't easily distinguish aborting
                        // vs non-aborting exceptions):
                        reserveOneDoc()
                        try {
                            indexingChain.processDocument(numDocsInRAM++, doc)
                        } finally {
                            onNewDocOnRAM.run()
                        }
                        docCount++
                    }
                    val numDocs = numDocsInRAM - docsInRamBefore
                    if (numDocs > 1) {
                        segmentInfo.setHasBlocks()
                    }
                    allDocsIndexed = true
                    val result = finishDocuments(deleteNode, docsInRamBefore)
                    return@withIndexingChainCallPathHint result
                } finally {
                    if (!allDocsIndexed && !this.isAborted) {
                        // the iterator threw an exception that is not aborting
                        // go and mark all docs from this block as deleted
                        deleteLastDocs(numDocsInRAM - docsInRamBefore)
                    }
                }
            } finally {
                maybeAbort("updateDocuments", flushNotifications)
            }
        }
    }

    private fun addParentField(
        doc: Iterable<IndexableField>, parentField: IndexableField
    ): Iterable<IndexableField> {
        return Iterable {
            val first: Iterator<IndexableField> = doc.iterator()
            object : Iterator<IndexableField> {
                var additionalField: IndexableField? = parentField

                override fun hasNext(): Boolean {
                    return additionalField != null || first.hasNext()
                }

                override fun next(): IndexableField {
                    if (additionalField != null) {
                        val field: IndexableField? = additionalField
                        additionalField = null
                        return field!!
                    }
                    if (first.hasNext()) {
                        return first.next()
                    }
                    throw NoSuchElementException()
                }
            }
        }
    }

    private fun finishDocuments(
        deleteNode: DocumentsWriterDeleteQueue.Node<*>?,
        docIdUpTo: Int
    ): Long {
        /*
     * here we actually finish the document in two steps 1. push the delete into
     * the queue and update our slice. 2. increment the DWPT private document
     * id.
     *
     * the updated slice we get from 1. holds all the deletes that have occurred
     * since we updated the slice the last time.
     */
        // Apply delTerm only after all indexing has
        // succeeded, but apply it only to docs prior to when
        // this batch started:
        var seqNo: Long
        if (deleteNode != null) {
            seqNo = deleteQueue.add(deleteNode, deleteSlice)
            assert(deleteSlice.isTail(deleteNode)) { "expected the delete term as the tail item" }
            deleteSlice.apply(pendingUpdates, docIdUpTo)
            return seqNo
        } else {
            seqNo = deleteQueue.updateSlice(deleteSlice)
            if (seqNo < 0) {
                seqNo = -seqNo
                deleteSlice.apply(pendingUpdates, docIdUpTo)
            } else {
                deleteSlice.reset()
            }
        }

        return seqNo
    }

    // This method marks the last N docs as deleted. This is used
    // in the case of a non-aborting exception. There are several cases
    // where we fail a document ie. due to an exception during analysis
    // that causes the doc to be rejected but won't cause the DWPT to be
    // stale nor the entire IW to abort and shutdown. In such a case
    // we only mark these docs as deleted and turn it into a livedocs
    // during flush
    private fun deleteLastDocs(docCount: Int) {
        val from = numDocsInRAM - docCount
        val to = numDocsInRAM
        deleteDocIDs = ArrayUtil.grow(deleteDocIDs, numDeletedDocIds + (to - from))
        for (docId in from..<to) {
            deleteDocIDs[numDeletedDocIds++] = docId
        }
        // NOTE: we do not trigger flush here.  This is
        // potentially a RAM leak, if you have an app that tries
        // to add docs but every single doc always hits a
        // non-aborting exception.  Allowing a flush here gets
        // very messy because we are only invoked when handling
        // exceptions so to do this properly, while handling an
        // exception we'd have to go off and flush new deletes
        // which is risky (likely would hit some other
        // confounding exception).
    }

    /**
     * Prepares this DWPT for flushing. This method will freeze and return the [ ]s global buffer and apply all pending deletes to this DWPT.
     */
    fun prepareFlush(): FrozenBufferedUpdates? {
        assert(numDocsInRAM > 0)
        val globalUpdates: FrozenBufferedUpdates? = deleteQueue.freezeGlobalBuffer(deleteSlice)
        /* deleteSlice can possibly be null if we have hit non-aborting exceptions during indexing and never succeeded
    adding a document. */
        if (deleteSlice != null) {
            // apply all deletes before we flush and release the delete slice
            deleteSlice.apply(pendingUpdates, numDocsInRAM)
            assert(deleteSlice.isEmpty)
            deleteSlice.reset()
        }
        return globalUpdates
    }

    /** Flush all pending docs to a new segment  */
    @OptIn(ExperimentalTime::class, ExperimentalAtomicApi::class)
    @Throws(IOException::class)
    fun flush(flushNotifications: FlushNotifications): FlushedSegment? {
        assert(flushPending.get() == true)
        assert(numDocsInRAM > 0)
        assert(deleteSlice.isEmpty) { "all deletes must be applied in prepareFlush" }
        return withDocumentsWriterPerThreadFlushCallPathHint {
            segmentInfo.setMaxDoc(numDocsInRAM)
            val flushState =
                SegmentWriteState(
                    infoStream,
                    directory,
                    segmentInfo,
                    fieldInfos.finish(),
                    pendingUpdates,
                    IOContext(
                        FlushInfo(
                            numDocsInRAM,
                            lastCommittedBytesUsed
                        )
                    )
                )
            val startMBUsed = lastCommittedBytesUsed / 1024.0 / 1024.0

            // Apply delete-by-docID now (delete-byDocID only
            // happens when an exception is hit processing that
            // doc, eg if analyzer has some problem w/ the text):
            if (numDeletedDocIds > 0) {
                flushState.liveDocs = FixedBitSet(numDocsInRAM)
                flushState.liveDocs!!.set(0, numDocsInRAM)
                for (i in 0..<numDeletedDocIds) {
                    flushState.liveDocs!!.clear(deleteDocIDs[i])
                }
                flushState.delCountOnFlush = numDeletedDocIds
                deleteDocIDs = IntArray(0)
            }

            if (this.isAborted) {
                if (infoStream.isEnabled("DWPT")) {
                    infoStream.message("DWPT", "flush: skip because aborting is set")
                }
                return@withDocumentsWriterPerThreadFlushCallPathHint null
            }

            val t0: Instant = Clock.System.now()
            val failurePathProbe = FailurePathProbe.find(directory)
            val previousFlushStage = failurePathProbe?.flushStage
            if (failurePathProbe != null) {
                failurePathProbe.flushStage = "flush"
            }

            if (infoStream.isEnabled("DWPT")) {
                infoStream.message(
                    "DWPT",
                    "flush postings as segment " + flushState.segmentInfo.name + " numDocs=" + numDocsInRAM
                )
            }
            val sortMap: Sorter.DocMap?
            var phase = "start"
            try {
            phase = "getHasDocValues"
            val softDeletedDocs = if (indexWriterConfig.softDeletesField != null) {
                indexingChain.getHasDocValues(indexWriterConfig.softDeletesField!!)
            } else {
                null
            }
            phase = "indexingChain.flush"
            sortMap = indexingChain.flush(flushState)
            if (softDeletedDocs == null) {
                flushState.softDelCountOnFlush = 0
            } else {
                phase = "countSoftDeletes"
                flushState.softDelCountOnFlush =
                    PendingSoftDeletes.countSoftDeletes(softDeletedDocs, flushState.liveDocs)
                assert(
                    flushState.segmentInfo.maxDoc()
                            >= flushState.softDelCountOnFlush + flushState.delCountOnFlush
                )
            }
            // We clear this here because we already resolved them (private to this segment) when writing
            // postings:
            pendingUpdates.clearDeleteTerms()
            phase = "setFiles"
            val createdFilesSnapshot = directory.createdFiles
            segmentInfo.setFiles(HashSet(createdFilesSnapshot))

            val segmentInfoPerCommit =
                SegmentCommitInfo(
                    segmentInfo,
                    0,
                    flushState.softDelCountOnFlush,
                    -1L,
                    -1L,
                    -1L,
                    StringHelper.randomId()
                )
            if (infoStream.isEnabled("DWPT")) {
                infoStream.message(
                    "DWPT",
                    ("new segment has "
                            + (if (flushState.liveDocs == null) 0 else flushState.delCountOnFlush)
                            + " deleted docs")
                )
                infoStream.message(
                    "DWPT", "new segment has " + flushState.softDelCountOnFlush + " soft-deleted docs"
                )
                infoStream.message(
                    "DWPT",
                    ("new segment has "
                            + (if (flushState.fieldInfos!!.hasTermVectors()) "vectors" else "no vectors")
                            + "; "
                            + (if (flushState.fieldInfos.hasNorms()) "norms" else "no norms")
                            + "; "
                            + (if (flushState.fieldInfos.hasDocValues()) "docValues" else "no docValues")
                            + "; "
                            + (if (flushState.fieldInfos.hasProx()) "prox" else "no prox")
                            + "; "
                            + (if (flushState.fieldInfos.hasFreq()) "freqs" else "no freqs"))
                )
                infoStream.message("DWPT", "flushedFiles=" + segmentInfoPerCommit.files())
                infoStream.message("DWPT", "flushed codec=$codec")
            }

            val segmentDeletes: BufferedUpdates?
            if (pendingUpdates.deleteQueries.isEmpty() && pendingUpdates.numFieldUpdates.load() == 0) {
                pendingUpdates.clear()
                segmentDeletes = null
            } else {
                segmentDeletes = pendingUpdates
            }

            if (infoStream.isEnabled("DWPT")) {
                val newSegmentSize: Double = segmentInfoPerCommit.sizeInBytes() / 1024.0 / 1024.0
                infoStream.message(
                    "DWPT",
                    ("flushed: segment="
                            + segmentInfo.name
                            + " ramUsed="
                            + startMBUsed
                            + " MB"
                            + " newFlushedSize="
                            + newSegmentSize
                            + " MB"
                            + " docs/MB="
                            + (flushState.segmentInfo.maxDoc() / newSegmentSize))
                )
            }

            checkNotNull(segmentInfo)

            val fs =
                FlushedSegment(
                    infoStream,
                    segmentInfoPerCommit,
                    flushState.fieldInfos!!,
                    segmentDeletes,
                    flushState.liveDocs,
                    flushState.delCountOnFlush,
                    sortMap
                )
            sealFlushedSegment(fs, sortMap, flushNotifications)
            if (infoStream.isEnabled("DWPT")) {
                infoStream.message(
                    "DWPT",
                    ("flush time "
                            + (Clock.System.now() - t0).toString(unit = kotlin.time.DurationUnit.MILLISECONDS)
                            + " ms")
                )
            }
                return@withDocumentsWriterPerThreadFlushCallPathHint fs
            } catch (t: Throwable) {
                onAbortingException(t)
                throw t
            } finally {
                if (failurePathProbe != null) {
                    failurePathProbe.flushStage = previousFlushStage
                }
                maybeAbort("flush", flushNotifications)
                hasFlushed.set(true)
            }
        }
    }

    @Throws(IOException::class)
    private fun maybeAbort(location: String, flushNotifications: FlushNotifications) {
        if (abortingException != null && !this.isAborted) {
            // if we are already aborted don't do anything here
            try {
                abort()
            } finally {
                // whatever we do here we have to fire this tragic event up.
                flushNotifications.onTragicEvent(abortingException!!, location)
            }
        }
    }

    private val filesToDelete: MutableSet<String> = HashSet()

    init {
        assert(numDocsInRAM == 0) { "num docs $numDocsInRAM" }
        deleteSlice = deleteQueue.newSlice()

        segmentInfo =
            SegmentInfo(
                directoryOrig,
                Version.LATEST,
                Version.LATEST,
                segmentName,
                -1,
                false,
                hasBlocks = false,
                codec = codec,
                diagnostics = mutableMapOf<String, String>(),
                id = StringHelper.randomId(),
                attributes = mutableMapOf<String, String>(),
                indexSort = indexWriterConfig.indexSort
            )
        assert(numDocsInRAM == 0)
        if (INFO_VERBOSE && infoStream.isEnabled("DWPT")) {
            infoStream.message(
                "DWPT",
                // TODO Thread is not supported in KMP, need to think what to do here
                (/*java.lang.Thread.currentThread().getName()
                        +*/ " init seg="
                        + segmentName
                        + " delQueue="
                        + deleteQueue)
            )
        }
        this.enableTestPoints = enableTestPoints
        indexingChain =
            IndexingChain(
                indexMajorVersionCreated,
                segmentInfo,
                this.directory,
                fieldInfos,
                indexWriterConfig
            ) { throwable: Throwable -> this.onAbortingException(throwable) }
        if (indexWriterConfig.parentField != null) {
            this.parentField =
                indexingChain.markAsReserved<NumericDocValuesField>(
                    NumericDocValuesField(indexWriterConfig.parentField!!, -1)
                )
        } else {
            this.parentField = null
        }
    }

    fun pendingFilesToDelete(): MutableSet<String> {
        return filesToDelete
    }

    private fun sortLiveDocs(
        liveDocs: Bits,
        sortMap: Sorter.DocMap
    ): FixedBitSet {
        assert(liveDocs != null && sortMap != null)
        val sortedLiveDocs = FixedBitSet(liveDocs.length())
        sortedLiveDocs.set(0, liveDocs.length())
        for (i in 0..<liveDocs.length()) {
            if (!liveDocs.get(i)) {
                sortedLiveDocs.clear(sortMap.oldToNew(i))
            }
        }
        return sortedLiveDocs
    }

    /**
     * Seals the [SegmentInfo] for the new flushed segment and persists the deleted documents
     * [FixedBitSet].
     */
    @Throws(IOException::class)
    fun sealFlushedSegment(
        flushedSegment: FlushedSegment,
        sortMap: Sorter.DocMap?,
        flushNotifications: FlushNotifications
    ) {
        //checkNotNull(flushedSegment)
        val newSegment: SegmentCommitInfo = flushedSegment.segmentInfo

        IndexWriter.setDiagnostics(
            newSegment.info,
            IndexWriter.SOURCE_FLUSH
        )

        val context =
            IOContext(
                FlushInfo(
                    newSegment.info.maxDoc(),
                    newSegment.sizeInBytes()
                )
            )

        var success = false
        try {
            if (indexWriterConfig.useCompoundFile) {
                val originalFiles: MutableSet<String> = newSegment.info.files()
                // TODO: like addIndexes, we are relying on createCompoundFile to successfully cleanup...
                IndexWriter.createCompoundFile(
                    infoStream,
                    TrackingDirectoryWrapper(directory),
                    newSegment.info,
                    context
                ) { files: MutableCollection<String> ->
                    flushNotifications.deleteUnusedFiles(
                        files
                    )
                }
                filesToDelete.addAll(originalFiles)
                newSegment.info.useCompoundFile = true
            }

            // Have codec write SegmentInfo.  Must do this after
            // creating CFS so that 1) .si isn't slurped into CFS,
            // and 2) .si reflects useCompoundFile=true change
            // above:
            codec.segmentInfoFormat().write(directory, newSegment.info, context)

            // TODO: ideally we would freeze newSegment here!!
            // because any changes after writing the .si will be
            // lost...

            // Must write deleted docs after the CFS so we don't
            // slurp the del file into CFS:
            if (flushedSegment.liveDocs != null) {
                val delCount = flushedSegment.delCount
                assert(delCount > 0)
                if (infoStream.isEnabled("DWPT")) {
                    infoStream.message(
                        "DWPT",
                        ("flush: write "
                                + delCount
                                + " deletes gen="
                                + flushedSegment.segmentInfo.delGen)
                    )
                }

                // TODO: we should prune the segment if it's 100%
                // deleted... but merge will also catch it.

                // TODO: in the NRT case it'd be better to hand
                // this del vector over to the
                // shortly-to-be-opened SegmentReader and let it
                // carry the changes; there's no reason to use
                // filesystem as intermediary here.
                val info: SegmentCommitInfo = flushedSegment.segmentInfo
                val codec: Codec = info.info.codec
                val bits: FixedBitSet = if (sortMap == null) {
                    flushedSegment.liveDocs
                } else {
                    sortLiveDocs(flushedSegment.liveDocs, sortMap)
                }
                codec.liveDocsFormat().writeLiveDocs(bits, directory, info, delCount, context)
                newSegment.delCount = delCount
                newSegment.advanceDelGen()
            }

            success = true
        } finally {
            if (!success) {
                if (infoStream.isEnabled("DWPT")) {
                    infoStream.message(
                        "DWPT",
                        "hit exception creating compound file for newly flushed segment "
                                + newSegment.info.name
                    )
                }
            }
        }
    }

    /** Get current segment info we are writing.  */
    fun getSegmentInfo(): SegmentInfo {
        return segmentInfo
    }

    override fun ramBytesUsed(): Long {
        assert(lock.isHeldByCurrentThread())
        return ((deleteDocIDs.size * Int.SIZE_BYTES.toLong())
                + pendingUpdates.ramBytesUsed()
                + indexingChain.ramBytesUsed())
    }

    override val childResources: MutableCollection<Accountable>
        get() {
            assert(lock.isHeldByCurrentThread())
            return mutableListOf(pendingUpdates, indexingChain)
        }

    override fun toString(): String {
        return ("DocumentsWriterPerThread [pendingDeletes="
                + pendingUpdates
                + ", segment="
                + segmentInfo.name
                + ", aborted="
                + this.isAborted
                + ", numDocsInRAM="
                + numDocsInRAM
                + ", deleteQueue="
                + deleteQueue
                + ", "
                + numDeletedDocIds
                + " deleted docIds"
                + "]")
    }

    /** Returns true iff this DWPT is marked as flush pending  */
    fun isFlushPending(): Boolean {
        return flushPending.get() == true
    }

    val isQueueAdvanced: Boolean
        get() = deleteQueue.isAdvanced

    /** Sets this DWPT as flush pending. This can only be set once.  */
    fun setFlushPending() {
        flushPending.set(true)
    }

    /**
     * Commits the current [.ramBytesUsed] and stores its value for later reuse. The last
     * committed bytes used can be retrieved via [.getLastCommittedBytesUsed]
     */
    fun commitLastBytesUsed(delta: Long) {
        assert(this.isHeldByCurrentThread)
        assert(this.commitLastBytesUsedDelta == delta) { "delta has changed" }
        lastCommittedBytesUsed += delta
    }

    val commitLastBytesUsedDelta: Long
        /**
         * Calculates the delta between the last committed bytes used and the currently used ram.
         *
         * @see .commitLastBytesUsed
         * @return the delta between the current [.ramBytesUsed] and the current [     ][.getLastCommittedBytesUsed]
         */
        get() {
            assert(this.isHeldByCurrentThread)
            val delta = ramBytesUsed() - lastCommittedBytesUsed
            return delta
        }

    override fun lock() {
        lock.lock()
    }

    @Throws(InterruptedException::class)
    override fun lockInterruptibly() {
        lock.lockInterruptibly()
    }

    override fun tryLock(): Boolean {
        return lock.tryLock()
    }

    @Throws(InterruptedException::class)
    override fun tryLock(time: Long, unit: TimeUnit): Boolean {
        return lock.tryLock(time, unit)
    }

    val isHeldByCurrentThread: Boolean
        /**
         * Returns true if the DWPT's lock is held by the current thread
         *
         * @see ReentrantLock.isHeldByCurrentThread
         */
        get() = lock.isHeldByCurrentThread()

    override fun unlock() {
        lock.unlock()
    }

    override fun newCondition(): Condition {
        throw UnsupportedOperationException()
    }

    /** Returns `true` iff this DWPT has been flushed  */
    fun hasFlushed(): Boolean {
        return hasFlushed.get() == true
    }

    companion object {
        private const val INFO_VERBOSE = false
    }
}
