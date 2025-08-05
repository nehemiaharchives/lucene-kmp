package org.gnit.lucenekmp.jdkport

import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.coroutines.resume

/**
 * Basic thread blocking primitives for creating locks and other
 * synchronization classes.
 *
 *
 * This class associates, with each thread that uses it, a permit
 * (in the sense of the [ Semaphore][java.util.concurrent.Semaphore] class). A call to `park` will return immediately
 * if the permit is available, consuming it in the process; otherwise
 * it *may* block.  A call to `unpark` makes the permit
 * available, if it was not already available. (Unlike with Semaphores
 * though, permits do not accumulate. There is at most one.)
 * Reliable usage requires the use of volatile (or atomic) variables
 * to control when to park or unpark.  Orderings of calls to these
 * methods are maintained with respect to volatile variable accesses,
 * but not necessarily non-volatile variable accesses.
 *
 *
 * Methods `park` and `unpark` provide efficient
 * means of blocking and unblocking threads that do not encounter the
 * problems that cause the deprecated methods `Thread.suspend`
 * and `Thread.resume` to be unusable for such purposes: Races
 * between one thread invoking `park` and another thread trying
 * to `unpark` it will preserve liveness, due to the
 * permit. Additionally, `park` will return if the caller's
 * thread was interrupted, and timeout versions are supported. The
 * `park` method may also return at any other time, for "no
 * reason", so in general must be invoked within a loop that rechecks
 * conditions upon return. In this sense `park` serves as an
 * optimization of a "busy wait" that does not waste as much time
 * spinning, but must be paired with an `unpark` to be
 * effective.
 *
 *
 * The three forms of `park` each also support a
 * `blocker` object parameter. This object is recorded while
 * the thread is blocked to permit monitoring and diagnostic tools to
 * identify the reasons that threads are blocked. (Such tools may
 * access blockers using method [.getBlocker].)
 * The use of these forms rather than the original forms without this
 * parameter is strongly encouraged. The normal argument to supply as
 * a `blocker` within a lock implementation is `this`.
 *
 *
 * These methods are designed to be used as tools for creating
 * higher-level synchronization utilities, and are not in themselves
 * useful for most concurrency control applications.  The `park`
 * method is designed for use only in constructions of the form:
 *
 * <pre> `while (!canProceed()) {
 * // ensure request to unpark is visible to other threads
 * ...
 * LockSupport.park(this);
 * }`</pre>
 *
 * where no actions by the thread publishing a request to unpark,
 * prior to the call to `park`, entail locking or blocking.
 * Because only one permit is associated with each thread, any
 * intermediary uses of `park`, including implicitly via class
 * loading, could lead to an unresponsive thread (a "lost unpark").
 *
 *
 * **Sample Usage.** Here is a sketch of a first-in-first-out
 * non-reentrant lock class:
 * <pre> `class FIFOMutex {
 * private final AtomicBoolean locked = new AtomicBoolean(false);
 * private final Queue<Thread> waiters
 * = new ConcurrentLinkedQueue<>();
 *
 * public void lock() {
 * boolean wasInterrupted = false;
 * // publish current thread for unparkers
 * waiters.add(Thread.currentThread());
 *
 * // Block while not first in queue or cannot acquire lock
 * while (waiters.peek() != Thread.currentThread() ||
 * !locked.compareAndSet(false, true)) {
 * LockSupport.park(this);
 * // ignore interrupts while waiting
 * if (Thread.interrupted())
 * wasInterrupted = true;
 * }
 *
 * waiters.remove();
 * // ensure correct interrupt status on return
 * if (wasInterrupted)
 * Thread.currentThread().interrupt();
 * }
 *
 * public void unlock() {
 * locked.set(false);
 * LockSupport.unpark(waiters.peek());
 * }
 *
 * static {
 * // Reduce the risk of "lost unpark" due to classloading
 * Class<?> ensureLoaded = LockSupport.class;
 * }
 * }`</pre>
 *
 * @since 1.5
 */
object LockSupport {

    private val blockerMutex = Mutex()
    private val blockerRegistry = mutableMapOf<Job, Any?>()

    private suspend fun setBlocker(t: /*java.lang.Thread*/Job?, blocker: Any?) {
        /*U.putReferenceOpaque(t, PARKBLOCKER, arg)*/
        blockerMutex.withLock {
            if (t != null) {
                blockerRegistry[t] = blocker
            } else {
                throw NullPointerException("Thread cannot be null")
            }
        }
    }

    /**
     * Sets the object to be returned by invocations of [ ][.getBlocker] for the current thread. This method may
     * be used before invoking the no-argument version of [ ][LockSupport.park] from non-public objects, allowing
     * more helpful diagnostics, or retaining compatibility with
     * previous implementations of blocking methods.  Previous values
     * of the blocker are not automatically restored after blocking.
     * To obtain the effects of `park(b`}, use `setCurrentBlocker(b); park(); setCurrentBlocker(null);`
     *
     * @param blocker the blocker object
     * @since 14
     */
    suspend fun setCurrentBlocker(blocker: Any?) {
        /*U.putReferenceOpaque(java.lang.Thread.currentThread(), PARKBLOCKER, blocker)*/
        val job = currentCoroutineContext()[Job]!!
        setBlocker(job, blocker)
    }

    // registry of parked coroutine continuations
    private val parkRegistry = mutableMapOf<Job, kotlin.coroutines.Continuation<Unit>>()

    private fun resumeParked(job: Job) {
        parkRegistry.remove(job)?.resume(Unit)
    }

    /**
     * Makes available the permit for the given thread, if it
     * was not already available.  If the thread was blocked on
     * `park` then it will unblock.  Otherwise, its next call
     * to `park` is guaranteed not to block. This operation
     * is not guaranteed to have any effect at all if the given
     * thread has not been started.
     *
     * @param thread the thread to unpark, or `null`, in which case
     * this operation has no effect
     */
    fun unpark(thread: /*java.lang.Thread*/ Job?) {
        if (thread != null) {
            // resume any suspended park on this Job
            resumeParked(thread)
        }
    }

    /**
     * Disables the current thread for thread scheduling purposes unless the
     * permit is available.
     *
     *
     * If the permit is available then it is consumed and the call returns
     * immediately; otherwise
     * the current thread becomes disabled for thread scheduling
     * purposes and lies dormant until one of three things happens:
     *
     *
     *  * Some other thread invokes [unpark][.unpark] with the
     * current thread as the target; or
     *
     *  * Some other thread [interrupts][Thread.interrupt]
     * the current thread; or
     *
     *  * The call spuriously (that is, for no reason) returns.
     *
     *
     *
     * This method does *not* report which of these caused the
     * method to return. Callers should re-check the conditions which caused
     * the thread to park in the first place. Callers may also determine,
     * for example, the interrupt status of the thread upon return.
     *
     * @param blocker the synchronization object responsible for this
     * thread parking
     * @since 1.6
     */
    suspend fun park(blocker: Any?) {
        val t: Job = currentCoroutineContext()[Job]!!
        setBlocker(t, blocker)
        try {
            suspendCancellableCoroutine<Unit> { cont ->
                parkRegistry[t] = cont
            }
        } finally {
            setBlocker(t, null)
        }
    }

    /**
     * Disables the current thread for thread scheduling purposes, for up to
     * the specified waiting time, unless the permit is available.
     *
     *
     * If the specified waiting time is zero or negative, the
     * method does nothing. Otherwise, if the permit is available then
     * it is consumed and the call returns immediately; otherwise the
     * current thread becomes disabled for thread scheduling purposes
     * and lies dormant until one of four things happens:
     *
     *
     *  * Some other thread invokes [unpark][.unpark] with the
     * current thread as the target; or
     *
     *  * Some other thread [interrupts][Thread.interrupt]
     * the current thread; or
     *
     *  * The specified waiting time elapses; or
     *
     *  * The call spuriously (that is, for no reason) returns.
     *
     *
     *
     * This method does *not* report which of these caused the
     * method to return. Callers should re-check the conditions which caused
     * the thread to park in the first place. Callers may also determine,
     * for example, the interrupt status of the thread, or the elapsed time
     * upon return.
     *
     * @param blocker the synchronization object responsible for this
     * thread parking
     * @param nanos the maximum number of nanoseconds to wait
     * @since 1.6
     */
    suspend fun parkNanos(blocker: Any?, nanos: Long) {
        if (nanos > 0) {
            val t: Job = currentCoroutineContext()[Job]!!
            setBlocker(t, blocker)
            try {
                delay(nanos / 1_000_000) // Convert nanoseconds to milliseconds for delay
            } finally {
                setBlocker(t, null)
            }
        }
    }

    /**
     * Disables the current thread for thread scheduling purposes, until
     * the specified deadline, unless the permit is available.
     *
     *
     * If the permit is available then it is consumed and the call
     * returns immediately; otherwise the current thread becomes disabled
     * for thread scheduling purposes and lies dormant until one of four
     * things happens:
     *
     *
     *  * Some other thread invokes [unpark][.unpark] with the
     * current thread as the target; or
     *
     *  * Some other thread [interrupts][Thread.interrupt] the
     * current thread; or
     *
     *  * The specified deadline passes; or
     *
     *  * The call spuriously (that is, for no reason) returns.
     *
     *
     *
     * This method does *not* report which of these caused the
     * method to return. Callers should re-check the conditions which caused
     * the thread to park in the first place. Callers may also determine,
     * for example, the interrupt status of the thread, or the current time
     * upon return.
     *
     * @param blocker the synchronization object responsible for this
     * thread parking
     * @param deadline the absolute time, in milliseconds from the Epoch,
     * to wait until
     * @since 1.6
     */
    @OptIn(ExperimentalTime::class)
    suspend fun parkUntil(blocker: Any?, deadline: /*Long*/ Instant) {
        val t: /*java.lang.Thread*/ Job = /*java.lang.Thread.currentThread()*/ currentCoroutineContext()[Job]!!
        setBlocker(t, blocker)
        try {
            parkUntil(deadline)
        } finally {
            setBlocker(t, null)
        }
    }

    /**
     * Returns the blocker object supplied to the most recent
     * invocation of a park method that has not yet unblocked, or null
     * if not blocked.  The value returned is just a momentary
     * snapshot -- the thread may have since unblocked or blocked on a
     * different blocker object.
     *
     * @param t the thread
     * @return the blocker
     * @throws NullPointerException if argument is null
     * @since 1.6
     */
    /*fun getBlocker(t: *//*java.lang.Thread*//* Job): Any? {
        //if (t == null) throw java.lang.NullPointerException()
        return U.getReferenceOpaque(t, PARKBLOCKER)
    }*/

    /**
     * Disables the current thread for thread scheduling purposes unless the
     * permit is available.
     *
     *
     * If the permit is available then it is consumed and the call
     * returns immediately; otherwise the current thread becomes disabled
     * for thread scheduling purposes and lies dormant until one of three
     * things happens:
     *
     *
     *
     *  * Some other thread invokes [unpark][.unpark] with the
     * current thread as the target; or
     *
     *  * Some other thread [interrupts][Thread.interrupt]
     * the current thread; or
     *
     *  * The call spuriously (that is, for no reason) returns.
     *
     *
     *
     * This method does *not* report which of these caused the
     * method to return. Callers should re-check the conditions which caused
     * the thread to park in the first place. Callers may also determine,
     * for example, the interrupt status of the thread upon return.
     */
    suspend fun park() {
        /*if (java.lang.Thread.currentThread().isVirtual()) {
            JLA.parkVirtualThread()
        } else {
            U.park(false, 0L)
        }*/

        park(null)
    }

    /**
     * Disables the current thread for thread scheduling purposes, for up to
     * the specified waiting time, unless the permit is available.
     *
     *
     * If the specified waiting time is zero or negative, the
     * method does nothing. Otherwise, if the permit is available then
     * it is consumed and the call returns immediately; otherwise the
     * current thread becomes disabled for thread scheduling purposes
     * and lies dormant until one of four things happens:
     *
     *
     *  * Some other thread invokes [unpark][.unpark] with the
     * current thread as the target; or
     *
     *  * Some other thread [interrupts][Thread.interrupt]
     * the current thread; or
     *
     *  * The specified waiting time elapses; or
     *
     *  * The call spuriously (that is, for no reason) returns.
     *
     *
     *
     * This method does *not* report which of these caused the
     * method to return. Callers should re-check the conditions which caused
     * the thread to park in the first place. Callers may also determine,
     * for example, the interrupt status of the thread, or the elapsed time
     * upon return.
     *
     * @param nanos the maximum number of nanoseconds to wait
     */
    suspend fun parkNanos(nanos: Long) {
        if (nanos > 0) {
            /*if (java.lang.Thread.currentThread().isVirtual()) {
                JLA.parkVirtualThread(nanos)
            } else {
                U.park(false, nanos)
            }*/

            delay(nanos / 1_000_000) // Convert nanoseconds to milliseconds for delay
        }
    }

    /**
     * Disables the current thread for thread scheduling purposes, until
     * the specified deadline, unless the permit is available.
     *
     *
     * If the permit is available then it is consumed and the call
     * returns immediately; otherwise the current thread becomes disabled
     * for thread scheduling purposes and lies dormant until one of four
     * things happens:
     *
     *
     *  * Some other thread invokes [unpark][.unpark] with the
     * current thread as the target; or
     *
     *  * Some other thread [interrupts][Thread.interrupt]
     * the current thread; or
     *
     *  * The specified deadline passes; or
     *
     *  * The call spuriously (that is, for no reason) returns.
     *
     *
     *
     * This method does *not* report which of these caused the
     * method to return. Callers should re-check the conditions which caused
     * the thread to park in the first place. Callers may also determine,
     * for example, the interrupt status of the thread, or the current time
     * upon return.
     *
     * @param deadline the absolute time, in milliseconds from the Epoch,
     * to wait until
     */
    @OptIn(ExperimentalTime::class)
    suspend fun parkUntil(deadline: /*Long*/Instant) {
        /*if (java.lang.Thread.currentThread().isVirtual()) {
            val millis: Long = deadline - java.lang.System.currentTimeMillis()
            JLA.parkVirtualThread(java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(millis))
        } else {
            U.park(true, deadline)
        }*/

        delay((deadline - Clock.System.now()).inWholeMilliseconds) // Convert to milliseconds for delay
    }

    /**
     * Returns the thread id for the given thread.
     */
    /*fun getThreadId(thread: *//*java.lang.Thread*//*Job): Long {
        return thread.threadId()
    }*/

    // following are not available in KMP, need to replace with KMP equivalents
    // Hotspot implementation via intrinsics API
    /*private val U: jdk.internal.misc.Unsafe = jdk.internal.misc.Unsafe.getUnsafe()
    private val PARKBLOCKER
            : Long = U.objectFieldOffset(java.lang.Thread::class, "parkBlocker")

    private val JLA: jdk.internal.access.JavaLangAccess = SharedSecrets.getJavaLangAccess()*/
}
