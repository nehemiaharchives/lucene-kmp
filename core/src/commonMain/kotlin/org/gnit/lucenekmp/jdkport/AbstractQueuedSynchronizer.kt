package org.gnit.lucenekmp.jdkport

import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.jvm.Transient
import kotlin.time.Clock
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant


/**
 * Provides a framework for implementing blocking locks and related
 * synchronizers (semaphores, events, etc) that rely on
 * first-in-first-out (FIFO) wait queues.  This class is designed to
 * be a useful basis for most kinds of synchronizers that rely on a
 * single atomic `int` value to represent state. Subclasses
 * must define the protected methods that change this state, and which
 * define what that state means in terms of this object being acquired
 * or released.  Given these, the other methods in this class carry
 * out all queuing and blocking mechanics. Subclasses can maintain
 * other state fields, but only the atomically updated `int`
 * value manipulated using methods [.getState], [ ][.setState] and [.compareAndSetState] is tracked with respect
 * to synchronization.
 *
 *
 * Subclasses should be defined as non-public internal helper
 * classes that are used to implement the synchronization properties
 * of their enclosing class.  Class
 * `AbstractQueuedSynchronizer` does not implement any
 * synchronization interface.  Instead it defines methods such as
 * [.acquireInterruptibly] that can be invoked as
 * appropriate by concrete locks and related synchronizers to
 * implement their public methods.
 *
 *
 * This class supports either or both a default *exclusive*
 * mode and a *shared* mode. When acquired in exclusive mode,
 * attempted acquires by other threads cannot succeed. Shared mode
 * acquires by multiple threads may (but need not) succeed. This class
 * does not &quot;understand&quot; these differences except in the
 * mechanical sense that when a shared mode acquire succeeds, the next
 * waiting thread (if one exists) must also determine whether it can
 * acquire as well. Threads waiting in the different modes share the
 * same FIFO queue. Usually, implementation subclasses support only
 * one of these modes, but both can come into play for example in a
 * [ReadWriteLock]. Subclasses that support only exclusive or
 * only shared modes need not define the methods supporting the unused mode.
 *
 *
 * This class defines a nested [ConditionObject] class that
 * can be used as a [Condition] implementation by subclasses
 * supporting exclusive mode for which method [ ][.isHeldExclusively] reports whether synchronization is exclusively
 * held with respect to the current thread, method [.release]
 * invoked with the current [.getState] value fully releases
 * this object, and [.acquire], given this saved state value,
 * eventually restores this object to its previous acquired state.  No
 * `AbstractQueuedSynchronizer` method otherwise creates such a
 * condition, so if this constraint cannot be met, do not use it.  The
 * behavior of [ConditionObject] depends of course on the
 * semantics of its synchronizer implementation.
 *
 *
 * This class provides inspection, instrumentation, and monitoring
 * methods for the internal queue, as well as similar methods for
 * condition objects. These can be exported as desired into classes
 * using an `AbstractQueuedSynchronizer` for their
 * synchronization mechanics.
 *
 *
 * Serialization of this class stores only the underlying atomic
 * integer maintaining state, so deserialized objects have empty
 * thread queues. Typical subclasses requiring serializability will
 * define a `readObject` method that restores this to a known
 * initial state upon deserialization.
 *
 * <h2>Usage</h2>
 *
 *
 * To use this class as the basis of a synchronizer, redefine the
 * following methods, as applicable, by inspecting and/or modifying
 * the synchronization state using [.getState], [ ][.setState] and/or [.compareAndSetState]:
 *
 *
 *  * [.tryAcquire]
 *  * [.tryRelease]
 *  * [.tryAcquireShared]
 *  * [.tryReleaseShared]
 *  * [.isHeldExclusively]
 *
 *
 * Each of these methods by default throws [ ].  Implementations of these methods
 * must be internally thread-safe, and should in general be short and
 * not block. Defining these methods is the *only* supported
 * means of using this class. All other methods are declared
 * `final` because they cannot be independently varied.
 *
 *
 * You may also find the inherited methods from [ ] useful to keep track of the thread
 * owning an exclusive synchronizer.  You are encouraged to use them
 * -- this enables monitoring and diagnostic tools to assist users in
 * determining which threads hold locks.
 *
 *
 * Even though this class is based on an internal FIFO queue, it
 * does not automatically enforce FIFO acquisition policies.  The core
 * of exclusive synchronization takes the form:
 *
 * <pre>
 * *Acquire:*
 * while (!tryAcquire(arg)) {
 * *enqueue thread if it is not already queued*;
 * *possibly block current thread*;
 * }
 *
 * *Release:*
 * if (tryRelease(arg))
 * *unblock the first queued thread*;
</pre> *
 *
 * (Shared mode is similar but may involve cascading signals.)
 *
 *
 * Because checks in acquire are invoked before
 * enqueuing, a newly acquiring thread may *barge* ahead of
 * others that are blocked and queued.  However, you can, if desired,
 * define `tryAcquire` and/or `tryAcquireShared` to
 * disable barging by internally invoking one or more of the inspection
 * methods, thereby providing a *fair* FIFO acquisition order.
 * In particular, most fair synchronizers can define `tryAcquire`
 * to return `false` if [.hasQueuedPredecessors] (a method
 * specifically designed to be used by fair synchronizers) returns
 * `true`.  Other variations are possible.
 *
 *
 * Throughput and scalability are generally highest for the
 * default barging (also known as *greedy*,
 * *renouncement*, and *convoy-avoidance*) strategy.
 * While this is not guaranteed to be fair or starvation-free, earlier
 * queued threads are allowed to recontend before later queued
 * threads, and each recontention has an unbiased chance to succeed
 * against incoming threads.  Also, while acquires do not
 * &quot;spin&quot; in the usual sense, they may perform multiple
 * invocations of `tryAcquire` interspersed with other
 * computations before blocking.  This gives most of the benefits of
 * spins when exclusive synchronization is only briefly held, without
 * most of the liabilities when it isn't. If so desired, you can
 * augment this by preceding calls to acquire methods with
 * "fast-path" checks, possibly prechecking [.hasContended]
 * and/or [.hasQueuedThreads] to only do so if the synchronizer
 * is likely not to be contended.
 *
 *
 * This class provides an efficient and scalable basis for
 * synchronization in part by specializing its range of use to
 * synchronizers that can rely on `int` state, acquire, and
 * release parameters, and an internal FIFO wait queue. When this does
 * not suffice, you can build synchronizers from a lower level using
 * [atomic][java.util.concurrent.atomic] classes, your own custom
 * [java.util.Queue] classes, and [LockSupport] blocking
 * support.
 *
 * <h2>Usage Examples</h2>
 *
 *
 * Here is a non-reentrant mutual exclusion lock class that uses
 * the value zero to represent the unlocked state, and one to
 * represent the locked state. While a non-reentrant lock
 * does not strictly require recording of the current owner
 * thread, this class does so anyway to make usage easier to monitor.
 * It also supports conditions and exposes some instrumentation methods:
 *
 * <pre> `class Mutex implements Lock, java.io.Serializable {
 *
 * // Our internal helper class
 * private static class Sync extends AbstractQueuedSynchronizer {
 * // Acquires the lock if state is zero
 * public boolean tryAcquire(int acquires) {
 * assert acquires == 1; // Otherwise unused
 * if (compareAndSetState(0, 1)) {
 * setExclusiveOwnerThread(Thread.currentThread());
 * return true;
 * }
 * return false;
 * }
 *
 * // Releases the lock by setting state to zero
 * protected boolean tryRelease(int releases) {
 * assert releases == 1; // Otherwise unused
 * if (!isHeldExclusively())
 * throw new IllegalMonitorStateException();
 * setExclusiveOwnerThread(null);
 * setState(0);
 * return true;
 * }
 *
 * // Reports whether in locked state
 * public boolean isLocked() {
 * return getState() != 0;
 * }
 *
 * public boolean isHeldExclusively() {
 * // a data race, but safe due to out-of-thin-air guarantees
 * return getExclusiveOwnerThread() == Thread.currentThread();
 * }
 *
 * // Provides a Condition
 * public Condition newCondition() {
 * return new ConditionObject();
 * }
 *
 * // Deserializes properly
 * private void readObject(ObjectInputStream s)
 * throws IOException, ClassNotFoundException {
 * s.defaultReadObject();
 * setState(0); // reset to unlocked state
 * }
 * }
 *
 * // The sync object does all the hard work. We just forward to it.
 * private final Sync sync = new Sync();
 *
 * public void lock()              { sync.acquire(1); }
 * public boolean tryLock()        { return sync.tryAcquire(1); }
 * public void unlock()            { sync.release(1); }
 * public Condition newCondition() { return sync.newCondition(); }
 * public boolean isLocked()       { return sync.isLocked(); }
 * public boolean isHeldByCurrentThread() {
 * return sync.isHeldExclusively();
 * }
 * public boolean hasQueuedThreads() {
 * return sync.hasQueuedThreads();
 * }
 * public void lockInterruptibly() throws InterruptedException {
 * sync.acquireInterruptibly(1);
 * }
 * public boolean tryLock(long timeout, TimeUnit unit)
 * throws InterruptedException {
 * return sync.tryAcquireNanos(1, unit.toNanos(timeout));
 * }
 * }`</pre>
 *
 *
 * Here is a latch class that is like a
 * [CountDownLatch][java.util.concurrent.CountDownLatch]
 * except that it only requires a single `signal` to
 * fire. Because a latch is non-exclusive, it uses the `shared`
 * acquire and release methods.
 *
 * <pre> `class BooleanLatch {
 *
 * private static class Sync extends AbstractQueuedSynchronizer {
 * boolean isSignalled() { return getState() != 0; }
 *
 * protected int tryAcquireShared(int ignore) {
 * return isSignalled() ? 1 : -1;
 * }
 *
 * protected boolean tryReleaseShared(int ignore) {
 * setState(1);
 * return true;
 * }
 * }
 *
 * private final Sync sync = new Sync();
 * public boolean isSignalled() { return sync.isSignalled(); }
 * public void signal()         { sync.releaseShared(1); }
 * public void await() throws InterruptedException {
 * sync.acquireSharedInterruptibly(1);
 * }
 * }`</pre>
 *
 * @since 1.5
 * @author Doug Lea
 */
abstract class AbstractQueuedSynchronizer

/**
 * Creates a new `AbstractQueuedSynchronizer` instance
 * with initial synchronization state of zero.
 */
protected constructor() : AbstractOwnableSynchronizer()/*, java.io.Serializable*/ {
    /** CLH Nodes  */
    abstract class Node {
        @OptIn(ExperimentalAtomicApi::class)
        private val STATUS = AtomicInt(0)

        @OptIn(ExperimentalAtomicApi::class)
        private val NEXT = AtomicReference<Node?>(null)

        @OptIn(ExperimentalAtomicApi::class)
        private val PREV = AtomicReference<Node?>(null)

        @Volatile
        var prev: Node? = null // initially attached via casTail

        @Volatile
        var next: Node? = null // visibly nonnull when signallable
        var waiter: /*java.lang.Thread*/Job? = null // visibly nonnull when enqueued

        @Volatile
        var status: Int = 0 // written by owner, atomic bit ops by others

        // methods for atomic operations
        @OptIn(ExperimentalAtomicApi::class)
        fun casPrev(c: Node?, v: Node?): Boolean {  // for cleanQueue
            return PREV.compareAndSet(c, v)
        }

        @OptIn(ExperimentalAtomicApi::class)
        fun casNext(c: Node?, v: Node?): Boolean {  // for cleanQueue
            return NEXT.compareAndSet(c, v)
        }

        @OptIn(ExperimentalAtomicApi::class)
        fun getAndUnsetStatus(v: Int): Int {     // for signalling
            val toReturn = STATUS.load()
            STATUS.store(v.inv())
            return toReturn
        }

        @OptIn(ExperimentalAtomicApi::class)
        fun setPrevRelaxed(p: Node?) {      // for off-queue assignment
            PREV.store(p)
        }

        @OptIn(ExperimentalAtomicApi::class)
        fun setStatusRelaxed(s: Int) {     // for off-queue assignment
            STATUS.store(s)
        }

        @OptIn(ExperimentalAtomicApi::class)
        fun clearStatus() {               // for reducing unneeded signals
            STATUS.store(0)
        }

    }

    // Concrete classes tagged by type
    internal class ExclusiveNode : Node()
    internal class SharedNode : Node()

    class ConditionNode : Node(), ForkJoinPool.ManagedBlocker {
        var nextWaiter: ConditionNode? = null // link to next waiting node

        override val isReleasable: Boolean
            /**
             * Allows Conditions to be used in ForkJoinPools without
             * risking fixed pool exhaustion. This is usable only for
             * untimed Condition waits, not timed versions.
             */
            get() = status <= 1 || runBlocking{ currentCoroutineContext()[Job]!!.isCancelled }

        override fun block(): Boolean {
            while (!this.isReleasable) {
                runBlocking{ LockSupport.park() }
            }
            return true
        }
    }

    // Unsafe
    /*private val U: jdk.internal.misc.Unsafe = jdk.internal.misc.Unsafe.getUnsafe()*/
    @OptIn(ExperimentalAtomicApi::class)
    private val STATE: AtomicInt = AtomicInt(0) // state field
            /*: Long = U.objectFieldOffset(AbstractQueuedSynchronizer::class, "state")*/
    @OptIn(ExperimentalAtomicApi::class)
    private val HEAD: AtomicReference<Node?> = AtomicReference(null) // head field
            /*: Long = U.objectFieldOffset(AbstractQueuedSynchronizer::class, "head")*/
    @OptIn(ExperimentalAtomicApi::class)
    private val TAIL: AtomicReference<Node?> = AtomicReference(null) // tail field
            /*: Long = U.objectFieldOffset(AbstractQueuedSynchronizer::class, "tail")*/

    /**
     * Head of the wait queue, lazily initialized.
     */
    @Volatile
    @Transient
    private var head: Node? = null

    /**
     * Tail of the wait queue. After initialization, modified only via casTail.
     */
    @Volatile
    @Transient
    private var tail: Node? = null

    /**
     * Returns the current value of synchronization state.
     * This operation has memory semantics of a `volatile` read.
     * @return current state value
     */
    /**
     * Sets the value of synchronization state.
     * This operation has memory semantics of a `volatile` write.
     * @param newState the new state value
     */
    /**
     * The synchronization state.
     */
    @Volatile
    protected var state: Int = 0

    /**
     * Atomically sets synchronization state to the given updated
     * value if the current state value equals the expected value.
     * This operation has memory semantics of a `volatile` read
     * and write.
     *
     * @param expect the expected value
     * @param update the new value
     * @return `true` if successful. False return indicates that the actual
     * value was not equal to the expected value.
     */
    @OptIn(ExperimentalAtomicApi::class)
    protected fun compareAndSetState(expect: Int, update: Int): Boolean {
        /*return U.compareAndSetInt(this, STATE, expect, update)*/
        return STATE.compareAndSet(expect, update)
    }

    // Queuing utilities
    @OptIn(ExperimentalAtomicApi::class)
    private fun casTail(c: Node?, v: Node?): Boolean {
        /*return U.compareAndSetReference(this, TAIL, c, v)*/
        return TAIL.compareAndSet(c, v)
    }

    /**
     * Tries to CAS a new dummy node for head.
     * Returns new tail, or null if OutOfMemory
     */
    @OptIn(ExperimentalAtomicApi::class)
    private suspend fun tryInitializeHead(): Node? {
        var h: Node? = null
        var t: Node?
        while (true) {
            if ((tail.also { t = it }) != null) return t
            else if (head != null) /*java.lang.Thread.onSpinWait()*/ yield()
            else {
                if (h == null) {
                    try {
                        h = ExclusiveNode()
                    } catch (oome: /*OutOfMemory*/Error) {
                        return null
                    }
                }
                if (/*U.compareAndSetReference(this, HEAD, null, h)*/ HEAD.compareAndSet(null, h)  ) return h.also { tail = it }
            }
        }
    }

    /**
     * Enqueues the node unless null. (Currently used only for
     * ConditionNodes; other cases are interleaved with acquires.)
     */
    suspend fun enqueue(node: ConditionNode?) {
        if (node != null) {
            var unpark = false
            var t: Node?
            while (true) {
                if ((tail.also { t = it }) == null && (tryInitializeHead().also { t = it }) == null) {
                    unpark = true // wake up to spin on OOME
                    break
                }
                node.setPrevRelaxed(t) // avoid unnecessary fence
                if (casTail(t, node)) {
                    t!!.next = node
                    if (t.status < 0)  // wake up to clean link
                        unpark = true
                    break
                }
            }
            if (unpark) LockSupport.unpark(node.waiter)
        }
    }

    /** Returns true if node is found in traversal from tail  */
    fun isEnqueued(node: Node?): Boolean {
        var t = tail
        while (t != null) {
            if (t === node) return true
            t = t.prev
        }
        return false
    }

    /**
     * Repeatedly invokes acquire, if its execution throws an Error or a Runtime Exception,
     * using an Unsafe.park-based backoff
     * @param node which to reacquire
     * @param arg the acquire argument
     */
    private suspend fun reacquire(node: Node?, arg: Int) {
        try {
            acquire(node, arg, false, false, false, 0L)
        } catch (firstEx: Error) {
            // While we currently do not emit an JFR events in this situation, mainly
            // because the conditions under which this happens are such that it
            // cannot be presumed to be possible to actually allocate an event, and
            // using a preconstructed one would have limited value in serviceability.
            // Having said that, the following place would be the more appropriate
            // place to put such logic:
            //     emit JFR event

            var nanos = 1L
            while (true) {
                delay(nanos.nanoseconds)/*U.park(false, nanos)*/ // must use Unsafe park to sleep
                if (nanos < 1L shl 30)  // max about 1 second
                    nanos = nanos shl 1

                try {
                    acquire(node, arg, false, false, false, 0L)
                } catch (ignored: Error) {
                    continue
                } catch (ignored: RuntimeException) {
                    continue
                }

                throw firstEx
            }
        }
    }

    /**
     * Main acquire method, invoked by all exported acquire methods.
     *
     * @param node null unless a reacquiring Condition
     * @param arg the acquire argument
     * @param shared true if shared mode else exclusive
     * @param interruptible if abort and return negative on interrupt
     * @param timed if true use timed waits
     * @param time if timed, the System.nanoTime value to timeout
     * @return positive if acquired, 0 if timed out, negative if interrupted
     */
    suspend fun acquire(
        node: Node?, arg: Int, shared: Boolean,
        interruptible: Boolean, timed: Boolean, time: Long
    ): Int {
        var node = node
        val current: /*java.lang.Thread*/ Job = currentCoroutineContext()[Job]!!
        var spins: Byte = 0
        var postSpins: Byte = 0 // retries upon unpark of first thread
        var interrupted = false
        var first = false
        var pred: Node? = null // predecessor of node when enqueued

        /*
         * Repeatedly:
         *  Check if node now first
         *    if so, ensure head stable, else ensure valid predecessor
         *  if node is first or not yet enqueued, try acquiring
         *  else if queue is not initialized, do so by attaching new header node
         *     resort to spinwait on OOME trying to create node
         *  else if node not yet created, create it
         *     resort to spinwait on OOME trying to create node
         *  else if not yet enqueued, try once to enqueue
         *  else if woken from park, retry (up to postSpins times)
         *  else if WAITING status not set, set and retry
         *  else park and clear WAITING status, and check cancellation
         */
        while (true) {
            if (!first && ((if (node == null) null else node.prev).also {
                    pred = it
                }) != null && !((head === pred).also { first = it })) {
                if (pred!!.status < 0) {
                    cleanQueue() // predecessor cancelled
                    continue
                } else if (pred.prev == null) {
                    /*java.lang.Thread.onSpinWait()*/ yield() // ensure serialization
                    continue
                }
            }
            if (first || pred == null) {
                val acquired: Boolean
                try {
                    if (shared) acquired = (tryAcquireShared(arg) >= 0)
                    else acquired = tryAcquire(arg)
                } catch (ex: Throwable) {
                    cancelAcquire(node, interrupted, false)
                    throw ex
                }
                if (acquired) {
                    if (first) {
                        node!!.prev = null
                        head = node
                        pred!!.next = null
                        node.waiter = null
                        if (shared) signalNextIfShared(node)
                        if (interrupted) current.cancel() /*.interrupt()*/
                    }
                    return 1
                }
            }
            val t: Node?
            if ((tail.also { t = it }) == null) {           // initialize queue
                if (tryInitializeHead() == null) return acquireOnOOME(shared, arg)
            } else if (node == null) {          // allocate; retry before enqueue
                try {
                    node = if (shared) SharedNode() else ExclusiveNode()
                } catch (oome: /*OutOfMemory*/Error) {
                    return acquireOnOOME(shared, arg)
                }
            } else if (pred == null) {          // try to enqueue
                node.waiter = current
                node.setPrevRelaxed(t) // avoid unnecessary fence
                if (!casTail(t, node)) node.setPrevRelaxed(null) // back out
                else t!!.next = node
            } else if (first && spins.toInt() != 0) {
                --spins // reduce unfairness on rewaits
                /*java.lang.Thread.onSpinWait()*/ yield()
            } else if (node.status == 0) {
                node.status = WAITING // enable signal and recheck
            } else {
                postSpins = ((postSpins.toInt() shl 1) or 1).toByte()
                spins = postSpins
                try {
                    val nanos: Long
                    if (!timed) LockSupport.park(this)
                    else if (((time - System.nanoTime()).also {
                            nanos = it
                        }) > 0L) LockSupport.parkNanos(this, nanos)
                    else break
                } catch (ex: Error) {
                    cancelAcquire(node, interrupted, interruptible) // cancel & rethrow
                    throw ex
                } catch (ex: RuntimeException) {
                    cancelAcquire(node, interrupted, interruptible)
                    throw ex
                }
                node.clearStatus()
                if ((currentCoroutineContext()[Job]!!.isCancelled
                        .let { interrupted = interrupted or it; interrupted }) && interruptible
                ) break
            }
        }
        return cancelAcquire(node, interrupted, interruptible)
    }

    /**
     * Spin-waits with backoff; used only upon OOME failures during acquire.
     */
    private suspend fun acquireOnOOME(shared: Boolean, arg: Int): Int {
        var nanos = 1L
        while (true) {
            if (if (shared) (tryAcquireShared(arg) >= 0) else tryAcquire(arg)) return 1
            delay(nanos.nanoseconds) /*U.park(false, nanos) */// must use Unsafe park to sleep
            if (nanos < 1L shl 30)  // max about 1 second
                nanos = nanos shl 1
        }
    }

    /**
     * Possibly repeatedly traverses from tail, unsplicing cancelled
     * nodes until none are found. Unparks nodes that may have been
     * relinked to be next eligible acquirer.
     */
    private fun cleanQueue() {
        while (true) {
            // restart point
            var q = tail
            var s: Node? = null
            var p: Node? = null
            var n: Node?
            while (true) {
                // (p, q, s) triples
                if (q == null || (q.prev.also { p = it }) == null) return  // end of list

                if (if (s == null) tail !== q else (s.prev !== q || s.status < 0)) break // inconsistent

                if (q.status < 0) {              // cancelled
                    if ((if (s == null) casTail(q, p) else s.casPrev(q, p)) &&
                        q.prev === p
                    ) {
                        p!!.casNext(q, s) // OK if fails
                        if (p.prev == null) signalNext(p)
                    }
                    break
                }
                if ((p!!.next.also { n = it }) !== q) {         // help finish
                    if (n != null && q.prev === p) {
                        p.casNext(n, q)
                        if (p.prev == null) signalNext(p)
                    }
                    break
                }
                s = q
                q = q.prev
            }
        }
    }

    /**
     * Cancels an ongoing attempt to acquire.
     *
     * @param node the node (may be null if cancelled before enqueuing)
     * @param interrupted true if thread interrupted
     * @param interruptible if should report interruption vs reset
     */
    private suspend fun cancelAcquire(
        node: Node?, interrupted: Boolean,
        interruptible: Boolean
    ): Int {
        if (node != null) {
            node.waiter = null
            node.status = CANCELLED
            if (node.prev != null) cleanQueue()
        }
        if (interrupted) {
            if (interruptible) return CANCELLED
            else currentCoroutineContext()[Job]!!.cancel()
        }
        return 0
    }

    // Main exported methods
    /**
     * Attempts to acquire in exclusive mode. This method should query
     * if the state of the object permits it to be acquired in the
     * exclusive mode, and if so to acquire it.
     *
     *
     * This method is always invoked by the thread performing
     * acquire.  If this method reports failure, the acquire method
     * may queue the thread, if it is not already queued, until it is
     * signalled by a release from some other thread. This can be used
     * to implement method [Lock.tryLock].
     *
     *
     * The default
     * implementation throws [UnsupportedOperationException].
     *
     * @param arg the acquire argument. This value is always the one
     * passed to an acquire method, or is the value saved on entry
     * to a condition wait.  The value is otherwise uninterpreted
     * and can represent anything you like.
     * @return `true` if successful. Upon success, this object has
     * been acquired.
     * @throws IllegalMonitorStateException if acquiring would place this
     * synchronizer in an illegal state. This exception must be
     * thrown in a consistent fashion for synchronization to work
     * correctly.
     * @throws UnsupportedOperationException if exclusive mode is not supported
     */
    protected open suspend fun tryAcquire(arg: Int): Boolean {
        throw UnsupportedOperationException()
    }

    /**
     * Attempts to set the state to reflect a release in exclusive
     * mode.
     *
     *
     * This method is always invoked by the thread performing release.
     *
     *
     * The default implementation throws
     * [UnsupportedOperationException].
     *
     * @param arg the release argument. This value is always the one
     * passed to a release method, or the current state value upon
     * entry to a condition wait.  The value is otherwise
     * uninterpreted and can represent anything you like.
     * @return `true` if this object is now in a fully released
     * state, so that any waiting threads may attempt to acquire;
     * and `false` otherwise.
     * @throws IllegalMonitorStateException if releasing would place this
     * synchronizer in an illegal state. This exception must be
     * thrown in a consistent fashion for synchronization to work
     * correctly.
     * @throws UnsupportedOperationException if exclusive mode is not supported
     */
    protected open fun tryRelease(arg: Int): Boolean {
        throw UnsupportedOperationException()
    }

    /**
     * Attempts to acquire in shared mode. This method should query if
     * the state of the object permits it to be acquired in the shared
     * mode, and if so to acquire it.
     *
     *
     * This method is always invoked by the thread performing
     * acquire.  If this method reports failure, the acquire method
     * may queue the thread, if it is not already queued, until it is
     * signalled by a release from some other thread.
     *
     *
     * The default implementation throws [ ].
     *
     * @param arg the acquire argument. This value is always the one
     * passed to an acquire method, or is the value saved on entry
     * to a condition wait.  The value is otherwise uninterpreted
     * and can represent anything you like.
     * @return a negative value on failure; zero if acquisition in shared
     * mode succeeded but no subsequent shared-mode acquire can
     * succeed; and a positive value if acquisition in shared
     * mode succeeded and subsequent shared-mode acquires might
     * also succeed, in which case a subsequent waiting thread
     * must check availability. (Support for three different
     * return values enables this method to be used in contexts
     * where acquires only sometimes act exclusively.)  Upon
     * success, this object has been acquired.
     * @throws IllegalMonitorStateException if acquiring would place this
     * synchronizer in an illegal state. This exception must be
     * thrown in a consistent fashion for synchronization to work
     * correctly.
     * @throws UnsupportedOperationException if shared mode is not supported
     */
    protected open fun tryAcquireShared(arg: Int): Int {
        throw UnsupportedOperationException()
    }

    /**
     * Attempts to set the state to reflect a release in shared mode.
     *
     *
     * This method is always invoked by the thread performing release.
     *
     *
     * The default implementation throws
     * [UnsupportedOperationException].
     *
     * @param arg the release argument. This value is always the one
     * passed to a release method, or the current state value upon
     * entry to a condition wait.  The value is otherwise
     * uninterpreted and can represent anything you like.
     * @return `true` if this release of shared mode may permit a
     * waiting acquire (shared or exclusive) to succeed; and
     * `false` otherwise
     * @throws IllegalMonitorStateException if releasing would place this
     * synchronizer in an illegal state. This exception must be
     * thrown in a consistent fashion for synchronization to work
     * correctly.
     * @throws UnsupportedOperationException if shared mode is not supported
     */
    protected open fun tryReleaseShared(arg: Int): Boolean {
        throw UnsupportedOperationException()
    }

    protected open val isHeldExclusively: Boolean
        /**
         * Returns `true` if synchronization is held exclusively with
         * respect to the current (calling) thread.  This method is invoked
         * upon each call to a [ConditionObject] method.
         *
         *
         * The default implementation throws [ ]. This method is invoked
         * internally only within [ConditionObject] methods, so need
         * not be defined if conditions are not used.
         *
         * @return `true` if synchronization is held exclusively;
         * `false` otherwise
         * @throws UnsupportedOperationException if conditions are not supported
         */
        get() {
            throw UnsupportedOperationException()
        }

    /**
     * Acquires in exclusive mode, ignoring interrupts.  Implemented
     * by invoking at least once [.tryAcquire],
     * returning on success.  Otherwise the thread is queued, possibly
     * repeatedly blocking and unblocking, invoking [ ][.tryAcquire] until success.  This method can be used
     * to implement method [Lock.lock].
     *
     * @param arg the acquire argument.  This value is conveyed to
     * [.tryAcquire] but is otherwise uninterpreted and
     * can represent anything you like.
     */
    suspend fun acquire(arg: Int) {
        if (!tryAcquire(arg)) acquire(null, arg, false, false, false, 0L)
    }

    /**
     * Acquires in exclusive mode, aborting if interrupted.
     * Implemented by first checking interrupt status, then invoking
     * at least once [.tryAcquire], returning on
     * success.  Otherwise the thread is queued, possibly repeatedly
     * blocking and unblocking, invoking [.tryAcquire]
     * until success or the thread is interrupted.  This method can be
     * used to implement method [Lock.lockInterruptibly].
     *
     * @param arg the acquire argument.  This value is conveyed to
     * [.tryAcquire] but is otherwise uninterpreted and
     * can represent anything you like.
     * @throws InterruptedException if the current thread is interrupted
     */
    suspend fun acquireInterruptibly(arg: Int) {
        if (currentCoroutineContext()[Job]!!.isCancelled ||
            (!tryAcquire(arg) && acquire(null, arg, false, true, false, 0L) < 0)
        ) throw InterruptedException()
    }

    /**
     * Attempts to acquire in exclusive mode, aborting if interrupted,
     * and failing if the given timeout elapses.  Implemented by first
     * checking interrupt status, then invoking at least once [ ][.tryAcquire], returning on success.  Otherwise, the thread is
     * queued, possibly repeatedly blocking and unblocking, invoking
     * [.tryAcquire] until success or the thread is interrupted
     * or the timeout elapses.  This method can be used to implement
     * method [Lock.tryLock].
     *
     * @param arg the acquire argument.  This value is conveyed to
     * [.tryAcquire] but is otherwise uninterpreted and
     * can represent anything you like.
     * @param nanosTimeout the maximum number of nanoseconds to wait
     * @return `true` if acquired; `false` if timed out
     * @throws InterruptedException if the current thread is interrupted
     */
    suspend fun tryAcquireNanos(arg: Int, nanosTimeout: Long): Boolean {
        if (!currentCoroutineContext()[Job]!!.isCancelled) {
            if (tryAcquire(arg)) return true
            if (nanosTimeout <= 0L) return false
            val stat = acquire(
                null, arg, false, true, true,
                System.nanoTime() + nanosTimeout
            )
            if (stat > 0) return true
            if (stat == 0) return false
        }
        throw InterruptedException()
    }

    /**
     * Releases in exclusive mode.  Implemented by unblocking one or
     * more threads if [.tryRelease] returns true.
     * This method can be used to implement method [Lock.unlock].
     *
     * @param arg the release argument.  This value is conveyed to
     * [.tryRelease] but is otherwise uninterpreted and
     * can represent anything you like.
     * @return the value returned from [.tryRelease]
     */
    fun release(arg: Int): Boolean {
        if (tryRelease(arg)) {
            signalNext(head)
            return true
        }
        return false
    }

    /**
     * Acquires in shared mode, ignoring interrupts.  Implemented by
     * first invoking at least once [.tryAcquireShared],
     * returning on success.  Otherwise the thread is queued, possibly
     * repeatedly blocking and unblocking, invoking [ ][.tryAcquireShared] until success.
     *
     * @param arg the acquire argument.  This value is conveyed to
     * [.tryAcquireShared] but is otherwise uninterpreted
     * and can represent anything you like.
     */
    suspend fun acquireShared(arg: Int) {
        if (tryAcquireShared(arg) < 0) acquire(null, arg, true, false, false, 0L)
    }

    /**
     * Acquires in shared mode, aborting if interrupted.  Implemented
     * by first checking interrupt status, then invoking at least once
     * [.tryAcquireShared], returning on success.  Otherwise the
     * thread is queued, possibly repeatedly blocking and unblocking,
     * invoking [.tryAcquireShared] until success or the thread
     * is interrupted.
     * @param arg the acquire argument.
     * This value is conveyed to [.tryAcquireShared] but is
     * otherwise uninterpreted and can represent anything
     * you like.
     * @throws InterruptedException if the current thread is interrupted
     */
    suspend fun acquireSharedInterruptibly(arg: Int) {
        if (currentCoroutineContext()[Job]!!.isCancelled ||
            (tryAcquireShared(arg) < 0 &&
                    acquire(null, arg, true, true, false, 0L) < 0)
        ) throw InterruptedException()
    }

    /**
     * Attempts to acquire in shared mode, aborting if interrupted, and
     * failing if the given timeout elapses.  Implemented by first
     * checking interrupt status, then invoking at least once [ ][.tryAcquireShared], returning on success.  Otherwise, the
     * thread is queued, possibly repeatedly blocking and unblocking,
     * invoking [.tryAcquireShared] until success or the thread
     * is interrupted or the timeout elapses.
     *
     * @param arg the acquire argument.  This value is conveyed to
     * [.tryAcquireShared] but is otherwise uninterpreted
     * and can represent anything you like.
     * @param nanosTimeout the maximum number of nanoseconds to wait
     * @return `true` if acquired; `false` if timed out
     * @throws InterruptedException if the current thread is interrupted
     */
    suspend fun tryAcquireSharedNanos(arg: Int, nanosTimeout: Long): Boolean {
        if (!currentCoroutineContext()[Job]!!.isCancelled) {
            if (tryAcquireShared(arg) >= 0) return true
            if (nanosTimeout <= 0L) return false
            val stat = acquire(
                null, arg, true, true, true,
                System.nanoTime() + nanosTimeout
            )
            if (stat > 0) return true
            if (stat == 0) return false
        }
        throw InterruptedException()
    }

    /**
     * Releases in shared mode.  Implemented by unblocking one or more
     * threads if [.tryReleaseShared] returns true.
     *
     * @param arg the release argument.  This value is conveyed to
     * [.tryReleaseShared] but is otherwise uninterpreted
     * and can represent anything you like.
     * @return the value returned from [.tryReleaseShared]
     */
    fun releaseShared(arg: Int): Boolean {
        if (tryReleaseShared(arg)) {
            signalNext(head)
            return true
        }
        return false
    }

    // Queue inspection methods
    /**
     * Queries whether any threads are waiting to acquire. Note that
     * because cancellations due to interrupts and timeouts may occur
     * at any time, a `true` return does not guarantee that any
     * other thread will ever acquire.
     *
     * @return `true` if there may be other threads waiting to acquire
     */
    fun hasQueuedThreads(): Boolean {
        var p = tail
        val h = head
        while (p !== h && p != null) {
            if (p.status >= 0) return true
            p = p.prev
        }
        return false
    }

    /**
     * Queries whether any threads have ever contended to acquire this
     * synchronizer; that is, if an acquire method has ever blocked.
     *
     *
     * In this implementation, this operation returns in
     * constant time.
     *
     * @return `true` if there has ever been contention
     */
    fun hasContended(): Boolean {
        return head != null
    }

    val firstQueuedThread: /*java.lang.Thread*/Job?
        /**
         * Returns the first (longest-waiting) thread in the queue, or
         * `null` if no threads are currently queued.
         *
         *
         * In this implementation, this operation normally returns in
         * constant time, but may iterate upon contention if other threads are
         * concurrently modifying the queue.
         *
         * @return the first (longest-waiting) thread in the queue, or
         * `null` if no threads are currently queued
         */
        get() {
            var first: /*java.lang.Thread*/Job? = null
            var w: /*java.lang.Thread*/Job?
            val h: Node?
            val s: Node?
            if ((head.also { h = it }) != null && ((h!!.next.also { s = it }) == null || (s!!.waiter.also {
                    first = it
                }) == null || s.prev == null)) {
                // traverse from tail on stale reads
                var p = tail
                var q: Node? = null
                while (p != null && (p.prev.also { q = it }) != null) {
                    if ((p.waiter.also { w = it }) != null) first = w
                    p = q
                }
            }
            return first
        }

    /**
     * Returns true if the given thread is currently queued.
     *
     *
     * This implementation traverses the queue to determine
     * presence of the given thread.
     *
     * @param thread the thread
     * @return `true` if the given thread is on the queue
     * @throws NullPointerException if the thread is null
     */
    fun isQueued(thread: /*java.lang.Thread*/ Job): Boolean {
        //if (thread == null) throw java.lang.NullPointerException()
        var p = tail
        while (p != null) {
            if (p.waiter === thread) return true
            p = p.prev
        }
        return false
    }

    /**
     * Returns `true` if the apparent first queued thread, if one
     * exists, is waiting in exclusive mode.  If this method returns
     * `true`, and the current thread is attempting to acquire in
     * shared mode (that is, this method is invoked from [ ][.tryAcquireShared]) then it is guaranteed that the current thread
     * is not the first queued thread.  Used only as a heuristic in
     * ReentrantReadWriteLock.
     */
    fun apparentlyFirstQueuedIsExclusive(): Boolean {
        val h: Node?
        var s: Node? = null
        return (head.also { h = it }) != null && (h!!.next.also {
            s = it
        }) != null && (s !is SharedNode) && s!!.waiter != null
    }

    /**
     * Queries whether any threads have been waiting to acquire longer
     * than the current thread.
     *
     *
     * An invocation of this method is equivalent to (but may be
     * more efficient than):
     * <pre> `getFirstQueuedThread() != Thread.currentThread()
     * && hasQueuedThreads()`</pre>
     *
     *
     * Note that because cancellations due to interrupts and
     * timeouts may occur at any time, a `true` return does not
     * guarantee that some other thread will acquire before the current
     * thread.  Likewise, it is possible for another thread to win a
     * race to enqueue after this method has returned `false`,
     * due to the queue being empty.
     *
     *
     * This method is designed to be used by a fair synchronizer to
     * avoid [barging](AbstractQueuedSynchronizer.html#barging).
     * Such a synchronizer's [.tryAcquire] method should return
     * `false`, and its [.tryAcquireShared] method should
     * return a negative value, if this method returns `true`
     * (unless this is a reentrant acquire).  For example, the `tryAcquire` method for a fair, reentrant, exclusive mode
     * synchronizer might look like this:
     *
     * <pre> `protected boolean tryAcquire(int arg) {
     * if (isHeldExclusively()) {
     * // A reentrant acquire; increment hold count
     * return true;
     * } else if (hasQueuedPredecessors()) {
     * return false;
     * } else {
     * // try to acquire normally
     * }
     * }`</pre>
     *
     * @return `true` if there is a queued thread preceding the
     * current thread, and `false` if the current thread
     * is at the head of the queue or the queue is empty
     * @since 1.7
     */
    suspend fun hasQueuedPredecessors(): Boolean {
        var first: /*java.lang.Thread*/Job? = null
        val h: Node?
        val s: Node?
        if ((head.also { h = it }) != null && ((h!!.next.also { s = it }) == null || (s!!.waiter.also {
                first = it
            }) == null || s.prev == null)) first =
            this.firstQueuedThread // retry via getFirstQueuedThread

        return first != null && first !== currentCoroutineContext()[Job]
    }

    // Instrumentation and monitoring methods
    val queueLength: Int
        /**
         * Returns an estimate of the number of threads waiting to
         * acquire.  The value is only an estimate because the number of
         * threads may change dynamically while this method traverses
         * internal data structures.  This method is designed for use in
         * monitoring system state, not for synchronization control.
         *
         * @return the estimated number of threads waiting to acquire
         */
        get() {
            var n = 0
            var p = tail
            while (p != null) {
                if (p.waiter != null) ++n
                p = p.prev
            }
            return n
        }

    // implement if needed
    /*val queuedThreads: MutableCollection<*//*java.lang.Thread*//* Job>
        *//**
         * Returns a collection containing threads that may be waiting to
         * acquire.  Because the actual set of threads may change
         * dynamically while constructing this result, the returned
         * collection is only a best-effort estimate.  The elements of the
         * returned collection are in no particular order.  This method is
         * designed to facilitate construction of subclasses that provide
         * more extensive monitoring facilities.
         *
         * @return the collection of threads
         *//*
        get() {
            val list: java.util.ArrayList<java.lang.Thread?> = java.util.ArrayList<java.lang.Thread?>()
            var p = tail
            while (p != null) {
                val t: java.lang.Thread? = p.waiter
                if (t != null) list.add(t)
                p = p.prev
            }
            return list
        }*/

    // implement if needed
    /*val exclusiveQueuedThreads: MutableCollection<java.lang.Thread>
        *//**
         * Returns a collection containing threads that may be waiting to
         * acquire in exclusive mode. This has the same properties
         * as [.getQueuedThreads] except that it only returns
         * those threads waiting due to an exclusive acquire.
         *
         * @return the collection of threads
         *//*
        get() {
            val list: java.util.ArrayList<java.lang.Thread?> = java.util.ArrayList<java.lang.Thread?>()
            var p = tail
            while (p != null) {
                if (p !is SharedNode) {
                    val t: java.lang.Thread? = p.waiter
                    if (t != null) list.add(t)
                }
                p = p.prev
            }
            return list
        }*/

    // implement if needed
    /*val sharedQueuedThreads: MutableCollection<java.lang.Thread>
        *//**
         * Returns a collection containing threads that may be waiting to
         * acquire in shared mode. This has the same properties
         * as [.getQueuedThreads] except that it only returns
         * those threads waiting due to a shared acquire.
         *
         * @return the collection of threads
         *//*
        get() {
            val list: java.util.ArrayList<java.lang.Thread?> = java.util.ArrayList<java.lang.Thread?>()
            var p = tail
            while (p != null) {
                if (p is SharedNode) {
                    val t: java.lang.Thread? = p.waiter
                    if (t != null) list.add(t)
                }
                p = p.prev
            }
            return list
        }*/

    /**
     * Returns a string identifying this synchronizer, as well as its state.
     * The state, in brackets, includes the String `"State ="`
     * followed by the current value of [.getState], and either
     * `"nonempty"` or `"empty"` depending on whether the
     * queue is empty.
     *
     * @return a string identifying this synchronizer, as well as its state
     */
    override fun toString(): String {
        return (super.toString()
                + "[State = " + this.state + ", "
                + (if (hasQueuedThreads()) "non" else "") + "empty queue]")
    }

    // Instrumentation methods for conditions
    /**
     * Queries whether the given ConditionObject
     * uses this synchronizer as its lock.
     *
     * @param condition the condition
     * @return `true` if owned
     * @throws NullPointerException if the condition is null
     */
    fun owns(condition: ConditionObject): Boolean {
        return condition.isOwnedBy(this)
    }

    /**
     * Queries whether any threads are waiting on the given condition
     * associated with this synchronizer. Note that because timeouts
     * and interrupts may occur at any time, a `true` return
     * does not guarantee that a future `signal` will awaken
     * any threads.  This method is designed primarily for use in
     * monitoring of the system state.
     *
     * @param condition the condition
     * @return `true` if there are any waiting threads
     * @throws IllegalMonitorStateException if exclusive synchronization
     * is not held
     * @throws IllegalArgumentException if the given condition is
     * not associated with this synchronizer
     * @throws NullPointerException if the condition is null
     */
    fun hasWaiters(condition: ConditionObject): Boolean {
        require(owns(condition)) { "Not owner" }
        return condition.hasWaiters()
    }

    /**
     * Returns an estimate of the number of threads waiting on the
     * given condition associated with this synchronizer. Note that
     * because timeouts and interrupts may occur at any time, the
     * estimate serves only as an upper bound on the actual number of
     * waiters.  This method is designed for use in monitoring system
     * state, not for synchronization control.
     *
     * @param condition the condition
     * @return the estimated number of waiting threads
     * @throws IllegalMonitorStateException if exclusive synchronization
     * is not held
     * @throws IllegalArgumentException if the given condition is
     * not associated with this synchronizer
     * @throws NullPointerException if the condition is null
     */
    fun getWaitQueueLength(condition: ConditionObject): Int {
        require(owns(condition)) { "Not owner" }
        return condition.waitQueueLength
    }

    /**
     * Returns a collection containing those threads that may be
     * waiting on the given condition associated with this
     * synchronizer.  Because the actual set of threads may change
     * dynamically while constructing this result, the returned
     * collection is only a best-effort estimate. The elements of the
     * returned collection are in no particular order.
     *
     * @param condition the condition
     * @return the collection of threads
     * @throws IllegalMonitorStateException if exclusive synchronization
     * is not held
     * @throws IllegalArgumentException if the given condition is
     * not associated with this synchronizer
     * @throws NullPointerException if the condition is null
     */
    fun getWaitingThreads(condition: ConditionObject): MutableCollection</*java.lang.Thread*/Job> {
        require(owns(condition)) { "Not owner" }
        return condition.waitingThreads
    }

    /**
     * Condition implementation for a [AbstractQueuedSynchronizer]
     * serving as the basis of a [Lock] implementation.
     *
     *
     * Method documentation for this class describes mechanics,
     * not behavioral specifications from the point of view of Lock
     * and Condition users. Exported versions of this class will in
     * general need to be accompanied by documentation describing
     * condition semantics that rely on those of the associated
     * `AbstractQueuedSynchronizer`.
     *
     *
     * This class is Serializable, but all fields are transient,
     * so deserialized conditions have no waiters.
     */
    inner class ConditionObject
    /**
     * Creates a new `ConditionObject` instance.
     */
        : Condition/*, java.io.Serializable */{
        /** First node of condition queue.  */
        @Transient
        private var firstWaiter: ConditionNode? = null

        /** Last node of condition queue.  */
        @Transient
        private var lastWaiter: ConditionNode? = null

        // Signalling methods
        /**
         * Removes and transfers one or all waiters to sync queue.
         */
        private suspend fun doSignal(first: ConditionNode?, all: Boolean) {
            var first = first
            while (first != null) {
                val next = first.nextWaiter

                if ((next.also { firstWaiter = it }) == null) lastWaiter = null
                else first.nextWaiter = null // GC assistance


                if ((first.getAndUnsetStatus(COND) and COND) != 0) {
                    enqueue(first)
                    if (!all) break
                }

                first = next
            }
        }

        /**
         * Moves the longest-waiting thread, if one exists, from the
         * wait queue for this condition to the wait queue for the
         * owning lock.
         *
         * @throws IllegalMonitorStateException if [.isHeldExclusively]
         * returns `false`
         */
        override suspend fun signal() {
            val first = firstWaiter
            if (!this@AbstractQueuedSynchronizer.isHeldExclusively) throw /*IllegalMonitorState*/Exception()
            else if (first != null) doSignal(first, false)
        }

        /**
         * Moves all threads from the wait queue for this condition to
         * the wait queue for the owning lock.
         *
         * @throws IllegalMonitorStateException if [.isHeldExclusively]
         * returns `false`
         */
        override suspend fun signalAll() {
            val first = firstWaiter
            if (!this@AbstractQueuedSynchronizer.isHeldExclusively) throw /*IllegalMonitorState*/Exception()
            else if (first != null) doSignal(first, true)
        }

        // Waiting methods
        /**
         * Adds node to condition list and releases lock.
         *
         * @param node the node
         * @return savedState to reacquire after wait
         */
        private suspend fun enableWait(node: ConditionNode): Int {
            if (this@AbstractQueuedSynchronizer.isHeldExclusively) {
                node.waiter = currentCoroutineContext()[Job]!!
                node.setStatusRelaxed(COND or WAITING)
                val last = lastWaiter
                if (last == null) firstWaiter = node
                else last.nextWaiter = node
                lastWaiter = node
                val savedState: Int = this@AbstractQueuedSynchronizer.state
                if (release(savedState)) return savedState
            }
            node.status = CANCELLED // lock not held or inconsistent
            throw /*IllegalMonitorState*/Exception()
        }

        /**
         * Returns true if a node that was initially placed on a condition
         * queue is now ready to reacquire on sync queue.
         * @param node the node
         * @return true if is reacquiring
         */
        private fun canReacquire(node: ConditionNode?): Boolean {
            // check links, not status to avoid enqueue race
            var p: Node? = null // traverse unless known to be bidirectionally linked
            return node != null && (node.prev.also { p = it }) != null &&
                    (p!!.next === node || isEnqueued(node))
        }

        /**
         * Unlinks the given node and other non-waiting nodes from
         * condition queue unless already unlinked.
         */
        private fun unlinkCancelledWaiters(node: ConditionNode?) {
            if (node == null || node.nextWaiter != null || node == lastWaiter) {
                var w = firstWaiter
                var trail: ConditionNode? = null
                while (w != null) {
                    val next = w.nextWaiter
                    if ((w.status and COND) == 0) {
                        w.nextWaiter = null
                        if (trail == null) firstWaiter = next
                        else trail.nextWaiter = next
                        if (next == null) lastWaiter = trail
                    } else trail = w
                    w = next
                }
            }
        }

        /**
         * Constructs objects needed for condition wait. On OOME,
         * releases lock, sleeps, reacquires, and returns null.
         */
        private suspend fun newConditionNode(): ConditionNode? {
            var savedState: Int = 0
            if (tryInitializeHead() != null) {
                try {
                    return ConditionNode()
                } catch (oome: /*OutOfMemory*/Error) {
                }
            }
            // fall through if encountered OutOfMemoryError
            if (!this@AbstractQueuedSynchronizer.isHeldExclusively || !release(this@AbstractQueuedSynchronizer.state.also {
                    savedState = it
                })) throw /*IllegalMonitorState*/Exception()
            delay(OOME_COND_WAIT_DELAY.nanoseconds) /*U.park(false, OOME_COND_WAIT_DELAY)*/
            acquireOnOOME(false, savedState)
            return null
        }

        /**
         * Implements uninterruptible condition wait.
         *
         *  1. Save lock state returned by [.getState].
         *  1. Invoke [.release] with saved state as argument,
         * throwing IllegalMonitorStateException if it fails.
         *  1. Block until signalled.
         *  1. Reacquire by invoking specialized version of
         * [.acquire] with saved state as argument.
         *
         */
        override suspend fun awaitUninterruptibly() {
            val node = newConditionNode()
            if (node == null) return
            val savedState = enableWait(node)
            LockSupport.setCurrentBlocker(this) // for back-compatibility
            var interrupted = false
            var rejected = false
            while (!canReacquire(node)) {
                if (currentCoroutineContext()[Job]!!.isCancelled) interrupted = true
                else if ((node.status and COND) != 0) {
                    try {
                        if (rejected) node.block()
                        else ForkJoinPool.managedBlock(node)
                    } catch (ex: RejectedExecutionException) {
                        rejected = true
                    } catch (ie: InterruptedException) {
                        interrupted = true
                    }
                } else /*java.lang.Thread.onSpinWait()*/ yield() // awoke while enqueuing
            }
            LockSupport.setCurrentBlocker(null)
            node.clearStatus()
            reacquire(node, savedState)
            if (interrupted) currentCoroutineContext()[Job]!!.cancel()
        }

        /**
         * Implements interruptible condition wait.
         *
         *  1. If current thread is interrupted, throw InterruptedException.
         *  1. Save lock state returned by [.getState].
         *  1. Invoke [.release] with saved state as argument,
         * throwing IllegalMonitorStateException if it fails.
         *  1. Block until signalled or interrupted.
         *  1. Reacquire by invoking specialized version of
         * [.acquire] with saved state as argument.
         *  1. If interrupted while blocked in step 4, throw InterruptedException.
         *
         */
        override suspend fun await() {
            if (currentCoroutineContext()[Job]!!.isCancelled) throw InterruptedException()
            val node = newConditionNode()
            if (node == null) return
            val savedState = enableWait(node)
            LockSupport.setCurrentBlocker(this) // for back-compatibility
            var interrupted = false
            var cancelled = false
            var rejected = false
            while (!canReacquire(node)) {
                if (currentCoroutineContext()[Job]!!.isCancelled.let { interrupted = interrupted or it; interrupted }) {
                    if (((node.getAndUnsetStatus(COND) and COND) != 0).also {
                            cancelled = it
                        }) break // else interrupted after signal
                } else if ((node.status and COND) != 0) {
                    try {
                        if (rejected) node.block()
                        else ForkJoinPool.managedBlock(node)
                    } catch (ex: RejectedExecutionException) {
                        rejected = true
                    } catch (ie: InterruptedException) {
                        interrupted = true
                    }
                } else /*java.lang.Thread.onSpinWait()*/ yield() // awoke while enqueuing
            }
            LockSupport.setCurrentBlocker(null)
            node.clearStatus()
            reacquire(node, savedState)
            if (interrupted) {
                if (cancelled) {
                    unlinkCancelledWaiters(node)
                    throw InterruptedException()
                }
                currentCoroutineContext()[Job]!!.cancel()
            }
        }

        /**
         * Implements timed condition wait.
         *
         *  1. If current thread is interrupted, throw InterruptedException.
         *  1. Save lock state returned by [.getState].
         *  1. Invoke [.release] with saved state as argument,
         * throwing IllegalMonitorStateException if it fails.
         *  1. Block until signalled, interrupted, or timed out.
         *  1. Reacquire by invoking specialized version of
         * [.acquire] with saved state as argument.
         *  1. If interrupted while blocked in step 4, throw InterruptedException.
         *
         */
        override suspend fun awaitNanos(nanosTimeout: Long): Long {
            if (currentCoroutineContext()[Job]!!.isCancelled) throw InterruptedException()
            val node = newConditionNode()
            if (node == null) return nanosTimeout - OOME_COND_WAIT_DELAY
            val savedState = enableWait(node)
            var nanos = if (nanosTimeout < 0L) 0L else nanosTimeout
            val deadline: Long = System.nanoTime() + nanos
            var cancelled = false
            var interrupted = false
            while (!canReacquire(node)) {
                if ((currentCoroutineContext()[Job]!!.isCancelled.let { interrupted = interrupted or it; interrupted }) ||
                    ((deadline - System.nanoTime()).also { nanos = it }) <= 0L
                ) {
                    if (((node.getAndUnsetStatus(COND) and COND) != 0).also { cancelled = it }) break
                } else LockSupport.parkNanos(this, nanos)
            }
            node.clearStatus()
            reacquire(node, savedState)
            if (cancelled) {
                unlinkCancelledWaiters(node)
                if (interrupted) throw InterruptedException()
            } else if (interrupted) currentCoroutineContext()[Job]!!.cancel()
            val remaining: Long = deadline - System.nanoTime() // avoid overflow
            return if (remaining <= nanosTimeout) remaining else Long.Companion.MIN_VALUE
        }

        /**
         * Implements absolute timed condition wait.
         *
         *  1. If current thread is interrupted, throw InterruptedException.
         *  1. Save lock state returned by [.getState].
         *  1. Invoke [.release] with saved state as argument,
         * throwing IllegalMonitorStateException if it fails.
         *  1. Block until signalled, interrupted, or timed out.
         *  1. Reacquire by invoking specialized version of
         * [.acquire] with saved state as argument.
         *  1. If interrupted while blocked in step 4, throw InterruptedException.
         *  1. If timed out while blocked in step 4, return false, else true.
         *
         */
        @OptIn(ExperimentalTime::class)
        override suspend fun awaitUntil(deadline: /*java.util.Date*/ Instant): Boolean {
            val abstime: Instant = deadline/*.getTime()*/
            if (currentCoroutineContext()[Job]!!.isActive) throw InterruptedException()
            val node = newConditionNode()
            if (node == null) return false
            val savedState = enableWait(node)
            var cancelled = false
            var interrupted = false
            while (!canReacquire(node)) {
                if ((currentCoroutineContext()[Job]!!.isCancelled.let { interrupted = interrupted or it; interrupted }) ||
                    /*java.lang.System.currentTimeMillis()*/ Clock.System.now() >= abstime
                ) {
                    if (((node.getAndUnsetStatus(COND) and COND) != 0).also { cancelled = it }) break
                } else LockSupport.parkUntil(this, abstime)
            }
            node.clearStatus()
            reacquire(node, savedState)
            if (cancelled) {
                unlinkCancelledWaiters(node)
                if (interrupted) throw InterruptedException()
            } else if (interrupted) currentCoroutineContext()[Job]!!.cancel()
            return !cancelled
        }

        /**
         * Implements timed condition wait.
         *
         *  1. If current thread is interrupted, throw InterruptedException.
         *  1. Save lock state returned by [.getState].
         *  1. Invoke [.release] with saved state as argument,
         * throwing IllegalMonitorStateException if it fails.
         *  1. Block until signalled, interrupted, or timed out.
         *  1. Reacquire by invoking specialized version of
         * [.acquire] with saved state as argument.
         *  1. If interrupted while blocked in step 4, throw InterruptedException.
         *  1. If timed out while blocked in step 4, return false, else true.
         *
         */
        override suspend fun await(time: Long, unit: TimeUnit): Boolean {
            val nanosTimeout: Long = unit.toNanos(time)
            if (currentCoroutineContext()[Job]!!.isCancelled) throw InterruptedException()
            val node = newConditionNode()
            if (node == null) return false
            val savedState = enableWait(node)
            var nanos = if (nanosTimeout < 0L) 0L else nanosTimeout
            val deadline: Long = System.nanoTime() + nanos
            var cancelled = false
            var interrupted = false
            while (!canReacquire(node)) {
                if ((currentCoroutineContext()[Job]!!.isCancelled.let { interrupted = interrupted or it; interrupted }) ||
                    ((deadline - System.nanoTime()).also { nanos = it }) <= 0L
                ) {
                    if (((node.getAndUnsetStatus(COND) and COND) != 0).also { cancelled = it }) break
                } else LockSupport.parkNanos(this, nanos)
            }
            node.clearStatus()
            reacquire(node, savedState)
            if (cancelled) {
                unlinkCancelledWaiters(node)
                if (interrupted) throw InterruptedException()
            } else if (interrupted) currentCoroutineContext()[Job]!!.cancel()
            return !cancelled
        }

        //  support for instrumentation
        /**
         * Returns true if this condition was created by the given
         * synchronization object.
         *
         * @return `true` if owned
         */
        fun isOwnedBy(sync: AbstractQueuedSynchronizer?): Boolean {
            return sync === this@AbstractQueuedSynchronizer
        }

        /**
         * Queries whether any threads are waiting on this condition.
         * Implements [AbstractQueuedSynchronizer.hasWaiters].
         *
         * @return `true` if there are any waiting threads
         * @throws IllegalMonitorStateException if [.isHeldExclusively]
         * returns `false`
         */
        fun hasWaiters(): Boolean {
            if (!this@AbstractQueuedSynchronizer.isHeldExclusively) throw /*IllegalMonitorState*/Exception()
            var w = firstWaiter
            while (w != null) {
                if ((w.status and COND) != 0) return true
                w = w.nextWaiter
            }
            return false
        }

        val waitQueueLength: Int
            /**
             * Returns an estimate of the number of threads waiting on
             * this condition.
             * Implements [AbstractQueuedSynchronizer.getWaitQueueLength].
             *
             * @return the estimated number of waiting threads
             * @throws IllegalMonitorStateException if [.isHeldExclusively]
             * returns `false`
             */
            get() {
                if (!this@AbstractQueuedSynchronizer.isHeldExclusively) throw /*IllegalMonitorState*/Exception()
                var n = 0
                var w = firstWaiter
                while (w != null) {
                    if ((w.status and COND) != 0) ++n
                    w = w.nextWaiter
                }
                return n
            }

        val waitingThreads: MutableCollection</*java.lang.Thread*/ Job>
            /**
             * Returns a collection containing those threads that may be
             * waiting on this Condition.
             * Implements [AbstractQueuedSynchronizer.getWaitingThreads].
             *
             * @return the collection of threads
             * @throws IllegalMonitorStateException if [.isHeldExclusively]
             * returns `false`
             */
            get() {
                if (!this@AbstractQueuedSynchronizer.isHeldExclusively) throw /*IllegalMonitorState*/Exception()
                val list: ArrayList</*java.lang.Thread?*/Job> = ArrayList()
                var w = firstWaiter
                while (w != null) {
                    if ((w.status and COND) != 0) {
                        val t: /*java.lang.Thread*/Job? = w.waiter
                        if (t != null) list.add(t)
                    }
                    w = w.nextWaiter
                }
                return list
            }

    }

    companion object {

        /**
         * Fixed delay in nanoseconds between releasing and reacquiring
         * lock during Condition waits that encounter OutOfMemoryErrors
         */
        const val OOME_COND_WAIT_DELAY: Long = 10L * 1000L * 1000L // 10 ms

        /*
     * Overview.
     *
     * The wait queue is a variant of a "CLH" (Craig, Landin, and
     * Hagersten) lock queue. CLH locks are normally used for
     * spinlocks.  We instead use them for blocking synchronizers by
     * including explicit ("prev" and "next") links plus a "status"
     * field that allow nodes to signal successors when releasing
     * locks, and handle cancellation due to interrupts and timeouts.
     * The status field includes bits that track whether a thread
     * needs a signal (using LockSupport.unpark). Despite these
     * additions, we maintain most CLH locality properties.
     *
     * To enqueue into a CLH lock, you atomically splice it in as new
     * tail. To dequeue, you set the head field, so the next eligible
     * waiter becomes first.
     *
     *  +------+  prev +-------+       +------+
     *  | head | <---- | first | <---- | tail |
     *  +------+       +-------+       +------+
     *
     * Insertion into a CLH queue requires only a single atomic
     * operation on "tail", so there is a simple point of demarcation
     * from unqueued to queued. The "next" link of the predecessor is
     * set by the enqueuing thread after successful CAS. Even though
     * non-atomic, this suffices to ensure that any blocked thread is
     * signalled by a predecessor when eligible (although in the case
     * of cancellation, possibly with the assistance of a signal in
     * method cleanQueue). Signalling is based in part on a
     * Dekker-like scheme in which the to-be waiting thread indicates
     * WAITING status, then retries acquiring, and then rechecks
     * status before blocking. The signaller atomically clears WAITING
     * status when unparking.
     *
     * Dequeuing on acquire involves detaching (nulling) a node's
     * "prev" node and then updating the "head". Other threads check
     * if a node is or was dequeued by checking "prev" rather than
     * head. We enforce the nulling then setting order by spin-waiting
     * if necessary. Because of this, the lock algorithm is not itself
     * strictly "lock-free" because an acquiring thread may need to
     * wait for a previous acquire to make progress. When used with
     * exclusive locks, such progress is required anyway. However
     * Shared mode may (uncommonly) require a spin-wait before
     * setting head field to ensure proper propagation. (Historical
     * note: This allows some simplifications and efficiencies
     * compared to previous versions of this class.)
     *
     * A node's predecessor can change due to cancellation while it is
     * waiting, until the node is first in queue, at which point it
     * cannot change. The acquire methods cope with this by rechecking
     * "prev" before waiting. The prev and next fields are modified
     * only via CAS by cancelled nodes in method cleanQueue. The
     * unsplice strategy is reminiscent of Michael-Scott queues in
     * that after a successful CAS to prev field, other threads help
     * fix next fields.  Because cancellation often occurs in bunches
     * that complicate decisions about necessary signals, each call to
     * cleanQueue traverses the queue until a clean sweep. Nodes that
     * become relinked as first are unconditionally unparked
     * (sometimes unnecessarily, but those cases are not worth
     * avoiding).
     *
     * A thread may try to acquire if it is first (frontmost) in the
     * queue, and sometimes before.  Being first does not guarantee
     * success; it only gives the right to contend. We balance
     * throughput, overhead, and fairness by allowing incoming threads
     * to "barge" and acquire the synchronizer while in the process of
     * enqueuing, in which case an awakened first thread may need to
     * rewait.  To counteract possible repeated unlucky rewaits, we
     * exponentially increase retries (up to 256) to acquire each time
     * a thread is unparked. Except in this case, AQS locks do not
     * spin; they instead interleave attempts to acquire with
     * bookkeeping steps. (Users who want spinlocks can use
     * tryAcquire.)
     *
     * To improve garbage collectibility, fields of nodes not yet on
     * list are null. (It is not rare to create and then throw away a
     * node without using it.) Fields of nodes coming off the list are
     * nulled out as soon as possible. This accentuates the challenge
     * of externally determining the first waiting thread (as in
     * method getFirstQueuedThread). This sometimes requires the
     * fallback of traversing backwards from the atomically updated
     * "tail" when fields appear null. (This is never needed in the
     * process of signalling though.)
     *
     * CLH queues need a dummy header node to get started. But
     * we don't create them on construction, because it would be wasted
     * effort if there is never contention. Instead, the node
     * is constructed and head and tail pointers are set upon first
     * contention.
     *
     * Shared mode operations differ from Exclusive in that an acquire
     * signals the next waiter to try to acquire if it is also
     * Shared. The tryAcquireShared API allows users to indicate the
     * degree of propagation, but in most applications, it is more
     * efficient to ignore this, allowing the successor to try
     * acquiring in any case.
     *
     * Threads waiting on Conditions use nodes with an additional
     * link to maintain the (FIFO) list of conditions. Conditions only
     * need to link nodes in simple (non-concurrent) linked queues
     * because they are only accessed when exclusively held.  Upon
     * await, a node is inserted into a condition queue.  Upon signal,
     * the node is enqueued on the main queue.  A special status field
     * value is used to track and atomically trigger this.
     *
     * Accesses to fields head, tail, and state use full Volatile
     * mode, along with CAS. Node fields status, prev and next also do
     * so while threads may be signallable, but sometimes use weaker
     * modes otherwise. Accesses to field "waiter" (the thread to be
     * signalled) are always sandwiched between other atomic accesses
     * so are used in Plain mode. We use jdk.internal Unsafe versions
     * of atomic access methods rather than VarHandles to avoid
     * potential VM bootstrap issues.
     *
     * Most of the above is performed by primary internal method
     * acquire, that is invoked in some way by all exported acquire
     * methods.  (It is usually easy for compilers to optimize
     * call-site specializations when heavily used.)
     *
     * Most AQS methods may be called by JDK components that cannot be
     * allowed to fail when encountering OutOfMemoryErrors. The main
     * acquire method resorts to spin-waits with backoff if nodes
     * cannot be allocated. Condition waits release and reacquire
     * locks upon OOME at a slow fixed rate (OOME_COND_WAIT_DELAY)
     * designed with the hope that eventually enough memory will be
     * recovered; if not performance can be very slow. Effectiveness
     * is also limited by the possibility of class loading triggered
     * by first-time usages, that may encounter unrecoverable
     * OOMEs. Also, it is possible for OutOfMemoryErrors to be thrown
     * when attempting to create and throw
     * IllegalMonitorStateExceptions and InterruptedExceptions.
     *
     * There are several arbitrary decisions about when and how to
     * check interrupts in both acquire and await before and/or after
     * blocking. The decisions are less arbitrary in implementation
     * updates because some users appear to rely on original behaviors
     * in ways that are racy and so (rarely) wrong in general but hard
     * to justify changing.
     *
     * Thanks go to Dave Dice, Mark Moir, Victor Luchangco, Bill
     * Scherer and Michael Scott, along with members of JSR-166
     * expert group, for helpful ideas, discussions, and critiques
     * on the design of this class.
     */
        // Node status bits, also used as argument and return values
        const val WAITING: Int = 1 // must be 1
        const val CANCELLED: Int = -0x80000000 // must be negative
        const val COND: Int = 2 // in a condition wait

        /**
         * Wakes up the successor of given node, if one exists, and unsets its
         * WAITING status to avoid park race. This may fail to wake up an
         * eligible thread when one or more have been cancelled, but
         * cancelAcquire ensures liveness.
         */
        private fun signalNext(h: Node?) {
            var s: Node? = null
            if (h != null && (h.next.also { s = it }) != null && s!!.status != 0) {
                s.getAndUnsetStatus(WAITING)
                LockSupport.unpark(s.waiter)
            }
        }

        /** Wakes up the given node if in shared mode  */
        private fun signalNextIfShared(h: Node?) {
            var s: Node? = null
            if (h != null && (h.next.also { s = it }) != null &&
                (s is SharedNode) && s.status != 0
            ) {
                s.getAndUnsetStatus(WAITING)
                LockSupport.unpark(s.waiter)
            }
        }

        // TODO not sure what is this for
        /*init {
            val ensureLoaded: java.lang.Class<*> = LockSupport::class
        }*/
    }
}
