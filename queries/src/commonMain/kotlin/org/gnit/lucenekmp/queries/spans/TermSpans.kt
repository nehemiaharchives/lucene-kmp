package org.gnit.lucenekmp.queries.spans

import okio.IOException
import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.DocIdSetIterator

/**
 * Expert: Public for extension only. This does not work correctly for terms that indexed at
 * position Integer.MAX_VALUE.
 */
open class TermSpans(
    protected val postingsInternal: PostingsEnum,
    protected val term: Term,
    private val positionsCostInternal: Float,
) : Spans() {
    protected var doc: Int = -1
    protected var freq: Int = 0
    protected var count: Int = 0
    protected var position: Int = -1
    protected var readPayload: Boolean = false

    init {
        assert(positionsCostInternal > 0) // otherwise the TermSpans should not be created.
    }

    @Throws(IOException::class)
    override fun nextDoc(): Int {
        doc = postingsInternal.nextDoc()
        if (doc != DocIdSetIterator.NO_MORE_DOCS) {
            freq = postingsInternal.freq()
            assert(freq >= 1)
            count = 0
        }
        position = -1
        return doc
    }

    @Throws(IOException::class)
    override fun advance(target: Int): Int {
        assert(target > doc)
        doc = postingsInternal.advance(target)
        if (doc != DocIdSetIterator.NO_MORE_DOCS) {
            freq = postingsInternal.freq()
            assert(freq >= 1)
            count = 0
        }
        position = -1
        return doc
    }

    override fun docID(): Int {
        return doc
    }

    @Throws(IOException::class)
    override fun nextStartPosition(): Int {
        if (count == freq) {
            assert(position != NO_MORE_POSITIONS)
            return NO_MORE_POSITIONS.also { position = it }
        }
        val prevPosition = position
        position = postingsInternal.nextPosition()
        assert(position >= prevPosition) { "prevPosition=$prevPosition > position=$position" }
        assert(position != NO_MORE_POSITIONS) // int endPosition not possible
        count++
        readPayload = false
        return position
    }

    override fun startPosition(): Int {
        return position
    }

    override fun endPosition(): Int {
        return if (position == -1) {
            -1
        } else if (position != NO_MORE_POSITIONS) {
            position + 1
        } else {
            NO_MORE_POSITIONS
        }
    }

    override fun width(): Int {
        return 0
    }

    override fun cost(): Long {
        return postingsInternal.cost()
    }

    @Throws(IOException::class)
    override fun collect(collector: SpanCollector) {
        collector.collectLeaf(postingsInternal, position, term)
    }

    override fun positionsCost(): Float {
        return positionsCostInternal
    }

    override fun toString(): String {
        return "spans($term)@" +
            if (doc == -1) {
                "START"
            } else if (doc == NO_MORE_DOCS) {
                "ENDDOC"
            } else {
                "$doc - ${if (position == NO_MORE_POSITIONS) "ENDPOS" else position}"
            }
    }

    fun getPostings(): PostingsEnum {
        return postingsInternal
    }
}
