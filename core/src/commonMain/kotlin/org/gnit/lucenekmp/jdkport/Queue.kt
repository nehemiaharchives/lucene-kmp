package org.gnit.lucenekmp.jdkport


/**
 * port of java.util.Queue
 *
 * A collection designed for holding elements prior to processing.
 * Besides basic [Collection] operations, queues provide
 * additional insertion, extraction, and inspection operations.
 * Each of these methods exists in two forms: one throws an exception
 * if the operation fails, the other returns a special value (either
 * `null` or `false`, depending on the operation).  The
 * latter form of the insert operation is designed specifically for
 * use with capacity-restricted `Queue` implementations; in most
 * implementations, insert operations cannot fail.
 *
 * <table class="striped">
 * <caption>Summary of Queue methods</caption>
 * <thead>
 * <tr>
 * <td></td>
 * <th scope="col" style="font-weight:normal; font-style:italic">Throws exception</th>
 * <th scope="col" style="font-weight:normal; font-style:italic">Returns special value</th>
</tr> *
</thead> *
 * <tbody>
 * <tr>
 * <th scope="row">Insert</th>
 * <td>[add(e)][.add]</td>
 * <td>[offer(e)][.offer]</td>
</tr> *
 * <tr>
 * <th scope="row">Remove</th>
 * <td>[remove()][.remove]</td>
 * <td>[poll()][.poll]</td>
</tr> *
 * <tr>
 * <th scope="row">Examine</th>
 * <td>[element()][.element]</td>
 * <td>[peek()][.peek]</td>
</tr> *
</tbody> *
</table> *
 *
 *
 * Queues typically, but do not necessarily, order elements in a
 * FIFO (first-in-first-out) manner.  Among the exceptions are
 * priority queues, which order elements according to a supplied
 * comparator, or the elements' natural ordering, and LIFO queues (or
 * stacks) which order the elements LIFO (last-in-first-out).
 * Whatever the ordering used, the *head* of the queue is that
 * element which would be removed by a call to [.remove] or
 * [.poll].  In a FIFO queue, all new elements are inserted at
 * the *tail* of the queue. Other kinds of queues may use
 * different placement rules.  Every `Queue` implementation
 * must specify its ordering properties.
 *
 *
 * The [offer][.offer] method inserts an element if possible,
 * otherwise returning `false`.  This differs from the [ ][java.util.Collection.add] method, which can fail to
 * add an element only by throwing an unchecked exception.  The
 * `offer` method is designed for use when failure is a normal,
 * rather than exceptional occurrence, for example, in fixed-capacity
 * (or &quot;bounded&quot;) queues.
 *
 *
 * The [.remove] and [.poll] methods remove and
 * return the head of the queue.
 * Exactly which element is removed from the queue is a
 * function of the queue's ordering policy, which differs from
 * implementation to implementation. The `remove()` and
 * `poll()` methods differ only in their behavior when the
 * queue is empty: the `remove()` method throws an exception,
 * while the `poll()` method returns `null`.
 *
 *
 * The [.element] and [.peek] methods return, but do
 * not remove, the head of the queue.
 *
 *
 * The `Queue` interface does not define the *blocking queue
 * methods*, which are common in concurrent programming.  These methods,
 * which wait for elements to appear or for space to become available, are
 * defined in the [java.util.concurrent.BlockingQueue] interface, which
 * extends this interface.
 *
 *
 * `Queue` implementations generally do not allow insertion
 * of `null` elements, although some implementations, such as
 * [LinkedList], do not prohibit insertion of `null`.
 * Even in the implementations that permit it, `null` should
 * not be inserted into a `Queue`, as `null` is also
 * used as a special return value by the `poll` method to
 * indicate that the queue contains no elements.
 *
 *
 * `Queue` implementations generally do not define
 * element-based versions of methods `equals` and
 * `hashCode` but instead inherit the identity based versions
 * from class `Object`, because element-based equality is not
 * always well-defined for queues with the same elements but different
 * ordering properties.
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
interface Queue<E> : MutableCollection<E> {
    /**
     * Inserts the specified element into this queue if it is possible to do so
     * immediately without violating capacity restrictions, returning
     * `true` upon success and throwing an `IllegalStateException`
     * if no space is currently available.
     *
     * @param e the element to add
     * @return `true` (as specified by [Collection.add])
     * @throws IllegalStateException if the element cannot be added at this
     * time due to capacity restrictions
     * @throws ClassCastException if the class of the specified element
     * prevents it from being added to this queue
     * @throws NullPointerException if the specified element is null and
     * this queue does not permit null elements
     * @throws IllegalArgumentException if some property of this element
     * prevents it from being added to this queue
     */
    override fun add(e: E): Boolean

    /**
     * Inserts the specified element into this queue if it is possible to do
     * so immediately without violating capacity restrictions.
     * When using a capacity-restricted queue, this method is generally
     * preferable to [.add], which can fail to insert an element only
     * by throwing an exception.
     *
     * @param e the element to add
     * @return `true` if the element was added to this queue, else
     * `false`
     * @throws ClassCastException if the class of the specified element
     * prevents it from being added to this queue
     * @throws NullPointerException if the specified element is null and
     * this queue does not permit null elements
     * @throws IllegalArgumentException if some property of this element
     * prevents it from being added to this queue
     */
    fun offer(e: E): Boolean

    /**
     * Retrieves and removes the head of this queue.  This method differs
     * from [poll()][.poll] only in that it throws an exception if
     * this queue is empty.
     *
     * @return the head of this queue
     * @throws NoSuchElementException if this queue is empty
     */
    fun remove(): E

    /**
     * Retrieves and removes the head of this queue,
     * or returns `null` if this queue is empty.
     *
     * @return the head of this queue, or `null` if this queue is empty
     */
    fun poll(): E?

    /**
     * Retrieves, but does not remove, the head of this queue.  This method
     * differs from [peek][.peek] only in that it throws an exception
     * if this queue is empty.
     *
     * @return the head of this queue
     * @throws NoSuchElementException if this queue is empty
     */
    fun element(): E

    /**
     * Retrieves, but does not remove, the head of this queue,
     * or returns `null` if this queue is empty.
     *
     * @return the head of this queue, or `null` if this queue is empty
     */
    fun peek(): E?
}
