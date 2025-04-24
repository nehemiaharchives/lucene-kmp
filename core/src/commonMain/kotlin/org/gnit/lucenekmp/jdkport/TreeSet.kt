package org.gnit.lucenekmp.jdkport

import kotlin.Cloneable
import kotlin.jvm.Transient


/**
 * A [NavigableSet] implementation based on a [TreeMap].
 * The elements are ordered using their [natural][Comparable], or by a [Comparator] provided at set creation
 * time, depending on which constructor is used.
 *
 *
 * This implementation provides guaranteed log(n) time cost for the basic
 * operations (`add`, `remove` and `contains`).
 *
 *
 * Note that the ordering maintained by a set (whether or not an explicit
 * comparator is provided) must be *consistent with equals* if it is to
 * correctly implement the `Set` interface.  (See `Comparable`
 * or `Comparator` for a precise definition of *consistent with
 * equals*.)  This is so because the `Set` interface is defined in
 * terms of the `equals` operation, but a `TreeSet` instance
 * performs all element comparisons using its `compareTo` (or
 * `compare`) method, so two elements that are deemed equal by this method
 * are, from the standpoint of the set, equal.  The behavior of a set
 * *is* well-defined even if its ordering is inconsistent with equals; it
 * just fails to obey the general contract of the `Set` interface.
 *
 *
 * **Note that this implementation is not synchronized.**
 * If multiple threads access a tree set concurrently, and at least one
 * of the threads modifies the set, it *must* be synchronized
 * externally.  This is typically accomplished by synchronizing on some
 * object that naturally encapsulates the set.
 * If no such object exists, the set should be "wrapped" using the
 * [Collections.synchronizedSortedSet]
 * method.  This is best done at creation time, to prevent accidental
 * unsynchronized access to the set: <pre>
 * SortedSet s = Collections.synchronizedSortedSet(new TreeSet(...));</pre>
 *
 *
 * The iterators returned by this class's `iterator` method are
 * *fail-fast*: if the set is modified at any time after the iterator is
 * created, in any way except through the iterator's own `remove`
 * method, the iterator will throw a [ConcurrentModificationException].
 * Thus, in the face of concurrent modification, the iterator fails quickly
 * and cleanly, rather than risking arbitrary, non-deterministic behavior at
 * an undetermined time in the future.
 *
 *
 * Note that the fail-fast behavior of an iterator cannot be guaranteed
 * as it is, generally speaking, impossible to make any hard guarantees in the
 * presence of unsynchronized concurrent modification.  Fail-fast iterators
 * throw `ConcurrentModificationException` on a best-effort basis.
 * Therefore, it would be wrong to write a program that depended on this
 * exception for its correctness:   *the fail-fast behavior of iterators
 * should be used only to detect bugs.*
 *
 *
 * The [addFirst][.addFirst] and [addLast][.addLast] methods of this class
 * throw `UnsupportedOperationException`. The encounter order of elements is determined
 * by the comparison method; therefore, explicit positioning is not supported.
 *
 *
 * This class is a member of the
 * [
 * Java Collections Framework]({@docRoot}/java.base/java/util/package-summary.html#CollectionsFramework).
 *
 * @param <E> the type of elements maintained by this set
 *
 * @author  Josh Bloch
 * @see Collection
 *
 * @see Set
 *
 * @see HashSet
 *
 * @see Comparable
 *
 * @see Comparator
 *
 * @see TreeMap
 *
 * @since   1.2
</E> */
class TreeSet<E> internal constructor(m: TreeMap<E, Any>) : AbstractMutableSet<E>(), /*NavigableSet<E>,*/
    Cloneable {
    /**
     * The backing map.
     */
    @Transient
    private var m: TreeMap<E, Any> = m

    /**
     * Constructs a new, empty tree set, sorted according to the
     * natural ordering of its elements.  All elements inserted into
     * the set must implement the [Comparable] interface.
     * Furthermore, all such elements must be *mutually
     * comparable*: `e1.compareTo(e2)` must not throw a
     * `ClassCastException` for any elements `e1` and
     * `e2` in the set.  If the user attempts to add an element
     * to the set that violates this constraint (for example, the user
     * attempts to add a string element to a set whose elements are
     * integers), the `add` call will throw a
     * `ClassCastException`.
     */
    constructor() : this(m = TreeMap<E, Any>())

    /**
     * Constructs a new, empty tree set, sorted according to the specified
     * comparator.  All elements inserted into the set must be *mutually
     * comparable* by the specified comparator: `comparator.compare(e1,
     * e2)` must not throw a `ClassCastException` for any elements
     * `e1` and `e2` in the set.  If the user attempts to add
     * an element to the set that violates this constraint, the
     * `add` call will throw a `ClassCastException`.
     *
     * @param comparator the comparator that will be used to order this set.
     * If `null`, the [natural][Comparable] of the elements will be used.
     */
    constructor(comparator: Comparator<in E>) : this(m = TreeMap<E, Any>(comparator))

    /**
     * Constructs a new tree set containing the elements in the specified
     * collection, sorted according to the *natural ordering* of its
     * elements.  All elements inserted into the set must implement the
     * [Comparable] interface.  Furthermore, all such elements must be
     * *mutually comparable*: `e1.compareTo(e2)` must not throw a
     * `ClassCastException` for any elements `e1` and
     * `e2` in the set.
     *
     * @param c collection whose elements will comprise the new set
     * @throws ClassCastException if the elements in `c` are
     * not [Comparable], or are not mutually comparable
     * @throws NullPointerException if the specified collection is null
     */
    constructor(c: MutableCollection<out E>) : this() {
        addAll(c)
    }

    /**
     * Constructs a new tree set containing the same elements and
     * using the same ordering as the specified sorted set.
     *
     * @param s sorted set whose elements will comprise the new set
     * @throws NullPointerException if the specified sorted set is null
     */
    /*constructor(s: SortedSet<E>) : this(s.comparator()) {
        addAll(s)
    }*/

    /**
     * Returns an iterator over the elements in this set in ascending order.
     *
     * @return an iterator over the elements in this set in ascending order
     */
    /*override fun iterator(): MutableIterator<E> {
        return m.navigableKeySet().iterator()
    }*/

    /**
     * Returns an iterator over the elements in this set in descending order.
     *
     * @return an iterator over the elements in this set in descending order
     * @since 1.6
     */
    /*override fun descendingIterator(): MutableIterator<E> {
        return m.descendingKeySet().iterator()
    }*/

    /**
     * @since 1.6
     */
    /*override fun descendingSet(): NavigableSet<E> {
        return TreeSet<E>(m.descendingMap())
    }*/

    /**
     * Returns the number of elements in this set (its cardinality).
     *
     * @return the number of elements in this set (its cardinality)
     */
    fun size(): Int {
        return m.size
    }

    val isEmpty: Boolean
        /**
         * Returns `true` if this set contains no elements.
         *
         * @return `true` if this set contains no elements
         */
        get() = m.isEmpty()

    /**
     * Returns `true` if this set contains the specified element.
     * More formally, returns `true` if and only if this set
     * contains an element `e` such that
     * `Objects.equals(o, e)`.
     *
     * @param o object to be checked for containment in this set
     * @return `true` if this set contains the specified element
     * @throws ClassCastException if the specified object cannot be compared
     * with the elements currently in the set
     * @throws NullPointerException if the specified element is null
     * and this set uses natural ordering, or its comparator
     * does not permit null elements
     */
    override fun contains(element: E): Boolean {
        return m.containsKey(element)
    }

    override val size: Int
        get() = m.size

    override fun iterator(): MutableIterator<E> {
        return m.keys.iterator()
    }

    /**
     * Adds the specified element to this set if it is not already present.
     * More formally, adds the specified element `e` to this set if
     * the set contains no element `e2` such that
     * `Objects.equals(e, e2)`.
     * If this set already contains the element, the call leaves the set
     * unchanged and returns `false`.
     *
     * @param e element to be added to this set
     * @return `true` if this set did not already contain the specified
     * element
     * @throws ClassCastException if the specified object cannot be compared
     * with the elements currently in this set
     * @throws NullPointerException if the specified element is null
     * and this set uses natural ordering, or its comparator
     * does not permit null elements
     */
    override fun add(e: E): Boolean {
        return m.put(e, PRESENT) == null
    }

    /**
     * Removes the specified element from this set if it is present.
     * More formally, removes an element `e` such that
     * `Objects.equals(o, e)`,
     * if this set contains such an element.  Returns `true` if
     * this set contained the element (or equivalently, if this set
     * changed as a result of the call).  (This set will not contain the
     * element once the call returns.)
     *
     * @param o object to be removed from this set, if present
     * @return `true` if this set contained the specified element
     * @throws ClassCastException if the specified object cannot be compared
     * with the elements currently in this set
     * @throws NullPointerException if the specified element is null
     * and this set uses natural ordering, or its comparator
     * does not permit null elements
     */
    override fun remove(element: E): Boolean {
        return m.remove(element) === PRESENT
    }

    /**
     * Removes all of the elements from this set.
     * The set will be empty after this call returns.
     */
    override fun clear() {
        m.clear()
    }

    /**
     * Adds all of the elements in the specified collection to this set.
     *
     * @param elements collection containing elements to be added to this set
     * @return `true` if this set changed as a result of the call
     * @throws ClassCastException if the elements provided cannot be compared
     * with the elements currently in the set
     * @throws NullPointerException if the specified collection is null or
     * if any element is null and this set uses natural ordering, or
     * its comparator does not permit null elements
     */
    override fun addAll(elements: Collection<E>): Boolean {
        // Use linear-time version if applicable
        // TODO implement/port SortedSet
        /*if (m.size == 0 && elements.size > 0 &&
            elements is SortedSet &&
            m is TreeMap<E, Any>
        ) {
            val set: SortedSet<out E> = elements as SortedSet<out E>
            if (set.comparator() == m.comparator()) {
                m.addAllForTreeSet(set, PRESENT)
                return true
            }
        }*/
        return super.addAll(elements)
    }

    /**
     * @throws ClassCastException {@inheritDoc}
     * @throws NullPointerException if `fromElement` or `toElement`
     * is null and this set uses natural ordering, or its comparator
     * does not permit null elements
     * @throws IllegalArgumentException {@inheritDoc}
     * @since 1.6
     */
    /*override fun subSet(
        fromElement: E, fromInclusive: Boolean,
        toElement: E, toInclusive: Boolean
    ): NavigableSet<E> {
        return TreeSet<E>(
            m.subMap(
                fromElement, fromInclusive,
                toElement, toInclusive
            )
        )
    }*/

    /**
     * @throws ClassCastException {@inheritDoc}
     * @throws NullPointerException if `toElement` is null and
     * this set uses natural ordering, or its comparator does
     * not permit null elements
     * @throws IllegalArgumentException {@inheritDoc}
     * @since 1.6
     */
    /*override fun headSet(toElement: E, inclusive: Boolean): NavigableSet<E> {
        return TreeSet<E>(m.headMap(toElement, inclusive))
    }*/

    /**
     * @throws ClassCastException {@inheritDoc}
     * @throws NullPointerException if `fromElement` is null and
     * this set uses natural ordering, or its comparator does
     * not permit null elements
     * @throws IllegalArgumentException {@inheritDoc}
     * @since 1.6
     */
    /*override fun tailSet(fromElement: E, inclusive: Boolean): NavigableSet<E> {
        return TreeSet<E>(m.tailMap(fromElement, inclusive))
    }*/

    /**
     * @throws ClassCastException {@inheritDoc}
     * @throws NullPointerException if `fromElement` or
     * `toElement` is null and this set uses natural ordering,
     * or its comparator does not permit null elements
     * @throws IllegalArgumentException {@inheritDoc}
     */
    /*override fun subSet(fromElement: E, toElement: E): SortedSet<E> {
        return subSet(fromElement, true, toElement, false)
    }*/

    /**
     * @throws ClassCastException {@inheritDoc}
     * @throws NullPointerException if `toElement` is null
     * and this set uses natural ordering, or its comparator does
     * not permit null elements
     * @throws IllegalArgumentException {@inheritDoc}
     */
    /*override fun headSet(toElement: E): SortedSet<E> {
        return headSet(toElement, false)
    }*/

    /**
     * @throws ClassCastException {@inheritDoc}
     * @throws NullPointerException if `fromElement` is null
     * and this set uses natural ordering, or its comparator does
     * not permit null elements
     * @throws IllegalArgumentException {@inheritDoc}
     */
    /*override fun tailSet(fromElement: E): SortedSet<E> {
        return tailSet(fromElement, true)
    }*/

    fun comparator(): Comparator<in E>? {
        return m.comparator()
    }

    /**
     * @throws NoSuchElementException {@inheritDoc}
     */
    fun first(): E {
        return m.firstKey()
    }

    /**
     * @throws NoSuchElementException {@inheritDoc}
     */
    fun last(): E {
        return m.lastKey()
    }

    // NavigableSet API methods
    /**
     * @throws ClassCastException {@inheritDoc}
     * @throws NullPointerException if the specified element is null
     * and this set uses natural ordering, or its comparator
     * does not permit null elements
     * @since 1.6
     */
    fun lower(e: E): E? {
        return m.lowerKey(e)
    }

    /**
     * @throws ClassCastException {@inheritDoc}
     * @throws NullPointerException if the specified element is null
     * and this set uses natural ordering, or its comparator
     * does not permit null elements
     * @since 1.6
     */
    fun floor(e: E): E? {
        return m.floorKey(e)
    }

    /**
     * @throws ClassCastException {@inheritDoc}
     * @throws NullPointerException if the specified element is null
     * and this set uses natural ordering, or its comparator
     * does not permit null elements
     * @since 1.6
     */
    fun ceiling(e: E): E? {
        return m.ceilingKey(e)
    }

    /**
     * @throws ClassCastException {@inheritDoc}
     * @throws NullPointerException if the specified element is null
     * and this set uses natural ordering, or its comparator
     * does not permit null elements
     * @since 1.6
     */
    fun higher(e: E): E? {
        return m.higherKey(e)
    }

    /**
     * @since 1.6
     */
    fun pollFirst(): E? {
        val e = m.pollFirstEntry()
        return if (e == null) null else e.key
    }

    /**
     * @since 1.6
     */
    fun pollLast(): E? {
        val e = m.pollLastEntry()
        return if (e == null) null else e.key
    }

    /**
     * Throws `UnsupportedOperationException`. The encounter order induced by this
     * set's comparison method determines the position of elements, so explicit positioning
     * is not supported.
     *
     * @throws UnsupportedOperationException always
     * @since 21
     */
    fun addFirst(e: E) {
        throw UnsupportedOperationException()
    }

    /**
     * Throws `UnsupportedOperationException`. The encounter order induced by this
     * set's comparison method determines the position of elements, so explicit positioning
     * is not supported.
     *
     * @throws UnsupportedOperationException always
     * @since 21
     */
    fun addLast(e: E) {
        throw UnsupportedOperationException()
    }

    /**
     * Returns a shallow copy of this `TreeSet` instance. (The elements
     * themselves are not cloned.)
     *
     * @return a shallow copy of this set
     */
    public override fun clone(): Any {
        val clone: TreeSet<E>
        try {
            clone = super.clone() as TreeSet<E>
        } catch (e: /*java.lang.CloneNotSupported*/Exception) {
            throw /*java.lang.Internal*/Error(e)
        }

        clone.m = TreeMap(m)
        return clone
    }

    /**
     * Save the state of the `TreeSet` instance to a stream (that is,
     * serialize it).
     *
     * @serialData Emits the comparator used to order this set, or
     * `null` if it obeys its elements' natural ordering
     * (Object), followed by the size of the set (the number of
     * elements it contains) (int), followed by all of its
     * elements (each an Object) in order (as determined by the
     * set's Comparator, or by the elements' natural ordering if
     * the set has no Comparator).
     */
    /*@Throws(IOException::class)
    private fun writeObject(s: ObjectOutputStream) {
        // Write out any hidden stuff
        s.defaultWriteObject()

        // Write out Comparator
        s.writeObject(m.comparator())

        // Write out size
        s.writeInt(m.size)

        // Write out all elements in the proper order.
        for (e in m.keys) s.writeObject(e)
    }*/

    /**
     * Reconstitute the `TreeSet` instance from a stream (that is,
     * deserialize it).
     */
    /*@Throws(IOException::class, java.lang.ClassNotFoundException::class)
    private fun readObject(s: ObjectInputStream) {
        // Read in any hidden stuff
        s.defaultReadObject()

        // Read in Comparator
        val c: Comparator<in E> = s.readObject() as Comparator<in E>

        // Create backing TreeMap
        val tm: TreeMap<E, Any> = TreeMap<E, Any>(c)
        m = tm

        // Read in size
        val size: Int = s.readInt()

        tm.readTreeSet(size, s, PRESENT)
    }*/

    /**
     * Creates a *[late-binding](Spliterator.html#binding)*
     * and *fail-fast* [Spliterator] over the elements in this
     * set.
     *
     *
     * The `Spliterator` reports [Spliterator.SIZED],
     * [Spliterator.DISTINCT], [Spliterator.SORTED], and
     * [Spliterator.ORDERED].  Overriding implementations should document
     * the reporting of additional characteristic values.
     *
     *
     * The spliterator's comparator (see
     * [java.util.Spliterator.getComparator]) is `null` if
     * the tree set's comparator (see [.comparator]) is `null`.
     * Otherwise, the spliterator's comparator is the same as or imposes the
     * same total ordering as the tree set's comparator.
     *
     * @return a `Spliterator` over the elements in this set
     * @since 1.8
     */
    /*override fun spliterator(): java.util.Spliterator<E> {
        return TreeMap.keySpliteratorFor<E>(m)
    }*/

    /**
     * Constructs a set backed by the specified navigable map.
     */
    init {
        this.m = m
    }

    companion object {
        // Dummy value to associate with an Object in the backing Map
        private val PRESENT = Any()
    }
}
