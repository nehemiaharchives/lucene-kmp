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

/** Basic tests for SpanTermQuery */
class TestSpanTermQuery : LuceneTestCase() {
    @Test
    fun testHashcodeEquals() {
        val q1 = SpanTermQuery(Term("field", "foo"))
        val q2 = SpanTermQuery(Term("field", "bar"))
        QueryUtils.check(q1)
        QueryUtils.check(q2)
        QueryUtils.checkUnequal(q1, q2)
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
        val expected = expectThrows(IllegalStateException::class) {
            searcher.search(query, 5)
        }
        assertTrue(expected.message!!.contains("was indexed without position data"))
        ir.close()
        dir.close()
    }
}
