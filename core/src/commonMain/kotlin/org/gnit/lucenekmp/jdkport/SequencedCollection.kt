package org.gnit.lucenekmp.jdkport


/**
 * A collection that has a well-defined encounter order, that supports operations at both ends,
 * and that is reversible. The elements of a sequenced collection have an <a id="encounter">
 * *encounter order*</a>, where conceptually the elements have a linear arrangement
 * from the first element to the last element. Given any two elements, one element is
 * either before (closer to the first element) or after (closer to the last element)
 * the other element.
 *
 *
 * (Note that this definition does not imply anything about physical positioning
 * of elements, such as their locations in a computer's memory.)
 *
 *
 * Several methods inherited from the [Collection] interface are required to operate
 * on elements according to this collection's encounter order. For instance, the
 * [iterator][Collection.iterator] method provides elements starting from the first element,
 * proceeding through successive elements, until the last element. Other methods that are
 * required to operate on elements in encounter order include the following:
 * [forEach][Iterable.forEach], [parallelStream][Collection.parallelStream],
 * [spliterator][Collection.spliterator], [stream][Collection.stream],
 * and all overloads of the [toArray][Collection.toArray] method.
 *
 *
 * This interface provides methods to add, retrieve, and remove elements at either end
 * of the collection.
 *
 *
 * This interface also defines the [reversed][.reversed] method, which provides
 * a reverse-ordered [view](Collection.html#view) of this collection.
 * In the reverse-ordered view, the concepts of first and last are inverted, as are
 * the concepts of successor and predecessor. The first element of this collection is
 * the last element of the reverse-ordered view, and vice-versa. The successor of some
 * element in this collection is its predecessor in the reversed view, and vice-versa. All
 * methods that respect the encounter order of the collection operate as if the encounter order
 * is inverted. For instance, the [.iterator] method of the reversed view reports the
 * elements in order from the last element of this collection to the first. The availability of
 * the `reversed` method, and its impact on the ordering semantics of all applicable
 * methods, allow convenient iteration, searching, copying, and streaming of the elements of
 * this collection in either forward order or reverse order.
 *
 *
 * This class is a member of the
 * [
 * Java Collections Framework]({@docRoot}/java.base/java/util/package-summary.html#CollectionsFramework).
 *
 * @apiNote
 * This interface does not impose any requirements on the `equals` and `hashCode`
 * methods, because requirements imposed by sub-interfaces [List] and [SequencedSet]
 * (which inherits requirements from [Set]) would be in conflict. See the specifications for
 * [Collection.equals] and [Collection.hashCode]
 * for further information.
 *
 * @param <E> the type of elements in this collection
 * @since 21
</E> */
interface SequencedCollection<E> : MutableCollection<E> {
    /**
     * Returns a reverse-ordered [view](Collection.html#view) of this collection.
     * The encounter order of elements in the returned view is the inverse of the encounter
     * order of elements in this collection. The reverse ordering affects all order-sensitive
     * operations, including those on the view collections of the returned view. If the collection
     * implementation permits modifications to this view, the modifications "write through" to the
     * underlying collection. Changes to the underlying collection might or might not be visible
     * in this reversed view, depending upon the implementation.
     *
     * @return a reverse-ordered view of this collection
     */
    fun reversed(): SequencedCollection<E>?

    /**
     * Adds an element as the first element of this collection (optional operation).
     * After this operation completes normally, the given element will be a member of
     * this collection, and it will be the first element in encounter order.
     *
     * @implSpec
     * The implementation in this interface always throws `UnsupportedOperationException`.
     *
     * @param e the element to be added
     * @throws NullPointerException if the specified element is null and this
     * collection does not permit null elements
     * @throws UnsupportedOperationException if this collection implementation
     * does not support this operation
     */
    fun addFirst(e: E) {
        throw UnsupportedOperationException()
    }

    /**
     * Adds an element as the last element of this collection (optional operation).
     * After this operation completes normally, the given element will be a member of
     * this collection, and it will be the last element in encounter order.
     *
     * @implSpec
     * The implementation in this interface always throws `UnsupportedOperationException`.
     *
     * @param e the element to be added.
     * @throws NullPointerException if the specified element is null and this
     * collection does not permit null elements
     * @throws UnsupportedOperationException if this collection implementation
     * does not support this operation
     */
    fun addLast(e: E) {
        throw UnsupportedOperationException()
    }

    val first: E?
        /**
         * Gets the first element of this collection.
         *
         * @implSpec
         * The implementation in this interface obtains an iterator of this collection, and
         * then it obtains an element by calling the iterator's `next` method. Any
         * `NoSuchElementException` thrown is propagated. Otherwise, it returns
         * the element.
         *
         * @return the retrieved element
         * @throws NoSuchElementException if this collection is empty
         */
        get() = this.iterator().next()

    val last: E?
        /**
         * Gets the last element of this collection.
         *
         * @implSpec
         * The implementation in this interface obtains an iterator of the reversed view
         * of this collection, and then it obtains an element by calling the iterator's
         * `next` method. Any `NoSuchElementException` thrown is propagated.
         * Otherwise, it returns the element.
         *
         * @return the retrieved element
         * @throws NoSuchElementException if this collection is empty
         */
        get() = this.reversed()!!.iterator().next()

    /**
     * Removes and returns the first element of this collection (optional operation).
     *
     * @implSpec
     * The implementation in this interface obtains an iterator of this collection, and then
     * it obtains an element by calling the iterator's `next` method. Any
     * `NoSuchElementException` thrown is propagated. It then calls the iterator's
     * `remove` method. Any `UnsupportedOperationException` thrown is propagated.
     * Then, it returns the element.
     *
     * @return the removed element
     * @throws NoSuchElementException if this collection is empty
     * @throws UnsupportedOperationException if this collection implementation
     * does not support this operation
     */
    fun removeFirst(): E? {
        val it = this.iterator()
        val e = it.next()
        it.remove()
        return e
    }

    /**
     * Removes and returns the last element of this collection (optional operation).
     *
     * @implSpec
     * The implementation in this interface obtains an iterator of the reversed view of this
     * collection, and then it obtains an element by calling the iterator's `next` method.
     * Any `NoSuchElementException` thrown is propagated. It then calls the iterator's
     * `remove` method. Any `UnsupportedOperationException` thrown is propagated.
     * Then, it returns the element.
     *
     * @return the removed element
     * @throws NoSuchElementException if this collection is empty
     * @throws UnsupportedOperationException if this collection implementation
     * does not support this operation
     */
    fun removeLast(): E? {
        val it = this.reversed()!!.iterator()
        val e = it.next()
        it.remove()
        return e
    }
}
