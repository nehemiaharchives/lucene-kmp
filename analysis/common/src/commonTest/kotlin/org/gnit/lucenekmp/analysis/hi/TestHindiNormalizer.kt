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

/** Test [HindiNormalizer]. */
class TestHindiNormalizer : BaseTokenStreamTestCase() {
    /** Test some basic normalization, with an example from the paper. */
    @Test
    @Throws(IOException::class)
    fun testBasics() {
        check("अँगरेज़ी", "अंगरेजि")
        check("अँगरेजी", "अंगरेजि")
        check("अँग्रेज़ी", "अंगरेजि")
        check("अँग्रेजी", "अंगरेजि")
        check("अंगरेज़ी", "अंगरेजि")
        check("अंगरेजी", "अंगरेजि")
        check("अंग्रेज़ी", "अंगरेजि")
        check("अंग्रेजी", "अंगरेजि")
    }

    @Test
    @Throws(IOException::class)
    fun testDecompositions() {
        // removing nukta dot
        check("क़िताब", "किताब")
        check("फ़र्ज़", "फरज")
        check("क़र्ज़", "करज")
        // some other composed nukta forms
        check("ऱऴख़ग़ड़ढ़य़", "रळखगडढय")
        // removal of format (ZWJ/ZWNJ)
        check("शार्‍मा", "शारमा")
        check("शार्‌मा", "शारमा")
        // removal of chandra
        check("ॅॆॉॊऍऎऑऒ\u0972", "ेेोोएएओओअ")
        // vowel shortening
        check("आईऊॠॡऐऔीूॄॣैौ", "अइउऋऌएओिुृॢेो")
    }

    private fun check(input: String, output: String) {
        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(StringReader(input))
        val tf: TokenFilter = HindiNormalizationFilter(tokenizer)
        assertTokenStreamContents(tf, arrayOf(output))
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val a = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, HindiNormalizationFilter(tokenizer))
            }
        }
        checkOneTerm(a, "", "")
        a.close()
    }
}
