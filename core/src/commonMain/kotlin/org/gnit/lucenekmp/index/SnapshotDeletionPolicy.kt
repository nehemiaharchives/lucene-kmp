package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.jdkport.ReentrantLock
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.withLock
import org.gnit.lucenekmp.store.Directory

/**
 * An [IndexDeletionPolicy] that wraps any other [IndexDeletionPolicy] and adds the
 * ability to hold and later release snapshots of an index. While a snapshot is held, the [ ]
 * will not remove any files associated with it even if the index is otherwise being actively,
 * arbitrarily changed. Because we wrap another arbitrary [IndexDeletionPolicy], this gives you the freedom to continue using whatever [IndexDeletionPolicy] you would
 * normally want to use with your index.
 *
 * <p>This class maintains all snapshots in-memory, and so the information is not persisted and not
 * protected against system failures. If persistence is important, you can use [ ].
 *
 * @lucene.experimental
 */
open class SnapshotDeletionPolicy(
    /** Wrapped [IndexDeletionPolicy]  */
    private val primary: IndexDeletionPolicy
) : IndexDeletionPolicy() {
    private val lock = ReentrantLock()

    /** Records how many snapshots are held against each commit generation */
    protected val refCounts: MutableMap<Long, Int> = HashMap()

    /** Used to map gen to IndexCommit.  */
    protected val indexCommits: MutableMap<Long, IndexCommit> = HashMap()

    /** Most recently committed [IndexCommit].  */
    protected var lastCommit: IndexCommit? = null

    /** Used to detect misuse  */
    private var initCalled = false

    @Throws(IOException::class)
    override fun onCommit(commits: MutableList<out IndexCommit>) {
        lock.withLock {
            primary.onCommit(wrapCommits(commits))
            lastCommit = commits[commits.size - 1]
        }
    }

    @Throws(IOException::class)
    override fun onInit(commits: MutableList<out IndexCommit>) {
        lock.withLock {
            initCalled = true
            primary.onInit(wrapCommits(commits))
            for (commit in commits) {
                if (refCounts.containsKey(commit.generation)) {
                    indexCommits[commit.generation] = commit
                }
            }
            if (commits.isNotEmpty()) {
                lastCommit = commits[commits.size - 1]
            }
        }
    }

    /**
     * Release a snapshotted commit.
     *
     * @param commit the commit previously returned by [.snapshot]
     */
    @Throws(IOException::class)
    open fun release(commit: IndexCommit) {
        lock.withLock {
            val gen = commit.generation
            releaseGen(gen)
        }
    }

    /** Release a snapshot by generation.  */
    @Throws(IOException::class)
    protected fun releaseGen(gen: Long) {
        lock.withLock {
            if (!initCalled) {
                throw IllegalStateException(
                    "this instance is not being used by IndexWriter; be sure to use the instance returned from writer.getConfig().getIndexDeletionPolicy()"
                )
            }
            val refCount = refCounts[gen]
                ?: throw IllegalArgumentException("commit gen=$gen is not currently snapshotted")
            assert(refCount > 0)
            val newRefCount = refCount - 1
            if (newRefCount == 0) {
                refCounts.remove(gen)
                indexCommits.remove(gen)
            } else {
                refCounts[gen] = newRefCount
            }
        }
    }

    /** Increments the refCount for this [IndexCommit].  */
    protected fun incRef(ic: IndexCommit) {
        lock.withLock {
            val gen = ic.generation
            val refCount = refCounts[gen]
            val refCountInt: Int =
                if (refCount == null) {
                    indexCommits[gen] = lastCommit!!
                    0
                } else {
                    refCount
                }
            refCounts[gen] = refCountInt + 1
        }
    }

    /**
     * Snapshots the last commit and returns it. Once a commit is 'snapshotted,' it is protected from
     * deletion (as long as this [IndexDeletionPolicy] is used). The snapshot can be removed by
     * calling [.release] followed by a call to [ ][IndexWriter.deleteUnusedFiles].
     *
     * <p><b>NOTE:</b> while the snapshot is held, the files it references will not be deleted, which
     * will consume additional disk space in your index. If you take a snapshot at a particularly bad
     * time (say just before you call forceMerge) then in the worst case this could consume an extra
     * 1X of your total index size, until you release the snapshot.
     *
     * @throws IllegalStateException if this index does not have any commits yet
     * @return the [IndexCommit] that was snapshotted.
     */
    @Throws(IOException::class)
    open fun snapshot(): IndexCommit {
        return lock.withLock {
            if (!initCalled) {
                throw IllegalStateException(
                    "this instance is not being used by IndexWriter; be sure to use the instance returned from writer.getConfig().getIndexDeletionPolicy()"
                )
            }
            val lastCommit = lastCommit ?: throw IllegalStateException("No index commit to snapshot")

            incRef(lastCommit)

            lastCommit
        }
    }

    /** Returns all IndexCommits held by at least one snapshot.  */
    fun getSnapshots(): MutableList<IndexCommit> {
        return lock.withLock {
            ArrayList(indexCommits.values)
        }
    }

    /** Returns the total number of snapshots currently held.  */
    fun getSnapshotCount(): Int {
        return lock.withLock {
            var total = 0
            for (refCount in refCounts.values) {
                total += refCount
            }
            total
        }
    }

    /**
     * Retrieve an [IndexCommit] from its generation; returns null if this IndexCommit is not
     * currently snapshotted
     */
    fun getIndexCommit(gen: Long): IndexCommit? {
        return lock.withLock {
            indexCommits[gen]
        }
    }

    /** Wraps each [IndexCommit] as a [SnapshotCommitPoint].  */
    private fun wrapCommits(commits: MutableList<out IndexCommit>): MutableList<IndexCommit> {
        val wrappedCommits: MutableList<IndexCommit> = ArrayList(commits.size)
        for (ic in commits) {
            wrappedCommits.add(SnapshotCommitPoint(ic))
        }
        return wrappedCommits
    }

    /** Wraps a provided [IndexCommit] and prevents it from being deleted.  */
    private inner class SnapshotCommitPoint(
        /** The [IndexCommit] we are preventing from deletion.  */
        protected var cp: IndexCommit
    ) : IndexCommit() {

        override fun toString(): String {
            return "SnapshotDeletionPolicy.SnapshotCommitPoint($cp)"
        }

        override fun delete() {
            lock.withLock {
                // Suppress the delete request if this commit point is
                // currently snapshotted.
                if (!refCounts.containsKey(cp.generation)) {
                    cp.delete()
                }
            }
        }

        override val directory: Directory
            get() = cp.directory

        override val fileNames: MutableCollection<String>
            get() = cp.fileNames

        override val generation: Long
            get() = cp.generation

        override val segmentsFileName: String?
            get() = cp.segmentsFileName

        override val userData: MutableMap<String, String>
            get() = cp.userData

        override val isDeleted: Boolean
            get() = cp.isDeleted

        override val segmentCount: Int
            get() = cp.segmentCount
    }
}
