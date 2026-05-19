package org.gnit.lucenekmp.analysis.vi.ct

import okio.IOException
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

class TestBibleVietnameseAnalyzer : BaseTokenStreamTestCase() {
    @Test
    @Throws(IOException::class)
    fun testJesusChristCompoundExpansion() {
        val a = BibleVietnameseAnalyzer()
        assertAnalyzesTo(
            a,
            "Jêsus-Christ",
            arrayOf("jesus", "christ"),
            posIncrements = intArrayOf(1, 1)
        )
        assertAnalyzesTo(
            a,
            "Jêsus Christ",
            arrayOf("jesus", "christ"),
            posIncrements = intArrayOf(1, 1)
        )
        assertAnalyzesTo(a, "Jêsus", arrayOf("jesus"))
        assertAnalyzesTo(a, "Christ", arrayOf("christ"))
        a.close()
    }
}
