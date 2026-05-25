package org.gnit.lucenekmp.analysis.om

import okio.IOException
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.LowerCaseFilter
import org.gnit.lucenekmp.analysis.miscellaneous.SetKeywordMarkerFilter
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test

/** Tests Oromo filter composition behavior below [OromoAnalyzer]. */
class TestOromoFilters : BaseTokenStreamTestCase() {
    @Test
    @Throws(IOException::class)
    fun testLowercaseNormalizationThenStemming() {
        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(StringReader("Ameerikaatti Fedhi"))
        var stream: TokenStream = LowerCaseFilter(tokenizer)
        stream = OromoNormalizationFilter(stream)
        stream = OromoStemFilter(stream)
        assertTokenStreamContents(stream, arrayOf("ameerikaa", "fedh"))
    }

    @Test
    @Throws(IOException::class)
    fun testKeywordTermsAreNotStemmedButStillCanBeNormalizedBeforeMarker() {
        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(StringReader("fedhi fedhiʼ"))
        var stream: TokenStream = OromoNormalizationFilter(tokenizer)
        stream = SetKeywordMarkerFilter(stream, CharArraySet(mutableSetOf<Any>("fedhi"), false))
        stream = OromoStemFilter(stream)
        assertTokenStreamContents(stream, arrayOf("fedhi", "fedhi'"))
    }
}
