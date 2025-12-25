package org.gnit.lucenekmp.tests.analysis

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.jdkport.Reader
import kotlin.test.Test

class TestMockCharFilter : BaseTokenStreamTestCase() {

    @Test
    @Throws(IOException::class)
    fun test() {
        val analyzer: Analyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): Analyzer.TokenStreamComponents {
                    val tokenizer: Tokenizer =
                        MockTokenizer(
                            MockTokenizer.WHITESPACE,
                            false
                        )
                    return Analyzer.TokenStreamComponents(
                        tokenizer,
                        tokenizer
                    )
                }

                override fun initReader(
                    fieldName: String,
                    reader: Reader
                ): Reader {
                    return MockCharFilter(reader, 7)
                }
            }

        assertAnalyzesTo(
            analyzer,
            "ab",
            arrayOf<String>("aab"),
            intArrayOf(0),
            intArrayOf(2)
        )

        assertAnalyzesTo(
            analyzer,
            "aba",
            arrayOf<String>("aabaa"),
            intArrayOf(0),
            intArrayOf(3)
        )

        assertAnalyzesTo(
            analyzer, "abcdefga", arrayOf<String>("aabcdefgaa"), intArrayOf(0), intArrayOf(8)
        )
    }
}
