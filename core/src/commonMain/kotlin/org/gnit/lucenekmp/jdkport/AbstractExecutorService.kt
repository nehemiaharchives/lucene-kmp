package org.gnit.lucenekmp.jdkport

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * port of java.util.concurrent.AbstractExecutorService
 *
 * Provides default implementations of [ExecutorService]
 * execution methods. This class implements the `submit`,
 * `invokeAny` and `invokeAll` methods using a
 * [RunnableFuture] returned by `newTaskFor`, which defaults
 * to the [FutureTask] class provided in this package.  For example,
 * the implementation of `submit(Runnable)` creates an
 * associated `RunnableFuture` that is executed and
 * returned. Subclasses may override the `newTaskFor` methods
 * to return `RunnableFuture` implementations other than
 * `FutureTask`.
 *
 *
 * **Extension example.** Here is a sketch of a class
 * that customizes [ThreadPoolExecutor] to use
 * a `CustomTask` class instead of the default `FutureTask`:
 * <pre> `public class CustomThreadPoolExecutor extends ThreadPoolExecutor {
 *
 * static class CustomTask<V> implements RunnableFuture<V> { ... }
 *
 * protected <V> RunnableFuture<V> newTaskFor(Callable<V> c) {
 * return new CustomTask<V>(c);
 * }
 * protected <V> RunnableFuture<V> newTaskFor(Runnable r, V v) {
 * return new CustomTask<V>(r, v);
 * }
 * // ... add constructors, etc.
 * }`</pre>
 *
 * @since 1.5
 * @author Doug Lea
 */
abstract class AbstractExecutorService
/**
 * Constructor for subclasses to call.
 */
    : ExecutorService {
    /**
     * Returns a `RunnableFuture` for the given runnable and default
     * value.
     *
     * @param runnable the runnable task being wrapped
     * @param value the default value for the returned future
     * @param <T> the type of the given value
     * @return a `RunnableFuture` which, when run, will run the
     * underlying runnable and which, as a `Future`, will yield
     * the given value as its result and provide for cancellation of
     * the underlying task
     * @since 1.6
    </T> */
    fun <T> newTaskFor(
        runnable: Runnable,
        value: T?
    ): RunnableFuture<T> {
        return FutureTask(runnable, value)
    }

    /**
     * Returns a `RunnableFuture` for the given callable task.
     *
     * @param callable the callable task being wrapped
     * @param <T> the type of the callable's result
     * @return a `RunnableFuture` which, when run, will call the
     * underlying callable and which, as a `Future`, will yield
     * the callable's result as its result and provide for
     * cancellation of the underlying task
     * @since 1.6
    </T> */
    fun <T> newTaskFor(callable: Callable<T?>): RunnableFuture<T> {
        return FutureTask(callable)
    }

    /**
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     */
    override fun submit(task: Runnable): Future<*> {
        /*if (task == null) throw java.lang.NullPointerException()*/
        val ftask: RunnableFuture<Void> = newTaskFor(task, null)
        execute(ftask)
        return ftask
    }

    /**
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     */
    override fun <T> submit(task: Runnable, result: T): Future<T> {
        /*if (task == null) throw java.lang.NullPointerException()*/
        val ftask: RunnableFuture<T> = newTaskFor<T>(task, result)
        execute(ftask)
        return ftask
    }

    /**
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     */
    override fun <T> submit(task: Callable<T?>): Future<T> {
        /*if (task == null) throw java.lang.NullPointerException()*/
        val ftask: RunnableFuture<T> = newTaskFor(task)
        execute(ftask)
        return ftask
    }

    /**
     * the main mechanics of invokeAny.
     */
    private suspend fun <T> doInvokeAny(
        tasks: MutableCollection<Callable<T?>>,
        timed: Boolean,
        nanos: Long
    ): T? {
        var nanos = nanos
        /*if (tasks == null) throw java.lang.NullPointerException()*/
        var ntasks = tasks.size
        require(ntasks != 0)
        val futures: ArrayList<Future<T?>> =
            ArrayList(ntasks)
        val ecs: ExecutorCompletionService<T> =
            ExecutorCompletionService(this)

        // For efficiency, especially in executors with limited
        // parallelism, check to see if previously submitted tasks are
        // done before submitting more of them. This interleaving
        // plus the exception mechanics account for messiness of main
        // loop.
        try {
            // Record exceptions so that if we fail to obtain any
            // result, we can throw the last exception we got.
            var ee: ExecutionException? = null
            val deadline = if (timed) System.nanoTime() + nanos else 0L
            val it: MutableIterator<Callable<T?>> = tasks.iterator()

            // Start one task for sure; the rest incrementally
            futures.add(ecs.submit(it.next()))
            --ntasks
            var active = 1

            while (true) {
                var f: Future<T?>? = ecs.poll()
                if (f == null) {
                    if (ntasks > 0) {
                        --ntasks
                        futures.add(ecs.submit(it.next()))
                        ++active
                    } else if (active == 0) break
                    else if (timed) {
                        f = ecs.poll(nanos, TimeUnit.NANOSECONDS)
                        if (f == null) throw TimeoutException()
                        nanos = deadline - System.nanoTime()
                    } else f = ecs.take()
                }
                if (f != null) {
                    --active
                    try {
                        return f.get()
                    } catch (eex: ExecutionException) {
                        ee = eex
                    } catch (rex: RuntimeException) {
                        ee = ExecutionException(rex)
                    }
                }
            }

            if (ee == null) ee = ExecutionException()
            throw ee
        } finally {
            cancelAll(futures)
        }
    }

    /**
     * @throws InterruptedException       {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     * @throws IllegalArgumentException   {@inheritDoc}
     * @throws ExecutionException         {@inheritDoc}
     * @throws RejectedExecutionException {@inheritDoc}
     */
    override suspend fun <T> invokeAny(tasks: MutableCollection<Callable<T?>>): T? {
        try {
            return doInvokeAny<T?>(tasks, false, 0)
        } catch (cannotHappen: TimeoutException) {
            assert(false)
            return null
        }
    }

    /**
     * @throws InterruptedException       {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     * @throws TimeoutException           {@inheritDoc}
     * @throws ExecutionException         {@inheritDoc}
     * @throws RejectedExecutionException {@inheritDoc}
     */
    override suspend fun <T> invokeAny(
        tasks: MutableCollection<Callable<T?>>,
        timeout: Long,
        unit: TimeUnit
    ): T? {
        return doInvokeAny<T>(tasks, true, unit.toNanos(timeout))
    }

    /**
     * @throws InterruptedException       {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     * @throws RejectedExecutionException {@inheritDoc}
     */
    override suspend fun <T> invokeAll(tasks: MutableCollection<Callable<T?>>): MutableList<Future<T>> = coroutineScope {
        /*if (tasks == null) throw java.lang.NullPointerException()*/
        val futures: ArrayList<Future<T>> = ArrayList(tasks.size)
        try {
            // Launch all tasks in parallel using coroutines
            for (t in tasks) {
                val f: RunnableFuture<T> = newTaskFor(t)
                futures.add(f)
                execute(f)
            }
            // Await all futures in parallel, catching exceptions as in original
            futures.map { f ->
                async {
                    if (!f.isDone()) {
                        try {
                            f.get()
                        } catch (_: CancellationException) {
                        } catch (_: ExecutionException) {
                        }
                    }
                }
            }.awaitAll()
            futures
        } catch (t: Throwable) {
            cancelAll(futures)
            throw t
        }
    }

    /**
     * @throws InterruptedException       {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     * @throws RejectedExecutionException {@inheritDoc}
     */
    override suspend fun <T> invokeAll(
        tasks: MutableCollection<Callable<T?>>,
        timeout: Long, unit: TimeUnit
    ): MutableList<Future<T>> = coroutineScope {
        /*if (tasks == null) throw java.lang.NullPointerException()*/
        val nanos: Long = unit.toNanos(timeout)
        val futures: ArrayList<Future<T>> = ArrayList(tasks.size)
        try {
            for (t in tasks) {
                val f: RunnableFuture<T> = newTaskFor(t)
                futures.add(f)
                execute(f)
            }
            // Await all futures in parallel, but with timeout
            val jobs = futures.map { f ->
                async {
                    if (!f.isDone()) {
                        try {
                            f.get()
                        } catch (_: CancellationException) {
                        } catch (_: ExecutionException) {
                        }
                    }
                }
            }
            // Use withTimeoutOrNull to enforce the timeout for all jobs
            val completed = withTimeoutOrNull(nanos / 1_000_000) { // nanos to millis
                jobs.awaitAll()
            }
            if (completed == null) {
                // Timed out before all the tasks could be completed; cancel remaining
                cancelAll(futures)
            }
            futures
        } catch (t: Throwable) {
            cancelAll(futures)
            throw t
        }
    }

    companion object {
        private fun <T> cancelAll(futures: ArrayList<Future<T>>) {
            cancelAll(futures, 0)
        }

        /** Cancels all futures with index at least j.  */
        private fun <T> cancelAll(futures: ArrayList<Future<T>>, j: Int) {
            var j = j
            val size: Int = futures.size
            while (j < size) {
                futures[j].cancel(true)
                j++
            }
        }
    }
}
