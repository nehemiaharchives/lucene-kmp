package org.gnit.lucenekmp.queries.spans

import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.TwoPhaseIterator
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class TestFilterSpans : LuceneTestCase() {
    @Test
    fun testOverrides() {
        val `in` = FakeSpans()
        val spans = newFilterSpans(`in`)

        assertEquals("Filter(${`in`})", spans.toString())
        assertEquals(11, spans.nextDoc())
        assertEquals(11, spans.docID())
        assertEquals(-1, spans.startPosition())
        assertEquals(-1, spans.endPosition())
        assertEquals(3, spans.nextStartPosition())
        assertEquals(7, spans.endPosition())
        assertEquals(5, spans.width())

        val collector = object : SpanCollector {
            override fun collectLeaf(postings: PostingsEnum, position: Int, term: Term) {}

            override fun reset() {}
        }
        spans.collect(collector)
        assertSame(collector, `in`.lastCollector)

        assertEquals(13L, spans.cost())
        assertFailsWith<UnsupportedOperationException> {
            spans.positionsCost()
        }
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, spans.nextDoc())

        val twoPhaseIn = FakeSpans()
        val twoPhaseSpans = newFilterSpans(twoPhaseIn)
        val twoPhaseIterator = twoPhaseSpans.asTwoPhaseIterator()
        assertSame(twoPhaseIn, twoPhaseIterator.approximation())
        assertEquals(11, twoPhaseIterator.approximation().nextDoc())
        assertEquals(17f, twoPhaseIterator.matchCost())
        assertEquals(true, twoPhaseIterator.matches())

        val advanceIn = FakeSpans()
        val advanceSpans = newFilterSpans(advanceIn)
        assertEquals(11, advanceSpans.advance(0))
        assertEquals(3, advanceSpans.nextStartPosition())
    }

    private fun newFilterSpans(`in`: FakeSpans): FilterSpans {
        return object : FilterSpans(`in`) {
            override fun accept(candidate: Spans): AcceptStatus {
                assertSame(`in`, candidate)
                return AcceptStatus.YES
            }
        }
    }

    private class FakeSpans : Spans() {
        var doc = -1
        var position = -1
        var lastCollector: SpanCollector? = null

        override fun nextDoc(): Int {
            doc = if (doc == -1) 11 else NO_MORE_DOCS
            position = -1
            return doc
        }

        override fun advance(target: Int): Int {
            doc = if (doc == -1 && target <= 11) 11 else NO_MORE_DOCS
            position = -1
            return doc
        }

        override fun docID(): Int {
            return doc
        }

        override fun nextStartPosition(): Int {
            position = if (position == -1) 3 else NO_MORE_POSITIONS
            return position
        }

        override fun startPosition(): Int {
            return position
        }

        override fun endPosition(): Int {
            return if (position == NO_MORE_POSITIONS) NO_MORE_POSITIONS else 7
        }

        override fun width(): Int {
            return 5
        }

        override fun collect(collector: SpanCollector) {
            lastCollector = collector
        }

        override fun cost(): Long {
            return 13L
        }

        override fun asTwoPhaseIterator(): TwoPhaseIterator {
            return object : TwoPhaseIterator(this@FakeSpans) {
                override fun matches(): Boolean {
                    return true
                }

                override fun matchCost(): Float {
                    return 17f
                }
            }
        }

        override fun positionsCost(): Float {
            return 19f
        }

        override fun toString(): String {
            return "FakeSpans"
        }
    }
}
