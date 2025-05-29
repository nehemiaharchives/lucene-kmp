package org.gnit.lucenekmp.jdkport


/**
 * port of java.util.concurrent.BlockingQueue
 *
 * A [Queue] that additionally supports operations that wait for
 * the queue to become non-empty when retrieving an element, and wait
 * for space to become available in the queue when storing an element.
 *
 *
 * `BlockingQueue` methods come in four forms, with different ways
 * of handling operations that cannot be satisfied immediately, but may be
 * satisfied at some point in the future:
 * one throws an exception, the second returns a special value (either
 * `null` or `false`, depending on the operation), the third
 * blocks the current thread indefinitely until the operation can succeed,
 * and the fourth blocks for only a given maximum time limit before giving
 * up.  These methods are summarized in the following table:
 *
 * <table class="plain">
 * <caption>Summary of BlockingQueue methods</caption>
 * <tr>
 * <td></td>
 * <th scope="col" style="font-weight:normal; font-style:italic">Throws exception</th>
 * <th scope="col" style="font-weight:normal; font-style:italic">Special value</th>
 * <th scope="col" style="font-weight:normal; font-style:italic">Blocks</th>
 * <th scope="col" style="font-weight:normal; font-style:italic">Times out</th>
</tr> *
 * <tr>
 * <th scope="row" style="text-align:left">Insert</th>
 * <td>[add(e)][.add]</td>
 * <td>[offer(e)][.offer]</td>
 * <td>[put(e)][.put]</td>
 * <td>[offer(e, time, unit)][.offer]</td>
</tr> *
 * <tr>
 * <th scope="row" style="text-align:left">Remove</th>
 * <td>[remove()][.remove]</td>
 * <td>[poll()][.poll]</td>
 * <td>[take()][.take]</td>
 * <td>[poll(time, unit)][.poll]</td>
</tr> *
 * <tr>
 * <th scope="row" style="text-align:left">Examine</th>
 * <td>[element()][.element]</td>
 * <td>[peek()][.peek]</td>
 * <td style="font-style: italic">not applicable</td>
 * <td style="font-style: italic">not applicable</td>
</tr> *
</table> *
 *
 *
 * A `BlockingQueue` does not accept `null` elements.
 * Implementations throw `NullPointerException` on attempts
 * to `add`, `put` or `offer` a `null`.  A
 * `null` is used as a sentinel value to indicate failure of
 * `poll` operations.
 *
 *
 * A `BlockingQueue` may be capacity bounded. At any given
 * time it may have a `remainingCapacity` beyond which no
 * additional elements can be `put` without blocking.
 * A `BlockingQueue` without any intrinsic capacity constraints always
 * reports a remaining capacity of `Integer.MAX_VALUE`.
 *
 *
 * `BlockingQueue` implementations are designed to be used
 * primarily for producer-consumer queues, but additionally support
 * the [Collection] interface.  So, for example, it is
 * possible to remove an arbitrary element from a queue using
 * `remove(x)`. However, such operations are in general
 * *not* performed very efficiently, and are intended for only
 * occasional use, such as when a queued message is cancelled.
 *
 *
 * `BlockingQueue` implementations are thread-safe.  All
 * queuing methods achieve their effects atomically using internal
 * locks or other forms of concurrency control. However, the
 * *bulk* Collection operations `addAll`,
 * `containsAll`, `retainAll` and `removeAll` are
 * *not* necessarily performed atomically unless specified
 * otherwise in an implementation. So it is possible, for example, for
 * `addAll(c)` to fail (throwing an exception) after adding
 * only some of the elements in `c`.
 *
 *
 * A `BlockingQueue` does *not* intrinsically support
 * any kind of &quot;close&quot; or &quot;shutdown&quot; operation to
 * indicate that no more items will be added.  The needs and usage of
 * such features tend to be implementation-dependent. For example, a
 * common tactic is for producers to insert special
 * *end-of-stream* or *poison* objects, that are
 * interpreted accordingly when taken by consumers.
 *
 *
 *
 * Usage example, based on a typical producer-consumer scenario.
 * Note that a `BlockingQueue` can safely be used with multiple
 * producers and multiple consumers.
 * <pre> `class Producer implements Runnable {
 * private final BlockingQueue queue;
 * Producer(BlockingQueue q) { queue = q; }
 * public void run() {
 * try {
 * while (true) { queue.put(produce()); }
 * } catch (InterruptedException ex) { ... handle ...}
 * }
 * Object produce() { ... }
 * }
 *
 * class Consumer implements Runnable {
 * private final BlockingQueue queue;
 * Consumer(BlockingQueue q) { queue = q; }
 * public void run() {
 * try {
 * while (true) { consume(queue.take()); }
 * } catch (InterruptedException ex) { ... handle ...}
 * }
 * void consume(Object x) { ... }
 * }
 *
 * class Setup {
 * void main() {
 * BlockingQueue q = new SomeQueueImplementation();
 * Producer p = new Producer(q);
 * Consumer c1 = new Consumer(q);
 * Consumer c2 = new Consumer(q);
 * new Thread(p).start();
 * new Thread(c1).start();
 * new Thread(c2).start();
 * }
 * }`</pre>
 *
 *
 * Memory consistency effects: As with other concurrent
 * collections, actions in a thread prior to placing an object into a
 * `BlockingQueue`
 * [*happen-before*](package-summary.html#MemoryVisibility)
 * actions subsequent to the access or removal of that element from
 * the `BlockingQueue` in another thread.
 *
 *
 * This interface is a member of the
 * [
 * Java Collections Framework]({@docRoot}/java.base/java/util/package-summary.html#CollectionsFramework).
 *
 * @since 1.5
 * @author Doug Lea
 * @param <E> the type of elements held in this queue
</E> */
interface BlockingQueue<E> : Queue<E> {
    /**
     * Inserts the specified element into this queue if it is possible to do
     * so immediately without violating capacity restrictions, returning
     * `true` upon success and throwing an
     * `IllegalStateException` if no space is currently available.
     * When using a capacity-restricted queue, it is generally preferable to
     * use [offer][.offer].
     *
     * @param e the element to add
     * @return `true` (as specified by [Collection.add])
     * @throws IllegalStateException if the element cannot be added at this
     * time due to capacity restrictions
     * @throws ClassCastException if the class of the specified element
     * prevents it from being added to this queue
     * @throws NullPointerException if the specified element is null
     * @throws IllegalArgumentException if some property of the specified
     * element prevents it from being added to this queue
     */
    override fun add(e: E): Boolean

    /**
     * Inserts the specified element into this queue if it is possible to do
     * so immediately without violating capacity restrictions, returning
     * `true` upon success and `false` if no space is currently
     * available.  When using a capacity-restricted queue, this method is
     * generally preferable to [.add], which can fail to insert an
     * element only by throwing an exception.
     *
     * @param e the element to add
     * @return `true` if the element was added to this queue, else
     * `false`
     * @throws ClassCastException if the class of the specified element
     * prevents it from being added to this queue
     * @throws NullPointerException if the specified element is null
     * @throws IllegalArgumentException if some property of the specified
     * element prevents it from being added to this queue
     */
    override fun offer(e: E): Boolean

    /**
     * Inserts the specified element into this queue, waiting if necessary
     * for space to become available.
     *
     * @param e the element to add
     * @throws InterruptedException if interrupted while waiting
     * @throws ClassCastException if the class of the specified element
     * prevents it from being added to this queue
     * @throws NullPointerException if the specified element is null
     * @throws IllegalArgumentException if some property of the specified
     * element prevents it from being added to this queue
     */
    suspend fun put(e: E)

    /**
     * Inserts the specified element into this queue, waiting up to the
     * specified wait time if necessary for space to become available.
     *
     * @param e the element to add
     * @param timeout how long to wait before giving up, in units of
     * `unit`
     * @param unit a `TimeUnit` determining how to interpret the
     * `timeout` parameter
     * @return `true` if successful, or `false` if
     * the specified waiting time elapses before space is available
     * @throws InterruptedException if interrupted while waiting
     * @throws ClassCastException if the class of the specified element
     * prevents it from being added to this queue
     * @throws NullPointerException if the specified element is null
     * @throws IllegalArgumentException if some property of the specified
     * element prevents it from being added to this queue
     */
    suspend fun offer(e: E, timeout: Long, unit: TimeUnit): Boolean

    /**
     * Retrieves and removes the head of this queue, waiting if necessary
     * until an element becomes available.
     *
     * @return the head of this queue
     * @throws InterruptedException if interrupted while waiting
     */
    suspend fun take(): E

    /**
     * Retrieves and removes the head of this queue, waiting up to the
     * specified wait time if necessary for an element to become available.
     *
     * @param timeout how long to wait before giving up, in units of
     * `unit`
     * @param unit a `TimeUnit` determining how to interpret the
     * `timeout` parameter
     * @return the head of this queue, or `null` if the
     * specified waiting time elapses before an element is available
     * @throws InterruptedException if interrupted while waiting
     */
    suspend fun poll(timeout: Long, unit: TimeUnit): E?

    /**
     * Returns the number of additional elements that this queue can ideally
     * (in the absence of memory or resource constraints) accept without
     * blocking, or `Integer.MAX_VALUE` if there is no intrinsic
     * limit.
     *
     *
     * Note that you *cannot* always tell if an attempt to insert
     * an element will succeed by inspecting `remainingCapacity`
     * because it may be the case that another thread is about to
     * insert or remove an element.
     *
     * @return the remaining capacity
     */
    fun remainingCapacity(): Int

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
     * @throws ClassCastException if the class of the specified element
     * is incompatible with this queue
     * ([optional]({@docRoot}/java.base/java/util/Collection.html#optional-restrictions))
     * @throws NullPointerException if the specified element is null
     * ([optional]({@docRoot}/java.base/java/util/Collection.html#optional-restrictions))
     */
    override fun remove(o: E): Boolean

    /**
     * Returns `true` if this queue contains the specified element.
     * More formally, returns `true` if and only if this queue contains
     * at least one element `e` such that `o.equals(e)`.
     *
     * @param o object to be checked for containment in this queue
     * @return `true` if this queue contains the specified element
     * @throws ClassCastException if the class of the specified element
     * is incompatible with this queue
     * ([optional]({@docRoot}/java.base/java/util/Collection.html#optional-restrictions))
     * @throws NullPointerException if the specified element is null
     * ([optional]({@docRoot}/java.base/java/util/Collection.html#optional-restrictions))
     */
    override fun contains(o: E): Boolean

    /**
     * Removes all available elements from this queue and adds them
     * to the given collection.  This operation may be more
     * efficient than repeatedly polling this queue.  A failure
     * encountered while attempting to add elements to
     * collection `c` may result in elements being in neither,
     * either or both collections when the associated exception is
     * thrown.  Attempts to drain a queue to itself result in
     * `IllegalArgumentException`. Further, the behavior of
     * this operation is undefined if the specified collection is
     * modified while the operation is in progress.
     *
     * @param c the collection to transfer elements into
     * @return the number of elements transferred
     * @throws UnsupportedOperationException if addition of elements
     * is not supported by the specified collection
     * @throws ClassCastException if the class of an element of this queue
     * prevents it from being added to the specified collection
     * @throws NullPointerException if the specified collection is null
     * @throws IllegalArgumentException if the specified collection is this
     * queue, or some property of an element of this queue prevents
     * it from being added to the specified collection
     */
    fun drainTo(c: MutableCollection<E>): Int

    /**
     * Removes at most the given number of available elements from
     * this queue and adds them to the given collection.  A failure
     * encountered while attempting to add elements to
     * collection `c` may result in elements being in neither,
     * either or both collections when the associated exception is
     * thrown.  Attempts to drain a queue to itself result in
     * `IllegalArgumentException`. Further, the behavior of
     * this operation is undefined if the specified collection is
     * modified while the operation is in progress.
     *
     * @param c the collection to transfer elements into
     * @param maxElements the maximum number of elements to transfer
     * @return the number of elements transferred
     * @throws UnsupportedOperationException if addition of elements
     * is not supported by the specified collection
     * @throws ClassCastException if the class of an element of this queue
     * prevents it from being added to the specified collection
     * @throws NullPointerException if the specified collection is null
     * @throws IllegalArgumentException if the specified collection is this
     * queue, or some property of an element of this queue prevents
     * it from being added to the specified collection
     */
    fun drainTo(c: MutableCollection<E>, maxElements: Int): Int
}
