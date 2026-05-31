package org.gnit.lucenekmp.analysis.core

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

class TestWhitespaceAnalyzer : BaseTokenStreamTestCase() {
    companion object {
        private const val LONGTOKEN =
            "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz" +
                "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz" +
                "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz"
    }

    @Test
    @Throws(IOException::class)
    fun testDefaultMaximumTokenLength() {
        WhitespaceAnalyzer().use { a: Analyzer ->
            assertAnalyzesTo(
                a,
                "$LONGTOKEN extra",
                arrayOf(
                    "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz" +
                        "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz" +
                        "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstu",
                    "vwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz",
                    "extra"
                )
            )
        }
    }

    @Test
    @Throws(IOException::class)
    fun testCustomMaximumTokenLength() {
        WhitespaceAnalyzer(1024).use { a: Analyzer ->
            assertAnalyzesTo(a, "$LONGTOKEN extra", arrayOf(LONGTOKEN, "extra"))
        }
    }
}

