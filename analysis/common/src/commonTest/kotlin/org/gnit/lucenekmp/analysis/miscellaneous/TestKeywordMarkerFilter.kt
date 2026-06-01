package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.KeywordAttribute
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

/** Testcase for [KeywordMarkerFilter] */
class TestKeywordMarkerFilter : BaseTokenStreamTestCase() {
    private fun charArraySetOf(ignoreCase: Boolean, vararg terms: String): CharArraySet {
        val set = CharArraySet(terms.size, ignoreCase)
        for (term in terms) {
            set.add(term)
        }
        return set
    }

    @Test
    fun testSetFilterIncrementToken() {
        val set = CharArraySet(5, true)
        set.add("lucenefox")
        val output = arrayOf("the", "quick", "brown", "LuceneFox", "jumps")
        assertTokenStreamContents(
            LowerCaseFilterMock(
                SetKeywordMarkerFilter(
                    whitespaceMockTokenizer("The quIck browN LuceneFox Jumps"),
                    set
                )
            ),
            output
        )
        val mixedCaseSet = charArraySetOf(false, "LuceneFox")
        assertTokenStreamContents(
            LowerCaseFilterMock(
                SetKeywordMarkerFilter(
                    whitespaceMockTokenizer("The quIck browN LuceneFox Jumps"),
                    mixedCaseSet
                )
            ),
            output
        )
        val set2 = set
        assertTokenStreamContents(
            LowerCaseFilterMock(
                SetKeywordMarkerFilter(
                    whitespaceMockTokenizer("The quIck browN LuceneFox Jumps"),
                    set2
                )
            ),
            output
        )
    }

    @Test
    fun testPatternFilterIncrementToken() {
        var output = arrayOf("the", "quick", "brown", "LuceneFox", "jumps")
        assertTokenStreamContents(
            LowerCaseFilterMock(
                PatternKeywordMarkerFilter(
                    whitespaceMockTokenizer("The quIck browN LuceneFox Jumps"),
                    Regex("[a-zA-Z]+[fF]ox")
                )
            ),
            output
        )

        output = arrayOf("the", "quick", "brown", "lucenefox", "jumps")
        assertTokenStreamContents(
            LowerCaseFilterMock(
                PatternKeywordMarkerFilter(
                    whitespaceMockTokenizer("The quIck browN LuceneFox Jumps"),
                    Regex("[a-zA-Z]+[f]ox")
                )
            ),
            output
        )
    }

    // LUCENE-2901
    @Test
    fun testComposition() {
        var ts: TokenStream =
            LowerCaseFilterMock(
                SetKeywordMarkerFilter(
                    SetKeywordMarkerFilter(
                        whitespaceMockTokenizer("Dogs Trees Birds Houses"),
                        charArraySetOf(false, "Birds", "Houses")
                    ),
                    charArraySetOf(false, "Dogs", "Trees")
                )
            )
        assertTokenStreamContents(ts, arrayOf("Dogs", "Trees", "Birds", "Houses"))

        ts =
            LowerCaseFilterMock(
                PatternKeywordMarkerFilter(
                    PatternKeywordMarkerFilter(
                        whitespaceMockTokenizer("Dogs Trees Birds Houses"),
                        Regex("Birds|Houses")
                    ),
                    Regex("Dogs|Trees")
                )
            )
        assertTokenStreamContents(ts, arrayOf("Dogs", "Trees", "Birds", "Houses"))

        ts =
            LowerCaseFilterMock(
                SetKeywordMarkerFilter(
                    PatternKeywordMarkerFilter(
                        whitespaceMockTokenizer("Dogs Trees Birds Houses"),
                        Regex("Birds|Houses")
                    ),
                    charArraySetOf(false, "Dogs", "Trees")
                )
            )
        assertTokenStreamContents(ts, arrayOf("Dogs", "Trees", "Birds", "Houses"))
    }

    class LowerCaseFilterMock(`in`: TokenStream) : TokenFilter(`in`) {
        private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
        private val keywordAttr: KeywordAttribute = addAttribute(KeywordAttribute::class)

        override fun incrementToken(): Boolean {
            if (input.incrementToken()) {
                if (!keywordAttr.isKeyword) {
                    val term = termAtt.toString().lowercase()
                    termAtt.setEmpty()!!.append(term)
                }
                return true
            }
            return false
        }
    }
}
