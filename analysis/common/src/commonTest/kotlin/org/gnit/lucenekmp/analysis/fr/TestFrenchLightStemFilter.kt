package org.gnit.lucenekmp.analysis.fr

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.core.KeywordTokenizer
import org.gnit.lucenekmp.analysis.miscellaneous.SetKeywordMarkerFilter
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test

/** Simple tests for [FrenchLightStemFilter]. */
class TestFrenchLightStemFilter : BaseTokenStreamTestCase() {
    private lateinit var analyzer: Analyzer

    @BeforeTest
    fun setUp() {
        analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val source: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                return TokenStreamComponents(source, FrenchLightStemFilter(source))
            }
        }
    }

    /** Test some examples from the paper. */
    @Test
    @Throws(IOException::class)
    fun testExamples() {
        checkOneTerm(analyzer, "chevaux", "cheval")
        checkOneTerm(analyzer, "cheval", "cheval")

        checkOneTerm(analyzer, "hiboux", "hibou")
        checkOneTerm(analyzer, "hibou", "hibou")

        checkOneTerm(analyzer, "chantés", "chant")
        checkOneTerm(analyzer, "chanter", "chant")
        checkOneTerm(analyzer, "chante", "chant")
        checkOneTerm(analyzer, "chant", "chant")

        checkOneTerm(analyzer, "baronnes", "baron")
        checkOneTerm(analyzer, "barons", "baron")
        checkOneTerm(analyzer, "baron", "baron")

        checkOneTerm(analyzer, "peaux", "peau")
        checkOneTerm(analyzer, "peau", "peau")

        checkOneTerm(analyzer, "anneaux", "aneau")
        checkOneTerm(analyzer, "anneau", "aneau")

        checkOneTerm(analyzer, "neveux", "neveu")
        checkOneTerm(analyzer, "neveu", "neveu")

        checkOneTerm(analyzer, "affreux", "afreu")
        checkOneTerm(analyzer, "affreuse", "afreu")

        checkOneTerm(analyzer, "investissement", "investi")
        checkOneTerm(analyzer, "investir", "investi")

        checkOneTerm(analyzer, "assourdissant", "asourdi")
        checkOneTerm(analyzer, "assourdir", "asourdi")

        checkOneTerm(analyzer, "pratiquement", "pratiqu")
        checkOneTerm(analyzer, "pratique", "pratiqu")

        checkOneTerm(analyzer, "administrativement", "administratif")
        checkOneTerm(analyzer, "administratif", "administratif")

        checkOneTerm(analyzer, "justificatrice", "justifi")
        checkOneTerm(analyzer, "justificateur", "justifi")
        checkOneTerm(analyzer, "justifier", "justifi")

        checkOneTerm(analyzer, "educatrice", "eduqu")
        checkOneTerm(analyzer, "eduquer", "eduqu")

        checkOneTerm(analyzer, "communicateur", "comuniqu")
        checkOneTerm(analyzer, "communiquer", "comuniqu")

        checkOneTerm(analyzer, "accompagnatrice", "acompagn")
        checkOneTerm(analyzer, "accompagnateur", "acompagn")

        checkOneTerm(analyzer, "administrateur", "administr")
        checkOneTerm(analyzer, "administrer", "administr")

        checkOneTerm(analyzer, "productrice", "product")
        checkOneTerm(analyzer, "producteur", "product")

        checkOneTerm(analyzer, "acheteuse", "achet")
        checkOneTerm(analyzer, "acheteur", "achet")

        checkOneTerm(analyzer, "planteur", "plant")
        checkOneTerm(analyzer, "plante", "plant")

        checkOneTerm(analyzer, "poreuse", "poreu")
        checkOneTerm(analyzer, "poreux", "poreu")

        checkOneTerm(analyzer, "plieuse", "plieu")

        checkOneTerm(analyzer, "bijoutière", "bijouti")
        checkOneTerm(analyzer, "bijoutier", "bijouti")

        checkOneTerm(analyzer, "caissière", "caisi")
        checkOneTerm(analyzer, "caissier", "caisi")

        checkOneTerm(analyzer, "abrasive", "abrasif")
        checkOneTerm(analyzer, "abrasif", "abrasif")

        checkOneTerm(analyzer, "folle", "fou")
        checkOneTerm(analyzer, "fou", "fou")

        checkOneTerm(analyzer, "personnelle", "person")
        checkOneTerm(analyzer, "personne", "person")

        checkOneTerm(analyzer, "complète", "complet")
        checkOneTerm(analyzer, "complet", "complet")

        checkOneTerm(analyzer, "aromatique", "aromat")

        checkOneTerm(analyzer, "faiblesse", "faibl")
        checkOneTerm(analyzer, "faible", "faibl")

        checkOneTerm(analyzer, "patinage", "patin")
        checkOneTerm(analyzer, "patin", "patin")

        checkOneTerm(analyzer, "sonorisation", "sono")

        checkOneTerm(analyzer, "ritualisation", "rituel")
        checkOneTerm(analyzer, "rituel", "rituel")

        checkOneTerm(analyzer, "nomination", "nomin")

        checkOneTerm(analyzer, "disposition", "dispos")
        checkOneTerm(analyzer, "dispose", "dispos")

        checkOneTerm(analyzer, "1234555", "1234555")
        checkOneTerm(analyzer, "12333345", "12333345")
        checkOneTerm(analyzer, "1234", "1234")
        checkOneTerm(analyzer, "abcdeff", "abcdef")
        checkOneTerm(analyzer, "abcccddeef", "abcdef")
        checkOneTerm(analyzer, "créées", "cre")
        checkOneTerm(analyzer, "22hh00", "22h00")
    }

    /** Test against a vocabulary from the reference impl. */
    @Ignore
    @Test
    @Throws(IOException::class)
    fun testVocabulary() {
        // Requires VocabularyAssert and test data path infrastructure.
    }

    @Test
    @Throws(IOException::class)
    fun testKeyword() {
        val exclusionSet = CharArraySet(mutableSetOf<Any>("chevaux"), false)
        val a = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val source: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
                val sink: TokenStream = SetKeywordMarkerFilter(source, exclusionSet)
                return TokenStreamComponents(source, FrenchLightStemFilter(sink))
            }
        }
        checkOneTerm(a, "chevaux", "chevaux")
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
    }

    @Test
    @Throws(IOException::class)
    fun testEmptyTerm() {
        val a = object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = KeywordTokenizer()
                return TokenStreamComponents(tokenizer, FrenchLightStemFilter(tokenizer))
            }
        }
        checkOneTerm(a, "", "")
        a.close()
    }
}
