package org.gnit.lucenekmp.util

/**
 * An implementation of a selection algorithm, ie. computing the k-th greatest value from a
 * collection.
 */
abstract class Selector {
    /**
     * Reorder elements so that the element at position `k` is the same as if all elements were
     * sorted and all other elements are partitioned around it: `[from, k)` only contains
     * elements that are less than or equal to `k` and `(k, to)` only contains elements
     * that are greater than or equal to `k`.
     */
    abstract fun select(from: Int, to: Int, k: Int)

    fun checkArgs(from: Int, to: Int, k: Int) {
        require(k >= from) { "k must be >= from" }
        require(k < to) { "k must be < to" }
    }

    /** Swap values at slots `i` and `j`.  */
    protected abstract fun swap(i: Int, j: Int)
}
