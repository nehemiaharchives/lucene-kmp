package org.gnit.lucenekmp.queries.spans

import okio.IOException
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.tests.search.QueryUtils
import kotlin.test.assertEquals

/** Some utility methods used for testing span queries */
object SpanTestUtil {
    /**
     * Adds additional asserts to a spanquery. Highly recommended if you want tests to actually be
     * debuggable.
     */
    fun spanQuery(query: SpanQuery): SpanQuery {
        QueryUtils.check(query)
        return AssertingSpanQuery(query)
    }

    /** Makes a new SpanTermQuery (with additional asserts). */
    fun spanTermQuery(field: String, term: String): SpanQuery {
        return spanQuery(SpanTermQuery(Term(field, term)))
    }

    /** Makes a new SpanOrQuery (with additional asserts) from the provided `terms`. */
    fun spanOrQuery(field: String, vararg terms: String): SpanQuery {
        val subqueries = Array(terms.size) { i -> spanTermQuery(field, terms[i]) }
        return spanOrQuery(*subqueries)
    }

    /** Makes a new SpanOrQuery (with additional asserts). */
    fun spanOrQuery(vararg subqueries: SpanQuery): SpanQuery {
        return spanQuery(SpanOrQuery(*subqueries))
    }

    /** Makes a new SpanNotQuery (with additional asserts). */
    fun spanNotQuery(include: SpanQuery, exclude: SpanQuery): SpanQuery {
        return spanQuery(SpanNotQuery(include, exclude))
    }

    /** Makes a new SpanNotQuery (with additional asserts). */
    fun spanNotQuery(include: SpanQuery, exclude: SpanQuery, pre: Int, post: Int): SpanQuery {
        return spanQuery(SpanNotQuery(include, exclude, pre, post))
    }

    /** Makes a new ordered SpanNearQuery (with additional asserts) from the provided `terms` */
    fun spanNearOrderedQuery(field: String, slop: Int, vararg terms: String): SpanQuery {
        val subqueries = Array(terms.size) { i -> spanTermQuery(field, terms[i]) }
        return spanNearOrderedQuery(slop, *subqueries)
    }

    /** Makes a new ordered SpanNearQuery (with additional asserts) */
    fun spanNearOrderedQuery(slop: Int, vararg subqueries: SpanQuery): SpanQuery {
        return spanQuery(SpanNearQuery(arrayOf(*subqueries), slop, true))
    }

    /**
     * Makes a new unordered SpanNearQuery (with additional asserts) from the provided `terms`
     */
    fun spanNearUnorderedQuery(field: String, slop: Int, vararg terms: String): SpanQuery {
        val builder = SpanNearQuery.newUnorderedNearQuery(field)
        builder.setSlop(slop)
        for (term in terms) {
            builder.addClause(SpanTermQuery(Term(field, term)))
        }
        return spanQuery(builder.build())
    }

    /** Makes a new unordered SpanNearQuery (with additional asserts) */
    fun spanNearUnorderedQuery(slop: Int, vararg subqueries: SpanQuery): SpanQuery {
        return spanQuery(SpanNearQuery(arrayOf(*subqueries), slop, false))
    }

    /**
     * Assert the next iteration from `spans` is a match from `start` to `end` in `doc`.
     */
    @Throws(IOException::class)
    fun assertNext(spans: Spans, doc: Int, start: Int, end: Int) {
        if (spans.docID() >= doc) {
            assertEquals(doc, spans.docID(), "docId")
        } else {
            if (spans.docID() >= 0) {
                assertEquals(Spans.NO_MORE_POSITIONS, spans.nextStartPosition(), "nextStartPosition of previous doc")
                assertEquals(Spans.NO_MORE_POSITIONS, spans.endPosition(), "endPosition of previous doc")
            }
            assertEquals(doc, spans.nextDoc(), "nextDoc")
            if (doc != DocIdSetIterator.NO_MORE_DOCS) {
                assertEquals(-1, spans.startPosition(), "first startPosition")
                assertEquals(-1, spans.endPosition(), "first endPosition")
            }
        }
        if (doc != DocIdSetIterator.NO_MORE_DOCS) {
            assertEquals(start, spans.nextStartPosition(), "nextStartPosition")
            assertEquals(start, spans.startPosition(), "startPosition")
            assertEquals(end, spans.endPosition(), "endPosition")
        }
    }

        /** Assert that `spans` is exhausted. */
    @Throws(Exception::class)
    fun assertFinished(spans: Spans?) {
        if (spans != null) {
            assertNext(spans, DocIdSetIterator.NO_MORE_DOCS, -2, -2)
        }
    }
}
