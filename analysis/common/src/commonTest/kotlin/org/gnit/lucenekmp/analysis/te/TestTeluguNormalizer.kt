package org.gnit.lucenekmp.analysis.te

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test

/** Test [TeluguNormalizer]. */
class TestTeluguNormalizer : BaseTokenStreamTestCase() {
    /** Long matras should be shortened. */
    @Test
    @Throws(IOException::class)
    fun testMatra() {
        check("పదాలూ", "పదాలు")
        check("అబ్బాయీ", "అబ్బాయి")
    }

    @Test
    @Throws(IOException::class)
    fun testVowels() {
        // removal of visarga (ః)
        check("ఃౌైాిు", "ౌైాిు")
        // vowel shortening
        check("ఔఐఆఈఊ", "ఓఏఅఇఉ")
    }

    private fun check(input: String, output: String) {
        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(StringReader(input))
        val tf: TokenFilter = TeluguNormalizationFilter(tokenizer)
        assertTokenStreamContents(tf, arrayOf(output))
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val a = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, TeluguNormalizationFilter(tokenizer))
            }
        }
        checkOneTerm(a, "", "")
        a.close()
    }
}
