package org.gnit.lucenekmp.index

import kotlinx.io.IOException
import org.gnit.lucenekmp.util.BytesRef


/**
 * Exposes multi-valued iterator view over a single-valued iterator.
 *
 *
 * This can be used if you want to have one multi-valued implementation that works for single or
 * multi-valued types.
 */
internal class SingletonSortedSetDocValues(`in`: SortedDocValues) : SortedSetDocValues() {
    private val `in`: SortedDocValues
    private var ord: Long = 0

    /** Creates a multi-valued view over the provided SortedDocValues  */
    init {
        check(`in`.docID() == -1) { "iterator has already been used: docID=" + `in`.docID() }
        this.`in` = `in`
    }

    val sortedDocValues: SortedDocValues
        /** Return the wrapped [SortedDocValues]  */
        get() {
            check(`in`.docID() == -1) { "iterator has already been used: docID=" + `in`.docID() }
            return `in`
        }

    override fun docID(): Int {
        return `in`.docID()
    }

    override fun nextOrd(): Long {
        return ord
    }

    override fun docValueCount(): Int {
        return 1
    }

    @Throws(IOException::class)
    override fun nextDoc(): Int {
        val docID = `in`.nextDoc()
        if (docID != NO_MORE_DOCS) {
            ord = `in`.ordValue().toLong()
        }
        return docID
    }

    @Throws(IOException::class)
    override fun advance(target: Int): Int {
        val docID = `in`.advance(target)
        if (docID != NO_MORE_DOCS) {
            ord = `in`.ordValue().toLong()
        }
        return docID
    }

    @Throws(IOException::class)
    override fun advanceExact(target: Int): Boolean {
        if (`in`.advanceExact(target)) {
            ord = `in`.ordValue().toLong()
            return true
        }
        return false
    }

    @Throws(IOException::class)
    override fun lookupOrd(ord: Long): BytesRef {
        // cast is ok: single-valued cannot exceed Integer.MAX_VALUE
        return `in`.lookupOrd(ord.toInt())
    }

    override val valueCount: Long
        get() = `in`.valueCount.toLong()

    @Throws(IOException::class)
    override fun lookupTerm(key: BytesRef): Long {
        return `in`.lookupTerm(key).toLong()
    }

    @Throws(IOException::class)
    override fun termsEnum(): TermsEnum {
        return `in`.termsEnum()!!
    }

    override fun cost(): Long {
        return `in`.cost()
    }
}
