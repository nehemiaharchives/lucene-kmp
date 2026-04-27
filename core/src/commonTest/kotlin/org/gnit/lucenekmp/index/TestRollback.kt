package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals

class TestRollback : LuceneTestCase() {

    // LUCENE-2536
    @Test
    @Throws(Exception::class)
    fun testRollbackIntegrityWithBufferFlush() {
        val dir: Directory = newDirectory()
        val rw = RandomIndexWriter(random(), dir)
        for (i in 0..4) {
            val doc = Document()
            doc.add(newStringField("pk", i.toString(), Field.Store.YES))
            rw.addDocument(doc)
        }
        rw.close()

        // If buffer size is small enough to cause a flush, errors ensue...
        val w = IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setMaxBufferedDocs(2)
                    .setOpenMode(IndexWriterConfig.OpenMode.APPEND)
            )

        for (i in 0..2) {
            val doc = Document()
            val value = i.toString()
            doc.add(newStringField("pk", value, Field.Store.YES))
            doc.add(newStringField("text", "foo", Field.Store.YES))
            w.updateDocument(Term("pk", value), doc)
        }
        w.rollback()

        val r: IndexReader = DirectoryReader.open(dir)
        assertEquals(5, r.numDocs().toLong(), "index should contain same number of docs post rollback")
        r.close()
        dir.close()
    }
}
