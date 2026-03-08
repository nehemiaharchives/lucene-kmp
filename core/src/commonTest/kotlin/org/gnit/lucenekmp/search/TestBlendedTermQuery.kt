package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field.Store
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.QueryUtils
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals

class TestBlendedTermQuery : LuceneTestCase() {
    @Test
    fun testEquals() {
        val t1 = Term("foo", "bar")

        var bt1 = BlendedTermQuery.Builder().add(t1).build()
        var bt2 = BlendedTermQuery.Builder().add(t1).build()
        QueryUtils.checkEqual(bt1, bt2)

        bt1 = BlendedTermQuery.Builder().setRewriteMethod(BlendedTermQuery.BOOLEAN_REWRITE).add(t1).build()
        bt2 = BlendedTermQuery.Builder().setRewriteMethod(BlendedTermQuery.DISJUNCTION_MAX_REWRITE).add(t1).build()
        QueryUtils.checkUnequal(bt1, bt2)

        val t2 = Term("foo", "baz")

        bt1 = BlendedTermQuery.Builder().add(t1).add(t2).build()
        bt2 = BlendedTermQuery.Builder().add(t2).add(t1).build()
        QueryUtils.checkEqual(bt1, bt2)

        val boost1 = random().nextFloat()
        val boost2 = random().nextFloat()
        bt1 = BlendedTermQuery.Builder().add(t1, boost1).add(t2, boost2).build()
        bt2 = BlendedTermQuery.Builder().add(t2, boost2).add(t1, boost1).build()
        QueryUtils.checkEqual(bt1, bt2)
    }

    @Test
    fun testToString() {
        assertEquals("Blended()", BlendedTermQuery.Builder().build().toString())
        val t1 = Term("foo", "bar")
        assertEquals("Blended(foo:bar)", BlendedTermQuery.Builder().add(t1).build().toString())
        val t2 = Term("foo", "baz")
        assertEquals(
            "Blended(foo:bar foo:baz)",
            BlendedTermQuery.Builder().add(t1).add(t2).build().toString()
        )
        assertEquals(
            "Blended((foo:bar)^4.0 (foo:baz)^3.0)",
            BlendedTermQuery.Builder().add(t1, 4f).add(t2, 3f).build().toString()
        )
    }

    @Test
    @Throws(IOException::class)
    fun testBlendedScores() {
        val dir: Directory = newDirectory()
        val w = RandomIndexWriter(random(), dir)

        var doc = Document()
        doc.add(StringField("f", "a", Store.NO))
        w.addDocument(doc)

        doc = Document()
        doc.add(StringField("f", "b", Store.NO))
        for (i in 0..<10) {
            w.addDocument(doc)
        }

        val reader: IndexReader = w.getReader(true, false)
        val searcher = newSearcher(reader)
        val query = BlendedTermQuery.Builder()
            .setRewriteMethod(BlendedTermQuery.DisjunctionMaxRewrite(0f))
            .add(Term("f", "a"))
            .add(Term("f", "b"))
            .build()

        val topDocs = searcher.search(query, 20)
        assertEquals(11L, topDocs.totalHits.value)
        for (i in topDocs.scoreDocs.indices) {
            assertEquals(topDocs.scoreDocs[0].score, topDocs.scoreDocs[i].score, 0.0f)
        }

        reader.close()
        w.close()
        dir.close()
    }
}
