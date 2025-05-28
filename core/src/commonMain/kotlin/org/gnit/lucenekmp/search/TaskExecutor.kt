package org.gnit.lucenekmp.search

import kotlinx.coroutines.Runnable
import okio.IOException
import org.gnit.lucenekmp.jdkport.Callable
import org.gnit.lucenekmp.jdkport.ExecutionException
import org.gnit.lucenekmp.jdkport.Executor
import org.gnit.lucenekmp.jdkport.Future
import org.gnit.lucenekmp.jdkport.FutureTask
import org.gnit.lucenekmp.jdkport.InterruptedException
import org.gnit.lucenekmp.jdkport.RejectedExecutionException
import org.gnit.lucenekmp.jdkport.RunnableFuture
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.ThreadInterruptedException
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
                runnable.invoke()
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
        val futures: MutableList<RunnableFuture<T>> =
            mutableListOf<RunnableFuture<T>>()
        for (callable in callables) {
            futures.add(Task<T>(callable, futures))
        }
        val count = futures.size
        // taskId provides the first index of an un-executed task in #futures
        val taskId = AtomicInt(0)
        // we fork execution count - 1 tasks to execute at least one task on the current thread to
        // minimize needless forking and blocking of the current thread
        if (count > 1) {
            val work: Runnable =
                Runnable {
                    val id: Int = taskId.fetchAndIncrement()
                    if (id < count) {
                        futures.get(id).run()
                    }
                }
            for (j in 0..<count - 1) {
                executor.execute(work::run)
            }
        }
        // try to execute as many tasks as possible on the current thread to minimize context
        // switching in case of long running concurrent
        // tasks as well as dead-locking if the current thread is part of #executor for executors that
        // have limited or no parallelism
        var id: Int
        while ((taskId.fetchAndIncrement().also { id = it }) < count) {
            futures[id].run()
            if (id >= count - 1) {
                // save redundant CAS in case this was the last task
                break
            }
        }
        return collectResults<T>(futures)
    }

    override fun toString(): String {
        return "TaskExecutor(executor=$executor)"
    }

    private class Task<T>(
        callable: Callable<T>,
        private val futures: MutableList<RunnableFuture<T>>
    ) : FutureTask<T>(callable) {

        @OptIn(ExperimentalAtomicApi::class)
        private val startedOrCancelled = AtomicReference(false)


        @OptIn(ExperimentalAtomicApi::class)
        override fun run() {
            if (startedOrCancelled.compareAndSet(false, true)) {
                super.run()
            }
        }

        override fun setException(t: Throwable) {
            super.setException(t)
            cancelAll<T>(futures)
        }

        @OptIn(ExperimentalAtomicApi::class)
        override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
            require(mayInterruptIfRunning == false) { "cancelling tasks that are running is not supported" }
            /*
            Future#get (called in #collectResults) throws CancellationException when invoked against a running task that has been cancelled but
            leaves the task running. We rather want to make sure that invokeAll does not leave any running tasks behind when it returns.
            Overriding cancel ensures that tasks that are already started will complete normally once cancelled, and Future#get will
            wait for them to finish instead of throwing CancellationException. A cleaner way would have been to override FutureTask#get and
            make it wait for cancelled tasks, but FutureTask#awaitDone is private. Tasks that are cancelled before they are started will be no-op.
             */
            if (startedOrCancelled.compareAndSet(false, true)) {
                // task is cancelled hence it has no results to return. That's fine: they would be
                // ignored anyway.
                set(null)
                return true
            }
            return false
        }
    }

    companion object {
        private suspend fun <T> collectResults(futures: MutableList<RunnableFuture<T>>): MutableList<T> {
            var exc: Throwable? = null
            val results: MutableList<T> = ArrayList<T>(futures.size)
            for (future in futures) {
                try {
                    results.add(future.get())
                } catch (e: InterruptedException) {
                    exc = IOUtils.useOrSuppress(exc, ThreadInterruptedException(e))
                } catch (e: ExecutionException) {
                    exc = IOUtils.useOrSuppress(exc, e.cause as Throwable)
                }catch (e: Throwable) {
                    exc = IOUtils.useOrSuppress(exc, e)
                }
            }
            require(assertAllFuturesCompleted(futures)) { "Some tasks are still running" }
            if (exc != null) {
                throw IOUtils.rethrowAlways(exc)
            }
            return results
        }

        private fun <T> assertAllFuturesCompleted(futures: MutableList<RunnableFuture<T>>): Boolean {
            return futures.all { it.isDone() }
        }

        private fun <T> cancelAll(futures: MutableCollection<out Future<T>>) {
            for (future in futures) {
                future.cancel(false)
            }
        }
    }
}
