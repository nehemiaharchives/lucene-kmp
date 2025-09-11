package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.index.IndexWriter.IndexReaderWarmer
import org.gnit.lucenekmp.index.IndexWriterConfig.OpenMode
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Sort
import org.gnit.lucenekmp.search.similarities.Similarity
import org.gnit.lucenekmp.util.InfoStream
import org.gnit.lucenekmp.util.Version
import kotlin.concurrent.Volatile

/**
 * Holds all the configuration used by [IndexWriter] with few setters for settings that can be
 * changed on an [IndexWriter] instance "live".
 *
 * @since 4.0
 */
open class LiveIndexWriterConfig internal constructor(open val analyzer: Analyzer) {

    /**
     * Returns the number of buffered added documents that will trigger a flush if enabled.
     *
     * @see .setMaxBufferedDocs
     */
    @Volatile
    open var maxBufferedDocs: Int /* java lucene does not initialize value here */ = IndexWriterConfig.DEFAULT_MAX_BUFFERED_DOCS

    /** Returns the value set by [.setRAMBufferSizeMB] if enabled.  */
    @Volatile
    open var rAMBufferSizeMB: Double /* java lucene does not initialize value here */ = IndexWriterConfig.DEFAULT_RAM_BUFFER_SIZE_MB

    @Volatile
    open var mergedSegmentWarmer: IndexReaderWarmer? = null
        /** Returns the current merged segment warmer. See [IndexReaderWarmer].  */
        fun get(): IndexReaderWarmer? {
            return mergedSegmentWarmer
        }

    /**
     * Set the merged segment warmer. See [IndexReaderWarmer].
     *
     *
     * Takes effect on the next merge.
     */
    open fun setMergedSegmentWarmer(mergeSegmentWarmer: IndexReaderWarmer): LiveIndexWriterConfig {
        this.mergedSegmentWarmer = mergeSegmentWarmer
        return this
    }

    // modified by IndexWriterConfig
    /** [IndexDeletionPolicy] controlling when commit points are deleted.  */
    @Volatile
    protected var delPolicy: IndexDeletionPolicy

    /** [IndexCommit] that [IndexWriter] is opened on.  */
    @Volatile
    protected var commit: IndexCommit?

    /** [OpenMode] that [IndexWriter] is opened with.  */
    @Volatile
    open lateinit var openMode: OpenMode

    /**
     * Return the compatibility version to use for this index.
     *
     * @see IndexWriterConfig.setIndexCreatedVersionMajor
     */
    /** Compatibility version to use for this index.  */
    var createdVersionMajor: Int = Version.LATEST.major
        protected set

    /** [Similarity] to use when encoding norms.  */
    @Volatile
    open lateinit var similarity: Similarity

    /** [MergeScheduler] to use for running merges.  */
    @Volatile
    open lateinit var mergeScheduler: MergeScheduler

    /** [Codec] used to write new segments.  */
    @Volatile
    open lateinit var codec: Codec

    /** [InfoStream] for debugging messages.  */
    @Volatile
    open lateinit var infoStream: InfoStream

    /** [MergePolicy] for selecting merges.  */
    @Volatile
    open lateinit var mergePolicy: MergePolicy

    /**
     * Returns `true` if [IndexWriter] should pool readers even if [ ][DirectoryReader.open] has not been called.
     */
    /** True if readers should be pooled.  */
    @Volatile
    open var readerPooling: Boolean /* java lucene does not set default value here */ = IndexWriterConfig.DEFAULT_READER_POOLING
        protected set

    /** [FlushPolicy] to control when segments are flushed.  */
    @Volatile
    open lateinit var flushPolicy: FlushPolicy

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
    var indexSort: Sort? = null

    /** The comparator for sorting leaf readers.  */
    var leafSorter: Comparator<LeafReader>? = null
        /**
         * Returns a comparator for sorting leaf readers. If not `null`, this comparator is used to
         * sort leaf readers within `DirectoryReader` opened from the `IndexWriter` of this
         * configuration.
         *
         * @return a comparator for sorting leaf readers
         */
        protected set

    /** Returns the field names involved in the index sort  */
    /** The field names involved in the index sort  */
    var indexSortFields: MutableSet<String?> = mutableSetOf()
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
    var checkPendingFlushOnUpdate: Boolean = true
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
        this.rAMBufferSizeMB = IndexWriterConfig.DEFAULT_RAM_BUFFER_SIZE_MB
        maxBufferedDocs = IndexWriterConfig.DEFAULT_MAX_BUFFERED_DOCS
        mergedSegmentWarmer = null
        delPolicy = KeepOnlyLastCommitDeletionPolicy()
        commit = null
        useCompoundFile = IndexWriterConfig.DEFAULT_USE_COMPOUND_FILE_SYSTEM
        openMode = OpenMode.CREATE_OR_APPEND
        similarity = IndexSearcher.defaultSimilarity
        mergeScheduler = ConcurrentMergeScheduler()
        codec = Codec.default
        if (codec == null) {
            throw NullPointerException()
        }
        infoStream = InfoStream.default
        mergePolicy = TieredMergePolicy()
        flushPolicy = FlushByRamOrCountsPolicy()
        readerPooling = IndexWriterConfig.DEFAULT_READER_POOLING
        this.rAMPerThreadHardLimitMB = IndexWriterConfig.DEFAULT_RAM_PER_THREAD_HARD_LIMIT_MB
        maxFullFlushMergeWaitMillis = IndexWriterConfig.DEFAULT_MAX_FULL_FLUSH_MERGE_WAIT_MILLIS
        eventListener = IndexWriterEventListener.NO_OP_LISTENER
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
    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    open fun setRAMBufferSizeMB(ramBufferSizeMB: Double): LiveIndexWriterConfig {
        require(!(ramBufferSizeMB != IndexWriterConfig.DISABLE_AUTO_FLUSH.toDouble() && ramBufferSizeMB <= 0.0)) { "ramBufferSize should be > 0.0 MB when enabled" }
        require(
            !(ramBufferSizeMB == IndexWriterConfig.DISABLE_AUTO_FLUSH.toDouble()
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
    // TODO Synchronized is not supported in KMP, need to think what to do here
    /*@Synchronized*/
    open fun setMaxBufferedDocs(maxBufferedDocs: Int): LiveIndexWriterConfig {
        require(!(maxBufferedDocs != IndexWriterConfig.DISABLE_AUTO_FLUSH && maxBufferedDocs < 2)) { "maxBufferedDocs must at least be 2 when enabled" }
        require(
            !(maxBufferedDocs == IndexWriterConfig.DISABLE_AUTO_FLUSH
                    && this.rAMBufferSizeMB == IndexWriterConfig.DISABLE_AUTO_FLUSH.toDouble())
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
    open fun setMergePolicy(mergePolicy: MergePolicy): LiveIndexWriterConfig {
        //requireNotNull(mergePolicy) { "mergePolicy must not be null" }
        this.mergePolicy = mergePolicy
        return this
    }

    open val indexDeletionPolicy: IndexDeletionPolicy
        /**
         * Returns the [IndexDeletionPolicy] specified in [ ][IndexWriterConfig.setIndexDeletionPolicy] or the default [ ]/
         */
        get() = delPolicy

    open val indexCommit: IndexCommit?
        /**
         * Returns the [IndexCommit] as specified in [ ][IndexWriterConfig.setIndexCommit] or the default, `null` which specifies to
         * open the latest index commit point.
         */
        get() = commit

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
    open fun setUseCompoundFile(useCompoundFile: Boolean): LiveIndexWriterConfig {
        this.useCompoundFile = useCompoundFile
        return this
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
    open fun setCheckPendingFlushUpdate(checkPendingFlushOnUpdate: Boolean): LiveIndexWriterConfig {
        this.checkPendingFlushOnUpdate = checkPendingFlushOnUpdate
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
        sb.append("ramBufferSizeMB=").append(this.rAMBufferSizeMB).append("\n")
        sb.append("maxBufferedDocs=").append(this.maxBufferedDocs).append("\n")
        sb.append("mergedSegmentWarmer=").append(mergedSegmentWarmer).append("\n")
        sb.append("delPolicy=").append(this.indexDeletionPolicy::class.qualifiedName).append("\n")
        val commit: IndexCommit? = this.indexCommit
        sb.append("commit=").append(if (commit == null) "null" else commit).append("\n")
        sb.append("openMode=").append(this.openMode).append("\n")
        sb.append("similarity=").append(this.similarity::class.qualifiedName).append("\n")
        sb.append("mergeScheduler=").append(this.mergeScheduler).append("\n")
        sb.append("codec=").append(this.codec).append("\n")
        sb.append("infoStream=").append(this.infoStream::class.qualifiedName).append("\n")
        sb.append("mergePolicy=").append(this.mergePolicy).append("\n")
        sb.append("readerPooling=").append(this.readerPooling).append("\n")
        sb.append("perThreadHardLimitMB=").append(this.rAMPerThreadHardLimitMB).append("\n")
        sb.append("useCompoundFile=").append(this.useCompoundFile).append("\n")
        sb.append("commitOnClose=").append(this.commitOnClose).append("\n")
        sb.append("indexSort=").append(this.indexSort).append("\n")
        sb.append("checkPendingFlushOnUpdate=").append(this.checkPendingFlushOnUpdate).append("\n")
        sb.append("softDeletesField=").append(this.softDeletesField).append("\n")
        sb.append("maxFullFlushMergeWaitMillis=").append(this.maxFullFlushMergeWaitMillis).append("\n")
        sb.append("leafSorter=").append(leafSorter).append("\n")
        sb.append("eventListener=").append(this.indexWriterEventListener).append("\n")
        sb.append("parentField=").append(this.parentField).append("\n")
        return sb.toString()
    }
}
