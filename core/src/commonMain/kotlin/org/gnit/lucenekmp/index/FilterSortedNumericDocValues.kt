package org.gnit.lucenekmp.index

import okio.IOException

/** Delegates all methods to a wrapped [SortedNumericDocValues].  */
abstract class FilterSortedNumericDocValues(
    /** Wrapped values  */
    protected val `in`: SortedNumericDocValues
) :
    SortedNumericDocValues() {

    @Throws(IOException::class)
    override fun advanceExact(target: Int): Boolean {
        return `in`.advanceExact(target)
    }

    @Throws(IOException::class)
    override fun nextValue(): Long {
        return `in`.nextValue()
    }

    override fun docValueCount(): Int {
        return `in`.docValueCount()
    }

    override fun docID(): Int {
        return `in`.docID()
    }

    @Throws(IOException::class)
    override fun nextDoc(): Int {
        return `in`.nextDoc()
    }

    @Throws(IOException::class)
    override fun advance(target: Int): Int {
        return `in`.advance(target)
    }

    override fun cost(): Long {
        return `in`.cost()
    }
}
