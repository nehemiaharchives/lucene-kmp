package org.gnit.lucenekmp.queries.spans

import org.gnit.lucenekmp.tests.search.BaseExplanationTestCase

abstract class BaseSpanExplanationTestCase : BaseExplanationTestCase() {
    /** MACRO for SpanTermQuery */
    fun st(s: String): SpanQuery {
        return SpanTestUtil.spanTermQuery(FIELD, s)
    }

    /** MACRO for SpanNotQuery */
    fun snot(i: SpanQuery, e: SpanQuery): SpanQuery {
        return SpanTestUtil.spanNotQuery(i, e)
    }

    /** MACRO for SpanOrQuery containing two SpanTerm queries */
    fun sor(s: String, e: String): SpanQuery {
        return SpanTestUtil.spanOrQuery(FIELD, s, e)
    }

    /** MACRO for SpanOrQuery containing two SpanQueries */
    fun sor(s: SpanQuery, e: SpanQuery): SpanQuery {
        return SpanTestUtil.spanOrQuery(s, e)
    }

    /** MACRO for SpanOrQuery containing three SpanTerm queries */
    fun sor(s: String, m: String, e: String): SpanQuery {
        return SpanTestUtil.spanOrQuery(FIELD, s, m, e)
    }

    /** MACRO for SpanOrQuery containing two SpanQueries */
    fun sor(s: SpanQuery, m: SpanQuery, e: SpanQuery): SpanQuery {
        return SpanTestUtil.spanOrQuery(s, m, e)
    }

    /** MACRO for SpanNearQuery containing two SpanTerm queries */
    fun snear(s: String, e: String, slop: Int, inOrder: Boolean): SpanQuery {
        return snear(st(s), st(e), slop, inOrder)
    }

    /** MACRO for SpanNearQuery containing two SpanQueries */
    fun snear(s: SpanQuery, e: SpanQuery, slop: Int, inOrder: Boolean): SpanQuery {
        return if (inOrder) {
            SpanTestUtil.spanNearOrderedQuery(slop, s, e)
        } else {
            SpanTestUtil.spanNearUnorderedQuery(slop, s, e)
        }
    }

    /** MACRO for SpanNearQuery containing three SpanTerm queries */
    fun snear(s: String, m: String, e: String, slop: Int, inOrder: Boolean): SpanQuery {
        return snear(st(s), st(m), st(e), slop, inOrder)
    }

    /** MACRO for SpanNearQuery containing three SpanQueries */
    fun snear(s: SpanQuery, m: SpanQuery, e: SpanQuery, slop: Int, inOrder: Boolean): SpanQuery {
        return if (inOrder) {
            SpanTestUtil.spanNearOrderedQuery(slop, s, m, e)
        } else {
            SpanTestUtil.spanNearUnorderedQuery(slop, s, m, e)
        }
    }

    /** MACRO for SpanFirst(SpanTermQuery) */
    fun sf(s: String, b: Int): SpanQuery {
        return SpanTestUtil.spanFirstQuery(st(s), b)
    }
}
