package org.gnit.lucenekmp.search

import okio.IOException


/** Per-segment, per-document long values, which can be calculated at search-time  */
abstract class LongValues {
    /** Get the long value for the current document  */
    @Throws(IOException::class)
    abstract fun longValue(): Long

    /**
     * Advance this instance to the given document id
     *
     * @return true if there is a value for this document
     */
    @Throws(IOException::class)
    abstract fun advanceExact(doc: Int): Boolean
}
