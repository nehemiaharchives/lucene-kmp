package org.gnit.lucenekmp.analysis.en.ct

import okio.IOException
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

class TestBibleEnglishAnalyzer : BaseTokenStreamTestCase() {
    @Test
    @Throws(IOException::class)
    fun testWeptNormalizesToWeep() {
        val a = BibleEnglishAnalyzer()
        assertAnalyzesTo(a, "weep weeps weeping wept", arrayOf("weep", "weep", "weep", "weep"))
        a.close()
    }
}
