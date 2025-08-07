package org.gnit.lucenekmp.index

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.codecs.FieldInfosFormat
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.index.DocValuesUpdate.BinaryDocValuesUpdate
import org.gnit.lucenekmp.index.DocValuesUpdate.NumericDocValuesUpdate
import org.gnit.lucenekmp.index.FieldInfos.FieldNumbers
import org.gnit.lucenekmp.index.IndexWriterConfig.OpenMode
import org.gnit.lucenekmp.index.Sorter.DocMap
import org.gnit.lucenekmp.internal.hppc.LongObjectHashMap
import org.gnit.lucenekmp.internal.tests.IndexPackageAccess
import org.gnit.lucenekmp.internal.tests.IndexWriterAccess
import org.gnit.lucenekmp.internal.tests.TestSecrets
import org.gnit.lucenekmp.jdkport.AtomicInteger
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.jdkport.Executor
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.Objects
import org.gnit.lucenekmp.jdkport.ReentrantLock
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.TimeUnit
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.computeIfAbsent
import org.gnit.lucenekmp.jdkport.getAndSet
import org.gnit.lucenekmp.jdkport.poll
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.FieldExistsQuery
import org.gnit.lucenekmp.search.MatchAllDocsQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.Sort
import org.gnit.lucenekmp.search.SortField
import org.gnit.lucenekmp.store.AlreadyClosedException
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.FlushInfo
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.Lock
import org.gnit.lucenekmp.store.LockValidatingDirectoryWrapper
import org.gnit.lucenekmp.store.MergeInfo
import org.gnit.lucenekmp.store.TrackingDirectoryWrapper
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.ByteBlockPool
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.Constants
import org.gnit.lucenekmp.util.Counter
import org.gnit.lucenekmp.util.IOConsumer
import org.gnit.lucenekmp.util.IOFunction
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.InfoStream
import org.gnit.lucenekmp.util.StringHelper
import org.gnit.lucenekmp.util.ThreadInterruptedException
import org.gnit.lucenekmp.util.UnicodeUtil
import org.gnit.lucenekmp.util.Version
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.jvm.JvmOverloads
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds

/**
 * An <code>IndexWriter</code> creates and maintains an index.
 *
 * <p>The {@link OpenMode} option on {@link IndexWriterConfig#setOpenMode(OpenMode)} determines
 * whether a new index is created, or whether an existing index is opened. Note that you can open an
 * index with {@link OpenMode#CREATE} even while readers are using the index. The old readers will
 * continue to search the "point in time" snapshot they had opened, and won't see the newly created
 * index until they re-open. If {@link OpenMode#CREATE_OR_APPEND} is used IndexWriter will create a
 * new index if there is not already an index at the provided path and otherwise open the existing
 * index.
 *
 * <p>In either case, documents are added with {@link #addDocument(Iterable) addDocument} and
 * removed with {@link #deleteDocuments(Term...)} or {@link #deleteDocuments(Query...)}. A document
 * can be updated with {@link #updateDocument(Term, Iterable) updateDocument} (which just deletes
 * and then adds the entire document). When finished adding, deleting and updating documents, {@link
 * #close() close} should be called. <a id="sequence_numbers"></a>
 *
 * <p>Each method that changes the index returns a {@code long} sequence number, which expresses the
 * effective order in which each change was applied. {@link #commit} also returns a sequence number,
 * describing which changes are in the commit point and which are not. Sequence numbers are
 * transient (not saved into the index in any way) and only valid within a single {@code
 * IndexWriter} instance. <a id="flush"></a>
 *
 * <p>These changes are buffered in memory and periodically flushed to the {@link Directory} (during
 * the above method calls). A flush is triggered when there are enough added documents since the
 * last flush. Flushing is triggered either by RAM usage of the documents (see {@link
 * IndexWriterConfig#setRAMBufferSizeMB}) or the number of added documents (see {@link
 * IndexWriterConfig#setMaxBufferedDocs(int)}). The default is to flush when RAM usage hits {@link
 * IndexWriterConfig#DEFAULT_RAM_BUFFER_SIZE_MB} MB. For best indexing speed you should flush by RAM
 * usage with a large RAM buffer. In contrast to the other flush options {@link
 * IndexWriterConfig#setRAMBufferSizeMB} and {@link IndexWriterConfig#setMaxBufferedDocs(int)},
 * deleted terms won't trigger a segment flush. Note that flushing just moves the internal buffered
 * state in IndexWriter into the index, but these changes are not visible to IndexReader until
 * either {@link #commit()} or {@link #close} is called. A flush may also trigger one or more
 * segment merges, which by default run within a background thread so as not to block the
 * addDocument calls (see <a href="#mergePolicy">below</a> for changing the {@link MergeScheduler}).
 *
 * <p>Opening an <code>IndexWriter</code> creates a lock file for the directory in use. Trying to
 * open another <code>IndexWriter</code> on the same directory will lead to a {@link
 * LockObtainFailedException}. <a id="deletionPolicy"></a>
 *
 * <p>Expert: <code>IndexWriter</code> allows an optional {@link IndexDeletionPolicy} implementation
 * to be specified. You can use this to control when prior commits are deleted from the index. The
 * default policy is {@link KeepOnlyLastCommitDeletionPolicy} which removes all prior commits as
 * soon as a new commit is done. Creating your own policy can allow you to explicitly keep previous
 * "point in time" commits alive in the index for some time, either because this is useful for your
 * application, or to give readers enough time to refresh to the new commit without having the old
 * commit deleted out from under them. The latter is necessary when multiple computers take turns
 * opening their own {@code IndexWriter} and {@code IndexReader}s against a single shared index
 * mounted via remote filesystems like NFS which do not support "delete on last close" semantics. A
 * single computer accessing an index via NFS is fine with the default deletion policy since NFS
 * clients emulate "delete on last close" locally. That said, accessing an index via NFS will likely
 * result in poor performance compared to a local IO device. <a id="mergePolicy"></a>
 *
 * <p>Expert: <code>IndexWriter</code> allows you to separately change the {@link MergePolicy} and
 * the {@link MergeScheduler}. The {@link MergePolicy} is invoked whenever there are changes to the
 * segments in the index. Its role is to select which merges to do, if any, and return a {@link
 * MergePolicy.MergeSpecification} describing the merges. The default is {@link
 * LogByteSizeMergePolicy}. Then, the {@link MergeScheduler} is invoked with the requested merges
 * and it decides when and how to run the merges. The default is {@link ConcurrentMergeScheduler}.
 * <a id="OOME"></a>
 *
 * <p><b>NOTE</b>: if you hit an Error, or disaster strikes during a checkpoint then IndexWriter
 * will close itself. This is a defensive measure in case any internal state (buffered documents,
 * deletions, reference counts) were corrupted. Any subsequent calls will throw an
 * AlreadyClosedException. <a id="thread-safety"></a>
 *
 * <p><b>NOTE</b>: {@link IndexWriter} instances are completely thread safe, meaning multiple
 * threads can call any of its methods, concurrently. If your application requires external
 * synchronization, you should <b>not</b> synchronize on the <code>IndexWriter</code> instance as
 * this may cause deadlock; use your own (non-Lucene) objects instead.
 *
 * <p><b>NOTE</b>: If you call <code>Thread.interrupt()</code> on a thread that's within
 * IndexWriter, IndexWriter will try to catch this (eg, if it's in a wait() or Thread.sleep()), and
 * will then throw the unchecked exception {@link ThreadInterruptedException} and <b>clear</b> the
 * interrupt status on the thread.
 */

/*
 * Clarification: Check Points (and commits)
 * IndexWriter writes new index files to the directory without writing a new segments_N
 * file which references these new files. It also means that the state of
 * the in-memory SegmentInfos object is different than the most recent
 * segments_N file written to the directory.
 *
 * Each time the SegmentInfos is changed, and matches the (possibly
 * modified) directory files, we have a new "check point".
 * If the modified/new SegmentInfos is written to disk - as a new
 * (generation of) segments_N file - this check point is also an
 * IndexCommit.
 *
 * A new checkpoint always replaces the previous checkpoint and
 * becomes the new "front" of the index. This allows the IndexFileDeleter
 * to delete files that are referenced only by stale checkpoints.
 * (files that were created since the last commit, but are no longer
 * referenced by the "front" of the index). For this, IndexFileDeleter
 * keeps track of the last non-commit checkpoint.
 */
@OptIn(ExperimentalAtomicApi::class)
open class IndexWriter(d: Directory, conf: IndexWriterConfig) : AutoCloseable, TwoPhaseCommit, Accountable,
    MergePolicy.MergeContext {
    /** Used only for testing.  */
    private val enableTestPoints: Boolean

    // when unrecoverable disaster strikes, we populate this with the reason that we had to close
    // IndexWriter
    @OptIn(ExperimentalAtomicApi::class)
    private val tragedy: AtomicReference<Throwable?> = AtomicReference(null)

    private val directoryOrig: Directory // original user directory
    private val directory: Directory // wrapped with additional checks

    // increments every time a change is completed
    @OptIn(ExperimentalAtomicApi::class)
    private val changeCount: AtomicLong = AtomicLong(0)

    @Volatile
    private var lastCommitChangeCount: Long = 0 // last changeCount that was committed

    // list of segmentInfo we will fallback to if the commit fails
    private var rollbackSegments: MutableList<SegmentCommitInfo>? = null

    // set when a commit is pending (after prepareCommit() & before commit())
    @Volatile
    private var pendingCommit: SegmentInfos? = null

    @Volatile
    private var pendingSeqNo: Long = 0

    @Volatile
    private var pendingCommitChangeCount: Long = 0

    private var filesToCommit: MutableCollection<String>? = null

    private val segmentInfos: SegmentInfos
    val globalFieldNumberMap: FieldNumbers

    val docWriter: DocumentsWriter
    private val eventQueue = EventQueue(this)
    private val mergeSource: MergeScheduler.MergeSource = IndexWriterMergeSource(this)
    private val addIndexesMergeSource = AddIndexesMergeSource(this)

    private val writeDocValuesLock: ReentrantLock = ReentrantLock()

    internal class EventQueue(private val writer: IndexWriter) : AutoCloseable {
        @Volatile
        private var closed = false

        // we use a semaphore here instead of simply synced methods to allow
        // events to be processed concurrently by multiple threads such that all events
        // for a certain thread are processed once the thread returns from IW
        private val permits: Semaphore = Semaphore(Int.MAX_VALUE)
        private val queue: ArrayDeque<Event> = ArrayDeque()

        private fun acquire() {
            if (!permits.tryAcquire()) {
                throw AlreadyClosedException("queue is closed")
            }
            if (closed) {
                permits.release()
                throw AlreadyClosedException("queue is closed")
            }
        }

        fun add(event: Event): Boolean {
            acquire()
            try {
                return queue.add(event)
            } finally {
                permits.release()
            }
        }

        @Throws(IOException::class)
        fun processEvents() {
            acquire()
            try {
                processEventsInternal()
            } finally {
                permits.release()
            }
        }

        @Throws(IOException::class)
        private fun processEventsInternal() {
            assert(
                Int.MAX_VALUE - permits.availablePermits > 0
            ) { "must acquire a permit before processing events" }
            var event: Event
            while ((queue.poll().also { event = it!! }) != null) {
                event.process(writer)
            }
        }

        // TODO Synchronized is not supported in KMP, need to think what to do here
        /*@Synchronized*/
        override fun close() { // synced to prevent double closing
            assert(!closed) { "we should never close this twice" }
            closed = true
            // it's possible that we close this queue while we are in a processEvents call
            if (writer.getTragicException() != null) {
                // we are already handling a tragic exception let's drop it all on the floor and return
                queue.clear()
            } else {
                // now we acquire all the permits to ensure we are the only one processing the queue
                runBlocking {
                    try {
                        permits.acquire(/*Int.MAX_VALUE*/)
                    } catch (e: CancellationException) {
                        throw ThreadInterruptedException(e)
                    }
                    try {
                        processEventsInternal()
                    } finally {
                        permits.release(/*Int.MAX_VALUE*/)
                    }
                }
            }
        }
    }

    private val deleter: IndexFileDeleter

    // used by forceMerge to note those needing merging
    private val segmentsToMerge: MutableMap<SegmentCommitInfo, Boolean> = HashMap()
    private var mergeMaxNumSegments = 0

    private var writeLock: Lock?

    @Volatile
    private var closed = false

    @Volatile
    private var closing = false

    @OptIn(ExperimentalAtomicApi::class)
    private val maybeMerge: AtomicBoolean = AtomicBoolean(true)

    private var commitUserData: Iterable<MutableMap.MutableEntry<String, String>>? = null

    // Holds all SegmentInfo instances currently involved in
    // merges
    override val mergingSegments: HashSet<SegmentCommitInfo> = HashSet()
    private val mergeScheduler: MergeScheduler
    private val runningAddIndexesMerges: MutableSet<SegmentMerger> = HashSet()
    private val pendingMerges: ArrayDeque<MergePolicy.OneMerge> = ArrayDeque()
    private val runningMerges: MutableSet<MergePolicy.OneMerge> = HashSet()
    private val mergeExceptions: MutableList<MergePolicy.OneMerge> = mutableListOf()
    private val merges = Merges()
    private var mergeGen: Long = 0
    private var didMessageState = false

    @OptIn(ExperimentalAtomicApi::class)
    private val flushCount: AtomicInteger = AtomicInteger(0)

    @OptIn(ExperimentalAtomicApi::class)
    private val flushDeletesCount: AtomicInteger = AtomicInteger(0)
    private val readerPool: ReaderPool
    private val bufferedUpdatesStream: BufferedUpdatesStream

    private val eventListener: IndexWriterEventListener

    /**
     * Counts how many merges have completed; this is used by [ ][.forceApply] to handle concurrently apply deletes/updates with merges
     * completing.
     */
    @OptIn(ExperimentalAtomicApi::class)
    private val mergeFinishedGen: AtomicLong = AtomicLong(0)

    // The instance that was passed to the constructor. It is saved only in order
    // to allow users to query an IndexWriter settings.
    var config: LiveIndexWriterConfig
        /**
         * Returns a [LiveIndexWriterConfig], which can be used to query the IndexWriter current
         * settings, as well as modify "live" ones.
         */
        get(): LiveIndexWriterConfig {
            ensureOpen(false)
            return config
        }

    /**
     * System.nanoTime() when commit started; used to write an infoStream message about how long
     * commit took.
     */
    private var startCommitTime: Long = 0

    /**
     * How many documents are in the index, or are in the process of being added (reserved). E.g.,
     * operations like addIndexes will first reserve the right to add N docs, before they actually
     * change the index, much like how hotels place an "authorization hold" on your credit card to
     * make sure they can later charge you when you check out.
     */
    @OptIn(ExperimentalAtomicApi::class)
    private val pendingNumDocs: AtomicLong = AtomicLong(0)

    private val softDeletesEnabled: Boolean

    @OptIn(ExperimentalAtomicApi::class)
    private val flushNotifications: DocumentsWriter.FlushNotifications =
        object : DocumentsWriter.FlushNotifications {
            override fun deleteUnusedFiles(files: MutableCollection<String>) {
                eventQueue.add { w: IndexWriter -> w.deleteNewFiles(files) }
            }

            override fun flushFailed(info: SegmentInfo) {
                eventQueue.add { w: IndexWriter -> w.flushFailed(info) }
            }

            @Throws(IOException::class)
            override fun afterSegmentsFlushed() {
                publishFlushedSegments(false)
            }

            override fun onTragicEvent(event: Throwable, message: String) {
                this@IndexWriter.onTragicEvent(event, message)
            }

            override fun onDeletesApplied() {
                eventQueue.add { w: IndexWriter ->
                    try {
                        w.publishFlushedSegments(true)
                    } finally {
                        flushCount.incrementAndFetch()
                    }
                }
            }

            override fun onTicketBacklog() {
                eventQueue.add { w: IndexWriter -> w.publishFlushedSegments(true) }
            }
        }

    /**
     * Expert: returns a readonly reader, covering all committed as well as un-committed changes to
     * the index. This provides "near real-time" searching, in that changes made during an IndexWriter
     * session can be quickly made available for searching without closing the writer nor calling
     * [.commit].
     *
     *
     * Note that this is functionally equivalent to calling {#flush} and then opening a new reader.
     * But the turnaround time of this method should be faster since it avoids the potentially costly
     * [.commit].
     *
     *
     * You must close the [IndexReader] returned by this method once you are done using it.
     *
     *
     * It's *near* real-time because there is no hard guarantee on how quickly you can get a
     * new reader after making changes with IndexWriter. You'll have to experiment in your situation
     * to determine if it's fast enough. As this is a new and experimental feature, please report back
     * on your findings so we can learn, improve and iterate.
     *
     *
     * The resulting reader supports [DirectoryReader.openIfChanged], but that call will
     * simply forward back to this method (though this may change in the future).
     *
     *
     * The very first time this method is called, this writer instance will make every effort to
     * pool the readers that it opens for doing merges, applying deletes, etc. This means additional
     * resources (RAM, file descriptors, CPU time) will be consumed.
     *
     *
     * For lower latency on reopening a reader, you should call [ ][IndexWriterConfig.setMergedSegmentWarmer] to pre-warm a newly merged segment before it's
     * committed to the index. This is important for minimizing index-to-search delay after a large
     * merge.
     *
     *
     * If an addIndexes* call is running in another thread, then this reader will only search those
     * segments from the foreign index that have been successfully copied over, so far.
     *
     *
     * **NOTE**: Once the writer is closed, any outstanding readers may continue to be used.
     * However, if you attempt to reopen any of those readers, you'll hit an [ ].
     *
     * @lucene.experimental
     * @return IndexReader that covers entire index plus all changes made so far by this IndexWriter
     * instance
     * @throws IOException If there is a low-level I/O error
     */
    @OptIn(ExperimentalAtomicApi::class)
    @Throws(IOException::class)
    fun getReader(applyAllDeletes: Boolean, writeAllDeletes: Boolean): DirectoryReader {
        ensureOpen()

        require(!(writeAllDeletes && !applyAllDeletes)) { "applyAllDeletes must be true when writeAllDeletes=true" }

        val tStart: Long = System.currentTimeMillis()

        if (infoStream.isEnabled("IW")) {
            infoStream.message("IW", "flush at getReader")
        }
        // Do this up front before flushing so that the readers
        // obtained during this flush are pooled, the first time
        // this method is called:
        readerPool.enableReaderPooling()
        var r: StandardDirectoryReader? = null
        doBeforeFlush()
        var anyChanges: Boolean
        val maxFullFlushMergeWaitMillis: Long = config.maxFullFlushMergeWaitMillis
        /*
     * for releasing a NRT reader we must ensure that
     * DW doesn't add any segments or deletes until we are
     * done with creating the NRT DirectoryReader.
     * We release the two stage full flush after we are done opening the
     * directory reader!
     */
        var onGetReaderMerges: MergePolicy.MergeSpecification? = null
        val stopCollectingMergedReaders = AtomicBoolean(false)
        val mergedReaders: MutableMap<String, SegmentReader> = HashMap()
        val openedReadOnlyClones: MutableMap<String, SegmentReader> = HashMap()
        // this function is used to control which SR are opened in order to keep track of them
        // and to reuse them in the case we wait for merges in this getReader call.
        val readerFactory: IOFunction<SegmentCommitInfo, SegmentReader> =
            IOFunction { sci: SegmentCommitInfo ->
                val rld: ReadersAndUpdates = getPooledInstance(sci, true)!!
                try {

                    // TODO Thread is not supported in KMP, need to think what to do here
                    //assert(java.lang.Thread.holdsLock(this@IndexWriter))
                    val segmentReader: SegmentReader =
                        runBlocking { rld.getReadOnlyClone(IOContext.DEFAULT) }
                    // only track this if we actually do fullFlush merges
                    if (maxFullFlushMergeWaitMillis > 0) {
                        openedReadOnlyClones[sci.info.name] = segmentReader
                    }
                    return@IOFunction segmentReader
                } finally {
                    release(rld)
                }
            }
        var onGetReaderMergeResources: AutoCloseable? = null
        var openingSegmentInfos: SegmentInfos? = null
        var success2 = false
        try {
            /* This is the essential part of the getReader method. We need to take care of the following things:
       *  - flush all currently in-memory DWPTs to disk
       *  - apply all deletes & updates to new and to the existing DWPTs
       *  - prevent flushes and applying deletes of concurrently indexing DWPTs to be applied
       *  - open a SDR on the updated SIS
       *
       * in order to prevent concurrent flushes we call DocumentsWriter#flushAllThreads that swaps out the deleteQueue
       *  (this enforces a happens before relationship between this and the subsequent full flush) and informs the
       * FlushControl (#markForFullFlush()) that it should prevent any new DWPTs from flushing until we are \
       * done (DocumentsWriter#finishFullFlush(boolean)). All this is guarded by the fullFlushLock to prevent multiple
       * full flushes from happening concurrently. Once the DocWriter has initiated a full flush we can sequentially flush
       * and apply deletes & updates to the written segments without worrying about concurrently indexing DWPTs. The important
       * aspect is that it all happens between DocumentsWriter#flushAllThread() and DocumentsWriter#finishFullFlush(boolean)
       * since once the flush is marked as done deletes start to be applied to the segments on disk without guarantees that
       * the corresponding added documents (in the update case) are flushed and visible when opening a SDR.
       */
            var success = false

            // TODO synchronized is not supported in KMP, need to think what to do here
            //synchronized(fullFlushLock) {
            try {
                // TODO: should we somehow make the seqNo available in the returned NRT reader
                anyChanges = runBlocking { docWriter.flushAllThreads() } < 0
                if (!anyChanges) {
                    // prevent double increment since docWriter#doFlush increments the flushcount
                    // if we flushed anything.
                    flushCount.incrementAndFetch()
                }
                publishFlushedSegments(true)
                processEvents(false)

                if (applyAllDeletes) {
                    applyAllDeletesAndUpdates()
                }

                // TODO synchronized is not supported in KMP, need to think what to do here
                //synchronized(this) {
                // NOTE: we cannot carry doc values updates in memory yet, so we always must write them
                // through to disk and re-open each
                // SegmentReader:

                // TODO: we could instead just clone SIS and pull/incref readers in sync'd block, and
                // then do this w/o IW's lock
                // Must do this sync'd on IW to prevent a merge from completing at the last second and
                // failing to write its DV updates:
                writeReaderPool(writeAllDeletes)

                // Prevent segmentInfos from changing while opening the
                // reader; in theory we could instead do similar retry logic,
                // just like we do when loading segments_N
                r =
                    StandardDirectoryReader.open(
                        this, readerFactory, segmentInfos, applyAllDeletes, writeAllDeletes
                    )
                if (infoStream.isEnabled("IW")) {
                    infoStream.message("IW", "return reader version=" + r.version + " reader=" + r)
                }
                if (maxFullFlushMergeWaitMillis > 0) {
                    // we take the SIS from the reader which has already pruned away fully deleted readers
                    // this makes pulling the readers below after the merge simpler since we can be safe
                    // that
                    // they are not closed. Every segment has a corresponding SR in the SDR we opened if
                    // we use
                    // this SIS
                    // we need to do this rather complicated management of SRs and infos since we can't
                    // wait for merges
                    // while we hold the fullFlushLock since the merge might hit a tragic event and that
                    // must not be reported
                    // while holding that lock. Merging outside of the lock ie. after calling
                    // docWriter.finishFullFlush(boolean) would
                    // yield wrong results because deletes might sneak in during the merge
                    openingSegmentInfos = r.segmentInfos.clone()
                    onGetReaderMerges =
                        preparePointInTimeMerge(
                            openingSegmentInfos,
                            { stopCollectingMergedReaders.load() },
                            MergeTrigger.GET_READER,
                            { sci: SegmentCommitInfo ->
                                assert(
                                    !stopCollectingMergedReaders.load()
                                ) { "illegal state  merge reader must be not pulled since we already stopped waiting for merges" }
                                val apply: SegmentReader = readerFactory.apply(sci)
                                mergedReaders[sci.info.name] = apply
                                // we need to incRef the files of the opened SR otherwise it's possible that
                                // another merge
                                // removes the segment before we pass it on to the SDR
                                deleter.incRef(sci.files())
                            })
                    onGetReaderMergeResources =
                        AutoCloseable {
                            // this needs to be closed once after we are done. In the case of an exception
                            // it releases
                            // all resources, closes the merged readers and decrements the files references.
                            // this only happens for readers that haven't been removed from the
                            // mergedReaders and release elsewhere

                            // TODO synchronized is not supported in KMP, need to think what to do here
                            //synchronized(this) {
                            stopCollectingMergedReaders.store(true)
                            IOUtils.close(
                                mergedReaders.values
                                    .map { sr: SegmentReader ->
                                        AutoCloseable {
                                            try {
                                                deleter.decRef(sr.segmentInfo.files())
                                            } finally {
                                                sr.close()
                                            }
                                        }
                                    }
                                    .toList())
                            //}
                        }
                }
                //} // synchronized(this)
                success = true
            } finally {
                // Done: finish the full flush!

                // TODO Thread is not supported in KMP, need to think what to do here
                //assert(java.lang.Thread.holdsLock(fullFlushLock))
                docWriter.finishFullFlush(success)
                if (success) {
                    processEvents(false)
                    doAfterFlush()
                } else {
                    if (infoStream.isEnabled("IW")) {
                        infoStream.message("IW", "hit exception during NRT reader")
                    }
                }
            }
            //} // synchronized(fullFlushLock)
            if (onGetReaderMerges != null) { // only relevant if we do merge on getReader
                val mergedReader: StandardDirectoryReader? =
                    finishGetReaderMerge(
                        stopCollectingMergedReaders,
                        mergedReaders,
                        openedReadOnlyClones,
                        openingSegmentInfos!!,
                        applyAllDeletes,
                        writeAllDeletes,
                        onGetReaderMerges,
                        maxFullFlushMergeWaitMillis
                    )
                if (mergedReader != null) {
                    try {
                        r.close()
                    } finally {
                        r = mergedReader
                    }
                }
            }

            anyChanges = anyChanges or maybeMerge.getAndSet(false)
            if (anyChanges) {
                maybeMerge(
                    config.mergePolicy,
                    MergeTrigger.FULL_FLUSH,
                    UNBOUNDED_MAX_MERGE_SEGMENTS
                )
            }
            if (infoStream.isEnabled("IW")) {
                infoStream.message("IW", "getReader took " + (System.currentTimeMillis() - tStart) + " ms")
            }
            success2 = true
        } catch (tragedy: Error) {
            tragicEvent(tragedy, "getReader")
            throw tragedy
        } finally {
            if (!success2) {
                try {
                    IOUtils.closeWhileHandlingException(r, onGetReaderMergeResources)
                } finally {
                    maybeCloseOnTragicEvent()
                }
            } else {
                IOUtils.close(onGetReaderMergeResources)
            }
        }
        return r
    }

    @Throws(IOException::class)
    private fun finishGetReaderMerge(
        stopCollectingMergedReaders: AtomicBoolean,
        mergedReaders: MutableMap<String, SegmentReader>,
        openedReadOnlyClones: MutableMap<String, SegmentReader>,
        openingSegmentInfos: SegmentInfos,
        applyAllDeletes: Boolean,
        writeAllDeletes: Boolean,
        pointInTimeMerges: MergePolicy.MergeSpecification,
        maxCommitMergeWaitMillis: Long
    ): StandardDirectoryReader? {
        //checkNotNull(openingSegmentInfos)
        runBlocking { mergeScheduler.merge(mergeSource, MergeTrigger.GET_READER) }
        pointInTimeMerges.await(maxCommitMergeWaitMillis, TimeUnit.MILLISECONDS)

        // TODO synchronized is not supported in KMP, need to think what to do here
        //synchronized(this) {
        stopCollectingMergedReaders.store(true)
        val reader: StandardDirectoryReader? =
            maybeReopenMergedNRTReader(
                mergedReaders,
                openedReadOnlyClones,
                openingSegmentInfos,
                applyAllDeletes,
                writeAllDeletes
            )
        IOUtils.close(mergedReaders.values)
        mergedReaders.clear()
        return reader
        //}
    }

    @Throws(IOException::class)
    private fun maybeReopenMergedNRTReader(
        mergedReaders: MutableMap<String, SegmentReader>,
        openedReadOnlyClones: MutableMap<String, SegmentReader>,
        openingSegmentInfos: SegmentInfos,
        applyAllDeletes: Boolean,
        writeAllDeletes: Boolean
    ): StandardDirectoryReader? {

        // TODO Thread is not supported in KMP, need to think what to do here
        //assert(java.lang.Thread.holdsLock(this))
        if (!mergedReaders.isEmpty()) {
            val files: MutableCollection<String> = ArrayList()
            try {
                return StandardDirectoryReader.open(
                    this,
                    { sci: SegmentCommitInfo ->
                        // as soon as we remove the reader and return it the StandardDirectoryReader#open
                        // will take care of closing it. We only need to handle the readers that remain in the
                        // mergedReaders map and close them.
                        var remove: SegmentReader? = mergedReaders.remove(sci.info.name)
                        if (remove == null) {
                            remove = openedReadOnlyClones.remove(sci.info.name)
                            checkNotNull(remove)
                            // each of the readers we reuse from the previous reader needs to be incRef'd
                            // since we reuse them but don't have an implicit incRef in the SDR:open call
                            remove.incRef()
                        } else {
                            files.addAll(remove.segmentInfo.files())
                        }
                        remove
                    },
                    openingSegmentInfos,
                    applyAllDeletes,
                    writeAllDeletes
                )
            } finally {
                // now the SDR#open call has incRef'd the files so we can let them go
                deleter.decRef(files)
            }
        }
        return null
    }

    override fun ramBytesUsed(): Long {
        ensureOpen()
        return docWriter.ramBytesUsed()
    }

    /** Returns the number of bytes currently being flushed  */
    fun getFlushingBytes(): Long {
        ensureOpen()
        return docWriter.flushingBytes
    }

    @Throws(IOException::class)
    fun writeSomeDocValuesUpdates() {
        if (writeDocValuesLock.tryLock()) {
            try {
                val ramBufferSizeMB: Double = config.rAMBufferSizeMB
                // If the reader pool is > 50% of our IW buffer, then write the updates:
                if (ramBufferSizeMB != IndexWriterConfig.DISABLE_AUTO_FLUSH.toDouble()) {
                    val startNS: Long = System.nanoTime()

                    var ramBytesUsed: Long = readerPool.ramBytesUsed()
                    if (ramBytesUsed > 0.5 * ramBufferSizeMB * 1024 * 1024) {
                        if (infoStream.isEnabled("BD")) {
                            infoStream.message(
                                "BD",
                                "now write some pending DV updates: ${ramBytesUsed / 1024.0 / 1024.0} MB used vs IWC Buffer $ramBufferSizeMB MB"
                            )
                        }

                        // Sort by largest ramBytesUsed:
                        val list: MutableList<ReadersAndUpdates> = readerPool.readersByRam
                        var count = 0
                        for (rld in list) {
                            if (ramBytesUsed <= 0.5 * ramBufferSizeMB * 1024 * 1024) {
                                break
                            }
                            // We need to do before/after because not all RAM in this RAU is used by DV updates,
                            // and
                            // not all of those bytes can be written here:
                            val bytesUsedBefore: Long = rld.ramBytesUsed.load()
                            if (bytesUsedBefore == 0L) {
                                continue  // nothing to do here - lets not acquire the lock
                            }
                            // Only acquire IW lock on each write, since this is a time consuming operation.  This
                            // way
                            // other threads get a chance to run in between our writes.

                            // TODO synchronized is not supported in KMP, need to think what to do here
                            //synchronized(this) {
                            // It's possible that the segment of a reader returned by readerPool#getReadersByRam
                            // is dropped before being processed here. If it happens, we need to skip that
                            // reader.
                            // this is also best effort to free ram, there might be some other thread writing
                            // this rld concurrently
                            // which wins and then if readerPooling is off this rld will be dropped.
                            if (readerPool.get(rld.info, false) == null) {
                                continue
                            }
                            if (runBlocking {
                                    rld.writeFieldUpdates(
                                        directory,
                                        globalFieldNumberMap,
                                        bufferedUpdatesStream.completedDelGen,
                                        infoStream
                                    )
                                }
                            ) {
                                checkpointNoSIS()
                            }
                            //}
                            val bytesUsedAfter: Long = rld.ramBytesUsed.load()
                            ramBytesUsed -= bytesUsedBefore - bytesUsedAfter
                            count++
                        }

                        if (infoStream.isEnabled("BD")) {
                            infoStream.message(
                                "BD",
                                "done write some DV updates for $count segments: now ${readerPool.ramBytesUsed() / 1024.0 / 1024.0} MB used vs IWC Buffer $ramBufferSizeMB MB; took ${
                                    ((System.nanoTime() - startNS) / TimeUnit.SECONDS.toNanos(
                                        1
                                    ).toDouble())
                                } sec"
                            )
                        }
                    }
                }
            } finally {
                writeDocValuesLock.unlock()
            }
        }
    }

    /**
     * Obtain the number of deleted docs for a pooled reader. If the reader isn't being pooled, the
     * segmentInfo's delCount is returned.
     */
    override fun numDeletedDocs(info: SegmentCommitInfo): Int {
        ensureOpen(false)
        validate(info)
        val rld: ReadersAndUpdates? = getPooledInstance(info, false)
        if (rld != null) {
            return rld.delCount // get the full count from here since SCI might change concurrently
        } else {
            val delCount: Int = info.getDelCount(softDeletesEnabled)
            assert(
                delCount <= info.info.maxDoc()
            ) { "delCount: " + delCount + " maxDoc: " + info.info.maxDoc() }
            return delCount
        }
    }

    /**
     * Used internally to throw an [AlreadyClosedException] if this IndexWriter has been closed
     * or is in the process of closing.
     *
     * @param failIfClosing if true, also fail when `IndexWriter` is in the process of closing
     * (`closing=true`) but not yet done closing ( `closed=false`)
     * @throws AlreadyClosedException if this IndexWriter is closed or in the process of closing
     */
    /**
     * Used internally to throw an [AlreadyClosedException] if this IndexWriter has been closed
     * (`closed=true`) or is in the process of closing (`closing=true`).
     *
     *
     * Calls [ensureOpen(true)][.ensureOpen].
     *
     * @throws AlreadyClosedException if this IndexWriter is closed
     */
    @Throws(AlreadyClosedException::class)
    fun ensureOpen(failIfClosing: Boolean = true) {
        if (closed || (failIfClosing && closing)) {
            throw AlreadyClosedException("this IndexWriter is closed", tragedy.load())
        }
    }

    /** Confirms that the incoming index sort (if any) matches the existing index sort (if any).  */
    private fun validateIndexSort() {
        val indexSort: Sort? = config.indexSort
        if (indexSort != null) {
            for (info in segmentInfos) {
                val segmentIndexSort: Sort? = info.info.indexSort
                require(!(segmentIndexSort == null || !isCongruentSort(indexSort, segmentIndexSort))) {
                    ("cannot change previous indexSort="
                            + segmentIndexSort
                            + " (from segment="
                            + info
                            + ") to new indexSort="
                            + indexSort)
                }
            }
        }
    }

    /**
     * Loads or returns the already loaded global field number map for this [SegmentInfos]. If
     * this [SegmentInfos] has no global field number map, the returned instance is empty.
     */
    @Throws(IOException::class)
    private fun getFieldNumberMap(): FieldNumbers {
        val map = FieldNumbers(config.softDeletesField!!, config.parentField)

        for (info in segmentInfos) {
            val fis: FieldInfos = readFieldInfos(info)
            for (fi in fis) {
                map.addOrGet(fi)
            }
        }
        return map
    }

    private fun messageState() {
        if (infoStream.isEnabled("IW") && !didMessageState) {
            didMessageState = true
            infoStream.message(
                "IW",
                ("\ndir="
                        + directoryOrig
                        + "\n"
                        + "index="
                        + segString()
                        + "\n"
                        + "version="
                        + Version.LATEST.toString()
                        + "\n"
                        + config.toString())
            )
        }
    }

    /**
     * Gracefully closes (commits, waits for merges), but calls rollback if there's an exc so the
     * IndexWriter is always closed. This is called from [.close] when [ ][IndexWriterConfig.commitOnClose] is `true`.
     */
    @Throws(IOException::class)
    private fun shutdown() {
        check(pendingCommit == null) { "cannot close: prepareCommit was already called with no corresponding call to commit" }
        // Ensure that only one thread actually gets to do the
        // closing
        if (shouldClose(true)) {
            try {
                if (infoStream.isEnabled("IW")) {
                    infoStream.message("IW", "now flush at close")
                }

                flush(true, applyAllDeletes = true)
                waitForMerges()
                commitInternal(config.mergePolicy)
            } catch (t: Throwable) {
                // Be certain to close the index on any exception
                try {
                    rollbackInternal()
                } catch (t1: Throwable) {
                    t.addSuppressed(t1)
                }
                throw t
            }
            rollbackInternal() // if we got that far lets rollback and close
        }
    }

    /**
     * Closes all open resources and releases the write lock.
     *
     *
     * If [IndexWriterConfig.commitOnClose] is `true`, this will attempt to
     * gracefully shut down by writing any changes, waiting for any running merges, committing, and
     * closing. In this case, note that:
     *
     *
     *  * If you called prepareCommit but failed to call commit, this method will throw `IllegalStateException` and the `IndexWriter` will not be closed.
     *  * If this method throws any other exception, the `IndexWriter` will be closed, but
     * changes may have been lost.
     *
     *
     *
     * Note that this may be a costly operation, so, try to re-use a single writer instead of
     * closing and opening a new one. See [.commit] for caveats about write caching done by
     * some IO devices.
     *
     *
     * **NOTE**: You must ensure no other threads are still making changes at the same time that
     * this method is invoked.
     */
    override fun close() {
        if (config.commitOnClose) {
            shutdown()
        } else {
            rollback()
        }
    }

    // Returns true if this thread should attempt to close, or
    // false if IndexWriter is now closed; else,
    // waits until another thread finishes closing
    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    private fun shouldClose(waitForClose: Boolean): Boolean {
        while (true) {
            if (!closed) {
                if (!closing) {
                    // We get to close
                    closing = true
                    return true
                } else if (!waitForClose) {
                    return false
                } else {
                    // Another thread is presently trying to close;
                    // wait until it finishes one way (closes
                    // successfully) or another (fails to close)
                    doWait()
                }
            } else {
                return false
            }
        }
    }

    /** Returns the Directory used by this index.  */
    fun getDirectory(): Directory {
        // return the original directory the user supplied, unwrapped.
        return directoryOrig
    }

    /*fun getInfoStream(): InfoStream {
        return infoStream
    }*/

    /** Returns the analyzer used by this index.  */
    fun getAnalyzer(): Analyzer {
        ensureOpen()
        return config.analyzer
    }

    /**
     * If [SegmentInfos.getVersion] is below `newVersion` then update it to this value.
     *
     * @lucene.internal
     */
    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    fun advanceSegmentInfosVersion(newVersion: Long) {
        ensureOpen()
        if (segmentInfos.version < newVersion) {
            segmentInfos.version = newVersion
        }
        changed()
    }

    /**
     * Returns true if this index has deletions (including buffered deletions). Note that this will
     * return true if there are buffered Term/Query deletions, even if it turns out those buffered
     * deletions don't match any documents.
     */
    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    fun hasDeletions(): Boolean {
        ensureOpen()
        if (bufferedUpdatesStream.any() || docWriter.anyDeletions() || readerPool.anyDeletions()) {
            return true
        }
        for (info in segmentInfos) {
            if (info.hasDeletions()) {
                return true
            }
        }
        return false
    }

    /**
     * Adds a document to this index.
     *
     *
     * Note that if an Exception is hit (for example disk full) then the index will be consistent,
     * but this document may not have been added. Furthermore, it's possible the index will have one
     * segment in non-compound format even when using compound files (when a merge has partially
     * succeeded).
     *
     *
     * This method periodically flushes pending documents to the Directory (see [above](#flush)), and also periodically triggers segment merges in the index according
     * to the [MergePolicy] in use.
     *
     *
     * Merges temporarily consume space in the directory. The amount of space required is up to 1X
     * the size of all segments being merged, when no readers/searchers are open against the index,
     * and up to 2X the size of all segments being merged when readers/searchers are open against the
     * index (see [.forceMerge] for details). The sequence of primitive merge operations
     * performed is governed by the merge policy.
     *
     *
     * Note that each term in the document can be no longer than [.MAX_TERM_LENGTH] in bytes,
     * otherwise an IllegalArgumentException will be thrown.
     *
     *
     * Note that it's possible to create an invalid Unicode string in java if a UTF16 surrogate
     * pair is malformed. In this case, the invalid characters are silently replaced with the Unicode
     * replacement character U+FFFD.
     *
     * @return The [sequence number](#sequence_number) for this operation
     * @throws CorruptIndexException if the index is corrupt
     * @throws IOException if there is a low-level IO error
     */
    @Throws(IOException::class)
    fun addDocument(doc: Iterable<out IndexableField>): Long {
        return updateDocument(null, doc)
    }

    /**
     * Atomically adds a block of documents with sequentially assigned document IDs, such that an
     * external reader will see all or none of the documents.
     *
     *
     * **WARNING**: the index does not currently record which documents were added as a block.
     * Today this is fine, because merging will preserve a block. The order of documents within a
     * segment will be preserved, even when child documents within a block are deleted. Most search
     * features (like result grouping and block joining) require you to mark documents; when these
     * documents are deleted these search features will not work as expected. Obviously adding
     * documents to an existing block will require you the reindex the entire block.
     *
     *
     * However it's possible that in the future Lucene may merge more aggressively re-order
     * documents (for example, perhaps to obtain better index compression), in which case you may need
     * to fully re-index your documents at that time.
     *
     *
     * See [.addDocument] for details on index and IndexWriter state after an
     * Exception, and flushing/merging temporary free space requirements.
     *
     *
     * **NOTE**: tools that do offline splitting of an index (for example, IndexSplitter in
     * contrib) or re-sorting of documents (for example, IndexSorter in contrib) are not aware of
     * these atomically added documents and will likely break them up. Use such tools at your own
     * risk!
     *
     * @return The [sequence number](#sequence_number) for this operation
     * @throws CorruptIndexException if the index is corrupt
     * @throws IOException if there is a low-level IO error
     * @lucene.experimental
     */
    @Throws(IOException::class)
    fun addDocuments(docs: Iterable<Iterable<IndexableField>>): Long {
        return updateDocuments(null, docs)
    }

    /**
     * Atomically deletes documents matching the provided delTerm and adds a block of documents with
     * sequentially assigned document IDs, such that an external reader will see all or none of the
     * documents.
     *
     *
     * See [.addDocuments].
     *
     * @return The [sequence number](#sequence_number) for this operation
     * @throws CorruptIndexException if the index is corrupt
     * @throws IOException if there is a low-level IO error
     * @lucene.experimental
     */
    @Throws(IOException::class)
    fun updateDocuments(
        delTerm: Term,
        docs: Iterable<Iterable<IndexableField>>
    ): Long {
        return updateDocuments(
            if (delTerm == null) null else DocumentsWriterDeleteQueue.newNode(delTerm), docs
        )
    }

    /**
     * Similar to [.updateDocuments], but take a query instead of a term to
     * identify the documents to be updated
     *
     * @lucene.experimental
     */
    @Throws(IOException::class)
    fun updateDocuments(
        delQuery: Query,
        docs: Iterable<Iterable<IndexableField>>
    ): Long {
        return updateDocuments(
            if (delQuery == null) null else DocumentsWriterDeleteQueue.newNode(delQuery), docs
        )
    }

    @Throws(IOException::class)
    private fun updateDocuments(
        delNode: DocumentsWriterDeleteQueue.Node<*>?,
        docs: Iterable<Iterable<IndexableField>>
    ): Long {
        ensureOpen()
        var success = false
        try {
            val seqNo = maybeProcessEvents(runBlocking { docWriter.updateDocuments(docs, delNode!!) })
            success = true
            return seqNo
        } catch (tragedy: Error) {
            tragicEvent(tragedy, "updateDocuments")
            throw tragedy
        } finally {
            if (!success) {
                if (infoStream.isEnabled("IW")) {
                    infoStream.message("IW", "hit exception updating document")
                }
                maybeCloseOnTragicEvent()
            }
        }
    }

    /**
     * Expert: Atomically updates documents matching the provided term with the given doc-values
     * fields and adds a block of documents with sequentially assigned document IDs, such that an
     * external reader will see all or none of the documents.
     *
     *
     * One use of this API is to retain older versions of documents instead of replacing them. The
     * existing documents can be updated to reflect they are no longer current while atomically adding
     * new documents at the same time.
     *
     *
     * In contrast to [.updateDocuments] this method will not delete
     * documents in the index matching the given term but instead update them with the given
     * doc-values fields which can be used as a soft-delete mechanism.
     *
     *
     * See [.addDocuments] and [.updateDocuments].
     *
     * @return The [sequence number](#sequence_number) for this operation
     * @throws CorruptIndexException if the index is corrupt
     * @throws IOException if there is a low-level IO error
     * @lucene.experimental
     */
    @Throws(IOException::class)
    fun softUpdateDocuments(
        term: Term,
        docs: Iterable<Iterable<IndexableField>>,
        vararg softDeletes: Field
    ): Long {
        //requireNotNull(term) { "term must not be null" }
        require(!(softDeletes == null || softDeletes.isEmpty())) { "at least one soft delete must be present" }
        return updateDocuments(
            DocumentsWriterDeleteQueue.newNode(*buildDocValuesUpdate(term, softDeletes)), docs
        )
    }

    /**
     * Expert: attempts to delete by document ID, as long as the provided reader is a near-real-time
     * reader (from [DirectoryReader.open]). If the provided reader is an NRT
     * reader obtained from this writer, and its segment has not been merged away, then the delete
     * succeeds and this method returns a valid (&gt; 0) sequence number; else, it returns -1 and the
     * caller must then separately delete by Term or Query.
     *
     *
     * **NOTE**: this method can only delete documents visible to the currently open NRT reader.
     * If you need to delete documents indexed after opening the NRT reader you must use [ ][.deleteDocuments]).
     */
    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    @Throws(IOException::class)
    fun tryDeleteDocument(readerIn: IndexReader, docID: Int): Long {
        // NOTE: DON'T use docID inside the closure
        return tryModifyDocument(
            readerIn,
            docID
        ) { leafDocId: Int, rld: ReadersAndUpdates ->
            if (runBlocking { rld.delete(leafDocId) }) {
                if (isFullyDeleted(rld)) {
                    dropDeletedSegment(rld.info)
                    checkpoint()
                }

                // Must bump changeCount so if no other changes
                // happened, we still commit this change:
                changed()
            }
        }
    }

    /**
     * Expert: attempts to update doc values by document ID, as long as the provided reader is a
     * near-real-time reader (from [DirectoryReader.open]). If the provided reader
     * is an NRT reader obtained from this writer, and its segment has not been merged away, then the
     * update succeeds and this method returns a valid (&gt; 0) sequence number; else, it returns -1
     * and the caller must then either retry the update and resolve the document again. If a doc
     * values fields data is `null` the existing value is removed from all documents
     * matching the term. This can be used to un-delete a soft-deleted document since this method will
     * apply the field update even if the document is marked as deleted.
     *
     *
     * **NOTE**: this method can only updates documents visible to the currently open NRT
     * reader. If you need to update documents indexed after opening the NRT reader you must use
     * [.updateDocValues].
     */
    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    @Throws(IOException::class)
    fun tryUpdateDocValue(
        readerIn: IndexReader,
        docID: Int,
        vararg fields: Field
    ): Long {
        // NOTE: DON'T use docID inside the closure
        val dvUpdates: Array<DocValuesUpdate> = buildDocValuesUpdate(null, fields)
        return tryModifyDocument(
            readerIn,
            docID
        ) { leafDocId: Int, rld: ReadersAndUpdates ->
            val nextGen: Long = bufferedUpdatesStream.getNextGen()
            try {
                val fieldUpdatesMap: MutableMap<String, DocValuesFieldUpdates> = HashMap()
                for (update in dvUpdates) {
                    val docValuesFieldUpdates: DocValuesFieldUpdates =
                        fieldUpdatesMap.computeIfAbsent(
                            update.field
                        ) { k: String ->
                            when (update.type) {
                                DocValuesType.NUMERIC -> return@computeIfAbsent NumericDocValuesFieldUpdates(
                                    nextGen, k, rld.info.info.maxDoc()
                                )

                                DocValuesType.BINARY -> return@computeIfAbsent BinaryDocValuesFieldUpdates(
                                    nextGen, k, rld.info.info.maxDoc()
                                )

                                DocValuesType.NONE, DocValuesType.SORTED, DocValuesType.SORTED_NUMERIC, DocValuesType.SORTED_SET -> throw AssertionError(
                                    "type: " + update.type + " is not supported"
                                )

                                else -> throw AssertionError("type: " + update.type + " is not supported")
                            }
                        }!!
                    if (update.hasValue()) {
                        when (update.type) {
                            DocValuesType.NUMERIC -> docValuesFieldUpdates.add(
                                leafDocId,
                                (update as NumericDocValuesUpdate).getValue()
                            )

                            DocValuesType.BINARY -> docValuesFieldUpdates.add(
                                leafDocId,
                                (update as BinaryDocValuesUpdate).getValue()!!
                            )

                            DocValuesType.NONE, DocValuesType.SORTED, DocValuesType.SORTED_SET, DocValuesType.SORTED_NUMERIC -> throw AssertionError(
                                "type: " + update.type + " is not supported"
                            )

                            else -> throw AssertionError("type: " + update.type + " is not supported")
                        }
                    } else {
                        docValuesFieldUpdates.reset(leafDocId)
                    }
                }
                for (updates in fieldUpdatesMap.values) {
                    updates.finish()
                    rld.addDVUpdate(updates)
                }
            } finally {
                bufferedUpdatesStream.finishedSegment(nextGen)
            }
            // Must bump changeCount so if no other changes
            // happened, we still commit this change:
            changed()
        }
    }

    private fun interface DocModifier {
        @Throws(IOException::class)
        fun run(docId: Int, readersAndUpdates: ReadersAndUpdates)
    }

    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    @Throws(IOException::class)
    private fun tryModifyDocument(
        readerIn: IndexReader,
        docID: Int,
        toApply: DocModifier
    ): Long {
        var docID = docID
        val reader: LeafReader
        if (readerIn is LeafReader) {
            // Reader is already atomic: use the incoming docID:
            reader = readerIn
        } else {
            // Composite reader: lookup sub-reader and re-base docID:
            val leaves: MutableList<LeafReaderContext> = readerIn.leaves()
            val subIndex: Int = ReaderUtil.subIndex(docID, leaves)
            reader = leaves[subIndex].reader()
            docID -= leaves[subIndex].docBase
            assert(docID >= 0)
            assert(docID < reader.maxDoc())
        }

        require(reader is SegmentReader) { "the reader must be a SegmentReader or composite reader containing only SegmentReaders" }

        val info: SegmentCommitInfo = reader.originalSegmentInfo

        // TODO: this is a slow linear search, but, number of
        // segments should be contained unless something is
        // seriously wrong w/ the index, so it should be a minor
        // cost:
        if (segmentInfos.indexOf(info) != -1) {
            val rld: ReadersAndUpdates? = getPooledInstance(info, false)
            if (rld != null) {

                // TODO synchronized is not supported in KMP, need to think what to do here
                //synchronized(bufferedUpdatesStream) {
                toApply.run(docID, rld)
                return docWriter.nextSequenceNumber
                //}
            }
        }
        return -1
    }

    /** Drops a segment that has 100% deleted documents.  */
    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    @Throws(IOException::class)
    private fun dropDeletedSegment(info: SegmentCommitInfo) {
        // If a merge has already registered for this
        // segment, we leave it in the readerPool; the
        // merge will skip merging it and will then drop
        // it once it's done:
        if (!mergingSegments.contains(info)) {
            // it's possible that we invoke this method more than once for the same SCI
            // we must only remove the docs once!
            var dropPendingDocs: Boolean = segmentInfos.remove(info)
            try {
                // this is sneaky - we might hit an exception while dropping a reader but then we have
                // already
                // removed the segment for the segmentInfo and we lost the pendingDocs update due to that.
                // therefore we execute the adjustPendingNumDocs in a finally block to account for that.
                dropPendingDocs = dropPendingDocs or runBlocking { readerPool.drop(info) }
            } finally {
                if (dropPendingDocs) {
                    adjustPendingNumDocs(-info.info.maxDoc().toLong())
                }
            }
        }
    }

    /**
     * Deletes the document(s) containing any of the terms. All given deletes are applied and flushed
     * atomically at the same time.
     *
     * @return The [sequence number](#sequence_number) for this operation
     * @param terms array of terms to identify the documents to be deleted
     * @throws CorruptIndexException if the index is corrupt
     * @throws IOException if there is a low-level IO error
     */
    @Throws(IOException::class)
    fun deleteDocuments(vararg terms: Term): Long {
        ensureOpen()
        try {
            return maybeProcessEvents(docWriter.deleteTerms(*terms))
        } catch (tragedy: Error) {
            tragicEvent(tragedy, "deleteDocuments(Term..)")
            throw tragedy
        }
    }

    /**
     * Deletes the document(s) matching any of the provided queries. All given deletes are applied and
     * flushed atomically at the same time.
     *
     * @return The [sequence number](#sequence_number) for this operation
     * @param queries array of queries to identify the documents to be deleted
     * @throws CorruptIndexException if the index is corrupt
     * @throws IOException if there is a low-level IO error
     */
    @Throws(IOException::class)
    fun deleteDocuments(vararg queries: Query): Long {
        ensureOpen()

        // LUCENE-6379: Specialize MatchAllDocsQuery
        for (query in queries) {
            if (query::class == MatchAllDocsQuery::class) {
                return deleteAll()
            }
        }

        try {
            return maybeProcessEvents(docWriter.deleteQueries(*queries))
        } catch (tragedy: Error) {
            tragicEvent(tragedy, "deleteDocuments(Query..)")
            throw tragedy
        }
    }

    /**
     * Updates a document by first deleting the document(s) containing `term` and then
     * adding the new document. The delete and then add are atomic as seen by a reader on the same
     * index (flush may happen only after the add).
     *
     * @return The [sequence number](#sequence_number) for this operation
     * @param term the term to identify the document(s) to be deleted
     * @param doc the document to be added
     * @throws CorruptIndexException if the index is corrupt
     * @throws IOException if there is a low-level IO error
     */
    @Throws(IOException::class)
    fun updateDocument(
        term: Term?,
        doc: Iterable<IndexableField>
    ): Long {
        return updateDocuments(
            if (term == null) null else DocumentsWriterDeleteQueue.newNode(term),
            mutableListOf(doc)
        )
    }

    /**
     * Expert: Updates a document by first updating the document(s) containing `term` with
     * the given doc-values fields and then adding the new document. The doc-values update and the
     * subsequent addition are atomic, as seen by a reader on the same index (a flush may happen only
     * after the addition).
     *
     *
     * One use of this API is to retain older versions of documents instead of replacing them. The
     * existing documents can be updated to reflect they are no longer current, while atomically
     * adding new documents at the same time.
     *
     *
     * In contrast to [.updateDocument] this method will not delete documents
     * in the index matching the given term but instead update them with the given doc-values fields
     * which can be used as a soft-delete mechanism.
     *
     *
     * See [.addDocuments] and [.updateDocuments].
     *
     * @return The [sequence number](#sequence_number) for this operation
     * @throws CorruptIndexException if the index is corrupt
     * @throws IOException if there is a low-level IO error
     * @lucene.experimental
     */
    @Throws(IOException::class)
    fun softUpdateDocument(
        term: Term,
        doc: Iterable<out IndexableField>,
        vararg softDeletes: Field
    ): Long {
        //requireNotNull(term) { "term must not be null" }
        require(!(softDeletes == null || softDeletes.isEmpty())) { "at least one soft delete must be present" }
        return updateDocuments(
            DocumentsWriterDeleteQueue.newNode(*buildDocValuesUpdate(term, softDeletes)),
            mutableListOf(doc)
        )
    }

    /**
     * Updates a document's [NumericDocValues] for `field` to the given `value
    ` * . You can only update fields that already exist in the index, not add new fields through
     * this method. You can only update fields that were indexed with doc values only.
     *
     * @param term the term to identify the document(s) to be updated
     * @param field field name of the [NumericDocValues] field
     * @param value new value for the field
     * @return The [sequence number](#sequence_number) for this operation
     * @throws CorruptIndexException if the index is corrupt
     * @throws IOException if there is a low-level IO error
     */
    @Throws(IOException::class)
    fun updateNumericDocValue(term: Term, field: String, value: Long): Long {
        ensureOpen()
        globalFieldNumberMap.verifyOrCreateDvOnlyField(field, DocValuesType.NUMERIC, true)
        require(!config.indexSortFields.contains(field)) {
            ("cannot update docvalues field involved in the index sort, field="
                    + field
                    + ", sort="
                    + config.indexSort)
        }
        try {
            return maybeProcessEvents(
                docWriter.updateDocValues(
                    NumericDocValuesUpdate(
                        term,
                        field,
                        value
                    )
                )
            )
        } catch (tragedy: Error) {
            tragicEvent(tragedy, "updateNumericDocValue")
            throw tragedy
        }
    }

    /**
     * Updates a document's [BinaryDocValues] for `field` to the given `value
    ` * . You can only update fields that already exist in the index, not add new fields through
     * this method. You can only update fields that were indexed only with doc values.
     *
     *
     * **NOTE:** this method currently replaces the existing value of all affected documents
     * with the new value.
     *
     * @param term the term to identify the document(s) to be updated
     * @param field field name of the [BinaryDocValues] field
     * @param value new value for the field
     * @return The [sequence number](#sequence_number) for this operation
     * @throws CorruptIndexException if the index is corrupt
     * @throws IOException if there is a low-level IO error
     */
    @Throws(IOException::class)
    fun updateBinaryDocValue(
        term: Term,
        field: String,
        value: BytesRef
    ): Long {
        ensureOpen()
        //requireNotNull(value) { "cannot update a field to a null value: $field" }
        globalFieldNumberMap.verifyOrCreateDvOnlyField(field, DocValuesType.BINARY, true)
        try {
            return maybeProcessEvents(
                docWriter.updateDocValues(
                    BinaryDocValuesUpdate(
                        term,
                        field,
                        value
                    )
                )
            )
        } catch (tragedy: Error) {
            tragicEvent(tragedy, "updateBinaryDocValue")
            throw tragedy
        }
    }

    /**
     * Updates documents' DocValues fields to the given values. Each field update is applied to the
     * set of documents that are associated with the [Term] to the same value. All updates are
     * atomically applied and flushed together. If a doc values fields data is `null` the
     * existing value is removed from all documents matching the term.
     *
     * @param updates the updates to apply
     * @return The [sequence number](#sequence_number) for this operation
     * @throws CorruptIndexException if the index is corrupt
     * @throws IOException if there is a low-level IO error
     */
    @Throws(IOException::class)
    fun updateDocValues(term: Term, vararg updates: Field): Long {
        ensureOpen()
        val dvUpdates: Array<DocValuesUpdate> = buildDocValuesUpdate(term, updates)
        try {
            return maybeProcessEvents(docWriter.updateDocValues(*dvUpdates))
        } catch (tragedy: Error) {
            tragicEvent(tragedy, "updateDocValues")
            throw tragedy
        }
    }

    private fun buildDocValuesUpdate(
        term: Term?,
        updates: Array<out Field>
    ): Array<DocValuesUpdate> {
        val dvUpdates: Array<DocValuesUpdate> =
            kotlin.arrayOfNulls<DocValuesUpdate>(updates.size) as Array<DocValuesUpdate>
        for (i in updates.indices) {
            val f: Field = updates[i]
            val dvType: DocValuesType = f.fieldType().docValuesType()
            if (dvType == null) {
                throw NullPointerException(
                    "DocValuesType must not be null (field: \"" + f.name() + "\")"
                )
            }
            require(dvType != DocValuesType.NONE) { "can only update NUMERIC or BINARY fields! field=" + f.name() }
            // if this field doesn't exists we try to add it.
            // if it exists and the DV type doesn't match or it is not DV only field,
            // we will get an error.
            globalFieldNumberMap.verifyOrCreateDvOnlyField(f.name(), dvType, false)
            require(!config.indexSortFields.contains(f.name())) {
                ("cannot update docvalues field involved in the index sort, field="
                        + f.name()
                        + ", sort="
                        + config.indexSort)
            }

            when (dvType) {
                DocValuesType.NUMERIC -> {
                    val value = f.numericValue() as Long
                    dvUpdates[i] = NumericDocValuesUpdate(term, f.name(), value)
                }

                DocValuesType.BINARY -> dvUpdates[i] =
                    BinaryDocValuesUpdate(term, f.name(), f.binaryValue()!!)

                DocValuesType.NONE, DocValuesType.SORTED, DocValuesType.SORTED_NUMERIC, DocValuesType.SORTED_SET -> throw IllegalArgumentException(
                    "can only update NUMERIC or BINARY fields: field=" + f.name() + ", type=" + dvType
                )

                else -> throw IllegalArgumentException(
                    "can only update NUMERIC or BINARY fields: field=" + f.name() + ", type=" + dvType
                )
            }
        }
        return dvUpdates
    }

    /**
     * Return an unmodifiable set of all field names as visible from this IndexWriter, across all
     * segments of the index.
     *
     * @lucene.experimental
     */
    fun getFieldNames(): MutableSet<String> {
        // FieldNumbers#getFieldNames() returns an unmodifiableSet
        return globalFieldNumberMap.fieldNames
    }

    // for test purpose
    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    fun getSegmentCount(): Int {
        return segmentInfos.size()
    }

    // for test purpose
    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    fun getNumBufferedDocuments(): Int {
        return docWriter.numDocs
    }

    // for test purpose
    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    fun maxDoc(i: Int): Int {
        return if (i >= 0 && i < segmentInfos.size()) {
            segmentInfos.info(i).info.maxDoc()
        } else {
            -1
        }
    }

    // for test purpose
    fun getFlushCount(): Int {
        return flushCount.load()
    }

    // for test purpose
    fun getFlushDeletesCount(): Int {
        return flushDeletesCount.load()
    }

    private fun newSegmentName(): String {
        // Cannot synchronize on IndexWriter because that causes
        // deadlock

        // TODO Synchronized is not supported in KMP, need to think what to do here
        //synchronized(segmentInfos) {
        // Important to increment changeCount so that the
        // segmentInfos is written on close.  Otherwise we
        // could close, re-open and re-return the same segment
        // name that was previously returned which can cause
        // problems at least with ConcurrentMergeScheduler.
        changeCount.incrementAndFetch()
        segmentInfos.changed()
        return "_" + (segmentInfos.counter++).toString(Character.MAX_RADIX.coerceIn(2, 36))
        //}
    }

    /** If enabled, information about merges will be printed to this.  */
    override lateinit var infoStream: InfoStream

    /**
     * Just like [.forceMerge], except you can specify whether the call should block until
     * all merging completes. This is only meaningful with a [MergeScheduler] that is able to
     * run merges in background threads.
     */
    /**
     * Forces merge policy to merge segments until there are `<= maxNumSegments`. The actual
     * merges to be executed are determined by the [MergePolicy].
     *
     *
     * This is a horribly costly operation, especially when you pass a small `maxNumSegments`; usually you should only call this if the index is static (will no longer be
     * changed).
     *
     *
     * Note that this requires free space that is proportional to the size of the index in your
     * Directory: 2X if you are not using compound file format, and 3X if you are. For example, if
     * your index size is 10 MB then you need an additional 20 MB free for this to complete (30 MB if
     * you're using compound file format). This is also affected by the [Codec] that is used to
     * execute the merge, and may result in even a bigger index. Also, it's best to call [ ][.commit] afterwards, to allow IndexWriter to free up disk space.
     *
     *
     * If some but not all readers re-open while merging is underway, this will cause `> 2X`
     * temporary space to be consumed as those new readers will then hold open the temporary segments
     * at that time. It is best not to re-open readers while merging is running.
     *
     *
     * The actual temporary usage could be much less than these figures (it depends on many
     * factors).
     *
     *
     * In general, once this completes, the total size of the index will be less than the size of
     * the starting index. It could be quite a bit smaller (if there were many pending deletes) or
     * just slightly smaller.
     *
     *
     * If an Exception is hit, for example due to disk full, the index will not be corrupted and no
     * documents will be lost. However, it may have been partially merged (some segments were merged
     * but not all), and it's possible that one of the segments in the index will be in non-compound
     * format even when using compound file format. This will occur when the Exception is hit during
     * conversion of the segment into compound format.
     *
     *
     * This call will merge those segments present in the index when the call started. If other
     * threads are still adding documents and flushing segments, those newly created segments will not
     * be merged unless you call forceMerge again.
     *
     * @param maxNumSegments maximum number of segments left in the index after merging finishes
     * @throws CorruptIndexException if the index is corrupt
     * @throws IOException if there is a low-level IO error
     * @see MergePolicy.findMerges
     */
    @JvmOverloads
    @Throws(IOException::class)
    fun forceMerge(maxNumSegments: Int, doWait: Boolean = true) {
        ensureOpen()

        require(maxNumSegments >= 1) { "maxNumSegments must be >= 1; got $maxNumSegments" }

        if (infoStream.isEnabled("IW")) {
            infoStream.message("IW", "forceMerge: index now " + segString())
            infoStream.message("IW", "now flush at forceMerge")
        }
        flush(triggerMerge = true, applyAllDeletes = true)

        // TODO Synchronized is not supported in KMP, need to think what to do here
        //synchronized(this) {
        resetMergeExceptions()
        segmentsToMerge.clear()
        for (info in segmentInfos) {
            //checkNotNull(info)
            segmentsToMerge[info] = true
        }
        mergeMaxNumSegments = maxNumSegments

        // Now mark all pending & running merges for forced
        // merge:
        for (merge in pendingMerges) {
            merge.maxNumSegments = maxNumSegments
            if (merge.info != null) {
                // this can be null since we register the merge under lock before we then do the actual
                // merge and
                // set the merge.info in _mergeInit
                segmentsToMerge[merge.info!!] = true
            }
        }
        for (merge in runningMerges) {
            merge.maxNumSegments = maxNumSegments
            if (merge.info != null) {
                // this can be null since we put the merge on runningMerges before we do the actual merge
                // and
                // set the merge.info in _mergeInit
                segmentsToMerge[merge.info!!] = true
            }
        }
        //} // end synchronized(this)

        maybeMerge(config.mergePolicy, MergeTrigger.EXPLICIT, maxNumSegments)

        if (doWait) {

            // TODO synchronized is not supported in KMP, need to think what to do here
            //synchronized(this) {
            while (true) {
                if (tragedy.load() != null) {
                    throw IllegalStateException(
                        "this writer hit an unrecoverable error; cannot complete forceMerge",
                        tragedy.load()
                    )
                }

                if (mergeExceptions.isNotEmpty()) {
                    // Forward any exceptions in background merge
                    // threads to the current thread:
                    val size = mergeExceptions.size
                    for (i in 0..<size) {
                        val merge: MergePolicy.OneMerge = mergeExceptions[i]
                        if (merge.maxNumSegments != UNBOUNDED_MAX_MERGE_SEGMENTS) {
                            throw IOException(
                                "background merge hit exception: " + merge.segString(), merge.exception
                            )
                        }
                    }
                }

                if (maxNumSegmentsMergesPending()) {
                    testPoint("forceMergeBeforeWait")
                    doWait()
                } else {
                    break
                }
            }
            //} // end synchronized(this)

            // If close is called while we are still
            // running, throw an exception so the calling
            // thread will know merging did not
            // complete
            ensureOpen()
        }
        // NOTE: in the ConcurrentMergeScheduler case, when
        // doWait is false, we can return immediately while
        // background threads accomplish the merging
    }

    /** Returns true if any merges in pendingMerges or runningMerges are maxNumSegments merges.  */
    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    private fun maxNumSegmentsMergesPending(): Boolean {
        for (merge in pendingMerges) {
            if (merge.maxNumSegments != UNBOUNDED_MAX_MERGE_SEGMENTS) return true
        }

        for (merge in runningMerges) {
            if (merge.maxNumSegments != UNBOUNDED_MAX_MERGE_SEGMENTS) return true
        }

        return false
    }

    /**
     * Just like [.forceMergeDeletes], except you can specify whether the call should block
     * until the operation completes. This is only meaningful with a [MergeScheduler] that is
     * able to run merges in background threads.
     */
    /**
     * Forces merging of all segments that have deleted documents. The actual merges to be executed
     * are determined by the [MergePolicy]. For example, the default [TieredMergePolicy]
     * will only pick a segment if the percentage of deleted docs is over 10%.
     *
     *
     * This is often a horribly costly operation; rarely is it warranted.
     *
     *
     * To see how many deletions you have pending in your index, call [ ][IndexReader.numDeletedDocs].
     *
     *
     * **NOTE**: this method first flushes a new segment (if there are indexed documents), and
     * applies all buffered deletes.
     */
    @OptIn(ExperimentalAtomicApi::class)
    @JvmOverloads
    @Throws(IOException::class)
    fun forceMergeDeletes(doWait: Boolean = true) {
        ensureOpen()

        flush(triggerMerge = true, applyAllDeletes = true)

        if (infoStream.isEnabled("IW")) {
            infoStream.message("IW", "forceMergeDeletes: index now " + segString())
        }

        val mergePolicy: MergePolicy = config.mergePolicy
        val cachingMergeContext = CachingMergeContext(this)
        var newMergesFound: Boolean

        // TODO synchronized is not supported in KMP, need to think what to do here
        //synchronized(this) {
        val spec = mergePolicy.findForcedDeletesMerges(segmentInfos, cachingMergeContext)
        newMergesFound = spec != null
        if (newMergesFound) {
            val numMerges: Int = spec!!.merges.size
            for (i in 0..<numMerges) registerMerge(spec.merges[i])
        }
        //}

        runBlocking { mergeScheduler.merge(mergeSource, MergeTrigger.EXPLICIT) }

        if (spec != null && doWait) {
            val numMerges: Int = spec.merges.size

            // TODO Synchronized is not supported in KMP, need to think what to do here
            //synchronized(this) {
            var running = true
            while (running) {
                if (tragedy.load() != null) {
                    throw IllegalStateException(
                        "this writer hit an unrecoverable error; cannot complete forceMergeDeletes",
                        tragedy.load()
                    )
                }

                // Check each merge that MergePolicy asked us to
                // do, to see if any of them are still running and
                // if any of them have hit an exception.
                running = false
                for (i in 0..<numMerges) {
                    val merge: MergePolicy.OneMerge = spec.merges[i]
                    if (pendingMerges.contains(merge) || runningMerges.contains(merge)) {
                        running = true
                    }
                    val t: Throwable? = merge.exception
                    if (t != null) {
                        throw IOException("background merge hit exception: " + merge.segString(), t)
                    }
                }

                // If any of our merges are still running, wait:
                if (running) doWait()
            }
            //}
        }

        // NOTE: in the ConcurrentMergeScheduler case, when
        // doWait is false, we can return immediately while
        // background threads accomplish the merging
    }

    /**
     * Expert: asks the mergePolicy whether any merges are necessary now and if so, runs the requested
     * merges and then iterate (test again if merges are needed) until no more merges are returned by
     * the mergePolicy.
     *
     *
     * Explicit calls to maybeMerge() are usually not necessary. The most common case is when merge
     * policy parameters have changed.
     *
     *
     * This method will call the [MergePolicy] with [MergeTrigger.EXPLICIT].
     */
    @Throws(IOException::class)
    fun maybeMerge() {
        maybeMerge(config.mergePolicy, MergeTrigger.EXPLICIT, UNBOUNDED_MAX_MERGE_SEGMENTS)
    }

    @Throws(IOException::class)
    private fun maybeMerge(
        mergePolicy: MergePolicy,
        trigger: MergeTrigger,
        maxNumSegments: Int
    ) {
        ensureOpen(false)
        if (updatePendingMerges(mergePolicy, trigger, maxNumSegments) != null) {
            executeMerge(trigger)
        }
    }

    @Throws(IOException::class)
    fun executeMerge(trigger: MergeTrigger) {
        runBlocking { mergeScheduler.merge(mergeSource, trigger) }
    }

    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    @Throws(IOException::class)
    private fun updatePendingMerges(
        mergePolicy: MergePolicy,
        trigger: MergeTrigger,
        maxNumSegments: Int
    ): MergePolicy.MergeSpecification? {
        // In case infoStream was disabled on init, but then enabled at some
        // point, try again to log the config here:

        messageState()

        assert(maxNumSegments == UNBOUNDED_MAX_MERGE_SEGMENTS || maxNumSegments > 0)
        //checkNotNull(trigger)
        if (!merges.areEnabled()) {
            return null
        }

        // Do not start new merges if disaster struck
        if (tragedy.load() != null) {
            return null
        }

        val spec: MergePolicy.MergeSpecification?
        val cachingMergeContext = CachingMergeContext(this)
        if (maxNumSegments != UNBOUNDED_MAX_MERGE_SEGMENTS) {
            assert(
                trigger == MergeTrigger.EXPLICIT || trigger == MergeTrigger.MERGE_FINISHED
            ) {
                ("Expected EXPLICT or MERGE_FINISHED as trigger even with maxNumSegments set but was: "
                        + trigger.name)
            }

            spec = mergePolicy.findForcedMerges(
                segmentInfos,
                maxNumSegments,
                segmentsToMerge.toMutableMap(),
                cachingMergeContext
            )
            if (spec != null) {
                val numMerges: Int = spec.merges.size
                for (i in 0..<numMerges) {
                    val merge: MergePolicy.OneMerge = spec.merges[i]
                    merge.maxNumSegments = maxNumSegments
                }
            }
        } else {
            spec = when (trigger) {
                MergeTrigger.GET_READER, MergeTrigger.COMMIT -> mergePolicy.findFullFlushMerges(
                    trigger,
                    segmentInfos,
                    cachingMergeContext
                )

                MergeTrigger.ADD_INDEXES -> throw IllegalStateException(
                    "Merges with ADD_INDEXES trigger should be "
                            + "called from within the addIndexes() API flow"
                )

                MergeTrigger.EXPLICIT, MergeTrigger.FULL_FLUSH, MergeTrigger.MERGE_FINISHED, MergeTrigger.SEGMENT_FLUSH, MergeTrigger.CLOSING -> mergePolicy.findMerges(
                    trigger,
                    segmentInfos,
                    cachingMergeContext
                )

                else -> mergePolicy.findMerges(trigger, segmentInfos, cachingMergeContext)
            }
        }
        if (spec != null) {
            val numMerges: Int = spec.merges.size
            for (i in 0..<numMerges) {
                registerMerge(spec.merges[i])
            }
        }
        return spec
    }

    /**
     * Expert: to be used by a [MergePolicy] to avoid selecting merges for segments already
     * being merged. The returned collection is not cloned, and thus is only safe to access if you
     * hold IndexWriter's lock (which you do when IndexWriter invokes the MergePolicy).
     *
     *
     * The Set is unmodifiable.
     */
    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    /*fun getMergingSegments(): MutableSet<SegmentCommitInfo> {
        return mergingSegments.toMutableSet()
    }*/

    /**
     * Expert: the [MergeScheduler] calls this method to retrieve the next merge requested by
     * the MergePolicy
     *
     * @lucene.experimental
     */
    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    private fun getNextMerge(): MergePolicy.OneMerge? {
        if (tragedy.load() != null) {
            throw IllegalStateException(
                "this writer hit an unrecoverable error; cannot merge", tragedy.load()
            )
        }
        if (pendingMerges.isEmpty()) {
            return null
        } else {
            // Advance the merge from pending to running
            val merge: MergePolicy.OneMerge = pendingMerges.removeFirst()
            runningMerges.add(merge)
            return merge
        }
    }

    /**
     * Expert: returns true if there are merges waiting to be scheduled.
     *
     * @lucene.experimental
     */
    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    fun hasPendingMerges(): Boolean {
        if (tragedy.load() != null) {
            throw IllegalStateException(
                "this writer hit an unrecoverable error; cannot merge", tragedy.load()
            )
        }
        return pendingMerges.isNotEmpty()
    }

    /**
     * Close the `IndexWriter` without committing any changes that have occurred since the
     * last commit (or since it was opened, if commit hasn't been called). This removes any temporary
     * files that had been created, after which the state of the index will be the same as it was when
     * commit() was last called or when this writer was first opened. This also clears a previous call
     * to [.prepareCommit].
     *
     * @throws IOException if there is a low-level IO error
     */
    @Throws(IOException::class)
    override fun rollback() {
        // don't call ensureOpen here: this acts like "close()" in closeable.

        // Ensure that only one thread actually gets to do the
        // closing, and make sure no commit is also in progress:

        if (shouldClose(true)) {
            rollbackInternal()
        }
    }

    @Throws(IOException::class)
    private fun rollbackInternal() {
        // Make sure no commit is running, else e.g. we can close while another thread is still
        // fsync'ing:

        // TODO Synchronized is not supported in KMP, need to think what to do here
        //synchronized(commitLock) {
        rollbackInternalNoCommit()
        assert(
            pendingNumDocs.load() == segmentInfos.totalMaxDoc().toLong()
        ) {
            ("pendingNumDocs "
                    + pendingNumDocs.load()
                    + " != "
                    + segmentInfos.totalMaxDoc()
                    + " totalMaxDoc")
        }
        //}
    }

    @Throws(IOException::class)
    private fun rollbackInternalNoCommit() {
        if (infoStream.isEnabled("IW")) {
            infoStream.message("IW", "rollback")
        }

        val cleanupAndNotify =
            AutoCloseable {

                // TODO Thread is not supported in KMP, need to think what to do here
                //assert(java.lang.Thread.holdsLock(this))
                writeLock = null
                closed = true
                closing = false
                // So any "concurrently closing" threads wake up and see that the close has now
                // completed:

                // TODO notifyAll() is not supported in KMP, need to think what to do here
                //(this as java.lang.Object).notifyAll()
            }

        try {

            // TODO Synchronized is not supported in KMP, need to think what to do here
            //synchronized(this) {
            // must be synced otherwise register merge might throw and exception if merges
            // changes concurrently, abortMerges is synced as well
            abortMerges() // this disables merges forever since we are closing and can't reenable them
            assert(
                mergingSegments.isEmpty()
            ) { "we aborted all merges but still have merging segments: $mergingSegments" }
            //}

            if (infoStream.isEnabled("IW")) {
                infoStream.message("IW", "rollback: done finish merges")
            }

            // Must pre-close in case it increments changeCount so that we can then
            // set it to false before calling rollbackInternal
            mergeScheduler.close()

            docWriter.close() // mark it as closed first to prevent subsequent indexing actions/flushes

            // TODO Thread is not supported in KMP, need to think what to do here
            //assert(!java.lang.Thread.holdsLock(this)) { "IndexWriter lock should never be hold when aborting" }
            runBlocking { docWriter.abort() } // don't sync on IW here
            docWriter.flushControl.waitForFlush() // wait for all concurrently running flushes
            publishFlushedSegments(
                true
            ) // empty the flush ticket queue otherwise we might not have cleaned up all
            // resources
            eventQueue.close()

            // TODO Synchronized is not supported in KMP, need to think what to do here
            //synchronized(this) {
            if (pendingCommit != null) {
                pendingCommit!!.rollbackCommit(directory)
                try {
                    deleter.decRef(pendingCommit!!)
                } finally {
                    pendingCommit = null

                    // TODO notifyAll() is not supported in KMP, need to think what to do here
                    //(this as java.lang.Object).notifyAll()
                }
            }
            val totalMaxDoc: Int = segmentInfos.totalMaxDoc()
            // Keep the same segmentInfos instance but replace all
            // of its SegmentInfo instances so IFD below will remove
            // any segments we flushed since the last commit:
            segmentInfos.rollbackSegmentInfos(rollbackSegments!!)
            val rollbackMaxDoc: Int = segmentInfos.totalMaxDoc()
            // now we need to adjust this back to the rolled back SI but don't set it to the absolute
            // value
            // otherwise we might hide internal bugsf
            adjustPendingNumDocs(-(totalMaxDoc - rollbackMaxDoc).toLong())
            if (infoStream.isEnabled("IW")) {
                infoStream.message("IW", "rollback: infos=" + segString(segmentInfos))
            }

            testPoint("rollback before checkpoint")

            // Ask deleter to locate unreferenced files & remove
            // them ... only when we are not experiencing a tragedy, else
            // these methods throw ACE:
            if (tragedy.load() == null) {
                deleter.checkpoint(segmentInfos, false)
                deleter.refresh()
                deleter.close()
            }

            lastCommitChangeCount = changeCount.load()
            // Don't bother saving any changes in our segmentInfos
            readerPool.close()
            // Must set closed while inside same sync block where we call deleter.refresh, else
            // concurrent threads may try to sneak a flush in,
            // after we leave this sync block and before we enter the sync block in the finally clause
            // below that sets closed:
            closed = true
            IOUtils.close(writeLock, cleanupAndNotify)
            //} // end synchronized(this)
        } catch (throwable: Throwable) {
            try {
                // Must not hold IW's lock while closing
                // mergeScheduler: this can lead to deadlock,
                // e.g. TestIW.testThreadInterruptDeadlock
                IOUtils.closeWhileHandlingException(
                    mergeScheduler,
                    AutoCloseable {

                        // TODO synchronized is not supported in KMP, need to think what to do here
                        //synchronized(this) {
                        // we tried to be nice about it: do the minimum
                        // don't leak a segments_N file if there is a pending commit
                        if (pendingCommit != null) {
                            try {
                                pendingCommit!!.rollbackCommit(directory)
                                deleter.decRef(pendingCommit!!)
                            } catch (t: Throwable) {
                                throwable.addSuppressed(t)
                            }
                            pendingCommit = null
                        }

                        // close all the closeables we can (but important is readerPool and writeLock to
                        // prevent leaks)
                        IOUtils.closeWhileHandlingException(
                            readerPool, deleter, writeLock, cleanupAndNotify
                        )
                        //}
                    })
            } catch (t: Throwable) {
                throwable.addSuppressed(t)
            } finally {
                if (throwable is Error) {
                    try {
                        tragicEvent(throwable, "rollbackInternal")
                    } catch (t1: Throwable) {
                        throwable.addSuppressed(t1)
                    }
                }
            }
            throw throwable
        }
    }

    /**
     * Delete all documents in the index.
     *
     *
     * This method will drop all buffered documents and will remove all segments from the index.
     * This change will not be visible until a [.commit] has been called. This method can be
     * rolled back using [.rollback].
     *
     *
     * NOTE: this method is much faster than using deleteDocuments( new MatchAllDocsQuery() ). Yet,
     * this method also has different semantics compared to [.deleteDocuments] since
     * internal data-structures are cleared as well as all segment information is forcefully dropped
     * anti-viral semantics like omitting norms are reset or doc value types are cleared. Essentially
     * a call to [.deleteAll] is equivalent to creating a new [IndexWriter] with [ ][OpenMode.CREATE] which a delete query only marks documents as deleted.
     *
     *
     * NOTE: this method will forcefully abort all merges in progress. If other threads are running
     * [.forceMerge], [.addIndexes] or [.forceMergeDeletes] methods,
     * they may receive [MergePolicy.MergeAbortedException]s.
     *
     * @return The [sequence number](#sequence_number) for this operation
     */
    @Throws(IOException::class)
    fun deleteAll(): Long {
        ensureOpen()
        // Remove any buffered docs
        var success = false
        /* hold the full flush lock to prevent concurrency commits / NRT reopens to
         * get in our way and do unnecessary work. -- if we don't lock this here we might
         * get in trouble if */
        /*
     * We first abort and trash everything we have in-memory
     * and keep the thread-states locked, the lockAndAbortAll operation
     * also guarantees "point in time semantics" ie. the checkpoint that we need in terms
     * of logical happens-before relationship in the DW. So we do
     * abort all in memory structures
     * We also drop global field numbering before during abort to make
     * sure it's just like a fresh index.
     */
        try {

            // TODO Synchronized is not supported in KMP, need to think what to do here
            //synchronized(fullFlushLock) {

            val finalizer: AutoCloseable = runBlocking { docWriter.lockAndAbortAll() }


            processEvents(false)

            // TODO Synchronized is not supported in KMP, need to think what to do here
            //synchronized(this) {
            try {

                // Abort any running merges
                try {
                    abortMerges()
                    assert(!merges.areEnabled()) { "merges should be disabled - who enabled them" }
                    assert(mergingSegments.isEmpty()) { "found merging segments but merges are disabled: $mergingSegments" }
                } finally {
                    // abortMerges disables all merges and we need to re-enable them here to make sure
                    // IW can function properly. An exception in abortMerges() might be fatal for IW but
                    // just to be sure
                    // lets re-enable merges anyway.
                    merges.enable()
                }
                adjustPendingNumDocs(-segmentInfos.totalMaxDoc().toLong())
                // Remove all segments
                segmentInfos.clear()
                // Ask deleter to locate unreferenced files & remove them:
                deleter.checkpoint(segmentInfos, false)

                /* don't refresh the deleter here since there might
                 * be concurrent indexing requests coming in opening
                 * files on the directory after we called DW#abort()
                 * if we do so these indexing requests might hit FNF exceptions.
                 * We will remove the files incrementally as we go...
                 */
                // Don't bother saving any changes in our segmentInfos
                runBlocking { readerPool.dropAll() }
                // Mark that the index has changed
                changeCount.incrementAndFetch()
                segmentInfos.changed()
                globalFieldNumberMap.clear()
                success = true
                val seqNo: Long = docWriter.nextSequenceNumber
                return seqNo
            } finally {
                if (!success) {
                    if (infoStream.isEnabled("IW")) {
                        infoStream.message("IW", "hit exception during deleteAll")
                    }
                }
            }
            // end synchronized(this)}

            //} // end synchronized(fullFlushLock)
        } catch (tragedy: Error) {
            tragicEvent(tragedy, "deleteAll")
            throw tragedy
        }
    }

    /**
     * Aborts running merges. Be careful when using this method: when you abort a long-running merge,
     * you lose a lot of work that must later be redone.
     */
    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    @Throws(IOException::class)
    private fun abortMerges() {
        merges.disable()
        // Abort all pending & running merges:
        IOUtils.applyToAll(
            pendingMerges
        ) { merge: MergePolicy.OneMerge ->
            if (infoStream.isEnabled("IW")) {
                infoStream.message("IW", "now abort pending merge " + segString(merge.segments))
            }
            abortOneMerge(merge)
            mergeFinish(merge)
        }
        pendingMerges.clear()

        // abort any merges pending from addIndexes(CodecReader...)
        addIndexesMergeSource.abortPendingMerges()

        runBlocking {
            for (merge in runningMerges) {
                if (infoStream.isEnabled("IW")) {
                    infoStream.message("IW", "now abort running merge " + segString(merge.segments))
                }
                merge.setAborted()
            }
        }

        // We wait here to make all merges stop.  It should not
        // take very long because they periodically check if
        // they are aborted.
        while (runningMerges.size + runningAddIndexesMerges.size != 0) {
            if (infoStream.isEnabled("IW")) {
                infoStream.message(
                    "IW",
                    ("now wait for "
                            + runningMerges.size
                            + " running merge/s to abort; currently running addIndexes: "
                            + runningAddIndexesMerges.size)
                )
            }

            doWait()
        }

        // TODO notifyAll() is not supported in KMP, need to think what to do here
        //(this as java.lang.Object).notifyAll()
        if (infoStream.isEnabled("IW")) {
            infoStream.message("IW", "all running merges have aborted")
        }
    }

    /**
     * Wait for any currently outstanding merges to finish.
     *
     *
     * It is guaranteed that any merges started prior to calling this method will have completed
     * once this method completes.
     */
    @Throws(IOException::class)
    fun waitForMerges() {
        // Give merge scheduler last chance to run, in case
        // any pending merges are waiting. We can't hold IW's lock
        // when going into merge because it can lead to deadlock.

        runBlocking { mergeScheduler.merge(mergeSource, MergeTrigger.CLOSING) }

        // TODO Synchronized is not supported in KMP, need to think what to do here
        //synchronized(this) {
        ensureOpen(false)
        if (infoStream.isEnabled("IW")) {
            infoStream.message("IW", "waitForMerges")
        }

        while (pendingMerges.isNotEmpty() || runningMerges.isNotEmpty()) {
            doWait()
        }

        // sanity check
        assert(mergingSegments.isEmpty())
        if (infoStream.isEnabled("IW")) {
            infoStream.message("IW", "waitForMerges done")
        }
        //}
    }

    /**
     * Called whenever the SegmentInfos has been updated and the index files referenced exist
     * (correctly) in the index directory.
     */
    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    @Throws(IOException::class)
    private fun checkpoint() {
        changed()
        deleter.checkpoint(segmentInfos, false)
    }

    /**
     * Checkpoints with IndexFileDeleter, so it's aware of new files, and increments changeCount, so
     * on close/commit we will write a new segments file, but does NOT bump segmentInfos.version.
     */
    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    @Throws(IOException::class)
    private fun checkpointNoSIS() {
        changeCount.incrementAndFetch()
        deleter.checkpoint(segmentInfos, false)
    }

    /** Called internally if any index state has changed.  */
    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    private fun changed() {
        changeCount.incrementAndFetch()
        segmentInfos.changed()
    }

    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    private fun publishFrozenUpdates(packet: FrozenBufferedUpdates): Long {
        assert(packet != null && packet.any())
        val nextGen: Long = bufferedUpdatesStream.push(packet)
        // Do this as an event so it applies higher in the stack when we are not holding
        // DocumentsWriterFlushQueue.purgeLock:
        eventQueue.add { w: IndexWriter ->
            try {
                // we call tryApply here since we don't want to block if a refresh or a flush is already
                // applying the
                // packet. The flush will retry this packet anyway to ensure all of them are applied
                tryApply(packet)
            } catch (t: Throwable) {
                try {
                    w.onTragicEvent(t, "applyUpdatesPacket")
                } catch (t1: Throwable) {
                    t.addSuppressed(t1)
                }
                throw t
            }
            w.flushDeletesCount.incrementAndFetch()
        }
        return nextGen
    }

    /**
     * Atomically adds the segment private delete packet and publishes the flushed segments
     * SegmentInfo to the index writer.
     */
    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    @Throws(IOException::class)
    private fun publishFlushedSegment(
        newSegment: SegmentCommitInfo,
        fieldInfos: FieldInfos,
        packet: FrozenBufferedUpdates,
        globalPacket: FrozenBufferedUpdates,
        sortMap: DocMap
    ) {
        var published = false
        try {
            // Lock order IW -> BDS
            ensureOpen(false)

            if (infoStream.isEnabled("IW")) {
                infoStream.message("IW", "publishFlushedSegment $newSegment")
            }

            if (globalPacket != null && globalPacket.any()) {
                publishFrozenUpdates(globalPacket)
            }

            // Publishing the segment must be sync'd on IW -> BDS to make the sure
            // that no merge prunes away the seg. private delete packet
            val nextGen: Long
            if (packet != null && packet.any()) {
                nextGen = publishFrozenUpdates(packet)
            } else {
                // Since we don't have a delete packet to apply we can get a new
                // generation right away
                nextGen = bufferedUpdatesStream.getNextGen()
                // No deletes/updates here, so marked finished immediately:
                bufferedUpdatesStream.finishedSegment(nextGen)
            }
            if (infoStream.isEnabled("IW")) {
                infoStream.message(
                    "IW", "publish sets newSegment delGen=" + nextGen + " seg=" + segString(newSegment)
                )
            }
            newSegment.bufferedDeletesGen = nextGen
            segmentInfos.add(newSegment)
            published = true
            checkpoint()
            if (packet != null && packet.any() && sortMap != null) {
                // TODO: not great we do this heavyish op while holding IW's monitor lock,
                // but it only applies if you are using sorted indices and updating doc values:
                val rld: ReadersAndUpdates = getPooledInstance(newSegment, true)!!
                rld.sortMap = sortMap
                // DON't release this ReadersAndUpdates we need to stick with that sortMap
            }
            val fieldInfo: FieldInfo? =
                fieldInfos.fieldInfo(config.softDeletesField!!) // will return null if no soft deletes are present
            // this is a corner case where documents delete them-self with soft deletes. This is used to
            // build delete tombstones etc. in this case we haven't seen any updates to the DV in this
            // fresh flushed segment.
            // if we have seen updates the update code checks if the segment is fully deleted.
            val hasInitialSoftDeleted =
                (fieldInfo != null && fieldInfo.docValuesGen == -1L && fieldInfo.docValuesType != DocValuesType.NONE)
            val isFullyHardDeleted = newSegment.delCount == newSegment.info.maxDoc()
            // we either have a fully hard-deleted segment or one or more docs are soft-deleted. In both
            // cases we need
            // to go and check if they are fully deleted. This has the nice side-effect that we now have
            // accurate numbers
            // for the soft delete right after we flushed to disk.
            if (hasInitialSoftDeleted || isFullyHardDeleted) {
                // this operation is only really executed if needed an if soft-deletes are not configured it
                // only be executed
                // if we deleted all docs in this newly flushed segment.
                val rld: ReadersAndUpdates = getPooledInstance(newSegment, true)!!
                try {
                    if (isFullyDeleted(rld)) {
                        dropDeletedSegment(newSegment)
                        checkpoint()
                    }
                } finally {
                    release(rld)
                }
            }
        } finally {
            if (!published) {
                adjustPendingNumDocs(-newSegment.info.maxDoc().toLong())
            }
            flushCount.incrementAndFetch()
            doAfterFlush()
        }
    }

    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    private fun resetMergeExceptions() {
        mergeExceptions.clear()
        mergeGen++
    }

    private fun noDupDirs(vararg dirs: Directory) {
        val dups: HashSet<Directory> = HashSet()
        for (i in dirs.indices) {
            require(!dups.contains(dirs[i])) { "Directory " + dirs[i] + " appears more than once" }
            require(dirs[i] !== directoryOrig) { "Cannot add directory to itself" }
            dups.add(dirs[i])
        }
    }

    /**
     * Acquires write locks on all the directories; be sure to match with a call to [ ][IOUtils.close] in a finally clause.
     */
    @Throws(IOException::class)
    private fun acquireWriteLocks(vararg dirs: Directory): MutableList<Lock> {
        val locks: MutableList<Lock> = ArrayList(dirs.size)
        for (i in dirs.indices) {
            var success = false
            try {
                val lock: Lock = dirs[i].obtainLock(WRITE_LOCK_NAME)
                locks.add(lock)
                success = true
            } finally {
                if (!success) {
                    // Release all previously acquired locks:
                    // TODO: addSuppressed it could be many...
                    IOUtils.closeWhileHandlingException(locks)
                }
            }
        }
        return locks
    }

    /**
     * Adds all segments from an array of indexes into this index.
     *
     *
     * This may be used to parallelize batch indexing. A large document collection can be broken
     * into sub-collections. Each sub-collection can be indexed in parallel, on a different thread,
     * process or machine. The complete index can then be created by merging sub-collection indexes
     * with this method.
     *
     *
     * **NOTE:** this method acquires the write lock in each directory, to ensure that no `IndexWriter` is currently open or tries to open while this is running.
     *
     *
     * This method is transactional in how Exceptions are handled: it does not commit a new
     * segments_N file until all indexes are added. This means if an Exception occurs (for example
     * disk full), then either no indexes will have been added or they all will have been.
     *
     *
     * Note that this requires temporary free space in the [Directory] up to 2X the sum of
     * all input indexes (including the starting index). If readers/searchers are open against the
     * starting index, then temporary free space required will be higher by the size of the starting
     * index (see [.forceMerge] for details).
     *
     *
     * This requires this index not be among those to be added.
     *
     *
     * All added indexes must have been created by the same Lucene version as this index.
     *
     * @return The [sequence number](#sequence_number) for this operation
     * @throws CorruptIndexException if the index is corrupt
     * @throws IOException if there is a low-level IO error
     * @throws IllegalArgumentException if addIndexes would cause the index to exceed [     ][.MAX_DOCS], or if the incoming index sort does not match this index's index sort
     */
    @Throws(IOException::class)
    fun addIndexes(vararg dirs: Directory): Long {
        ensureOpen()

        noDupDirs(*dirs)

        val locks: MutableList<Lock> = acquireWriteLocks(*dirs)

        val indexSort: Sort? = config.indexSort

        var successTop = false

        var seqNo: Long

        try {
            if (infoStream.isEnabled("IW")) {
                infoStream.message("IW", "flush at addIndexes(Directory...)")
            }

            flush(triggerMerge = false, applyAllDeletes = true)

            val infos: MutableList<SegmentCommitInfo> = mutableListOf()

            // long so we can detect int overflow:
            var totalMaxDoc: Long = 0
            val commits: MutableList<SegmentInfos> = ArrayList(dirs.size)
            for (dir in dirs) {
                if (infoStream.isEnabled("IW")) {
                    infoStream.message("IW", "addIndexes: process directory $dir")
                }
                val sis: SegmentInfos =
                    SegmentInfos.readLatestCommit(dir) // read infos from dir
                require(segmentInfos.indexCreatedVersionMajor == sis.indexCreatedVersionMajor) {
                    ("Cannot use addIndexes(Directory) with indexes that have been created "
                            + "by a different Lucene version. The current index was generated by Lucene "
                            + segmentInfos.indexCreatedVersionMajor
                            + " while one of the directories contains an index that was generated with Lucene "
                            + sis.indexCreatedVersionMajor)
                }
                totalMaxDoc += sis.totalMaxDoc().toLong()
                commits.add(sis)
            }

            // Best-effort up front check:
            testReserveDocs(totalMaxDoc)

            var success = false
            try {
                for (sis in commits) {
                    for (info in sis) {
                        assert(
                            !infos.contains(info)
                        ) { "dup info dir=" + info.info.dir + " name=" + info.info.name }

                        val segmentIndexSort: Sort? = info.info.indexSort

                        require(
                            !(indexSort != null && (segmentIndexSort == null || !isCongruentSort(
                                indexSort,
                                segmentIndexSort
                            )))
                        ) { "cannot change index sort from $segmentIndexSort to $indexSort" }

                        val newSegName = newSegmentName()

                        if (infoStream.isEnabled("IW")) {
                            infoStream.message(
                                "IW",
                                ("addIndexes: process segment origName="
                                        + info.info.name
                                        + " newName="
                                        + newSegName
                                        + " info="
                                        + info)
                            )
                        }

                        val context = IOContext(FlushInfo(info.info.maxDoc(), info.sizeInBytes()))

                        val fis: FieldInfos = readFieldInfos(info)
                        for (fi in fis) {
                            // This will throw exceptions if any of the incoming fields
                            // has an illegal schema change
                            globalFieldNumberMap.addOrGet(fi)
                        }
                        infos.add(copySegmentAsIs(info, newSegName, context))
                    }
                }
                success = true
            } finally {
                if (!success) {
                    for (sipc in infos) {
                        // Safe: these files must exist
                        deleteNewFiles(sipc.files())
                    }
                }
            }

            // TODO synchronized is not supported in KMP, need to think what to do here
            //synchronized(this) {
            success = false
            try {
                ensureOpen()

                // Now reserve the docs, just before we update SIS:
                reserveDocs(totalMaxDoc)

                seqNo = docWriter.nextSequenceNumber

                success = true
            } finally {
                if (!success) {
                    for (sipc in infos) {
                        // Safe: these files must exist
                        deleteNewFiles(sipc.files())
                    }
                }
            }
            segmentInfos.addAll(infos)
            checkpoint()
            //}

            successTop = true
        } catch (tragedy: Error) {
            tragicEvent(tragedy, "addIndexes(Directory...)")
            throw tragedy
        } finally {
            if (successTop) {
                IOUtils.close(locks)
            } else {
                IOUtils.closeWhileHandlingException(locks)
            }
        }
        maybeMerge()

        return seqNo
    }

    private fun validateMergeReader(leaf: CodecReader) {
        val segmentMeta: LeafMetaData = leaf.metaData
        require(segmentInfos.indexCreatedVersionMajor == segmentMeta.createdVersionMajor) {
            ("Cannot merge a segment that has been created with major version "
                    + segmentMeta.createdVersionMajor
                    + " into this index which has been created by major version "
                    + segmentInfos.indexCreatedVersionMajor)
        }

        check(!(segmentInfos.indexCreatedVersionMajor >= 7 && segmentMeta.minVersion == null)) {
            ("Indexes created on or after Lucene 7 must record the created version major, but "
                    + leaf
                    + " hides it")
        }

        val leafIndexSort: Sort? = segmentMeta.sort
        require(
            !(config.indexSort != null && (leafIndexSort == null || !isCongruentSort(
                config.indexSort!!,
                leafIndexSort
            )))
        ) { "cannot change index sort from " + leafIndexSort + " to " + config.indexSort }
    }

    /**
     * Merges the provided indexes into this index.
     *
     *
     * The provided IndexReaders are not closed.
     *
     *
     * See [.addIndexes] for details on transactional semantics, temporary free space
     * required in the Directory, and non-CFS segments on an Exception.
     *
     *
     * **NOTE:** empty segments are dropped by this method and not added to this index.
     *
     *
     * **NOTE:** provided [LeafReader]s are merged as specified by the [ ][MergePolicy.findMerges] API. Default behavior is to merge all provided readers
     * into a single segment. You can modify this by overriding the `findMerge` API in your
     * custom merge policy.
     *
     * @return The [sequence number](#sequence_number) for this operation
     * @throws CorruptIndexException if the index is corrupt
     * @throws IOException if there is a low-level IO error
     * @throws IllegalArgumentException if addIndexes would cause the index to exceed [     ][.MAX_DOCS]
     */
    @Throws(IOException::class)
    fun addIndexes(vararg readers: CodecReader): Long {
        ensureOpen()

        // long so we can detect int overflow:
        var numDocs: Long = 0
        val seqNo: Long

        try {
            // Best effort up front validations
            readers.forEach { leaf: CodecReader ->
                validateMergeReader(leaf)
                for (fi in leaf.fieldInfos) {
                    globalFieldNumberMap.verifyFieldInfo(fi)
                }
                numDocs += leaf.numDocs().toLong()
            }
            testReserveDocs(numDocs)

            // TODO synchronized is not supported in KMP, need to think what to do here
            //synchronized(this) {
            ensureOpen()
            if (!merges.areEnabled()) {
                throw AlreadyClosedException(
                    "this IndexWriter is closed. Cannot execute addIndexes(CodecReaders...) API"
                )
            }
            //}

            val mergePolicy: MergePolicy = config.mergePolicy
            val spec: MergePolicy.MergeSpecification = mergePolicy.findMerges(*readers)
            var mergeSuccess = false
            if (spec != null && spec.merges.isNotEmpty()) {
                try {
                    spec.merges.forEach { merge: MergePolicy.OneMerge ->
                        addIndexesMergeSource.registerMerge(
                            merge
                        )
                    }
                    runBlocking { mergeScheduler.merge(addIndexesMergeSource, MergeTrigger.ADD_INDEXES) }
                    spec.await()
                    mergeSuccess =
                        spec.merges.all { m: MergePolicy.OneMerge ->
                            m.hasCompletedSuccessfully().orElse(false)!!
                        }
                } finally {
                    if (!mergeSuccess) {
                        for (merge in spec.merges) {
                            if (merge.mergeInfo != null) {
                                deleteNewFiles(merge.mergeInfo!!.files())
                            }
                        }
                    }
                }
            } else {
                if (infoStream.isEnabled("IW")) {
                    if (spec == null) {
                        infoStream.message(
                            "addIndexes(CodecReaders...)",
                            "received null mergeSpecification from MergePolicy. No indexes to add, returning.."
                        )
                    } else {
                        infoStream.message(
                            "addIndexes(CodecReaders...)",
                            "received empty mergeSpecification from MergePolicy. No indexes to add, returning.."
                        )
                    }
                }
                return docWriter.nextSequenceNumber
            }

            if (mergeSuccess) {
                val infos: MutableList<SegmentCommitInfo> = mutableListOf()
                var totalDocs: Long = 0
                for (merge in spec.merges) {
                    totalDocs += merge.totalMaxDoc.toLong()
                    if (merge.mergeInfo != null) {
                        infos.add(merge.mergeInfo!!)
                    }
                }

                // TODO synchronized is not supported in KMP, need to think what to do here
                //synchronized(this) {
                if (!infos.isEmpty()) {
                    var registerSegmentSuccess = false
                    try {
                        ensureOpen()
                        // Reserve the docs, just before we update SIS:
                        reserveDocs(totalDocs)
                        registerSegmentSuccess = true
                    } finally {
                        if (!registerSegmentSuccess) {
                            for (sipc in infos) {
                                // Safe: these files must exist
                                deleteNewFiles(sipc.files())
                            }
                        }
                    }
                    segmentInfos.addAll(infos)
                    checkpoint()
                }
                seqNo = docWriter.nextSequenceNumber
                //}
            } else {
                if (infoStream.isEnabled("IW")) {
                    infoStream.message(
                        "addIndexes(CodecReaders...)", "failed to successfully merge all provided readers."
                    )
                }
                for (merge in spec.merges) {
                    if (merge.isAborted) {
                        throw MergePolicy.MergeAbortedException("merge was aborted.")
                    }
                    val t: Throwable? = merge.exception
                    if (t != null) {
                        IOUtils.rethrowAlways(t)
                    }
                }
                // If no merge hit an exception, and merge was not aborted, but we still failed to add
                // indexes, fail the API
                throw RuntimeException(
                    "failed to successfully merge all provided readers in addIndexes(CodecReader...)"
                )
            }
        } catch (tragedy: Error) {
            tragicEvent(tragedy, "addIndexes(CodecReader...)")
            throw tragedy
        }

        maybeMerge()
        return seqNo
    }

    private inner class AddIndexesMergeSource(private val writer: IndexWriter) :
        MergeScheduler.MergeSource {
        private val pendingAddIndexesMerges: ArrayDeque<MergePolicy.OneMerge> = ArrayDeque()

        fun registerMerge(merge: MergePolicy.OneMerge) {

            // TODO synchronized is not supported in KMP, need to think what to do here
            //synchronized(this@IndexWriter) {
            pendingAddIndexesMerges.add(merge)
            //}
        }

        override val nextMerge: MergePolicy.OneMerge?
            get() {

                // TODO synchronized is not supported in KMP, need to think what to do here
                //synchronized(this@IndexWriter) {
                if (!hasPendingMerges()) {
                    return null
                }
                val merge: MergePolicy.OneMerge = pendingAddIndexesMerges.poll()!! /*.remove()*/
                runningMerges.add(merge)
                return merge
                //}
            }

        override fun onMergeFinished(merge: MergePolicy.OneMerge) {

            // TODO synchronized is not supported in KMP, need to think what to do here
            //synchronized(this@IndexWriter) {
            runningMerges.remove(merge)
            //}
        }

        override fun hasPendingMerges(): Boolean {
            return pendingAddIndexesMerges.isNotEmpty()
        }

        @Throws(IOException::class)
        fun abortPendingMerges() {

            // TODO synchronized is not supported in KMP, need to think what to do here
            //synchronized(this@IndexWriter) {
            IOUtils.applyToAll(
                pendingAddIndexesMerges
            ) { merge: MergePolicy.OneMerge ->
                if (infoStream.isEnabled("IW")) {
                    infoStream.message("IW", "now abort pending addIndexes merge")
                }
                runBlocking { merge.setAborted() }
                merge.close(
                    success = false,
                    segmentDropped = false
                ) { `_`: MergePolicy.MergeReader -> }
                onMergeFinished(merge)
            }
            pendingAddIndexesMerges.clear()
            //}
        }

        @Throws(IOException::class)
        override fun merge(merge: MergePolicy.OneMerge) {
            var success = false
            try {
                writer.addIndexesReaderMerge(merge)
                success = true
            } catch (t: Throwable) {
                handleMergeException(t, merge)
            } finally {

                // TODO synchronized is not supported in KMP, need to think what to do here
                //synchronized(this@IndexWriter) {
                merge.close(
                    success,
                    false
                ) { `_`: MergePolicy.MergeReader -> }
                onMergeFinished(merge)
                //}
            }
        }
    }

    /**
     * Runs a single merge operation for [IndexWriter.addIndexes].
     *
     *
     * Merges and creates a SegmentInfo, for the readers grouped together in provided OneMerge.
     *
     * @param merge OneMerge object initialized from readers.
     * @throws IOException if there is a low-level IO error
     */
    @Throws(IOException::class)
    fun addIndexesReaderMerge(merge: MergePolicy.OneMerge) {
        runBlocking { merge.mergeInit() }
        merge.checkAborted()

        // long so we can detect int overflow:
        var numDocs: Long = 0
        if (infoStream.isEnabled("IW")) {
            infoStream.message("IW", "flush at addIndexes(CodecReader...)")
        }
        flush(triggerMerge = false, applyAllDeletes = true)

        val mergedName = newSegmentName()
        val mergeDirectory: Directory = mergeScheduler.wrapForMerge(merge, directory)
        var numSoftDeleted = 0
        var hasBlocks = false
        for (reader in merge.mergeReader) {
            val leaf: CodecReader = reader.codecReader
            numDocs += leaf.numDocs().toLong()
            for (context in reader.codecReader.leaves()) {
                hasBlocks = hasBlocks or context.reader().metaData.hasBlocks
            }
            if (softDeletesEnabled) {
                val liveDocs: Bits? = reader.hardLiveDocs
                numSoftDeleted +=
                    PendingSoftDeletes.countSoftDeletes(
                        FieldExistsQuery.getDocValuesDocIdSetIterator(
                            config.softDeletesField!!,
                            leaf
                        ),
                        liveDocs
                    )
            }
        }

        // Best-effort up front check:
        testReserveDocs(numDocs)

        val context = IOContext(MergeInfo(Math.toIntExact(numDocs), -1, false, UNBOUNDED_MAX_MERGE_SEGMENTS))

        val trackingDir = TrackingDirectoryWrapper(mergeDirectory)
        val codec: Codec = config.codec
        // We set the min version to null for now, it will be set later by SegmentMerger
        val segInfo =
            SegmentInfo(
                directoryOrig,
                Version.LATEST,
                null,
                mergedName,
                -1,
                false,
                hasBlocks,
                codec,
                mutableMapOf(),
                StringHelper.randomId(),
                mutableMapOf(),
                config.indexSort
            )

        var readers: MutableList<CodecReader> = mutableListOf()
        for (mr in merge.mergeReader) {
            val reader: CodecReader = merge.wrapForMerge(mr.codecReader)
            readers.add(reader)
        }

        // Don't reorder if an explicit sort is configured.
        val hasIndexSort = config.indexSort != null
        // Don't reorder if blocks can't be identified using the parent field.
        val hasBlocksButNoParentField = readers.map { obj: LeafReader -> obj.metaData }.any(LeafMetaData::hasBlocks)
                && readers.map { obj: CodecReader -> obj.fieldInfos }
            .map { obj: FieldInfos -> obj.parentField }.any { obj: Any -> Objects.isNull(obj) }

        val intraMergeExecutor: Executor = mergeScheduler.getIntraMergeExecutor(merge)

        if (!hasIndexSort && !hasBlocksButNoParentField && !readers.isEmpty()) {
            val mergedReader: CodecReader =
                SlowCompositeCodecReaderWrapper.wrap(readers)
            val docMap: DocMap? = merge.reorder(mergedReader, directory, intraMergeExecutor)
            if (docMap != null) {
                readers = mutableListOf(
                    SortingCodecReader.wrap(
                        mergedReader,
                        docMap,
                        null
                    )
                )
            }
        }

        val merger =
            SegmentMerger(
                readers,
                segInfo,
                infoStream,
                trackingDir,
                globalFieldNumberMap,
                context,
                intraMergeExecutor
            )

        if (!merger.shouldMerge()) {
            return
        }

        merge.checkAborted()

        // TODO Synchronized is not supported in KMP, need to think what to do here
        //synchronized(this) {
        runningAddIndexesMerges.add(merger)
        //}
        merge.mergeStartNS = System.nanoTime()
        try {
            merger.merge() // merge 'em
        } finally {

            // TODO synchronized is not supported in KMP, need to think what to do here
            //synchronized(this) {
            runningAddIndexesMerges.remove(merger)

            // TODO notifyAll is not supported in KMP, need to think what to do here
            //(this as java.lang.Object).notifyAll()
            //}
        }

        merge.setMergeInfo(
            SegmentCommitInfo(
                segInfo,
                0,
                numSoftDeleted,
                -1L,
                -1L,
                -1L,
                StringHelper.randomId()
            )
        )
        merge.mergeInfo!!.info.setFiles(HashSet(trackingDir.createdFiles))
        trackingDir.clearCreatedFiles()

        setDiagnostics(merge.mergeInfo!!.info, SOURCE_ADDINDEXES_READERS)

        val mergePolicy: MergePolicy = config.mergePolicy
        val useCompoundFile: Boolean

        // TODO Synchronized is not supported in KMP, need to think what to do here
        //synchronized(this) {
        merge.checkAborted()
        useCompoundFile = mergePolicy.useCompoundFile(segmentInfos, merge.mergeInfo!!, this)
        //}

        // Now create the compound file if needed
        if (useCompoundFile) {
            val filesToDelete: MutableCollection<String> = merge.mergeInfo!!.files()
            val trackingCFSDir = TrackingDirectoryWrapper(mergeDirectory)
            // createCompoundFile tries to cleanup, but it might not always be able to...
            createCompoundFile(
                infoStream,
                trackingCFSDir,
                merge.mergeInfo!!.info,
                context
            ) { files: MutableCollection<String> -> this.deleteNewFiles(files) }

            // creating cfs resets the files tracked in SegmentInfo. if it succeeds, we
            // delete the non cfs files directly as they are not tracked anymore.
            deleteNewFiles(filesToDelete)
            merge.mergeInfo!!.info.useCompoundFile = true
        }

        merge.setMergeInfo(merge.info!!)

        // Have codec write SegmentInfo.  Must do this after
        // creating CFS so that 1) .si isn't slurped into CFS,
        // and 2) .si reflects useCompoundFile=true change
        // above:
        codec.segmentInfoFormat().write(trackingDir, merge.mergeInfo!!.info, context)
        merge.mergeInfo!!.info.addFiles(trackingDir.createdFiles)
        // Return without registering the segment files with IndexWriter.
        // We do this together for all merges triggered by an addIndexes API,
        // to keep the API transactional.
    }

    /** Copies the segment files as-is into the IndexWriter's directory.  */
    @Throws(IOException::class)
    private fun copySegmentAsIs(
        info: SegmentCommitInfo, segName: String, context: IOContext
    ): SegmentCommitInfo {
        // Same SI as before but we change directory and name

        val newInfo =
            SegmentInfo(
                directoryOrig,
                info.info.version,
                info.info.minVersion,
                segName,
                info.info.maxDoc(),
                info.info.useCompoundFile,
                info.info.hasBlocks,
                info.info.codec,
                info.info.getDiagnostics(),
                info.info.getId(),
                info.info.attributes,
                info.info.indexSort
            )
        val newInfoPerCommit =
            SegmentCommitInfo(
                newInfo,
                info.delCount,
                info.getSoftDelCount(),
                info.delGen,
                info.fieldInfosGen,
                info.docValuesGen,
                info.getId()
            )

        newInfo.setFiles(info.info.files())
        newInfoPerCommit.setFieldInfosFiles(info.getFieldInfosFiles())
        newInfoPerCommit.docValuesUpdatesFiles = info.docValuesUpdatesFiles

        var success = false

        val copiedFiles: MutableSet<String> = HashSet()
        try {
            // Copy the segment's files
            for (file in info.files()) {
                val newFileName: String = newInfo.namedForThisSegment(file)
                directory.copyFrom(info.info.dir, file, newFileName, context)
                copiedFiles.add(newFileName)
            }
            success = true
        } finally {
            if (!success) {
                // Safe: these files must exist
                deleteNewFiles(copiedFiles)
            }
        }

        assert(
            copiedFiles == newInfoPerCommit.files()
        ) { "copiedFiles=" + copiedFiles + " vs " + newInfoPerCommit.files() }

        return newInfoPerCommit
    }

    /**
     * A hook for extending classes to execute operations after pending added and deleted documents
     * have been flushed to the Directory but before the change is committed (new segments_N file
     * written).
     */
    @Throws(IOException::class)
    protected fun doAfterFlush() {
    }

    /**
     * A hook for extending classes to execute operations before pending added and deleted documents
     * are flushed to the Directory.
     */
    @Throws(IOException::class)
    protected fun doBeforeFlush() {
    }

    /**
     * Expert: prepare for commit. This does the first phase of 2-phase commit. This method does all
     * steps necessary to commit changes since this writer was opened: flushes pending added and
     * deleted docs, syncs the index files, writes most of next segments_N file. After calling this
     * you must call either [.commit] to finish the commit, or [.rollback] to revert
     * the commit and undo all changes done since the writer was opened.
     *
     *
     * You can also just call [.commit] directly without prepareCommit first in which case
     * that method will internally call prepareCommit.
     *
     * @return The [sequence number](#sequence_number) of the last operation in the commit.
     * All sequence numbers &lt;= this value will be reflected in the commit, and all others will
     * not.
     */
    @OptIn(ExperimentalAtomicApi::class)
    @Throws(IOException::class)
    override fun prepareCommit(): Long {
        ensureOpen()
        pendingSeqNo = prepareCommitInternal()
        // we must do this outside of the commitLock else we can deadlock:
        if (maybeMerge.getAndSet(false)) {
            maybeMerge(
                config.mergePolicy,
                MergeTrigger.FULL_FLUSH,
                UNBOUNDED_MAX_MERGE_SEGMENTS
            )
        }
        return pendingSeqNo
    }

    /**
     * Expert: Flushes the next pending writer per thread buffer if available or the largest active
     * non-pending writer per thread buffer in the calling thread. This can be used to flush documents
     * to disk outside of an indexing thread. In contrast to [.flush] this won't mark all
     * currently active indexing buffers as flush-pending.
     *
     *
     * Note: this method is best-effort and might not flush any segments to disk. If there is a
     * full flush happening concurrently multiple segments might have been flushed. Users of this API
     * can access the IndexWriters current memory consumption via [.ramBytesUsed]
     *
     * @return `true` iff this method flushed at least on segment to disk.
     * @lucene.experimental
     */
    @Throws(IOException::class)
    fun flushNextBuffer(): Boolean {
        try {
            if (runBlocking { docWriter.flushOneDWPT() }) {
                processEvents(true)
                return true // we wrote a segment
            }
            return false
        } catch (tragedy: Error) {
            tragicEvent(tragedy, "flushNextBuffer")
            throw tragedy
        } finally {
            maybeCloseOnTragicEvent()
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    @Throws(IOException::class)
    private fun prepareCommitInternal(): Long {
        startCommitTime = System.nanoTime()

        // TODO synchronized is not supported in KMP, need to think what to do here
        //synchronized(commitLock) {
        ensureOpen(false)
        if (infoStream.isEnabled("IW")) {
            infoStream.message("IW", "prepareCommit: flush")
            infoStream.message("IW", "  index before flush " + segString())
        }

        if (tragedy.load() != null) {
            throw IllegalStateException(
                "this writer hit an unrecoverable error; cannot commit", tragedy.load()
            )
        }

        check(pendingCommit == null) { "prepareCommit was already called with no corresponding call to commit" }

        doBeforeFlush()
        testPoint("startDoFlush")
        var toCommit: SegmentInfos?
        var anyChanges = false
        var seqNo: Long
        var pointInTimeMerges: MergePolicy.MergeSpecification? = null
        val stopAddingMergedSegments = AtomicBoolean(false)
        val maxCommitMergeWaitMillis: Long = config.maxFullFlushMergeWaitMillis

        // This is copied from doFlush, except it's modified to
        // clone & incRef the flushed SegmentInfos inside the
        // sync block:
        try {

            // TODO synchronized is not supported in KMP, need to think what to do here
            //synchronized(fullFlushLock) {
            var flushSuccess = false
            var success = false
            try {
                seqNo = runBlocking { docWriter.flushAllThreads() }
                if (seqNo < 0) {
                    anyChanges = true
                    seqNo = -seqNo
                }
                if (!anyChanges) {
                    // prevent double increment since docWriter#doFlush increments the flushcount
                    // if we flushed anything.
                    flushCount.incrementAndFetch()
                }
                publishFlushedSegments(true)
                // cannot pass triggerMerges=true here else it can lead to deadlock:
                processEvents(false)

                flushSuccess = true

                applyAllDeletesAndUpdates()

                // TODO synchronized is not supported in KMP, need to think what to do here
                //synchronized(this) {
                writeReaderPool(true)
                if (changeCount.load() != lastCommitChangeCount) {
                    // There are changes to commit, so we will write a new segments_N in startCommit.
                    // The act of committing is itself an NRT-visible change (an NRT reader that was
                    // just opened before this should see it on reopen) so we increment changeCount
                    // and segments version so a future NRT reopen will see the change:
                    changeCount.incrementAndFetch()
                    segmentInfos.changed()
                }

                if (commitUserData != null) {
                    val userData: MutableMap<String, String> = HashMap()
                    for (ent in commitUserData) {
                        userData[ent.key] = ent.value
                    }
                    segmentInfos.setUserData(userData, false)
                }

                // Must clone the segmentInfos while we still
                // hold fullFlushLock and while sync'd so that
                // no partial changes (eg a delete w/o
                // corresponding add from an updateDocument) can
                // sneak into the commit point:
                toCommit = segmentInfos.clone()
                pendingCommitChangeCount = changeCount.load()
                // This protects the segmentInfos we are now going
                // to commit.  This is important in case, eg, while
                // we are trying to sync all referenced files, a
                // merge completes which would otherwise have
                // removed the files we are now syncing.
                deleter.incRef(toCommit.files(false))
                if (maxCommitMergeWaitMillis > 0) {
                    // we can safely call preparePointInTimeMerge since writeReaderPool(true) above
                    // wrote all
                    // necessary files to disk and checkpointed them.
                    pointInTimeMerges =
                        preparePointInTimeMerge(
                            toCommit,
                            { stopAddingMergedSegments.load() },
                            MergeTrigger.COMMIT,
                            { `_`: SegmentCommitInfo -> })
                }
                //} // end of synchronized(this)
                success = true
            } finally {
                if (!success) {
                    if (infoStream.isEnabled("IW")) {
                        infoStream.message("IW", "hit exception during prepareCommit")
                    }
                }

                // TODO Thread is not supported in KMP, need to think what to do here
                //assert(java.lang.Thread.holdsLock(fullFlushLock))
                // Done: finish the full flush!
                docWriter.finishFullFlush(flushSuccess)
                doAfterFlush()
            }
            //} // end of synchronized(fullFlushLock)
        } catch (tragedy: Error) {
            tragicEvent(tragedy, "prepareCommit")
            throw tragedy
        } finally {
            maybeCloseOnTragicEvent()
        }

        if (pointInTimeMerges != null) {
            if (infoStream.isEnabled("IW")) {
                infoStream.message(
                    "IW", "now run merges during commit: " + pointInTimeMerges.segString(directory)
                )
            }
            eventListener.beginMergeOnFullFlush(pointInTimeMerges)

            runBlocking { mergeScheduler.merge(mergeSource, MergeTrigger.COMMIT) }
            pointInTimeMerges.await(maxCommitMergeWaitMillis, TimeUnit.MILLISECONDS)

            if (infoStream.isEnabled("IW")) {
                infoStream.message("IW", "done waiting for merges during commit")
            }
            eventListener.endMergeOnFullFlush(pointInTimeMerges)

            // TODO synchronized is not supported in KMP, need to think what to do here
            //synchronized(this) {
            // we need to call this under lock since mergeFinished above is also called under the IW
            // lock
            stopAddingMergedSegments.store(true)
            //}
        }
        // do this after handling any pointInTimeMerges since the files will have changed if any
        // merges
        // did complete
        filesToCommit = toCommit.files(false)
        try {
            if (anyChanges) {
                maybeMerge.store(true)
            }
            startCommit(toCommit)
            return if (pendingCommit == null) {
                -1
            } else {
                seqNo
            }
        } catch (t: Throwable) {

            // TODO synchronized is not supported in KMP, need to think what to do here
            //synchronized(this) {
            if (filesToCommit != null) {
                try {
                    deleter.decRef(filesToCommit!!)
                } catch (t1: Throwable) {
                    t.addSuppressed(t1)
                } finally {
                    filesToCommit = null
                }
            }
            //}
            throw t
        }
        //} // end of synchronized(commitLock)
    }

    /**
     * This optimization allows a commit/getReader to wait for merges on smallish segments to reduce
     * the eventual number of tiny segments in the commit point / NRT Reader. We wrap a `OneMerge` to update the `mergingSegmentInfos` once the merge has finished. We replace the
     * source segments in the SIS that we are going to commit / open the reader on with the freshly
     * merged segment, but ignore all deletions and updates that are made to documents in the merged
     * segment while it was merging. The updates that are made do not belong to the point-in-time
     * commit point / NRT READER and should therefore not be included. See the clone call in `onMergeComplete` below. We also ensure that we pull the merge readers while holding `IndexWriter`'s lock. Otherwise we could see concurrent deletions/updates applied that do not
     * belong to the segment.
     */
    @Throws(IOException::class)
    private fun preparePointInTimeMerge(
        mergingSegmentInfos: SegmentInfos,
        stopCollectingMergeResults: () -> Boolean /*java.util.function.BooleanSupplier*/,
        trigger: MergeTrigger,
        mergeFinished: IOConsumer<SegmentCommitInfo>
    ): MergePolicy.MergeSpecification? {

        // TODO Thread is not supported in KMP, need to think what to do here
        //assert(java.lang.Thread.holdsLock(this))

        assert(
            trigger == MergeTrigger.GET_READER || trigger == MergeTrigger.COMMIT
        ) { "illegal trigger: $trigger" }
        val pointInTimeMerges: MergePolicy.MergeSpecification? =
            updatePendingMerges(
                OneMergeWrappingMergePolicy(
                    config.mergePolicy
                ) { toWrap: MergePolicy.OneMerge ->
                    object : MergePolicy.OneMerge(toWrap) {
                        var origInfo: SegmentCommitInfo? = null
                        val onlyOnce: AtomicBoolean =
                            AtomicBoolean(false)

                        @Throws(IOException::class)
                        override fun mergeFinished(committed: Boolean, segmentDropped: Boolean) {

                            // TODO Thread is not supported in KMP, need to think what to do here
                            //assert(java.lang.Thread.holdsLock(this@IndexWriter))

                            // includedInCommit will be set (above, by our caller) to false if the
                            // allowed max wall clock
                            // time (IWC.getMaxCommitMergeWaitMillis()) has elapsed, which means we did
                            // not make the timeout
                            // and will not commit our merge to the to-be-committed SegmentInfos
                            if (!segmentDropped && committed && !stopCollectingMergeResults()) {
                                // make sure onMergeComplete really was called:

                                checkNotNull(origInfo)

                                if (infoStream.isEnabled("IW")) {
                                    infoStream.message(
                                        "IW", "now apply merge during commit: " + toWrap.segString()
                                    )
                                }

                                if (trigger == MergeTrigger.COMMIT) {
                                    // if we do this in a getReader call here this is obsolete since we
                                    // already hold a reader that has
                                    // incRef'd these files
                                    deleter.incRef(origInfo!!.files())
                                }
                                val mergedSegmentNames: MutableSet<String> = HashSet()
                                for (sci in segments) {
                                    mergedSegmentNames.add(sci.info.name)
                                }
                                val toCommitMergedAwaySegments: MutableList<SegmentCommitInfo> = mutableListOf()
                                for (sci in mergingSegmentInfos) {
                                    if (mergedSegmentNames.contains(sci.info.name)) {
                                        toCommitMergedAwaySegments.add(sci)
                                        if (trigger == MergeTrigger.COMMIT) {
                                            // if we do this in a getReader call here this is obsolete since we
                                            // already hold a reader that has
                                            // incRef'd these files and will decRef them when it's closed
                                            deleter.decRef(sci.files())
                                        }
                                    }
                                }
                                // Construct a OneMerge that applies to toCommit
                                val applicableMerge: MergePolicy.OneMerge =
                                    MergePolicy.OneMerge(toCommitMergedAwaySegments)
                                applicableMerge.info = origInfo
                                val segmentCounter =
                                    origInfo!!.info.name.substring(1).toLong(Character.MAX_RADIX)
                                mergingSegmentInfos.counter = max(mergingSegmentInfos.counter, segmentCounter + 1)
                                mergingSegmentInfos.applyMergeChanges(applicableMerge, false)
                            } else {
                                if (infoStream.isEnabled("IW")) {
                                    infoStream.message(
                                        "IW", "skip apply merge during commit: " + toWrap.segString()
                                    )
                                }
                            }
                            toWrap.mergeFinished(committed, segmentDropped)
                            super.mergeFinished(committed, segmentDropped)
                        }

                        @Throws(IOException::class)
                        override fun onMergeComplete() {

                            // TODO Thread is not supported in KMP, need to think what to do here
                            //assert(java.lang.Thread.holdsLock(this@IndexWriter))
                            if (!stopCollectingMergeResults() && !isAborted && (info!!.info.maxDoc() > 0) /* never do this if the segment if dropped / empty */) {
                                mergeFinished.accept(info!!)
                                // clone the target info to make sure we have the original info without
                                // the updated del and update gens
                                origInfo = info!!.clone()
                            }
                            toWrap.onMergeComplete()
                            super.onMergeComplete()
                        }

                        @Throws(IOException::class)
                        override fun initMergeReaders(
                            readerFactory: IOFunction<SegmentCommitInfo, MergePolicy.MergeReader>
                        ) {
                            if (onlyOnce.compareAndSet(expectedValue = false, newValue = true)) {
                                // we do this only once below to pull readers as point in time readers
                                // with respect to the commit point
                                // we try to update
                                super.initMergeReaders(readerFactory)
                            }
                        }

                        @Throws(IOException::class)
                        override fun wrapForMerge(reader: CodecReader): CodecReader {
                            return toWrap.wrapForMerge(reader) // must delegate
                        }

                        @Throws(IOException::class)
                        override fun reorder(
                            reader: CodecReader,
                            dir: Directory,
                            executor: Executor
                        ): DocMap {
                            return toWrap.reorder(reader, dir, executor)!! // must delegate
                        }

                        override fun setMergeInfo(info: SegmentCommitInfo) {
                            super.setMergeInfo(info)
                            toWrap.setMergeInfo(info)
                        }
                    }
                },
                trigger,
                UNBOUNDED_MAX_MERGE_SEGMENTS
            )
        if (pointInTimeMerges != null) {
            var closeReaders = true
            try {
                for (merge in pointInTimeMerges.merges) {
                    val context = IOContext(merge.storeMergeInfo)
                    merge.initMergeReaders { sci: SegmentCommitInfo ->
                        val rld: ReadersAndUpdates = getPooledInstance(sci, true)!!
                        // calling setIsMerging is important since it causes the RaU to record all DV
                        // updates
                        // in a separate map in order to be applied to the merged segment after it's done
                        rld.setIsMerging()
                        runBlocking {
                            rld.getReaderForMerge(context) { mr: MergePolicy.MergeReader ->
                                deleter.incRef(mr.reader!!.segmentInfo.files())
                            }
                        }
                    }
                }
                closeReaders = false
            } finally {
                if (closeReaders) {
                    IOUtils.applyToAll(
                        pointInTimeMerges.merges
                    ) { merge: MergePolicy.OneMerge ->
                        // that merge is broken we need to clean up after it - it's fine we still have the
                        // IW lock to do this
                        val removed: Boolean = pendingMerges.remove(merge)
                        assert(removed) { "merge should be pending but isn't: " + merge.segString() }
                        try {
                            abortOneMerge(merge)
                        } finally {
                            mergeFinish(merge)
                        }
                    }
                }
            }
        }
        return pointInTimeMerges
    }

    /**
     * Ensures that all changes in the reader-pool are written to disk.
     *
     * @param writeDeletes if `true` if deletes should be written to disk too.
     */
    @Throws(IOException::class)
    private fun writeReaderPool(writeDeletes: Boolean) {

        // TODO Thread is not supported in KMP, need to think what to do here
        //assert(java.lang.Thread.holdsLock(this))
        runBlocking {
            if (writeDeletes) {
                if (readerPool.commit(segmentInfos)) {
                    checkpointNoSIS()
                }
            } else { // only write the docValues
                if (readerPool.writeAllDocValuesUpdates()) {
                    checkpoint()
                }
            }
        }
        // now do some best effort to check if a segment is fully deleted
        val toDrop: MutableList<SegmentCommitInfo> = mutableListOf() // don't modify segmentInfos in-place
        for (info in segmentInfos) {
            val readersAndUpdates: ReadersAndUpdates? = readerPool.get(info, false)
            if (readersAndUpdates != null) {
                if (isFullyDeleted(readersAndUpdates)) {
                    toDrop.add(info)
                }
            }
        }
        for (info in toDrop) {
            dropDeletedSegment(info)
        }
        if (!toDrop.isEmpty()) {
            checkpoint()
        }
    }

    /**
     * Sets the iterator to provide the commit user data map at commit time. Calling this method is
     * considered a committable change and will be [committed][.commit] even if there are no
     * other changes this writer. Note that you must call this method before [.prepareCommit].
     * Otherwise it won't be included in the follow-on [.commit].
     *
     *
     * **NOTE:** the iterator is late-binding: it is only visited once all documents for the
     * commit have been written to their segments, before the next segments_N file is written
     */
    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    fun setLiveCommitData(
        commitUserData: Iterable<MutableMap.MutableEntry<String, String>>
    ) {
        setLiveCommitData(commitUserData, true)
    }

    /**
     * Sets the commit user data iterator, controlling whether to advance the [ ][SegmentInfos.getVersion].
     *
     * @see .setLiveCommitData
     * @lucene.internal
     */
    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    fun setLiveCommitData(
        commitUserData: Iterable<MutableMap.MutableEntry<String, String>>, doIncrementVersion: Boolean
    ) {
        this.commitUserData = commitUserData
        if (doIncrementVersion) {
            segmentInfos.changed()
        }
        changeCount.incrementAndFetch()
    }

    /**
     * Returns the commit user data iterable previously set with [.setLiveCommitData],
     * or null if nothing has been set yet.
     */
    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    fun getLiveCommitData(): Iterable<MutableMap.MutableEntry<String, String>>? {
        return commitUserData
    }

    // Used only by commit and prepareCommit, below; lock
    // order is commitLock -> IW
    private val commitLock = Any()

    /**
     * Commits all pending changes (added and deleted documents, segment merges, added indexes, etc.)
     * to the index, and syncs all referenced index files, such that a reader will see the changes and
     * the index updates will survive an OS or machine crash or power loss. Note that this does not
     * wait for any running background merges to finish. This may be a costly operation, so you should
     * test the cost in your application and do it only when really necessary.
     *
     *
     * Note that this operation calls Directory.sync on the index files. That call should not
     * return until the file contents and metadata are on stable storage. For FSDirectory, this calls
     * the OS's fsync. But, beware: some hardware devices may in fact cache writes even during fsync,
     * and return before the bits are actually on stable storage, to give the appearance of faster
     * performance. If you have such a device, and it does not have a battery backup (for example)
     * then on power loss it may still lose data. Lucene cannot guarantee consistency on such devices.
     *
     *
     * If nothing was committed, because there were no pending changes, this returns -1. Otherwise,
     * it returns the sequence number such that all indexing operations prior to this sequence will be
     * included in the commit point, and all other operations will not.
     *
     * @see .prepareCommit
     *
     * @return The [sequence number](#sequence_number) of the last operation in the commit.
     * All sequence numbers &lt;= this value will be reflected in the commit, and all others will
     * not.
     */
    @Throws(IOException::class)
    override fun commit(): Long {
        ensureOpen()
        return commitInternal(config.mergePolicy)
    }

    /**
     * Returns true if there may be changes that have not been committed. There are cases where this
     * may return true when there are no actual "real" changes to the index, for example if you've
     * deleted by Term or Query but that Term or Query does not match any documents. Also, if a merge
     * kicked off as a result of flushing a new segment during [.commit], or a concurrent merged
     * finished, this method may return true right after you had just called [.commit].
     */
    fun hasUncommittedChanges(): Boolean {
        return changeCount.load() != lastCommitChangeCount || hasChangesInRam()
    }

    /** Returns true if there are any changes or deletes that are not flushed or applied.  */
    fun hasChangesInRam(): Boolean {
        return docWriter.anyChanges() || bufferedUpdatesStream.any()
    }

    @Throws(IOException::class)
    private fun commitInternal(mergePolicy: MergePolicy): Long {
        if (infoStream.isEnabled("IW")) {
            infoStream.message("IW", "commit: start")
        }

        val seqNo: Long

        // TODO synchronized is not supported in KMP, need to think what to do here
        //synchronized(commitLock) {
        ensureOpen(false)
        if (infoStream.isEnabled("IW")) {
            infoStream.message("IW", "commit: enter lock")
        }

        if (pendingCommit == null) {
            if (infoStream.isEnabled("IW")) {
                infoStream.message("IW", "commit: now prepare")
            }
            seqNo = prepareCommitInternal()
        } else {
            if (infoStream.isEnabled("IW")) {
                infoStream.message("IW", "commit: already prepared")
            }
            seqNo = pendingSeqNo
        }
        finishCommit()
        //}

        // we must do this outside of the commitLock else we can deadlock:
        if (maybeMerge.getAndSet(false)) {
            maybeMerge(mergePolicy, MergeTrigger.FULL_FLUSH, UNBOUNDED_MAX_MERGE_SEGMENTS)
        }

        return seqNo
    }

    @Throws(IOException::class)
    private fun finishCommit() {
        var commitCompleted = false
        var committedSegmentsFileName: String?

        try {

            // TODO synchronized is not supported in KMP, need to think what to do here
            //synchronized(this) {
            ensureOpen(false)
            if (tragedy.load() != null) {
                throw IllegalStateException(
                    "this writer hit an unrecoverable error; cannot complete commit", tragedy.load()
                )
            }
            if (pendingCommit != null) {
                val commitFiles = this.filesToCommit
                try {
                    AutoCloseable { deleter.decRef(commitFiles!!) }.use { finalizer ->
                        if (infoStream.isEnabled("IW")) {
                            infoStream.message("IW", "commit: pendingCommit != null")
                        }
                        committedSegmentsFileName = pendingCommit!!.finishCommit(directory)

                        // we committed, if anything goes wrong after this, we are screwed and it's a tragedy:
                        commitCompleted = true

                        if (infoStream.isEnabled("IW")) {
                            infoStream.message(
                                "IW", "commit: done writing segments file \"$committedSegmentsFileName\""
                            )
                        }

                        // NOTE: don't use this.checkpoint() here, because
                        // we do not want to increment changeCount:
                        deleter.checkpoint(pendingCommit!!, true)

                        // Carry over generation to our master SegmentInfos:
                        segmentInfos.updateGeneration(pendingCommit!!)

                        lastCommitChangeCount = pendingCommitChangeCount
                        rollbackSegments = pendingCommit!!.createBackupSegmentInfos()
                    }
                } finally {

                    // TODO notifyAll is not supported in KMP, need to think what to do here
                    //(this as java.lang.Object).notifyAll()
                    pendingCommit = null
                    this.filesToCommit = null
                }
            } else {
                assert(filesToCommit == null)
                if (infoStream.isEnabled("IW")) {
                    infoStream.message("IW", "commit: pendingCommit == null; skip")
                }
            }
            //} // end of synchronized(this)
        } catch (t: Throwable) {
            if (infoStream.isEnabled("IW")) {
                infoStream.message("IW", "hit exception during finishCommit: " + t.message)
            }
            if (commitCompleted) {
                tragicEvent(t, "finishCommit")
            }
            throw t
        }

        if (infoStream.isEnabled("IW")) {
            infoStream.message(
                "IW",
                "commit: took ${
                    (System.nanoTime() - startCommitTime) / TimeUnit.MILLISECONDS.toNanos(1).toDouble()
                } msec"
            )
            infoStream.message("IW", "commit: done")
        }
    }

    // Ensures only one flush() is actually flushing segments
    // at a time:
    private val fullFlushLock = Any()

    /**
     * Moves all in-memory segments to the [Directory], but does not commit (fsync) them (call
     * [.commit] for that).
     */
    @Throws(IOException::class)
    fun flush() {
        flush(triggerMerge = true, applyAllDeletes = true)
    }

    /**
     * Flush all in-memory buffered updates (adds and deletes) to the Directory.
     *
     * @param triggerMerge if true, we may merge segments (if deletes or docs were flushed) if
     * necessary
     * @param applyAllDeletes whether pending deletes should also
     */
    @Throws(IOException::class)
    fun flush(triggerMerge: Boolean, applyAllDeletes: Boolean) {
        // NOTE: this method cannot be sync'd because
        // maybeMerge() in turn calls mergeScheduler.merge which
        // in turn can take a long time to run and we don't want
        // to hold the lock for that.  In the case of
        // ConcurrentMergeScheduler this can lead to deadlock
        // when it stalls due to too many running merges.

        // We can be called during close, when closing==true, so we must pass false to ensureOpen:

        ensureOpen(false)
        if (doFlush(applyAllDeletes) && triggerMerge) {
            maybeMerge(
                config.mergePolicy,
                MergeTrigger.FULL_FLUSH,
                UNBOUNDED_MAX_MERGE_SEGMENTS
            )
        }
    }

    /** Returns true a segment was flushed or deletes were applied.  */
    @Throws(IOException::class)
    private fun doFlush(applyAllDeletes: Boolean): Boolean {
        if (tragedy.load() != null) {
            throw IllegalStateException(
                "this writer hit an unrecoverable error; cannot flush", tragedy.load()
            )
        }

        doBeforeFlush()
        testPoint("startDoFlush")
        var success = false
        try {
            if (infoStream.isEnabled("IW")) {
                infoStream.message("IW", "  start flush: applyAllDeletes=$applyAllDeletes")
                infoStream.message("IW", "  index before flush " + segString())
            }
            var anyChanges: Boolean

            // TODO synchronized is not supported in KMP, need to think what to do here
            //synchronized(fullFlushLock) {
            var flushSuccess = false
            try {
                anyChanges = (runBlocking { docWriter.flushAllThreads() } < 0)
                if (!anyChanges) {
                    // flushCount is incremented in flushAllThreads
                    flushCount.incrementAndFetch()
                }
                publishFlushedSegments(true)
                flushSuccess = true
            } finally {

                // TODO Thread is not supported in KMP, need to think what to do here
                //assert(java.lang.Thread.holdsLock(fullFlushLock))
                docWriter.finishFullFlush(flushSuccess)
                processEvents(false)
            }
            //}

            if (applyAllDeletes) {
                applyAllDeletesAndUpdates()
            }

            anyChanges = anyChanges or maybeMerge.getAndSet(false)

            // TODO synchronized is not supported in KMP, need to think what to do here
            //synchronized(this) {
            writeReaderPool(applyAllDeletes)
            doAfterFlush()
            success = true
            return anyChanges
            //}
        } catch (tragedy: Error) {
            tragicEvent(tragedy, "doFlush")
            throw tragedy
        } finally {
            if (!success) {
                if (infoStream.isEnabled("IW")) {
                    infoStream.message("IW", "hit exception during flush")
                }
                maybeCloseOnTragicEvent()
            }
        }
    }

    @Throws(IOException::class)
    private fun applyAllDeletesAndUpdates() {

        // TODO Thread is not supported in KMP, need to think what to do here
        //assert(java.lang.Thread.holdsLock(this) == false)
        flushDeletesCount.incrementAndFetch()
        if (infoStream.isEnabled("IW")) {
            infoStream.message(
                "IW",
                ("now apply all deletes for all segments buffered updates bytesUsed="
                        + bufferedUpdatesStream.ramBytesUsed()
                        + " reader pool bytesUsed="
                        + readerPool.ramBytesUsed())
            )
        }
        bufferedUpdatesStream.waitApplyAll(this)
    }

    // for testing only
    fun getDocsWriter(): DocumentsWriter {
        return docWriter
    }

    /** Expert: Return the number of documents currently buffered in RAM.  */
    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    fun numRamDocs(): Int {
        ensureOpen()
        return docWriter.numDocs
    }

    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    private fun ensureValidMerge(merge: MergePolicy.OneMerge) {
        for (info in merge.segments) {
            if (!segmentInfos.contains(info)) {
                throw MergePolicy.MergeException(
                    ("MergePolicy selected a segment ("
                            + info.info.name
                            + ") that is not in the current index "
                            + segString())
                )
            }
        }
    }

    /**
     * Carefully merges deletes and updates for the segments we just merged. This is tricky because,
     * although merging will clear all deletes (compacts the documents) and compact all the updates,
     * new deletes and updates may have been flushed to the segments since the merge was started. This
     * method "carries over" such new deletes and updates onto the newly merged segment, and saves the
     * resulting deletes and updates files (incrementing the delete and DV generations for
     * merge.info). If no deletes were flushed, no new deletes file is saved.
     */
    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    @Throws(IOException::class)
    private fun commitMergedDeletesAndUpdates(
        merge: MergePolicy.OneMerge, docMaps: Array<MergeState.DocMap>
    ): ReadersAndUpdates {
        mergeFinishedGen.incrementAndFetch()

        testPoint("startCommitMergeDeletes")

        val sourceSegments: MutableList<SegmentCommitInfo> = merge.segments

        if (infoStream.isEnabled("IW")) {
            infoStream.message("IW", "commitMergeDeletes " + segString(merge.segments))
        }

        // Carefully merge deletes that occurred after we
        // started merging:
        var minGen = Long.MAX_VALUE

        // Lazy init (only when we find a delete or update to carry over):
        val mergedDeletesAndUpdates: ReadersAndUpdates = getPooledInstance(merge.info!!, true)!!
        val numDeletesBefore: Int = mergedDeletesAndUpdates.delCount
        // field -> delGen -> dv field updates
        val mappedDVUpdates: MutableMap<String, LongObjectHashMap<DocValuesFieldUpdates>> = HashMap()

        var anyDVUpdates = false

        assert(sourceSegments.size == docMaps.size)
        for (i in sourceSegments.indices) {
            val info: SegmentCommitInfo = sourceSegments[i]
            min(info.bufferedDeletesGen, minGen).also { minGen = it }
            val maxDoc: Int = info.info.maxDoc()
            val rld: ReadersAndUpdates =
                checkNotNull(getPooledInstance(info, false)) { "seg=" + info.info.name }
            val segDocMap: MergeState.DocMap = docMaps[i]

            carryOverHardDeletes(
                mergedDeletesAndUpdates,
                maxDoc,
                merge.mergeReader[i].hardLiveDocs,
                rld.hardLiveDocs!!,
                segDocMap
            )

            // Now carry over all doc values updates that were resolved while we were merging, remapping
            // the docIDs to the newly merged docIDs.
            // We only carry over packets that finished resolving; if any are still running (concurrently)
            // they will detect that our merge completed
            // and re-resolve against the newly merged segment:
            val mergingDVUpdates: MutableMap<String, MutableList<DocValuesFieldUpdates>> =
                rld.getMergingDVUpdates()
            for (ent in mergingDVUpdates.entries) {
                val field: String = ent.key

                var mappedField: LongObjectHashMap<DocValuesFieldUpdates>? = mappedDVUpdates[field]
                if (mappedField == null) {
                    mappedField = LongObjectHashMap()
                    mappedDVUpdates[field] = mappedField
                }

                for (updates in ent.value) {
                    if (bufferedUpdatesStream.stillRunning(updates.delGen)) {
                        continue
                    }

                    // sanity check:
                    assert(field == updates.field)

                    var mappedUpdates: DocValuesFieldUpdates? = mappedField.get(updates.delGen)
                    if (mappedUpdates == null) {
                        mappedUpdates = when (updates.type) {
                            DocValuesType.NUMERIC -> NumericDocValuesFieldUpdates(
                                updates.delGen, updates.field, merge.info!!.info.maxDoc()
                            )

                            DocValuesType.BINARY -> BinaryDocValuesFieldUpdates(
                                updates.delGen, updates.field, merge.info!!.info.maxDoc()
                            )

                            DocValuesType.NONE, DocValuesType.SORTED, DocValuesType.SORTED_SET, DocValuesType.SORTED_NUMERIC -> throw AssertionError()
                            else -> throw AssertionError()
                        }
                        mappedField.put(updates.delGen, mappedUpdates)
                    }

                    val it: DocValuesFieldUpdates.Iterator = updates.iterator()
                    var doc: Int
                    while ((it.nextDoc().also { doc = it }) != DocIdSetIterator.NO_MORE_DOCS) {
                        val mappedDoc: Int = segDocMap.get(doc)
                        if (mappedDoc != -1) {
                            if (it.hasValue()) {
                                // not deleted
                                mappedUpdates.add(mappedDoc, it)
                            } else {
                                mappedUpdates.reset(mappedDoc)
                            }
                            anyDVUpdates = true
                        }
                    }
                }
            }
        }

        if (anyDVUpdates) {
            // Persist the merged DV updates onto the RAU for the merged segment:
            for (d in mappedDVUpdates.values) {
                for (updates in d.values()) {
                    updates.value!!.finish()
                    mergedDeletesAndUpdates.addDVUpdate(updates.value!!)
                }
            }
        }

        if (infoStream.isEnabled("IW")) {
            var msg = (mergedDeletesAndUpdates.delCount - numDeletesBefore).toString() + " new deletes"
            if (anyDVUpdates) {
                msg += " and " + mergedDeletesAndUpdates.numDVUpdates + " new field updates"
                msg += " (" + mergedDeletesAndUpdates.ramBytesUsed.load() + ") bytes"
            }
            msg += " since merge started"
            infoStream.message("IW", msg)
        }

        merge.info!!.bufferedDeletesGen = minGen

        return mergedDeletesAndUpdates
    }

    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    @Throws(IOException::class)
    private fun commitMerge(
        merge: MergePolicy.OneMerge,
        docMaps: Array<MergeState.DocMap>
    ): Boolean {
        merge.onMergeComplete()
        testPoint("startCommitMerge")

        if (tragedy.load() != null) {
            throw IllegalStateException(
                "this writer hit an unrecoverable error; cannot complete merge", tragedy.load()
            )
        }

        if (infoStream.isEnabled("IW")) {
            infoStream.message(
                "IW", "commitMerge: " + segString(merge.segments) + " index=" + segString()
            )
        }

        assert(merge.registerDone)

        // If merge was explicitly aborted, or, if rollback() or
        // rollbackTransaction() had been called since our merge
        // started (which results in an unqualified
        // deleter.refresh() call that will remove any index
        // file that current segments does not reference), we
        // abort this merge
        if (merge.isAborted) {
            if (infoStream.isEnabled("IW")) {
                infoStream.message("IW", "commitMerge: skip: it was aborted")
            }
            // In case we opened and pooled a reader for this
            // segment, drop it now.  This ensures that we close
            // the reader before trying to delete any of its
            // files.  This is not a very big deal, since this
            // reader will never be used by any NRT reader, and
            // another thread is currently running close(false)
            // so it will be dropped shortly anyway, but not
            // doing this  makes  MockDirWrapper angry in
            // TestNRTThreads (LUCENE-5434):
            runBlocking { readerPool.drop(merge.info!!) }
            // Safe: these files must exist:
            deleteNewFiles(merge.info!!.files())
            return false
        }

        val mergedUpdates: ReadersAndUpdates? =
            if (merge.info!!.info.maxDoc() == 0) null else commitMergedDeletesAndUpdates(merge, docMaps)

        // If the doc store we are using has been closed and
        // is in now compound format (but wasn't when we
        // started), then we will switch to the compound
        // format as well:
        assert(!segmentInfos.contains(merge.info))

        val allDeleted =
            merge.segments.isEmpty() || merge.info!!.info.maxDoc() == 0 || (mergedUpdates != null && isFullyDeleted(
                mergedUpdates
            ))

        if (infoStream.isEnabled("IW")) {
            if (allDeleted) {
                infoStream.message(
                    "IW", "merged segment " + merge.info + " is 100% deleted; skipping insert"
                )
            }
        }

        val dropSegment = allDeleted

        // If we merged no segments then we better be dropping
        // the new segment:
        assert(merge.segments.isNotEmpty() || dropSegment)

        assert(merge.info!!.info.maxDoc() != 0 || dropSegment)

        if (mergedUpdates != null) {
            var success = false
            try {
                if (dropSegment) {
                    mergedUpdates.dropChanges()
                }
                // Pass false for assertInfoLive because the merged
                // segment is not yet live (only below do we commit it
                // to the segmentInfos):
                release(mergedUpdates, false)
                success = true
            } finally {
                if (!success) {
                    mergedUpdates.dropChanges()
                    runBlocking { readerPool.drop(merge.info!!) }
                }
            }
        }

        // Must do this after readerPool.release, in case an
        // exception is hit e.g. writing the live docs for the
        // merge segment, in which case we need to abort the
        // merge:
        segmentInfos.applyMergeChanges(merge, dropSegment)

        // Now deduct the deleted docs that we just reclaimed from this
        // merge:
        val delDocCount: Int = if (dropSegment) {
            // if we drop the segment we have to reduce the pendingNumDocs by merge.totalMaxDocs since we
            // never drop
            // the docs when we apply deletes if the segment is currently merged.
            merge.totalMaxDoc
        } else {
            merge.totalMaxDoc - merge.info!!.info.maxDoc()
        }
        assert(delDocCount >= 0)
        adjustPendingNumDocs(-delDocCount.toLong())

        if (dropSegment) {
            assert(!segmentInfos.contains(merge.info))
            runBlocking { readerPool.drop(merge.info!!) }
            // Safe: these files must exist
            deleteNewFiles(merge.info!!.files())
        }

        AutoCloseable { this.checkpoint() }.use { `_` ->
            // Must close before checkpoint, otherwise IFD won't be
            // able to delete the held-open files from the merge
            // readers:
            closeMergeReaders(merge, false, dropSegment)
        }
        if (infoStream.isEnabled("IW")) {
            infoStream.message("IW", "after commitMerge: " + segString())
        }

        if (merge.maxNumSegments != UNBOUNDED_MAX_MERGE_SEGMENTS && !dropSegment) {
            // cascade the forceMerge:
            if (!segmentsToMerge.containsKey(merge.info)) {
                segmentsToMerge[merge.info!!] = false
            }
        }

        return true
    }

    @Throws(IOException::class)
    private fun handleMergeException(t: Throwable, merge: MergePolicy.OneMerge) {
        if (infoStream.isEnabled("IW")) {
            infoStream.message(
                "IW", "handleMergeException: merge=" + segString(merge.segments) + " exc=" + t
            )
        }

        // Set the exception on the merge, so if
        // forceMerge is waiting on us it sees the root
        // cause exception:
        merge.exception = t
        addMergeException(merge)

        if (t is MergePolicy.MergeAbortedException) {
            // We can ignore this exception (it happens when
            // deleteAll or rollback is called), unless the
            // merge involves segments from external directories,
            // in which case we must throw it so, for example, the
            // rollbackTransaction code in addIndexes* is
            // executed.
            if (merge.isExternal) { // TODO can we simplify this and just throw all the time this would
                // simplify this a lot
                throw t
            }
        } else {
            //checkNotNull(t)
            throw IOUtils.rethrowAlways(t)
        }
    }

    /**
     * Merges the indicated segments, replacing them in the stack with a single segment.
     *
     * @lucene.experimental
     */
    @Throws(IOException::class)
    protected fun merge(merge: MergePolicy.OneMerge) {
        var success = false

        val t0: Long = System.currentTimeMillis()

        val mergePolicy: MergePolicy = config.mergePolicy
        try {
            try {
                try {
                    mergeInit(merge)
                    if (infoStream.isEnabled("IW")) {
                        infoStream.message(
                            "IW",
                            "now merge\n  merge=" + segString(merge.segments) + "\n  index=" + segString()
                        )
                    }
                    mergeMiddle(merge, mergePolicy)
                    mergeSuccess(merge)
                    success = true
                } catch (t: Throwable) {
                    handleMergeException(t, merge)
                }
            } finally {

                // TODO synchronized is not supported in KMP, need to think what to do here
                //synchronized(this) {
                // Readers are already closed in commitMerge if we didn't hit
                // an exc:
                if (!success) {
                    closeMergeReaders(merge, suppressExceptions = true, droppedSegment = false)
                }
                mergeFinish(merge)
                if (!success) {
                    if (infoStream.isEnabled("IW")) {
                        infoStream.message("IW", "hit exception during merge")
                    }
                } else if (!merge.isAborted
                    && (merge.maxNumSegments != UNBOUNDED_MAX_MERGE_SEGMENTS || (!closed && !closing))
                ) {
                    // This merge (and, generally, any change to the
                    // segments) may now enable new merges, so we call
                    // merge policy & update pending merges.
                    updatePendingMerges(
                        mergePolicy,
                        MergeTrigger.MERGE_FINISHED,
                        merge.maxNumSegments
                    )
                }
                //}
            }
        } catch (t: Throwable) {
            // Important that tragicEvent is called after mergeFinish, else we hang
            // waiting for our merge thread to be removed from runningMerges:
            tragicEvent(t, "merge")
            throw t
        }

        if (merge.info != null && !merge.isAborted) {
            if (infoStream.isEnabled("IW")) {
                infoStream.message(
                    "IW",
                    ("merge time "
                            + (System.currentTimeMillis() - t0)
                            + " ms for "
                            + merge.info!!.info.maxDoc()
                            + " docs")
                )
            }
        }
    }

    /** Hook that's called when the specified merge is complete.  */
    protected fun mergeSuccess(merge: MergePolicy.OneMerge) {}

    @Throws(IOException::class)
    private fun abortOneMerge(merge: MergePolicy.OneMerge) {
        runBlocking { merge.setAborted() }
        closeMergeReaders(merge, suppressExceptions = true, droppedSegment = false)
    }

    /**
     * Checks whether this merge involves any segments already participating in a merge. If not, this
     * merge is "registered", meaning we record that its segments are now participating in a merge,
     * and true is returned. Else (the merge conflicts) false is returned.
     */
    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    @Throws(IOException::class)
    private fun registerMerge(merge: MergePolicy.OneMerge): Boolean {
        if (merge.registerDone) {
            return true
        }
        assert(merge.segments.isNotEmpty())

        if (!merges.areEnabled()) {
            abortOneMerge(merge)
            throw MergePolicy.MergeAbortedException("merge is aborted: " + segString(merge.segments))
        }

        var isExternal = false
        for (info in merge.segments) {
            if (mergingSegments.contains(info)) {
                if (infoStream.isEnabled("IW")) {
                    infoStream.message(
                        "IW",
                        ("reject merge "
                                + segString(merge.segments)
                                + ": segment "
                                + segString(info)
                                + " is already marked for merge")
                    )
                }
                return false
            }
            if (!segmentInfos.contains(info)) {
                if (infoStream.isEnabled("IW")) {
                    infoStream.message(
                        "IW",
                        ("reject merge "
                                + segString(merge.segments)
                                + ": segment "
                                + segString(info)
                                + " does not exist in live infos")
                    )
                }
                return false
            }
            if (info.info.dir !== directoryOrig) {
                isExternal = true
            }
            if (segmentsToMerge.containsKey(info)) {
                merge.maxNumSegments = mergeMaxNumSegments
            }
        }

        ensureValidMerge(merge)

        pendingMerges.add(merge)

        if (infoStream.isEnabled("IW")) {
            infoStream.message(
                "IW",
                ("add merge to pendingMerges: "
                        + segString(merge.segments)
                        + " [total "
                        + pendingMerges.size
                        + " pending]")
            )
        }

        merge.mergeGen = mergeGen
        merge.isExternal = isExternal

        // OK it does not conflict; now record that this merge
        // is running (while synchronized) to avoid race
        // condition where two conflicting merges from different
        // threads, start
        if (infoStream.isEnabled("IW")) {
            val builder = StringBuilder("registerMerge merging= [")
            for (info in mergingSegments) {
                builder.append(info.info.name).append(", ")
            }
            builder.append("]")
            // don't call mergingSegments.toString() could lead to ConcurrentModException
            // since merge updates the segments FieldInfos
            if (infoStream.isEnabled("IW")) {
                infoStream.message("IW", builder.toString())
            }
        }
        for (info in merge.segments) {
            if (infoStream.isEnabled("IW")) {
                infoStream.message("IW", "registerMerge info=" + segString(info))
            }
            mergingSegments.add(info)
        }

        assert(merge.estimatedMergeBytes == 0L)
        assert(merge.totalMergeBytes == 0L)
        for (info in merge.segments) {
            if (info.info.maxDoc() > 0) {
                val delCount = numDeletedDocs(info)
                assert(delCount <= info.info.maxDoc())
                val delRatio: Double = (delCount.toDouble()) / info.info.maxDoc()
                merge.estimatedMergeBytes += (info.sizeInBytes() * (1.0 - delRatio)).toLong()
                merge.totalMergeBytes += info.sizeInBytes()
            }
        }

        // Merge is now registered
        merge.registerDone = true

        return true
    }

    /**
     * Does initial setup for a merge, which is fast but holds the synchronized lock on IndexWriter
     * instance.
     */
    @Throws(IOException::class)
    fun mergeInit(merge: MergePolicy.OneMerge) {

        // TODO Thread is not supported in KMP, need to think what to do here
        //assert(java.lang.Thread.holdsLock(this) == false)
        // Make sure any deletes that must be resolved before we commit the merge are complete:
        bufferedUpdatesStream.waitApplyForMerge(merge.segments, this)

        var success = false
        try {
            _mergeInit(merge)
            success = true
        } finally {
            if (!success) {
                if (infoStream.isEnabled("IW")) {
                    infoStream.message("IW", "hit exception in mergeInit")
                }
                mergeFinish(merge)
            }
        }
    }

    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    @Throws(IOException::class)
    private fun _mergeInit(merge: MergePolicy.OneMerge) {
        testPoint("startMergeInit")

        assert(merge.registerDone)
        assert(merge.maxNumSegments == UNBOUNDED_MAX_MERGE_SEGMENTS || merge.maxNumSegments > 0)

        if (tragedy.load() != null) {
            throw IllegalStateException(
                "this writer hit an unrecoverable error; cannot merge", tragedy.load()
            )
        }

        if (merge.info != null) {
            // mergeInit already done
            return
        }

        runBlocking { merge.mergeInit() }

        if (merge.isAborted) {
            return
        }

        // TODO: in the non-pool'd case this is somewhat
        // wasteful, because we open these readers, close them,
        // and then open them again for merging.  Maybe  we
        // could pre-pool them somehow in that case...
        if (infoStream.isEnabled("IW")) {
            infoStream.message(
                "IW", "now apply deletes for " + merge.segments.size + " merging segments"
            )
        }

        // Must move the pending doc values updates to disk now, else the newly merged segment will not
        // see them:
        // TODO: we could fix merging to pull the merged DV iterator so we don't have to move these
        // updates to disk first, i.e. just carry them
        // in memory:
        if (runBlocking { readerPool.writeDocValuesUpdatesForMerge(merge.segments) }) {
            checkpoint()
        }
        var hasBlocks = false
        for (info in merge.segments) {
            if (info.info.hasBlocks) {
                hasBlocks = true
                break
            }
        }
        // Bind a new segment name here so even with
        // ConcurrentMergePolicy we keep deterministic segment
        // names.
        val mergeSegmentName = newSegmentName()
        // We set the min version to null for now, it will be set later by SegmentMerger
        val si =
            SegmentInfo(
                directoryOrig,
                Version.LATEST,
                null,
                mergeSegmentName,
                -1,
                false,
                hasBlocks,
                config.codec,
                mutableMapOf(),
                StringHelper.randomId(),
                mutableMapOf(),
                config.indexSort
            )
        val details: MutableMap<String, String> = HashMap()
        details["mergeMaxNumSegments"] = "" + merge.maxNumSegments
        details["mergeFactor"] = merge.segments.size.toString()
        setDiagnostics(si, SOURCE_MERGE, details)
        merge.setMergeInfo(
            SegmentCommitInfo(
                si,
                0,
                0,
                -1L,
                -1L,
                -1L,
                StringHelper.randomId()
            )
        )

        if (infoStream.isEnabled("IW")) {
            infoStream.message(
                "IW", "merge seg=" + merge.info!!.info.name + " " + segString(merge.segments)
            )
        }
    }

    /**
     * Does finishing for a merge, which is fast but holds the synchronized lock on IndexWriter
     * instance.
     */
    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    private fun mergeFinish(merge: MergePolicy.OneMerge) {
        // forceMerge, addIndexes or waitForMerges may be waiting
        // on merges to finish.

        // TODO notifyAll is not supported in KMP, need to think what to do here
        //(this as java.lang.Object).notifyAll()

        // It's possible we are called twice, eg if there was an
        // exception inside mergeInit
        if (merge.registerDone) {
            val sourceSegments: MutableList<SegmentCommitInfo> = merge.segments
            for (info in sourceSegments) {
                mergingSegments.remove(info)
            }
            merge.registerDone = false
        }

        runningMerges.remove(merge)
    }

    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    @Throws(IOException::class)
    private fun closeMergeReaders(
        merge: MergePolicy.OneMerge, suppressExceptions: Boolean, droppedSegment: Boolean
    ) {
        if (!merge.hasFinished()) {
            val drop = !suppressExceptions
            // first call mergeFinished before we potentially drop the reader and the last reference.
            merge.close(
                !suppressExceptions,
                droppedSegment
            ) { mr: MergePolicy.MergeReader ->
                if (merge.usesPooledReaders) {
                    val sr: SegmentReader = mr.reader!!
                    val rld: ReadersAndUpdates =
                        checkNotNull(getPooledInstance(sr.originalSegmentInfo, false))
                    if (drop) {
                        rld.dropChanges()
                    } else {
                        rld.dropMergingUpdates()
                    }
                    runBlocking {
                        rld.release(sr)
                        release(rld)
                        if (drop) {
                            readerPool.drop(rld.info)
                        }
                    }
                }
                deleter.decRef(mr.reader!!.segmentInfo.files())
            }
        } else {
            assert(
                merge.mergeReader.isEmpty()
            ) { "we are done but still have readers: " + merge.mergeReader }
            assert(suppressExceptions) { "can't be done and not suppressing exceptions" }
        }
    }

    @Throws(IOException::class)
    private fun countSoftDeletes(
        reader: CodecReader,
        wrappedLiveDocs: Bits?,
        hardLiveDocs: Bits?,
        softDeleteCounter: Counter,
        hardDeleteCounter: Counter
    ) {
        var hardDeleteCount = 0
        var softDeletesCount = 0
        val softDeletedDocs: DocIdSetIterator? =
            FieldExistsQuery.getDocValuesDocIdSetIterator(config.softDeletesField!!, reader)
        if (softDeletedDocs != null) {
            var docId: Int
            while ((softDeletedDocs.nextDoc()
                    .also { docId = it }) != DocIdSetIterator.NO_MORE_DOCS
            ) {
                if (wrappedLiveDocs == null || wrappedLiveDocs.get(docId)) {
                    if (hardLiveDocs == null || hardLiveDocs.get(docId)) {
                        softDeletesCount++
                    } else {
                        hardDeleteCount++
                    }
                }
            }
        }
        softDeleteCounter.addAndGet(softDeletesCount.toLong())
        hardDeleteCounter.addAndGet(hardDeleteCount.toLong())
    }

    @Throws(IOException::class)
    private fun assertSoftDeletesCount(reader: CodecReader, expectedCount: Int): Boolean {
        val count: Counter = Counter.newCounter(false)
        val hardDeletes: Counter = Counter.newCounter(false)
        countSoftDeletes(reader, reader.liveDocs, null, count, hardDeletes)
        assert(
            count.get() == expectedCount.toLong()
        ) { "soft-deletes count mismatch expected: " + expectedCount + " but actual: " + count.get() }
        return true
    }

    /**
     * Does the actual (time-consuming) work of the merge, but without holding synchronized lock on
     * IndexWriter instance
     */
    @Throws(IOException::class)
    private fun mergeMiddle(
        merge: MergePolicy.OneMerge,
        mergePolicy: MergePolicy
    ): Int {
        testPoint("mergeMiddleStart")
        merge.checkAborted()

        val mergeDirectory: Directory = mergeScheduler.wrapForMerge(merge, directory)
        val context = IOContext(merge.storeMergeInfo)

        val dirWrapper = TrackingDirectoryWrapper(mergeDirectory)

        if (infoStream.isEnabled("IW")) {
            infoStream.message("IW", "merging " + segString(merge.segments))
        }

        // This is try/finally to make sure merger's readers are
        // closed:
        var success = false
        try {
            merge.initMergeReaders { sci: SegmentCommitInfo ->
                val rld: ReadersAndUpdates = getPooledInstance(sci, true)!!
                rld.setIsMerging()

                // TODO synchronized is not supported in KMP, need to think what to do here
                //synchronized(this) {
                return@initMergeReaders runBlocking {
                    rld.getReaderForMerge(context) { mr: MergePolicy.MergeReader ->
                        deleter.incRef(
                            mr.reader!!.segmentInfo.files()
                        )
                    }
                }
                //}
            }
            // Let the merge wrap readers
            var mergeReaders: MutableList<CodecReader> = mutableListOf()
            val softDeleteCount: Counter = Counter.newCounter(false)
            for (mergeReader in merge.mergeReader) {
                val reader: SegmentReader = mergeReader.reader!!
                var wrappedReader: CodecReader = merge.wrapForMerge(reader)
                validateMergeReader(wrappedReader)
                if (softDeletesEnabled) {
                    if (reader !== wrappedReader) { // if we don't have a wrapped reader we won't preserve any
                        // soft-deletes
                        val hardLiveDocs: Bits? = mergeReader.hardLiveDocs
                        // we only need to do this accounting if we have mixed deletes
                        if (hardLiveDocs != null) {
                            val wrappedLiveDocs: Bits? = wrappedReader.liveDocs
                            val hardDeleteCounter: Counter = Counter.newCounter(false)
                            countSoftDeletes(
                                wrappedReader, wrappedLiveDocs!!, hardLiveDocs, softDeleteCount, hardDeleteCounter
                            )
                            val hardDeleteCount: Int = Math.toIntExact(hardDeleteCounter.get())
                            // Wrap the wrapped reader again if we have excluded some hard-deleted docs
                            if (hardDeleteCount > 0) {
                                val liveDocs: Bits? =
                                    if (wrappedLiveDocs == null)
                                        hardLiveDocs
                                    else
                                        object : Bits {
                                            override fun get(index: Int): Boolean {
                                                return hardLiveDocs.get(index) && wrappedLiveDocs.get(index)
                                            }

                                            override fun length(): Int {
                                                return hardLiveDocs.length()
                                            }
                                        }
                                wrappedReader =
                                    FilterCodecReader.wrapLiveDocs(
                                        wrappedReader, liveDocs!!, wrappedReader.numDocs() - hardDeleteCount
                                    )
                            }
                        } else {
                            val carryOverSoftDeletes: Int =
                                reader.segmentInfo.getSoftDelCount() - wrappedReader.numDeletedDocs()
                            assert(carryOverSoftDeletes >= 0) { "carry-over soft-deletes must be positive" }
                            assert(assertSoftDeletesCount(wrappedReader, carryOverSoftDeletes))
                            softDeleteCount.addAndGet(carryOverSoftDeletes.toLong())
                        }
                    }
                }
                mergeReaders.add(wrappedReader)
            }

            val intraMergeExecutor: Executor = mergeScheduler.getIntraMergeExecutor(merge)

            var reorderDocMaps: Array<MergeState.DocMap>? = null
            // Don't reorder if an explicit sort is configured.
            val hasIndexSort = config.indexSort != null
            // Don't reorder if blocks can't be identified using the parent field.
            val hasBlocksButNoParentField =
                mergeReaders
                    .map { obj: LeafReader -> obj.metaData }
                    .any(LeafMetaData::hasBlocks)
                        && mergeReaders
                    .map { obj: CodecReader -> obj.fieldInfos }
                    .map { obj: FieldInfos -> obj.parentField }
                    .any { obj: Any -> Objects.isNull(obj) }

            if (!hasIndexSort && !hasBlocksButNoParentField) {
                // Create a merged view of the input segments. This effectively does the merge.
                val mergedView: CodecReader =
                    SlowCompositeCodecReaderWrapper.wrap(mergeReaders)
                val docMap: DocMap? =
                    merge.reorder(mergedView, directory, intraMergeExecutor)
                if (docMap != null) {
                    reorderDocMaps =
                        kotlin.arrayOfNulls<MergeState.DocMap>(mergeReaders.size) as Array<MergeState.DocMap>
                    var docBase = 0
                    var i = 0
                    for (reader in mergeReaders) {
                        val currentDocBase = docBase
                        reorderDocMaps[i] =
                            MergeState.DocMap { docID: Int ->
                                Objects.checkIndex(docID, reader.maxDoc())
                                docMap.oldToNew(currentDocBase + docID)
                            }
                        i++
                        docBase += reader.maxDoc()
                    }
                    // This makes merging more expensive as it disables some bulk merging optimizations, so
                    // only do this if a non-null DocMap is returned.
                    mergeReaders = mutableListOf(
                        SortingCodecReader.wrap(
                            mergedView,
                            docMap,
                            null
                        )
                    )
                }
            }

            val merger =
                SegmentMerger(
                    mergeReaders,
                    merge.info!!.info,
                    infoStream,
                    dirWrapper,
                    globalFieldNumberMap,
                    context,
                    intraMergeExecutor
                )
            merge.info!!.setSoftDelCount(Math.toIntExact(softDeleteCount.get()))
            merge.checkAborted()

            val mergeState: MergeState = merger.mergeState
            val docMaps: Array<MergeState.DocMap>?
            if (reorderDocMaps == null) {
                docMaps = mergeState.docMaps
            } else {
                // Since the reader was reordered, we passed a merged view to MergeState and from its
                // perspective there is a single input segment to the merge and the
                // SlowCompositeCodecReaderWrapper is effectively doing the merge.
                assert(
                    mergeState.docMaps!!.size == 1
                ) { "Got " + mergeState.docMaps.size + " docMaps, but expected 1" }
                val compactionDocMap: MergeState.DocMap = mergeState.docMaps[0]
                docMaps = kotlin.arrayOfNulls<MergeState.DocMap>(reorderDocMaps.size) as Array<MergeState.DocMap>
                for (i in docMaps.indices) {
                    val reorderDocMap: MergeState.DocMap = reorderDocMaps[i]
                    docMaps[i] = MergeState.DocMap { docID: Int ->
                        compactionDocMap.get(
                            reorderDocMap.get(docID)
                        )
                    }
                }
            }

            merge.mergeStartNS = System.nanoTime()

            // This is where all the work happens:
            if (merger.shouldMerge()) {
                merger.merge()
            }

            assert(mergeState.segmentInfo === merge.info!!.info)
            merge.info!!.info.setFiles(HashSet(dirWrapper.createdFiles))
            val codec: Codec = config.codec
            if (infoStream.isEnabled("IW")) {
                if (merger.shouldMerge()) {
                    var pauseInfo: String =
                        merge.mergeProgress.pauseTimes.entries
                            .filter { e: MutableMap.MutableEntry<MergePolicy.OneMergeProgress.PauseReason, Long> -> e.value > 0 }
                            .joinToString(separator = ", ") { e: MutableMap.MutableEntry<MergePolicy.OneMergeProgress.PauseReason, Long> ->
                                "${e.value / TimeUnit.SECONDS.toNanos(1).toDouble()} sec ${e.key.name.lowercase()}"
                            }
                    if (!pauseInfo.isEmpty()) {
                        pauseInfo = " ($pauseInfo)"
                    }

                    val t1: Long = System.nanoTime()
                    val sec: Double =
                        (t1 - merge.mergeStartNS) / TimeUnit.SECONDS.toNanos(1).toDouble()
                    val segmentMB: Double = (merge.info!!.sizeInBytes() / 1024.0 / 1024.0)
                    infoStream.message(
                        "IW",
                        (("merge codec=$codec")
                                + (" maxDoc=" + merge.info!!.info.maxDoc())
                                + ("; merged segment has "
                                + (if (mergeState.mergeFieldInfos!!.hasTermVectors()) "vectors" else "no vectors"))
                                + ("; " + (if (mergeState.mergeFieldInfos!!.hasNorms()) "norms" else "no norms"))
                                + ("; "
                                + (if (mergeState.mergeFieldInfos!!.hasDocValues()) "docValues" else "no docValues"))
                                + ("; " + (if (mergeState.mergeFieldInfos!!.hasProx()) "prox" else "no prox"))
                                + ("; " + (if (mergeState.mergeFieldInfos!!.hasFreq()) "freqs" else "no freqs"))
                                + ("; " + (if (mergeState.mergeFieldInfos!!.hasPointValues()) "points" else "no points"))
                                + ("; "
                                + "$sec sec $pauseInfo to merge segment [$segmentMB MB, ${segmentMB / sec} MB/sec]"))
                    )
                } else {
                    infoStream.message("IW", "skip merging fully deleted segments")
                }
            }

            if (!merger.shouldMerge()) {
                // Merge would produce a 0-doc segment, so we do nothing except commit the merge to remove
                // all the 0-doc segments that we "merged":
                assert(merge.info!!.info.maxDoc() == 0)
                success = commitMerge(merge, docMaps!!)
                return 0
            }

            assert(merge.info!!.info.maxDoc() > 0)

            // Very important to do this before opening the reader
            // because codec must know if prox was written for
            // this segment:
            val useCompoundFile: Boolean

            // TODO synchronized is not supported in KMP, need to think what to do here
            //synchronized(this) { // Guard segmentInfos
            useCompoundFile = mergePolicy.useCompoundFile(segmentInfos, merge.info!!, this)
            //}

            if (useCompoundFile) {
                success = false

                val filesToRemove: MutableCollection<String> = merge.info!!.files()
                // NOTE: Creation of the CFS file must be performed with the original
                // directory rather than with the merging directory, so that it is not
                // subject to merge throttling.
                val trackingCFSDir = TrackingDirectoryWrapper(directory)
                try {
                    createCompoundFile(
                        infoStream,
                        trackingCFSDir,
                        merge.info!!.info,
                        context
                    ) { files: MutableCollection<String> ->
                        this.deleteNewFiles(files)
                    }
                    success = true
                } catch (t: Throwable) {

                    // TODO synchronized is not supported in KMP, need to think what to do here
                    //synchronized(this) {
                    if (merge.isAborted) {
                        // This can happen if rollback is called while we were building
                        // our CFS -- fall through to logic below to remove the non-CFS
                        // merged files:
                        if (infoStream.isEnabled("IW")) {
                            infoStream.message(
                                "IW", "hit merge abort exception creating compound file during merge"
                            )
                        }
                        return 0
                    } else {
                        handleMergeException(t, merge)
                    }
                    //}
                } finally {
                    if (!success) {
                        if (infoStream.isEnabled("IW")) {
                            infoStream.message("IW", "hit exception creating compound file during merge")
                        }
                        // Safe: these files must exist
                        deleteNewFiles(merge.info!!.files())
                    }
                }

                // So that, if we hit exc in deleteNewFiles (next)
                // or in commitMerge (later), we close the
                // per-segment readers in the finally clause below:
                success = false

                // TODO synchronized is not supported in KMP, need to think what to do here
                //synchronized(this) {
                // delete new non cfs files directly: they were never
                // registered with IFD
                deleteNewFiles(filesToRemove)
                if (merge.isAborted) {
                    if (infoStream.isEnabled("IW")) {
                        infoStream.message("IW", "abort merge after building CFS")
                    }
                    // Safe: these files must exist
                    deleteNewFiles(merge.info!!.files())
                    return 0
                }
                //}

                merge.info!!.info.useCompoundFile = true
            } else {
                // So that, if we hit exc in commitMerge (later),
                // we close the per-segment readers in the finally
                // clause below:
                success = false
            }

            merge.setMergeInfo(merge.info!!)

            // Have codec write SegmentInfo.  Must do this after
            // creating CFS so that 1) .si isn't slurped into CFS,
            // and 2) .si reflects useCompoundFile=true change
            // above:
            var success2 = false
            try {
                codec.segmentInfoFormat().write(directory, merge.info!!.info, context)
                success2 = true
            } finally {
                if (!success2) {
                    // Safe: these files must exist
                    deleteNewFiles(merge.info!!.files())
                }
            }

            // TODO: ideally we would freeze merge.info here!!
            // because any changes after writing the .si will be
            // lost...
            if (infoStream.isEnabled("IW")) {
                infoStream.message(
                    "IW",
                    "merged segment size=${merge.info!!.sizeInBytes() / 1024.0 / 1024.0} MB vs estimate=${merge.estimatedMergeBytes / 1024.0 / 1024.0} MB"
                )
            }

            val mergedSegmentWarmer: IndexReaderWarmer? = config.mergedSegmentWarmer
            if (readerPool.isReaderPoolingEnabled && mergedSegmentWarmer != null) {
                val rld: ReadersAndUpdates = getPooledInstance(merge.info!!, true)!!
                val sr: SegmentReader = rld.getReader(IOContext.DEFAULT)
                try {
                    mergedSegmentWarmer.warm(sr)
                } finally {

                    // TODO synchronized is not supported in KMP, need to think what to do here
                    //synchronized(this) {
                    runBlocking { rld.release(sr) }
                    release(rld)
                    //}
                }
            }

            if (!commitMerge(merge, docMaps!!)) {
                // commitMerge will return false if this merge was
                // aborted
                return 0
            }

            success = true
        } finally {
            // Readers are already closed in commitMerge if we didn't hit
            // an exc:
            if (!success) {
                closeMergeReaders(merge, suppressExceptions = true, droppedSegment = false)
            }
        }

        return merge.info!!.info.maxDoc()
    }

    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    private fun addMergeException(merge: MergePolicy.OneMerge) {
        checkNotNull(merge.exception)
        if (!mergeExceptions.contains(merge) && mergeGen == merge.mergeGen) {
            mergeExceptions.add(merge)
        }
    }

    // For test purposes.
    fun getBufferedDeleteTermsSize(): Int {
        return docWriter.bufferedDeleteTermsSize
    }

    // utility routines for tests
    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    fun newestSegment(): SegmentCommitInfo? {
        return if (segmentInfos.size() > 0) segmentInfos.info(segmentInfos.size() - 1) else null
    }

    /**
     * Returns a string description of all segments, for debugging.
     *
     * @lucene.internal
     */
    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    fun segString(): String {
        return segString(segmentInfos)
    }

    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    fun segString(infos: Iterable<SegmentCommitInfo>): String {
        return infos.joinToString(" ") { segString(it) }
    }

    /**
     * Returns a string description of the specified segment, for debugging.
     *
     * @lucene.internal
     */
    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    private fun segString(info: SegmentCommitInfo): String {
        return info.toString(numDeletedDocs(info) - info.getDelCount(softDeletesEnabled))
    }

    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    private fun doWait() {
        // NOTE: the callers of this method should in theory
        // be able to do simply wait(), but, as a defense
        // against thread timing hazards where notifyAll()
        // fails to be called, we wait for at most 1 second
        // and then return so caller can check if wait
        // conditions are satisfied:
        try {
            /*(this as java.lang.Object).wait(1000)*/
            runBlocking {
                delay(1.seconds)
            }
        } catch (ie: CancellationException) {
            throw ThreadInterruptedException(ie)
        }
    }

    // called only from assert
    @Throws(IOException::class)
    private fun filesExist(toSync: SegmentInfos): Boolean {
        val files: MutableCollection<String> = toSync.files(false)
        for (fileName in files) {
            // If this trips it means we are missing a call to
            // .checkpoint somewhere, because by the time we
            // are called, deleter should know about every
            // file referenced by the current head
            // segmentInfos:
            assert(deleter.exists(fileName)) { "IndexFileDeleter doesn't know about file $fileName" }
        }
        return true
    }

    // For infoStream output
    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    fun toLiveInfos(sis: SegmentInfos): SegmentInfos {
        val newSIS = SegmentInfos(sis.indexCreatedVersionMajor)
        val liveSIS: MutableMap<SegmentCommitInfo, SegmentCommitInfo> = HashMap()
        for (info in segmentInfos) {
            liveSIS[info] = info
        }
        for (info in sis) {
            var info: SegmentCommitInfo = info
            val liveInfo: SegmentCommitInfo? = liveSIS[info]
            if (liveInfo != null) {
                info = liveInfo
            }
            newSIS.add(info)
        }

        return newSIS
    }

    /**
     * Walk through all files referenced by the current segmentInfos and ask the Directory to sync
     * each file, if it wasn't already. If that succeeds, then we prepare a new segments_N file but do
     * not fully commit it.
     */
    @OptIn(ExperimentalAtomicApi::class)
    @Throws(IOException::class)
    private fun startCommit(toSync: SegmentInfos) {
        testPoint("startStartCommit")
        assert(pendingCommit == null)

        if (tragedy.load() != null) {
            throw IllegalStateException(
                "this writer hit an unrecoverable error; cannot commit", tragedy.load()
            )
        }

        try {
            if (infoStream.isEnabled("IW")) {
                infoStream.message("IW", "startCommit(): start")
            }

            // TODO synchronized is not supported in KMP, need to think what to do here
            //synchronized(this) {
            check(lastCommitChangeCount <= changeCount.load()) { "lastCommitChangeCount=$lastCommitChangeCount,changeCount=$changeCount" }
            if (pendingCommitChangeCount == lastCommitChangeCount) {
                if (infoStream.isEnabled("IW")) {
                    infoStream.message("IW", "  skip startCommit(): no changes pending")
                }
                try {
                    deleter.decRef(filesToCommit!!)
                } finally {
                    filesToCommit = null
                }
                return
            }

            if (infoStream.isEnabled("IW")) {
                infoStream.message(
                    "IW",
                    ("startCommit index="
                            + segString(toLiveInfos(toSync))
                            + " changeCount="
                            + changeCount)
                )
            }
            assert(filesExist(toSync))
            //}

            testPoint("midStartCommit")

            var pendingCommitSet = false

            try {
                testPoint("midStartCommit2")

                // TODO synchronized is not supported in KMP, need to think what to do here
                //synchronized(this) {
                assert(pendingCommit == null)
                assert(segmentInfos.generation == toSync.generation)

                // Exception here means nothing is prepared
                // (this method unwinds everything it did on
                // an exception)
                toSync.prepareCommit(directory)
                if (infoStream.isEnabled("IW")) {
                    infoStream.message(
                        "IW",
                        ("startCommit: wrote pending segments file \""
                                + IndexFileNames.fileNameFromGeneration(
                            IndexFileNames.PENDING_SEGMENTS, "", toSync.generation
                        )
                                + "\"")
                    )
                }

                pendingCommitSet = true
                pendingCommit = toSync
                //}

                // This call can take a long time -- 10s of seconds
                // or more.  We do it without syncing on this:
                var success = false
                val filesToSync: MutableCollection<String>
                try {
                    filesToSync = toSync.files(false)
                    directory.sync(filesToSync)
                    success = true
                } finally {
                    if (!success) {
                        pendingCommitSet = false
                        pendingCommit = null
                        toSync.rollbackCommit(directory)
                    }
                }

                if (infoStream.isEnabled("IW")) {
                    infoStream.message("IW", "done all syncs: $filesToSync")
                }

                testPoint("midStartCommitSuccess")
            } catch (t: Throwable) {
                // TODO synchronized is not supported in KMP, need to think what to do here
                //synchronized(this) {
                if (!pendingCommitSet) {
                    if (infoStream.isEnabled("IW")) {
                        infoStream.message("IW", "hit exception committing segments file")
                    }
                    try {
                        // Hit exception
                        deleter.decRef(filesToCommit!!)
                    } catch (t1: Throwable) {
                        t.addSuppressed(t1)
                    } finally {
                        filesToCommit = null
                    }
                }
                //}
                throw t
            } finally {
                // TODO synchronizedis not supported in KMP, need to think what to do here
                //synchronized(this) {
                // Have our master segmentInfos record the
                // generations we just prepared.  We do this
                // on error or success so we don't
                // double-write a segments_N file.
                segmentInfos.updateGeneration(toSync)
                //}
            }
        } catch (tragedy: Error) {
            tragicEvent(tragedy, "startCommit")
            throw tragedy
        }
        testPoint("finishStartCommit")
    }

    /**
     * If [DirectoryReader.open] has been called (ie, this writer is in near
     * real-time mode), then after a merge completes, this class can be invoked to warm the reader on
     * the newly merged segment, before the merge commits. This is not required for near real-time
     * search, but will reduce search latency on opening a new near real-time reader after a merge
     * completes.
     *
     * @lucene.experimental
     *
     * **NOTE**: [.warm] is called before any deletes have been carried
     * over to the merged segment.
     */
    fun interface IndexReaderWarmer {
        /**
         * Invoked on the [LeafReader] for the newly merged segment, before that segment is made
         * visible to near-real-time readers.
         */
        @Throws(IOException::class)
        fun warm(reader: LeafReader)
    }

    /**
     * This method should be called on a tragic event ie. if a downstream class of the writer hits an
     * unrecoverable exception. This method does not rethrow the tragic event exception.
     *
     *
     * Note: This method will not close the writer but can be called from any location without
     * respecting any lock order
     *
     * @lucene.internal
     */
    @OptIn(ExperimentalAtomicApi::class)
    fun onTragicEvent(tragedy: Throwable, location: String) {
        // This is not supposed to be tragic: IW is supposed to catch this and
        // ignore, because it means we asked the merge to abort:
        assert(tragedy !is MergePolicy.MergeAbortedException)
        // How can it be a tragedy when nothing happened
        //checkNotNull(tragedy)
        if (infoStream.isEnabled("IW")) {
            infoStream.message(
                "IW", "hit tragic " + tragedy::class.simpleName + " inside " + location
            )
        }
        this.tragedy.compareAndSet(null, tragedy) // only set it once
    }

    /**
     * This method set the tragic exception unless it's already set and closes the writer if
     * necessary. Note this method will not rethrow the throwable passed to it.
     */
    @Throws(IOException::class)
    private fun tragicEvent(tragedy: Throwable, location: String) {
        try {
            onTragicEvent(tragedy, location)
        } finally {
            maybeCloseOnTragicEvent()
        }
    }

    @Throws(IOException::class)
    private fun maybeCloseOnTragicEvent() {
        // We cannot hold IW's lock here else it can lead to deadlock:

        // TODO Thread is not supported in KMP, need to think what to do here
        //assert(java.lang.Thread.holdsLock(this) == false)
        //assert(java.lang.Thread.holdsLock(fullFlushLock) == false)
        // if we are already closed (e.g. called by rollback), this will be a no-op.
        if (this.tragedy.load() != null && shouldClose(false)) {
            rollbackInternal()
        }
    }

    /**
     * If this `IndexWriter` was closed as a side-effect of a tragic exception, e.g. disk full
     * while flushing a new segment, this returns the root cause exception. Otherwise (no tragic
     * exception has occurred) it returns null.
     */
    fun getTragicException(): Throwable? {
        return tragedy.load()
    }

    /** Returns `true` if this `IndexWriter` is still open.  */
    fun isOpen(): Boolean {
        return !closing && !closed
    }

    // Used for testing.  Current points:
    //   startDoFlush
    //   startCommitMerge
    //   startStartCommit
    //   midStartCommit
    //   midStartCommit2
    //   midStartCommitSuccess
    //   finishStartCommit
    //   startCommitMergeDeletes
    //   startMergeInit
    //   DocumentsWriterPerThread addDocuments start
    private fun testPoint(message: String) {
        if (enableTestPoints) {
            assert(
                infoStream.isEnabled("TP") // don't enable unless you need them.
            )
            infoStream.message("TP", message)
        }
    }

    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    fun nrtIsCurrent(infos: SegmentInfos): Boolean {
        ensureOpen()
        val isCurrent =
            infos.version == segmentInfos.version && !docWriter.anyChanges() && !bufferedUpdatesStream.any() && !readerPool.anyDocValuesChanges()
        if (infoStream.isEnabled("IW")) {
            if (!isCurrent) {
                infoStream.message(
                    "IW",
                    ("nrtIsCurrent: infoVersion matches: "
                            + (infos.version == segmentInfos.version)
                            + "; DW changes: "
                            + docWriter.anyChanges()
                            + "; BD changes: "
                            + bufferedUpdatesStream.any())
                )
            }
        }
        return isCurrent
    }

    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    fun isClosed(): Boolean {
        return closed
    }

    fun isDeleterClosed(): Boolean {
        return deleter.isClosed
    }

    /**
     * Expert: remove any index files that are no longer used.
     *
     *
     * IndexWriter normally deletes unused files itself, during indexing. However, on Windows,
     * which disallows deletion of open files, if there is a reader open on the index then those files
     * cannot be deleted. This is fine, because IndexWriter will periodically retry the deletion.
     *
     *
     * However, IndexWriter doesn't try that often: only on open, close, flushing a new segment,
     * and finishing a merge. If you don't do any of these actions with your IndexWriter, you'll see
     * the unused files linger. If that's a problem, call this method to delete them (once you've
     * closed the open readers that were preventing their deletion).
     *
     *
     * In addition, you can call this method to delete unreferenced index commits. This might be
     * useful if you are using an [IndexDeletionPolicy] which holds onto index commits until
     * some criteria are met, but those commits are no longer needed. Otherwise, those commits will be
     * deleted the next time commit() is called.
     */
    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    @Throws(IOException::class)
    fun deleteUnusedFiles() {
        // TODO: should we remove this method now that it's the Directory's job to retry deletions
        // Except, for the super expert IDP use case
        // it's still needed
        ensureOpen(false)
        deleter.revisitPolicy()
    }

    /**
     * Tries to delete the given files if unreferenced
     *
     * @param files the files to delete
     * @throws IOException if an [IOException] occurs
     * @see IndexFileDeleter.deleteNewFiles
     */
    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    @Throws(IOException::class)
    private fun deleteNewFiles(files: MutableCollection<String>) {
        deleter.deleteNewFiles(files)
    }

    /** Cleans up residuals from a segment that could not be entirely flushed due to an error  */
    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    @Throws(IOException::class)
    private fun flushFailed(info: SegmentInfo) {
        // TODO: this really should be a tragic
        var files = try {
            info.files()
        } catch (ise: IllegalStateException) {
            // OK
            null
        }
        if (files != null) {
            deleter.deleteNewFiles(files)
        }
    }

    /**
     * Publishes the flushed segment, segment-private deletes (if any) and its associated global
     * delete (if present) to IndexWriter. The actual publishing operation is synced on `IW ->
     * BDS` so that the [SegmentInfo]'s delete generation is always
     * GlobalPacket_deleteGeneration + 1
     *
     * @param forced if `true` this call will block on the ticket queue if the lock is held
     * by another thread. if `false` the call will try to acquire the queue lock and
     * exits if it's held by another thread.
     */
    @Throws(IOException::class)
    private fun publishFlushedSegments(forced: Boolean) {
        docWriter.purgeFlushTickets(
            forced
        ) { ticket: DocumentsWriterFlushQueue.FlushTicket ->
            val newSegment: DocumentsWriterPerThread.FlushedSegment? = ticket.flushedSegment
            val bufferedUpdates: FrozenBufferedUpdates = ticket.getFrozenUpdates()
            ticket.markPublished()
            if (newSegment == null) { // this is a flushed global deletes package - not a segments
                if (bufferedUpdates != null && bufferedUpdates.any()) { // TODO why can this be null
                    publishFrozenUpdates(bufferedUpdates)
                    if (infoStream.isEnabled("IW")) {
                        infoStream.message("IW", "flush: push buffered updates: $bufferedUpdates")
                    }
                }
            } else {
                checkNotNull(newSegment.segmentInfo)
                if (infoStream.isEnabled("IW")) {
                    infoStream.message(
                        "IW", "publishFlushedSegment seg-private updates=" + newSegment.segmentUpdates
                    )
                }
                if (newSegment.segmentUpdates != null && infoStream.isEnabled("DW")) {
                    infoStream.message(
                        "IW", "flush: push buffered seg private updates: " + newSegment.segmentUpdates
                    )
                }
                // now publish!
                publishFlushedSegment(
                    newSegment.segmentInfo,
                    newSegment.fieldInfos,
                    newSegment.segmentUpdates!!,
                    bufferedUpdates,
                    newSegment.sortMap
                )
            }
        }
    }

    /**
     * Record that the files referenced by this [SegmentInfos] are still in use.
     *
     * @lucene.internal
     */
    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    @Throws(IOException::class)
    fun incRefDeleter(segmentInfos: SegmentInfos) {
        ensureOpen()
        deleter.incRef(segmentInfos, false)
        if (infoStream.isEnabled("IW")) {
            infoStream.message(
                "IW",
                ("incRefDeleter for NRT reader version="
                        + segmentInfos.version
                        + " segments="
                        + segString(segmentInfos))
            )
        }
    }

    /**
     * Record that the files referenced by this [SegmentInfos] are no longer in use. Only call
     * this if you are sure you previously called [.incRefDeleter].
     *
     * @lucene.internal
     */
    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    @Throws(IOException::class)
    fun decRefDeleter(segmentInfos: SegmentInfos) {
        ensureOpen()
        deleter.decRef(segmentInfos)
        if (infoStream.isEnabled("IW")) {
            infoStream.message(
                "IW",
                ("decRefDeleter for NRT reader version="
                        + segmentInfos.version
                        + " segments="
                        + segString(segmentInfos))
            )
        }
    }

    /**
     * Processes all events and might trigger a merge if the given seqNo is negative
     *
     * @param seqNo if the seqNo is less than 0 this method will process events otherwise it's a
     * no-op.
     * @return the given seqId inverted if negative.
     */
    @Throws(IOException::class)
    private fun maybeProcessEvents(seqNo: Long): Long {
        var seqNo = seqNo
        if (seqNo < 0) {
            seqNo = -seqNo
            processEvents(true)
        }
        return seqNo
    }

    @OptIn(ExperimentalAtomicApi::class)
    @Throws(IOException::class)
    private fun processEvents(triggerMerge: Boolean) {
        if (tragedy.load() == null) {
            eventQueue.processEvents()
        }
        if (triggerMerge) {
            maybeMerge(
                config.mergePolicy,
                MergeTrigger.SEGMENT_FLUSH,
                UNBOUNDED_MAX_MERGE_SEGMENTS
            )
        }
    }

    /**
     * Interface for internal atomic events. See [DocumentsWriter] for details. Events are
     * executed concurrently and no order is guaranteed. Each event should only rely on the
     * serializability within its process method. All actions that must happen before or after a
     * certain action must be encoded inside the [.process] method.
     */
    internal fun interface Event {
        /**
         * Processes the event. This method is called by the [IndexWriter] passed as the first
         * argument.
         *
         * @param writer the [IndexWriter] that executes the event.
         * @throws IOException if an [IOException] occurs
         */
        @Throws(IOException::class)
        fun process(writer: IndexWriter)
    }

    /**
     * Anything that will add N docs to the index should reserve first to make sure it's allowed. This
     * will throw `IllegalArgumentException` if it's not allowed.
     */
    private fun reserveDocs(addedNumDocs: Long) {
        assert(addedNumDocs >= 0)
        if (adjustPendingNumDocs(addedNumDocs) > actualMaxDocs) {
            // Reserve failed: put the docs back and throw exc:
            adjustPendingNumDocs(-addedNumDocs)
            tooManyDocs(addedNumDocs)
        }
    }

    /**
     * Does a best-effort check, that the current index would accept this many additional docs, but
     * does not actually reserve them.
     *
     * @throws IllegalArgumentException if there would be too many docs
     */
    @OptIn(ExperimentalAtomicApi::class)
    private fun testReserveDocs(addedNumDocs: Long) {
        assert(addedNumDocs >= 0)
        if (pendingNumDocs.load() + addedNumDocs > actualMaxDocs) {
            tooManyDocs(addedNumDocs)
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    private fun tooManyDocs(addedNumDocs: Long) {
        assert(addedNumDocs >= 0)
        throw IllegalArgumentException(
            ("number of documents in the index cannot exceed "
                    + actualMaxDocs
                    + " (current document count is "
                    + pendingNumDocs.load()
                    + "; added numDocs is "
                    + addedNumDocs
                    + ")")
        )
    }

    /**
     * Returns the number of documents in the index including documents are being added (i.e.,
     * reserved).
     *
     * @lucene.experimental
     */
    @OptIn(ExperimentalAtomicApi::class)
    fun getPendingNumDocs(): Long {
        return pendingNumDocs.load()
    }

    /**
     * Returns the highest [sequence number](#sequence_number) across all completed
     * operations, or 0 if no operations have finished yet. Still in-flight operations (in other
     * threads) are not counted until they finish.
     *
     * @lucene.experimental
     */
    fun getMaxCompletedSequenceNumber(): Long {
        ensureOpen()
        return docWriter.maxCompletedSequenceNumber
    }

    @OptIn(ExperimentalAtomicApi::class)
    private fun adjustPendingNumDocs(numDocs: Long): Long {
        val count: Long = pendingNumDocs.addAndFetch(numDocs)
        assert(count >= 0) { "pendingNumDocs is negative: $count" }
        return count
    }

    @Throws(IOException::class)
    fun isFullyDeleted(readersAndUpdates: ReadersAndUpdates): Boolean {
        if (readersAndUpdates.isFullyDeleted) {

            // TODO Thread is not supported in KMP, need to think what to do here
            //assert(java.lang.Thread.holdsLock(this))
            return !readersAndUpdates.keepFullyDeletedSegment(config.mergePolicy)
        }
        return false
    }

    /**
     * Returns the number of deletes a merge would claim back if the given segment is merged.
     *
     * @see MergePolicy.numDeletesToMerge
     * @param info the segment to get the number of deletes for
     * @lucene.experimental
     */
    @Throws(IOException::class)
    override fun numDeletesToMerge(info: SegmentCommitInfo): Int {
        ensureOpen(false)
        validate(info)
        val mergePolicy: MergePolicy = config.mergePolicy
        val rld: ReadersAndUpdates? = getPooledInstance(info, false)
        val numDeletesToMerge: Int = rld?.numDeletesToMerge(mergePolicy)
            ?: // if we don't have a  pooled instance lets just return the hard deletes, this is safe!
            info.delCount
        assert(
            numDeletesToMerge <= info.info.maxDoc()
        ) { "numDeletesToMerge: " + numDeletesToMerge + " > maxDoc: " + info.info.maxDoc() }
        return numDeletesToMerge
    }

    @Throws(IOException::class)
    fun release(readersAndUpdates: ReadersAndUpdates) {
        release(readersAndUpdates, true)
    }

    @Throws(IOException::class)
    private fun release(readersAndUpdates: ReadersAndUpdates, assertLiveInfo: Boolean) {

        val released: Boolean = runBlocking { readerPool.release(readersAndUpdates, assertLiveInfo) }

        // TODO Thread is not supported in KMP, need to think what to do here
        //assert(java.lang.Thread.holdsLock(this))
        if (released) {
            // if we write anything here we have to hold the lock otherwise IDF will delete files
            // underneath us

            // TODO Thread is not supported in KMP, need to think what to do here
            //assert(java.lang.Thread.holdsLock(this))
            checkpointNoSIS()
        }
    }

    fun getPooledInstance(
        info: SegmentCommitInfo,
        create: Boolean
    ): ReadersAndUpdates? {
        ensureOpen(false)
        return readerPool.get(info, create)
    }

    // FrozenBufferedUpdates
    /**
     * Translates a frozen packet of delete term/query, or doc values updates, into their actual
     * docIDs in the index, and applies the change. This is a heavy operation and is done concurrently
     * by incoming indexing threads. This method will return immediately without blocking if another
     * thread is currently applying the package. In order to ensure the packet has been applied,
     * [IndexWriter.forceApply] must be called.
     */
    @Throws(IOException::class)
    fun tryApply(updates: FrozenBufferedUpdates): Boolean {
        if (updates.tryLock()) {
            try {
                forceApply(updates)
                return true
            } finally {
                updates.unlock()
            }
        }
        return false
    }

    /**
     * Translates a frozen packet of delete term/query, or doc values updates, into their actual
     * docIDs in the index, and applies the change. This is a heavy operation and is done concurrently
     * by incoming indexing threads.
     */
    @OptIn(ExperimentalAtomicApi::class)
    @Throws(IOException::class)
    fun forceApply(updates: FrozenBufferedUpdates) {
        updates.lock()
        try {
            if (updates.isApplied()) {
                // already done
                return
            }
            val startNS: Long = System.nanoTime()

            assert(updates.any())

            val seenSegments: MutableSet<SegmentCommitInfo> = HashSet()

            var iter = 0
            var totalSegmentCount = 0
            var totalDelCount: Long = 0

            var finished = false

            // Optimistic concurrency: assume we are free to resolve the deletes against all current
            // segments in the index, despite that
            // concurrent merges are running.  Once we are done, we check to see if a merge completed
            // while we were running.  If so, we must retry
            // resolving against the newly merged segment(s).  Eventually no merge finishes while we were
            // running and we are done.
            while (true) {
                val messagePrefix: String = if (iter == 0) {
                    ""
                } else {
                    "iter $iter"
                }

                val iterStartNS: Long = System.nanoTime()

                val mergeGenStart: Long = mergeFinishedGen.load()

                val delFiles: MutableSet<String> = HashSet()
                val segStates: Array<BufferedUpdatesStream.SegmentState>

                // TODO synchronized is not supported in KMP, need to think what to do here
                //synchronized(this) {
                val infos: MutableList<SegmentCommitInfo> = getInfosToApply(updates) ?: break

                for (info in infos) {
                    delFiles.addAll(info.files())
                }

                // Must open while holding IW lock so that e.g. segments are not merged
                // away, dropped from 100% deletions, etc., before we can open the readers
                segStates = openSegmentStates(infos, seenSegments, updates.delGen())

                if (segStates.isEmpty()) {
                    if (infoStream.isEnabled("BD")) {
                        infoStream.message("BD", "packet matches no segments")
                    }
                    break
                }

                if (infoStream.isEnabled("BD")) {
                    infoStream.message(
                        "BD",
                        messagePrefix + "now apply del packet ($this) to ${segStates.size} segments, mergeGen $mergeGenStart"
                    )
                }

                totalSegmentCount += segStates.size

                // Important, else IFD may try to delete our files while we are still using them,
                // if e.g. a merge finishes on some of the segments we are resolving on:
                deleter.incRef(delFiles)
                //}

                val success = AtomicBoolean(false)
                val delCount: Long
                AutoCloseable { finishApply(segStates, success.load(), delFiles) }.use { finalizer ->
                    //checkNotNull(finalizer) // access the finalizer to prevent a warning
                    // don't hold IW monitor lock here so threads are free concurrently resolve
                    // deletes/updates:
                    delCount = runBlocking { updates.apply(segStates) }
                    success.store(true)
                }
                // Since we just resolved some more deletes/updates, now is a good time to write them:
                writeSomeDocValuesUpdates()

                // It's OK to add this here, even if the while loop retries, because delCount only includes
                // newly
                // deleted documents, on the segments we didn't already do in previous iterations:
                totalDelCount += delCount

                if (infoStream.isEnabled("BD")) {
                    infoStream.message(
                        "BD",
                        messagePrefix + "done inner apply del packet ($this) to ${segStates.size} segments; $delCount new deletes/updates; took ${
                            (System.nanoTime() - iterStartNS) / TimeUnit.SECONDS.toNanos(
                                1
                            ).toDouble()
                        } sec"
                    )
                }
                if (updates.privateSegment != null) {
                    // No need to retry for a segment-private packet: the merge that folds in our private
                    // segment already waits for all deletes to
                    // be applied before it kicks off, so this private segment must already not be in the set
                    // of merging segments

                    break
                }

                // Must sync on writer here so that IW.mergeCommit is not running concurrently, so that if
                // we exit, we know mergeCommit will succeed
                // in pulling all our delGens into a merge:

                // TODO synchronized is not supported in KMP, need to think what to do here
                //synchronized(this) {
                val mergeGenCur: Long = mergeFinishedGen.load()
                if (mergeGenCur == mergeGenStart) {
                    // Must do this while still holding IW lock else a merge could finish and skip carrying
                    // over our updates:

                    // Record that this packet is finished:

                    bufferedUpdatesStream.finished(updates)

                    finished = true

                    // No merge finished while we were applying, so we are done!
                    break
                }
                //}

                if (infoStream.isEnabled("BD")) {
                    infoStream.message("BD", messagePrefix + "concurrent merges finished; move to next iter")
                }

                // A merge completed while we were running.  In this case, that merge may have picked up
                // some of the updates we did, but not
                // necessarily all of them, so we cycle again, re-applying all our updates to the newly
                // merged segment.
                iter++
            }

            if (!finished) {
                // Record that this packet is finished:
                bufferedUpdatesStream.finished(updates)
            }

            if (infoStream.isEnabled("BD")) {
                var message =
                    "done apply del packet ($this) to $totalSegmentCount segments; $totalDelCount new deletes/updates; took ${
                        (System.nanoTime() - startNS) / TimeUnit.SECONDS.toNanos(1).toDouble()
                    } sec"
                if (iter > 0) {
                    message += "; " + (iter + 1) + " iters due to concurrent merges"
                }
                message += "; " + bufferedUpdatesStream.pendingUpdatesCount + " packets remain"
                infoStream.message("BD", message)
            }
        } finally {
            updates.unlock()
        }
    }

    /**
     * Returns the [SegmentCommitInfo] that this packet is supposed to apply its deletes to, or
     * null if the private segment was already merged away.
     */
    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    private fun getInfosToApply(updates: FrozenBufferedUpdates): MutableList<SegmentCommitInfo> {
        val infos: MutableList<SegmentCommitInfo>?
        if (updates.privateSegment != null) {
            if (segmentInfos.contains(updates.privateSegment)) {
                infos = mutableListOf(updates.privateSegment)
            } else {
                if (infoStream.isEnabled("BD")) {
                    infoStream.message("BD", "private segment already gone; skip processing updates")
                }
                infos = null
            }
        } else {
            infos = segmentInfos.asList()
        }
        return infos!!
    }

    @OptIn(ExperimentalAtomicApi::class)
    @Throws(IOException::class)
    private fun finishApply(
        segStates: Array<BufferedUpdatesStream.SegmentState>,
        success: Boolean,
        delFiles: MutableSet<String>
    ) {
        // TODO synchronized is not supported in KMP, need to think what to do here
        //synchronized(this) {
        var result: BufferedUpdatesStream.ApplyDeletesResult
        try {
            result = closeSegmentStates(segStates, success)
        } finally {
            // Matches the incRef we did above, but we must do the decRef after closing segment states
            // else
            // IFD can't delete still-open files
            deleter.decRef(delFiles)
        }

        if (result.anyDeletes) {
            maybeMerge.store(true)
            checkpoint()
        }
        if (result.allDeleted != null) {
            if (infoStream.isEnabled("IW")) {
                infoStream.message("IW", "drop 100% deleted segments: " + segString(result.allDeleted))
            }
            for (info in result.allDeleted) {
                dropDeletedSegment(info)
            }
            checkpoint()
        }
        //}
    }

    /** Close segment states previously opened with openSegmentStates.  */
    @Throws(IOException::class)
    private fun closeSegmentStates(
        segStates: Array<BufferedUpdatesStream.SegmentState>, success: Boolean
    ): BufferedUpdatesStream.ApplyDeletesResult {
        var allDeleted: MutableList<SegmentCommitInfo>? = null
        var totDelCount: Long = 0
        try {
            for (segState in segStates) {
                if (success) {
                    totDelCount += (segState.rld.delCount - segState.startDelCount).toLong()
                    val fullDelCount: Int = segState.rld.delCount
                    assert(
                        fullDelCount <= segState.rld.info.info.maxDoc()
                    ) { fullDelCount.toString() + " > " + segState.rld.info.info.maxDoc() }
                    if (segState.rld.isFullyDeleted && !config.mergePolicy
                            .keepFullyDeletedSegment { segState.reader }
                    ) {
                        if (allDeleted == null) {
                            allDeleted = mutableListOf()
                        }
                        allDeleted.add(segState.reader.originalSegmentInfo)
                    }
                }
            }
        } finally {
            IOUtils.close(*segStates)
        }
        if (infoStream.isEnabled("BD")) {
            infoStream.message(
                "BD",
                ("closeSegmentStates: "
                        + totDelCount
                        + " new deleted documents; pool "
                        + bufferedUpdatesStream.pendingUpdatesCount
                        + " packets; bytesUsed="
                        + readerPool.ramBytesUsed())
            )
        }

        return BufferedUpdatesStream.ApplyDeletesResult(totDelCount > 0, allDeleted!!)
    }

    /** Opens SegmentReader and inits SegmentState for each segment.  */
    @Throws(IOException::class)
    private fun openSegmentStates(
        infos: MutableList<SegmentCommitInfo>,
        alreadySeenSegments: MutableSet<SegmentCommitInfo>,
        delGen: Long
    ): Array<BufferedUpdatesStream.SegmentState> {
        val segStates: MutableList<BufferedUpdatesStream.SegmentState> = mutableListOf()
        try {
            for (info in infos) {
                if (info.bufferedDeletesGen <= delGen && !alreadySeenSegments.contains(info)) {
                    segStates.add(
                        BufferedUpdatesStream.SegmentState(
                            getPooledInstance(info, true)!!,
                            { readersAndUpdates: ReadersAndUpdates ->
                                this.release(readersAndUpdates)
                            },
                            info
                        )
                    )
                    alreadySeenSegments.add(info)
                }
            }
        } catch (t: Throwable) {
            try {
                IOUtils.close(segStates)
            } catch (t1: Throwable) {
                t.addSuppressed(t1)
            }
            throw t
        }

        return segStates.toTypedArray<BufferedUpdatesStream.SegmentState>()
    }

    /** Tests should override this to enable test points. Default is `false`.  */
    protected fun isEnableTestPoints(): Boolean {
        return false
    }

    private fun validate(info: SegmentCommitInfo) {
        require(info.info.dir === directoryOrig) { "SegmentCommitInfo must be from the same directory" }
    }

    /** Tests should use this method to snapshot the current segmentInfos to have a consistent view  */
    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    fun cloneSegmentInfos(): SegmentInfos {
        return segmentInfos.clone()
    }

    /**
     * Returns accurate [DocStats] for this writer. The numDoc for instance can change after
     * maxDoc is fetched that causes numDocs to be greater than maxDoc which makes it hard to get
     * accurate document stats from IndexWriter.
     */
    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    fun getDocStats(): DocStats {
        ensureOpen()
        var numDocs: Int = docWriter.numDocs
        var maxDoc = numDocs
        for (info in segmentInfos) {
            maxDoc += info.info.maxDoc()
            numDocs += info.info.maxDoc() - numDeletedDocs(info)
        }
        assert(maxDoc >= numDocs) { "maxDoc is less than numDocs: $maxDoc < $numDocs" }
        return DocStats(maxDoc, numDocs)
    }

    /** DocStats for this index  */
    class DocStats(
        /**
         * The total number of docs in this index, counting docs not yet flushed (still in the RAM
         * buffer), and also counting deleted docs. **NOTE:** buffered deletions are not counted. If
         * you really need these to be counted you should call [IndexWriter.commit] first.
         */
        val maxDoc: Int,
        /**
         * The total number of docs in this index, counting docs not yet flushed (still in the RAM
         * buffer), but not counting deleted docs.
         */
        val numDocs: Int
    )

    private data class IndexWriterMergeSource(val writer: IndexWriter) :
        MergeScheduler.MergeSource {
        override val nextMerge: MergePolicy.OneMerge?
            get() {
                val nextMerge: MergePolicy.OneMerge? = writer.getNextMerge()
                if (nextMerge != null) {
                    if (writer.mergeScheduler.verbose()) {
                        writer.mergeScheduler.message(
                            "  checked out merge " + writer.segString(nextMerge.segments)
                        )
                    }
                }
                return nextMerge
            }

        override fun onMergeFinished(merge: MergePolicy.OneMerge) {
            writer.mergeFinish(merge)
        }

        override fun hasPendingMerges(): Boolean {
            return writer.hasPendingMerges()
        }

        @Throws(IOException::class)
        override fun merge(merge: MergePolicy.OneMerge) {

            // TODO Thread is not supported in KMP, need to think what to do here
            //assert(java.lang.Thread.holdsLock(writer) == false)
            writer.merge(merge)
        }

        override fun toString(): String {
            return writer.segString()
        }
    }

    private inner class Merges {
        private var mergesEnabled = true

        fun areEnabled(): Boolean {

            // TODO Thread is not supported in KMP, need to think what to do here
            //assert(java.lang.Thread.holdsLock(this@IndexWriter))
            return mergesEnabled
        }

        fun disable() {

            // TODO Thread is not supported in KMP, need to think what to do here
            //assert(java.lang.Thread.holdsLock(this@IndexWriter))
            mergesEnabled = false
        }

        fun enable() {
            ensureOpen()

            // TODO Thread is not supported in KMP, need to think what to do here
            //assert(java.lang.Thread.holdsLock(this@IndexWriter))
            mergesEnabled = true
        }
    }

    /**
     * Constructs a new IndexWriter per the settings given in `conf`. If you want to make
     * "live" changes to this writer instance, use [.getConfig].
     *
     *
     * **NOTE:** after ths writer is created, the given configuration instance cannot be passed
     * to another writer.
     *
     * @param d the index directory. The index is either created or appended according `
     * conf.openMode`.
     * @param conf the configuration settings according to which IndexWriter should be initialized.
     * @throws IOException if the directory cannot be read/written to, or if it does not exist and
     * `conf.openMode` is `OpenMode.APPEND` or if there is any other
     * low-level IO error
     */
    init {
        enableTestPoints = isEnableTestPoints()
        conf.setIndexWriter(this) // prevent reuse by other instances
        config = conf
        infoStream = config.infoStream
        softDeletesEnabled = config.softDeletesField != null
        eventListener = config.indexWriterEventListener
        // obtain the write.lock. If the user configured a timeout,
        // we wrap with a sleeper and this might take some time.
        writeLock = d.obtainLock(WRITE_LOCK_NAME)

        var success = false
        try {
            directoryOrig = d
            directory = LockValidatingDirectoryWrapper(d, writeLock!!)
            mergeScheduler = config.mergeScheduler
            mergeScheduler.initialize(infoStream, directoryOrig)
            val mode: OpenMode = config.openMode
            val indexExists: Boolean
            val create: Boolean
            when (mode) {
                OpenMode.CREATE -> {
                    indexExists = DirectoryReader.indexExists(directory)
                    create = true
                }

                OpenMode.APPEND -> {
                    indexExists = true
                    create = false
                }

                else -> {
                    // CREATE_OR_APPEND - create only if an index does not exist
                    indexExists = DirectoryReader.indexExists(directory)
                    create = !indexExists
                }
            }

            // If index is too old, reading the segments will throw
            // IndexFormatTooOldException.
            val files: Array<String> = directory.listAll()

            // Set up our initial SegmentInfos:
            val commit: IndexCommit? = config.indexCommit

            // Set up our initial SegmentInfos:
            val reader = commit?.reader

            if (create) {
                if (config.indexCommit != null) {
                    // We cannot both open from a commit point and create:
                    require(mode != OpenMode.CREATE) { "cannot use IndexWriterConfig.setIndexCommit() with OpenMode.CREATE" }
                    throw IllegalArgumentException(
                        "cannot use IndexWriterConfig.setIndexCommit() when index has no commit"
                    )
                }

                // Try to read first.  This is to allow create
                // against an index that's currently open for
                // searching.  In this case we write the next
                // segments_N file with no segments:
                val sis = SegmentInfos(config.createdVersionMajor)
                if (indexExists) {
                    val previous: SegmentInfos =
                        SegmentInfos.readLatestCommit(directory)
                    sis.updateGenerationVersionAndCounter(previous)
                }
                segmentInfos = sis
                rollbackSegments = segmentInfos.createBackupSegmentInfos()

                // Record that we have a change (zero out all
                // segments) pending:
                changed()
            } else if (reader != null) {
                require(reader.segmentInfos.indexCreatedVersionMajor >= Version.MIN_SUPPORTED_MAJOR) {
                    ("createdVersionMajor must be >= "
                            + Version.MIN_SUPPORTED_MAJOR
                            + ", got: "
                            + reader.segmentInfos.indexCreatedVersionMajor)
                }

                // Init from an existing already opened NRT or non-NRT reader:
                require(reader.directory() === commit.directory) { "IndexCommit's reader must have the same directory as the IndexCommit" }

                require(reader.directory() === directoryOrig) { "IndexCommit's reader must have the same directory passed to IndexWriter" }

                require(reader.segmentInfos.lastGeneration != 0L) { "index must already have an initial commit to open from reader" }

                // Must clone because we don't want the incoming NRT reader to "see" any changes this writer
                // now makes:
                segmentInfos = reader.segmentInfos.clone()

                val lastCommit: SegmentInfos
                try {
                    lastCommit = SegmentInfos.readCommit(
                        directoryOrig,
                        segmentInfos.segmentsFileName!!
                    )
                } catch (ioe: IOException) {
                    throw IllegalArgumentException(
                        ("the provided reader is stale: its prior commit file \""
                                + segmentInfos.segmentsFileName
                                + "\" is missing from index"),
                        ioe
                    )
                }

                if (reader.writer != null) {
                    // The old writer better be closed (we have the write lock now!):

                    assert(reader.writer.closed)

                    // In case the old writer wrote further segments (which we are now dropping),
                    // update SIS metadata so we remain write-once:
                    segmentInfos.updateGenerationVersionAndCounter(reader.writer.segmentInfos)
                    lastCommit.updateGenerationVersionAndCounter(reader.writer.segmentInfos)
                }

                rollbackSegments = lastCommit.createBackupSegmentInfos()
            } else {
                // Init from either the latest commit point, or an explicit prior commit point:

                val lastSegmentsFile: String =
                    SegmentInfos.getLastCommitSegmentsFileName(files) ?: throw IndexNotFoundException(
                        "no segments* file found in " + directory + ": files: " + files.contentToString()
                    )

                // Do not use SegmentInfos.read(Directory) since the spooky
                // retrying it does is not necessary here (we hold the write lock):
                segmentInfos = SegmentInfos.readCommit(directoryOrig, lastSegmentsFile)

                if (commit != null) {
                    // Swap out all segments, but keep metadata in
                    // SegmentInfos, like version & generation, to
                    // preserve write-once.  This is important if
                    // readers are open against the future commit
                    // points.
                    require(commit.directory === directoryOrig) {
                        ("IndexCommit's directory doesn't match my directory, expected="
                                + directoryOrig
                                + ", got="
                                + commit.directory)
                    }

                    val oldInfos: SegmentInfos = SegmentInfos.readCommit(directoryOrig, commit.segmentsFileName!!)
                    segmentInfos.replace(oldInfos)
                    changed()

                    if (infoStream.isEnabled("IW")) {
                        infoStream.message(
                            "IW", "init: loaded commit \"" + commit.segmentsFileName + "\""
                        )
                    }
                }

                rollbackSegments = segmentInfos.createBackupSegmentInfos()
            }

            commitUserData = HashMap<String, String>(segmentInfos.userData).entries

            pendingNumDocs.store(segmentInfos.totalMaxDoc().toLong())

            // start with previous field numbers, but new FieldInfos
            // NOTE: this is correct even for an NRT reader because we'll pull FieldInfos even for the
            // un-committed segments:
            globalFieldNumberMap = getFieldNumberMap()
            require(
                !(!create && conf.parentField != null && !globalFieldNumberMap.fieldNames
                    .isEmpty() && !globalFieldNumberMap.fieldNames
                    .contains(conf.parentField))
            ) { "can't add a parent field to an already existing index without a parent field" }

            validateIndexSort()

            config.flushPolicy.init(config)
            bufferedUpdatesStream = BufferedUpdatesStream(infoStream)
            docWriter =
                DocumentsWriter(
                    flushNotifications,
                    segmentInfos.indexCreatedVersionMajor,
                    pendingNumDocs,
                    enableTestPoints,
                    { this.newSegmentName() },
                    config,
                    directoryOrig,
                    directory,
                    globalFieldNumberMap
                )
            readerPool =
                ReaderPool(
                    directory,
                    directoryOrig,
                    segmentInfos,
                    globalFieldNumberMap,
                    { bufferedUpdatesStream.completedDelGen },
                    infoStream,
                    conf.softDeletesField!!,
                    reader!!
                )
            if (config.readerPooling) {
                readerPool.enableReaderPooling()
            }

            // Default deleter (for backwards compatibility) is
            // KeepOnlyLastCommitDeleter:

            // Sync'd is silly here, but IFD asserts we sync'd on the IW instance:

            // TODO synchronized is not supported in KMP, need to think what to do here
            //synchronized(this) {
            deleter =
                IndexFileDeleter(
                    files,
                    directoryOrig,
                    directory,
                    config.indexDeletionPolicy,
                    segmentInfos,
                    infoStream,
                    this,
                    indexExists,
                    reader != null
                )
            // We incRef all files when we return an NRT reader from IW, so all files must exist even in
            // the NRT case:
            assert(create || filesExist(segmentInfos))
            //}

            if (deleter.startingCommitDeleted) {
                // Deletion policy deleted the "head" commit point.
                // We have to mark ourself as changed so that if we
                // are closed w/o any further changes we write a new
                // segments_N file.
                changed()
            }

            if (reader != null) {
                // We always assume we are carrying over incoming changes when opening from reader:
                segmentInfos.changed()
                changed()
            }

            if (infoStream.isEnabled("IW")) {
                infoStream.message("IW", "init: create=$create reader=$reader")
                messageState()
            }

            success = true
        } finally {
            if (!success) {
                if (infoStream.isEnabled("IW")) {
                    infoStream.message("IW", "init: hit exception on init; releasing write lock")
                }
                IOUtils.closeWhileHandlingException(writeLock)
                writeLock = null
            }
        }
    }

    companion object {
        /**
         * Hard limit on maximum number of documents that may be added to the index. If you try to add
         * more than this you'll hit `IllegalArgumentException`.
         */
        // We defensively subtract 128 to be well below the lowest
        // ArrayUtil.MAX_ARRAY_LENGTH on "typical" JVMs.  We don't just use
        // ArrayUtil.MAX_ARRAY_LENGTH here because this can vary across JVMs:
        const val MAX_DOCS: Int = Int.MAX_VALUE - 128

        /** Maximum value of the token position in an indexed field.  */
        const val MAX_POSITION: Int = Int.MAX_VALUE - 128

        // Use package-private instance var to enforce the limit so testing
        // can use less electricity:
        var actualMaxDocs: Int = MAX_DOCS
            private set

        /** Used only for testing.  */
        fun setMaxDocs(maxDocs: Int) {
            require(maxDocs <= MAX_DOCS) { "maxDocs must be <= IndexWriter.MAX_DOCS=$MAX_DOCS; got: $maxDocs" }
            actualMaxDocs = maxDocs
        }

        private const val UNBOUNDED_MAX_MERGE_SEGMENTS = -1

        /** Name of the write lock in the index.  */
        const val WRITE_LOCK_NAME: String = "write.lock"

        /** Key for the source of a segment in the [diagnostics][SegmentInfo.getDiagnostics].  */
        const val SOURCE: String = "source"

        /** Source of a segment which results from a merge of other segments.  */
        const val SOURCE_MERGE: String = "merge"

        /** Source of a segment which results from a flush.  */
        const val SOURCE_FLUSH: String = "flush"

        /** Source of a segment which results from a call to [.addIndexes].  */
        const val SOURCE_ADDINDEXES_READERS: String = "addIndexes(CodecReader...)"

        /**
         * Absolute hard maximum length for a term, in bytes once encoded as UTF8. If a term arrives from
         * the analyzer longer than this length, an `IllegalArgumentException` is thrown and a
         * message is printed to infoStream, if set (see [ ][IndexWriterConfig.setInfoStream]).
         */
        const val MAX_TERM_LENGTH: Int = ByteBlockPool.BYTE_BLOCK_SIZE - 2

        /** Maximum length string for a stored field.  */
        val MAX_STORED_STRING_LENGTH: Int =
            ArrayUtil.MAX_ARRAY_LENGTH / UnicodeUtil.MAX_UTF8_BYTES_PER_CHAR

        /** Returns true if `indexSort` is a prefix of `otherSort`.  */
        fun isCongruentSort(
            indexSort: Sort,
            otherSort: Sort
        ): Boolean {
            val fields1: Array<SortField> = indexSort.sort
            val fields2: Array<SortField> = otherSort.sort
            if (fields1.size > fields2.size) {
                return false
            }
            return fields1.toList() == fields2.toList().take(fields1.size)
        }

        // reads latest field infos for the commit
        // this is used on IW init and addIndexes(Dir) to create/update the global field map.
        // TODO: fix tests abusing this method!
        @Throws(IOException::class)
        fun readFieldInfos(si: SegmentCommitInfo): FieldInfos {
            val codec: Codec = si.info.codec
            val reader: FieldInfosFormat = codec.fieldInfosFormat()

            if (si.hasFieldUpdates()) {
                // there are updates, we read latest (always outside of CFS)
                val segmentSuffix = si.fieldInfosGen.toString(Character.MAX_RADIX.coerceIn(2, 36))
                return reader.read(si.info.dir, si.info, segmentSuffix, IOContext.READONCE)
            } else if (si.info.useCompoundFile) {
                // cfs
                codec.compoundFormat().getCompoundReader(si.info.dir, si.info).use { cfs ->
                    return reader.read(cfs, si.info, "", IOContext.READONCE)
                }
            } else {
                // no cfs
                return reader.read(si.info.dir, si.info, "", IOContext.READONCE)
            }
        }

        /**
         * This method carries over hard-deleted documents that are applied to the source segment during a
         * merge.
         */
        @Throws(IOException::class)
        private fun carryOverHardDeletes(
            mergedReadersAndUpdates: ReadersAndUpdates,
            maxDoc: Int,
            prevHardLiveDocs: Bits?,  // the hard deletes when the merge reader was pulled
            currentHardLiveDocs: Bits,  // the current hard deletes
            segDocMap: MergeState.DocMap
        ) {
            // if we mix soft and hard deletes we need to make sure that we only carry over deletes
            // that were not deleted before. Otherwise the segDocMap doesn't contain a mapping.
            // yet this is also required if any MergePolicy modifies the liveDocs since this is
            // what the segDocMap is build on.

            val carryOverDelete: (Int) -> Boolean =
                { docId: Int ->
                    segDocMap.get(docId) != -1 && !currentHardLiveDocs.get(docId)
                }
            if (prevHardLiveDocs != null) {
                // If we had deletions on starting the merge we must
                // still have deletions now:
                //checkNotNull(currentHardLiveDocs)
                assert(prevHardLiveDocs.length() == maxDoc)
                assert(currentHardLiveDocs.length() == maxDoc)

                // There were deletes on this segment when the merge
                // started.  The merge has collapsed away those
                // deletes, but, if new deletes were flushed since
                // the merge started, we must now carefully keep any
                // newly flushed deletes but mapping them to the new
                // docIDs.

                // Since we copy-on-write, if any new deletes were
                // applied after merging has started, we can just
                // check if the before/after liveDocs have changed.
                // If so, we must carefully merge the liveDocs one
                // doc at a time:
                if (currentHardLiveDocs !== prevHardLiveDocs) {
                    // This means this segment received new deletes
                    // since we started the merge, so we
                    // must merge them:
                    for (j in 0..<maxDoc) {
                        if (!prevHardLiveDocs.get(j)) {
                            // if the document was deleted before, it better still be deleted!
                            assert(!currentHardLiveDocs.get(j))
                        } else if (carryOverDelete(j)) {
                            // the document was deleted while we were merging:
                            runBlocking { mergedReadersAndUpdates.delete(segDocMap.get(j)) }
                        }
                    }
                }
            } else if (currentHardLiveDocs != null) {
                assert(currentHardLiveDocs.length() == maxDoc)
                // This segment had no deletes before but now it
                // does:
                for (j in 0..<maxDoc) {
                    if (carryOverDelete(j)) {
                        runBlocking { mergedReadersAndUpdates.delete(segDocMap.get(j)) }
                    }
                }
            }
        }

        fun setDiagnostics(info: SegmentInfo, source: String) {
            setDiagnostics(info, source, null)
        }

        private fun setDiagnostics(
            info: SegmentInfo,
            source: String,
            details: MutableMap<String, String>?
        ) {
            val diagnostics: MutableMap<String, String> = HashMap()
            diagnostics["source"] = source
            diagnostics["lucene.version"] = Version.LATEST.toString()
            diagnostics["os"] = Constants.OS_NAME
            diagnostics["os.arch"] = Constants.OS_ARCH
            diagnostics["os.version"] = Constants.OS_VERSION
            //diagnostics.put("java.runtime.version", java.lang.Runtime.version().toString()) //TODO should we put kotlin version?
            diagnostics["java.vendor"] = Constants.JAVA_VENDOR
            diagnostics["timestamp"] = System.currentTimeMillis() /*java.time.Instant.now().toEpochMilli()*/.toString()
            if (details != null) {
                diagnostics.putAll(details)
            }
            info.setDiagnostics(diagnostics)
        }

        /**
         * NOTE: this method creates a compound file for all files returned by info.files(). While,
         * generally, this may include separate norms and deletion files, this SegmentInfo must not
         * reference such files when this method is called, because they are not allowed within a compound
         * file.
         */
        @Throws(IOException::class)
        fun createCompoundFile(
            infoStream: InfoStream,
            directory: TrackingDirectoryWrapper,
            info: SegmentInfo,
            context: IOContext,
            deleteFiles: IOConsumer<MutableCollection<String>>
        ) {
            // maybe this check is not needed, but why take the risk

            check(directory.createdFiles.isEmpty()) { "pass a clean trackingdir for CFS creation" }

            if (infoStream.isEnabled("IW")) {
                infoStream.message("IW", "create compound file")
            }
            // Now merge all added files
            var success = false
            try {
                info.codec.compoundFormat().write(directory, info, context)
                success = true
            } finally {
                if (!success) {
                    // Safe: these files must exist
                    deleteFiles.accept(directory.createdFiles)
                }
            }

            // Replace all previous files with the CFS/CFE files:
            info.setFiles(HashSet(directory.createdFiles))
        }

        init {
            TestSecrets.setIndexWriterAccess(
                object : IndexWriterAccess {
                    override fun segString(iw: IndexWriter): String {
                        return iw.segString()
                    }

                    override fun getSegmentCount(iw: IndexWriter): Int {
                        return iw.getSegmentCount()
                    }

                    override fun isClosed(iw: IndexWriter): Boolean {
                        return iw.isClosed()
                    }

                    @Throws(IOException::class)
                    override fun getReader(
                        iw: IndexWriter, applyDeletions: Boolean, writeAllDeletes: Boolean
                    ): DirectoryReader {
                        return iw.getReader(applyDeletions, writeAllDeletes)
                    }

                    override fun getDocWriterThreadPoolSize(iw: IndexWriter): Int {
                        return runBlocking { iw.docWriter.perThreadPool.size() }
                    }

                    override fun isDeleterClosed(iw: IndexWriter): Boolean {
                        return iw.isDeleterClosed()
                    }

                    override fun newestSegment(iw: IndexWriter): SegmentCommitInfo {
                        return iw.newestSegment()!!
                    }
                })

            // Piggyback general package-scope accessors.
            TestSecrets.setIndexPackageAccess(
                object : IndexPackageAccess {
                    override fun newCacheKey(): IndexReader.CacheKey {
                        return IndexReader.CacheKey()
                    }

                    override fun setIndexWriterMaxDocs(limit: Int) {
                        setMaxDocs(limit)
                    }

                    override fun newFieldInfosBuilder(
                        softDeletesFieldName: String, parentFieldName: String
                    ): IndexPackageAccess.FieldInfosBuilder {
                        return object : IndexPackageAccess.FieldInfosBuilder {
                            private val builder: FieldInfos.Builder =
                                FieldInfos.Builder(
                                    FieldNumbers(
                                        softDeletesFieldName,
                                        parentFieldName
                                    )
                                )

                            override fun add(fi: FieldInfo): IndexPackageAccess.FieldInfosBuilder {
                                builder.add(fi)
                                return this
                            }

                            override fun finish(): FieldInfos {
                                return builder.finish()
                            }
                        }
                    }

                    override fun checkImpacts(impacts: Impacts, max: Int) {
                        CheckIndex.checkImpacts(impacts, max)
                    }
                })
        }
    }
}
