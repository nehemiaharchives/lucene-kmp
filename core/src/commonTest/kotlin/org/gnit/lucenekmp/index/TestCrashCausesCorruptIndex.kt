package org.gnit.lucenekmp.index

import okio.IOException
import okio.Path
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.search.TopDocs
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.FSDirectory
import org.gnit.lucenekmp.store.FilterDirectory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class TestCrashCausesCorruptIndex : LuceneTestCase() {
    private lateinit var path: Path

    /** LUCENE-3627: This test fails. */
    @Test
    fun testCrashCorruptsIndexing() {
        path = createTempDir("testCrashCorruptsIndexing")

        indexAndCrashOnCreateOutputSegments2()

        searchForFleas(2)

        indexAfterRestart()

        searchForFleas(3)
    }

    /**
     * index 1 document and commit. prepare for crashing. index 1 more document, and upon commit,
     * creation of segments_2 will crash.
     */
    private fun indexAndCrashOnCreateOutputSegments2() {
        val realDirectory: Directory = FSDirectory.open(path)
        val crashAfterCreateOutput = CrashAfterCreateOutput(realDirectory)

        // NOTE: cannot use RandomIndexWriter because it
        // sometimes commits:
        val indexWriter =
            IndexWriter(crashAfterCreateOutput, newIndexWriterConfig(MockAnalyzer(random())))

        indexWriter.addDocument(getDocument())
        // writes segments_1:
        indexWriter.commit()

        crashAfterCreateOutput.setCrashAfterCreateOutput("pending_segments_2")
        indexWriter.addDocument(getDocument())
        // tries to write segments_2 but hits fake exc:
        expectThrows(CrashingException::class) {
            indexWriter.commit()
        }

        // writes segments_3
        indexWriter.close()
        assertFalse(slowFileExists(realDirectory, "segments_2"))
        crashAfterCreateOutput.close()
    }

    /** Attempts to index another 1 document. */
    private fun indexAfterRestart() {
        val realDirectory = newFSDirectory(path)

        // LUCENE-3627 (before the fix): this line fails because
        // it doesn't know what to do with the created but empty
        // segments_2 file
        val indexWriter =
            IndexWriter(realDirectory, newIndexWriterConfig(MockAnalyzer(random())))

        // currently the test fails above.
        // however, to test the fix, the following lines should pass as well.
        indexWriter.addDocument(getDocument())
        indexWriter.close()
        assertFalse(slowFileExists(realDirectory, "segments_2"))
        realDirectory.close()
    }

    /** Run an example search. */
    private fun searchForFleas(expectedTotalHits: Int) {
        val realDirectory = newFSDirectory(path)
        val indexReader = DirectoryReader.open(realDirectory)
        val indexSearcher: IndexSearcher = newSearcher(indexReader)
        val topDocs: TopDocs = indexSearcher.search(TermQuery(Term(TEXT_FIELD, "fleas")), 10)
        assertNotNull(topDocs)
        assertEquals(expectedTotalHits.toLong(), topDocs.totalHits.value)
        indexReader.close()
        realDirectory.close()
    }

    private fun getDocument(): Document {
        val document = Document()
        document.add(newTextField(TEXT_FIELD, "my dog has fleas", Field.Store.NO))
        return document
    }

    /** The marker RuntimeException that we use in lieu of an actual machine crash. */
    private class CrashingException(msg: String) : RuntimeException(msg)

    /**
     * This test class provides direct access to "simulating" a crash right after
     * realDirectory.createOutput(..) has been called on a certain specified name.
     */
    private class CrashAfterCreateOutput(realDirectory: Directory) : FilterDirectory(realDirectory) {
        private var crashAfterCreateOutput: String? = null

        fun setCrashAfterCreateOutput(name: String) {
            this.crashAfterCreateOutput = name
        }

        @Throws(IOException::class)
        override fun createOutput(name: String, context: IOContext): IndexOutput {
            val indexOutput = `in`.createOutput(name, context)
            if (crashAfterCreateOutput != null && name == crashAfterCreateOutput) {
                // CRASH!
                indexOutput.close()
                if (VERBOSE) {
                    println("TEST: now crash")
                    Throwable().printStackTrace()
                }
                throw CrashingException("crashAfterCreateOutput $crashAfterCreateOutput")
            }
            return indexOutput
        }
    }

    companion object {
        private const val TEXT_FIELD: String = "text"
    }
}
