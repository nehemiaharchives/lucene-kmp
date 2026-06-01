package org.gnit.lucenekmp.analysis.miscellaneous

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test
import kotlin.test.assertEquals

class TestLimitTokenCountAnalyzer : BaseTokenStreamTestCase() {
    @Test
    @Throws(IOException::class)
    fun testLimitTokenCountAnalyzer() {
        for (consumeAll in booleanArrayOf(true, false)) {
            val mock = MockAnalyzer(random())

            // if we are consuming all tokens, we can use the checks,
            // otherwise we can't
            mock.setEnableChecks(consumeAll)
            val a: Analyzer = LimitTokenCountAnalyzer(mock, 2, consumeAll)

            // dont use assertAnalyzesTo here, as the end offset is not the end of the string (unless
            // consumeAll is true, in which case it's correct)!
            assertTokenStreamContents(
                a.tokenStream("dummy", "1  2     3  4  5"),
                arrayOf("1", "2"),
                intArrayOf(0, 3),
                intArrayOf(1, 4),
                finalOffset = if (consumeAll) 16 else null
            )
            assertTokenStreamContents(
                a.tokenStream("dummy", "1 2 3 4 5"),
                arrayOf("1", "2"),
                intArrayOf(0, 2),
                intArrayOf(1, 3),
                finalOffset = if (consumeAll) 9 else null
            )

            // less than the limit, ensure we behave correctly
            assertTokenStreamContents(
                a.tokenStream("dummy", "1  "),
                arrayOf("1"),
                intArrayOf(0),
                intArrayOf(1),
                finalOffset = if (consumeAll) 3 else null
            )

            // equal to limit
            assertTokenStreamContents(
                a.tokenStream("dummy", "1  2  "),
                arrayOf("1", "2"),
                intArrayOf(0, 3),
                intArrayOf(1, 4),
                finalOffset = if (consumeAll) 6 else null
            )
            a.close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun testLimitTokenCountIndexWriter() {
        for (consumeAll in booleanArrayOf(true, false)) {
            val dir: Directory = newDirectory()
            val limit = TestUtil.nextInt(random(), 50, 101000)
            val mock = MockAnalyzer(random())

            // if we are consuming all tokens, we can use the checks,
            // otherwise we can't
            mock.setEnableChecks(consumeAll)
            val a: Analyzer = LimitTokenCountAnalyzer(mock, limit, consumeAll)

            val writer = IndexWriter(dir, IndexWriterConfig(a))

            val doc = org.gnit.lucenekmp.document.Document()
            val b = StringBuilder()
            for (i in 1 until limit) b.append(" a")
            b.append(" x")
            b.append(" z")
            doc.add(newTextField("field", b.toString(), Field.Store.NO))
            writer.addDocument(doc)
            writer.close()

            val reader = DirectoryReader.open(dir)
            var t = Term("field", "x")
            assertEquals(1, reader.docFreq(t))
            t = Term("field", "z")
            assertEquals(0, reader.docFreq(t))
            reader.close()
            dir.close()
            a.close()
        }
    }
}
