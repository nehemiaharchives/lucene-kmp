package org.gnit.lucenekmp.jdkport

import kotlin.comparisons.reverseOrder

object Collections {

    /**
     * Swaps the elements at the specified positions in the specified list.
     * (If the specified positions are equal, invoking this method leaves
     * the list unchanged.)
     *
     * @param list The list in which to swap elements.
     * @param i the index of one element to be swapped.
     * @param j the index of the other element to be swapped.
     * @throws IndexOutOfBoundsException if either `i` or `j`
     * is out of range (i &lt; 0 || i &gt;= list.size()
     * || j &lt; 0 || j &gt;= list.size()).
     * @since 1.4
     */
    fun <T> swap(list: MutableList<T>, i: Int, j: Int) {
        // instead of using a raw type here, it's possible to capture
        // the wildcard but it will require a call to a supplementary
        // private method
        val l = list
        l[i] = l.set(j, l[i])
    }

    /**
     * Returns a comparator that imposes the reverse of the *natural
     * ordering* on a collection of objects that implement the
     * `Comparable` interface.  (The natural ordering is the ordering
     * imposed by the objects' own `compareTo` method.)  This enables a
     * simple idiom for sorting (or maintaining) collections (or arrays) of
     * objects that implement the `Comparable` interface in
     * reverse-natural-order.  For example, suppose `a` is an array of
     * strings. Then: <pre>
     * Arrays.sort(a, Collections.reverseOrder());
    </pre> *  sorts the array in reverse-lexicographic (alphabetical) order.
     *
     *
     *
     * The returned comparator is serializable.
     *
     * @apiNote
     * This method returns a `Comparator` that is suitable for sorting
     * elements in reverse order. To obtain a reverse-ordered *view* of a
     * sequenced collection, use the [ SequencedCollection.reversed][SequencedCollection.reversed] method. Or, to obtain a reverse-ordered
     * *view* of a sequenced map, use the [ SequencedMap.reversed][SequencedMap.reversed] method.
     *
     * @param  <T> the class of the objects compared by the comparator
     * @return A comparator that imposes the reverse of the *natural
     * ordering* on a collection of objects that implement
     * the `Comparable` interface.
     * @see Comparable
    </T> */
        /*fun <T> reverseOrder(): Comparator<T?> {
            return ReverseComparator.REVERSE_ORDER as Comparator<T?>
        }*/

    /**
     * Returns a comparator that imposes the reverse ordering of the specified
     * comparator.  If the specified comparator is `null`, this method is
     * equivalent to [.reverseOrder] (in other words, it returns a
     * comparator that imposes the reverse of the *natural ordering* on
     * a collection of objects that implement the Comparable interface).
     *
     *
     * The returned comparator is serializable (assuming the specified
     * comparator is also serializable or `null`).
     *
     * @apiNote
     * This method returns a `Comparator` that is suitable for sorting
     * elements in reverse order. To obtain a reverse-ordered *view* of a
     * sequenced collection, use the [ SequencedCollection.reversed][SequencedCollection.reversed] method. Or, to obtain a reverse-ordered
     * *view* of a sequenced map, use the [ SequencedMap.reversed][SequencedMap.reversed] method.
     *
     * @param <T> the class of the objects compared by the comparator
     * @param cmp a comparator who's ordering is to be reversed by the returned
     * comparator or `null`
     * @return A comparator that imposes the reverse ordering of the
     * specified comparator.
     * @since 1.5
    </T> */
    /*fun <T> reverseOrder(cmp: Comparator<T?>?): Comparator<T?> {
        if (cmp == null) {
            return ReverseComparator.REVERSE_ORDER as Comparator<T?>
        } else if (cmp === ReverseComparator.REVERSE_ORDER) {
            return Comparators.NaturalOrderComparator.INSTANCE as Comparator<T?>
        } else if (cmp === Comparators.NaturalOrderComparator.INSTANCE) {
            return ReverseComparator.REVERSE_ORDER as Comparator<T?>
        } else if (cmp is ReverseComparator2) {
            return (cmp as ReverseComparator2<T?>).cmp
        } else {
            return ReverseComparator2(cmp)
        }
    }*/

    @Suppress("UNCHECKED_CAST")
    fun <T> reverseOrder(): Comparator<T> = ReverseComparator.REVERSE_ORDER as Comparator<T>

    fun <T> reverseOrder(cmp: Comparator<T?>?): Comparator<T?> {
        if (cmp == null) {
            // Type cast is safe as REVERSE_ORDER is a Comparator<Comparable<Any>>
            // and T? would be Comparable<Any?> effectively.
            return ReverseComparator.REVERSE_ORDER as Comparator<T?>
        }
        // TODO: Add check for cmp === Comparators.NaturalOrderComparator.INSTANCE if we implement it
        // else if (cmp === Comparators.NaturalOrderComparator.INSTANCE) {
        //    return ReverseComparator.REVERSE_ORDER as Comparator<T?>
        // }
        if (cmp is ReverseComparator2<*>) {
            // If cmp is already a ReverseComparator2, return its underlying comparator
            return (cmp as ReverseComparator2<T?>).cmp
        }
        // Otherwise, wrap cmp in a new ReverseComparator2
        return ReverseComparator2(cmp)
    }

    class ReverseComparator<T> : Comparator<Comparable<T>> {
        override fun compare(c1: Comparable<T>, c2: Comparable<T>): Int {
            // The test expects this to behave like natural order but with arguments swapped
            // For compare(2, 1), it should return 1 (as if 2 > 1)
            // For compare(1, 2), it should return -1 (as if 1 < 2)
            // This implementation matches the behavior of JDK's Collections.reverseOrder()
            // which is equivalent to c2.compareTo(c1)
            @Suppress("UNCHECKED_CAST")
            return (c2 as Comparable<Any>).compareTo(c1 as Any)
        }

        fun readResolve(): Any {
            return REVERSE_ORDER
        }

        /*fun <T : Comparable<T>> reversed(): Comparator<T> {
            return naturalOrder()
        }*/

        companion object {
            val REVERSE_ORDER: ReverseComparator<Any> = ReverseComparator()
        }
    }

    internal class ReverseComparator2<T>(val cmp: Comparator<T?>) : Comparator<T?> {
        /**
         * The comparator specified in the static factory.  This will never
         * be null, as the static factory returns a ReverseComparator
         * instance if its argument is null.
         *
         * @serial
         */
        // Conditionally serializable
        init {
            checkNotNull(cmp)
        }

        override fun compare(t1: T?, t2: T?): Int {
            return cmp.compare(t2, t1)
        }

        override fun equals(o: Any?): Boolean {
            return (o === this) ||
                    (o is ReverseComparator2<*> &&
                            cmp == o.cmp)
        }

        override fun hashCode(): Int {
            return cmp.hashCode() xor Int.Companion.MIN_VALUE
        }

        /*fun reversed(): Comparator<T?> {
            return cmp
        }*/
    }

    fun <T> reverse(list: MutableList<T>) {
        list.reverse()
    }
}
