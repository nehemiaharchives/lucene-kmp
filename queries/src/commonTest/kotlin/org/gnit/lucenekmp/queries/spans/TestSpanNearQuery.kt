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

/** Basic tests for SpanNearQuery */
class TestSpanNearQuery : LuceneTestCase() {
    @Test
    fun testHashcodeEquals() {
        val q1 = SpanTermQuery(Term("field", "foo"))
        val q2 = SpanTermQuery(Term("field", "bar"))
        val q3 = SpanTermQuery(Term("field", "baz"))
        val near1 = SpanNearQuery(arrayOf(q1, q2), 10, true)
        val near2 = SpanNearQuery(arrayOf(q2, q3), 10, true)
        QueryUtils.check(near1)
        QueryUtils.check(near2)
        QueryUtils.checkUnequal(near1, near2)
    }

    @Test
    fun testDifferentField() {
        val q1 = SpanTermQuery(Term("field1", "foo"))
        val q2 = SpanTermQuery(Term("field2", "bar"))
        val expected = expectThrows(IllegalArgumentException::class) {
            SpanNearQuery(arrayOf(q1, q2), 10, true)
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
            searcher.search(SpanNearQuery(arrayOf(query, query2), 10, true), 5)
        }
        assertTrue(expected.message!!.contains("was indexed without position data"))
        ir.close()
        dir.close()
    }

    @Test
    fun testBuilder() {
        expectThrows(IllegalArgumentException::class) {
            SpanNearQuery.newOrderedNearQuery("field1")
                .addClause(SpanTermQuery(Term("field2", "term")))
        }
        expectThrows(IllegalArgumentException::class) {
            SpanNearQuery.newUnorderedNearQuery("field1").addGap(1)
        }
    }
}
