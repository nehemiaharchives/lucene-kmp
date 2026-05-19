package org.gnit.lucenekmp.analysis.hi.ct

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

class TestBibleHindiAnalyzer : BaseTokenStreamTestCase() {
    @Test
    @Throws(IOException::class)
    fun testJesusStaysDistinctFromYishaiStem() {
        val analyzer: Analyzer = BibleHindiAnalyzer()
        assertAnalyzesTo(analyzer, "यीशु", arrayOf("यीशु"))
        assertAnalyzesTo(analyzer, "यिशै", arrayOf("यिश"))
        assertAnalyzesTo(analyzer, "यीशु मसीह", arrayOf("यीशु", "मसीह"))
        analyzer.close()
    }

    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val analyzer: Analyzer = BibleHindiAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }
}
