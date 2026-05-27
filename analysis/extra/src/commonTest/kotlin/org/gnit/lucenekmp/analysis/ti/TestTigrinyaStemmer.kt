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

/** Test [TigrinyaStemmer]. */
class TestTigrinyaStemmer : BaseTokenStreamTestCase() {
    /** Test conservative plural and possessive endings. */
    @Test
    @Throws(IOException::class)
    fun testNominals() {
        check("መጽሀፍታት", "መጽሀፍ")
        check("መጽሀፍኩም", "መጽሀፍ")
        check("መጽሀፍክን", "መጽሀፍ")
        check("መጽሀፍና", "መጽሀፍ")
    }

    /** Test prepositional object-like endings. */
    @Test
    @Throws(IOException::class)
    fun testObjectSuffixes() {
        check("ኸፊተለይ", "ኸፊተ")
        check("ኸፊተልካ", "ኸፊተ")
        check("ኸፊተላ", "ኸፊተ")
    }

    private fun check(input: String, output: String) {
        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(StringReader(input))
        val tf: TokenFilter = TigrinyaStemFilter(tokenizer)
        assertTokenStreamContents(tf, arrayOf(output))
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, TigrinyaStemFilter(tokenizer))
            }
        }
        checkOneTerm(analyzer, "", "")
        analyzer.close()
    }
}
