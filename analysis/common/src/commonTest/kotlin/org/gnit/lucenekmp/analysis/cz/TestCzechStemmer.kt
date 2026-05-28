package org.gnit.lucenekmp.analysis.cz

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.analysis.miscellaneous.SetKeywordMarkerFilter
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test

/** Test the Czech stemmer. */
class TestCzechStemmer : BaseTokenStreamTestCase() {
    /** Test showing how masculine noun forms conflate. */
    @Test
    @Throws(Exception::class)
    fun testMasculineNouns() {
        val cz = CzechAnalyzer()

        assertAnalyzesTo(cz, "pán", arrayOf("pán"))
        assertAnalyzesTo(cz, "páni", arrayOf("pán"))
        assertAnalyzesTo(cz, "pánové", arrayOf("pán"))
        assertAnalyzesTo(cz, "pána", arrayOf("pán"))
        assertAnalyzesTo(cz, "pánů", arrayOf("pán"))
        assertAnalyzesTo(cz, "pánovi", arrayOf("pán"))
        assertAnalyzesTo(cz, "pánům", arrayOf("pán"))
        assertAnalyzesTo(cz, "pány", arrayOf("pán"))
        assertAnalyzesTo(cz, "páne", arrayOf("pán"))
        assertAnalyzesTo(cz, "pánech", arrayOf("pán"))
        assertAnalyzesTo(cz, "pánem", arrayOf("pán"))

        assertAnalyzesTo(cz, "hrad", arrayOf("hrad"))
        assertAnalyzesTo(cz, "hradu", arrayOf("hrad"))
        assertAnalyzesTo(cz, "hrade", arrayOf("hrad"))
        assertAnalyzesTo(cz, "hradem", arrayOf("hrad"))
        assertAnalyzesTo(cz, "hrady", arrayOf("hrad"))
        assertAnalyzesTo(cz, "hradech", arrayOf("hrad"))
        assertAnalyzesTo(cz, "hradům", arrayOf("hrad"))
        assertAnalyzesTo(cz, "hradů", arrayOf("hrad"))

        assertAnalyzesTo(cz, "muž", arrayOf("muh"))
        assertAnalyzesTo(cz, "muži", arrayOf("muh"))
        assertAnalyzesTo(cz, "muže", arrayOf("muh"))
        assertAnalyzesTo(cz, "mužů", arrayOf("muh"))
        assertAnalyzesTo(cz, "mužům", arrayOf("muh"))
        assertAnalyzesTo(cz, "mužích", arrayOf("muh"))
        assertAnalyzesTo(cz, "mužem", arrayOf("muh"))

        assertAnalyzesTo(cz, "stroj", arrayOf("stroj"))
        assertAnalyzesTo(cz, "stroje", arrayOf("stroj"))
        assertAnalyzesTo(cz, "strojů", arrayOf("stroj"))
        assertAnalyzesTo(cz, "stroji", arrayOf("stroj"))
        assertAnalyzesTo(cz, "strojům", arrayOf("stroj"))
        assertAnalyzesTo(cz, "strojích", arrayOf("stroj"))
        assertAnalyzesTo(cz, "strojem", arrayOf("stroj"))

        assertAnalyzesTo(cz, "předseda", arrayOf("předsd"))
        assertAnalyzesTo(cz, "předsedové", arrayOf("předsd"))
        assertAnalyzesTo(cz, "předsedy", arrayOf("předsd"))
        assertAnalyzesTo(cz, "předsedů", arrayOf("předsd"))
        assertAnalyzesTo(cz, "předsedovi", arrayOf("předsd"))
        assertAnalyzesTo(cz, "předsedům", arrayOf("předsd"))
        assertAnalyzesTo(cz, "předsedu", arrayOf("předsd"))
        assertAnalyzesTo(cz, "předsedo", arrayOf("předsd"))
        assertAnalyzesTo(cz, "předsedech", arrayOf("předsd"))
        assertAnalyzesTo(cz, "předsedou", arrayOf("předsd"))

        assertAnalyzesTo(cz, "soudce", arrayOf("soudk"))
        assertAnalyzesTo(cz, "soudci", arrayOf("soudk"))
        assertAnalyzesTo(cz, "soudců", arrayOf("soudk"))
        assertAnalyzesTo(cz, "soudcům", arrayOf("soudk"))
        assertAnalyzesTo(cz, "soudcích", arrayOf("soudk"))
        assertAnalyzesTo(cz, "soudcem", arrayOf("soudk"))

        cz.close()
    }

    /** Test showing how feminine noun forms conflate. */
    @Test
    @Throws(Exception::class)
    fun testFeminineNouns() {
        val cz = CzechAnalyzer()

        assertAnalyzesTo(cz, "kost", arrayOf("kost"))
        assertAnalyzesTo(cz, "kosti", arrayOf("kost"))
        assertAnalyzesTo(cz, "kostí", arrayOf("kost"))
        assertAnalyzesTo(cz, "kostem", arrayOf("kost"))
        assertAnalyzesTo(cz, "kostech", arrayOf("kost"))
        assertAnalyzesTo(cz, "kostmi", arrayOf("kost"))

        assertAnalyzesTo(cz, "píseň", arrayOf("písň"))
        assertAnalyzesTo(cz, "písně", arrayOf("písn"))
        assertAnalyzesTo(cz, "písni", arrayOf("písn"))
        assertAnalyzesTo(cz, "písněmi", arrayOf("písn"))
        assertAnalyzesTo(cz, "písních", arrayOf("písn"))
        assertAnalyzesTo(cz, "písním", arrayOf("písn"))

        assertAnalyzesTo(cz, "růže", arrayOf("růh"))
        assertAnalyzesTo(cz, "růží", arrayOf("růh"))
        assertAnalyzesTo(cz, "růžím", arrayOf("růh"))
        assertAnalyzesTo(cz, "růžích", arrayOf("růh"))
        assertAnalyzesTo(cz, "růžemi", arrayOf("růh"))
        assertAnalyzesTo(cz, "růži", arrayOf("růh"))

        assertAnalyzesTo(cz, "žena", arrayOf("žn"))
        assertAnalyzesTo(cz, "ženy", arrayOf("žn"))
        assertAnalyzesTo(cz, "žen", arrayOf("žn"))
        assertAnalyzesTo(cz, "ženě", arrayOf("žn"))
        assertAnalyzesTo(cz, "ženám", arrayOf("žn"))
        assertAnalyzesTo(cz, "ženu", arrayOf("žn"))
        assertAnalyzesTo(cz, "ženo", arrayOf("žn"))
        assertAnalyzesTo(cz, "ženách", arrayOf("žn"))
        assertAnalyzesTo(cz, "ženou", arrayOf("žn"))
        assertAnalyzesTo(cz, "ženami", arrayOf("žn"))

        cz.close()
    }

    /** Test showing how neuter noun forms conflate. */
    @Test
    @Throws(Exception::class)
    fun testNeuterNouns() {
        val cz = CzechAnalyzer()

        assertAnalyzesTo(cz, "město", arrayOf("měst"))
        assertAnalyzesTo(cz, "města", arrayOf("měst"))
        assertAnalyzesTo(cz, "měst", arrayOf("měst"))
        assertAnalyzesTo(cz, "městu", arrayOf("měst"))
        assertAnalyzesTo(cz, "městům", arrayOf("měst"))
        assertAnalyzesTo(cz, "městě", arrayOf("měst"))
        assertAnalyzesTo(cz, "městech", arrayOf("měst"))
        assertAnalyzesTo(cz, "městem", arrayOf("měst"))
        assertAnalyzesTo(cz, "městy", arrayOf("měst"))

        assertAnalyzesTo(cz, "moře", arrayOf("moř"))
        assertAnalyzesTo(cz, "moří", arrayOf("moř"))
        assertAnalyzesTo(cz, "mořím", arrayOf("moř"))
        assertAnalyzesTo(cz, "moři", arrayOf("moř"))
        assertAnalyzesTo(cz, "mořích", arrayOf("moř"))
        assertAnalyzesTo(cz, "mořem", arrayOf("moř"))

        assertAnalyzesTo(cz, "kuře", arrayOf("kuř"))
        assertAnalyzesTo(cz, "kuřata", arrayOf("kuř"))
        assertAnalyzesTo(cz, "kuřete", arrayOf("kuř"))
        assertAnalyzesTo(cz, "kuřat", arrayOf("kuř"))
        assertAnalyzesTo(cz, "kuřeti", arrayOf("kuř"))
        assertAnalyzesTo(cz, "kuřatům", arrayOf("kuř"))
        assertAnalyzesTo(cz, "kuřatech", arrayOf("kuř"))
        assertAnalyzesTo(cz, "kuřetem", arrayOf("kuř"))
        assertAnalyzesTo(cz, "kuřaty", arrayOf("kuř"))

        assertAnalyzesTo(cz, "stavení", arrayOf("stavn"))
        assertAnalyzesTo(cz, "stavením", arrayOf("stavn"))
        assertAnalyzesTo(cz, "staveních", arrayOf("stavn"))
        assertAnalyzesTo(cz, "staveními", arrayOf("stavn"))

        cz.close()
    }

    /** Test showing how adjectival forms conflate. */
    @Test
    @Throws(Exception::class)
    fun testAdjectives() {
        val cz = CzechAnalyzer()

        assertAnalyzesTo(cz, "mladý", arrayOf("mlad"))
        assertAnalyzesTo(cz, "mladí", arrayOf("mlad"))
        assertAnalyzesTo(cz, "mladého", arrayOf("mlad"))
        assertAnalyzesTo(cz, "mladých", arrayOf("mlad"))
        assertAnalyzesTo(cz, "mladému", arrayOf("mlad"))
        assertAnalyzesTo(cz, "mladým", arrayOf("mlad"))
        assertAnalyzesTo(cz, "mladé", arrayOf("mlad"))
        assertAnalyzesTo(cz, "mladém", arrayOf("mlad"))
        assertAnalyzesTo(cz, "mladými", arrayOf("mlad"))
        assertAnalyzesTo(cz, "mladá", arrayOf("mlad"))
        assertAnalyzesTo(cz, "mladou", arrayOf("mlad"))

        assertAnalyzesTo(cz, "jarní", arrayOf("jarn"))
        assertAnalyzesTo(cz, "jarního", arrayOf("jarn"))
        assertAnalyzesTo(cz, "jarních", arrayOf("jarn"))
        assertAnalyzesTo(cz, "jarnímu", arrayOf("jarn"))
        assertAnalyzesTo(cz, "jarním", arrayOf("jarn"))
        assertAnalyzesTo(cz, "jarními", arrayOf("jarn"))

        cz.close()
    }

    /** Test some possessive suffixes. */
    @Test
    @Throws(Exception::class)
    fun testPossessive() {
        val cz = CzechAnalyzer()
        assertAnalyzesTo(cz, "Karlův", arrayOf("karl"))
        assertAnalyzesTo(cz, "jazykový", arrayOf("jazyk"))
        cz.close()
    }

    /** Test some exceptional rules, implemented as rewrites. */
    @Test
    @Throws(Exception::class)
    fun testExceptions() {
        val cz = CzechAnalyzer()

        assertAnalyzesTo(cz, "český", arrayOf("česk"))
        assertAnalyzesTo(cz, "čeští", arrayOf("česk"))
        assertAnalyzesTo(cz, "anglický", arrayOf("anglick"))
        assertAnalyzesTo(cz, "angličtí", arrayOf("anglick"))
        assertAnalyzesTo(cz, "kniha", arrayOf("knih"))
        assertAnalyzesTo(cz, "knize", arrayOf("knih"))
        assertAnalyzesTo(cz, "mazat", arrayOf("mah"))
        assertAnalyzesTo(cz, "mažu", arrayOf("mah"))
        assertAnalyzesTo(cz, "kluk", arrayOf("kluk"))
        assertAnalyzesTo(cz, "kluci", arrayOf("kluk"))
        assertAnalyzesTo(cz, "klucích", arrayOf("kluk"))
        assertAnalyzesTo(cz, "hezký", arrayOf("hezk"))
        assertAnalyzesTo(cz, "hezčí", arrayOf("hezk"))
        assertAnalyzesTo(cz, "hůl", arrayOf("hol"))
        assertAnalyzesTo(cz, "hole", arrayOf("hol"))
        assertAnalyzesTo(cz, "deska", arrayOf("desk"))
        assertAnalyzesTo(cz, "desek", arrayOf("desk"))

        cz.close()
    }

    /** Test that very short words are not stemmed. */
    @Test
    @Throws(Exception::class)
    fun testDontStem() {
        val cz = CzechAnalyzer()
        assertAnalyzesTo(cz, "e", arrayOf("e"))
        assertAnalyzesTo(cz, "zi", arrayOf("zi"))
        cz.close()
    }

    @Test
    @Throws(Exception::class)
    fun testWithKeywordAttribute() {
        val set = CharArraySet(1, true)
        set.add("hole")
        val tokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenStream.setReader(StringReader("hole desek"))

        val filter = CzechStemFilter(SetKeywordMarkerFilter(tokenStream, set))
        assertTokenStreamContents(filter, arrayOf("hole", "desk"))
    }

    @Test
    @Throws(Exception::class)
    fun testEmptyTerm() {
        val a =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer: Tokenizer = KeywordTokenizer()
                    return TokenStreamComponents(tokenizer, CzechStemFilter(tokenizer))
                }
            }
        checkOneTerm(a, "", "")
        a.close()
    }
}
