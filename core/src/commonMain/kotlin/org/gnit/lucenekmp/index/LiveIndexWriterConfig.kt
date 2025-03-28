package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.util.KClassValue.Version
import kotlin.concurrent.Volatile


/**
 * Holds all the configuration used by [IndexWriter] with few setters for settings that can be
 * changed on an [IndexWriter] instance "live".
 *
 * @since 4.0
 */
class LiveIndexWriterConfig internal constructor(analyzer: Analyzer) {
    private val analyzer: Analyzer = analyzer

    /**
     * Returns the number of buffered added documents that will trigger a flush if enabled.
     *
     * @see .setMaxBufferedDocs
     */
    @Volatile
    var maxBufferedDocs: Int
        private set

    /** Returns the value set by [.setRAMBufferSizeMB] if enabled.  */
    @Volatile
    var rAMBufferSizeMB: Double
        private set

    @Volatile
    private var mergedSegmentWarmer: IndexReaderWarmer

    // modified by IndexWriterConfig
    /** [IndexDeletionPolicy] controlling when commit points are deleted.  */
    @Volatile
    protected var delPolicy: IndexDeletionPolicy

    /** [IndexCommit] that [IndexWriter] is opened on.  */
    @Volatile
    protected var commit: IndexCommit

    /** [OpenMode] that [IndexWriter] is opened with.  */
    @Volatile
    protected var openMode: OpenMode

    /**
     * Return the compatibility version to use for this index.
     *
     * @see IndexWriterConfig.setIndexCreatedVersionMajor
     */
    /** Compatibility version to use for this index.  */
    var indexCreatedVersionMajor: Int = Version.LATEST.major
        protected set

    /** [Similarity] to use when encoding norms.  */
    @Volatile
    protected var similarity: Similarity

    /** [MergeScheduler] to use for running merges.  */
    @Volatile
    protected var mergeScheduler: MergeScheduler

    /** [Codec] used to write new segments.  */
    @Volatile
    protected var codec: Codec?

    /** [InfoStream] for debugging messages.  */
    @Volatile
    protected var infoStream: InfoStream

    /** [MergePolicy] for selecting merges.  */
    @Volatile
    protected var mergePolicy: MergePolicy

    /**
     * Returns `true` if [IndexWriter] should pool readers even if [ ][DirectoryReader.open] has not been called.
     */
    /** True if readers should be pooled.  */
    @Volatile
    var readerPooling: Boolean
        protected set

    /** [FlushPolicy] to control when segments are flushed.  */
    @Volatile
    protected var flushPolicy: FlushPolicy

    /**
     * Returns the max amount of memory each [DocumentsWriterPerThread] can consume until
     * forcefully flushed.
     *
     * @see IndexWriterConfig.setRAMPerThreadHardLimitMB
     */
    /**
     * Sets the hard upper bound on RAM usage for a single segment, after which the segment is forced
     * to flush.
     */
    @Volatile
    var rAMPerThreadHardLimitMB: Int
        protected set

    /**
     * Returns `true` iff the [IndexWriter] packs newly written segments in a
     * compound file. Default is `true`.
     */
    /** True if segment flushes should use compound file format  */
    @Volatile
    var useCompoundFile: Boolean
        protected set

    /**
     * Returns `true` if [IndexWriter.close] should first commit before closing.
     */
    /** True if calls to [IndexWriter.close] should first do a commit.  */
    var commitOnClose: Boolean = IndexWriterConfig.DEFAULT_COMMIT_ON_CLOSE
        protected set

    /** The sort order to use to write merged segments.  */
    protected var indexSort: Sort? = null

    /** The comparator for sorting leaf readers.  */
    protected var leafSorter: java.util.Comparator<LeafReader>? = null

    /** Returns the field names involved in the index sort  */
    /** The field names involved in the index sort  */
    var indexSortFields: Set<String> = emptySet()
        protected set

    /** Returns the parent document field name if configured.  */
    /** parent document field  */
    var parentField: String? = null
        protected set

    /**
     * Expert: Returns if indexing threads check for pending flushes on update in order to help our
     * flushing indexing buffers to disk
     *
     * @lucene.experimental
     */
    /**
     * if an indexing thread should check for pending flushes on update in order to help out on a full
     * flush
     */
    @Volatile
    var isCheckPendingFlushOnUpdate: Boolean = true
        protected set

    /**
     * Returns the soft deletes field or `null` if soft-deletes are disabled. See [ ][IndexWriterConfig.setSoftDeletesField] for details.
     */
    /** soft deletes field  */
    var softDeletesField: String? = null
        protected set

    /**
     * Expert: return the amount of time to wait for merges returned by by
     * MergePolicy.findFullFlushMerges(...). If this time is reached, we proceed with the commit based
     * on segments merged up to that point. The merges are not cancelled, and may still run to
     * completion independent of the commit.
     */
    /** Amount of time to wait for merges returned by MergePolicy.findFullFlushMerges(...)  */
    @Volatile
    var maxFullFlushMergeWaitMillis: Long
        protected set

    /** The IndexWriter event listener to record key events *  */
    protected var eventListener: IndexWriterEventListener

    // used by IndexWriterConfig
    init {
        rAMBufferSizeMB = IndexWriterConfig.DEFAULT_RAM_BUFFER_SIZE_MB
        maxBufferedDocs = IndexWriterConfig.DEFAULT_MAX_BUFFERED_DOCS
        mergedSegmentWarmer = null
        delPolicy = KeepOnlyLastCommitDeletionPolicy()
        commit = null
        useCompoundFile = IndexWriterConfig.DEFAULT_USE_COMPOUND_FILE_SYSTEM
        openMode = OpenMode.CREATE_OR_APPEND
        similarity = IndexSearcher.getDefaultSimilarity()
        mergeScheduler = ConcurrentMergeScheduler()
        codec = Codec.getDefault()
        if (codec == null) {
            throw java.lang.NullPointerException()
        }
        infoStream = InfoStream.getDefault()
        mergePolicy = TieredMergePolicy()
        flushPolicy = FlushByRamOrCountsPolicy()
        readerPooling = IndexWriterConfig.DEFAULT_READER_POOLING
        rAMPerThreadHardLimitMB = IndexWriterConfig.DEFAULT_RAM_PER_THREAD_HARD_LIMIT_MB
        maxFullFlushMergeWaitMillis = IndexWriterConfig.DEFAULT_MAX_FULL_FLUSH_MERGE_WAIT_MILLIS
        eventListener = IndexWriterEventListener.NO_OP_LISTENER
    }

    /** Returns the default analyzer to use for indexing documents.  */
    fun getAnalyzer(): Analyzer? {
        return analyzer
    }

    /**
     * Determines the amount of RAM that may be used for buffering added documents and deletions
     * before they are flushed to the Directory. Generally for faster indexing performance it's best
     * to flush by RAM usage instead of document count and use as large a RAM buffer as you can.
     *
     *
     * When this is set, the writer will flush whenever buffered documents and deletions use this
     * much RAM. Pass in [IndexWriterConfig.DISABLE_AUTO_FLUSH] to prevent triggering a flush
     * due to RAM usage. Note that if flushing by document count is also enabled, then the flush will
     * be triggered by whichever comes first.
     *
     *
     * The maximum RAM limit is inherently determined by the JVMs available memory. Yet, an [ ] session can consume a significantly larger amount of memory than the given RAM
     * limit since this limit is just an indicator when to flush memory resident documents to the
     * Directory. Flushes are likely to happen concurrently while other threads adding documents to
     * the writer. For application stability the available memory in the JVM should be significantly
     * larger than the RAM buffer used for indexing.
     *
     *
     * **NOTE**: the account of RAM usage for pending deletions is only approximate.
     * Specifically, if you delete by Query, Lucene currently has no way to measure the RAM usage of
     * individual Queries so the accounting will under-estimate and you should compensate by either
     * calling commit() or refresh() periodically yourself.
     *
     *
     * **NOTE**: It's not guaranteed that all memory resident documents are flushed once this
     * limit is exceeded. Depending on the configured [FlushPolicy] only a subset of the
     * buffered documents are flushed and therefore only parts of the RAM buffer is released.
     *
     *
     * The default value is [IndexWriterConfig.DEFAULT_RAM_BUFFER_SIZE_MB].
     *
     *
     * Takes effect immediately, but only the next time a document is added, updated or deleted.
     *
     * @see IndexWriterConfig.setRAMPerThreadHardLimitMB
     * @throws IllegalArgumentException if ramBufferSize is enabled but non-positive, or it disables
     * ramBufferSize when maxBufferedDocs is already disabled
     */
    @Synchronized
    fun setRAMBufferSizeMB(ramBufferSizeMB: Double): LiveIndexWriterConfig {
        require(!(ramBufferSizeMB != IndexWriterConfig.DISABLE_AUTO_FLUSH && ramBufferSizeMB <= 0.0)) { "ramBufferSize should be > 0.0 MB when enabled" }
        require(
            !(ramBufferSizeMB == IndexWriterConfig.DISABLE_AUTO_FLUSH
                    && maxBufferedDocs == IndexWriterConfig.DISABLE_AUTO_FLUSH)
        ) { "at least one of ramBufferSize and maxBufferedDocs must be enabled" }
        this.rAMBufferSizeMB = ramBufferSizeMB
        return this
    }

    /**
     * Determines the minimal number of documents required before the buffered in-memory documents are
     * flushed as a new Segment. Large values generally give faster indexing.
     *
     *
     * When this is set, the writer will flush every maxBufferedDocs added documents. Pass in
     * [IndexWriterConfig.DISABLE_AUTO_FLUSH] to prevent triggering a flush due to number of
     * buffered documents. Note that if flushing by RAM usage is also enabled, then the flush will be
     * triggered by whichever comes first.
     *
     *
     * Disabled by default (writer flushes by RAM usage).
     *
     *
     * Takes effect immediately, but only the next time a document is added, updated or deleted.
     *
     * @see .setRAMBufferSizeMB
     * @throws IllegalArgumentException if maxBufferedDocs is enabled but smaller than 2, or it
     * disables maxBufferedDocs when ramBufferSize is already disabled
     */
    @Synchronized
    fun setMaxBufferedDocs(maxBufferedDocs: Int): LiveIndexWriterConfig {
        require(!(maxBufferedDocs != IndexWriterConfig.DISABLE_AUTO_FLUSH && maxBufferedDocs < 2)) { "maxBufferedDocs must at least be 2 when enabled" }
        require(
            !(maxBufferedDocs == IndexWriterConfig.DISABLE_AUTO_FLUSH
                    && rAMBufferSizeMB == IndexWriterConfig.DISABLE_AUTO_FLUSH)
        ) { "at least one of ramBufferSize and maxBufferedDocs must be enabled" }
        this.maxBufferedDocs = maxBufferedDocs
        return this
    }

    /**
     * Expert: [MergePolicy] is invoked whenever there are changes to the segments in the index.
     * Its role is to select which merges to do, if any, and return a [ ] describing the merges. It also selects merges to do for
     * forceMerge.
     *
     *
     * Takes effect on subsequent merge selections. Any merges in flight or any merges already
     * registered by the previous [MergePolicy] are not affected.
     */
    fun setMergePolicy(mergePolicy: MergePolicy): LiveIndexWriterConfig {
        requireNotNull(mergePolicy) { "mergePolicy must not be null" }
        this.mergePolicy = mergePolicy
        return this
    }

    /**
     * Set the merged segment warmer. See [IndexReaderWarmer].
     *
     *
     * Takes effect on the next merge.
     */
    fun setMergedSegmentWarmer(mergeSegmentWarmer: IndexReaderWarmer?): LiveIndexWriterConfig {
        this.mergedSegmentWarmer = mergeSegmentWarmer
        return this
    }

    /** Returns the current merged segment warmer. See [IndexReaderWarmer].  */
    fun getMergedSegmentWarmer(): IndexReaderWarmer? {
        return mergedSegmentWarmer
    }

    /** Returns the [OpenMode] set by [IndexWriterConfig.setOpenMode].  */
    fun getOpenMode(): OpenMode {
        return openMode
    }

    val indexDeletionPolicy: IndexDeletionPolicy
        /**
         * Returns the [IndexDeletionPolicy] specified in [ ][IndexWriterConfig.setIndexDeletionPolicy] or the default [ ]/
         */
        get() = delPolicy

    val indexCommit: IndexCommit?
        /**
         * Returns the [IndexCommit] as specified in [ ][IndexWriterConfig.setIndexCommit] or the default, `null` which specifies to
         * open the latest index commit point.
         */
        get() = commit

    /** Expert: returns the [Similarity] implementation used by this [IndexWriter].  */
    fun getSimilarity(): Similarity {
        return similarity
    }

    /**
     * Returns the [MergeScheduler] that was set by [ ][IndexWriterConfig.setMergeScheduler].
     */
    fun getMergeScheduler(): MergeScheduler {
        return mergeScheduler
    }

    /** Returns the current [Codec].  */
    fun getCodec(): Codec? {
        return codec
    }

    /**
     * Returns the current MergePolicy in use by this writer.
     *
     * @see IndexWriterConfig.setMergePolicy
     */
    fun getMergePolicy(): MergePolicy {
        return mergePolicy
    }

    /**
     * @see IndexWriterConfig.setFlushPolicy
     */
    fun getFlushPolicy(): FlushPolicy {
        return flushPolicy
    }

    /**
     * Returns [InfoStream] used for debugging.
     *
     * @see IndexWriterConfig.setInfoStream
     */
    fun getInfoStream(): InfoStream {
        return infoStream
    }

    /**
     * Sets if the [IndexWriter] should pack newly written segments in a compound file. Default
     * is `true`.
     *
     *
     * Use `false` for batch indexing with very large ram buffer settings.
     *
     *
     * **Note: To control compound file usage during segment merges see [ ][MergePolicy.setNoCFSRatio] and [MergePolicy.setMaxCFSSegmentSizeMB]. This
     * setting only applies to newly created segments.**
     */
    fun setUseCompoundFile(useCompoundFile: Boolean): LiveIndexWriterConfig {
        this.useCompoundFile = useCompoundFile
        return this
    }

    /** Get the index-time [Sort] order, applied to all (flushed and merged) segments.  */
    fun getIndexSort(): Sort? {
        return indexSort
    }

    /**
     * Returns a comparator for sorting leaf readers. If not `null`, this comparator is used to
     * sort leaf readers within `DirectoryReader` opened from the `IndexWriter` of this
     * configuration.
     *
     * @return a comparator for sorting leaf readers
     */
    fun getLeafSorter(): java.util.Comparator<LeafReader>? {
        return leafSorter
    }

    /**
     * Expert: sets if indexing threads check for pending flushes on update in order to help our
     * flushing indexing buffers to disk. As a consequence, threads calling [ ][DirectoryReader.openIfChanged] or [IndexWriter.flush]
     * will be the only thread writing segments to disk unless flushes are falling behind. If indexing
     * is stalled due to too many pending flushes indexing threads will help our writing pending
     * segment flushes to disk.
     *
     * @lucene.experimental
     */
    fun setCheckPendingFlushUpdate(checkPendingFlushOnUpdate: Boolean): LiveIndexWriterConfig {
        this.isCheckPendingFlushOnUpdate = checkPendingFlushOnUpdate
        return this
    }

    val indexWriterEventListener: IndexWriterEventListener
        /** Returns the IndexWriterEventListener callback that tracks the key IndexWriter operations.  */
        get() = eventListener

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("analyzer=")
            .append(if (analyzer == null) "null" else analyzer::class.qualifiedName)
            .append("\n")
        sb.append("ramBufferSizeMB=").append(rAMBufferSizeMB).append("\n")
        sb.append("maxBufferedDocs=").append(maxBufferedDocs).append("\n")
        sb.append("mergedSegmentWarmer=").append(getMergedSegmentWarmer()).append("\n")
        sb.append("delPolicy=").append(indexDeletionPolicy::class.qualifiedName).append("\n")
        val commit: IndexCommit? = indexCommit
        sb.append("commit=").append(if (commit == null) "null" else commit).append("\n")
        sb.append("openMode=").append(getOpenMode()).append("\n")
        sb.append("similarity=").append(getSimilarity()::class.qualifiedName).append("\n")
        sb.append("mergeScheduler=").append(getMergeScheduler()).append("\n")
        sb.append("codec=").append(getCodec()).append("\n")
        sb.append("infoStream=").append(getInfoStream()::class.qualifiedName).append("\n")
        sb.append("mergePolicy=").append(getMergePolicy()).append("\n")
        sb.append("readerPooling=").append(readerPooling).append("\n")
        sb.append("perThreadHardLimitMB=").append(rAMPerThreadHardLimitMB).append("\n")
        sb.append("useCompoundFile=").append(useCompoundFile).append("\n")
        sb.append("commitOnClose=").append(commitOnClose).append("\n")
        sb.append("indexSort=").append(getIndexSort()).append("\n")
        sb.append("checkPendingFlushOnUpdate=").append(isCheckPendingFlushOnUpdate).append("\n")
        sb.append("softDeletesField=").append(softDeletesField).append("\n")
        sb.append("maxFullFlushMergeWaitMillis=").append(maxFullFlushMergeWaitMillis).append("\n")
        sb.append("leafSorter=").append(getLeafSorter()).append("\n")
        sb.append("eventListener=").append(indexWriterEventListener).append("\n")
        sb.append("parentField=").append(parentField).append("\n")
        return sb.toString()
    }
}
