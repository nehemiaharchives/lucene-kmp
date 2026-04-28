package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.IOUtils
import kotlin.test.Test
import kotlin.test.assertEquals

class TestTermStates : LuceneTestCase() {

    @Test
    @Throws(Exception::class)
    fun testToStringOnNullTermState() {
        val dir: Directory = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        w.addDocument(Document())
        val r: IndexReader = w.reader
        val s = IndexSearcher(r)
        val states: TermStates = TermStates.build(s, Term("foo", "bar"), random().nextBoolean())
        assertEquals("TermStates\n  state=null\n", states.toString())
        IOUtils.close(r, w, dir)
    }
}
