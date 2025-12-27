package org.gnit.lucenekmp.analysis.hi

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test

/** Test [HindiStemmer]. */
class TestHindiStemmer : BaseTokenStreamTestCase() {
    /** Test masc noun inflections */
    @Test
    @Throws(IOException::class)
    fun testMasculineNouns() {
        check("लडका", "लडक")
        check("लडके", "लडक")
        check("लडकों", "लडक")

        check("गुरु", "गुर")
        check("गुरुओं", "गुर")

        check("दोस्त", "दोस्त")
        check("दोस्तों", "दोस्त")
    }

    /** Test feminine noun inflections */
    @Test
    @Throws(IOException::class)
    fun testFeminineNouns() {
        check("लडकी", "लडक")
        check("लडकियों", "लडक")

        check("किताब", "किताब")
        check("किताबें", "किताब")
        check("किताबों", "किताब")

        check("आध्यापीका", "आध्यापीक")
        check("आध्यापीकाएं", "आध्यापीक")
        check("आध्यापीकाओं", "आध्यापीक")
    }

    /** Test some verb forms */
    @Test
    @Throws(IOException::class)
    fun testVerbs() {
        check("खाना", "खा")
        check("खाता", "खा")
        check("खाती", "खा")
        check("खा", "खा")
    }

    /**
     * From the paper: since the suffix list for verbs includes AI, awA and anI, additional suffixes
     * had to be added to the list for noun/adjectives ending with these endings.
     */
    @Test
    @Throws(IOException::class)
    fun testExceptions() {
        check("कठिनाइयां", "कठिन")
        check("कठिन", "कठिन")
    }

    private fun check(input: String, output: String) {
        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(StringReader(input))
        val tf: TokenFilter = HindiStemFilter(tokenizer)
        assertTokenStreamContents(tf, arrayOf(output))
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, HindiStemFilter(tokenizer))
            }
        }
        checkOneTerm(analyzer, "", "")
        analyzer.close()
    }
}
