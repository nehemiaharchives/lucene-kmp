package org.gnit.lucenekmp.index

import okio.IOException

/**
 * An interface for implementations that support 2-phase commit. You can use [ ] to execute a 2-phase commit algorithm over several [TwoPhaseCommit]s.
 *
 * @lucene.experimental
 */
interface TwoPhaseCommit {
    /**
     * The first stage of a 2-phase commit. Implementations should do as much work as possible in this
     * method, but avoid actual committing changes. If the 2-phase commit fails, [.rollback]
     * is called to discard all changes since last successful commit.
     */
    @Throws(IOException::class)
    fun prepareCommit(): Long

    /**
     * The second phase of a 2-phase commit. Implementations should ideally do very little work in
     * this method (following [.prepareCommit], and after it returns, the caller can assume
     * that the changes were successfully committed to the underlying storage.
     */
    @Throws(IOException::class)
    fun commit(): Long

    /**
     * Discards any changes that have occurred since the last commit. In a 2-phase commit algorithm,
     * where one of the objects failed to [.commit] or [.prepareCommit], this method
     * is used to roll all other objects back to their previous state.
     */
    @Throws(IOException::class)
    fun rollback()
}
