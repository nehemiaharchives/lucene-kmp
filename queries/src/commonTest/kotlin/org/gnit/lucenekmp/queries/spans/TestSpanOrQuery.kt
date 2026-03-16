package org.gnit.lucenekmp.queries.spans

import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.tests.search.QueryUtils
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertTrue

/** Basic tests for SpanOrQuery */
class TestSpanOrQuery : LuceneTestCase() {
    @Test
    fun testHashcodeEquals() {
        val q1 = SpanTermQuery(Term("field", "foo"))
        val q2 = SpanTermQuery(Term("field", "bar"))
        val q3 = SpanTermQuery(Term("field", "baz"))
        val or1 = SpanOrQuery(q1, q2)
        val or2 = SpanOrQuery(q2, q3)
        QueryUtils.check(or1)
        QueryUtils.check(or2)
        QueryUtils.checkUnequal(or1, or2)
    }

    @Test
    fun testSpanOrEmpty() {
        val a = SpanOrQuery()
        val b = SpanOrQuery()
        assertTrue(a == b, "empty should equal")
    }

    @Test
    fun testDifferentField() {
        val q1 = SpanTermQuery(Term("field1", "foo"))
        val q2 = SpanTermQuery(Term("field2", "bar"))
        val expected = expectThrows(IllegalArgumentException::class) {
            SpanOrQuery(q1, q2)
        }
        assertTrue(expected.message!!.contains("must have same field"))
    }
}
