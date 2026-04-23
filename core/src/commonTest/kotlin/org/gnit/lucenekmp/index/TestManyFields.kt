package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.LuceneTestCase.Companion.SuppressCodecs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Test that creates way, way, way too many fields */
@SuppressCodecs("SimpleText")
class TestManyFields : LuceneTestCase() {

    @Test
    @Throws(IOException::class)
    fun testManyFields() {
        val dir: Directory = newDirectory()
        val writer =
            IndexWriter(
                dir, newIndexWriterConfig(MockAnalyzer(random())).setMaxBufferedDocs(10)
            )
        for (j in 0..<100) {
            val doc = Document()
            doc.add(newField("a$j", "aaa$j", storedTextType))
            doc.add(newField("b$j", "aaa$j", storedTextType))
            doc.add(newField("c$j", "aaa$j", storedTextType))
            doc.add(newField("d$j", "aaa", storedTextType))
            doc.add(newField("e$j", "aaa", storedTextType))
            doc.add(newField("f$j", "aaa", storedTextType))
            writer.addDocument(doc)
        }
        writer.close()

        val reader = DirectoryReader.open(dir)
        assertEquals(100, reader.maxDoc())
        assertEquals(100, reader.numDocs())
        for (j in 0..<100) {
            assertEquals(1, reader.docFreq(Term("a$j", "aaa$j")))
            assertEquals(1, reader.docFreq(Term("b$j", "aaa$j")))
            assertEquals(1, reader.docFreq(Term("c$j", "aaa$j")))
            assertEquals(1, reader.docFreq(Term("d$j", "aaa")))
            assertEquals(1, reader.docFreq(Term("e$j", "aaa")))
            assertEquals(1, reader.docFreq(Term("f$j", "aaa")))
        }
        reader.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testDiverseDocs() {
        val dir: Directory = newDirectory()
        val writer =
            IndexWriter(
                dir, newIndexWriterConfig(MockAnalyzer(random())).setRAMBufferSizeMB(0.5)
            )
        val n = atLeast(1)
        for (i in 0..<n) {
            // First, docs where every term is unique (heavy on
            // Posting instances)
            for (j in 0..<100) {
                val doc = Document()
                for (k in 0..<100) {
                    doc.add(newField("field", random().nextInt().toString(), storedTextType))
                }
                writer.addDocument(doc)
            }

            // Next, many single term docs where only one term
            // occurs (heavy on byte blocks)
            for (j in 0..<100) {
                val doc = Document()
                doc.add(newField("field", "aaa aaa aaa aaa aaa aaa aaa aaa aaa aaa", storedTextType))
                writer.addDocument(doc)
            }

            // Next, many single term docs where only one term
            // occurs but the terms are very long (heavy on
            // char[] arrays)
            for (j in 0..<100) {
                val b = StringBuilder()
                val x = "$j."
                for (k in 0..<1000) b.append(x)
                val longTerm = b.toString()

                val doc = Document()
                doc.add(newField("field", longTerm, storedTextType))
                writer.addDocument(doc)
            }
        }
        writer.close()

        val reader = DirectoryReader.open(dir)
        val searcher = newSearcher(reader)
        val totalHits = searcher.count(TermQuery(Term("field", "aaa")))
        assertEquals(n * 100, totalHits)
        reader.close()

        dir.close()
    }

    // LUCENE-4398
    @Test
    @Throws(Exception::class)
    fun testRotatingFieldNames() {
        val dir: Directory = newFSDirectory(createTempDir("TestIndexWriter.testChangingFields"))
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        iwc.setRAMBufferSizeMB(0.2)
        iwc.setMaxBufferedDocs(-1)
        val w = IndexWriter(dir, iwc)
        var upto = 0

        val ft = FieldType(TextField.TYPE_NOT_STORED)
        ft.setOmitNorms(true)

        var firstDocCount = -1
        for (iter in 0..<10) {
            val startFlushCount = w.getFlushCount()
            var docCount = 0
            while (w.getFlushCount() == startFlushCount) {
                val doc = Document()
                for (i in 0..<10) {
                    doc.add(Field("field${upto++}", "content", ft))
                }
                w.addDocument(doc)
                docCount++
            }

            if (VERBOSE) {
                println("TEST: iter=$iter flushed after docCount=$docCount")
            }

            if (iter == 0) {
                firstDocCount = docCount
            }

            assertTrue(
                (docCount.toFloat()) / firstDocCount > 0.9,
                "flushed after too few docs: first segment flushed at docCount=$firstDocCount, but current segment flushed after docCount=$docCount; iter=$iter"
            )

            if (upto > 5000) {
                // Start re-using field names after a while
                // ... important because otherwise we can OOME due
                // to too many FieldInfo instances.
                upto = 0
            }
        }
        w.close()
        dir.close()
    }

    companion object {
        private val storedTextType = FieldType(TextField.TYPE_NOT_STORED)
    }
}
