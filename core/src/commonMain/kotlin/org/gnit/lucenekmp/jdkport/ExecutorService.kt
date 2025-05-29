package org.gnit.lucenekmp.jdkport

import kotlinx.coroutines.Runnable


/**
 * port of java.util.concurrent.ExecutorService
 *
 * An [Executor] that provides methods to manage termination and
 * methods that can produce a [Future] for tracking progress of
 * one or more asynchronous tasks.
 *
 *
 * An `ExecutorService` can be shut down, which will cause
 * it to reject new tasks.  Two different methods are provided for
 * shutting down an `ExecutorService`. The [.shutdown]
 * method will allow previously submitted tasks to execute before
 * terminating, while the [.shutdownNow] method prevents waiting
 * tasks from starting and attempts to stop currently executing tasks.
 * Upon termination, an executor has no tasks actively executing, no
 * tasks awaiting execution, and no new tasks can be submitted.  An
 * unused `ExecutorService` should be shut down to allow
 * reclamation of its resources.
 *
 *
 * Method `submit` extends base method [ ][Executor.execute] by creating and returning a [Future]
 * that can be used to cancel execution and/or wait for completion.
 * Methods `invokeAny` and `invokeAll` perform the most
 * commonly useful forms of bulk execution, executing a collection of
 * tasks and then waiting for at least one, or all, to
 * complete. (Class [ExecutorCompletionService] can be used to
 * write customized variants of these methods.)
 *
 *
 * The [Executors] class provides factory methods for the
 * executor services provided in this package.
 *
 * <h2>Usage Examples</h2>
 *
 * Here is a sketch of a network service in which threads in a thread
 * pool service incoming requests. It uses the preconfigured [ ][Executors.newFixedThreadPool] factory method:
 *
 * ```
 *     class NetworkService implements Runnable {
 *         private final ServerSocket serverSocket;
 *         private final ExecutorService pool;
 *
 *         public NetworkService(int port, int poolSize) throws IOException {
 *             serverSocket = new ServerSocket(port);
 *             pool = Executors.newFixedThreadPool(poolSize);
 *         }
 *
 *         public void run() { // run the service
 *             try {
 *                 for (;;) {
 *                     pool.execute(new Handler(serverSocket.accept()));
 *                 }
 *             } catch (IOException ex) {
 *                 pool.shutdown();
 *             }
 *         }
 *     }
 *
 *     class Handler implements Runnable {
 *         private final Socket socket;
 *         Handler(Socket socket) { this.socket = socket; }
 *         public void run() {
 *             // read and service request on socket
 *         }
 *     }
 * ```
 *
 * An `ExecutorService` may also be established and closed
 * (shutdown, blocking until terminated) as follows; illustrating with
 * a different `Executors` factory method:
 *
 * ```
 * try (ExecutorService e =  Executors.newWorkStealingPool()) {
 *  // submit or execute many tasks with e ...
 * }
 * ```
 *
 * Further customization is also possible. For example, the following
 * method shuts down an `ExecutorService` in two phases, first
 * by calling `shutdown` to reject incoming tasks, and then
 * calling `shutdownNow`, if necessary, to cancel any lingering
 * tasks:
 *
 * ```
 *  void shutdownAndAwaitTermination(ExecutorService pool) {
 *      pool.shutdown(); // Disable new tasks from being submitted
 *      try {
 *          // Wait a while for existing tasks to terminate
 *          if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
 *              pool.shutdownNow(); // Cancel currently executing tasks
 *              // Wait a while for tasks to respond to being cancelled
 *              if (!pool.awaitTermination(60, TimeUnit.SECONDS))
 *                  System.err.println("Pool did not terminate");
 *          }
 *      } catch (InterruptedException ex) {
 *          // (Re-)Cancel if current thread also interrupted
 *          pool.shutdownNow();
 *          // Preserve interrupt status
 *          Thread.currentThread().interrupt();
 *      }
 *  }
 *```
 *
 * Memory consistency effects: Actions in a thread prior to the
 * submission of a `Runnable` or `Callable` task to an
 * `ExecutorService`
 * [*happen-before*](package-summary.html#MemoryVisibility)
 * any actions taken by that task, which in turn *happen-before* the
 * result is retrieved via `Future.get()`.
 *
 * @since 1.5
 * @author Doug Lea
 */
interface ExecutorService : Executor, AutoCloseable {
    /**
     * Initiates an orderly shutdown in which previously submitted
     * tasks are executed, but no new tasks will be accepted.
     * Invocation has no additional effect if already shut down.
     *
     *
     * This method does not wait for previously submitted tasks to
     * complete execution.  Use [awaitTermination][.awaitTermination]
     * to do that.
     *
     * @throws SecurityException if a security manager exists and
     * shutting down this ExecutorService may manipulate
     * threads that the caller is not permitted to modify
     * because it does not hold [         ]`("modifyThread")`,
     * or the security manager's `checkAccess` method
     * denies access.
     */
    fun shutdown()

    /**
     * Attempts to stop all actively executing tasks, halts the
     * processing of waiting tasks, and returns a list of the tasks
     * that were awaiting execution.
     *
     *
     * This method does not wait for actively executing tasks to
     * terminate.  Use [awaitTermination][.awaitTermination] to
     * do that.
     *
     *
     * There are no guarantees beyond best-effort attempts to stop
     * processing actively executing tasks.  For example, typical
     * implementations will cancel via [Thread.interrupt], so any
     * task that fails to respond to interrupts may never terminate.
     *
     * @return list of tasks that never commenced execution
     * @throws SecurityException if a security manager exists and
     * shutting down this ExecutorService may manipulate
     * threads that the caller is not permitted to modify
     * because it does not hold [         ]`("modifyThread")`,
     * or the security manager's `checkAccess` method
     * denies access.
     */
    fun shutdownNow(): MutableList<Runnable>

    /**
     * Returns `true` if this executor has been shut down.
     *
     * @return `true` if this executor has been shut down
     */
    val isShutdown: Boolean

    /**
     * Returns `true` if all tasks have completed following shut down.
     * Note that `isTerminated` is never `true` unless
     * either `shutdown` or `shutdownNow` was called first.
     *
     * @return `true` if all tasks have completed following shut down
     */
    val isTerminated: Boolean

    /**
     * Blocks until all tasks have completed execution after a shutdown
     * request, or the timeout occurs, or the current thread is
     * interrupted, whichever happens first.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @return `true` if this executor terminated and
     * `false` if the timeout elapsed before termination
     * @throws InterruptedException if interrupted while waiting
     */
    @Throws(InterruptedException::class)
    fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean

    /**
     * Submits a value-returning task for execution and returns a
     * Future representing the pending results of the task. The
     * Future's `get` method will return the task's result upon
     * successful completion.
     *
     *
     *
     * If you would like to immediately block waiting
     * for a task, you can use constructions of the form
     * `result = exec.submit(aCallable).get();`
     *
     *
     * Note: The [Executors] class includes a set of methods
     * that can convert some other common closure-like objects,
     * for example, [java.security.PrivilegedAction] to
     * [Callable] form so they can be submitted.
     *
     * @param task the task to submit
     * @param <T> the type of the task's result
     * @return a Future representing pending completion of the task
     * @throws RejectedExecutionException if the task cannot be
     * scheduled for execution
     * @throws NullPointerException if the task is null
    </T> */
    fun <T> submit(task: Callable<T?>): Future<T>

    /**
     * Submits a Runnable task for execution and returns a Future
     * representing that task. The Future's `get` method will
     * return the given result upon successful completion.
     *
     * @param task the task to submit
     * @param result the result to return
     * @param <T> the type of the result
     * @return a Future representing pending completion of the task
     * @throws RejectedExecutionException if the task cannot be
     * scheduled for execution
     * @throws NullPointerException if the task is null
    </T> */
    fun <T> submit(task: Runnable, result: T): Future<T>

    /**
     * Submits a Runnable task for execution and returns a Future
     * representing that task. The Future's `get` method will
     * return `null` upon *successful* completion.
     *
     * @param task the task to submit
     * @return a Future representing pending completion of the task
     * @throws RejectedExecutionException if the task cannot be
     * scheduled for execution
     * @throws NullPointerException if the task is null
     */
    fun submit(task: Runnable): Future<*>

    /**
     * Executes the given tasks, returning a list of Futures holding
     * their status and results when all complete.
     * [Future.isDone] is `true` for each
     * element of the returned list.
     * Note that a *completed* task could have
     * terminated either normally or by throwing an exception.
     * The results of this method are undefined if the given
     * collection is modified while this operation is in progress.
     *
     * @param tasks the collection of tasks
     * @param <T> the type of the values returned from the tasks
     * @return a list of Futures representing the tasks, in the same
     * sequential order as produced by the iterator for the
     * given task list, each of which has completed
     * @throws InterruptedException if interrupted while waiting, in
     * which case unfinished tasks are cancelled
     * @throws NullPointerException if tasks or any of its elements are `null`
     * @throws RejectedExecutionException if any task cannot be
     * scheduled for execution
    </T> */
    suspend fun <T> invokeAll(tasks: MutableCollection<Callable<T?>>): MutableList<Future<T>>

    /**
     * Executes the given tasks, returning a list of Futures holding
     * their status and results
     * when all complete or the timeout expires, whichever happens first.
     * [Future.isDone] is `true` for each
     * element of the returned list.
     * Upon return, tasks that have not completed are cancelled.
     * Note that a *completed* task could have
     * terminated either normally or by throwing an exception.
     * The results of this method are undefined if the given
     * collection is modified while this operation is in progress.
     *
     * @param tasks the collection of tasks
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @param <T> the type of the values returned from the tasks
     * @return a list of Futures representing the tasks, in the same
     * sequential order as produced by the iterator for the
     * given task list. If the operation did not time out,
     * each task will have completed. If it did time out, some
     * of these tasks will not have completed.
     * @throws InterruptedException if interrupted while waiting, in
     * which case unfinished tasks are cancelled
     * @throws NullPointerException if tasks, any of its elements, or
     * unit are `null`
     * @throws RejectedExecutionException if any task cannot be scheduled
     * for execution
    </T> */
    suspend fun <T> invokeAll(
        tasks: MutableCollection<Callable<T?>>,
        timeout: Long, unit: TimeUnit
    ): MutableList<Future<T>>

    /**
     * Executes the given tasks, returning the result
     * of one that has completed successfully (i.e., without throwing
     * an exception), if any do. Upon normal or exceptional return,
     * tasks that have not completed are cancelled.
     * The results of this method are undefined if the given
     * collection is modified while this operation is in progress.
     *
     * @param tasks the collection of tasks
     * @param <T> the type of the values returned from the tasks
     * @return the result returned by one of the tasks
     * @throws InterruptedException if interrupted while waiting
     * @throws NullPointerException if tasks or any element task
     * subject to execution is `null`
     * @throws IllegalArgumentException if tasks is empty
     * @throws ExecutionException if no task successfully completes
     * @throws RejectedExecutionException if tasks cannot be scheduled
     * for execution
    </T> */
    suspend fun <T> invokeAny(tasks: MutableCollection<Callable<T?>>): T?

    /**
     * Executes the given tasks, returning the result
     * of one that has completed successfully (i.e., without throwing
     * an exception), if any do before the given timeout elapses.
     * Upon normal or exceptional return, tasks that have not
     * completed are cancelled.
     * The results of this method are undefined if the given
     * collection is modified while this operation is in progress.
     *
     * @param tasks the collection of tasks
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @param <T> the type of the values returned from the tasks
     * @return the result returned by one of the tasks
     * @throws InterruptedException if interrupted while waiting
     * @throws NullPointerException if tasks, or unit, or any element
     * task subject to execution is `null`
     * @throws TimeoutException if the given timeout elapses before
     * any task successfully completes
     * @throws ExecutionException if no task successfully completes
     * @throws RejectedExecutionException if tasks cannot be scheduled
     * for execution
    </T> */
    suspend fun <T> invokeAny(
        tasks: MutableCollection<Callable<T?>>,
        timeout: Long,
        unit: TimeUnit
    ): T?

    /**
     * Initiates an orderly shutdown in which previously submitted tasks are
     * executed, but no new tasks will be accepted. This method waits until all
     * tasks have completed execution and the executor has terminated.
     *
     *
     *  If interrupted while waiting, this method stops all executing tasks as
     * if by invoking [.shutdownNow]. It then continues to wait until all
     * actively executing tasks have completed. Tasks that were awaiting
     * execution are not executed. The interrupt status will be re-asserted
     * before this method returns.
     *
     *
     *  If already terminated, invoking this method has no effect.
     *
     * @implSpec
     * The default implementation invokes `shutdown()` and waits for tasks
     * to complete execution with `awaitTermination`.
     *
     * @throws SecurityException if a security manager exists and
     * shutting down this ExecutorService may manipulate
     * threads that the caller is not permitted to modify
     * because it does not hold [         ]`("modifyThread")`,
     * or the security manager's `checkAccess` method
     * denies access.
     * @since 19
     */
    override fun close() {
        var terminated = this.isTerminated
        if (!terminated) {
            shutdown()
            var interrupted = false
            while (!terminated) {
                try {
                    terminated = awaitTermination(1L, TimeUnit.DAYS)
                } catch (e: InterruptedException) {
                    if (!interrupted) {
                        shutdownNow()
                        interrupted = true
                    }
                }
            }
            if (interrupted) {
                // TODO replace following code with coroutine equivalent
                //java.lang.Thread.currentThread().interrupt()
                throw RuntimeException("${this::class.qualifiedName}.close() is not implemented yet")
            }
        }
    }
}
