package org.gnit.lucenekmp.jdkport

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndDecrement
import kotlin.concurrent.atomics.fetchAndIncrement
import kotlin.jvm.Transient
import kotlin.math.min
import kotlin.time.DurationUnit
import kotlin.time.toDuration

open class LinkedBlockingQueue<E>(capacity: Int = Int.MAX_VALUE) :
    AbstractQueue<E>(), BlockingQueue<E> {

    /**
     * A variant of the "two lock queue" algorithm.  The putLock gates
     * entry to put (and offer), and has an associated condition for
     * waiting puts.  Similarly for the takeLock.  The "count" field
     * that they both rely on is maintained as an atomic to avoid
     * needing to get both locks in most cases. Also, to minimize need
     * for puts to get takeLock and vice-versa, cascading notifies are
     * used. When a put notices that it has enabled at least one take,
     * it signals taker. That taker in turn signals others if more
     * items have been entered since the signal. And symmetrically for
     * takes signalling puts. Operations such as remove(Object) and
     * iterators acquire both locks.
     *
     * Visibility between writers and readers is provided as follows:
     *
     * Whenever an element is enqueued, the putLock is acquired and
     * count updated.  A subsequent reader guarantees visibility to the
     * enqueued Node by either acquiring the putLock (via fullyLock)
     * or by acquiring the takeLock, and then reading n = count.get();
     * this gives visibility to the first n items.
     *
     * To implement weakly consistent iterators, it appears we need to
     * keep all Nodes GC-reachable from a predecessor dequeued Node.
     * That would cause two problems:
     * - allow a rogue Iterator to cause unbounded memory retention
     * - cause cross-generational linking of old Nodes to new Nodes if
     *   a Node was tenured while live, which generational GCs have a
     *   hard time dealing with, causing repeated major collections.
     * However, only non-deleted Nodes need to be reachable from
     * dequeued Nodes, and reachability does not necessarily have to
     * be of the kind understood by the GC.  We use the trick of
     * linking a Node that has just been dequeued to itself.  Such a
     * self-link implicitly means to advance to head.next.
     */
    /**
     * Linked list node class.
     */
    class Node<E>(var item: E?) {
        /**
         * One of:
         * - the real successor Node
         * - this Node, meaning the successor is head.next
         * - null, meaning there is no successor (this is the last node)
         */
        var next: Node<E>? = null
    }

    /** The capacity bound, or Integer.MAX_VALUE if none  */
    private val capacity: Int

    /** Current number of elements  */
    @OptIn(ExperimentalAtomicApi::class)
    private val count: AtomicInteger = AtomicInteger(0)

    /**
     * Head of linked list.
     * Invariant: head.item == null
     */
    @Transient
    var head: Node<E>?

    /**
     * Tail of linked list.
     * Invariant: last.next == null
     */
    @Transient
    private var last: Node<E>

    // Channels to signal not empty/full
    private val notEmptyCh = Channel<Unit>(capacity)
    private val notFullCh = Channel<Unit>(capacity)

    /**
     * Links node at end of queue.
     *
     * @param node the node
     */
    private fun enqueue(node: Node<E>) {
        // assert last.next == null;
        last.next = node
        last = last.next!!
    }

    /**
     * Removes a node from head of queue.
     *
     * @return the node
     */
    private fun dequeue(): E? {
        // assert head.item == null;
        val h = head
        val first = h!!.next
        h.next = h // help GC
        head = first
        val x = first!!.item
        first.item = null
        return x
    }

    /**
     * Creates a `LinkedBlockingQueue` with the given (fixed) capacity.
     *
     * @param capacity the capacity of this queue
     * @throws IllegalArgumentException if `capacity` is not greater
     * than zero
     */
    /**
     * Creates a `LinkedBlockingQueue` with a capacity of
     * [Integer.MAX_VALUE].
     */
    init {
        require(capacity > 0)
        this.capacity = capacity
        head = Node<E>(null)
        last = head!!
    }

    /**
     * Creates a `LinkedBlockingQueue` with a capacity of
     * [Integer.MAX_VALUE], initially containing the elements of the
     * given collection,
     * added in traversal order of the collection's iterator.
     *
     * @param c the collection of elements to initially contain
     * @throws NullPointerException if the specified collection or any
     * of its elements are null
     */
    @OptIn(ExperimentalAtomicApi::class)
    constructor(c: MutableCollection<E>) : this(Int.Companion.MAX_VALUE) {
        var n = 0
        for (e in c) {
            check(n != capacity) { "Queue full" }
            enqueue(Node<E>(e))
            ++n
        }
        count.set(n)
    }

    // this doc comment is overridden to remove the reference to collections
    // greater in size than Integer.MAX_VALUE
    /**
     * Returns the number of elements in this queue.
     *
     * @return the number of elements in this queue
     */
    @OptIn(ExperimentalAtomicApi::class)
    override val size: Int
        get() = count.get()

    // this doc comment is a modified copy of the inherited doc comment,
    // without the reference to unlimited queues.
    /**
     * Returns the number of additional elements that this queue can ideally
     * (in the absence of memory or resource constraints) accept without
     * blocking. This is always equal to the initial capacity of this queue
     * less the current `size` of this queue.
     *
     *
     * Note that you *cannot* always tell if an attempt to insert
     * an element will succeed by inspecting `remainingCapacity`
     * because it may be the case that another thread is about to
     * insert or remove an element.
     */
    @OptIn(ExperimentalAtomicApi::class)
    override fun remainingCapacity(): Int {
        return capacity - count.get()
    }

    /**
     * Inserts the specified element at the tail of this queue, waiting if
     * necessary for space to become available.
     *
     * @throws InterruptedException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    @OptIn(ExperimentalAtomicApi::class)
    override suspend fun put(e: E) {
        val c: Int
        val node = Node(e)
        while (true) {
            if (count.get() < capacity) {
                enqueue(node)
                val c = count.fetchAndIncrement()
                if (c + 1 < capacity) notFullCh.trySend(Unit)
                if (c == 0) notEmptyCh.trySend(Unit)
                return
            } else {
                notFullCh.receive()
            }
        }
    }

    /**
     * Inserts the specified element at the tail of this queue, waiting if
     * necessary up to the specified wait time for space to become available.
     *
     * @return `true` if successful, or `false` if
     * the specified waiting time elapses before space is available
     * @throws InterruptedException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    @OptIn(ExperimentalAtomicApi::class)
    override suspend fun offer(e: E, timeout: Long, unit: TimeUnit): Boolean {
        val deadline = System.nanoTime() + unit.toNanos(timeout)
        val node = Node(e)
        while (true) {
            if (count.get() < capacity) {
                enqueue(node)
                val c = count.fetchAndIncrement()
                if (c + 1 < capacity) notFullCh.trySend(Unit)
                if (c == 0) notEmptyCh.trySend(Unit)
                return true
            } else {
                val now = System.nanoTime()
                if (now >= deadline) return false
                val remaining = deadline - now
                withTimeoutOrNull(remaining.toDuration(DurationUnit.NANOSECONDS)) {
                    notFullCh.receive()
                } ?: return false
            }
        }
    }

    /**
     * Inserts the specified element at the tail of this queue if it is
     * possible to do so immediately without exceeding the queue's capacity,
     * returning `true` upon success and `false` if this queue
     * is full.
     * When using a capacity-restricted queue, this method is generally
     * preferable to method [add][BlockingQueue.add], which can fail to
     * insert an element only by throwing an exception.
     *
     * @throws NullPointerException if the specified element is null
     */
    @OptIn(ExperimentalAtomicApi::class)
    override fun offer(e: E): Boolean {
        val count: AtomicInteger = this.count
        if (count.get() == capacity) return false
        val node = Node(e)
        enqueue(node)
        val c = count.fetchAndIncrement()
        if (c + 1 < capacity) notFullCh.trySendBlocking(Unit)
        if (c == 0) notEmptyCh.trySendBlocking(Unit)
        return true
    }

    @OptIn(ExperimentalAtomicApi::class)
    override suspend fun take(): E {
        while (true) {
            if (count.get() > 0) {
                val x = dequeue()
                val c = count.fetchAndDecrement()
                if (c > 1) notEmptyCh.trySend(Unit)
                if (c == capacity) notFullCh.trySend(Unit)
                return x!!
            } else {
                notEmptyCh.receive()
            }
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    override suspend fun poll(timeout: Long, unit: TimeUnit): E? {
        val deadline = System.nanoTime() + unit.toNanos(timeout)
        while (true) {
            if (count.get() > 0) {
                val x = dequeue()
                val c = count.fetchAndDecrement()
                if (c > 1) notEmptyCh.trySend(Unit)
                if (c == capacity) notFullCh.trySend(Unit)
                return x
            } else {
                val now = System.nanoTime()
                if (now >= deadline) return null
                val remaining = deadline - now
                withTimeoutOrNull(remaining.toDuration(DurationUnit.NANOSECONDS)) {
                    notEmptyCh.receive()
                } ?: return null
            }
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    override fun poll(): E? {
        val count: AtomicInteger = this.count

        if (count.get() == 0) return null

        val x = dequeue()
        val c = count.fetchAndDecrement()

        if (c > 1) notEmptyCh.trySendBlocking(Unit)

        if (c == capacity) notFullCh.trySendBlocking(Unit)

        return x
    }

    @OptIn(ExperimentalAtomicApi::class)
    override fun peek(): E? {
        val count: AtomicInteger = this.count
        if (count.get() == 0) return null
        return head!!.next!!.item
    }

    /**
     * Unlinks interior Node p with predecessor pred.
     */
    @OptIn(ExperimentalAtomicApi::class)
    fun unlink(p: Node<E>, pred: Node<E>) {
        // p.next is not changed, to allow iterators that are
        // traversing p to maintain their weak-consistency guarantee.
        p.item = null
        pred.next = p.next
        if (last === p) last = pred
        if (count.fetchAndDecrement() == capacity) notFullCh.trySend(Unit)
    }

    /**
     * Removes a single instance of the specified element from this queue,
     * if it is present.  More formally, removes an element `e` such
     * that `o.equals(e)`, if this queue contains one or more such
     * elements.
     * Returns `true` if this queue contained the specified element
     * (or equivalently, if this queue changed as a result of the call).
     *
     * @param o element to be removed from this queue, if present
     * @return `true` if this queue changed as a result of the call
     */
    override fun remove(o: E): Boolean {
        if (o == null) return false
        var pred = head
        var p = pred!!.next
        while (p != null
        ) {
            if (o == p.item) {
                unlink(p, pred!!)
                return true
            }
            pred = p
            p = p.next
        }
        return false
    }

    /**
     * Returns `true` if this queue contains the specified element.
     * More formally, returns `true` if and only if this queue contains
     * at least one element `e` such that `o.equals(e)`.
     *
     * @param o object to be checked for containment in this queue
     * @return `true` if this queue contains the specified element
     */
    override fun contains(o: E): Boolean {
        if (o == null) return false
        var p = head!!.next
        while (p != null) {
            if (o == p.item) return true
            p = p.next
        }
        return false
    }

    /**
     * Returns an array containing all of the elements in this queue, in
     * proper sequence.
     *
     *
     * The returned array will be "safe" in that no references to it are
     * maintained by this queue.  (In other words, this method must allocate
     * a new array).  The caller is thus free to modify the returned array.
     *
     *
     * This method acts as bridge between array-based and collection-based
     * APIs.
     *
     * @return an array containing all of the elements in this queue
     */
    /*@OptIn(ExperimentalAtomicApi::class)
    override fun toArray(): Array<E> {
        val size = count.get()
        val a = kotlin.arrayOfNulls<E>(size)
        var k = 0
        var p = head!!.next
        while (p != null) {
            a[k++] = p.item
            p = p.next
        }
        return a
    }*/

    /**
     * Returns an array containing all of the elements in this queue, in
     * proper sequence; the runtime type of the returned array is that of
     * the specified array.  If the queue fits in the specified array, it
     * is returned therein.  Otherwise, a new array is allocated with the
     * runtime type of the specified array and the size of this queue.
     *
     *
     * If this queue fits in the specified array with room to spare
     * (i.e., the array has more elements than this queue), the element in
     * the array immediately following the end of the queue is set to
     * `null`.
     *
     *
     * Like the [.toArray] method, this method acts as bridge between
     * array-based and collection-based APIs.  Further, this method allows
     * precise control over the runtime type of the output array, and may,
     * under certain circumstances, be used to save allocation costs.
     *
     *
     * Suppose `x` is a queue known to contain only strings.
     * The following code can be used to dump the queue into a newly
     * allocated array of `String`:
     *
     * <pre> `String[] y = x.toArray(new String[0]);`</pre>
     *
     * Note that `toArray(new Object[0])` is identical in function to
     * `toArray()`.
     *
     * @param a the array into which the elements of the queue are to
     * be stored, if it is big enough; otherwise, a new array of the
     * same runtime type is allocated for this purpose
     * @return an array containing all of the elements in this queue
     * @throws ArrayStoreException if the runtime type of the specified array
     * is not a supertype of the runtime type of every element in
     * this queue
     * @throws NullPointerException if the specified array is null
     */
    /*@OptIn(ExperimentalAtomicApi::class)
    override fun <T> toArray(a: Array<T>): Array<T> {
        var a = a
        val size = count.get()
        if (a.size < size) a =
            java.lang.reflect.Array.newInstance(a.javaClass.getComponentType(), size) as Array<T>

        var k = 0
        var p = head!!.next
        while (p != null) {
            a[k++] = p.item as T
            p = p.next
        }
        if (a.size > k) a[k] = null
        return a
    }*/

    override fun toString(): String {
        return /*java.util.concurrent.Helpers.collectionToString(this)*/ this.joinToString(
            prefix = "[",
            postfix = "]",
            separator = ", ",
            transform = { it.toString() }
        )
    }

    /**
     * Atomically removes all of the elements from this queue.
     * The queue will be empty after this call returns.
     */
    @OptIn(ExperimentalAtomicApi::class)
    override fun clear() {
        var p: Node<E>?
        var h = head
        while ((h!!.next.also { p = it }) != null) {
            h.next = h
            p!!.item = null
            h = p
        }
        head = last
        // assert head.item == null && head.next == null;
        if (count.exchange(0) == capacity) notFullCh.trySend(Unit)
    }

    /**
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     * @throws IllegalArgumentException      {@inheritDoc}
     */
    override fun drainTo(c: MutableCollection<E>): Int {
        return drainTo(c, Int.Companion.MAX_VALUE)
    }

    /**
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     * @throws IllegalArgumentException      {@inheritDoc}
     */
    @OptIn(ExperimentalAtomicApi::class)
    override fun drainTo(c: MutableCollection<E>, maxElements: Int): Int {
        require(c !== this)
        if (maxElements <= 0) return 0
        val n = min(maxElements, count.get())
        // count.get provides visibility to first n Nodes
        var h = head
        var i = 0
        try {
            while (i < n) {
                val p = h!!.next
                c.add(p!!.item!!)
                p.item = null
                h.next = h
                h = p
                ++i
            }
            return n
        } finally {
            // Restore invariants even if c.add() threw
            if (i > 0) {
                // assert h.item == null;
                head = h
                if (count.fetchAndAdd(-i) == capacity) notFullCh.trySend(Unit)
            }
        }
    }

    /**
     * Used for any element traversal that is not entirely under lock.
     * Such traversals must handle both:
     * - dequeued nodes (p.next == p)
     * - (possibly multiple) interior removed nodes (p.item == null)
     */
    fun succ(p: Node<E>): Node<E> {
        var p = p
        if (p === (p.next.also { p = it!! })) p = head!!.next!!
        return p
    }

    /**
     * Returns an iterator over the elements in this queue in proper sequence.
     * The elements will be returned in order from first (head) to last (tail).
     *
     *
     * The returned iterator is
     * [*weakly consistent*](package-summary.html#Weakly).
     *
     * @return an iterator over the elements in this queue in proper sequence
     */
    override fun iterator(): MutableIterator<E> {
        return this.Itr()
    }

    /**
     * Weakly-consistent iterator.
     *
     * Lazily updated ancestor field provides expected O(1) remove(),
     * but still O(n) in the worst case, whenever the saved ancestor
     * is concurrently deleted.
     */
    private inner class Itr : MutableIterator<E> {
        private var next: Node<E>? = null // Node holding nextItem
        private var nextItem: E? = null // next item to hand out
        private var lastRet: Node<E>? = null
        private var ancestor: Node<E>? = null // Helps unlink lastRet on remove()

        init {
            if ((head!!.next.also { next = it }) != null) nextItem = next!!.item
        }

        override fun hasNext(): Boolean {
            return next != null
        }

        override fun next(): E {
            var p: Node<E>?
            if ((next.also { p = it }) == null) throw NoSuchElementException()
            lastRet = p
            val x = nextItem
            var e: E? = null
            p = p!!.next
            while (p != null && (p.item.also { e = it }) == null) {
                p = succ(p)
            }
            next = p
            nextItem = e
            return x!!
        }

        fun forEachRemaining(action: (E) -> Unit /*java.util.function.Consumer<in E>*/) {
            // A variant of forEachFrom
            var p: Node<E>?
            if ((next.also { p = it }) == null) return
            lastRet = p
            next = null
            val batchSize = 64
            var es: Array<E?>? = null
            var n: Int
            var len = 1
            do {
                if (es == null) {
                    p = p!!.next
                    var q = p
                    while (q != null) {
                        if (q.item != null && ++len == batchSize) break
                        q = succ(q)
                    }
                    es = kotlin.arrayOfNulls(len)
                    es[0] = nextItem
                    nextItem = null
                    n = 1
                } else n = 0
                while (p != null && n < len) {
                    if ((p.item.also { es[n] = it }) != null) {
                        lastRet = p
                        n++
                    }
                    p = succ(p)
                }
                for (i in 0..<n) {
                    val e = es[i] as E
                    action(e)
                }
            } while (n > 0 && p != null)
        }

        override fun remove() {
            val p = lastRet
            checkNotNull(p)
            lastRet = null
            if (p.item != null) {
                if (ancestor == null) ancestor = head
                ancestor = findPred(p, ancestor!!)
                unlink(p, ancestor!!)
            }
        }
    }

    /**
     * A customized variant of Spliterators.IteratorSpliterator.
     * Keep this class in sync with (very similar) LBDSpliterator.
     */
    private inner class LBQSpliterator : Spliterator<E> {

        private val MAX_BATCH: Int = 1 shl 25 // max batch array size;

        var current: Node<E>? = null // current node; null until initialized
        var batch: Int = 0 // batch size for splits
        var exhausted: Boolean = false // true when no more nodes
        var est: Long = size.toLong() // size estimate

        override fun estimateSize(): Long {
            return est
        }

        override fun trySplit(): Spliterator<E>? {
            var h: Node<E>? = null
            if (!exhausted &&
                ((current.also { h = it }) != null || (head!!.next.also { h = it }) != null)
                && h!!.next != null
            ) {
                batch = min(batch + 1, MAX_BATCH)
                val n = batch
                val a = kotlin.arrayOfNulls<Any?>(n) as Array<E?>
                var i = 0
                var p = current

                if (p != null || (head!!.next.also { p = it }) != null) while (p != null && i < n) {
                    if ((p.item.also { a[i] = it }) != null) i++
                    p = succ(p)
                }

                if ((p.also { current = it }) == null) {
                    est = 0L
                    exhausted = true
                } else if ((i.let { est -= it; est }) < 0L) est = 0L
                if (i > 0) return Spliterators.spliterator(
                    a, 0, i, (Spliterator.ORDERED or
                            Spliterator.NONNULL or
                            Spliterator.CONCURRENT)
                )
            }
            return null
        }

        override fun tryAdvance(action: (E) -> Unit /*java.util.function.Consumer<in E>*/): Boolean {
            if (!exhausted) {
                var e: E? = null

                var p: Node<E>?
                if ((current.also { p = it }) != null || (head!!.next.also { p = it }) != null) do {
                    e = p!!.item
                    p = succ(p)
                } while (e == null && p != null)
                if ((p.also { current = it }) == null) exhausted = true

                if (e != null) {
                    action(e)
                    return true
                }
            }
            return false
        }

        override fun forEachRemaining(action: (E) -> Unit /*java.util.function.Consumer<in E>*/) {
            if (!exhausted) {
                exhausted = true
                val p = current
                current = null
                forEachFrom(action, p)
            }
        }

        override fun characteristics(): Int {
            return (Spliterator.ORDERED or
                    Spliterator.NONNULL or
                    Spliterator.CONCURRENT)
        }

    }

    /**
     * Returns a [Spliterator] over the elements in this queue.
     *
     *
     * The returned spliterator is
     * [*weakly consistent*](package-summary.html#Weakly).
     *
     *
     * The `Spliterator` reports [Spliterator.CONCURRENT],
     * [Spliterator.ORDERED], and [Spliterator.NONNULL].
     *
     * @implNote
     * The `Spliterator` implements `trySplit` to permit limited
     * parallelism.
     *
     * @return a `Spliterator` over the elements in this queue
     * @since 1.8
     */
    fun spliterator(): Spliterator<E> {
        return this.LBQSpliterator()
    }

    /**
     * @throws NullPointerException {@inheritDoc}
     */
    fun forEach(action: (E) -> Unit /*java.util.function.Consumer<in E>*/) {
        forEachFrom(action, null)
    }

    /**
     * Runs action on each element found during a traversal starting at p.
     * If p is null, traversal starts at head.
     */
    fun forEachFrom(action: (E) -> Unit /*java.util.function.Consumer<in E>*/, p: Node<E>?) {
        // Extract batches of elements while holding the lock; then
        // run the action on the elements while not
        var p = p
        val batchSize = 64 // max number of elements per batch
        var es: Array<E?>? = null // container for batch of elements
        var n: Int
        var len = 0
        do {
            if (es == null) {
                if (p == null) p = head!!.next
                var q = p
                while (q != null) {
                    if (q.item != null && ++len == batchSize) break
                    q = succ(q)
                }
                es = arrayOfNulls(len)
            }
            n = 0
            while (p != null && n < len) {
                if ((p.item.also { es[n] = it }) != null) n++
                p = succ(p)
            }
            for (i in 0..<n) {
                val e = es[i] as E
                action(e)
            }
        } while (n > 0 && p != null)
    }

    /**
     * @throws NullPointerException {@inheritDoc}
     */
    fun removeIf(filter: (E?) -> Boolean /*java.util.function.Predicate<in E>*/): Boolean {
        return bulkRemove(filter)
    }

    /**
     * @throws NullPointerException {@inheritDoc}
     */
    override fun removeAll(c: Collection<E>): Boolean {
        return bulkRemove { e: E? -> c.contains(e) }
    }

    /**
     * @throws NullPointerException {@inheritDoc}
     */
    override fun retainAll(c: Collection<E>): Boolean {
        return bulkRemove { e: E? -> !c.contains(e) }
    }

    /**
     * Returns the predecessor of live node p, given a node that was
     * once a live ancestor of p (or head); allows unlinking of p.
     */
    fun findPred(p: Node<E>?, ancestor: Node<E>): Node<E> {
        // assert p.item != null;
        var ancestor = ancestor
        if (ancestor.item == null) ancestor = head!!
        // Fails with NPE if precondition not satisfied
        var q: Node<E>?
        while ((ancestor.next.also { q = it }) !== p) {
            ancestor = q!!
        }
        return ancestor
    }

    /** Implementation of bulk remove methods.  */
    private fun bulkRemove(filter: (E?) -> Boolean /*java.util.function.Predicate<in E>*/): Boolean {
        var removed = false
        var p: Node<E>? = null
        var ancestor = head
        var nodes: Array<Node<E>?>? = null
        var n: Int
        var len = 0
        do {
            // 1. Extract batch of up to 64 elements while holding the lock.
            if (nodes == null) {  // first batch; initialize
                p = head!!.next
                var q = p
                while (q != null) {
                    if (q.item != null && ++len == 64) break
                    q = succ(q)
                }
                nodes = kotlin.arrayOfNulls<Node<E>?>(len)
            }
            n = 0
            while (p != null && n < len) {
                nodes[n++] = p
                p = succ(p)
            }

            // 2. Run the filter on the elements while lock is free.
            var deathRow = 0L // "bitset" of size 64
            for (i in 0..<n) {
                val e: E?
                if ((nodes[i]!!.item.also { e = it }) != null && filter(e)) deathRow = deathRow or (1L shl i)
            }

            // 3. Remove any filtered elements while holding the lock.
            if (deathRow != 0L) {
                for (i in 0..<n) {
                    var q: Node<E>? = null
                    if ((deathRow and (1L shl i)) != 0L
                        && (nodes[i].also { q = it })!!.item != null
                    ) {
                        ancestor = findPred(q, ancestor!!)
                        unlink(q!!, ancestor)
                        removed = true
                    }
                    nodes[i] = null // help GC
                }
            }
        } while (n > 0 && p != null)
        return removed
    }

    /**
     * Saves this queue to a stream (that is, serializes it).
     *
     * @param s the stream
     * @throws IOException if an I/O error occurs
     * @serialData The capacity is emitted (int), followed by all of
     * its elements (each an `Object`) in the proper order,
     * followed by a null
     */
    /*@Throws(IOException::class)
    private fun writeObject(s: ObjectOutputStream) {
        // Write out any hidden stuff, plus capacity
        s.defaultWriteObject()

        // Write out all elements in the proper order.
        var p = head!!.next
        while (p != null) {
            s.writeObject(p.item)
            p = p.next
        }

        // Use trailing null as sentinel
        s.writeObject(null)
    }*/

    /**
     * Reconstitutes this queue from a stream (that is, deserializes it).
     * @param s the stream
     * @throws ClassNotFoundException if the class of a serialized object
     * could not be found
     * @throws IOException if an I/O error occurs
     */
    /*@Throws(IOException::class, java.lang.ClassNotFoundException::class)
    private fun readObject(s: ObjectInputStream) {
        // Read in capacity, and any hidden stuff
        s.defaultReadObject()

        count.set(0)
        head = Node<E>(null)
        last = head!!

        // Read in all elements and place in queue
        while (true) {
            val item = s.readObject() as E
            if (item == null) break
            add(item)
        }
    }*/
}
