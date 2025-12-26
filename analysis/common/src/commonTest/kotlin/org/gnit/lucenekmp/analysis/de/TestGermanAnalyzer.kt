package org.gnit.lucenekmp.analysis.de

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.LowerCaseFilter
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.LetterTokenizer
import org.gnit.lucenekmp.analysis.miscellaneous.SetKeywordMarkerFilter
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

class TestGermanAnalyzer : BaseTokenStreamTestCase() {
    @Test
    @Throws(Exception::class)
    fun testReusableTokenStream() {
        val a: Analyzer = GermanAnalyzer()
        checkOneTerm(a, "Tisch", "tisch")
        checkOneTerm(a, "Tische", "tisch")
        checkOneTerm(a, "Tischen", "tisch")
        a.close()
    }

    @Test
    @Throws(IOException::class)
    fun testWithKeywordAttribute() {
        val set = CharArraySet(1, true)
        set.add("fischen")
        val `in`: Tokenizer = LetterTokenizer()
        `in`.setReader(StringReader("Fischen Trinken"))
        val filter = GermanStemFilter(SetKeywordMarkerFilter(LowerCaseFilter(`in`), set))
        assertTokenStreamContents(filter, arrayOf("fischen", "trink"))
    }

    @Test
    @Throws(Exception::class)
    fun testStemExclusionTable() {
        val a = GermanAnalyzer(CharArraySet.EMPTY_SET, CharArraySet(mutableSetOf<Any>("tischen"), false))
        checkOneTerm(a, "tischen", "tischen")
        a.close()
    }

    /**
     * test some features of the new snowball filter these only pass with LATEST, not if you use
     * GermanStemmer
     */
    @Test
    @Throws(Exception::class)
    fun testGermanSpecials() {
        val a = GermanAnalyzer()
        checkOneTerm(a, "Schaltflächen", "schaltflach")
        checkOneTerm(a, "Schaltflaechen", "schaltflach")
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val a = GermanAnalyzer()
        checkRandomData(random(), a, 200 * RANDOM_MULTIPLIER)
        a.close()
    }
}
