package org.gnit.lucenekmp.jdkport

import kotlinx.coroutines.Runnable


/**
 * A service that decouples the production of new asynchronous tasks
 * from the consumption of the results of completed tasks.  Producers
 * `submit` tasks for execution. Consumers `take`
 * completed tasks and process their results in the order they
 * complete.  A `CompletionService` can for example be used to
 * manage asynchronous I/O, in which tasks that perform reads are
 * submitted in one part of a program or system, and then acted upon
 * in a different part of the program when the reads complete,
 * possibly in a different order than they were requested.
 *
 *
 * Typically, a `CompletionService` relies on a separate
 * [Executor] to actually execute the tasks, in which case the
 * `CompletionService` only manages an internal completion
 * queue. The [ExecutorCompletionService] class provides an
 * implementation of this approach.
 *
 *
 * Memory consistency effects: Actions in a thread prior to
 * submitting a task to a `CompletionService`
 * [*happen-before*](package-summary.html#MemoryVisibility)
 * actions taken by that task, which in turn *happen-before*
 * actions following a successful return from the corresponding `take()`.
 *
 * @param <V> the type of values the tasks of this service produce and consume
 *
 * @since 1.5
</V> */
interface CompletionService<V> {
    /**
     * Submits a value-returning task for execution and returns a Future
     * representing the pending results of the task.  Upon completion,
     * this task may be taken or polled.
     *
     * @param task the task to submit
     * @return a Future representing pending completion of the task
     * @throws RejectedExecutionException if the task cannot be
     * scheduled for execution
     * @throws NullPointerException if the task is null
     */
    fun submit(task: Callable<V>): Future<V>

    /**
     * Submits a Runnable task for execution and returns a Future
     * representing that task.  Upon completion, this task may be
     * taken or polled.
     *
     * @param task the task to submit
     * @param result the result to return upon successful completion
     * @return a Future representing pending completion of the task,
     * and whose `get()` method will return the given
     * result value upon completion
     * @throws RejectedExecutionException if the task cannot be
     * scheduled for execution
     * @throws NullPointerException if the task is null
     */
    fun submit(task: Runnable, result: V): Future<V>

    /**
     * Retrieves and removes the Future representing the next
     * completed task, waiting if none are yet present.
     *
     * @return the Future representing the next completed task
     * @throws InterruptedException if interrupted while waiting
     */
    suspend fun take(): Future<V>

    /**
     * Retrieves and removes the Future representing the next
     * completed task, or `null` if none are present.
     *
     * @return the Future representing the next completed task, or
     * `null` if none are present
     */
    fun poll(): Future<V>?

    /**
     * Retrieves and removes the Future representing the next
     * completed task, waiting if necessary up to the specified wait
     * time if none are yet present.
     *
     * @param timeout how long to wait before giving up, in units of
     * `unit`
     * @param unit a `TimeUnit` determining how to interpret the
     * `timeout` parameter
     * @return the Future representing the next completed task or
     * `null` if the specified waiting time elapses
     * before one is present
     * @throws InterruptedException if interrupted while waiting
     */
    suspend fun poll(timeout: Long, unit: TimeUnit): Future<V>?
}
