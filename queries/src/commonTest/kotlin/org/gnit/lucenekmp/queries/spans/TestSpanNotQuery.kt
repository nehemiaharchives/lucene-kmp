package org.gnit.lucenekmp.queries.spans

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.QueryUtils
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertTrue

/** Basic tests for SpanNotQuery */
class TestSpanNotQuery : LuceneTestCase() {
    @Test
    fun testHashcodeEquals() {
        val q1 = SpanTermQuery(Term("field", "foo"))
        val q2 = SpanTermQuery(Term("field", "bar"))
        val q3 = SpanTermQuery(Term("field", "baz"))
        val not1 = SpanNotQuery(q1, q2)
        val not2 = SpanNotQuery(q2, q3)
        QueryUtils.check(not1)
        QueryUtils.check(not2)
        QueryUtils.checkUnequal(not1, not2)
    }

    @Test
    fun testDifferentField() {
        val q1 = SpanTermQuery(Term("field1", "foo"))
        val q2 = SpanTermQuery(Term("field2", "bar"))
        val expected = expectThrows(IllegalArgumentException::class) {
            SpanNotQuery(q1, q2)
        }
        assertTrue(expected.message!!.contains("must have same field"))
    }

    @Test
    fun testNoPositions() {
        val dir: Directory = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        val doc = Document()
        doc.add(StringField("foo", "bar", Field.Store.NO))
        iw.addDocument(doc)
        val ir = iw.reader
        iw.close()
        val searcher = IndexSearcher(ir)
        val query = SpanTermQuery(Term("foo", "bar"))
        val query2 = SpanTermQuery(Term("foo", "baz"))
        val expected = expectThrows(IllegalStateException::class) {
            searcher.search(SpanNotQuery(query, query2), 5)
        }
        assertTrue(expected.message!!.contains("was indexed without position data"))
        ir.close()
        dir.close()
    }
}
