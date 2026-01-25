package org.gnit.lucenekmp.index

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import okio.IOException
import org.gnit.lucenekmp.jdkport.ExecutionException
import org.gnit.lucenekmp.jdkport.Executor
import org.gnit.lucenekmp.jdkport.Optional
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.TimeUnit
import org.gnit.lucenekmp.jdkport.TimeoutException
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.MergeInfo
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.IOConsumer
import org.gnit.lucenekmp.util.IOFunction
import org.gnit.lucenekmp.util.IOSupplier
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.InfoStream
import org.gnit.lucenekmp.util.ThreadInterruptedException
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Expert: a MergePolicy determines the sequence of primitive merge operations.
 *
 *
 * Whenever the segments in an index have been altered by [IndexWriter], either the
 * addition of a newly flushed segment, addition of many segments from addIndexes* calls, or a
 * previous merge that may now need to cascade, [IndexWriter] invokes [.findMerges] to
 * give the MergePolicy a chance to pick merges that are now required. This method returns a [ ] instance describing the set of merges that should be done, or null if no
 * merges are necessary. When IndexWriter.forceMerge is called, it calls [ ][.findForcedMerges] and the MergePolicy should then return
 * the necessary merges.
 *
 *
 * Note that the policy can return more than one merge at a time. In this case, if the writer is
 * using [SerialMergeScheduler], the merges will be run sequentially but if it is using [ ] they will be run concurrently.
 *
 *
 * The default MergePolicy is [TieredMergePolicy].
 *
 * @lucene.experimental
 */
abstract class MergePolicy
/** Creates a new merge policy instance.  */ protected constructor(
    /**
     * If the size of the merge segment exceeds this ratio of the total index size then it will remain
     * in non-compound format
     * Returns current `noCFSRatio`.
     *
     * @see .setNoCFSRatio
     */
    noCFSRatio: Double = DEFAULT_NO_CFS_RATIO,
    /**
     * If the size of the merged segment exceeds this value then it will not use compound file format.
     */
    protected var maxCFSSegmentSize: Long = DEFAULT_MAX_CFS_SEGMENT_SIZE
) {

    open var noCFSRatio: Double = noCFSRatio
        /**
         * If a merged segment will be more than this percentage of the total size of the index, leave the
         * segment as non-compound file even if compound file is enabled. Set to 1.0 to always use CFS
         * regardless of merge size.
         */
        set(noCFSRatio) {
            require(!(noCFSRatio < 0.0 || noCFSRatio > 1.0)) { "noCFSRatio must be 0.0 to 1.0 inclusive; got $noCFSRatio" }
            //this.noCFSRatio = noCFSRatio  // TODO commented out because it emits java.lang.StackOverflowError while running TestSearch.kt fix it in the future
            //	at org.gnit.lucenekmp.index.MergePolicy.setNoCFSRatio(MergePolicy.kt:85)
        }

    /**
     * Progress and state for an executing merge. This class encapsulates the logic to pause and
     * resume the merge thread or to abort the merge entirely.
     *
     * @lucene.experimental
     */
    @OptIn(ExperimentalAtomicApi::class)
    class OneMergeProgress {
        /** Reason for pausing the merge thread.  */
        enum class PauseReason {
            /** Stopped (because of throughput rate set to 0, typically).  */
            STOPPED,

            /** Temporarily paused because of exceeded throughput rate.  */
            PAUSED,

            /** Other reason.  */
            OTHER
        }

        private val pauseLock: Mutex = Mutex()
        private val pausing: Channel<Unit> = Channel<Unit>(Channel.RENDEZVOUS)

        /** Pause times (in nanoseconds) for each [PauseReason].  */
        // Place all the pause reasons in there immediately so that we can simply update values.
        @OptIn(ExperimentalAtomicApi::class)
        private val pauseTimesNS: MutableMap<PauseReason, AtomicLong> = mutableMapOf<PauseReason, AtomicLong>()

        /** Return the aborted state of this merge.  */
        @Volatile
        var isAborted: Boolean = false
            private set

        /**
         * This field is for sanity-check purposes only. Only the same thread that invoked [ ][OneMerge.mergeInit] is permitted to be calling [.pauseNanos]. This is always verified
         * at runtime.
         */
        private var owner: Job? = null

        /** Creates a new merge progress info.  */
        init {
            for (p in PauseReason.entries) {
                pauseTimesNS.put(p, AtomicLong(0))
            }
        }

        /** Abort the merge this progress tracks at the next possible moment.  */
        suspend fun abort() {
            this.isAborted = true
            wakeup() // wakeup any paused merge thread.
        }

        /**
         * Pauses the calling thread for at least `pauseNanos` nanoseconds unless the merge
         * is aborted or the external condition returns `false`, in which case control
         * returns immediately.
         *
         *
         * The external condition is required so that other threads can terminate the pausing
         * immediately, before `pauseNanos` expires. We can't rely on just [ ][Condition.awaitNanos] alone because it can return due to spurious wakeups too.
         *
         * @param condition The pause condition that should return false if immediate return from this
         * method is needed. Other threads can wake up any sleeping thread by calling [     ][.wakeup], but it'd fall to sleep for the remainder of the requested time if this
         * condition
         */
        suspend fun pauseNanos(initialPauseNanos: Long, reason: PauseReason, condition: () -> Boolean /*java.util.function.BooleanSupplier*/) {
            var remainingNanosToPause = initialPauseNanos
            val functionStartTime: Long = System.nanoTime()
            val timeUpdate: AtomicLong = pauseTimesNS[reason]!!
            pauseLock.lock()
            try {
                while (remainingNanosToPause > 0 && !this.isAborted && condition()) {
                    val iterationStartTimeNanos: Long = System.nanoTime()
                    try{
                        withTimeout(remainingNanosToPause.nanoseconds){
                            pausing.receive()
                        }

                        val elapsedInIteration = System.nanoTime() - iterationStartTimeNanos
                        remainingNanosToPause -= elapsedInIteration
                        if(remainingNanosToPause < 0) {
                            remainingNanosToPause = 0
                        }
                    }catch (e: TimeoutCancellationException) {
                        remainingNanosToPause = 0
                    }catch(e: CancellationException){
                        throw RuntimeException("Merge was aborted while waiting for pause", e)
                    }
                }
            } finally {
                pauseLock.unlock()
                timeUpdate.addAndFetch(System.nanoTime() - functionStartTime)
            }
        }

        /** Request a wakeup for any threads stalled in [.pauseNanos].  */
        suspend fun wakeup() {
            pauseLock.withLock {
                pausing.trySend(Unit)
            }
        }

        val pauseTimes: MutableMap<PauseReason, Long>
            /** Returns pause reasons and associated times in nanoseconds.  */
            get() = pauseTimesNS.mapValues {(_, atomic) -> atomic.load()}.toMutableMap()

        fun setMergeThread(owner: Job) {
            assert(this.owner == null)
            this.owner = owner
        }
    }

    /**
     * OneMerge provides the information necessary to perform an individual primitive merge operation,
     * resulting in a single new segment. The merge spec includes the subset of segments to be merged
     * as well as whether the new segment should use the compound file format.
     *
     * @lucene.experimental
     */
    open class OneMerge {
        val mergeCompleted = CompletableDeferred<Boolean>()
        var info: SegmentCommitInfo? = null // used by IndexWriter
        var registerDone: Boolean = false // used by IndexWriter
        var mergeGen: Long = 0 // used by IndexWriter
        var isExternal: Boolean = false // used by IndexWriter
        var maxNumSegments: Int = -1 // used by IndexWriter
        var usesPooledReaders: Boolean // used by IndexWriter to drop readers while closing

        /** Estimated size in bytes of the merged segment.  */
        @Volatile
        var estimatedMergeBytes: Long = 0 // used by IndexWriter

        // Sum of sizeInBytes of all SegmentInfos; set by IW.mergeInit
        @Volatile
        var totalMergeBytes: Long = 0

        /** Returns the merge readers or an empty list if the readers were not initialized yet.  */
        var mergeReader: MutableList<MergeReader> // used by IndexWriter
            private set

        /** Segments to be merged.  */
        val segments: MutableList<SegmentCommitInfo>

        /**
         * Returns a [OneMergeProgress] instance for this merge, which provides statistics of the
         * merge threads (run time vs. sleep time) if merging is throttled.
         */
        /** Control used to pause/stop/resume the merge thread.  */
        val mergeProgress: OneMergeProgress

        @Volatile
        var mergeStartNS: Long = -1

        /** Total number of documents in segments to be merged, not accounting for deletions.  */
        val totalMaxDoc: Int

        /** Retrieve previous exception set by [.setException].  */
        /** Record that an exception occurred while executing this merge  */
        var exception: Throwable? = null

        /**
         * Sole constructor.
         *
         * @param segments List of [SegmentCommitInfo]s to be merged.
         */
        constructor(segments: MutableList<SegmentCommitInfo>) {
            if (segments.isEmpty()) {
                throw RuntimeException("segments must include at least one segment")
            }
            // clone the list, as the in list may be based off original SegmentInfos and may be modified
            this.segments = /*java.util.List.copyOf<SegmentCommitInfo>(segments)*/ segments.toMutableList()
            totalMaxDoc = segments.sumOf { i: SegmentCommitInfo -> i.info.maxDoc() }
            mergeProgress = OneMergeProgress()
            this.mergeReader = mutableListOf<MergePolicy.MergeReader>()
            usesPooledReaders = true
        }

        /**
         * Create a OneMerge directly from CodecReaders. Used to merge incoming readers in [ ][IndexWriter.addIndexes]. This OneMerge works directly on readers and has an
         * empty segments list.
         *
         * @param codecReaders Codec readers to merge
         */
        constructor(vararg codecReaders: CodecReader) {
            val readers: MutableList<MergeReader> = ArrayList(codecReaders.size)
            var totalDocs = 0
            for (r in codecReaders) {
                readers.add(MergeReader(r, r.liveDocs!!))
                totalDocs += r.numDocs()
            }
            this.mergeReader = readers.toMutableList()
            segments = mutableListOf<SegmentCommitInfo>()
            totalMaxDoc = totalDocs
            mergeProgress = OneMergeProgress()
            usesPooledReaders = false
        }

        /** Constructor for wrapping.  */
        protected constructor(oneMerge: OneMerge) {
            this.segments = oneMerge.segments
            this.mergeReader = oneMerge.mergeReader
            this.totalMaxDoc = oneMerge.totalMaxDoc
            this.mergeProgress = OneMergeProgress()
            this.usesPooledReaders = oneMerge.usesPooledReaders
        }

        /**
         * Called by [IndexWriter] after the merge started and from the thread that will be
         * executing the merge.
         */
        suspend fun mergeInit() {
            val currentJob = coroutineContext[Job] ?: throw IOException("mergeInit must be called from a coroutine context with a Job")
            mergeProgress.setMergeThread(currentJob)
        }

        /**
         * Called by [IndexWriter] after the merge is done and all readers have been closed.
         *
         * @param success true iff the merge finished successfully i.e. was committed
         * @param segmentDropped true iff the merged segment was dropped since it was fully deleted
         */
        @Throws(IOException::class)
        open fun mergeFinished(success: Boolean, segmentDropped: Boolean) {
        }

        /** Closes this merge and releases all merge readers  */
        @Throws(IOException::class)
        fun close(
            success: Boolean, segmentDropped: Boolean, readerConsumer: IOConsumer<MergeReader>
        ) {
            // this method is final to ensure we never miss a super call to clean up and finish the merge
            check(mergeCompleted.complete(success) != false) { "merge has already finished" }
            try {
                mergeFinished(success, segmentDropped)
            } finally {
                val readers = this.mergeReader
                this.mergeReader = mutableListOf()
                IOUtils.applyToAll(
                    readers,
                    readerConsumer
                )
            }
        }

        /**
         * Wrap a reader prior to merging in order to add/remove fields or documents.
         *
         *
         * **NOTE:** It is illegal to reorder doc IDs here, use [ ][.reorder] instead.
         */
        @Throws(IOException::class)
        open fun wrapForMerge(reader: CodecReader): CodecReader {
            return reader
        }

        /**
         * Extend this method if you wish to renumber doc IDs. This method will be called when index
         * sorting is disabled on a merged view of the [OneMerge]. A `null` return value
         * indicates that doc IDs should not be reordered.
         *
         *
         * **NOTE:** Returning a non-null value here disables several optimizations and increases
         * the merging overhead.
         *
         * @param reader The reader to reorder.
         * @param dir The [Directory] of the index, which may be used to create temporary files.
         * @param executor An executor that can be used to parallelize the reordering logic. May be
         * `null` if no concurrency is supported.
         * @lucene.experimental
         */
        @Throws(IOException::class)
        open fun reorder(
            reader: CodecReader,
            dir: Directory,
            executor: Executor
        ): Sorter.DocMap? {
            return null
        }

        /**
         * Expert: Sets the [SegmentCommitInfo] of the merged segment. Allows sub-classes to e.g.
         * [add diagnostic][SegmentInfo.addDiagnostics] properties.
         */
        open fun setMergeInfo(info: SegmentCommitInfo) {
            this.info = info
        }

        val mergeInfo: SegmentCommitInfo?
            /**
             * Returns the [SegmentCommitInfo] for the merged segment, or null if it hasn't been set
             * yet.
             */
            get() = info

        /** Returns a readable description of the current merge state.  */
        fun segString(): String {
            val b = StringBuilder()
            val numSegments = segments.size
            for (i in 0..<numSegments) {
                if (i > 0) {
                    b.append(' ')
                }
                b.append(segments[i].toString())
            }
            if (info != null) {
                b.append(" into ").append(info!!.info.name)
            }
            if (maxNumSegments != -1) {
                b.append(" [maxNumSegments=").append(maxNumSegments).append(']')
            }
            if (this.isAborted) {
                b.append(" [ABORTED]")
            }
            return b.toString()
        }

        /**
         * Returns the total size in bytes of this merge. Note that this does not indicate the size of
         * the merged segment, but the input total size. This is only set once the merge is initialized
         * by IndexWriter.
         */
        fun totalBytesSize(): Long {
            return totalMergeBytes
        }

        /**
         * Returns the total number of documents that are included with this merge. Note that this does
         * not indicate the number of documents after the merge.
         */
        fun totalNumDocs(): Int {
            return totalMaxDoc
        }

        val storeMergeInfo: MergeInfo
            /** Return [MergeInfo] describing this merge.  */
            get() = MergeInfo(totalMaxDoc, estimatedMergeBytes, isExternal, maxNumSegments)

        val isAborted: Boolean
            /** Returns true if this merge was or should be aborted.  */
            get() = mergeProgress.isAborted

        /**
         * Marks this merge as aborted. The merge thread should terminate at the soonest possible
         * moment.
         */
        suspend fun setAborted() {
            this.mergeProgress.abort()
        }

        /** Checks if merge has been aborted and throws a merge exception if so.  */
        @Throws(MergeAbortedException::class)
        fun checkAborted() {
            if (this.isAborted) {
                throw MergeAbortedException("merge is aborted: " + segString())
            }
        }

        /**
         * Waits for this merge to be completed
         *
         * @return true if the merge finished within the specified timeout
         */
        suspend fun await(timeout: Long, timeUnit: TimeUnit): Boolean {
            return try {
                withTimeout(timeUnit.toNanos(timeout).nanoseconds) {
                    // Wait for the merge to complete
                    mergeCompleted.await()
                    true
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ThreadInterruptedException(ce)
            } catch (e: ExecutionException) {
                false
            } catch (e: TimeoutException) {
                false
            } catch (e: TimeoutCancellationException) {
                false
            }
        }

        /**
         * Returns true if the merge has finished or false if it's still running or has not been
         * started. This method will not block.
         */
        fun hasFinished(): Boolean {
            return mergeCompleted.isCompleted
        }

        /**
         * Returns true if the merge completed successfully or false if the merge succeeded with a
         * failure. This method will not block and return an empty Optional if the merge has not
         * finished yet
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        fun hasCompletedSuccessfully(): Optional<Boolean?> {
            return Optional.ofNullable(mergeCompleted.getCompleted())
        }

        /** Called just before the merge is applied to IndexWriter's SegmentInfos  */
        @Throws(IOException::class)
        open fun onMergeComplete() {
        }

        /** Sets the merge readers for this merge.  */
        @Throws(IOException::class)
        open fun initMergeReaders(readerFactory: IOFunction<SegmentCommitInfo, MergeReader>) {
            assert(mergeReader.isEmpty()) { "merge readers must be empty" }
            assert(!mergeCompleted.isCompleted) { "merge is already done" }
            val readers: ArrayList<MergeReader> = ArrayList(segments.size)
            try {
                for (info in segments) {
                    // Hold onto the "live" reader; we will use this to
                    // commit merged deletes
                    readers.add(readerFactory.apply(info))
                }
            } finally {
                // ensure we assign this to close them in the case of an exception
                // we do a copy here to ensure that mergeReaders are an immutable list
                this.mergeReader = /*java.util.List.copyOf<MergePolicy.MergeReader>(readers)*/ readers.toMutableList()
            }
        }
    }

    /**
     * A MergeSpecification instance provides the information necessary to perform multiple merges. It
     * simply contains a list of [OneMerge] instances.
     */
    class MergeSpecification
    /** Sole constructor. Use [.add] to add merges.  */
    {
        /** The subset of segments to be included in the primitive merge.  */
        val merges: MutableList<OneMerge> = ArrayList()

        /** Adds the provided [OneMerge] to this specification.  */
        fun add(merge: OneMerge) {
            merges.add(merge)
        }

        // TODO: deprecate me (dir is never used!  and is sometimes difficult to provide!)
        /** Returns a description of the merges in this specification.  */
        fun segString(dir: Directory): String {
            val b = StringBuilder()
            b.append("MergeSpec:\n")
            val count = merges.size
            for (i in 0..<count) {
                b.append("  ").append(1 + i).append(": ").append(merges[i].segString())
            }
            return b.toString()
        }

        override fun toString(): String {
            val b = StringBuilder()
            b.append("MergeSpec:")
            val count = merges.size
            for (i in 0..<count) {
                b.append("\n  ").append(1 + i).append(": ").append(merges[i].segString())
            }
            return b.toString()
        }

        @OptIn(DelicateCoroutinesApi::class)
        val mergeCompletedFutures: Deferred<Unit>
            get() = GlobalScope.async {
                merges
                    .map{ it.mergeCompleted }
                    .awaitAll()
                Unit
            }

        /** Waits, until interrupted, for all merges to complete.  */
        fun await(): Boolean {
            return try {
                runBlocking{
                    this@MergeSpecification.mergeCompletedFutures.await()
                }
                true
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw ThreadInterruptedException(e)
            } catch (e: ExecutionException) {
                false
            } catch (e: CancellationException) {
                false
            }
        }

        /** Waits if necessary for at most the given time for all merges.  */
        fun await(timeout: Long, unit: TimeUnit): Boolean {
            return try {
                runBlocking {
                    withTimeout(unit.toNanos(timeout).nanoseconds) {
                        this@MergeSpecification.mergeCompletedFutures.await()
                    }
                }
                true
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw ThreadInterruptedException(e)
            } catch (e: ExecutionException) {
                false
            } catch (e: TimeoutException) {
                false
            } catch (e: TimeoutCancellationException) {
                false
            } catch (e: CancellationException) {
                false
            }
        }
    }

    /** Exception thrown if there are any problems while executing a merge.  */
    class MergeException : RuntimeException {
        /** Create a `MergeException`.  */
        constructor(message: String) : super(message)

        /** Create a `MergeException`.  */
        constructor(exc: Throwable) : super(exc)
    }

    /**
     * Thrown when a merge was explicitly aborted because [IndexWriter.abortMerges] was called.
     * Normally this exception is privately caught and suppressed by [IndexWriter].
     */
    class MergeAbortedException : IOException {
        /** Create a [MergeAbortedException].  */
        constructor() : super("merge is aborted")

        /** Create a [MergeAbortedException] with a specified message.  */
        constructor(message: String) : super(message)
    }

    /**
     * Creates a new merge policy instance with default settings for noCFSRatio and maxCFSSegmentSize.
     * This ctor should be used by subclasses using different defaults than the [MergePolicy]
     */

    /**
     * Determine what set of merge operations are now necessary on the index. [IndexWriter]
     * calls this whenever there is a change to the segments. This call is always synchronized on the
     * [IndexWriter] instance so only one thread at a time will call this method.
     *
     * @param mergeTrigger the event that triggered the merge
     * @param segmentInfos the total set of segments in the index
     * @param mergeContext the IndexWriter to find the merges on
     */
    @Throws(IOException::class)
    abstract fun findMerges(
        mergeTrigger: MergeTrigger?, segmentInfos: SegmentInfos, mergeContext: MergeContext
    ): MergeSpecification?

    /**
     * Define the set of merge operations to perform on provided codec readers in [ ][IndexWriter.addIndexes].
     *
     *
     * The merge operation is required to convert provided readers into segments that can be added
     * to the writer. This API can be overridden in custom merge policies to control the concurrency
     * for addIndexes. Default implementation creates a single merge operation for all provided
     * readers (lowest concurrency). Creating a merge for each reader, would provide the highest level
     * of concurrency possible with the configured merge scheduler.
     *
     * @param readers CodecReader(s) to merge into the main index
     */
    @Throws(IOException::class)
    open fun findMerges(vararg readers: CodecReader): MergeSpecification {
        val mergeSpec = MergeSpecification()
        mergeSpec.add(OneMerge(*readers))
        return mergeSpec
    }

    /**
     * Determine what set of merge operations is necessary in order to merge to `<=` the
     * specified segment count. [IndexWriter] calls this when its [IndexWriter.forceMerge]
     * method is called. This call is always synchronized on the [IndexWriter] instance so only
     * one thread at a time will call this method.
     *
     * @param segmentInfos the total set of segments in the index
     * @param maxSegmentCount requested maximum number of segments in the index
     * @param segmentsToMerge contains the specific SegmentInfo instances that must be merged away.
     * This may be a subset of all SegmentInfos. If the value is True for a given SegmentInfo,
     * that means this segment was an original segment present in the to-be-merged index; else, it
     * was a segment produced by a cascaded merge.
     * @param mergeContext the MergeContext to find the merges on
     */
    @Throws(IOException::class)
    abstract fun findForcedMerges(
        segmentInfos: SegmentInfos,
        maxSegmentCount: Int,
        segmentsToMerge: MutableMap<SegmentCommitInfo, Boolean>,
        mergeContext: MergeContext
    ): MergeSpecification?

    /**
     * Determine what set of merge operations is necessary in order to expunge all deletes from the
     * index.
     *
     * @param segmentInfos the total set of segments in the index
     * @param mergeContext the MergeContext to find the merges on
     */
    @Throws(IOException::class)
    abstract fun findForcedDeletesMerges(
        segmentInfos: SegmentInfos, mergeContext: MergeContext
    ): MergeSpecification?

    /**
     * Identifies merges that we want to execute (synchronously) on commit. By default, this will
     * return [natural merges][.findMerges] whose segments are all less than the [ ][.maxFullFlushMergeSize].
     *
     *
     * Any merges returned here will make [IndexWriter.commit], [ ][IndexWriter.prepareCommit] or [IndexWriter.getReader] block until the
     * merges complete or until [IndexWriterConfig.getMaxFullFlushMergeWaitMillis] has
     * elapsed. This may be used to merge small segments that have just been flushed, reducing the
     * number of segments in the point in time snapshot. If a merge does not complete in the allotted
     * time, it will continue to execute, and eventually finish and apply to future point in time
     * snapshot, but will not be reflected in the current one.
     *
     *
     * If a [OneMerge] in the returned [MergeSpecification] includes a segment already
     * included in a registered merge, then [IndexWriter.commit] or [ ][IndexWriter.prepareCommit] will throw a [IllegalStateException]. Use [ ][MergeContext.getMergingSegments] to determine which segments are currently registered to
     * merge.
     *
     * @param mergeTrigger the event that triggered the merge (COMMIT or GET_READER).
     * @param segmentInfos the total set of segments in the index (while preparing the commit)
     * @param mergeContext the MergeContext to find the merges on, which should be used to determine
     * which segments are already in a registered merge (see [     ][MergeContext.getMergingSegments]).
     */
    @Throws(IOException::class)
    open fun findFullFlushMerges(
        mergeTrigger: MergeTrigger, segmentInfos: SegmentInfos, mergeContext: MergeContext
    ): MergeSpecification? {
        // This returns natural merges that contain segments below the minimum size
        val mergeSpec = findMerges(mergeTrigger, segmentInfos, mergeContext)
        if (mergeSpec == null) {
            return null
        }
        var newMergeSpec: MergeSpecification? = null
        for (oneMerge in mergeSpec.merges) {
            var belowMaxFullFlushSize = true
            for (sci in oneMerge.segments) {
                if (size(sci, mergeContext) >= maxFullFlushMergeSize()) {
                    belowMaxFullFlushSize = false
                    break
                }
            }
            if (belowMaxFullFlushSize) {
                if (newMergeSpec == null) {
                    newMergeSpec = MergeSpecification()
                }
                newMergeSpec.add(oneMerge)
            }
        }
        return newMergeSpec
    }

    /**
     * Returns true if a new segment (regardless of its origin) should use the compound file format.
     * The default implementation returns `true` iff the size of the given mergedInfo is
     * less or equal to [.getMaxCFSSegmentSizeMB] and the size is less or equal to the
     * TotalIndexSize * [.getNoCFSRatio] otherwise `false`.
     */
    @Throws(IOException::class)
    open fun useCompoundFile(
        infos: SegmentInfos,
        mergedInfo: SegmentCommitInfo,
        mergeContext: MergeContext
    ): Boolean {
        if (noCFSRatio == 0.0) {
            return false
        }
        val mergedInfoSize = size(mergedInfo, mergeContext)
        if (mergedInfoSize > maxCFSSegmentSize) {
            return false
        }
        if (noCFSRatio >= 1.0) {
            return true
        }
        var totalSize: Long = 0
        for (info in infos) {
            totalSize += size(info, mergeContext)
        }
        return mergedInfoSize <= noCFSRatio * totalSize
    }

    /**
     * Return the byte size of the provided [SegmentCommitInfo], prorated by percentage of
     * non-deleted documents.
     */
    @Throws(IOException::class)
    open fun size(info: SegmentCommitInfo, mergeContext: MergeContext): Long {
        val byteSize: Long = info.sizeInBytes()
        val delCount = mergeContext.numDeletesToMerge(info)
        assert(assertDelCount(delCount, info))
        val delRatio =
            if (info.info.maxDoc() <= 0) 0.0 else delCount.toDouble() / info.info.maxDoc().toDouble()
        assert(delRatio <= 1.0)
        return (if (info.info.maxDoc() <= 0) byteSize else (byteSize * (1.0 - delRatio)).toLong())
    }

    /**
     * Return the maximum size of segments to be included in full-flush merges by the default
     * implementation of [.findFullFlushMerges].
     */
    open fun maxFullFlushMergeSize(): Long {
        return 0L
    }

    /** Asserts that the delCount for this SegmentCommitInfo is valid  */
    protected fun assertDelCount(delCount: Int, info: SegmentCommitInfo): Boolean {
        assert(delCount >= 0) { "delCount must be positive: $delCount" }
        assert(
            delCount <= info.info.maxDoc()
        ) { "delCount: " + delCount + " must be leq than maxDoc: " + info.info.maxDoc() }
        return true
    }

    /**
     * Returns true if this single info is already fully merged (has no pending deletes, is in the
     * same dir as the writer, and matches the current compound file setting
     */
    @Throws(IOException::class)
    protected fun isMerged(
        infos: SegmentInfos,
        info: SegmentCommitInfo,
        mergeContext: MergeContext
    ): Boolean {
        checkNotNull(mergeContext)
        val delCount = mergeContext.numDeletesToMerge(info)
        assert(assertDelCount(delCount, info))
        return delCount == 0
                && useCompoundFile(infos, info, mergeContext) == info.info.useCompoundFile
    }

    open var maxCFSSegmentSizeMB: Double
        /** Returns the largest size allowed for a compound file segment  */
        get() = maxCFSSegmentSize / 1024.0 / 1024.0
        /**
         * If a merged segment will be more than this value, leave the segment as non-compound file even
         * if compound file is enabled. Set this to Double.POSITIVE_INFINITY (default) and noCFSRatio to
         * 1.0 to always use CFS regardless of merge size.
         */
        set(v) {
            var v = v
            require(!(v < 0.0)) { "maxCFSSegmentSizeMB must be >=0 (got $v)" }
            v *= (1024 * 1024).toDouble()
            this.maxCFSSegmentSize = if (v > Long.Companion.MAX_VALUE) Long.Companion.MAX_VALUE else v.toLong()
        }

    /**
     * Returns true if the segment represented by the given CodecReader should be kept even if it's
     * fully deleted. This is useful for testing of for instance if the merge policy implements
     * retention policies for soft deletes.
     */
    @Throws(IOException::class)
    open fun keepFullyDeletedSegment(readerIOSupplier: IOSupplier<CodecReader>): Boolean {
        return false
    }

    /**
     * Returns the number of deletes that a merge would claim on the given segment. This method will
     * by default return the sum of the del count on disk and the pending delete count. Yet,
     * subclasses that wrap merge readers might modify this to reflect deletes that are carried over
     * to the target segment in the case of soft deletes.
     *
     *
     * Soft deletes all deletes to survive across merges in order to control when the soft-deleted
     * data is claimed.
     *
     * @see IndexWriter.softUpdateDocument
     * @see IndexWriterConfig.setSoftDeletesField
     * @param info the segment info that identifies the segment
     * @param delCount the number deleted documents for this segment
     * @param readerSupplier a supplier that allows to obtain a [CodecReader] for this segment
     */
    @Throws(IOException::class)
    open fun numDeletesToMerge(
        info: SegmentCommitInfo,
        delCount: Int,
        readerSupplier: IOSupplier<CodecReader>
    ): Int {
        return delCount
    }

    /** Builds a String representation of the given SegmentCommitInfo instances  */
    protected fun segString(
        mergeContext: MergeContext,
        infos: Iterable<SegmentCommitInfo>
    ): String = infos.joinToString(" ") { info ->
        val delCount = mergeContext.numDeletedDocs(info) - info.delCount
        info.toString(delCount)
    }

    /** Print a debug message to [MergeContext]'s `infoStream`.  */
    protected fun message(message: String, mergeContext: MergeContext) {
        if (verbose(mergeContext)) {
            mergeContext.infoStream.message("MP", message)
        }
    }

    /**
     * Returns `true` if the info-stream is in verbose mode
     *
     * @see .message
     */
    protected fun verbose(mergeContext: MergeContext): Boolean {
        return mergeContext.infoStream.isEnabled("MP")
    }

    /**
     * This interface represents the current context of the merge selection process. It allows to
     * access real-time information like the currently merging segments or how many deletes a segment
     * would claim back if merged. This context might be stateful and change during the execution of a
     * merge policy's selection processes.
     *
     * @lucene.experimental
     */
    interface MergeContext {
        /**
         * Returns the number of deletes a merge would claim back if the given segment is merged.
         *
         * @see MergePolicy.numDeletesToMerge
         * @param info the segment to get the number of deletes for
         */
        @Throws(IOException::class)
        fun numDeletesToMerge(info: SegmentCommitInfo): Int

        /** Returns the number of deleted documents in the given segments.  */
        fun numDeletedDocs(info: SegmentCommitInfo): Int

        /** Returns the info stream that can be used to log messages  */
        val infoStream: InfoStream

        /** Returns an unmodifiable set of segments that are currently merging.  */
        val mergingSegments: MutableSet<SegmentCommitInfo>
    }

    class MergeReader {
        val codecReader: CodecReader
        val reader: SegmentReader?
        val hardLiveDocs: Bits?

        constructor(reader: SegmentReader, hardLiveDocs: Bits?) {
            this.codecReader = reader
            this.reader = reader
            this.hardLiveDocs = hardLiveDocs
        }

        constructor(reader: CodecReader, hardLiveDocs: Bits?) {
            if (/*SegmentReader::class.java.isAssignableFrom(reader.javaClass)*/ reader is SegmentReader) {
                this.reader = reader as SegmentReader
            } else {
                this.reader = null
            }
            this.codecReader = reader
            this.hardLiveDocs = hardLiveDocs
        }
    }

    companion object {
        /**
         * Default ratio for compound file system usage. Set to `1.0`, always use compound file
         * system.
         */
        protected const val DEFAULT_NO_CFS_RATIO: Double = 1.0

        /**
         * Default max segment size in order to use compound file system. Set to [Long.MAX_VALUE].
         */
        protected const val DEFAULT_MAX_CFS_SEGMENT_SIZE: Long = Long.Companion.MAX_VALUE
    }
}
