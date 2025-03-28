package org.gnit.lucenekmp.index

import kotlinx.io.IOException

/** A list of per-document numeric values, sorted according to [Long.compare].  */
abstract class SortedNumericDocValues
/** Sole constructor. (For invocation by subclass constructors, typically implicit.)  */
protected constructor() : DocValuesIterator() {
    /**
     * Iterates to the next value in the current document. Do not call this more than [ ][.docValueCount] times for the document.
     */
    @Throws(IOException::class)
    abstract fun nextValue(): Long

    /**
     * Retrieves the number of values for the current document. This must always be greater than zero.
     * It is illegal to call this method after [.advanceExact] returned `false`.
     */
    abstract fun docValueCount(): Int
}
