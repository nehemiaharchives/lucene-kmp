package org.gnit.lucenekmp.search

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Runnable
import org.gnit.lucenekmp.jdkport.Callable
import org.gnit.lucenekmp.jdkport.Executor
import org.gnit.lucenekmp.jdkport.RejectedExecutionException
import org.gnit.lucenekmp.util.IOUtils
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement


/**
 * Executor wrapper responsible for the execution of concurrent tasks. Used to parallelize search
 * across segments as well as query rewrite in some cases. Exposes a single [ ][.invokeAll] method that takes a collection of [Callable]s and executes them
 * concurrently. Once all but one task have been submitted to the executor, it tries to run as many
 * tasks as possible on the calling thread, then waits for all tasks that have been executed in
 * parallel on the executor to be completed and then returns a list with the obtained results.
 *
 * @lucene.experimental
 */
class TaskExecutor(executor: Executor) {
    private val executor: Executor

    /**
     * Creates a TaskExecutor instance
     *
     * @param executor the executor to be used for running tasks concurrently
     */
    init {
        requireNotNull(executor) { "Executor is null" }
        this.executor = Executor { runnable ->
            try {
                executor.execute(runnable)
            } catch (rejectedExecutionException: RejectedExecutionException) {
                // execute directly on the current thread in case of rejection to ensure a rejecting
                // executor only reduces parallelism and does not
                // result in failure
                runnable.run()
            }
        }
    }

    /**
     * Execute all the callables provided as an argument, wait for them to complete and return the
     * obtained results. If an exception is thrown by more than one callable, the subsequent ones will
     * be added as suppressed exceptions to the first one that was caught. Additionally, if one task
     * throws an exception, all other tasks from the same group are cancelled, to avoid needless
     * computation as their results would not be exposed anyways.
     *
     * @param callables the callables to execute
     * @return a list containing the results from the tasks execution
     * @param <T> the return type of the task execution
    </T> */
    @OptIn(ExperimentalAtomicApi::class)
    suspend fun <T> invokeAll(callables: MutableCollection<Callable<T>>): MutableList<T> {
        val count = callables.size
        if (count == 0) {
            return mutableListOf()
        }
        if (count == 1) {
            // Fast path for the common single-task case (eg, single-slice search):
            // avoid FutureTask allocation, atomics, and collectResults/get suspension overhead.
            return try {
                mutableListOf(callables.first().call())
            } catch (t: Throwable) {
                throw IOUtils.rethrowAlways(t)
            }
        }

        val tasks: MutableList<TaskState<T>> = mutableListOf()
        for (callable in callables) {
            tasks.add(TaskState(callable) { cancelAll(tasks) })
        }
        val taskCount = tasks.size
        // taskId provides the first index of an un-executed task in tasks.
        val taskId = AtomicInt(0)
        // We fork execution count - 1 tasks to execute at least one task on the current thread to
        // minimize needless forking and blocking of the current thread
        if (taskCount > 1) {
            val work = Runnable {
                    val id: Int = taskId.fetchAndIncrement()
                    if (id < taskCount) {
                        tasks[id].run()
                    }
                }
            for (j in 0..<taskCount - 1) {
                executor.execute(work::run)
            }
        }
        // Try to execute as many tasks as possible on the current thread to minimize context
        // switching in case of long running concurrent
        // tasks as well as dead-locking if the current thread is part of #executor for executors that
        // have limited or no parallelism
        var id: Int
        while ((taskId.fetchAndIncrement().also { id = it }) < taskCount) {
            tasks[id].run()
            if (id >= taskCount - 1) {
                // save redundant CAS in case this was the last task
                break
            }
        }
        val results = collectResults(tasks)
        return results
    }

    override fun toString(): String {
        return "TaskExecutor(executor=$executor)"
    }

    private class TaskState<T>(
        private val callable: Callable<T>,
        private val onFailure: () -> Unit
    ) {
        private class Failure(val throwable: Throwable)

        @OptIn(ExperimentalAtomicApi::class)
        private val startedOrCancelled = AtomicReference(false)
        @OptIn(ExperimentalAtomicApi::class)
        private val outcome = AtomicReference<Any?>(UNSET)
        private val completion = CompletableDeferred<Unit>()

        @OptIn(ExperimentalAtomicApi::class)
        fun run() {
            if (startedOrCancelled.compareAndSet(false, true)) {
                try {
                    outcome.store(callable.call())
                } catch (t: Throwable) {
                    outcome.store(Failure(t))
                    onFailure()
                } finally {
                    completion.complete(Unit)
                }
            }
        }

        @OptIn(ExperimentalAtomicApi::class)
        fun cancel(): Boolean {
            /*
             Tasks cancelled before start become completed with `null` result,
             which preserves previous Task/FutureTask behavior for callers.
             */
            if (startedOrCancelled.compareAndSet(false, true)) {
                outcome.store(CANCELLED_TO_NULL)
                completion.complete(Unit)
                return true
            }
            return false
        }

        @OptIn(ExperimentalAtomicApi::class)
        suspend fun awaitOutcome(): Any? {
            completion.await()
            return outcome.load()
        }

        companion object {
            private val UNSET = Any()
            private val CANCELLED_TO_NULL = Any()
        }

        fun isCancelledToNull(outcome: Any?): Boolean = outcome === CANCELLED_TO_NULL
        fun isFailure(outcome: Any?): Boolean = outcome is Failure
        fun failure(outcome: Any?): Throwable = (outcome as Failure).throwable
    }

    companion object {
        private suspend fun <T> collectResults(tasks: MutableList<TaskState<T>>): MutableList<T> {
            var exc: Throwable? = null
            val results: MutableList<T> = ArrayList(tasks.size)
            for (task in tasks) {
                val outcome = task.awaitOutcome()
                if (task.isFailure(outcome)) {
                    exc = IOUtils.useOrSuppress(exc, task.failure(outcome))
                } else if (task.isCancelledToNull(outcome)) {
                    @Suppress("UNCHECKED_CAST")
                    results.add(null as T)
                } else {
                    @Suppress("UNCHECKED_CAST")
                    results.add(outcome as T)
                }
            }
            if (exc != null) {
                throw IOUtils.rethrowAlways(exc)
            }
            return results
        }

        private fun <T> cancelAll(tasks: MutableCollection<TaskState<T>>) {
            for (task in tasks) {
                task.cancel()
            }
        }
    }
}
