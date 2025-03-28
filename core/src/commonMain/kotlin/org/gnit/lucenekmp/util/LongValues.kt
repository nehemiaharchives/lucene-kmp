package org.gnit.lucenekmp.util

/**
 * Abstraction over an array of longs.
 *
 * @lucene.internal
 */
abstract class LongValues {
    /** Get value at `index`.  */
    abstract fun get(index: Long): Long

    companion object {
        /** An instance that returns the provided value.  */
        val IDENTITY: LongValues = object : LongValues() {
            override fun get(index: Long): Long {
                return index
            }
        }

        val ZEROES: LongValues = object : LongValues() {
            override fun get(index: Long): Long {
                return 0
            }
        }
    }
}
