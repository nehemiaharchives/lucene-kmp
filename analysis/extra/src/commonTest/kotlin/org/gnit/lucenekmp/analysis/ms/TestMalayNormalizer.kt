package org.gnit.lucenekmp.analysis.ms

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

/** Test [MalayNormalizer]. */
class TestMalayNormalizer : BaseTokenStreamTestCase() {
    @Test
    @Throws(IOException::class)
    fun testDiacritics() {
        check("méja", "meja")
        check("mēja", "meja")
    }

    @Test
    @Throws(IOException::class)
    fun testPunctuation() {
        check("kata–kata", "kata-kata")
        check("rakyat’s", "rakyat's")
    }

    private fun check(input: String, output: String) {
        val analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer: Tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, MalayNormalizationFilter(tokenizer))
            }
        }
        checkOneTerm(analyzer, input, output)
        analyzer.close()
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, MalayNormalizationFilter(tokenizer))
            }
        }
        checkOneTerm(analyzer, "", "")
        analyzer.close()
    }
}
