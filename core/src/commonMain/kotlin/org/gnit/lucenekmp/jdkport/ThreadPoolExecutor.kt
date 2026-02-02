package org.gnit.lucenekmp.jdkport

import kotlinx.coroutines.Job
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.cancellation.CancellationException
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.coroutineContext
import kotlin.math.min


/**
 * An [ExecutorService] that executes each submitted task using
 * one of possibly several pooled threads, normally configured
 * using [Executors] factory methods.
 *
 *
 * Thread pools address two different problems: they usually
 * provide improved performance when executing large numbers of
 * asynchronous tasks, due to reduced per-task invocation overhead,
 * and they provide a means of bounding and managing the resources,
 * including threads, consumed when executing a collection of tasks.
 * Each `ThreadPoolExecutor` also maintains some basic
 * statistics, such as the number of completed tasks.
 *
 *
 * To be useful across a wide range of contexts, this class
 * provides many adjustable parameters and extensibility
 * hooks. However, programmers are urged to use the more convenient
 * [Executors] factory methods [ ][Executors.newCachedThreadPool] (unbounded thread pool, with
 * automatic thread reclamation), [Executors.newFixedThreadPool]
 * (fixed size thread pool) and [ ][Executors.newSingleThreadExecutor] (single background thread), that
 * preconfigure settings for the most common usage
 * scenarios. Otherwise, use the following guide when manually
 * configuring and tuning this class:
 *
 * <dl>
 *
 * <dt>Core and maximum pool sizes</dt>
 *
 * <dd>A `ThreadPoolExecutor` will automatically adjust the
 * pool size (see [.getPoolSize])
 * according to the bounds set by
 * corePoolSize (see [.getCorePoolSize]) and
 * maximumPoolSize (see [.getMaximumPoolSize]).
 *
 * When a new task is submitted in method [.execute],
 * if fewer than corePoolSize threads are running, a new thread is
 * created to handle the request, even if other worker threads are
 * idle.  Else if fewer than maximumPoolSize threads are running, a
 * new thread will be created to handle the request only if the queue
 * is full.  By setting corePoolSize and maximumPoolSize the same, you
 * create a fixed-size thread pool. By setting maximumPoolSize to an
 * essentially unbounded value such as `Integer.MAX_VALUE`, you
 * allow the pool to accommodate an arbitrary number of concurrent
 * tasks. Most typically, core and maximum pool sizes are set only
 * upon construction, but they may also be changed dynamically using
 * [.setCorePoolSize] and [.setMaximumPoolSize]. </dd>
 *
 * <dt>On-demand construction</dt>
 *
 * <dd>By default, even core threads are initially created and
 * started only when new tasks arrive, but this can be overridden
 * dynamically using method [.prestartCoreThread] or [ ][.prestartAllCoreThreads].  You probably want to prestart threads if
 * you construct the pool with a non-empty queue. </dd>
 *
 * <dt>Creating new threads</dt>
 *
 * <dd>New threads are created using a [ThreadFactory].  If not
 * otherwise specified, a [Executors.defaultThreadFactory] is
 * used, that creates threads to all be in the same [ ] and with the same `NORM_PRIORITY` priority and
 * non-daemon status. By supplying a different ThreadFactory, you can
 * alter the thread's name, thread group, priority, daemon status,
 * etc. If a `ThreadFactory` fails to create a thread when asked
 * by returning null from `newThread`, the executor will
 * continue, but might not be able to execute any tasks. Threads
 * should possess the "modifyThread" `RuntimePermission`. If
 * worker threads or other threads using the pool do not possess this
 * permission, service may be degraded: configuration changes may not
 * take effect in a timely manner, and a shutdown pool may remain in a
 * state in which termination is possible but not completed.</dd>
 *
 * <dt>Keep-alive times</dt>
 *
 * <dd>If the pool currently has more than corePoolSize threads,
 * excess threads will be terminated if they have been idle for more
 * than the keepAliveTime (see [.getKeepAliveTime]).
 * This provides a means of reducing resource consumption when the
 * pool is not being actively used. If the pool becomes more active
 * later, new threads will be constructed. This parameter can also be
 * changed dynamically using method [.setKeepAliveTime].  Using a value of `Long.MAX_VALUE` [ ][TimeUnit.NANOSECONDS] effectively disables idle threads from ever
 * terminating prior to shut down. By default, the keep-alive policy
 * applies only when there are more than corePoolSize threads, but
 * method [.allowCoreThreadTimeOut] can be used to
 * apply this time-out policy to core threads as well, so long as the
 * keepAliveTime value is non-zero. </dd>
 *
 * <dt>Queuing</dt>
 *
 * <dd>Any [BlockingQueue] may be used to transfer and hold
 * submitted tasks.  The use of this queue interacts with pool sizing:
 *
 *
 *
 *  * If fewer than corePoolSize threads are running, the Executor
 * always prefers adding a new thread
 * rather than queuing.
 *
 *  * If corePoolSize or more threads are running, the Executor
 * always prefers queuing a request rather than adding a new
 * thread.
 *
 *  * If a request cannot be queued, a new thread is created unless
 * this would exceed maximumPoolSize, in which case, the task will be
 * rejected.
 *
 *
 *
 * There are three general strategies for queuing:
 *
 *
 *  1. * Direct handoffs.* A good default choice for a work
 * queue is a [SynchronousQueue] that hands off tasks to threads
 * without otherwise holding them. Here, an attempt to queue a task
 * will fail if no threads are immediately available to run it, so a
 * new thread will be constructed. This policy avoids lockups when
 * handling sets of requests that might have internal dependencies.
 * Direct handoffs generally require unbounded maximumPoolSizes to
 * avoid rejection of new submitted tasks. This in turn admits the
 * possibility of unbounded thread growth when commands continue to
 * arrive on average faster than they can be processed.
 *
 *  1. * Unbounded queues.* Using an unbounded queue (for
 * example a [LinkedBlockingQueue] without a predefined
 * capacity) will cause new tasks to wait in the queue when all
 * corePoolSize threads are busy. Thus, no more than corePoolSize
 * threads will ever be created. (And the value of the maximumPoolSize
 * therefore doesn't have any effect.)  This may be appropriate when
 * each task is completely independent of others, so tasks cannot
 * affect each others execution; for example, in a web page server.
 * While this style of queuing can be useful in smoothing out
 * transient bursts of requests, it admits the possibility of
 * unbounded work queue growth when commands continue to arrive on
 * average faster than they can be processed.
 *
 *  1. *Bounded queues.* A bounded queue (for example, an
 * [ArrayBlockingQueue]) helps prevent resource exhaustion when
 * used with finite maximumPoolSizes, but can be more difficult to
 * tune and control.  Queue sizes and maximum pool sizes may be traded
 * off for each other: Using large queues and small pools minimizes
 * CPU usage, OS resources, and context-switching overhead, but can
 * lead to artificially low throughput.  If tasks frequently block (for
 * example if they are I/O bound), a system may be able to schedule
 * time for more threads than you otherwise allow. Use of small queues
 * generally requires larger pool sizes, which keeps CPUs busier but
 * may encounter unacceptable scheduling overhead, which also
 * decreases throughput.
 *
 *
 *
</dd> *
 *
 * <dt>Rejected tasks</dt>
 *
 * <dd>New tasks submitted in method [.execute] will be
 * *rejected* when the Executor has been shut down, and also when
 * the Executor uses finite bounds for both maximum threads and work queue
 * capacity, and is saturated.  In either case, the `execute` method
 * invokes the [ ][RejectedExecutionHandler.rejectedExecution]
 * method of its [RejectedExecutionHandler].  Four predefined handler
 * policies are provided:
 *
 *
 *
 *  1. In the default [ThreadPoolExecutor.AbortPolicy], the handler
 * throws a runtime [RejectedExecutionException] upon rejection.
 *
 *  1. In [ThreadPoolExecutor.CallerRunsPolicy], the thread
 * that invokes `execute` itself runs the task. This provides a
 * simple feedback control mechanism that will slow down the rate that
 * new tasks are submitted.
 *
 *  1. In [ThreadPoolExecutor.DiscardPolicy], a task that cannot
 * be executed is simply dropped. This policy is designed only for
 * those rare cases in which task completion is never relied upon.
 *
 *  1. In [ThreadPoolExecutor.DiscardOldestPolicy], if the
 * executor is not shut down, the task at the head of the work queue
 * is dropped, and then execution is retried (which can fail again,
 * causing this to be repeated.) This policy is rarely acceptable.  In
 * nearly all cases, you should also cancel the task to cause an
 * exception in any component waiting for its completion, and/or log
 * the failure, as illustrated in [ ] documentation.
 *
 *
 *
 * It is possible to define and use other kinds of [ ] classes. Doing so requires some care
 * especially when policies are designed to work only under particular
 * capacity or queuing policies. </dd>
 *
 * <dt>Hook methods</dt>
 *
 * <dd>This class provides `protected` overridable
 * [.beforeExecute] and
 * [.afterExecute] methods that are called
 * before and after execution of each task.  These can be used to
 * manipulate the execution environment; for example, reinitializing
 * ThreadLocals, gathering statistics, or adding log entries.
 * Additionally, method [.terminated] can be overridden to perform
 * any special processing that needs to be done once the Executor has
 * fully terminated.
 *
 *
 * If hook, callback, or BlockingQueue methods throw exceptions,
 * internal worker threads may in turn fail, abruptly terminate, and
 * possibly be replaced.</dd>
 *
 * <dt>Queue maintenance</dt>
 *
 * <dd>Method [.getQueue] allows access to the work queue
 * for purposes of monitoring and debugging.  Use of this method for
 * any other purpose is strongly discouraged.  Two supplied methods,
 * [.remove] and [.purge] are available to
 * assist in storage reclamation when large numbers of queued tasks
 * become cancelled.</dd>
 *
 * <dt>Reclamation</dt>
 *
 * <dd>A pool that is no longer referenced in a program *AND*
 * has no remaining threads may be reclaimed (garbage collected)
 * without being explicitly shutdown. You can configure a pool to
 * allow all unused threads to eventually die by setting appropriate
 * keep-alive times, using a lower bound of zero core threads and/or
 * setting [.allowCoreThreadTimeOut].  </dd>
 *
</dl> *
 *
 *
 * **Extension example.** Most extensions of this class
 * override one or more of the protected hook methods. For example,
 * here is a subclass that adds a simple pause/resume feature:
 *
 * <pre> `class PausableThreadPoolExecutor extends ThreadPoolExecutor {
 * private boolean isPaused;
 * private ReentrantLock pauseLock = new ReentrantLock();
 * private Condition unpaused = pauseLock.newCondition();
 *
 * public PausableThreadPoolExecutor(...) { super(...); }
 *
 * protected void beforeExecute(Thread t, Runnable r) {
 * super.beforeExecute(t, r);
 * pauseLock.lock();
 * try {
 * while (isPaused) unpaused.await();
 * } catch (InterruptedException ie) {
 * t.interrupt();
 * } finally {
 * pauseLock.unlock();
 * }
 * }
 *
 * public void pause() {
 * pauseLock.lock();
 * try {
 * isPaused = true;
 * } finally {
 * pauseLock.unlock();
 * }
 * }
 *
 * public void resume() {
 * pauseLock.lock();
 * try {
 * isPaused = false;
 * unpaused.signalAll();
 * } finally {
 * pauseLock.unlock();
 * }
 * }
 * }`</pre>
 *
 * @since 1.5
 * @author Doug Lea
 */
@Ported(from = "java.util.concurrent.ThreadPoolExecutor")
open class ThreadPoolExecutor(
    corePoolSize: Int,
    maximumPoolSize: Int,
    keepAliveTime: Long,
    unit: TimeUnit,
    workQueue: BlockingQueue<Runnable>,
    threadFactory: ThreadFactory = Executors.defaultThreadFactory(),
    handler: RejectedExecutionHandler = defaultHandler,
) : AbstractExecutorService() {
    /**
     * The main pool control state, ctl, is an atomic integer packing
     * two conceptual fields
     * workerCount, indicating the effective number of threads
     * runState,    indicating whether running, shutting down etc
     *
     * In order to pack them into one int, we limit workerCount to
     * (2^29)-1 (about 500 million) threads rather than (2^31)-1 (2
     * billion) otherwise representable. If this is ever an issue in
     * the future, the variable can be changed to be an AtomicLong,
     * and the shift/mask constants below adjusted. But until the need
     * arises, this code is a bit faster and simpler using an int.
     *
     * The workerCount is the number of workers that have been
     * permitted to start and not permitted to stop.  The value may be
     * transiently different from the actual number of live threads,
     * for example when a ThreadFactory fails to create a thread when
     * asked, and when exiting threads are still performing
     * bookkeeping before terminating. The user-visible pool size is
     * reported as the current size of the workers set.
     *
     * The runState provides the main lifecycle control, taking on values:
     *
     * RUNNING:  Accept new tasks and process queued tasks
     * SHUTDOWN: Don't accept new tasks, but process queued tasks
     * STOP:     Don't accept new tasks, don't process queued tasks,
     * and interrupt in-progress tasks
     * TIDYING:  All tasks have terminated, workerCount is zero,
     * the thread transitioning to state TIDYING
     * will run the terminated() hook method
     * TERMINATED: terminated() has completed
     *
     * The numerical order among these values matters, to allow
     * ordered comparisons. The runState monotonically increases over
     * time, but need not hit each state. The transitions are:
     *
     * RUNNING -> SHUTDOWN
     * On invocation of shutdown()
     * (RUNNING or SHUTDOWN) -> STOP
     * On invocation of shutdownNow()
     * SHUTDOWN -> TIDYING
     * When both queue and pool are empty
     * STOP -> TIDYING
     * When pool is empty
     * TIDYING -> TERMINATED
     * When the terminated() hook method has completed
     *
     * Threads waiting in awaitTermination() will return when the
     * state reaches TERMINATED.
     *
     * Detecting the transition from SHUTDOWN to TIDYING is less
     * straightforward than you'd like because the queue may become
     * empty after non-empty and vice versa during SHUTDOWN state, but
     * we can only terminate if, after seeing that it is empty, we see
     * that workerCount is 0 (which sometimes entails a recheck -- see
     * below).
     */
    private val ctl = AtomicInteger(ctlOf(RUNNING, 0))

    /**
     * Attempts to CAS-increment the workerCount field of ctl.
     */
    @OptIn(ExperimentalAtomicApi::class)
    private fun compareAndIncrementWorkerCount(expect: Int): Boolean {
        return ctl.compareAndSet(expect, expect + 1)
    }

    /**
     * Attempts to CAS-decrement the workerCount field of ctl.
     */
    @OptIn(ExperimentalAtomicApi::class)
    private fun compareAndDecrementWorkerCount(expect: Int): Boolean {
        return ctl.compareAndSet(expect, expect - 1)
    }

    /**
     * Decrements the workerCount field of ctl. This is called only on
     * abrupt termination of a thread (see processWorkerExit). Other
     * decrements are performed within getTask.
     */
    @OptIn(ExperimentalAtomicApi::class)
    private fun decrementWorkerCount() {
        ctl.addAndFetch(-1)
    }

    /**
     * The queue used for holding tasks and handing off to worker
     * threads.  We do not require that workQueue.poll() returning
     * null necessarily means that workQueue.isEmpty(), so rely
     * solely on isEmpty to see if the queue is empty (which we must
     * do for example when deciding whether to transition from
     * SHUTDOWN to TIDYING).  This accommodates special-purpose
     * queues such as DelayQueues for which poll() is allowed to
     * return null even if it may later return non-null when delays
     * expire.
     */
    private val workQueue: BlockingQueue<Runnable>

    /**
     * Lock held on access to workers set and related bookkeeping.
     * While we could use a concurrent set of some sort, it turns out
     * to be generally preferable to use a lock. Among the reasons is
     * that this serializes interruptIdleWorkers, which avoids
     * unnecessary interrupt storms, especially during shutdown.
     * Otherwise exiting threads would concurrently interrupt those
     * that have not yet interrupted. It also simplifies some of the
     * associated statistics bookkeeping of largestPoolSize etc. We
     * also hold mainLock on shutdown and shutdownNow, for the sake of
     * ensuring workers set is stable while separately checking
     * permission to interrupt and actually interrupting.
     */
    private val mainLock: ReentrantLock = ReentrantLock()

    /**
     * Set containing all worker threads in pool. Accessed only when
     * holding mainLock.
     */
    private val workers = HashSet<Worker>()
    private val logger = KotlinLogging.logger {}

    /**
     * Wait condition to support awaitTermination.
     */
    private val termination: Condition = mainLock.newCondition()

    /**
     * The thread container for the worker threads.
     */
    private val container: SharedThreadContainer

    /**
     * Tracks largest attained pool size. Accessed only under
     * mainLock.
     */
    private var largestPoolSize = 0

    /**
     * Counter for completed tasks. Updated only on termination of
     * worker threads. Accessed only under mainLock.
     */
    private var completedTaskCount: Long = 0

    /*
     * All user control parameters are declared as volatiles so that
     * ongoing actions are based on freshest values, but without need
     * for locking, since no internal invariants depend on them
     * changing synchronously with respect to other actions.
     */
    /**
     * Factory for new threads. All threads are created using this
     * factory (via method addWorker).  All callers must be prepared
     * for addWorker to fail, which may reflect a system or user's
     * policy limiting the number of threads.  Even though it is not
     * treated as an error, failure to create threads may result in
     * new tasks being rejected or existing ones remaining stuck in
     * the queue.
     *
     * We go further and preserve pool invariants even in the face of
     * errors such as OutOfMemoryError, that might be thrown while
     * trying to create threads.  Such errors are rather common due to
     * the need to allocate a native stack in Thread.start, and users
     * will want to perform clean pool shutdown to clean up.  There
     * will likely be enough memory available for the cleanup code to
     * complete without encountering yet another OutOfMemoryError.
     */
    @Volatile
    private var threadFactory: ThreadFactory

    /**
     * Handler called when saturated or shutdown in execute.
     */
    @Volatile
    private var handler: RejectedExecutionHandler

    /**
     * Timeout in nanoseconds for idle threads waiting for work.
     * Threads use this timeout when there are more than corePoolSize
     * present or if allowCoreThreadTimeOut. Otherwise they wait
     * forever for new work.
     */
    @Volatile
    private var keepAliveTime: Long

    /**
     * If false (default), core threads stay alive even when idle.
     * If true, core threads use keepAliveTime to time out waiting
     * for work.
     */
    @Volatile
    private var allowCoreThreadTimeOut = false

    /**
     * Core pool size is the minimum number of workers to keep alive
     * (and not allow to time out etc) unless allowCoreThreadTimeOut
     * is set, in which case the minimum is zero.
     *
     * Since the worker count is actually stored in COUNT_BITS bits,
     * the effective limit is `corePoolSize & COUNT_MASK`.
     */
    @Volatile
    private var corePoolSize: Int

    /**
     * Maximum pool size.
     *
     * Since the worker count is actually stored in COUNT_BITS bits,
     * the effective limit is `maximumPoolSize & COUNT_MASK`.
     */
    @Volatile
    private var maximumPoolSize: Int

    /**
     * Class Worker mainly maintains interrupt control state for
     * threads running tasks, along with other minor bookkeeping.
     * This class opportunistically extends AbstractQueuedSynchronizer
     * to simplify acquiring and releasing a lock surrounding each
     * task execution.  This protects against interrupts that are
     * intended to wake up a worker thread waiting for a task from
     * instead interrupting a task being run.  We implement a simple
     * non-reentrant mutual exclusion lock rather than use
     * ReentrantLock because we do not want worker tasks to be able to
     * reacquire the lock when they invoke pool control methods like
     * setCorePoolSize.  Additionally, to suppress interrupts until
     * the thread actually starts running tasks, we initialize lock
     * state to a negative value, and clear it upon start (in
     * runWorker).
     */
    inner class Worker
        (firstTask: Runnable?) : AbstractQueuedSynchronizer(), CoroutineRunnable {
        /** Thread this worker is running in.  Null if factory fails.  */
        // Unlikely to be serializable
        val thread: Job/*java.lang.Thread*/

        /** Initial task to run.  Possibly null.  */
        var firstTask:  // Not statically typed as Serializable
                Runnable?

        /** Per-thread task counter  */
        @Volatile
        var completedTasks: Long = 0

        // Simple spin lock replacing AQS usage for task execution critical section to avoid coroutine suspension deadlock.
        @OptIn(ExperimentalAtomicApi::class)
        private val simpleLock = kotlin.concurrent.atomics.AtomicInt(0)

        // Simple, non-inline wrapper to avoid exposing private field in public inline site.
        @OptIn(ExperimentalAtomicApi::class)
        fun <T> withSimpleLock(block: () -> T): T {
            while (!simpleLock.compareAndSet(0, 1)) {
                // spin; could add cooperative backoff if needed
            }
            // Emulate original AQS lock visibility for isLocked checks (state!=0)
            val prevState = state
            state = 1
            try {
                return block()
            } finally {
                state = 0 // back to unlocked (ignore prevState; should be 0 after first unlock())
                simpleLock.store(0)
            }
        }

        // TODO: switch to AbstractQueuedLongSynchronizer and move
        // completedTasks into the lock word.
        /**
         * Creates with given first task and thread from ThreadFactory.
         * @param firstTask the first task (null if none)
         */
        init {
            state = -1 // inhibit interrupts until runWorker
            this.firstTask = firstTask
            this.thread = threadFactory.newThread(this)
            //logger.debug { "[Worker] created id=${this.hashCode()} hasFirstTask=${firstTask!=null} job=$thread" }
        }

        /** Delegates main run loop to outer runWorker.  */
        override fun run() {
            // no-op: execution happens in runSuspending to avoid nested runBlocking
            // Fallback runners (that don't call runSuspending) still have r.run() invoked
        }

        override suspend fun runSuspending() {
            //logger.debug { "[Worker] enter runSuspending id=${this.hashCode()} firstTaskWasNull=${firstTask==null}" }
            runWorker(this@Worker)
            //logger.debug { "[Worker] exit runSuspending id=${this.hashCode()}" }
        }

        override val isHeldExclusively: Boolean
            // Lock methods
            get() = state != 0

        override suspend fun tryAcquire(unused: Int): Boolean {
            if (compareAndSetState(0, 1)) {
                setExclusiveOwnerThread(/*java.lang.Thread.currentThread()*/ currentCoroutineContext()[Job]!!)
                return true
            }
            return false
        }

        override fun tryRelease(unused: Int): Boolean {
            setExclusiveOwnerThread(null)
            state = 0
            return true
        }

        suspend fun lock() {
            acquire(1)
        }

    suspend fun tryLock(): Boolean = tryAcquire(1)

        fun unlock() {
            release(1)
        }

        val isLocked: Boolean
            get() = this.isHeldExclusively

        fun interruptIfStarted() {
            var t: Job? = null /*java.lang.Thread*/
            if (state >= 0 && (thread.also { t = it }) != null && !t!!.isCancelled) {
                t.cancel()
            }
        }
    }

    /*
     * Methods for setting control state
     */
    /**
     * Transitions runState to given target, or leaves it alone if
     * already at least the given target.
     *
     * @param targetState the desired state, either SHUTDOWN or STOP
     * (but not TIDYING or TERMINATED -- use tryTerminate for that)
     */
    @OptIn(ExperimentalAtomicApi::class)
    private fun advanceRunState(targetState: Int) {
        // assert targetState == SHUTDOWN || targetState == STOP;
        while (true) {
            val c = ctl.load()
            if (runStateAtLeast(c, targetState) ||
                ctl.compareAndSet(c, ctlOf(targetState, workerCountOf(c)))
            ) break
        }
    }

    /**
     * Transitions to TERMINATED state if either (SHUTDOWN and pool
     * and queue empty) or (STOP and pool empty).  If otherwise
     * eligible to terminate but workerCount is nonzero, interrupts an
     * idle worker to ensure that shutdown signals propagate. This
     * method must be called following any action that might make
     * termination possible -- reducing worker count or removing tasks
     * from the queue during shutdown. The method is non-private to
     * allow access from ScheduledThreadPoolExecutor.
     */
    @OptIn(ExperimentalAtomicApi::class)
    suspend fun tryTerminate() {
        while (true) {
            val c = ctl.load()
            if (isRunning(c) ||
                runStateAtLeast(c, TIDYING) ||
                (runStateLessThan(c, STOP) && !workQueue.isEmpty())
            ) return
            if (workerCountOf(c) != 0) { // Eligible to terminate
                interruptIdleWorkers(ONLY_ONE)
                return
            }

            val mainLock: ReentrantLock = this.mainLock
            mainLock.lock()
            try {
                if (ctl.compareAndSet(c, ctlOf(TIDYING, 0))) {
                    try {
                        terminated()
                    } finally {
                        ctl.set(ctlOf(TERMINATED, 0))
                        termination.signalAll()
                        container.close()
                    }
                    return
                }
            } finally {
                mainLock.unlock()
            }
        }
    }

    /*
     * Methods for controlling interrupts to worker threads.
     */
    /**
     * Interrupts all threads, even if active.
     */
    private fun interruptWorkers() {
        // assert mainLock.isHeldByCurrentThread();
        for (w in workers) w.interruptIfStarted()
    }

    /**
     * Interrupts threads that might be waiting for tasks (as
     * indicated by not being locked) so they can check for
     * termination or configuration changes.
     *
     * @param onlyOne If true, interrupt at most one worker. This is
     * called only from tryTerminate when termination is otherwise
     * enabled but there are still other workers.  In this case, at
     * most one waiting worker is interrupted to propagate shutdown
     * signals in case all threads are currently waiting.
     * Interrupting any arbitrary thread ensures that newly arriving
     * workers since shutdown began will also eventually exit.
     * To guarantee eventual termination, it suffices to always
     * interrupt only one idle worker, but shutdown() interrupts all
     * idle workers so that redundant workers exit promptly, not
     * waiting for a straggler task to finish.
     */
    /**
     * Common form of interruptIdleWorkers, to avoid having to
     * remember what the boolean argument means.
     */
    private fun interruptIdleWorkers(onlyOne: Boolean = false) {
        val mainLock: ReentrantLock = this.mainLock
        mainLock.lock()
        try {
            for (w in workers) {
                val t: Job = w.thread
                val lockedAttempt = runBlocking { w.tryLock() }
                val currentlyLocked = !lockedAttempt && w.isLocked
                //logger.debug { "[interruptIdleWorkers] considering id=${w.hashCode()} cancelled=${t.isCancelled} acquired=$lockedAttempt isLocked=${w.isLocked} onlyOne=$onlyOne" }
                // Idle if not holding lock (in our port, worker holds lock only while running a task)
                val idle = !w.isLocked
                if (!t.isCancelled && idle) {
                    //logger.debug { "[interruptIdleWorkers] cancelling IDLE id=${w.hashCode()}" }
                    t.cancel()
                }
                // release if we actually acquired
                if (lockedAttempt) w.unlock()
                if (onlyOne) break
            }
        } finally {
            mainLock.unlock()
        }
    }

    /**
     * Variant used when caller already holds mainLock to avoid re-lock deadlock (shutdown path).
     */
    private fun interruptIdleWorkersUnderLock(onlyOne: Boolean = false) {
        // mainLock already held
        for (w in workers) {
            val t: Job = w.thread
            val lockedAttempt = runBlocking { w.tryLock() }
            val currentlyLocked = !lockedAttempt && w.isLocked
            //logger.debug { "[interruptIdleWorkers*] underLock considering id=${w.hashCode()} cancelled=${t.isCancelled} acquired=$lockedAttempt isLocked=${w.isLocked} onlyOne=$onlyOne" }
            val idle = !w.isLocked
            if (!t.isCancelled && idle) {
                //logger.debug { "[interruptIdleWorkers*] underLock cancelling IDLE id=${w.hashCode()}" }
                t.cancel()
            }
            if (lockedAttempt) w.unlock()
            if (onlyOne) break
        }
    }

    /*
     * Misc utilities, most of which are also exported to
     * ScheduledThreadPoolExecutor
     */
    /**
     * Invokes the rejected execution handler for the given command.
     * Package-protected for use by ScheduledThreadPoolExecutor.
     */
    fun reject(command: Runnable) {
        handler.rejectedExecution(command, this)
    }

    /**
     * Performs any further cleanup following run state transition on
     * invocation of shutdown.  A no-op here, but used by
     * ScheduledThreadPoolExecutor to cancel delayed tasks.
     */
    open fun onShutdown() {
    }

    /**
     * Drains the task queue into a new list, normally using
     * drainTo. But if the queue is a DelayQueue or any other kind of
     * queue for which poll or drainTo may fail to remove some
     * elements, it deletes them one by one.
     */
    private fun drainQueue(): MutableList<Runnable> {
        val q: BlockingQueue<Runnable> = workQueue
        val taskList: ArrayList<Runnable> = ArrayList()
        q.drainTo(taskList)
        if (!q.isEmpty()) {
            for (r in q.toTypedArray<Runnable>()) {
                if (q.remove(r)) taskList.add(r)
            }
        }
        return taskList
    }

    /*
     * Methods for creating, running and cleaning up after workers
     */
    /**
     * Checks if a new worker can be added with respect to current
     * pool state and the given bound (either core or maximum). If so,
     * the worker count is adjusted accordingly, and, if possible, a
     * new worker is created and started, running firstTask as its
     * first task. This method returns false if the pool is stopped or
     * eligible to shut down. It also returns false if the thread
     * factory fails to create a thread when asked.  If the thread
     * creation fails, either due to the thread factory returning
     * null, or due to an exception (typically OutOfMemoryError in
     * Thread.start()), we roll back cleanly.
     *
     * @param firstTask the task the new thread should run first (or
     * null if none). Workers are created with an initial first task
     * (in method execute()) to bypass queuing when there are fewer
     * than corePoolSize threads (in which case we always start one),
     * or when the queue is full (in which case we must bypass queue).
     * Initially idle threads are usually created via
     * prestartCoreThread or to replace other dying workers.
     *
     * @param core if true use corePoolSize as bound, else
     * maximumPoolSize. (A boolean indicator is used here rather than a
     * value to ensure reads of fresh values after checking other pool
     * state).
     * @return true if successful
     */
    @OptIn(ExperimentalAtomicApi::class)
    private fun addWorker(firstTask: Runnable?, core: Boolean): Boolean {
    //logger.debug { "[addWorker] request firstTask=${firstTask!=null} core=$core currentWorkers=${workerCountOf(ctl.load())}" }
        var c = ctl.load()
        retry@ while (true) {
            // Check if queue empty only if necessary.
            if (runStateAtLeast(c, SHUTDOWN)
                && (runStateAtLeast(c, STOP)
                        || firstTask != null || workQueue.isEmpty())
            ) return false

            while (true) {
                if (workerCountOf(c)
                    >= ((if (core) corePoolSize else maximumPoolSize) and COUNT_MASK)
                ) return false
                if (compareAndIncrementWorkerCount(c)) break@retry
                c = ctl.load() // Re-read ctl
                if (runStateAtLeast(c, SHUTDOWN)) {
                    continue@retry
                }
            }
        }
        var workerStarted = false
        var workerAdded = false
        var w: Worker? = null
    try {
            w = Worker(firstTask)
            val t: Job /*java.lang.Thread*/ = w.thread
            if (t != null) {
                val mainLock: ReentrantLock = this.mainLock
                mainLock.lock()
                try {
                    // Recheck while holding lock.
                    // Back out on ThreadFactory failure or if
                    // shut down before lock acquired.
                    val c = ctl.load()

                    if (isRunning(c) ||
                        (runStateLessThan(c, STOP) && firstTask == null)
                    ) {
                        // TODO check if thread is NEW but use Job instead of Thread
                        //if (t.getState() != java.lang.Thread.State.NEW) throw /*IllegalThreadState*/Exception()

                        workers.add(w)
                        workerAdded = true
                        val s = workers.size
                        if (s > largestPoolSize) largestPoolSize = s
                    }
                } finally {
                    mainLock.unlock()
                }
                if (workerAdded) {
                    //logger.debug { "[addWorker] starting id=${w.hashCode()} firstTask=${firstTask!=null} core=$core workersNow=${workers.size}" }
                    container.start(t)
                    workerStarted = true
                }
            }
        } finally {
            if (!workerStarted) {
                runBlocking{ addWorkerFailed(w!!) }
            }
        }
        return workerStarted
    }

    /**
     * Rolls back the worker thread creation.
     * - removes worker from workers, if present
     * - decrements worker count
     * - rechecks for termination, in case the existence of this
     * worker was holding up termination
     */
    private suspend fun addWorkerFailed(w: Worker) {
        val mainLock: ReentrantLock = this.mainLock
        mainLock.lock()
        try {
            if (w != null) workers.remove(w)
            decrementWorkerCount()
            tryTerminate()
        } finally {
            mainLock.unlock()
        }
    }

    /**
     * Performs cleanup and bookkeeping for a dying worker. Called
     * only from worker threads. Unless completedAbruptly is set,
     * assumes that workerCount has already been adjusted to account
     * for exit.  This method removes thread from worker set, and
     * possibly terminates the pool or replaces the worker if either
     * it exited due to user task exception or if fewer than
     * corePoolSize workers are running or queue is non-empty but
     * there are no workers.
     *
     * @param w the worker
     * @param completedAbruptly if the worker died due to user exception
     */
    @OptIn(ExperimentalAtomicApi::class)
    private suspend fun processWorkerExit(w: Worker, completedAbruptly: Boolean) {
        if (completedAbruptly)  // If abrupt, then workerCount wasn't adjusted
            decrementWorkerCount()

        val mainLock: ReentrantLock = this.mainLock
        mainLock.lock()
        try {
            completedTaskCount += w.completedTasks
            workers.remove(w)
            //logger.debug { "[processWorkerExit] removed id=${w.hashCode()} abrupt=$completedAbruptly workersNow=${workers.size} completedTasks=${w.completedTasks}" }
        } finally {
            mainLock.unlock()
        }

        try {
            tryTerminate()
        } catch (t: Throwable) {
            //logger.debug { "[processWorkerExit] tryTerminate threw ${t::class.simpleName}: ${t.message}" }
            throw t
        }

        val c = ctl.load()
        if (runStateLessThan(c, STOP)) {
            if (!completedAbruptly) {
                var min = if (allowCoreThreadTimeOut) 0 else corePoolSize
                if (min == 0 && !workQueue.isEmpty()) min = 1
                if (workerCountOf(c) >= min) {
                    //logger.debug { "[processWorkerExit] no replacement needed state=${runStateOf(c)} wc=${workerCountOf(c)} min=$min queueEmpty=${workQueue.isEmpty()}" }
                    return  // replacement not needed
                }
            }
            //logger.debug { "[processWorkerExit] adding replacement worker state=${runStateOf(c)} wc=${workerCountOf(c)} core=$corePoolSize queueEmpty=${workQueue.isEmpty()}" }
            addWorker(null, false)
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    private suspend fun getTask(): Runnable? {
        var timedOut = false // Did the last poll() time out
        while (true) {
            val c = ctl.load()
            val state = runStateOf(c)
            val wc = workerCountOf(c)
            val queueSize = workQueue.size
            val timed = allowCoreThreadTimeOut || wc > corePoolSize
            //logger.debug { "[getTask] loop state=$state wc=$wc queueSize=$queueSize timed=$timed timedOutPreviously=$timedOut" }

            if (runStateAtLeast(c, SHUTDOWN) && (runStateAtLeast(c, STOP) || workQueue.isEmpty())) {
                //logger.debug { "[getTask] exit state>=SHUTDOWN queueEmpty=${workQueue.isEmpty()} state=$state wc=$wc -> decrement & return null" }
                decrementWorkerCount()
                return null
            }

            if ((wc > maximumPoolSize || (timed && timedOut)) && (wc > 1 || workQueue.isEmpty())) {
                if (compareAndDecrementWorkerCount(c)) {
                    //logger.debug { "[getTask] exit culling wc=$wc max=$maximumPoolSize timed=$timed timedOut=$timedOut queueEmpty=${workQueue.isEmpty()}" }
                    return null
                }
                //logger.debug { "[getTask] CAS decrement failed; retry loop" }
                continue
            }

            try {
                val r: Runnable? = if (timed) {
                    val rlocal = workQueue.poll(keepAliveTime, TimeUnit.NANOSECONDS)
                    if (rlocal != null) {
                        //logger.debug { "[getTask] obtained timed task" }
                    }else{
                        //logger.debug { "[getTask] poll timed out (no task)" }
                    }
                    rlocal
                } else {
                    //logger.debug { "[getTask] invoking take (may suspend)" }
                    val rlocal = workQueue.take()
                    //logger.debug { "[getTask] obtained task via take" }
                    rlocal
                }
                if (r != null) return r
                timedOut = true
            } catch (retry: InterruptedException) {
                timedOut = false
            } catch (retry: CancellationException) {
                decrementWorkerCount()
                return null
            }
        }
    }

    /**
     * Main worker run loop.  Repeatedly gets tasks from queue and
     * executes them, while coping with a number of issues:
     *
     * 1. We may start out with an initial task, in which case we
     * don't need to get the first one. Otherwise, as long as pool is
     * running, we get tasks from getTask. If it returns null then the
     * worker exits due to changed pool state or configuration
     * parameters.  Other exits result from exception throws in
     * external code, in which case completedAbruptly holds, which
     * usually leads processWorkerExit to replace this thread.
     *
     * 2. Before running any task, the lock is acquired to prevent
     * other pool interrupts while the task is executing, and then we
     * ensure that unless pool is stopping, this thread does not have
     * its interrupt set.
     *
     * 3. Each task run is preceded by a call to beforeExecute, which
     * might throw an exception, in which case we cause thread to die
     * (breaking loop with completedAbruptly true) without processing
     * the task.
     *
     * 4. Assuming beforeExecute completes normally, we run the task,
     * gathering any of its thrown exceptions to send to afterExecute.
     * We separately handle RuntimeException, Error (both of which the
     * specs guarantee that we trap) and arbitrary Throwables.
     * Because we cannot rethrow Throwables within Runnable.run, we
     * wrap them within Errors on the way out (to the thread's
     * UncaughtExceptionHandler).  Any thrown exception also
     * conservatively causes thread to die.
     *
     * 5. After task.run completes, we call afterExecute, which may
     * also throw an exception, which will also cause thread to
     * die. According to JLS Sec 14.20, this exception is the one that
     * will be in effect even if task.run throws.
     *
     * The net effect of the exception mechanics is that afterExecute
     * and the thread's UncaughtExceptionHandler have as accurate
     * information as we can provide about any problems encountered by
     * user code.
     *
     * @param w the worker
     */
    @OptIn(ExperimentalAtomicApi::class)
    suspend fun runWorker(w: Worker) {
        val wt: Job /*java.lang.Thread*/ = coroutineContext[Job]!! /*java.lang.Thread.currentThread()*/
        var task: Runnable? = w.firstTask
        w.firstTask = null
        w.unlock() // allow interrupts
        var completedAbruptly = true
        try {
            while (task != null || (getTask().also { task = it }) != null) {
                if (task is FutureTask<*>) {
                    //logger.debug { "[runWorker] about to run FutureTask hash=${task.hashCode()}" }
                }
                // Temporarily bypass worker lock (AQS) to diagnose hang
                // println("[DBG][runWorker] attempting to lock worker id=${w.hashCode()}")
                // w.lock()
                // println("[DBG][runWorker] acquired lock worker id=${w.hashCode()}")
                // If pool is stopping, ensure thread is interrupted;
                // if not, ensure thread is not interrupted.  This
                // requires a recheck in second case to deal with
                // shutdownNow race while clearing interrupt
                if ((runStateAtLeast(ctl.load(), STOP) ||
                            (/*java.lang.Thread.interrupted()*/ currentCoroutineContext()[Job]!!.isCancelled &&
                                    runStateAtLeast(ctl.load(), STOP))) &&
                    !wt.isCancelled
                ) wt.cancel()
                w.withSimpleLock {
                    val current = task!!
                    try {
                        beforeExecute(wt, current)
                        try {
                            current.run()
                            //logger.debug { "worker id=${w.hashCode()} task run complete" }
                            if (current is FutureTask<*>) {
                                //logger.debug { "[DBG][runWorker] FutureTask run complete hash=${current.hashCode()}" }
                            }
                            afterExecute(current, null)
                        } catch (ex: Throwable) {
                            afterExecute(current, ex)
                            throw ex
                        }
                    } finally {
                        task = null
                        w.completedTasks++
                    }
                }
            }
            completedAbruptly = false
        } finally {
            //logger.debug { "[runWorker] exit worker id=${w.hashCode()} completedAbruptly=$completedAbruptly" }
            processWorkerExit(w, completedAbruptly)
        }
    }

    /**
     * Creates a new `ThreadPoolExecutor` with the given initial
     * parameters and the
     * [default thread factory][Executors.defaultThreadFactory].
     *
     * @param corePoolSize the number of threads to keep in the pool, even
     * if they are idle, unless `allowCoreThreadTimeOut` is set
     * @param maximumPoolSize the maximum number of threads to allow in the
     * pool
     * @param keepAliveTime when the number of threads is greater than
     * the core, this is the maximum time that excess idle threads
     * will wait for new tasks before terminating.
     * @param unit the time unit for the `keepAliveTime` argument
     * @param workQueue the queue to use for holding tasks before they are
     * executed.  This queue will hold only the `Runnable`
     * tasks submitted by the `execute` method.
     * @param handler the handler to use when execution is blocked
     * because the thread bounds and queue capacities are reached
     * @throws IllegalArgumentException if one of the following holds:<br></br>
     * `corePoolSize < 0`<br></br>
     * `keepAliveTime < 0`<br></br>
     * `maximumPoolSize <= 0`<br></br>
     * `maximumPoolSize < corePoolSize`
     * @throws NullPointerException if `workQueue`
     * or `handler` is null
     */
    constructor(
        corePoolSize: Int,
        maximumPoolSize: Int,
        keepAliveTime: Long,
        unit: TimeUnit,
        workQueue: BlockingQueue<Runnable>,
        handler: RejectedExecutionHandler
    ) : this(
        corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
        Executors.defaultThreadFactory(), handler
    )

    /**
     * Creates a new `ThreadPoolExecutor` with the given initial
     * parameters.
     *
     * @param corePoolSize the number of threads to keep in the pool, even
     * if they are idle, unless `allowCoreThreadTimeOut` is set
     * @param maximumPoolSize the maximum number of threads to allow in the
     * pool
     * @param keepAliveTime when the number of threads is greater than
     * the core, this is the maximum time that excess idle threads
     * will wait for new tasks before terminating.
     * @param unit the time unit for the `keepAliveTime` argument
     * @param workQueue the queue to use for holding tasks before they are
     * executed.  This queue will hold only the `Runnable`
     * tasks submitted by the `execute` method.
     * @param threadFactory the factory to use when the executor
     * creates a new thread
     * @param handler the handler to use when execution is blocked
     * because the thread bounds and queue capacities are reached
     * @throws IllegalArgumentException if one of the following holds:<br></br>
     * `corePoolSize < 0`<br></br>
     * `keepAliveTime < 0`<br></br>
     * `maximumPoolSize <= 0`<br></br>
     * `maximumPoolSize < corePoolSize`
     * @throws NullPointerException if `workQueue`
     * or `threadFactory` or `handler` is null
     */
    // Public constructors and methods
    /**
     * Creates a new `ThreadPoolExecutor` with the given initial
     * parameters, the
     * [default thread factory][Executors.defaultThreadFactory]
     * and the [ default rejected execution handler][ThreadPoolExecutor.AbortPolicy].
     *
     *
     * It may be more convenient to use one of the [Executors]
     * factory methods instead of this general purpose constructor.
     *
     * @param corePoolSize the number of threads to keep in the pool, even
     * if they are idle, unless `allowCoreThreadTimeOut` is set
     * @param maximumPoolSize the maximum number of threads to allow in the
     * pool
     * @param keepAliveTime when the number of threads is greater than
     * the core, this is the maximum time that excess idle threads
     * will wait for new tasks before terminating.
     * @param unit the time unit for the `keepAliveTime` argument
     * @param workQueue the queue to use for holding tasks before they are
     * executed.  This queue will hold only the `Runnable`
     * tasks submitted by the `execute` method.
     * @throws IllegalArgumentException if one of the following holds:<br></br>
     * `corePoolSize < 0`<br></br>
     * `keepAliveTime < 0`<br></br>
     * `maximumPoolSize <= 0`<br></br>
     * `maximumPoolSize < corePoolSize`
     * @throws NullPointerException if `workQueue` is null
     */
    /**
     * Creates a new `ThreadPoolExecutor` with the given initial
     * parameters and the [ default rejected execution handler][ThreadPoolExecutor.AbortPolicy].
     *
     * @param corePoolSize the number of threads to keep in the pool, even
     * if they are idle, unless `allowCoreThreadTimeOut` is set
     * @param maximumPoolSize the maximum number of threads to allow in the
     * pool
     * @param keepAliveTime when the number of threads is greater than
     * the core, this is the maximum time that excess idle threads
     * will wait for new tasks before terminating.
     * @param unit the time unit for the `keepAliveTime` argument
     * @param workQueue the queue to use for holding tasks before they are
     * executed.  This queue will hold only the `Runnable`
     * tasks submitted by the `execute` method.
     * @param threadFactory the factory to use when the executor
     * creates a new thread
     * @throws IllegalArgumentException if one of the following holds:<br></br>
     * `corePoolSize < 0`<br></br>
     * `keepAliveTime < 0`<br></br>
     * `maximumPoolSize <= 0`<br></br>
     * `maximumPoolSize < corePoolSize`
     * @throws NullPointerException if `workQueue`
     * or `threadFactory` is null
     */
    init {
        require(!(corePoolSize < 0 || maximumPoolSize <= 0 || maximumPoolSize < corePoolSize || keepAliveTime < 0))
    // Kotlin's type system ensures non-null parameters; no need for manual null checks here.

        this.corePoolSize = corePoolSize
        this.maximumPoolSize = maximumPoolSize
        this.workQueue = workQueue
        this.keepAliveTime = unit.toNanos(keepAliveTime)
        this.threadFactory = threadFactory
        this.handler = handler

        val name: String = /*java.util.Objects.toIdentityString(this)*/
            "ThreadPoolExecutor@" + Int.toHexString(hashCode())
        this.container = SharedThreadContainer.create(name)
    }

    /**
     * Executes the given task sometime in the future.  The task
     * may execute in a new thread or in an existing pooled thread.
     *
     * If the task cannot be submitted for execution, either because this
     * executor has been shutdown or because its capacity has been reached,
     * the task is handled by the current [RejectedExecutionHandler].
     *
     * @param command the task to execute
     * @throws RejectedExecutionException at discretion of
     * `RejectedExecutionHandler`, if the task
     * cannot be accepted for execution
     * @throws NullPointerException if `command` is null
     */
    @OptIn(ExperimentalAtomicApi::class)
    override fun execute(command: Runnable) {
        // not needed because of null safety of kotlin language
        //if (command == null) throw java.lang.NullPointerException()

        /*
         * Proceed in 3 steps:
         *
         * 1. If fewer than corePoolSize threads are running, try to
         * start a new thread with the given command as its first
         * task.  The call to addWorker atomically checks runState and
         * workerCount, and so prevents false alarms that would add
         * threads when it shouldn't, by returning false.
         *
         * 2. If a task can be successfully queued, then we still need
         * to double-check whether we should have added a thread
         * (because existing ones died since last checking) or that
         * the pool shut down since entry into this method. So we
         * recheck state and if necessary roll back the enqueuing if
         * stopped, or start a new thread if there are none.
         *
         * 3. If we cannot queue task, then we try to add a new
         * thread.  If it fails, we know we are shut down or saturated
         * and so reject the task.
         */
        var c = ctl.load()
        //logger.debug { "[execute] enter workers=${workerCountOf(c)} core=$corePoolSize max=$maximumPoolSize queueSize=${workQueue.size}" }
        if (workerCountOf(c) < corePoolSize) {
            if (addWorker(command, true)) {
                //logger.debug { "[execute] started core worker with first task" }
                return
            }
            c = ctl.load()
        }
        if (isRunning(c) && workQueue.offer(command)) {
            val recheck = ctl.load()
            if (!isRunning(recheck) && runBlocking { remove(command) }) {
                //logger.debug { "[execute] removed task after detecting not running; rejecting" }
                reject(command)
            }
            else if (workerCountOf(recheck) == 0) {
                //logger.debug { "[execute] no workers -> add non-core worker" }
                addWorker(null, false)
            }
            //logger.debug { "[execute] queued task queueSize=${workQueue.size}" }
        } else if (!addWorker(command, false)) {
            //logger.debug { "[execute] addWorker(non-core) failed -> reject" }
            reject(command)
        } else {
            //logger.debug { "[execute] started non-core worker with first task" }
        }
    }

    /**
     * Initiates an orderly shutdown in which previously submitted
     * tasks are executed, but no new tasks will be accepted.
     * Invocation has no additional effect if already shut down.
     *
     *
     * This method does not wait for previously submitted tasks to
     * complete execution.  Use [awaitTermination][.awaitTermination]
     * to do that.
     */
    @OptIn(ExperimentalAtomicApi::class)
    override fun shutdown() {
        val mainLock: ReentrantLock = this.mainLock
        mainLock.lock()
        try {
            //logger.debug { "[shutdown] advancing run state to SHUTDOWN" }
            advanceRunState(SHUTDOWN)
            //logger.debug { "[shutdown] interrupting idle workers size=${workers.size}" }
            // Avoid attempting to re-acquire mainLock inside interruptIdleWorkers (our lock may not be truly reentrant in this port)
            interruptIdleWorkersUnderLock()
            onShutdown() // hook for ScheduledThreadPoolExecutor
        } finally {
            mainLock.unlock()
        }
        //logger.debug { "[shutdown] invoking tryTerminate" }
        runBlocking { tryTerminate() }
        //logger.debug { "[shutdown] completed tryTerminate state=${runStateOf(ctl.load())} wc=${workerCountOf(ctl.load())}" }
    }

    /**
     * Attempts to stop all actively executing tasks, halts the
     * processing of waiting tasks, and returns a list of the tasks
     * that were awaiting execution. These tasks are drained (removed)
     * from the task queue upon return from this method.
     *
     *
     * This method does not wait for actively executing tasks to
     * terminate.  Use [awaitTermination][.awaitTermination] to
     * do that.
     *
     *
     * There are no guarantees beyond best-effort attempts to stop
     * processing actively executing tasks.  This implementation
     * interrupts tasks via [Thread.interrupt]; any task that
     * fails to respond to interrupts may never terminate.
     */
    override suspend fun shutdownNow(): MutableList<Runnable> {
        var tasks: MutableList<Runnable>
        val mainLock: ReentrantLock = this.mainLock
        mainLock.lock()
        try {
            advanceRunState(STOP)
            interruptWorkers()
            tasks = drainQueue()
        } finally {
            mainLock.unlock()
        }
        tryTerminate()
        return tasks
    }

    @OptIn(ExperimentalAtomicApi::class)
    override val isShutdown: Boolean
        get() = runStateAtLeast(ctl.load(), SHUTDOWN)

    @OptIn(ExperimentalAtomicApi::class)
    val isStopped: Boolean
        /** Used by ScheduledThreadPoolExecutor.  */
        get() = runStateAtLeast(ctl.load(), STOP)

    @OptIn(ExperimentalAtomicApi::class)
    open val isTerminating: Boolean
        /**
         * Returns true if this executor is in the process of terminating
         * after [.shutdown] or [.shutdownNow] but has not
         * completely terminated.  This method may be useful for
         * debugging. A return of `true` reported a sufficient
         * period after shutdown may indicate that submitted tasks have
         * ignored or suppressed interruption, causing this executor not
         * to properly terminate.
         *
         * @return `true` if terminating but not yet terminated
         */
        get() {
            val c = ctl.load()
            return runStateAtLeast(c, SHUTDOWN) && runStateLessThan(c, TERMINATED)
        }

    @OptIn(ExperimentalAtomicApi::class)
    override val isTerminated: Boolean
        get() = runStateAtLeast(ctl.load(), TERMINATED)

    @OptIn(ExperimentalAtomicApi::class)
    override suspend fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean {
        var nanos: Long = unit.toNanos(timeout)
        val mainLock: ReentrantLock = this.mainLock
        mainLock.lock()
        try {
            while (runStateLessThan(ctl.load(), TERMINATED)) {
                if (nanos <= 0L) return false
                nanos = termination.awaitNanos(nanos)
            }
            return true
        } finally {
            mainLock.unlock()
        }
    }

    // Override without "throws Throwable" for compatibility with subclasses
    // whose finalize method invokes super.finalize() (as is recommended).
    // Before JDK 11, finalize() had a non-empty method body.
    /**
     * @implNote Previous versions of this class had a finalize method
     * that shut down this executor, but in this version, finalize
     * does nothing.
     *
     */
    @Deprecated(
        """Finalization has been deprecated for removal.  See
      {@link java.lang.Object#finalize} for background information and details
      about migration options."""
    )
    protected open fun finalize() {
    }

    /**
     * Sets the thread factory used to create new threads.
     *
     * @param threadFactory the new thread factory
     * @throws NullPointerException if threadFactory is null
     * @see .getThreadFactory
     */
    /*fun setThreadFactory(threadFactory: ThreadFactory) {
        // not needed because of null safety of kotlin language
        //if (threadFactory == null) throw java.lang.NullPointerException()

        this.threadFactory = threadFactory
    }*/

    /**
     * Returns the thread factory used to create new threads.
     *
     * @return the current thread factory
     * @see .setThreadFactory
     */
    /*fun getThreadFactory(): ThreadFactory {
        return threadFactory
    }*/

    /**
     * Sets a new handler for unexecutable tasks.
     *
     * @param handler the new handler
     * @throws NullPointerException if handler is null
     * @see .getRejectedExecutionHandler
     */
    fun setRejectedExecutionHandler(handler: RejectedExecutionHandler) {
        // not needed because of null safety of kotlin language
        //if (handler == null) throw java.lang.NullPointerException()

        this.handler = handler
    }

    val rejectedExecutionHandler: RejectedExecutionHandler
        /**
         * Returns the current handler for unexecutable tasks.
         *
         * @return the current handler
         * @see .setRejectedExecutionHandler
         */
        get() = handler

    /**
     * Sets the core number of threads.  This overrides any value set
     * in the constructor.  If the new value is smaller than the
     * current value, excess existing threads will be terminated when
     * they next become idle.  If larger, new threads will, if needed,
     * be started to execute any queued tasks.
     *
     * @param corePoolSize the new core size
     * @throws IllegalArgumentException if `corePoolSize < 0`
     * or `corePoolSize` is greater than the [         ][.getMaximumPoolSize]
     * @see .getCorePoolSize
     */
    @OptIn(ExperimentalAtomicApi::class)
    fun setCorePoolSize(corePoolSize: Int) {
        require(!(corePoolSize < 0 || maximumPoolSize < corePoolSize))
        val delta = corePoolSize - this.corePoolSize
        this.corePoolSize = corePoolSize
        if (workerCountOf(ctl.load()) > corePoolSize) interruptIdleWorkers()
        else if (delta > 0) {
            // We don't really know how many new threads are "needed".
            // As a heuristic, prestart enough new workers (up to new
            // core size) to handle the current number of tasks in
            // queue, but stop if queue becomes empty while doing so.
            var k = min(delta, workQueue.size)
            while (k-- > 0 && addWorker(null, true)) {
                if (workQueue.isEmpty()) break
            }
        }
    }

    /**
     * Returns the core number of threads.
     *
     * @return the core number of threads
     * @see .setCorePoolSize
     */
    fun getCorePoolSize(): Int {
        return corePoolSize
    }

    /**
     * Starts a core thread, causing it to idly wait for work. This
     * overrides the default policy of starting core threads only when
     * new tasks are executed. This method will return `false`
     * if all core threads have already been started.
     *
     * @return `true` if a thread was started
     */
    @OptIn(ExperimentalAtomicApi::class)
    fun prestartCoreThread(): Boolean {
        return workerCountOf(ctl.load()) < corePoolSize &&
                addWorker(null, true)
    }

    /**
     * Same as prestartCoreThread except arranges that at least one
     * thread is started even if corePoolSize is 0.
     */
    @OptIn(ExperimentalAtomicApi::class)
    fun ensurePrestart() {
        val wc = workerCountOf(ctl.load())
        if (wc < corePoolSize) addWorker(null, true)
        else if (wc == 0) addWorker(null, false)
    }

    /**
     * Starts all core threads, causing them to idly wait for work. This
     * overrides the default policy of starting core threads only when
     * new tasks are executed.
     *
     * @return the number of threads started
     */
    fun prestartAllCoreThreads(): Int {
        var n = 0
        while (addWorker(null, true)) ++n
        return n
    }

    /**
     * Returns true if this pool allows core threads to time out and
     * terminate if no tasks arrive within the keepAlive time, being
     * replaced if needed when new tasks arrive. When true, the same
     * keep-alive policy applying to non-core threads applies also to
     * core threads. When false (the default), core threads are never
     * terminated due to lack of incoming tasks.
     *
     * @return `true` if core threads are allowed to time out,
     * else `false`
     *
     * @since 1.6
     */
    fun allowsCoreThreadTimeOut(): Boolean {
        return allowCoreThreadTimeOut
    }

    /**
     * Sets the policy governing whether core threads may time out and
     * terminate if no tasks arrive within the keep-alive time, being
     * replaced if needed when new tasks arrive. When false, core
     * threads are never terminated due to lack of incoming
     * tasks. When true, the same keep-alive policy applying to
     * non-core threads applies also to core threads. To avoid
     * continual thread replacement, the keep-alive time must be
     * greater than zero when setting `true`. This method
     * should in general be called before the pool is actively used.
     *
     * @param value `true` if should time out, else `false`
     * @throws IllegalArgumentException if value is `true`
     * and the current keep-alive time is not greater than zero
     *
     * @since 1.6
     */
    fun allowCoreThreadTimeOut(value: Boolean) {
        require(!(value && keepAliveTime <= 0)) { "Core threads must have nonzero keep alive times" }
        if (value != allowCoreThreadTimeOut) {
            allowCoreThreadTimeOut = value
            if (value) interruptIdleWorkers()
        }
    }

    /**
     * Sets the maximum allowed number of threads. This overrides any
     * value set in the constructor. If the new value is smaller than
     * the current value, excess existing threads will be
     * terminated when they next become idle.
     *
     * @param maximumPoolSize the new maximum
     * @throws IllegalArgumentException if the new maximum is
     * less than or equal to zero, or
     * less than the [core pool size][.getCorePoolSize]
     * @see .getMaximumPoolSize
     */
    @OptIn(ExperimentalAtomicApi::class)
    fun setMaximumPoolSize(maximumPoolSize: Int) {
        require(!(maximumPoolSize <= 0 || maximumPoolSize < corePoolSize))
        this.maximumPoolSize = maximumPoolSize
        if (workerCountOf(ctl.load()) > maximumPoolSize) interruptIdleWorkers()
    }

    /**
     * Returns the maximum allowed number of threads.
     *
     * @return the maximum allowed number of threads
     * @see .setMaximumPoolSize
     */
    fun getMaximumPoolSize(): Int {
        return maximumPoolSize
    }

    /**
     * Sets the thread keep-alive time, which is the amount of time
     * that threads may remain idle before being terminated.
     * Threads that wait this amount of time without processing a
     * task will be terminated if there are more than the core
     * number of threads currently in the pool, or if this pool
     * [allows core thread timeout][.allowsCoreThreadTimeOut].
     * This overrides any value set in the constructor.
     *
     * @param time the time to wait.  A time value of zero will cause
     * excess threads to terminate immediately after executing tasks.
     * @param unit the time unit of the `time` argument
     * @throws IllegalArgumentException if `time` less than zero or
     * if `time` is zero and `allowsCoreThreadTimeOut`
     * @see .getKeepAliveTime
     */
    fun setKeepAliveTime(time: Long, unit: TimeUnit) {
        require(time >= 0)
        require(!(time == 0L && allowsCoreThreadTimeOut())) { "Core threads must have nonzero keep alive times" }
        val keepAliveTime: Long = unit.toNanos(time)
        val delta = keepAliveTime - this.keepAliveTime
        this.keepAliveTime = keepAliveTime
        if (delta < 0) interruptIdleWorkers()
    }

    /**
     * Returns the thread keep-alive time, which is the amount of time
     * that threads may remain idle before being terminated.
     * Threads that wait this amount of time without processing a
     * task will be terminated if there are more than the core
     * number of threads currently in the pool, or if this pool
     * [allows core thread timeout][.allowsCoreThreadTimeOut].
     *
     * @param unit the desired time unit of the result
     * @return the time limit
     * @see .setKeepAliveTime
     */
    fun getKeepAliveTime(unit: TimeUnit): Long {
        return unit.convert(keepAliveTime, TimeUnit.NANOSECONDS)
    }

    /* User-level queue utilities */
    open val queue: BlockingQueue<Runnable>
        /**
         * Returns the task queue used by this executor. Access to the
         * task queue is intended primarily for debugging and monitoring.
         * This queue may be in active use.  Retrieving the task queue
         * does not prevent queued tasks from executing.
         *
         * @return the task queue
         */
        get() = workQueue

    /**
     * Removes this task from the executor's internal queue if it is
     * present, thus causing it not to be run if it has not already
     * started.
     *
     *
     * This method may be useful as one part of a cancellation
     * scheme.  It may fail to remove tasks that have been converted
     * into other forms before being placed on the internal queue.
     * For example, a task entered using `submit` might be
     * converted into a form that maintains `Future` status.
     * However, in such cases, method [.purge] may be used to
     * remove those Futures that have been cancelled.
     *
     * @param task the task to remove
     * @return `true` if the task was removed
     */
    suspend fun remove(task: Runnable): Boolean {
        val removed: Boolean = workQueue.remove(task)
        tryTerminate() // In case SHUTDOWN and now empty
        return removed
    }

    /**
     * Tries to remove from the work queue all [Future]
     * tasks that have been cancelled. This method can be useful as a
     * storage reclamation operation, that has no other impact on
     * functionality. Cancelled tasks are never executed, but may
     * accumulate in work queues until worker threads can actively
     * remove them. Invoking this method instead tries to remove them now.
     * However, this method may fail to remove tasks in
     * the presence of interference by other threads.
     */
    // implement if needed
    /*fun purge() {
        val q: BlockingQueue<Runnable> = workQueue
        try {
            val it: MutableIterator<Runnable> = q.iterator()
            while (it.hasNext()) {
                val r: Runnable = it.next()
                if (r is java.util.concurrent.Future<*> && (r as java.util.concurrent.Future<*>).isCancelled()) it.remove()
            }
        } catch (fallThrough: ConcurrentModificationException) {
            // Take slow path if we encounter interference during traversal.
            // Make copy for traversal and call remove for cancelled entries.
            // The slow path is more likely to be O(N*N).
            for (r in q.toTypedArray()) if (r is java.util.concurrent.Future<*> && (r as java.util.concurrent.Future<*>).isCancelled()) q.remove(
                r
            )
        }

        tryTerminate() // In case SHUTDOWN and now empty
    }*/

    /* Statistics */
    @OptIn(ExperimentalAtomicApi::class)
    val poolSize: Int
        /**
         * Returns the current number of threads in the pool.
         *
         * @return the number of threads
         */
        get() {
            val mainLock: ReentrantLock = this.mainLock
            mainLock.lock()
            try {
                // Remove rare and surprising possibility of
                // isTerminated() && getPoolSize() > 0
                return if (runStateAtLeast(ctl.load(), TIDYING))
                    0
                else
                    workers.size
            } finally {
                mainLock.unlock()
            }
        }

    val activeCount: Int
        /**
         * Returns the approximate number of threads that are actively
         * executing tasks.
         *
         * @return the number of threads
         */
        get() {
            val mainLock: ReentrantLock = this.mainLock
            mainLock.lock()
            try {
                var n = 0
                for (w in workers) if (w.isLocked) ++n
                return n
            } finally {
                mainLock.unlock()
            }
        }

    /**
     * Returns the largest number of threads that have ever
     * simultaneously been in the pool.
     *
     * @return the number of threads
     */
    fun getLargestPoolSize(): Int {
        val mainLock: ReentrantLock = this.mainLock
        mainLock.lock()
        try {
            return largestPoolSize
        } finally {
            mainLock.unlock()
        }
    }

    val taskCount: Long
        /**
         * Returns the approximate total number of tasks that have ever been
         * scheduled for execution. Because the states of tasks and
         * threads may change dynamically during computation, the returned
         * value is only an approximation.
         *
         * @return the number of tasks
         */
        get() {
            val mainLock: ReentrantLock = this.mainLock
            mainLock.lock()
            try {
                var n = completedTaskCount
                for (w in workers) {
                    n += w.completedTasks
                    if (w.isLocked) ++n
                }
                return n + workQueue.size
            } finally {
                mainLock.unlock()
            }
        }

    /**
     * Returns the approximate total number of tasks that have
     * completed execution. Because the states of tasks and threads
     * may change dynamically during computation, the returned value
     * is only an approximation, but one that does not ever decrease
     * across successive calls.
     *
     * @return the number of tasks
     */
    fun getCompletedTaskCount(): Long {
        val mainLock: ReentrantLock = this.mainLock
        mainLock.lock()
        try {
            var n = completedTaskCount
            for (w in workers) n += w.completedTasks
            return n
        } finally {
            mainLock.unlock()
        }
    }

    /**
     * Returns a string identifying this pool, as well as its state,
     * including indications of run state and estimated worker and
     * task counts.
     *
     * @return a string identifying this pool, as well as its state
     */
    @OptIn(ExperimentalAtomicApi::class)
    override fun toString(): String {
        var ncompleted: Long
        var nworkers: Int
        var nactive: Int
        val mainLock: ReentrantLock = this.mainLock
        mainLock.lock()
        try {
            ncompleted = completedTaskCount
            nactive = 0
            nworkers = workers.size
            for (w in workers) {
                ncompleted += w.completedTasks
                if (w.isLocked) ++nactive
            }
        } finally {
            mainLock.unlock()
        }
        val c = ctl.load()
        val runState =
            if (isRunning(c)) "Running" else if (runStateAtLeast(c, TERMINATED)) "Terminated" else "Shutting down"
        return super.toString() +
                "[" + runState +
                ", pool size = " + nworkers +
                ", active threads = " + nactive +
                ", queued tasks = " + workQueue.size +
                ", completed tasks = " + ncompleted +
                "]"
    }

    /* Extension hooks */
    /**
     * Method invoked prior to executing the given Runnable in the
     * given thread.  This method is invoked by thread `t` that
     * will execute task `r`, and may be used to re-initialize
     * ThreadLocals, or to perform logging.
     *
     *
     * This implementation does nothing, but may be customized in
     * subclasses. Note: To properly nest multiple overridings, subclasses
     * should generally invoke `super.beforeExecute` at the end of
     * this method.
     *
     * @param t the thread that will run task `r`
     * @param r the task that will be executed
     */
    protected open fun beforeExecute(t: Job /*java.lang.Thread*/, r: Runnable) {}

    /**
     * Method invoked upon completion of execution of the given Runnable.
     * This method is invoked by the thread that executed the task. If
     * non-null, the Throwable is the uncaught `RuntimeException`
     * or `Error` that caused execution to terminate abruptly.
     *
     *
     * This implementation does nothing, but may be customized in
     * subclasses. Note: To properly nest multiple overridings, subclasses
     * should generally invoke `super.afterExecute` at the
     * beginning of this method.
     *
     *
     * **Note:** When actions are enclosed in tasks (such as
     * [FutureTask]) either explicitly or via methods such as
     * `submit`, these task objects catch and maintain
     * computational exceptions, and so they do not cause abrupt
     * termination, and the internal exceptions are *not*
     * passed to this method. If you would like to trap both kinds of
     * failures in this method, you can further probe for such cases,
     * as in this sample subclass that prints either the direct cause
     * or the underlying exception if a task has been aborted:
     *
     * <pre> `class ExtendedExecutor extends ThreadPoolExecutor {
     * // ...
     * protected void afterExecute(Runnable r, Throwable t) {
     * super.afterExecute(r, t);
     * if (t == null
     * && r instanceof Future<>
     * && ((Future<>)r).isDone()) {
     * try {
     * Object result = ((Future<>) r).get();
     * } catch (CancellationException ce) {
     * t = ce;
     * } catch (ExecutionException ee) {
     * t = ee.getCause();
     * } catch (InterruptedException ie) {
     * // ignore/reset
     * Thread.currentThread().interrupt();
     * }
     * }
     * if (t != null)
     * System.out.println(t);
     * }
     * }`</pre>
     *
     * @param r the runnable that has completed
     * @param t the exception that caused termination, or null if
     * execution completed normally
     */
    protected open fun afterExecute(r: Runnable, t: Throwable?) {}

    /**
     * Method invoked when the Executor has terminated.  Default
     * implementation does nothing. Note: To properly nest multiple
     * overridings, subclasses should generally invoke
     * `super.terminated` within this method.
     */
    protected open fun terminated() {}

    /* Predefined RejectedExecutionHandlers */
    /**
     * A handler for rejected tasks that runs the rejected task
     * directly in the calling thread of the `execute` method,
     * unless the executor has been shut down, in which case the task
     * is discarded.
     */
    class CallerRunsPolicy
    /**
     * Creates a `CallerRunsPolicy`.
     */
        : RejectedExecutionHandler {
        /**
         * Executes task r in the caller's thread, unless the executor
         * has been shut down, in which case the task is discarded.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         */
        override fun rejectedExecution(r: Runnable, e: ThreadPoolExecutor) {
            if (!e.isShutdown) {
                r.run()
            }
        }
    }

    /**
     * A handler for rejected tasks that throws a
     * [RejectedExecutionException].
     *
     * This is the default handler for [ThreadPoolExecutor] and
     * [ScheduledThreadPoolExecutor].
     */
    class AbortPolicy
    /**
     * Creates an `AbortPolicy`.
     */
        : RejectedExecutionHandler {
        /**
         * Always throws RejectedExecutionException.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         * @throws RejectedExecutionException always
         */
        override fun rejectedExecution(r: Runnable, e: ThreadPoolExecutor) {
            throw /*java.util.concurrent.RejectedExecution*/Exception(
                "Task " + r.toString() +
                        " rejected from " +
                        e.toString()
            )
        }
    }

    /**
     * A handler for rejected tasks that silently discards the
     * rejected task.
     */
    class DiscardPolicy
    /**
     * Creates a `DiscardPolicy`.
     */
        : RejectedExecutionHandler {
        /**
         * Does nothing, which has the effect of discarding task r.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         */
        override fun rejectedExecution(r: Runnable, e: ThreadPoolExecutor) {
        }
    }

    /**
     * A handler for rejected tasks that discards the oldest unhandled
     * request and then retries `execute`, unless the executor
     * is shut down, in which case the task is discarded. This policy is
     * rarely useful in cases where other threads may be waiting for
     * tasks to terminate, or failures must be recorded. Instead consider
     * using a handler of the form:
     * <pre> `new RejectedExecutionHandler() {
     * public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
     * Runnable dropped = e.getQueue().poll();
     * if (dropped instanceof Future<>) {
     * ((Future<>)dropped).cancel(false);
     * // also consider logging the failure
     * }
     * e.execute(r);  // retry
     * }}`</pre>
     */
    class DiscardOldestPolicy
    /**
     * Creates a `DiscardOldestPolicy` for the given executor.
     */
        : RejectedExecutionHandler {
        /**
         * Obtains and ignores the next task that the executor
         * would otherwise execute, if one is immediately available,
         * and then retries execution of task r, unless the executor
         * is shut down, in which case task r is instead discarded.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         */
        override fun rejectedExecution(r: Runnable, e: ThreadPoolExecutor) {
            if (!e.isShutdown) {
                e.queue.poll()
                e.execute(r)
            }
        }
    }

    companion object {
        private const val COUNT_BITS = Int.SIZE_BITS - 3
        private const val COUNT_MASK = (1 shl COUNT_BITS) - 1

        // runState is stored in the high-order bits
        private const val RUNNING = -1 shl COUNT_BITS
        private const val SHUTDOWN = 0 shl COUNT_BITS
        private const val STOP = 1 shl COUNT_BITS
        private const val TIDYING = 2 shl COUNT_BITS
        private const val TERMINATED = 3 shl COUNT_BITS

        // Packing and unpacking ctl
        private fun runStateOf(c: Int): Int {
            return c and COUNT_MASK.inv()
        }

        private fun workerCountOf(c: Int): Int {
            return c and COUNT_MASK
        }

        private fun ctlOf(rs: Int, wc: Int): Int {
            return rs or wc
        }

        /*
     * Bit field accessors that don't require unpacking ctl.
     * These depend on the bit layout and on workerCount being never negative.
     */
        private fun runStateLessThan(c: Int, s: Int): Boolean {
            return c < s
        }

        private fun runStateAtLeast(c: Int, s: Int): Boolean {
            return c >= s
        }

        private fun isRunning(c: Int): Boolean {
            return c < SHUTDOWN
        }

        /**
         * The default rejected execution handler.
         */
        private val defaultHandler: RejectedExecutionHandler = AbortPolicy()

        private const val ONLY_ONE = true
    }
}
