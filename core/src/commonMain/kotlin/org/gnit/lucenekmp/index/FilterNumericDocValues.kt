package org.gnit.lucenekmp.index

import okio.IOException

/** Delegates all methods to a wrapped [NumericDocValues].  */
abstract class FilterNumericDocValues protected constructor(
    /** Wrapped values  */
    protected val `in`: NumericDocValues
) : NumericDocValues() {

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

    @Throws(IOException::class)
    override fun advanceExact(target: Int): Boolean {
        return `in`.advanceExact(target)
    }

    override fun cost(): Long {
        return `in`.cost()
    }

    @Throws(IOException::class)
    override fun longValue(): Long {
        return `in`.longValue()
    }
}
