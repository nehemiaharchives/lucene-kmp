package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.standard.StandardAnalyzer
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.index.IndexWriter.IndexReaderWarmer
import org.gnit.lucenekmp.jdkport.PrintStream
import org.gnit.lucenekmp.search.Sort
import org.gnit.lucenekmp.search.similarities.Similarity
import org.gnit.lucenekmp.util.InfoStream
import org.gnit.lucenekmp.util.PrintStreamInfoStream
import org.gnit.lucenekmp.util.SetOnce
import org.gnit.lucenekmp.util.SetOnce.AlreadySetException
import org.gnit.lucenekmp.util.Version

/**
 * Holds all the configuration that is used to create an [IndexWriter]. Once [ ] has been created with this object, changes to this object will not affect the [ ] instance. For that, use [LiveIndexWriterConfig] that is returned from [ ][IndexWriter.getConfig].
 *
 *
 * All setter methods return [IndexWriterConfig] to allow chaining settings conveniently,
 * for example:
 *
 * <pre class="prettyprint">
 * IndexWriterConfig conf = new IndexWriterConfig(analyzer);
 * conf.setter1().setter2();
</pre> *
 *
 * @see IndexWriter.getConfig
 * @since 3.1
 */
class IndexWriterConfig
/**
 * Creates a new config that with the provided [Analyzer]. By default, [ ] is used for merging; Note that [TieredMergePolicy] is free to select
 * non-contiguous merges, which means docIDs may not remain monotonic over time. If this is a
 * problem you should switch to [LogByteSizeMergePolicy] or [LogDocMergePolicy].
 */
/**
 * Creates a new config, using [StandardAnalyzer] as the analyzer. By default, [ ] is used for merging; Note that [TieredMergePolicy] is free to select
 * non-contiguous merges, which means docIDs may not remain monotonic over time. If this is a
 * problem you should switch to [LogByteSizeMergePolicy] or [LogDocMergePolicy].
 */
constructor(analyzer: Analyzer = StandardAnalyzer()) :
    LiveIndexWriterConfig(analyzer) {
    /** Specifies the open mode for [IndexWriter].  */
    enum class OpenMode {
        /** Creates a new index or overwrites an existing one.  */
        CREATE,

        /** Opens an existing index.  */
        APPEND,

        /**
         * Creates a new index, if one does not exist, otherwise opens the index and documents will be
         * appended.
         */
        CREATE_OR_APPEND
    }

    // indicates whether this config instance is already attached to a writer.
    // not final so that it can be cloned properly.
    private val writer: SetOnce<IndexWriter> = SetOnce()

    /**
     * Sets the [IndexWriter] this config is attached to.
     *
     * @throws AlreadySetException if this config is already attached to a writer.
     */
    fun setIndexWriter(writer: IndexWriter): IndexWriterConfig {
        check(this.writer.get() == null) { "do not share IndexWriterConfig instances across IndexWriters" }
        this.writer.set(writer)
        return this
    }

    /**
     * Specifies [OpenMode] of the index.
     *
     *
     * Only takes effect when IndexWriter is first created.
     */
    fun setOpenMode(openMode: OpenMode): IndexWriterConfig {
        //requireNotNull(openMode) { "openMode must not be null" }
        this.openMode = openMode
        return this
    }

    public override lateinit var openMode: OpenMode

    /**
     * Expert: set the compatibility version to use for this index. In case the index is created, it
     * will use the given major version for compatibility. It is sometimes useful to set the previous
     * major version for compatibility due to the fact that [IndexWriter.addIndexes] only
     * accepts indices that have been written with the same major version as the current index. If the
     * index already exists, then this value is ignored. Default value is the [ major][Version.major] of the [latest version][Version.LATEST].
     *
     *
     * **NOTE**: Changing the creation version reduces backward compatibility guarantees. For
     * instance an index created with Lucene 8 with a compatibility version of 7 can't be read with
     * Lucene 9 due to the fact that Lucene only supports reading indices created with the current or
     * previous major release.
     *
     * @param indexCreatedVersionMajor the major version to use for compatibility
     */
    fun setIndexCreatedVersionMajor(indexCreatedVersionMajor: Int): IndexWriterConfig {
        require(indexCreatedVersionMajor <= Version.LATEST.major) {
            ("indexCreatedVersionMajor may not be in the future: current major version is "
                    + Version.LATEST.major
                    + ", but got: "
                    + indexCreatedVersionMajor)
        }
        require(indexCreatedVersionMajor >= Version.LATEST.major - 1) {
            ("indexCreatedVersionMajor may not be less than the minimum supported version: "
                    + (Version.LATEST.major - 1)
                    + ", but got: "
                    + indexCreatedVersionMajor)
        }
        this.createdVersionMajor = indexCreatedVersionMajor
        return this
    }

    /**
     * Return the compatibility version to use for this index.
     *
     * @see IndexWriterConfig.setIndexCreatedVersionMajor
     */
    /*fun getIndexCreatedVersionMajor(): Int {
        return createdVersionMajor
    }*/


    /**
     * Expert: allows an optional [IndexDeletionPolicy] implementation to be specified. You can
     * use this to control when prior commits are deleted from the index. The default policy is [ ] which removes all prior commits as soon as a new commit is
     * done (this matches behavior before 2.2). Creating your own policy can allow you to explicitly
     * keep previous "point in time" commits alive in the index for some time, to allow readers to
     * refresh to the new commit without having the old commit deleted out from under them. This is
     * necessary on filesystems like NFS that do not support "delete on last close" semantics, which
     * Lucene's "point in time" search normally relies on.
     *
     *
     * **NOTE:** the deletion policy must not be null.
     *
     *
     * Only takes effect when IndexWriter is first created.
     */
    fun setIndexDeletionPolicy(delPolicy: IndexDeletionPolicy): IndexWriterConfig {
        //requireNotNull(delPolicy) { "indexDeletionPolicy must not be null" }
        this.delPolicy = delPolicy
        return this
    }

    override val indexDeletionPolicy: IndexDeletionPolicy
        get() = delPolicy

    /**
     * Expert: allows to open a certain commit point. The default is null which opens the latest
     * commit point. This can also be used to open [IndexWriter] from a near-real-time reader,
     * if you pass the reader's [DirectoryReader.getIndexCommit].
     *
     *
     * Only takes effect when IndexWriter is first created.
     */
    fun setIndexCommit(commit: IndexCommit): IndexWriterConfig {
        this.commit = commit
        return this
    }

    override val indexCommit: IndexCommit?
        get() = commit

    /**
     * Expert: set the [Similarity] implementation used by this IndexWriter.
     *
     *
     * **NOTE:** the similarity must not be null.
     *
     *
     * Only takes effect when IndexWriter is first created.
     */
    fun setSimilarity(similarity: Similarity): IndexWriterConfig {
        //requireNotNull(similarity) { "similarity must not be null" }
        this.similarity = similarity
        return this
    }

    override lateinit var similarity: Similarity

    /**
     * Expert: sets the merge scheduler used by this writer. The default is [ ].
     *
     *
     * **NOTE:** the merge scheduler must not be null.
     *
     *
     * Only takes effect when IndexWriter is first created.
     */
    fun setMergeScheduler(mergeScheduler: MergeScheduler): IndexWriterConfig {
        //requireNotNull(mergeScheduler) { "mergeScheduler must not be null" }
        this.mergeScheduler = mergeScheduler
        return this
    }

    public override lateinit var mergeScheduler: MergeScheduler

    /**
     * Set the [Codec].
     *
     *
     * Only takes effect when IndexWriter is first created.
     */
    fun setCodec(codec: Codec): IndexWriterConfig {
        //requireNotNull(codec) { "codec must not be null" }
        this.codec = codec
        return this
    }

    override lateinit var codec: Codec

    override lateinit var mergePolicy: MergePolicy

    /**
     * By default, IndexWriter does not pool the SegmentReaders it must open for deletions and
     * merging, unless a near-real-time reader has been obtained by calling [ ][DirectoryReader.open]. This method lets you enable pooling without getting a
     * near-real-time reader. NOTE: if you set this to false, IndexWriter will still pool readers once
     * [DirectoryReader.open] is called.
     *
     *
     * Only takes effect when IndexWriter is first created.
     */
    fun setReaderPooling(readerPooling: Boolean): IndexWriterConfig {
        this.readerPooling = readerPooling
        return this
    }

    override var readerPooling: Boolean /* init code was not set in java lucene */ = DEFAULT_READER_POOLING

    /**
     * Expert: Controls when segments are flushed to disk during indexing. The [FlushPolicy]
     * initialized during [IndexWriter] instantiation and once initialized the given instance is
     * bound to this [IndexWriter] and should not be used with another writer.
     *
     * @see .setMaxBufferedDocs
     * @see .setRAMBufferSizeMB
     */
    fun setFlushPolicy(flushPolicy: FlushPolicy): IndexWriterConfig {
        //requireNotNull(flushPolicy) { "flushPolicy must not be null" }
        this.flushPolicy = flushPolicy
        return this
    }

    /**
     * Expert: Sets the maximum memory consumption per thread triggering a forced flush if exceeded. A
     * [DocumentsWriterPerThread] is forcefully flushed once it exceeds this limit even if the
     * [.getRAMBufferSizeMB] has not been exceeded. This is a safety limit to prevent a [ ] from address space exhaustion due to its internal 32 bit signed
     * integer based memory addressing. The given value must be less that 2GB (2048MB)
     *
     * @see .DEFAULT_RAM_PER_THREAD_HARD_LIMIT_MB
     */
    var perThreadHardLimitMB: Int = DEFAULT_RAM_PER_THREAD_HARD_LIMIT_MB

    fun setRAMPerThreadHardLimitMB(perThreadHardLimitMB: Int): IndexWriterConfig {
        require(!(perThreadHardLimitMB <= 0 || perThreadHardLimitMB >= 2048)) { "PerThreadHardLimit must be greater than 0 and less than 2048MB" }
        this.perThreadHardLimitMB = perThreadHardLimitMB
        return this
    }

    /**
     * Information about merges, deletes and a message when maxFieldLength is reached will be printed
     * to this. Must not be null, but [InfoStream.NO_OUTPUT] may be used to suppress output.
     */
    fun setInfoStream(infoStream: InfoStream): IndexWriterConfig {
        /*requireNotNull(infoStream) {
            ("Cannot set InfoStream implementation to null. "
                    + "To disable logging use InfoStream.NO_OUTPUT")
        }*/
        this.infoStream = infoStream
        return this
    }

    /** Convenience method that uses [PrintStreamInfoStream]. Must not be null.  */
    fun setInfoStream(printStream: PrintStream): IndexWriterConfig {
        //requireNotNull(printStream) { "printStream must not be null" }
        return setInfoStream(PrintStreamInfoStream(printStream))
    }

    override fun setMergePolicy(mergePolicy: MergePolicy): IndexWriterConfig {
        return super.setMergePolicy(mergePolicy) as IndexWriterConfig
    }

    override fun setMaxBufferedDocs(maxBufferedDocs: Int): IndexWriterConfig {
        return super.setMaxBufferedDocs(maxBufferedDocs) as IndexWriterConfig
    }

    override fun setMergedSegmentWarmer(mergeSegmentWarmer: IndexReaderWarmer): IndexWriterConfig {
        return super.setMergedSegmentWarmer(mergeSegmentWarmer) as IndexWriterConfig
    }

    override fun setRAMBufferSizeMB(ramBufferSizeMB: Double): IndexWriterConfig {
        return super.setRAMBufferSizeMB(ramBufferSizeMB) as IndexWriterConfig
    }

    override fun setUseCompoundFile(useCompoundFile: Boolean): IndexWriterConfig {
        return super.setUseCompoundFile(useCompoundFile) as IndexWriterConfig
    }

    /**
     * Sets if calls [IndexWriter.close] should first commit before closing. Use `true
    ` *  to match behavior of Lucene 4.x.
     */
    fun setCommitOnClose(commitOnClose: Boolean): IndexWriterConfig {
        this.commitOnClose = commitOnClose
        return this
    }

    /**
     * Expert: sets the amount of time to wait for merges (during [IndexWriter.commit] or [ ][IndexWriter.getReader]) returned by MergePolicy.findFullFlushMerges(...). If
     * this time is reached, we proceed with the commit based on segments merged up to that point. The
     * merges are not aborted, and will still run to completion independent of the commit or getReader
     * call, like natural segment merges. The default is `
     * {@value IndexWriterConfig#DEFAULT_MAX_FULL_FLUSH_MERGE_WAIT_MILLIS}`.
     *
     *
     * Note: Which segments would get merged depends on the implementation of [ ][MergePolicy.findFullFlushMerges]
     *
     *
     * Note: Set to 0 to disable merging on full flush.
     *
     *
     * Note: If [SerialMergeScheduler] is used and a non-zero timout is configured,
     * full-flush merges will always wait for the merge to finish without honoring the configured
     * timeout.
     */
    fun setMaxFullFlushMergeWaitMillis(maxFullFlushMergeWaitMillis: Long): IndexWriterConfig {
        this.maxFullFlushMergeWaitMillis = maxFullFlushMergeWaitMillis
        return this
    }

    /** Set the [Sort] order to use for all (flushed and merged) segments.  */
    fun setIndexSort(sort: Sort): IndexWriterConfig {
        for (sortField in sort.sort) {
            requireNotNull(sortField.getIndexSorter()) { "Cannot sort index with sort field $sortField" }
        }
        this.indexSort = sort
        this.indexSortFields = sort.sort.map { it.field }.toMutableSet()
        return this
    }

    /**
     * Set the comparator for sorting leaf readers. A DirectoryReader opened from a IndexWriter with
     * this configuration will have its leaf readers sorted with the provided leaf sorter.
     *
     * @param leafSorter – a comparator for sorting leaf readers
     * @return IndexWriterConfig with leafSorter set.
     */
    fun setLeafSorter(leafSorter: Comparator<LeafReader>): IndexWriterConfig {
        this.leafSorter = leafSorter
        return this
    }

    override fun toString(): String {
        val sb = StringBuilder(super.toString())
        sb.append("writer=").append(writer.get()).append("\n")
        return sb.toString()
    }

    override fun setCheckPendingFlushUpdate(checkPendingFlushOnUpdate: Boolean): IndexWriterConfig {
        return super.setCheckPendingFlushUpdate(checkPendingFlushOnUpdate) as IndexWriterConfig
    }

    /**
     * Sets the soft-deletes field. A soft-delete field in Lucene is a doc-values field that marks a
     * document as soft-deleted, if a document has at least one value in that field. If a document is
     * marked as soft-deleted, the document is treated as if it has been hard-deleted through the
     * IndexWriter API ([IndexWriter.deleteDocuments]. Merges will reclaim soft-deleted
     * as well as hard-deleted documents, and index readers obtained from the IndexWriter will reflect
     * all deleted documents in its live docs. If soft-deletes are used, documents must be indexed via
     * [IndexWriter.softUpdateDocument]. Deletes are applied via
     * [IndexWriter.updateDocValues].
     *
     *
     * Soft deletes allow to retain documents across merges if the merge policy modifies the live
     * docs of a merge reader. [SoftDeletesRetentionMergePolicy] for instance allows to specify
     * an arbitrary query to mark all documents that should survive the merge. This can be used, for
     * example, to keep all document modifications for a certain time interval or the last N
     * operations if some kind of sequence ID is available in the index.
     *
     *
     * Currently there is no API support to un-delete a soft-deleted document. In order to
     * un-delete a document, it must be re-indexed using [IndexWriter.softUpdateDocument].
     *
     *
     * The default value for this is `null`, which disables soft-deletes. If
     * soft-deletes are enabled, documents can still be hard-deleted. Hard-deleted documents won't be
     * considered as soft-deleted even if they have a value in the soft-deletes field.
     *
     * @see .getSoftDeletesField
     */
    fun setSoftDeletesField(softDeletesField: String): IndexWriterConfig {
        this.softDeletesField = softDeletesField
        return this
    }

    /** Set event listener to record key events in IndexWriter  */
    fun setIndexWriterEventListener(
        eventListener: IndexWriterEventListener
    ): IndexWriterConfig {
        this.eventListener = eventListener
        return this
    }

    /**
     * Sets the parent document field. If this optional property is set, IndexWriter will add an
     * internal field to every root document added to the index writer. A document is considered a
     * parent document if it's the last document in a document block indexed via [ ][IndexWriter.addDocuments] or [IndexWriter.updateDocuments] and
     * its relatives. Additionally, all individual documents added via the single document methods
     * ([IndexWriter.addDocuments] etc.) are also considered parent documents. This
     * property is optional for all indices that don't use document blocks in combination with index
     * sorting. In order to maintain the API guarantee that the document order of a block is not
     * altered by the [IndexWriter] a marker for parent documents is required.
     */
    fun setParentField(parentField: String): IndexWriterConfig {
        this.parentField = parentField
        return this
    }

    companion object {
        /** Denotes a flush trigger is disabled.  */
        const val DISABLE_AUTO_FLUSH: Int = -1

        /** Disabled by default (because IndexWriter flushes by RAM usage by default).  */
        const val DEFAULT_MAX_BUFFERED_DELETE_TERMS: Int = DISABLE_AUTO_FLUSH

        /** Disabled by default (because IndexWriter flushes by RAM usage by default).  */
        const val DEFAULT_MAX_BUFFERED_DOCS: Int = DISABLE_AUTO_FLUSH

        /**
         * Default value is 16 MB (which means flush when buffered docs consume approximately 16 MB RAM).
         */
        const val DEFAULT_RAM_BUFFER_SIZE_MB: Double = 16.0

        /** Default setting (true) for [.setReaderPooling].  */ // We changed this default to true with concurrent deletes/updates (LUCENE-7868),
        // because we will otherwise need to open and close segment readers more frequently.
        // False is still supported, but will have worse performance since readers will
        // be forced to aggressively move all state to disk.
        const val DEFAULT_READER_POOLING: Boolean = true

        /** Default value is 1945. Change using [.setRAMPerThreadHardLimitMB]  */
        const val DEFAULT_RAM_PER_THREAD_HARD_LIMIT_MB: Int = 1945

        /**
         * Default value for compound file system for newly written segments (set to `true`).
         * For batch indexing with very large ram buffers use `false`
         */
        const val DEFAULT_USE_COMPOUND_FILE_SYSTEM: Boolean = true

        /** Default value for whether calls to [IndexWriter.close] include a commit.  */
        const val DEFAULT_COMMIT_ON_CLOSE: Boolean = true

        /**
         * Default value for time to wait for merges on commit or getReader (when using a [ ] that implements [MergePolicy.findFullFlushMerges]).
         */
        const val DEFAULT_MAX_FULL_FLUSH_MERGE_WAIT_MILLIS: Long = 500
    }
}
