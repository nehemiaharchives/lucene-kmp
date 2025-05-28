package org.gnit.lucenekmp.index

import okio.IOException


/**
 * Exposes multi-valued view over a single-valued instance.
 *
 *
 * This can be used if you want to have one multi-valued implementation that works for single or
 * multi-valued types.
 */
internal class SingletonSortedNumericDocValues(`in`: NumericDocValues) : SortedNumericDocValues() {
    private val `in`: NumericDocValues

    init {
        check(`in`.docID() == -1) { "iterator has already been used: docID=" + `in`.docID() }
        this.`in` = `in`
    }

    val numericDocValues: NumericDocValues
        /** Return the wrapped [NumericDocValues]  */
        get() {
            check(`in`.docID() == -1) { "iterator has already been used: docID=" + `in`.docID() }
            return `in`
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

    @Throws(IOException::class)
    override fun advanceExact(target: Int): Boolean {
        return `in`.advanceExact(target)
    }

    override fun cost(): Long {
        return `in`.cost()
    }

    @Throws(IOException::class)
    override fun nextValue(): Long {
        return `in`.longValue()
    }

    override fun docValueCount(): Int {
        return 1
    }
}
