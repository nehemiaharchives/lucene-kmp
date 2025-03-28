package org.gnit.lucenekmp.util.automaton

import org.gnit.lucenekmp.jdkport.Arrays

internal abstract class IntSet {
    /**
     * Return an array representation of this int set's values. Values are valid for indices [0,
     * [.size]). If this is a mutable int set, then changes to the set are not guaranteed to
     * be visible in this array.
     *
     * @return an array containing the values for this set, guaranteed to be at least [.size]
     * elements
     */
    abstract val array: IntArray

    /**
     * Guaranteed to be less than or equal to the length of the array returned by [.getArray].
     *
     * @return The number of values in this set.
     */
    abstract fun size(): Int

    abstract fun longHashCode(): Long

    override fun hashCode(): Int {
        return longHashCode().hashCode()
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o !is IntSet) return false
        val that = o
        return longHashCode() == that.longHashCode()
                && Arrays.equals(this.array, 0, size(), that.array, 0, that.size())
    }
}
