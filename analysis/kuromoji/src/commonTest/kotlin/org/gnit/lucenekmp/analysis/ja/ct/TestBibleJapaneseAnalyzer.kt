package org.gnit.lucenekmp.analysis.ja.ct

import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

class TestBibleJapaneseAnalyzer : BaseTokenStreamTestCase() {

    @Test
    fun testNormalizeJesusChristOrder() {
        val analyzer = BibleJapaneseAnalyzer()
        assertAnalyzesTo(analyzer, "キリスト・イエス", arrayOf("イエス", "キリスト"))
        analyzer.close()
    }

    @Test
    fun testCanonicalOrderUnchanged() {
        val analyzer = BibleJapaneseAnalyzer()
        assertAnalyzesTo(analyzer, "イエス・キリスト", arrayOf("イエス", "キリスト"))
        analyzer.close()
    }
}
