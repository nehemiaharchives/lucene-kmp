package org.gnit.lucenekmp.analysis.tr

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

/** Test the Turkish lowercase filter. */
class TestTurkishLowerCaseFilter : BaseTokenStreamTestCase() {

    /** Test composed forms */
    @Test
    @Throws(Exception::class)
    fun testTurkishLowerCaseFilter() {
        val stream: TokenStream = whitespaceMockTokenizer("\u0130STANBUL \u0130ZM\u0130R ISPARTA")
        val filter = TurkishLowerCaseFilter(stream)
        assertTokenStreamContents(
            filter,
            arrayOf(
                "istanbul", "izmir", "\u0131sparta",
            )
        )
    }

    /** Test decomposed forms */
    @Test
    @Throws(Exception::class)
    fun testDecomposed() {
        val stream: TokenStream =
            whitespaceMockTokenizer("\u0049\u0307STANBUL \u0049\u0307ZM\u0049\u0307R ISPARTA")
        val filter = TurkishLowerCaseFilter(stream)
        assertTokenStreamContents(
            filter,
            arrayOf(
                "istanbul", "izmir", "\u0131sparta",
            )
        )
    }

    /**
     * Test decomposed forms with additional accents In this example, U+0049 + U+0316 + U+0307 is
     * canonically equivalent to U+0130 + U+0316, and is lowercased the same way.
     */
    @Test
    @Throws(Exception::class)
    fun testDecomposed2() {
        val stream: TokenStream =
            whitespaceMockTokenizer(
                "\u0049\u0316\u0307STANBUL \u0049\u0307ZM\u0049\u0307R I\u0316SPARTA"
            )
        val filter = TurkishLowerCaseFilter(stream)
        assertTokenStreamContents(
            filter,
            arrayOf(
                "i\u0316stanbul", "izmir", "\u0131\u0316sparta",
            )
        )
    }

    @Test
    @Throws(Exception::class)
    fun testDecomposed3() {
        val stream: TokenStream = whitespaceMockTokenizer("\u0049\u0307")
        val filter = TurkishLowerCaseFilter(stream)
        assertTokenStreamContents(filter, arrayOf("i"))
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val a: Analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer: Tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, TurkishLowerCaseFilter(tokenizer))
            }
        }
        checkOneTerm(a, "", "")
        a.close()
    }
}
