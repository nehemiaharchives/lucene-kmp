package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestFlex : LuceneTestCase() {

    // Test non-flex API emulated on flex index
    @Test
    @Suppress("LocalVariableName")
    @Throws(Exception::class)
    fun testNonFlex() {
        val d: Directory = newDirectory()

        val DOC_COUNT = 177

        val w =
            IndexWriter(
                d,
                IndexWriterConfig(MockAnalyzer(random()))
                    .setMaxBufferedDocs(7)
                    .setMergePolicy(newLogMergePolicy())
            )

        for (iter in 0..1) {
            if (iter == 0) {
                val doc = Document()
                doc.add(newTextField("field1", "this is field1", Field.Store.NO))
                doc.add(newTextField("field2", "this is field2", Field.Store.NO))
                doc.add(newTextField("field3", "aaa", Field.Store.NO))
                doc.add(newTextField("field4", "bbb", Field.Store.NO))
                repeat(DOC_COUNT) {
                    w.addDocument(doc)
                }
            } else {
                w.forceMerge(1)
            }

            val r = DirectoryReader.open(w)

            val terms = MultiTerms.getTerms(r, "field3")!!.iterator()
            assertEquals(TermsEnum.SeekStatus.END, terms.seekCeil(BytesRef("abc")))
            r.close()
        }

        w.close()
        d.close()
    }

    @Test
    @Throws(Exception::class)
    fun testTermOrd() {
        val d = newDirectory()
        val w =
            IndexWriter(
                d,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setCodec(TestUtil.alwaysPostingsFormat(TestUtil.getDefaultPostingsFormat()))
            )
        val doc = Document()
        doc.add(newTextField("f", "a b c", Field.Store.NO))
        w.addDocument(doc)
        w.forceMerge(1)
        val r = DirectoryReader.open(w)
        val terms = getOnlyLeafReader(r).terms("f")!!.iterator()
        assertTrue(terms.next() != null)
        try {
            assertEquals(0, terms.ord())
        } catch (_: UnsupportedOperationException) {
            // ok -- codec is not required to support this op
        }
        r.close()
        w.close()
        d.close()
    }
}
