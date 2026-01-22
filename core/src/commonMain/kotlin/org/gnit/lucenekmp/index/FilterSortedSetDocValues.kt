package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.automaton.CompiledAutomaton


/** Delegates all methods to a wrapped [SortedSetDocValues].  */
class FilterSortedSetDocValues(
    /** Wrapped values  */
    protected val `in`: SortedSetDocValues
) :
    SortedSetDocValues() {

    @Throws(IOException::class)
    override fun advanceExact(target: Int): Boolean {
        return `in`.advanceExact(target)
    }

    @Throws(IOException::class)
    override fun nextOrd(): Long {
        return `in`.nextOrd()
    }

    override fun docValueCount(): Int {
        return `in`.docValueCount()
    }

    @Throws(IOException::class)
    override fun lookupOrd(ord: Long): BytesRef? {
        return `in`.lookupOrd(ord)
    }

    override val valueCount: Long
        get() = `in`.valueCount

    @Throws(IOException::class)
    override fun lookupTerm(key: BytesRef): Long {
        return `in`.lookupTerm(key)
    }

    @Throws(IOException::class)
    override fun termsEnum(): TermsEnum {
        return `in`.termsEnum()
    }

    @Throws(IOException::class)
    override fun intersect(automaton: CompiledAutomaton): TermsEnum {
        return `in`.intersect(automaton)
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
