package org.gnit.lucenekmp.index

import okio.IOException

/**
 * A utility for executing 2-phase commit on several objects.
 *
 * @see TwoPhaseCommit
 *
 * @lucene.experimental
 */
object TwoPhaseCommitTool {
    /** No instance */

    /**
     * Thrown by [TwoPhaseCommitTool.execute] when an object fails to
     * prepareCommit().
     */
    class PrepareCommitFailException(cause: kotlin.Throwable, obj: TwoPhaseCommit?) :
        IOException("prepareCommit() failed on $obj", cause)

    /**
     * Thrown by [TwoPhaseCommitTool.execute] when an object fails to
     * commit().
     */
    class CommitFailException(cause: kotlin.Throwable, obj: TwoPhaseCommit?) :
        IOException("commit() failed on $obj", cause)

    /** rollback all objects, discarding any exceptions that occur.  */
    private fun rollback(vararg objects: TwoPhaseCommit?) {
        for (tpc in objects) {
            // ignore any exception that occurs during rollback - we want to ensure
            // all objects are rolled-back.
            if (tpc != null) {
                try {
                    tpc.rollback()
                } catch (t: kotlin.Throwable) {
                }
            }
        }
    }

    /**
     * Executes a 2-phase commit algorithm by first [TwoPhaseCommit.prepareCommit] all objects
     * and only if all succeed, it proceeds with [TwoPhaseCommit.commit]. If any of the
     * objects fail on either the preparation or actual commit, it terminates and [ ][TwoPhaseCommit.rollback] all of them.
     *
     *
     * **NOTE:** it may happen that an object fails to commit, after few have already
     * successfully committed. This tool will still issue a rollback instruction on them as well, but
     * depending on the implementation, it may not have any effect.
     *
     *
     * **NOTE:** if any of the objects are `null`, this method simply skips over them.
     *
     * @throws PrepareCommitFailException if any of the objects fail to [     ][TwoPhaseCommit.prepareCommit]
     * @throws CommitFailException if any of the objects fail to [TwoPhaseCommit.commit]
     */
    fun execute(vararg objects: TwoPhaseCommit?) {
        var tpc: TwoPhaseCommit? = null
        try {
            // first, all should successfully prepareCommit()
            for (obj in objects) {
                tpc = obj
                obj?.prepareCommit()
            }
        } catch (t: kotlin.Throwable) {
            // first object that fails results in rollback all of them and
            // throwing an exception.
            rollback(*objects)
            throw PrepareCommitFailException(t, tpc)
        }

        // If all successfully prepareCommit(), attempt the actual commit()
        try {
            for (obj in objects) {
                tpc = obj
                obj?.commit()
            }
        } catch (t: kotlin.Throwable) {
            // first object that fails results in rollback all of them and
            // throwing an exception.
            rollback(*objects)
            throw CommitFailException(t, tpc)
        }
    }
}
