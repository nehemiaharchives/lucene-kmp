package org.gnit.lucenekmp.jdkport

import kotlinx.coroutines.Runnable

/**
 * A handler for tasks that cannot be executed by a [ThreadPoolExecutor].
 *
 * @since 1.5
 * @author Doug Lea
 */
interface RejectedExecutionHandler {
    /**
     * Method that may be invoked by a [ThreadPoolExecutor] when
     * [execute][ThreadPoolExecutor.execute] cannot accept a
     * task.  This may occur when no more threads or queue slots are
     * available because their bounds would be exceeded, or upon
     * shutdown of the Executor.
     *
     *
     * In the absence of other alternatives, the method may throw
     * an unchecked [RejectedExecutionException], which will be
     * propagated to the caller of `execute`.
     *
     * @param r the runnable task requested to be executed
     * @param executor the executor attempting to execute this task
     * @throws RejectedExecutionException if there is no remedy
     */
    fun rejectedExecution(r: Runnable, executor: ThreadPoolExecutor)
}
