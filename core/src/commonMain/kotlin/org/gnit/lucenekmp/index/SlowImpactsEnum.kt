package org.gnit.lucenekmp.index

import kotlinx.io.IOException
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.util.BytesRef


/**
 * [ImpactsEnum] that doesn't index impacts but implements the API in a legal way. This is
 * typically used for short postings that do not need skipping.
 */
class SlowImpactsEnum(private val delegate: PostingsEnum) : ImpactsEnum() {


    @Throws(IOException::class)
    override fun nextDoc(): Int {
        return delegate.nextDoc()
    }

    override fun docID(): Int {
        return delegate.docID()
    }

    override fun cost(): Long {
        return delegate.cost()
    }

    @Throws(IOException::class)
    override fun advance(target: Int): Int {
        return delegate.advance(target)
    }

    @Throws(IOException::class)
    override fun startOffset(): Int {
        return delegate.startOffset()
    }

    @Throws(IOException::class)
    override fun nextPosition(): Int {
        return delegate.nextPosition()
    }

    @get:Throws(IOException::class)
    override val payload: BytesRef?
        get() = delegate.payload

    @Throws(IOException::class)
    override fun freq(): Int {
        return delegate.freq()
    }

    @Throws(IOException::class)
    override fun endOffset(): Int {
        return delegate.endOffset()
    }

    override fun advanceShallow(target: Int) {}

    override val impacts: Impacts
        get() {
            return DUMMY_IMPACTS
        }

    companion object {

        val DUMMY_IMPACTS: Impacts = generateDummyImpacts()

        fun generateDummyImpacts(): Impacts {
            val impacts = mutableListOf<Impact>(Impact(Int.Companion.MAX_VALUE, 1L))
            return object : Impacts() {
                override fun numLevels(): Int {
                    return 1
                }

                override fun getDocIdUpTo(level: Int): Int {
                    return DocIdSetIterator.NO_MORE_DOCS
                }

                override fun getImpacts(level: Int): MutableList<Impact> {
                    return impacts
                }
            }
        }
    }
}
