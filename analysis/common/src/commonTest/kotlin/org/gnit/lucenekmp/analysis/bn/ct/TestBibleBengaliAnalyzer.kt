package org.gnit.lucenekmp.analysis.bn.ct

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.bn.ct.BibleBengaliAnalyzer
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

class TestBibleBengaliAnalyzer : BaseTokenStreamTestCase() {
    @Test
    fun testJesusChristFormsNeedCanonicalization() {
        val analyzer: Analyzer = BibleBengaliAnalyzer()
        assertAnalyzesTo(analyzer, "যীশু", arrayOf("যিসু"))
        assertAnalyzesTo(analyzer, "যীশুর", arrayOf("যিসুর", "যিসু"))
        assertAnalyzesTo(analyzer, "খ্রীষ্ট", arrayOf("খ্রিস্ট"))
        assertAnalyzesTo(analyzer, "খ্রীষ্টের", arrayOf("খ্রিস্টের", "খ্রিস্ট"))
        analyzer.close()
    }

    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val analyzer: Analyzer = BibleBengaliAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }
}
