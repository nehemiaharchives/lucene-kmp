package org.gnit.lucenekmp.analysis.ga

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

/** Test the Irish lowercase filter. */
class TestIrishLowerCaseFilter : BaseTokenStreamTestCase() {

    /** Test lowercase */
    @Test
    @Throws(Exception::class)
    fun testIrishLowerCaseFilter() {
        val stream: TokenStream = whitespaceMockTokenizer("nAthair tUISCE hARD")
        val filter = IrishLowerCaseFilter(stream)
        assertTokenStreamContents(
            filter,
            arrayOf(
                "n-athair", "t-uisce", "hard"
            )
        )
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val a =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer = KeywordTokenizer()
                    return TokenStreamComponents(tokenizer, IrishLowerCaseFilter(tokenizer))
                }
            }
        checkOneTerm(a, "", "")
        a.close()
    }
}
