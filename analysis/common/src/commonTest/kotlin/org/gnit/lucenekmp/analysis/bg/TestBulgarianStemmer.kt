package org.gnit.lucenekmp.analysis.bg

import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.analysis.miscellaneous.SetKeywordMarkerFilter
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.jdkport.StringReader
import kotlin.test.Test

/** Test the Bulgarian Stemmer */
class TestBulgarianStemmer : BaseTokenStreamTestCase() {
    /**
     * Test showing how masculine noun forms conflate. An example noun for each common (and some rare)
     * plural pattern is listed.
     */
    @Test
    @Throws(okio.IOException::class)
    fun testMasculineNouns() {
        val a = BulgarianAnalyzer()

        // -и pattern
        assertAnalyzesTo(a, "град", arrayOf("град"))
        assertAnalyzesTo(a, "града", arrayOf("град"))
        assertAnalyzesTo(a, "градът", arrayOf("град"))
        assertAnalyzesTo(a, "градове", arrayOf("град"))
        assertAnalyzesTo(a, "градовете", arrayOf("град"))

        // -ове pattern
        assertAnalyzesTo(a, "народ", arrayOf("народ"))
        assertAnalyzesTo(a, "народа", arrayOf("народ"))
        assertAnalyzesTo(a, "народът", arrayOf("народ"))
        assertAnalyzesTo(a, "народи", arrayOf("народ"))
        assertAnalyzesTo(a, "народите", arrayOf("народ"))
        assertAnalyzesTo(a, "народе", arrayOf("народ"))

        // -ища pattern
        assertAnalyzesTo(a, "път", arrayOf("път"))
        assertAnalyzesTo(a, "пътя", arrayOf("път"))
        assertAnalyzesTo(a, "пътят", arrayOf("път"))
        assertAnalyzesTo(a, "пътища", arrayOf("път"))
        assertAnalyzesTo(a, "пътищата", arrayOf("път"))

        // -чета pattern
        assertAnalyzesTo(a, "градец", arrayOf("градец"))
        assertAnalyzesTo(a, "градеца", arrayOf("градец"))
        assertAnalyzesTo(a, "градецът", arrayOf("градец"))
        /* note the below forms conflate with each other, but not the rest */
        assertAnalyzesTo(a, "градовце", arrayOf("градовц"))
        assertAnalyzesTo(a, "градовцете", arrayOf("градовц"))

        // -овци pattern
        assertAnalyzesTo(a, "дядо", arrayOf("дяд"))
        assertAnalyzesTo(a, "дядото", arrayOf("дяд"))
        assertAnalyzesTo(a, "дядовци", arrayOf("дяд"))
        assertAnalyzesTo(a, "дядовците", arrayOf("дяд"))

        // -е pattern
        assertAnalyzesTo(a, "мъж", arrayOf("мъж"))
        assertAnalyzesTo(a, "мъжа", arrayOf("мъж"))
        assertAnalyzesTo(a, "мъже", arrayOf("мъж"))
        assertAnalyzesTo(a, "мъжете", arrayOf("мъж"))
        assertAnalyzesTo(a, "мъжо", arrayOf("мъж"))
        /* word is too short, will not remove -ът */
        assertAnalyzesTo(a, "мъжът", arrayOf("мъжът"))

        // -а pattern
        assertAnalyzesTo(a, "крак", arrayOf("крак"))
        assertAnalyzesTo(a, "крака", arrayOf("крак"))
        assertAnalyzesTo(a, "кракът", arrayOf("крак"))
        assertAnalyzesTo(a, "краката", arrayOf("крак"))

        // брат
        assertAnalyzesTo(a, "брат", arrayOf("брат"))
        assertAnalyzesTo(a, "брата", arrayOf("брат"))
        assertAnalyzesTo(a, "братът", arrayOf("брат"))
        assertAnalyzesTo(a, "братя", arrayOf("брат"))
        assertAnalyzesTo(a, "братята", arrayOf("брат"))
        assertAnalyzesTo(a, "брате", arrayOf("брат"))

        a.close()
    }

    /** Test showing how feminine noun forms conflate */
    @Test
    @Throws(okio.IOException::class)
    fun testFeminineNouns() {
        val a = BulgarianAnalyzer()

        assertAnalyzesTo(a, "вест", arrayOf("вест"))
        assertAnalyzesTo(a, "вестта", arrayOf("вест"))
        assertAnalyzesTo(a, "вести", arrayOf("вест"))
        assertAnalyzesTo(a, "вестите", arrayOf("вест"))

        a.close()
    }

    /**
     * Test showing how neuter noun forms conflate an example noun for each common plural pattern is
     * listed
     */
    @Test
    @Throws(okio.IOException::class)
    fun testNeuterNouns() {
        val a = BulgarianAnalyzer()

        // -а pattern
        assertAnalyzesTo(a, "дърво", arrayOf("дърв"))
        assertAnalyzesTo(a, "дървото", arrayOf("дърв"))
        assertAnalyzesTo(a, "дърва", arrayOf("дърв"))
        assertAnalyzesTo(a, "дървета", arrayOf("дърв"))
        assertAnalyzesTo(a, "дървата", arrayOf("дърв"))
        assertAnalyzesTo(a, "дърветата", arrayOf("дърв"))

        // -та pattern
        assertAnalyzesTo(a, "море", arrayOf("мор"))
        assertAnalyzesTo(a, "морето", arrayOf("мор"))
        assertAnalyzesTo(a, "морета", arrayOf("мор"))
        assertAnalyzesTo(a, "моретата", arrayOf("мор"))

        // -я pattern
        assertAnalyzesTo(a, "изключение", arrayOf("изключени"))
        assertAnalyzesTo(a, "изключението", arrayOf("изключени"))
        assertAnalyzesTo(a, "изключенията", arrayOf("изключени"))
        /* note the below form in this example does not conflate with the rest */
        assertAnalyzesTo(a, "изключения", arrayOf("изключн"))

        a.close()
    }

    /** Test showing how adjectival forms conflate */
    @Test
    @Throws(okio.IOException::class)
    fun testAdjectives() {
        val a = BulgarianAnalyzer()
        assertAnalyzesTo(a, "красив", arrayOf("красив"))
        assertAnalyzesTo(a, "красивия", arrayOf("красив"))
        assertAnalyzesTo(a, "красивият", arrayOf("красив"))
        assertAnalyzesTo(a, "красива", arrayOf("красив"))
        assertAnalyzesTo(a, "красивата", arrayOf("красив"))
        assertAnalyzesTo(a, "красиво", arrayOf("красив"))
        assertAnalyzesTo(a, "красивото", arrayOf("красив"))
        assertAnalyzesTo(a, "красиви", arrayOf("красив"))
        assertAnalyzesTo(a, "красивите", arrayOf("красив"))
        a.close()
    }

    /** Test some exceptional rules, implemented as rewrites. */
    @Test
    @Throws(okio.IOException::class)
    fun testExceptions() {
        val a = BulgarianAnalyzer()

        // ци -> к
        assertAnalyzesTo(a, "собственик", arrayOf("собственик"))
        assertAnalyzesTo(a, "собственика", arrayOf("собственик"))
        assertAnalyzesTo(a, "собственикът", arrayOf("собственик"))
        assertAnalyzesTo(a, "собственици", arrayOf("собственик"))
        assertAnalyzesTo(a, "собствениците", arrayOf("собственик"))

        // зи -> г
        assertAnalyzesTo(a, "подлог", arrayOf("подлог"))
        assertAnalyzesTo(a, "подлога", arrayOf("подлог"))
        assertAnalyzesTo(a, "подлогът", arrayOf("подлог"))
        assertAnalyzesTo(a, "подлози", arrayOf("подлог"))
        assertAnalyzesTo(a, "подлозите", arrayOf("подлог"))

        // си -> х
        assertAnalyzesTo(a, "кожух", arrayOf("кожух"))
        assertAnalyzesTo(a, "кожуха", arrayOf("кожух"))
        assertAnalyzesTo(a, "кожухът", arrayOf("кожух"))
        assertAnalyzesTo(a, "кожуси", arrayOf("кожух"))
        assertAnalyzesTo(a, "кожусите", arrayOf("кожух"))

        // ъ deletion
        assertAnalyzesTo(a, "център", arrayOf("центр"))
        assertAnalyzesTo(a, "центъра", arrayOf("центр"))
        assertAnalyzesTo(a, "центърът", arrayOf("центр"))
        assertAnalyzesTo(a, "центрове", arrayOf("центр"))
        assertAnalyzesTo(a, "центровете", arrayOf("центр"))

        // е*и -> я*
        assertAnalyzesTo(a, "промяна", arrayOf("промян"))
        assertAnalyzesTo(a, "промяната", arrayOf("промян"))
        assertAnalyzesTo(a, "промени", arrayOf("промян"))
        assertAnalyzesTo(a, "промените", arrayOf("промян"))

        // ен -> н
        assertAnalyzesTo(a, "песен", arrayOf("песн"))
        assertAnalyzesTo(a, "песента", arrayOf("песн"))
        assertAnalyzesTo(a, "песни", arrayOf("песн"))
        assertAnalyzesTo(a, "песните", arrayOf("песн"))

        // -еве -> й
        // note: this is the only word i think this rule works for.
        // most -еве pluralized nouns are monosyllabic,
        // and the stemmer requires length > 6...
        assertAnalyzesTo(a, "строй", arrayOf("строй"))
        assertAnalyzesTo(a, "строеве", arrayOf("строй"))
        assertAnalyzesTo(a, "строевете", arrayOf("строй"))
        /* note the below forms conflate with each other, but not the rest */
        assertAnalyzesTo(a, "строя", arrayOf("стр"))
        assertAnalyzesTo(a, "строят", arrayOf("стр"))

        a.close()
    }

    @Test
    @Throws(okio.IOException::class)
    fun testWithKeywordAttribute() {
        val set = CharArraySet(1, true)
        set.add("строеве")
        val tokenStream = MockTokenizer(MockTokenizer.WHITESPACE, false)
        tokenStream.setReader(StringReader("строевете строеве"))

        val filter = BulgarianStemFilter(SetKeywordMarkerFilter(tokenStream, set))
        assertTokenStreamContents(filter, arrayOf("строй", "строеве"))
    }

    @Test
    @Throws(okio.IOException::class)
    fun testEmptyTerm() {
        val a =
            object : org.gnit.lucenekmp.analysis.Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    val tokenizer = KeywordTokenizer()
                    return TokenStreamComponents(tokenizer, BulgarianStemFilter(tokenizer))
                }
            }
        checkOneTerm(a, "", "")
        a.close()
    }
}
