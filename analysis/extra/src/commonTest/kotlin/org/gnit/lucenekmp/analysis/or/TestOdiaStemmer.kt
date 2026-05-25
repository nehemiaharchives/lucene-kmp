package org.gnit.lucenekmp.analysis.or

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test

/** Test [OdiaStemmer]. */
class TestOdiaStemmer : BaseTokenStreamTestCase() {
    /** Test noun inflections and postpositions */
    @Test
    @Throws(IOException::class)
    fun testNouns() {
        check("ଘରକୁ", "ଘର")
        check("ଘରରେ", "ଘର")
        check("ଘରଠାରୁ", "ଘର")
        check("ପିଲାମାନେ", "ପିଲା")
        check("ପିଲାମାନଙ୍କର", "ପିଲା")
        check("ବହିଗୁଡ଼ିକ", "ବହି")
        check("ସମସ୍ୟାଗୁଡ଼ିକର", "ସମସ୍ୟା")
        check("ସମସ୍ୟାଗୁଡ଼ିକର", "ସମସ୍ୟା")
        check("ଗଣମାଧ୍ୟମଗୁଡ଼ିକରେ", "ଗଣମାଧ୍ୟମ")
        check("ଗଣମାଧ୍ୟମଗୁଡ଼ିକରେ", "ଗଣମାଧ୍ୟମ")
        check("ସ୍ନେହୀଜନମାନଙ୍କଠାରୁ", "ସ୍ନେହୀଜନ")
        check("ଭବନଟିକୁ", "ଭବନ")
    }

    /** Test verb and infinitive suffixes */
    @Test
    @Throws(IOException::class)
    fun testVerbs() {
        check("ଲେଖିବା", "ଲେଖ")
        check("କରିବାକୁ", "କର")
        check("କହୁଛନ୍ତି", "କହୁ")
    }

    private fun check(input: String, output: String) {
        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(StringReader(input))
        val tf: TokenFilter = OdiaStemFilter(tokenizer)
        assertTokenStreamContents(tf, arrayOf(output))
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, OdiaStemFilter(tokenizer))
            }
        }
        checkOneTerm(analyzer, "", "")
        analyzer.close()
    }
}
