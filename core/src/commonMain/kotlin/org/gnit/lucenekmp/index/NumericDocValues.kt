package org.gnit.lucenekmp.index

import okio.IOException

/** A per-document numeric value.  */
abstract class NumericDocValues
/** Sole constructor. (For invocation by subclass constructors, typically implicit.)  */
protected constructor() : DocValuesIterator() {
    /**
     * Returns the numeric value for the current document ID. It is illegal to call this method after
     * [.advanceExact] returned `false`.
     *
     * @return numeric value
     */
    @Throws(IOException::class)
    abstract fun longValue(): Long
}
