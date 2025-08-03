package org.gnit.lucenekmp.index

/**
 * This [IndexDeletionPolicy] implementation that keeps only the most recent commit and
 * immediately removes all prior commits after a new commit is done. This is the default deletion
 * policy.
 */
class KeepOnlyLastCommitDeletionPolicy
/** Sole constructor.  */
    : IndexDeletionPolicy() {
    /** Deletes all commits except the most recent one.  */
    override fun onInit(commits: MutableList<out IndexCommit>) {
        // Note that commits.size() should normally be 1:
        onCommit(commits)
    }

    /** Deletes all commits except the most recent one.  */
    override fun onCommit(commits: MutableList<out IndexCommit>) {
        // Note that commits.size() should normally be 2 (if not
        // called by onInit above):
        val size = commits.size
        for (i in 0..<size - 1) {
            commits[i].delete()
        }
    }
}
