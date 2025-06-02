package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.search.Sort
import org.gnit.lucenekmp.search.SortField
import org.gnit.lucenekmp.store.Directory

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

class IndexWriter/*: AutoCloseable, TwoPhaseCommit, Accountable, MergePolicy.MergeContext*/{

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
        fun warm(reader: LeafReader?)
    }

    /**
     * Record that the files referenced by this [SegmentInfos] are still in use.
     *
     * @lucene.internal
     */
    @Throws(IOException::class)
    fun incRefDeleter(segmentInfos: SegmentInfos) {
        /*ensureOpen()
        deleter.incRef(segmentInfos, false)
        if (infoStream.isEnabled("IW")) {
            infoStream.message(
                "IW",
                ("incRefDeleter for NRT reader version="
                        + segmentInfos.getVersion()
                        + " segments="
                        + segString(segmentInfos))
            )
        }*/
    }

    private var closed = false

    fun isClosed(): Boolean {
        return closed
    }

    fun nrtIsCurrent(infos: SegmentInfos ): Boolean {
        return true
    }

    /**
     * Record that the files referenced by this [SegmentInfos] are no longer in use. Only call
     * this if you are sure you previously called [.incRefDeleter].
     *
     * @lucene.internal
     */
    @Throws(IOException::class)
    fun decRefDeleter(segmentInfos: SegmentInfos) {
        /*ensureOpen()
        deleter.decRef(segmentInfos)
        if (infoStream.isEnabled("IW")) {
            infoStream.message(
                "IW",
                ("decRefDeleter for NRT reader version="
                        + segmentInfos.getVersion()
                        + " segments="
                        + segString(segmentInfos))
            )
        }*/
    }


    // The instance that was passed to the constructor. It is saved only in order
    // to allow users to query an IndexWriter settings.
    val config: LiveIndexWriterConfig? = null

    private val directoryOrig: Directory? = null // original user directory


    /** Returns the Directory used by this index.  */
    fun getDirectory(): Directory {
        // return the original directory the user supplied, unwrapped.
        return directoryOrig!!
    }

    fun getReader(applyAllDeletes: Boolean, writeAllDeletes: Boolean): DirectoryReader{
        throw UnsupportedOperationException()
    }

    companion object{

        /**
         * Hard limit on maximum number of documents that may be added to the index. If you try to add
         * more than this you'll hit `IllegalArgumentException`.
         */
        // We defensively subtract 128 to be well below the lowest
        // ArrayUtil.MAX_ARRAY_LENGTH on "typical" JVMs.  We don't just use
        // ArrayUtil.MAX_ARRAY_LENGTH here because this can vary across JVMs:
        const val MAX_DOCS: Int = Int.Companion.MAX_VALUE - 128

        // Use package-private instance var to enforce the limit so testing
        // can use less electricity:
        var actualMaxDocs: Int = MAX_DOCS

        /** Maximum value of the token position in an indexed field.  */
        const val MAX_POSITION: Int = Int.Companion.MAX_VALUE - 128

        /** Returns true if `indexSort` is a prefix of `otherSort`.  */
        fun isCongruentSort(indexSort: Sort, otherSort: Sort): Boolean {
            val fields1: Array<SortField> = indexSort.sort
            val fields2: Array<SortField> = otherSort.sort
            if (fields1.size > fields2.size) {
                return false
            }
            return fields1.asList() == fields2.asList().subList(0, fields1.size)
        }

    }


}
