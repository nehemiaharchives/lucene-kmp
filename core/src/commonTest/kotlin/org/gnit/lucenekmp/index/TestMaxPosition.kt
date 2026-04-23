package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.CannedTokenStream
import org.gnit.lucenekmp.tests.analysis.Token
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.BytesRef
import kotlin.test.Test
import kotlin.test.assertEquals

// LUCENE-6382
class TestMaxPosition : LuceneTestCase() {

    @Test
    @Throws(Exception::class)
    fun testTooBigPosition() {
        val dir: Directory = newDirectory()
        val iw = IndexWriter(dir, IndexWriterConfig())
        val doc = Document()
        // This is at position 1:
        val t1 = Token("foo", 0, 3)
        t1.setPositionIncrement(2)
        if (random().nextBoolean()) {
            t1.payload = BytesRef(byteArrayOf(0x1))
        }
        val t2 = Token("foo", 4, 7)
        // This should overflow max:
        t2.setPositionIncrement(IndexWriter.MAX_POSITION)
        if (random().nextBoolean()) {
            t2.payload = BytesRef(byteArrayOf(0x1))
        }
        doc.add(TextField("foo", CannedTokenStream(t1, t2)))
        expectThrows(IllegalArgumentException::class) {
            iw.addDocument(doc)
        }

        // Document should not be visible:
        val r: IndexReader = DirectoryReader.open(iw)
        assertEquals(0, r.numDocs())
        r.close()

        iw.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testMaxPosition() {
        val dir: Directory = newDirectory()
        val iw = IndexWriter(dir, IndexWriterConfig())
        val doc = Document()
        // This is at position 0:
        val t1 = Token("foo", 0, 3)
        if (random().nextBoolean()) {
            t1.payload = BytesRef(byteArrayOf(0x1))
        }
        val t2 = Token("foo", 4, 7)
        t2.setPositionIncrement(IndexWriter.MAX_POSITION)
        if (random().nextBoolean()) {
            t2.payload = BytesRef(byteArrayOf(0x1))
        }
        doc.add(TextField("foo", CannedTokenStream(t1, t2)))
        iw.addDocument(doc)

        // Document should be visible:
        val r: IndexReader = DirectoryReader.open(iw)
        assertEquals(1, r.numDocs())
        val postings = MultiTerms.getTermPostingsEnum(r, "foo", BytesRef("foo"))!!

        // "foo" appears in docID=0
        assertEquals(0, postings.nextDoc())

        // "foo" appears 2 times in the doc
        assertEquals(2, postings.freq())

        // first at pos=0
        assertEquals(0, postings.nextPosition())

        // next at pos=MAX
        assertEquals(IndexWriter.MAX_POSITION, postings.nextPosition())

        r.close()

        iw.close()
        dir.close()
    }
}
