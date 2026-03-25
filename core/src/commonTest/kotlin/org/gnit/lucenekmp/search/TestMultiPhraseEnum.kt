package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals

/** simple tests for unionpostingsenum */
class TestMultiPhraseEnum : LuceneTestCase() {

    /** Tests union on one document */
    @Test
    @Throws(IOException::class)
    fun testOneDocument() {
        val dir = newDirectory()
        val iwc = newIndexWriterConfig()
        iwc.setMergePolicy(newLogMergePolicy())
        val writer = IndexWriter(dir, iwc)

        val doc = Document()
        doc.add(TextField("field", "foo bar", Field.Store.NO))
        writer.addDocument(doc)

        val ir = DirectoryReader.open(writer)
        writer.close()

        val p1 =
            getOnlyLeafReader(ir).postings(Term("field", "foo"), PostingsEnum.POSITIONS.toInt())
        val p2 =
            getOnlyLeafReader(ir).postings(Term("field", "bar"), PostingsEnum.POSITIONS.toInt())
        val union = MultiPhraseQuery.UnionPostingsEnum(mutableListOf(p1!!, p2!!))

        assertEquals(-1, union.docID())

        assertEquals(0, union.nextDoc())
        assertEquals(2, union.freq())
        assertEquals(0, union.nextPosition())
        assertEquals(1, union.nextPosition())

        assertEquals(DocIdSetIterator.NO_MORE_DOCS, union.nextDoc())

        ir.close()
        dir.close()
    }

    /** Tests union on a few documents */
    @Test
    @Throws(IOException::class)
    fun testSomeDocuments() {
        val dir = newDirectory()
        val iwc = newIndexWriterConfig()
        iwc.setMergePolicy(newLogMergePolicy())
        val writer = IndexWriter(dir, iwc)

        var doc = Document()
        doc.add(TextField("field", "foo", Field.Store.NO))
        writer.addDocument(doc)

        writer.addDocument(Document())

        doc = Document()
        doc.add(TextField("field", "foo bar", Field.Store.NO))
        writer.addDocument(doc)

        doc = Document()
        doc.add(TextField("field", "bar", Field.Store.NO))
        writer.addDocument(doc)

        writer.forceMerge(1)
        val ir = DirectoryReader.open(writer)
        writer.close()

        val p1 =
            getOnlyLeafReader(ir).postings(Term("field", "foo"), PostingsEnum.POSITIONS.toInt())
        val p2 =
            getOnlyLeafReader(ir).postings(Term("field", "bar"), PostingsEnum.POSITIONS.toInt())
        val union = MultiPhraseQuery.UnionPostingsEnum(mutableListOf(p1!!, p2!!))

        assertEquals(-1, union.docID())

        assertEquals(0, union.nextDoc())
        assertEquals(1, union.freq())
        assertEquals(0, union.nextPosition())

        assertEquals(2, union.nextDoc())
        assertEquals(2, union.freq())
        assertEquals(0, union.nextPosition())
        assertEquals(1, union.nextPosition())

        assertEquals(3, union.nextDoc())
        assertEquals(1, union.freq())
        assertEquals(0, union.nextPosition())

        assertEquals(DocIdSetIterator.NO_MORE_DOCS, union.nextDoc())

        ir.close()
        dir.close()
    }
}
