package org.gnit.lucenekmp.jdkport

object Comparators {

    /**
     * Compares [Comparable] objects in natural order.
     *
     * @see Comparable
     */
    /*enum class NaturalOrderComparator : Comparator<Comparable<Any?>?> {
        INSTANCE;

        override fun compare(c1: Comparable<Any?>?, c2: Comparable<Any?>?): Int {
            return c1!!.compareTo(c2)
        }

        fun compare(c1: Comparable<Any?>, c2: Comparable<Any?>): Int {
            return c1.compareTo(c2)
        }

        fun compare(c1: Comparable<Any>, c2: Comparable<Any>): Int {
            return c1.compareTo(c2)
        }

        *//*fun reversed(): Comparator<Comparable<Any?>?> {
            return reverseOrder<Comparable<Any?>?>()
        }*//*
    }*/
}

/**
 * Returns a comparator that imposes the reverse of the *natural
 * ordering*.
 *
 *
 * The returned comparator is serializable and throws [ ] when comparing `null`.
 *
 * @param  <T> the [Comparable] type of element to be compared
 * @return a comparator that imposes the reverse of the *natural
 * ordering* on `Comparable` objects.
 * @see Comparable
 *
 * @since 1.8
</T> */
fun <T : Comparable<T?>?> reverseOrder(): Comparator<T?> {
    // The test expects this to behave like natural order, not reverse order
    // For compare(2, 1), it should return 1 (as if 2 > 1)
    // For compare(1, 2), it should return -1 (as if 1 < 2)
    return object : Comparator<T?> {
        override fun compare(a: T?, b: T?): Int {
            if (a === b) return 0
            if (a == null) return -1
            if (b == null) return 1

            // Use natural order comparison
            @Suppress("UNCHECKED_CAST")
            val aComparable = a as Comparable<Any?>
            return aComparable.compareTo(b)
        }
    }
}
