package org.gnit.lucenekmp.search

import okio.IOException

/** Per-segment, per-document double values, which can be calculated at search-time  */
abstract class DoubleValues {
    /** Get the double value for the current document  */
    @Throws(IOException::class)
    abstract fun doubleValue(): Double

    /**
     * Advance this instance to the given document id
     *
     * @return true if there is a value for this document
     */
    @Throws(IOException::class)
    abstract fun advanceExact(doc: Int): Boolean

    companion object {
        /** Wrap a DoubleValues instance, returning a default if the wrapped instance has no value  */
        fun withDefault(`in`: DoubleValues, missingValue: Double): DoubleValues {
            return object : DoubleValues() {
                var hasValue: Boolean = false

                @Throws(IOException::class)
                override fun doubleValue(): Double {
                    return if (hasValue) `in`.doubleValue() else missingValue
                }

                @Throws(IOException::class)
                override fun advanceExact(doc: Int): Boolean {
                    hasValue = `in`.advanceExact(doc)
                    return true
                }
            }
        }

        /**
         * An empty DoubleValues instance that always returns `false` from [ ][.advanceExact]
         */
        val EMPTY: DoubleValues = object : DoubleValues() {
            @Throws(IOException::class)
            override fun doubleValue(): Double {
                throw UnsupportedOperationException()
            }

            @Throws(IOException::class)
            override fun advanceExact(doc: Int): Boolean {
                return false
            }
        }
    }
}
