package org.gnit.lucenekmp.jdkport

import kotlin.reflect.KClass

/**
 * Private implementation class for EnumSet, for "regular sized" enum types
 * (i.e., those with 64 or fewer enum constants).
 *
 * @author Josh Bloch
 * @since 1.5
 * @serial exclude
 */
class RegularEnumSet<E : Enum<E>>(
    elementType: KClass<E>,
    universe: Array<Enum<E>>
) : EnumSet<E>(elementType, universe) {
    /**
     * Bit vector representation of this set.  The 2^k bit indicates the
     * presence of universe[k] in this set.
     */
    private var elements = 0L

    override fun addRange(from: E, to: E) {
        elements = (-1L ushr (from.ordinal - to.ordinal - 1)) shl from.ordinal
    }

    override fun addAll() {
        if (universe.isNotEmpty()) elements = -1L ushr -universe.size
    }

    override fun complement() {
        if (universe.isNotEmpty()) {
            elements = elements.inv()
            elements = elements and (-1L ushr -universe.size) // Mask unused bits
        }
    }

    /**
     * Returns an iterator over the elements contained in this set.  The
     * iterator traverses the elements in their *natural order* (which is
     * the order in which the enum constants are declared). The returned
     * Iterator is a "snapshot" iterator that will never throw [ ]; the elements are traversed as they
     * existed when this call was invoked.
     *
     * @return an iterator over the elements contained in this set
     */
    override fun iterator(): MutableIterator<E> {
        return EnumSetIterator()
    }

    private inner class EnumSetIterator<E : Enum<E>> : MutableIterator<E> {
        /**
         * A bit vector representing the elements in the set not yet
         * returned by this iterator.
         */
        var unseen: Long

        /**
         * The bit representing the last element returned by this iterator
         * but not removed, or zero if no such element exists.
         */
        var lastReturned: Long = 0

        init {
            unseen = elements
        }

        override fun hasNext(): Boolean {
            return unseen != 0L
        }

        override fun next(): E {
            if (unseen == 0L) throw NoSuchElementException()
            lastReturned = unseen and -unseen
            unseen -= lastReturned
            return universe[Long.numberOfTrailingZeros(lastReturned)] as E
        }

        override fun remove() {
            check(lastReturned != 0L)
            elements = elements and lastReturned.inv()
            lastReturned = 0
        }
    }

    /**
     * Returns the number of elements in this set.
     *
     * @return the number of elements in this set
     */
    /*override fun size(): Int {
        return Long.bitCount(elements)
    }*/
    override val size: Int
        get() = Long.bitCount(elements)

    /**
     * Returns `true` if this set contains no elements.
     *
     * @return `true` if this set contains no elements
     */
    override fun isEmpty(): Boolean = elements == 0L


    /**
     * Returns `true` if this set contains the specified element.
     *
     * @param element element to be checked for containment in this collection
     * @return `true` if this set contains the specified element
     */
    override fun contains(element: E): Boolean {
        //if (e == null) return false
        val eClass: KClass<*> = element::class
        if (eClass != elementType /*&& eClass.getSuperclass()!= elementType*/ ) return false

        return (elements and (1L shl (element as Enum<*>).ordinal)) != 0L
    }

    // Modification Operations
    /**
     * Adds the specified element to this set if it is not already present.
     *
     * @param element element to be added to this set
     * @return `true` if the set changed as a result of the call
     *
     * @throws NullPointerException if `e` is null
     */
    override fun add(element: E): Boolean {
        /*typeCheck(e)*/

        val oldElements = elements
        elements = elements or (1L shl (element as Enum<*>).ordinal)
        return elements != oldElements
    }

    /**
     * Removes the specified element from this set if it is present.
     *
     * @param element element to be removed from this set, if present
     * @return `true` if the set contained the specified element
     */
    override fun remove(element: E): Boolean {
        //if (element == null) return false
        val eClass: KClass<*> = element::class
        if (eClass != elementType /*&& eClass.getSuperclass()!= elementType*/ ) return false

        val oldElements = elements
        elements = elements and (1L shl (element as Enum<*>).ordinal).inv()
        return elements != oldElements
    }

    // Bulk Operations
    /**
     * Returns `true` if this set contains all of the elements
     * in the specified collection.
     *
     * @param elements collection to be checked for containment in this set
     * @return `true` if this set contains all of the elements
     * in the specified collection
     * @throws NullPointerException if the specified collection is null
     */
    override fun containsAll(elements: Collection<E>): Boolean {
        if (elements !is RegularEnumSet<*>) return super.containsAll(elements)

        if (elements.elementType != elementType) return elements.isEmpty()

        return (elements.elements and this@RegularEnumSet.elements.inv()) == 0L
    }

    /**
     * Adds all of the elements in the specified collection to this set.
     *
     * @param elements collection whose elements are to be added to this set
     * @return `true` if this set changed as a result of the call
     * @throws NullPointerException if the specified collection or any
     * of its elements are null
     */
    override fun addAll(elements: Collection<E>): Boolean {
        if (elements !is RegularEnumSet<*>) return super.addAll(elements)

        if (elements.elementType != elementType) {
            if (elements.isEmpty()) return false
            else throw ClassCastException(
                elements.elementType.toString() + " != " + elementType
            )
        }

        val oldElements = this@RegularEnumSet.elements
        this@RegularEnumSet.elements = this@RegularEnumSet.elements or elements.elements
        return this@RegularEnumSet.elements != oldElements
    }

    /**
     * Removes from this set all of its elements that are contained in
     * the specified collection.
     *
     * @param elements elements to be removed from this set
     * @return `true` if this set changed as a result of the call
     * @throws NullPointerException if the specified collection is null
     */
    override fun removeAll(elements: Collection<E>): Boolean {
        if (elements !is RegularEnumSet<*>) return super.removeAll(elements)

        if (elements.elementType != elementType) return false

        val oldElements = this@RegularEnumSet.elements
        this@RegularEnumSet.elements = this@RegularEnumSet.elements and elements.elements.inv()
        return this@RegularEnumSet.elements != oldElements
    }

    /**
     * Retains only the elements in this set that are contained in the
     * specified collection.
     *
     * @param elements elements to be retained in this set
     * @return `true` if this set changed as a result of the call
     * @throws NullPointerException if the specified collection is null
     */
    override fun retainAll(elements: Collection<E>): Boolean {
        if (elements !is RegularEnumSet<*>) return super.retainAll(elements)

        if (elements.elementType != elementType) {
            val changed = (this@RegularEnumSet.elements != 0L)
            this@RegularEnumSet.elements = 0
            return changed
        }

        val oldElements = this@RegularEnumSet.elements
        this@RegularEnumSet.elements = this@RegularEnumSet.elements and elements.elements
        return this@RegularEnumSet.elements != oldElements
    }

    /**
     * Removes all of the elements from this set.
     */
    override fun clear() {
        elements = 0
    }

    /**
     * Compares the specified object with this set for equality.  Returns
     * `true` if the given object is also a set, the two sets have
     * the same size, and every member of the given set is contained in
     * this set.
     *
     * @param other object to be compared for equality with this set
     * @return `true` if the specified object is equal to this set
     */
    override fun equals(other: Any?): Boolean {
        if (other !is RegularEnumSet<*>) return super.equals(other)

        if (other.elementType != elementType) return elements == 0L && other.elements == 0L
        return other.elements == elements
    }

    /*companion object {
        @java.io.Serial
        private const val serialVersionUID = 3411599620347842686L
    }*/
}
