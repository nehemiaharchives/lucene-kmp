package org.gnit.lucenekmp.analysis.am

import okio.IOException
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.miscellaneous.SetKeywordMarkerFilter
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test

/** Tests Amharic filter composition behavior below [AmharicAnalyzer]. */
class TestAmharicFilters : BaseTokenStreamTestCase() {
    @Test
    @Throws(IOException::class)
    fun testNormalizationThenStemming() {
        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(StringReader("ሃገር መጽሐፎችን"))
        var stream: TokenStream = AmharicNormalizationFilter(tokenizer)
        stream = AmharicStemFilter(stream)
        assertTokenStreamContents(stream, arrayOf("hager", "መፅሀፍ"))
    }

    @Test
    @Throws(IOException::class)
    fun testKeywordTermsAreNotStemmedButStillCanBeNormalizedBeforeMarker() {
        val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenizer.setReader(StringReader("ሃገር ሀገር"))
        var stream: TokenStream = AmharicNormalizationFilter(tokenizer)
        stream = SetKeywordMarkerFilter(stream, CharArraySet(mutableSetOf<Any>("ሀገር"), false))
        stream = AmharicStemFilter(stream)
        assertTokenStreamContents(stream, arrayOf("ሀገር", "ሀገር"))
    }
}
