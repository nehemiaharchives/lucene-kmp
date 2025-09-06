package org.gnit.lucenekmp.jdkport

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Function interface to replace java.util.concurrent.Executor
 */
@Ported(from = "java.util.concurrent.Executor")
fun interface Executor {
    fun execute(command: Runnable)
}

/**
 * Function interface to replace java.util.concurrent.Callable
 */
fun interface Callable<T> {
    fun call(): T
}

/**
 * Interface to replace java.util.concurrent.Future
 */
@Ported(from = "java.util.concurrent.Future")
interface Future<T> {
    /** Attempts to cancel execution of this task.
     * @param mayInterruptIfRunning (ignored in this implementation; tasks are cooperatively cancelled)
     * @return `true` if cancellation was successful (task will not produce a result), `false` if it was already completed. */
    fun cancel(mayInterruptIfRunning: Boolean): Boolean

    /** Returns `true` if the task completed (either successfully, with an exception, or was cancelled). */
    fun isDone(): Boolean

    /** Returns `true` if the task was cancelled before completion. */
    fun isCancelled(): Boolean

    /** Waits if necessary for the computation to complete, then returns the result.
     * @throws CancellationException if the computation was cancelled.
     * @throws Exception if the computation threw an exception. */
    @Throws(Exception::class)
    suspend fun get(): T
}


/**
 * Interface to replace java.util.concurrent.RunnableFuture
 */
interface RunnableFuture<T> : Future<T>, Runnable


/**
 * Exception to replace java.util.concurrent.ExecutionException
 */
class ExecutionException(cause: Throwable? = null) : Exception(cause)

/**
 * A cancellable asynchronous computation. This class provides a base
 * implementation of [Future], with methods to start and cancel
 * a computation, query to see if the computation is complete, and
 * retrieve the result of the computation. The result can only be
 * retrieved when the computation has completed; the `get`
 * methods will block if the computation has not yet completed. Once
 * the computation has completed, the computation cannot be restarted
 * or cancelled (unless the computation is invoked using
 * [runAndReset]).
 *
 * A [FutureTask] can be used to wrap a [CommonCallable] or
 * [Runnable] object. Because [FutureTask] implements
 * [Runnable], a [FutureTask] can be submitted to an
 * [CoroutineScope] for execution.
 *
 * In addition to serving as a standalone class, this class provides
 * `protected` functionality that may be useful when creating
 * customized task classes.
 *
 * @param <V> The result type returned by this FutureTask's `get` methods
 */
open class FutureTask<V> : RunnableFuture<V> {
    /**
     * The run state of this task, initially NEW. The run state
     * transitions to a terminal state only in methods set,
     * setException, and cancel. During completion, state may take on
     * transient values of COMPLETING (while outcome is being set) or
     * INTERRUPTING (only while interrupting the runner to satisfy a
     * cancel(true)). Transitions from these intermediate to final
     * states use cheaper ordered/lazy writes because values are unique
     * and cannot be further modified.
     *
     * Possible state transitions:
     * NEW -> COMPLETING -> NORMAL
     * NEW -> COMPLETING -> EXCEPTIONAL
     * NEW -> CANCELLED
     * NEW -> INTERRUPTING -> INTERRUPTED
     */
    @OptIn(ExperimentalAtomicApi::class)
    private val state = AtomicInt(NEW)

    @OptIn(ExperimentalAtomicApi::class)
    private val outcome = AtomicReference<Any?>(null) // null indicates not yet set

    @OptIn(ExperimentalAtomicApi::class)
    private val runner = AtomicReference<Job?>(null)
    private val mutex = Mutex()
    private var continuation: CancellableContinuation<V>? = null

    private companion object {
        private const val NEW = 0
        private const val COMPLETING = 1
        private const val NORMAL = 2
        private const val EXCEPTIONAL = 3
        private const val CANCELLED = 4
        private const val INTERRUPTING = 5
        private const val INTERRUPTED = 6
    }

    /** The underlying callable; nulled out after running */
    private var callable: Callable<V>? = null

    // Logging support (platform agnostic)
    private val logger = io.github.oshai.kotlinlogging.KotlinLogging.logger {}

    /**
     * Returns result or throws exception for completed task.
     *
     * @param s completed state value
     */
    @OptIn(ExperimentalAtomicApi::class)
    @Suppress("UNCHECKED_CAST")
    private fun report(s: Int): V {
        val x = outcome.load()
        if (s == NORMAL)
            return x as V
        if (s >= CANCELLED)
            throw CancellationException("Task was cancelled") as Throwable
        throw ExecutionException(x as Throwable)
    }

    /**
     * Creates a `FutureTask` that will, upon running, execute the
     * given `Callable`.
     *
     * @param callable the callable task
     * @throws NullPointerException if the callable is null
     */
    constructor(callable: Callable<V>) {
        this.callable = callable
        /*if (callable == null)
            throw NullPointerException()*/
    }

    /**
     * Creates a `FutureTask` that will, upon running, execute the
     * given `Runnable`, and arrange that `get` will return the
     * given result on successful completion.
     *
     * @param runnable the runnable task
     * @param result the result to return on successful completion. If
     * you don't need a particular result, consider using
     * constructions of the form:
     * `Future<> f = new FutureTask<Void>(runnable, null)`
     * @throws NullPointerException if the runnable is null
     */
    constructor(runnable: Runnable, result: V?) : this(Executors.callable(runnable, result))

    @OptIn(ExperimentalAtomicApi::class)
    override fun isCancelled(): Boolean {
        return state.load() >= CANCELLED
    }

    @OptIn(ExperimentalAtomicApi::class)
    override fun isDone(): Boolean {
        return state.load() != NEW
    }

    @OptIn(ExperimentalAtomicApi::class)
    override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
        if (!(state.compareAndSet(NEW, if (mayInterruptIfRunning) INTERRUPTING else CANCELLED)))
            return false

        try {
            if (mayInterruptIfRunning) {
                runner.load()!!.cancel()
                state.store(INTERRUPTED)
            }
        } finally {
            finishCompletion()
        }
        return true
    }

    /**
     * @throws CancellationException {@inheritDoc}
     */
    @OptIn(ExperimentalAtomicApi::class)
    override suspend fun get(): V {
        var s = state.load()
        if (s <= COMPLETING) {
            if (s == NEW) {
                mutex.withLock {
                    s = state.load()
                    if (s == NEW) {
                        return suspendCancellableCoroutine { cont ->
                            this.continuation = cont
                            cont.invokeOnCancellation {
                                if (state.compareAndSet(NEW, CANCELLED)) {
                                    finishCompletion()
                                }
                            }
                        }
                    }
                }
                s = state.load()
            }
            if (s <= COMPLETING) {
                return awaitDone(false, 0.nanoseconds)
            }
        }
        return report(s)
    }

    /**
     * @throws CancellationException {@inheritDoc}
     */
    @OptIn(ExperimentalAtomicApi::class)
    suspend fun get(timeout: Long, unit: TimeUnit): V {
        if (unit == null)
            throw NullPointerException()
        var s = state.load()
        if (s <= COMPLETING) {
            if (s == NEW) {
                mutex.withLock {
                    s = state.load()
                    if (s == NEW) {
                        return suspendCancellableCoroutine { cont ->
                            this.continuation = cont
                            cont.invokeOnCancellation {
                                if (state.compareAndSet(NEW, CANCELLED)) {
                                    finishCompletion()
                                }
                            }
                        }
                    }
                }
                s = state.load()
            }
            if (s <= COMPLETING) {
                val duration = unit.toNanos(timeout).nanoseconds
                val result = awaitDone(true, duration)
                if (state.load() <= COMPLETING) {
                    throw TimeoutException()
                }
                return result
            }
        }
        return report(s)
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun resultNow(): V {
        return when (state.load()) {
            NORMAL -> outcome.load() as V
            EXCEPTIONAL -> throw IllegalStateException("Task completed with exception")
            CANCELLED, INTERRUPTING, INTERRUPTED -> throw IllegalStateException("Task was cancelled")
            else -> throw IllegalStateException("Task has not completed")
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun exceptionNow(): Throwable {
        return when (state.load()) {
            NORMAL -> throw IllegalStateException("Task completed with a result")
            EXCEPTIONAL -> outcome.load() as Throwable
            CANCELLED, INTERRUPTING, INTERRUPTED -> throw IllegalStateException("Task was cancelled")
            else -> throw IllegalStateException("Task has not completed")
        }
    }

    enum class State {
        RUNNING,
        SUCCESS,
        FAILED,
        CANCELLED
    }

    @OptIn(ExperimentalAtomicApi::class)
    suspend fun state(): State {
        var s = state.load()
        while (s == COMPLETING) {
            // waiting for transition to NORMAL or EXCEPTIONAL
            yield() // Use coroutine yield for platform-agnostic waiting
            s = state.load()
        }
        return when (s) {
            NORMAL -> State.SUCCESS
            EXCEPTIONAL -> State.FAILED
            CANCELLED, INTERRUPTING, INTERRUPTED -> State.CANCELLED
            else -> State.RUNNING
        }
    }

    /**
     * Protected method invoked when this task transitions to state
     * `isDone` (whether normally or via cancellation). The
     * default implementation does nothing. Subclasses may override
     * this method to invoke completion callbacks or perform
     * bookkeeping. Note that you can query status inside the
     * implementation of this method to determine whether this task
     * has been cancelled.
     */
    protected open fun done() {}

    /**
     * Sets the result of this future to the given value unless
     * this future has already been set or has been cancelled.
     *
     * This method is invoked internally by the [run] method
     * upon successful completion of the computation.
     *
     * @param v the value
     */
    @OptIn(ExperimentalAtomicApi::class)
    protected fun set(v: V?) {
        if (state.compareAndSet(NEW, COMPLETING)) {
            outcome.store(v)
            state.store(NORMAL) // final state
            finishCompletion()
        }
    }

    /**
     * Causes this future to report an [ExecutionException]
     * with the given throwable as its cause, unless this future has
     * already been set or has been cancelled.
     *
     * This method is invoked internally by the [run] method
     * upon failure of the computation.
     *
     * @param t the cause of failure
     */
    @OptIn(ExperimentalAtomicApi::class)
    protected open fun setException(t: Throwable) {
        if (state.compareAndSet(NEW, COMPLETING)) {
            outcome.store(t)
            state.store(EXCEPTIONAL) // final state
            finishCompletion()
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    override fun run() {
    println("[DBG][FutureTask.run] enter state=${state.load()} hasRunner=${runner.load()!=null} this=${this.hashCode()}")
    logger.debug { "[FutureTask.run] enter state=${state.load()} hasRunner=${runner.load()!=null} hash=${this.hashCode()}" }
        if (state.load() != NEW || !runner.compareAndSet(null, Job())) {
        println("[DBG][FutureTask.run] abort state=${state.load()} runnerWasSet=${runner.load()!=null} hash=${this.hashCode()}")
        logger.debug { "[FutureTask.run] abort: state=${state.load()} runnerWasSet=${runner.load()!=null} hash=${this.hashCode()}" }
            return
        }
        val currentRunner = runner.load()!!
        try {
            val c = callable
            if (c != null && state.load() == NEW) {
                var result: V? = null
                var ran = false
                try {
            println("[DBG][FutureTask.run] invoking callable hash=${this.hashCode()}")
            logger.debug { "[FutureTask.run] invoking callable hash=${this.hashCode()}" }
                    result = c.call()
                    ran = true
                } catch (ex: Throwable) {
            println("[DBG][FutureTask.run] exception ${ex::class.simpleName} msg=${ex.message} hash=${this.hashCode()}")
            logger.debug { "[FutureTask.run] callable threw ${ex::class.simpleName}: ${ex.message} hash=${this.hashCode()}" }
                    setException(ex)
                }
                if (ran) {
            println("[DBG][FutureTask.run] callable completed setting result hash=${this.hashCode()}")
            logger.debug { "[FutureTask.run] callable completed, setting result hash=${this.hashCode()}" }
                    set(result as V)
                }
            }
        } finally {
            // runner must be non-null until state is settled to
            // prevent concurrent calls to run()
            runner.store(null)
            // state must be re-read after nulling runner to prevent
            // leaked interrupts
            val s = state.load()
            if (s >= INTERRUPTING) {
                handlePossibleCancellationInterrupt(s)
            }
            println("[DBG][FutureTask.run] exit finalState=$s hash=${this.hashCode()}")
            logger.debug { "[FutureTask.run] exit finalState=$s hash=${this.hashCode()}" }
        }
    }

    /**
     * Executes the computation without setting its result, and then
     * resets this future to initial state, failing to do so if the
     * computation encounters an exception or is cancelled. This is
     * designed for use with tasks that intrinsically execute more
     * than once.
     *
     * @return `true` if successfully run and reset
     */
    @OptIn(ExperimentalAtomicApi::class)
    protected open fun runAndReset(): Boolean {
        if (state.load() != NEW || !runner.compareAndSet(null, Job())) {
            return false
        }
        var ran = false
        var s = state.load()
        try {
            val c = callable
            if (c != null && s == NEW) {
                try {
                    c.call() // don't set result
                    ran = true
                } catch (ex: Throwable) {
                    setException(ex)
                }
            }
        } finally {
            // runner must be non-null until state is settled to
            // prevent concurrent calls to run()
            runner.store(null)
            // state must be re-read after nulling runner to prevent
            // leaked interrupts
            s = state.load()
            if (s >= INTERRUPTING) {
                handlePossibleCancellationInterrupt(s)
            }
        }
        return ran && state.load() == NEW
    }

    /**
     * Ensures that any interrupt from a possible cancel(true) is only
     * delivered to a task while in run or runAndReset.
     */
    private fun handlePossibleCancellationInterrupt(s: Int) {
        // In a coroutine-based world, we rely on Job cancellation.
        // There isn't a direct equivalent of Thread.interrupt() in common code.
        // We've already cancelled the Job if mayInterruptIfRunning was true.
    }

    /**
     * Removes and signals all waiting threads, invokes done(), and
     * nulls out callable.
     */
    @OptIn(ExperimentalAtomicApi::class)
    private fun finishCompletion() {
        // assert state.load() > COMPLETING;
        continuation.let { cont ->
            continuation = null
            val s = state.load()
            if(cont != null) {
                if (s >= CANCELLED) {
                    cont.cancel()
                } else {
                    cont.resume(report(s))
                }
            }
        }
        done()
        callable = null // to reduce footprint
    }

    /**
     * Awaits completion or aborts on interrupt or timeout.
     *
     * @param timed true if use timed waits
     * @param duration time to wait, if timed
     * @return result upon completion or at timeout
     */
    @OptIn(ExperimentalAtomicApi::class)
    private suspend fun awaitDone(timed: Boolean, duration: Duration): V {
        val deadline = if (timed) System.nanoTime() + duration.inWholeNanoseconds else Long.MAX_VALUE
        while (true) {
            val s = state.load()
            if (s > COMPLETING) return report(s)
            if (timed && System.nanoTime() >= deadline) {
                if (state.compareAndSet(NEW, CANCELLED)) {
                    finishCompletion()
                }
                throw TimeoutException()
            }
            if (!currentCoroutineContext().isActive) {
                if (state.compareAndSet(NEW, CANCELLED)) {
                    finishCompletion()
                }
                throw CancellationException()
            }
            yield()
        }
    }

    /**
     * Returns a string representation of this FutureTask.
     *
     * @return a string representation of this FutureTask
     */
    @OptIn(ExperimentalAtomicApi::class)
    override fun toString(): String {
        val status: String = when (state.load()) {
            NORMAL -> "[Completed normally]"
            EXCEPTIONAL -> "[Completed exceptionally: ${outcome.load()}]"
            CANCELLED, INTERRUPTING, INTERRUPTED -> "[Cancelled]"
            else -> {
                val callableLocal = callable
                if (callableLocal == null) {
                    "[Not completed]"
                } else {
                    "[Not completed, task = $callableLocal]"
                }
            }
        }
        return "${super.toString()}$status"
    }
}

// Platform-agnostic equivalent of Executors.callable
object Executors {
    fun <T> callable(runnable: Runnable, result: T?): Callable<T> {
        return Callable {
            runnable.run()
            result as T
        }
    }

    fun <T> callable(runnable: Runnable): Callable<T> {
        return Callable {
            runnable.run()
            null as T // Return null if no result is expected
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun defaultThreadFactory():ThreadFactory {
        return object : ThreadFactory {
            private val count = AtomicInteger(1)
            override fun newThread(r: Runnable): Job {
                val threadName = "pool-thread-${count.fetchAndIncrement()}"
                println("[DBG][ThreadFactory] creating new coroutine thread name=$threadName runnableClass=${r::class.simpleName}")
                return GlobalScope.launch(CoroutineName(threadName)) {
                    println("[DBG][ThreadFactory] launched coroutine name=$threadName starting runnableClass=${r::class.simpleName}")
                    when (r) {
                        is CoroutineRunnable -> r.runSuspending()
                        else -> r.run()
                    }
                    println("[DBG][ThreadFactory] coroutine name=$threadName finished runnableClass=${r::class.simpleName}")
                }
            }
        }
    }
}
