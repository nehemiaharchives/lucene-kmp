package org.gnit.lucenekmp.analysis.ti

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test

/** Test [TigrinyaNormalizer]. */
class TestTigrinyaNormalizer : BaseTokenStreamTestCase() {
    /** Test equivalent Ethiopic series normalization. */
    @Test
    @Throws(IOException::class)
    fun testBasics() {
        check("ሠላም", "ሰላም")
        check("መፅሐፍ", "መጽሀፍ")
        check("ዓዲ", "አዲ")
    }

    @Test
    @Throws(IOException::class)
    fun testPunctuation() {
        check("ይኹን’ምበር", "ይኹን'ምበር")
        check("ትግርኛ–መጽሓፍ", "ትግርኛ-መጽሀፍ")
    }

    @Test
    @Throws(IOException::class)
    fun testWawVariants() {
        check("ዉሽጢ", "ውሽጢ")
        check("ዎ", "ወ")
    }

    private fun check(input: String, output: String) {
        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(StringReader(input))
        val tf: TokenFilter = TigrinyaNormalizationFilter(tokenizer)
        assertTokenStreamContents(tf, arrayOf(output))
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val a = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, TigrinyaNormalizationFilter(tokenizer))
            }
        }
        checkOneTerm(a, "", "")
        a.close()
    }
}
