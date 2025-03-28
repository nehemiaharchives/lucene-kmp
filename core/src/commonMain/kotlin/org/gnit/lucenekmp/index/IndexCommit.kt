package org.gnit.lucenekmp.index

import kotlinx.io.IOException
import org.gnit.lucenekmp.store.Directory

/**
 * Expert: represents a single commit into an index as seen by the [IndexDeletionPolicy] or
 * [IndexReader].
 *
 *
 * Changes to the content of an index are made visible only after the writer who made that change
 * commits by writing a new segments file (`segments_N`). This point in time, when the
 * action of writing of a new segments file to the directory is completed, is an index commit.
 *
 *
 * Each index commit point has a unique segments file associated with it. The segments file
 * associated with a later index commit point would have a larger N.
 *
 * @lucene.experimental
 */
// TODO: this is now a poor name, because this class also represents a
// point-in-time view from an NRT reader
abstract class IndexCommit
/** Sole constructor. (For invocation by subclass constructors, typically implicit.)  */
protected constructor() : Comparable<IndexCommit?> {
    /** Get the segments file (`segments_N`) associated with this commit point.  */
    abstract val segmentsFileName: String?

    @get:Throws(IOException::class)
    abstract val fileNames: MutableCollection<String?>?

    /** Returns the [Directory] for the index.  */
    abstract val directory: Directory?

    /**
     * Delete this commit point. This only applies when using the commit point in the context of
     * IndexWriter's IndexDeletionPolicy.
     *
     *
     * Upon calling this, the writer is notified that this commit point should be deleted.
     *
     *
     * Decision that a commit-point should be deleted is taken by the [IndexDeletionPolicy]
     * in effect and therefore this should only be called by its [ onInit()][IndexDeletionPolicy.onInit] or [onCommit()][IndexDeletionPolicy.onCommit] methods.
     */
    abstract fun delete()

    /**
     * Returns true if this commit should be deleted; this is only used by [IndexWriter] after
     * invoking the [IndexDeletionPolicy].
     */
    abstract val isDeleted: Boolean

    /** Returns number of segments referenced by this commit.  */
    abstract val segmentCount: Int

    /** Two IndexCommits are equal if both their Directory and versions are equal.  */
    override fun equals(other: Any?): Boolean {
        if (other is IndexCommit) {
            return other.directory === this.directory
                    && other.generation == this.generation
        } else {
            return false
        }
    }

    override fun hashCode(): Int {
        return this.directory.hashCode() + this.generation.hashCode()
    }

    /** Returns the generation (the _N in segments_N) for this IndexCommit  */
    abstract val generation: Long

    @get:Throws(IOException::class)
    abstract val userData: MutableMap<String?, String?>?

    override fun compareTo(commit: IndexCommit?): Int {
        if (this.directory !== commit!!.directory) {
            throw UnsupportedOperationException(
                "cannot compare IndexCommits from different Directory instances"
            )
        }

        val gen = this.generation
        val comgen = commit.generation
        return gen.compareTo(comgen)
    }

    val reader: StandardDirectoryReader?
        /**
         * Package-private API for IndexWriter to init from a commit-point pulled from an NRT or non-NRT
         * reader.
         */
        get() = null
}
