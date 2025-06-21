package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.index.StandardDirectoryReader.ReaderCommit
import okio.FileNotFoundException
import okio.IOException
import org.gnit.lucenekmp.jdkport.NoSuchFileException
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.store.Directory
import kotlin.jvm.JvmOverloads

/**
 * DirectoryReader is an implementation of [CompositeReader] that can read indexes in a [ ].
 *
 *
 * DirectoryReader instances are usually constructed with a call to one of the static `
 * open()` methods, e.g. [.open].
 *
 *
 * For efficiency, in this API documents are often referred to via *document numbers*,
 * non-negative integers which each name a unique document in the index. These document numbers are
 * ephemeral -- they may change as documents are added to and deleted from an index. Clients should
 * thus not rely on a given document having the same number between sessions.
 *
 *
 * <a id="thread-safety"></a>
 *
 *
 * **NOTE**: [IndexReader] instances are completely thread safe, meaning multiple
 * threads can call any of its methods, concurrently. If your application requires external
 * synchronization, you should **not** synchronize on the `IndexReader` instance; use
 * your own (non-Lucene) objects instead.
 */
abstract class DirectoryReader protected constructor(
    /** The index directory.  */
    protected val directory: Directory,
    segmentReaders: Array<out LeafReader>,
    leafSorter: Comparator<out LeafReader>?
) : BaseCompositeReader<LeafReader>(segmentReaders, leafSorter) {

    /** Returns the directory this index resides in.  */
    fun directory(): Directory {
        // Don't ensureOpen here -- in certain cases, when a
        // cloned/reopened reader needs to commit, it may call
        // this method on the closed original reader
        return directory
    }

    /**
     * Implement this method to support [.openIfChanged]. If this reader does
     * not support reopen, return `null`, so client code is happy. This should be consistent
     * with [.isCurrent] (should always return `true`) if reopen is not supported.
     *
     * @throws IOException if there is a low-level IO error
     * @return null if there are no changes; else, a new DirectoryReader instance.
     */
    @Throws(IOException::class)
    protected abstract fun doOpenIfChanged(): DirectoryReader?

    /**
     * Implement this method to support [.openIfChanged]. If this
     * reader does not support reopen from a specific [IndexCommit], throw [ ].
     *
     * @throws IOException if there is a low-level IO error
     * @return null if there are no changes; else, a new DirectoryReader instance.
     */
    @Throws(IOException::class)
    protected abstract fun doOpenIfChanged(commit: IndexCommit?): DirectoryReader?

    /**
     * Implement this method to support [.openIfChanged].
     * If this reader does not support reopen from [IndexWriter], throw [ ].
     *
     * @throws IOException if there is a low-level IO error
     * @return null if there are no changes; else, a new DirectoryReader instance.
     */
    @Throws(IOException::class)
    protected abstract fun doOpenIfChanged(
        writer: IndexWriter,
        applyAllDeletes: Boolean
    ): DirectoryReader?

    /**
     * Version number when this IndexReader was opened.
     *
     *
     * This method returns the version recorded in the commit that the reader opened. This version
     * is advanced every time a change is made with [IndexWriter].
     */
    abstract val version: Long

    abstract val isCurrent: Boolean

    abstract val indexCommit: IndexCommit

    companion object {
        /**
         * Returns a IndexReader reading the index in the given Directory
         *
         * @param directory the index directory
         * @throws IOException if there is a low-level IO error
         */
        @Throws(IOException::class)
        fun open(directory: Directory): DirectoryReader {
            return StandardDirectoryReader.open(directory, null, null)
        }

        /**
         * Returns a IndexReader for the index in the given Directory
         *
         * @param directory the index directory
         * @param leafSorter a comparator for sorting leaf readers. Providing leafSorter is useful for
         * indices on which it is expected to run many queries with particular sort criteria (e.g. for
         * time-based indices this is usually a descending sort on timestamp). In this case `leafSorter` should sort leaves according to this sort criteria. Providing leafSorter allows
         * to speed up this particular type of sort queries by early terminating while iterating
         * through segments and segments' documents.
         * @throws IOException if there is a low-level IO error
         */
        @Throws(IOException::class)
        fun open(
            directory: Directory,
            leafSorter: Comparator<LeafReader>
        ): DirectoryReader {
            return StandardDirectoryReader.open(directory, null, leafSorter)
        }

        /**
         * Expert: open a near real time IndexReader from the [IndexWriter],
         * controlling whether past deletions should be applied.
         *
         * @param writer The IndexWriter to open from
         * @param applyAllDeletes If true, all buffered deletes will be applied (made visible) in the
         * returned reader. If false, the deletes are not applied but remain buffered (in IndexWriter)
         * so that they will be applied in the future. Applying deletes can be costly, so if your app
         * can tolerate deleted documents being returned you might gain some performance by passing
         * false.
         * @param writeAllDeletes If true, new deletes will be written down to index files instead of
         * carried over from writer to reader directly in heap
         * @see .open
         * @lucene.experimental
         */
        /**
         * Open a near real time IndexReader from the [IndexWriter].
         *
         * @param writer The IndexWriter to open from
         * @return The new IndexReader
         * @throws CorruptIndexException if the index is corrupt
         * @throws IOException if there is a low-level IO error
         * @see .openIfChanged
         * @lucene.experimental
         */
        @JvmOverloads
        @Throws(IOException::class)
        fun open(
            writer: IndexWriter,
            applyAllDeletes: Boolean = true,
            writeAllDeletes: Boolean = false
        ): DirectoryReader {
            return writer.getReader(applyAllDeletes, writeAllDeletes)
        }

        /**
         * Expert: returns an IndexReader reading the index in the given [IndexCommit].
         *
         * @param commit the commit point to open
         * @throws IOException if there is a low-level IO error
         */
        @Throws(IOException::class)
        fun open(commit: IndexCommit): DirectoryReader {
            return StandardDirectoryReader.open(commit.directory, commit, null)
        }

        /**
         * Expert: returns an IndexReader reading the index on the given [IndexCommit]. This method
         * allows to open indices that were created with a Lucene version older than N-1 provided that all
         * codecs for this index are available in the classpath and the segment file format used was
         * created with Lucene 7 or newer. Users of this API must be aware that Lucene doesn't guarantee
         * semantic compatibility for indices created with versions older than N-1. All backwards
         * compatibility aside from the file format is optional and applied on a best effort basis.
         *
         * @param commit the commit point to open
         * @param minSupportedMajorVersion the minimum supported major index version
         * @param leafSorter a comparator for sorting leaf readers. Providing leafSorter is useful for
         * indices on which it is expected to run many queries with particular sort criteria (e.g. for
         * time-based indices, this is usually a descending sort on timestamp). In this case `leafSorter` should sort leaves according to this sort criteria. Providing leafSorter allows
         * to speed up this particular type of sort queries by early terminating while iterating
         * through segments and segments' documents
         * @throws IOException if there is a low-level IO error
         */
        @Throws(IOException::class)
        fun open(
            commit: IndexCommit,
            minSupportedMajorVersion: Int,
            leafSorter: Comparator<LeafReader>
        ): DirectoryReader {
            return StandardDirectoryReader.open(
                commit.directory, minSupportedMajorVersion, commit, leafSorter
            )
        }

        /**
         * If the index has changed since the provided reader was opened, open and return a new reader;
         * else, return null. The new reader, if not null, will be the same type of reader as the previous
         * one, ie an NRT reader will open a new NRT reader etc.
         *
         *
         * This method is typically far less costly than opening a fully new `DirectoryReader
        ` *  as it shares resources (for example sub-readers) with the provided `
         * DirectoryReader`, when possible.
         *
         *
         * The provided reader is not closed (you are responsible for doing so); if a new reader is
         * returned you also must eventually close it. Be sure to never close a reader while other threads
         * are still using it; see [SearcherManager] to simplify managing this.
         *
         * @throws CorruptIndexException if the index is corrupt
         * @throws IOException if there is a low-level IO error
         * @return null if there are no changes; else, a new DirectoryReader instance which you must
         * eventually close
         */
        @Throws(IOException::class)
        fun openIfChanged(oldReader: DirectoryReader): DirectoryReader? {
            val newReader = oldReader.doOpenIfChanged()
            assert(newReader !== oldReader)
            return newReader
        }

        /**
         * If the IndexCommit differs from what the provided reader is searching, open and return a new
         * reader; else, return null.
         *
         * @see .openIfChanged
         */
        @Throws(IOException::class)
        fun openIfChanged(oldReader: DirectoryReader, commit: IndexCommit): DirectoryReader? {
            val newReader = oldReader.doOpenIfChanged(commit)
            assert(newReader !== oldReader)
            return newReader
        }

        /**
         * Expert: Opens a new reader, if there are any changes, controlling whether past deletions should
         * be applied.
         *
         * @see .openIfChanged
         * @param writer The IndexWriter to open from
         * @param applyAllDeletes If true, all buffered deletes will be applied (made visible) in the
         * returned reader. If false, the deletes are not applied but remain buffered (in IndexWriter)
         * so that they will be applied in the future. Applying deletes can be costly, so if your app
         * can tolerate deleted documents being returned you might gain some performance by passing
         * false.
         * @throws IOException if there is a low-level IO error
         * @lucene.experimental
         */
        /**
         * Expert: If there changes (committed or not) in the [IndexWriter] versus what the provided
         * reader is searching, then open and return a new IndexReader searching both committed and
         * uncommitted changes from the writer; else, return null (though, the current implementation
         * never returns null).
         *
         *
         * This provides "near real-time" searching, in that changes made during an [IndexWriter]
         * session can be quickly made available for searching without closing the writer or calling
         * [IndexWriter.commit].
         *
         *
         * It's *near* real-time because there is no hard guarantee on how quickly you can get a
         * new reader after making changes with IndexWriter. You'll have to experiment in your situation
         * to determine if it's fast enough. As this is a new and experimental feature, please report back
         * on your findings so we can learn, improve and iterate.
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
         * @return DirectoryReader that covers entire index plus all changes made so far by this
         * IndexWriter instance, or null if there are no new changes
         * @param writer The IndexWriter to open from
         * @throws IOException if there is a low-level IO error
         * @lucene.experimental
         */
        @JvmOverloads
        @Throws(IOException::class)
        fun openIfChanged(
            oldReader: DirectoryReader, writer: IndexWriter, applyAllDeletes: Boolean = true
        ): DirectoryReader? {
            val newReader = oldReader.doOpenIfChanged(writer, applyAllDeletes)
            assert(newReader !== oldReader)
            return newReader
        }

        /**
         * Returns all commit points that exist in the Directory. Normally, because the default is [ ], there would be only one commit point. But if you're using a
         * custom [IndexDeletionPolicy] then there could be many commits. Once you have a given
         * commit, you can open a reader on it by calling [DirectoryReader.open] There
         * must be at least one commit in the Directory, else this method throws [ ]. Note that if a commit is in progress while this method is running,
         * that commit may or may not be returned.
         *
         * @return a sorted list of [IndexCommit]s, from oldest to latest.
         */
        @Throws(IOException::class)
        fun listCommits(dir: Directory): MutableList<IndexCommit> {
            val files: Array<String> = dir.listAll()

            val commits: MutableList<IndexCommit> = ArrayList()

            val latest: SegmentInfos = SegmentInfos.readLatestCommit(dir, 0)
            val currentGen: Long = latest.generation

            commits.add(ReaderCommit(null, latest, dir))

            for (i in files.indices) {
                val fileName = files[i]

                if (fileName.startsWith(IndexFileNames.SEGMENTS)
                    && SegmentInfos.generationFromSegmentsFileName(fileName) < currentGen
                ) {
                    var sis: SegmentInfos? = null
                    try {
                        // IOException allowed to throw there, in case
                        // segments_N is corrupt
                        sis = SegmentInfos.readCommit(dir, fileName, 0)
                    } catch (fnfe: FileNotFoundException) {
                        // LUCENE-948: on NFS (and maybe others), if
                        // you have writers switching back and forth
                        // between machines, it's very likely that the
                        // dir listing will be stale and will claim a
                        // file segments_X exists when in fact it
                        // doesn't.  So, we catch this and handle it
                        // as if the file does not exist
                    } catch (fnfe: NoSuchFileException) {
                    }

                    if (sis != null) {
                        commits.add(ReaderCommit(null, sis, dir))
                    }
                }
            }

            // Ensure that the commit points are sorted in ascending order.
            /*Collections.sort<IndexCommit>(commits)*/
            commits.sort()

            return commits
        }

        /**
         * Returns `true` if an index likely exists at the specified directory. Note that if a
         * corrupt index exists, or if an index in the process of committing the return value is not
         * reliable.
         *
         * @param directory the directory to check for an index
         * @return `true` if an index exists; `false` otherwise
         */
        @Throws(IOException::class)
        fun indexExists(directory: Directory): Boolean {
            // LUCENE-2812, LUCENE-2727, LUCENE-4738: this logic will
            // return true in cases that should arguably be false,
            // such as only IW.prepareCommit has been called, or a
            // corrupt first commit, but it's too deadly to make
            // this logic "smarter" and risk accidentally returning
            // false due to various cases like file description
            // exhaustion, access denied, etc., because in that
            // case IndexWriter may delete the entire index.  It's
            // safer to err towards "index exists" than try to be
            // smart about detecting not-yet-fully-committed or
            // corrupt indices.  This means that IndexWriter will
            // throw an exception on such indices and the app must
            // resolve the situation manually:
            val files: Array<String> = directory.listAll()

            val prefix: String = IndexFileNames.SEGMENTS + "_"
            for (file in files) {
                if (file.startsWith(prefix)) {
                    return true
                }
            }
            return false
        }
    }
}
