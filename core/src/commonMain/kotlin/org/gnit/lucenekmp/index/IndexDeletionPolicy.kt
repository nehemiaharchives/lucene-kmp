package org.gnit.lucenekmp.index

import okio.IOException


/**
 * Expert: policy for deletion of stale [index commits][IndexCommit].
 *
 *
 * Implement this interface, and set it on [ ][IndexWriterConfig.setIndexDeletionPolicy] to customize when older [ ] are deleted from the index directory.
 *
 *
 * The default deletion policy is [KeepOnlyLastCommitDeletionPolicy], always removes old
 * commits as soon as a new commit is done (this matches the behavior before 2.2).
 *
 *
 * One expected use case for this (and the reason why it was first created) is to work around
 * problems with an index directory accessed via filesystems like NFS because NFS does not provide
 * the "delete on last close" semantics that Lucene's "point in time" search normally relies on. By
 * implementing a custom deletion policy, such as "a commit is only removed once it has been stale
 * for more than X minutes", you can give your readers time to refresh to the new commit before
 * [IndexWriter] removes the old commits. Note that doing so will increase the storage
 * requirements of the index. See [LUCENE-710](http://issues.apache.org/jira/browse/LUCENE-710) for details.
 */
abstract class IndexDeletionPolicy
/** Sole constructor, typically called by sub-classes constructors.  */
protected constructor() {
    /**
     * This is called once when a writer is first instantiated to give the policy a chance to remove
     * old commit points.
     *
     *
     * The writer locates all index commits present in the index directory and calls this method.
     * The policy may choose to delete some of the commit points, doing so by calling method [ ][IndexCommit.delete] of [IndexCommit].
     *
     *
     * <u>Note:</u> the last CommitPoint is the most recent one, i.e. the "front index state". Be
     * careful not to delete it, unless you know for sure what you are doing, and unless you can
     * afford to lose the index content while doing that.
     *
     * @param commits List of current [point-in-time commits][IndexCommit], sorted by age (the
     * 0th one is the oldest commit). Note that for a new index this method is invoked with an
     * empty list.
     */
    @Throws(IOException::class)
    abstract fun onInit(commits: MutableList<out IndexCommit>)

    /**
     * This is called each time the writer completed a commit. This gives the policy a chance to
     * remove old commit points with each commit.
     *
     *
     * The policy may now choose to delete old commit points by calling method [ ][IndexCommit.delete] of [IndexCommit].
     *
     *
     * This method is only called when [IndexWriter.commit] or [IndexWriter.close] is
     * called, or possibly not at all if the [IndexWriter.rollback] is called.
     *
     *
     * <u>Note:</u> the last CommitPoint is the most recent one, i.e. the "front index state". Be
     * careful not to delete it, unless you know for sure what you are doing, and unless you can
     * afford to lose the index content while doing that.
     *
     * @param commits List of [IndexCommit], sorted by age (the 0th one is the oldest commit).
     */
    @Throws(IOException::class)
    abstract fun onCommit(commits: MutableList<out IndexCommit>)
}
