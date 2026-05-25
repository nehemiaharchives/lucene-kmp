package org.gnit.lucenekmp.analysis.he

import kotlin.test.Test

class TestASCIIFoldingFilter {
    @Test
    fun testLatinAccentFolding() {
        val a = HebrewExactAnalyzer(HebrewTestUtil.dictionary)
        HebrewTestUtil.assertAnalyzesTo(a, "Légion d'Honneur", arrayOf("legion$", "d'honneur$"))
    }
}
