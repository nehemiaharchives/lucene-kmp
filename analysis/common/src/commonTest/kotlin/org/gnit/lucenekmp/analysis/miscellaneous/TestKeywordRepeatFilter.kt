package org.gnit.lucenekmp.analysis.miscellaneous

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.en.PorterStemFilter
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

class TestKeywordRepeatFilter : BaseTokenStreamTestCase() {
    @Test
    @Throws(IOException::class)
    fun testBasic() {
        val ts: TokenStream = RemoveDuplicatesTokenFilter(
            PorterStemFilter(
                KeywordRepeatFilter(whitespaceMockTokenizer("the birds are flying"))
            )
        )
        assertTokenStreamContents(
            ts,
            arrayOf("the", "birds", "bird", "are", "ar", "flying", "fly"),
            intArrayOf(1, 1, 0, 1, 0, 1, 0)
        )
    }

    @Test
    @Throws(IOException::class)
    fun testComposition() {
        val ts: TokenStream = RemoveDuplicatesTokenFilter(
            PorterStemFilter(
                KeywordRepeatFilter(
                    KeywordRepeatFilter(whitespaceMockTokenizer("the birds are flying"))
                )
            )
        )
        assertTokenStreamContents(
            ts,
            arrayOf("the", "birds", "bird", "are", "ar", "flying", "fly"),
            intArrayOf(1, 1, 0, 1, 0, 1, 0)
        )
    }
}
