package org.gnit.lucenekmp.jdkport

import kotlinx.coroutines.Runnable


/**
 * A [CompletionService] that uses a supplied [Executor]
 * to execute tasks.  This class arranges that submitted tasks are,
 * upon completion, placed on a queue accessible using `take`.
 * The class is lightweight enough to be suitable for transient use
 * when processing groups of tasks.
 *
 *
 *
 *
 * **Usage Examples.**
 *
 * Suppose you have a set of solvers for a certain problem, each
 * returning a value of some type `Result`, and would like to
 * run them concurrently, processing the results of each of them that
 * return a non-null value, in some method `use(Result r)`. You
 * could write this as:
 *
 * <pre> `void solve(Executor e,
 * Collection<Callable<Result>> solvers)
 * throws InterruptedException, ExecutionException {
 * CompletionService<Result> cs
 * = new ExecutorCompletionService<>(e);
 * solvers.forEach(cs::submit);
 * for (int i = solvers.size(); i > 0; i--) {
 * Result r = cs.take().get();
 * if (r != null)
 * use(r);
 * }
 * }`</pre>
 *
 * Suppose instead that you would like to use the first non-null result
 * of the set of tasks, ignoring any that encounter exceptions,
 * and cancelling all other tasks when the first one is ready:
 *
 * <pre> `void solve(Executor e,
 * Collection<Callable<Result>> solvers)
 * throws InterruptedException {
 * CompletionService<Result> cs
 * = new ExecutorCompletionService<>(e);
 * int n = solvers.size();
 * List<Future<Result>> futures = new ArrayList<>(n);
 * Result result = null;
 * try {
 * solvers.forEach(solver -> futures.add(cs.submit(solver)));
 * for (int i = n; i > 0; i--) {
 * try {
 * Result r = cs.take().get();
 * if (r != null) {
 * result = r;
 * break;
 * }
 * } catch (ExecutionException ignore) {}
 * }
 * } finally {
 * futures.forEach(future -> future.cancel(true));
 * }
 *
 * if (result != null)
 * use(result);
 * }`</pre>
 *
 * @param <V> the type of values the tasks of this service produce and consume
 *
 * @since 1.5
</V> */
class ExecutorCompletionService<V> : CompletionService<V> {
    private val executor: Executor
    private val aes: AbstractExecutorService?
    private val completionQueue: BlockingQueue<Future<V?>>

    /**
     * FutureTask extension to enqueue upon completion.
     */
    private class QueueingFuture<V>(
        task: RunnableFuture<V?>,
        completionQueue: BlockingQueue<Future<V?>>
    ) : FutureTask<Void?>(task, null) {
        private val task: Future<V?>
        private val completionQueue: BlockingQueue<Future<V?>>

        init {
            this.task = task
            this.completionQueue = completionQueue
        }

        override fun done() {
            completionQueue.add(task)
        }
    }

    private fun newTaskFor(task: Callable<V?>): RunnableFuture<V?> {
        if (aes == null) return FutureTask<V?>(task)
        else return aes.newTaskFor<V?>(task)
    }

    private fun newTaskFor(task: Runnable, result: V?): RunnableFuture<V?> {
        if (aes == null) return FutureTask<V?>(task, result)
        else return aes.newTaskFor<V?>(task, result)
    }

    /**
     * Creates an ExecutorCompletionService using the supplied
     * executor for base task execution and a
     * [LinkedBlockingQueue] as a completion queue.
     *
     * @param executor the executor to use
     * @throws NullPointerException if executor is `null`
     */
    constructor(executor: Executor) {
        this.executor = executor
        this.aes =
            if (executor is AbstractExecutorService) executor as AbstractExecutorService else null
        this.completionQueue = LinkedBlockingQueue<Future<V?>>()
    }

    /**
     * Creates an ExecutorCompletionService using the supplied
     * executor for base task execution and the supplied queue as its
     * completion queue.
     *
     * @param executor the executor to use
     * @param completionQueue the queue to use as the completion queue
     * normally one dedicated for use by this service. This
     * queue is treated as unbounded -- failed attempted
     * `Queue.add` operations for completed tasks cause
     * them not to be retrievable.
     * @throws NullPointerException if executor or completionQueue are `null`
     */
    constructor(
        executor: Executor,
        completionQueue: BlockingQueue<Future<V?>>
    ) {
        this.executor = executor
        this.aes =
            if (executor is AbstractExecutorService) executor as AbstractExecutorService else null
        this.completionQueue = completionQueue
    }

    /**
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     */
    override fun submit(task: Callable<V?>): Future<V?> {
        val f: RunnableFuture<V?> = newTaskFor(task)
        executor.execute(QueueingFuture<V?>(f, completionQueue))
        return f
    }

    /**
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     */
    override fun submit(task: Runnable, result: V?): Future<V?> {
        val f: RunnableFuture<V?> = newTaskFor(task, result)
        executor.execute(QueueingFuture<V?>(f, completionQueue))
        return f
    }

    override suspend fun take(): Future<V?> {
        return completionQueue.take()
    }

    override fun poll(): Future<V?>? {
        return completionQueue.poll()
    }

    override suspend fun poll(timeout: Long, unit: TimeUnit): Future<V?>? {
        return completionQueue.poll(timeout, unit)
    }
}
