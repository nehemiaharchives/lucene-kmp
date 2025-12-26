package org.gnit.lucenekmp.analysis.fr

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

/** Test case for FrenchAnalyzer. */
class TestFrenchAnalyzer : BaseTokenStreamTestCase() {

    @Test
    @Throws(Exception::class)
    fun testAnalyzer() {
        val fa = FrenchAnalyzer()

        assertAnalyzesTo(fa, "", arrayOf())

        assertAnalyzesTo(fa, "chien chat cheval", arrayOf("chien", "chat", "cheval"))
        assertAnalyzesTo(fa, "chien CHAT CHEVAL", arrayOf("chien", "chat", "cheval"))
        assertAnalyzesTo(
            fa,
            "  chien  ,? + = -  CHAT /: > CHEVAL",
            arrayOf("chien", "chat", "cheval")
        )
        assertAnalyzesTo(fa, "chien++", arrayOf("chien"))
        assertAnalyzesTo(fa, "mot \"entreguillemet\"", arrayOf("mot", "entreguilemet"))

        assertAnalyzesTo(fa, "Jean-François", arrayOf("jean", "francoi"))

        assertAnalyzesTo(
            fa,
            "le la chien les aux chat du des à cheval",
            arrayOf("chien", "chat", "cheval")
        )

        assertAnalyzesTo(
            fa,
            "lances chismes habitable chiste éléments captifs",
            arrayOf("lanc", "chism", "habitabl", "chist", "element", "captif")
        )

        assertAnalyzesTo(
            fa,
            "finissions souffrirent rugissante",
            arrayOf("finision", "soufrirent", "rugisant")
        )

        assertAnalyzesTo(
            fa,
            "C3PO aujourd'hui oeuf ïâöûàä anticonstitutionnellement Java++ ",
            arrayOf("c3po", "aujourd'hui", "oeuf", "ïaöuaä", "anticonstitutionel", "java")
        )

        assertAnalyzesTo(
            fa,
            "33Bis 1940-1945 1940:1945 (---i+++)*",
            arrayOf("33bi", "1940", "1945", "1940", "1945", "i")
        )
        fa.close()
    }

    @Test
    @Throws(Exception::class)
    fun testReusableTokenStream() {
        val fa = FrenchAnalyzer()
        assertAnalyzesTo(
            fa,
            "le la chien les aux chat du des à cheval",
            arrayOf("chien", "chat", "cheval")
        )
        assertAnalyzesTo(
            fa,
            "lances chismes habitable chiste éléments captifs",
            arrayOf("lanc", "chism", "habitabl", "chist", "element", "captif")
        )
        fa.close()
    }

    @Test
    @Throws(Exception::class)
    fun testExclusionTableViaCtor() {
        val set = CharArraySet(1, true)
        set.add("habitable")
        var fa = FrenchAnalyzer(CharArraySet.EMPTY_SET, set)
        assertAnalyzesTo(fa, "habitable chiste", arrayOf("habitable", "chist"))
        fa.close()

        fa = FrenchAnalyzer(CharArraySet.EMPTY_SET, set)
        assertAnalyzesTo(fa, "habitable chiste", arrayOf("habitable", "chist"))
        fa.close()
    }

    @Test
    @Throws(Exception::class)
    fun testElision() {
        val fa = FrenchAnalyzer()
        assertAnalyzesTo(fa, "voir l'embrouille", arrayOf("voir", "embrouil"))
        fa.close()
    }

    /** Test that stopwords are not case sensitive. */
    @Test
    @Throws(IOException::class)
    fun testStopwordsCasing() {
        val a = FrenchAnalyzer()
        assertAnalyzesTo(a, "Votre", arrayOf())
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val a: Analyzer = FrenchAnalyzer()
        checkRandomData(random(), a, 200 * RANDOM_MULTIPLIER)
        a.close()
    }

    /** test accent-insensitive */
    @Test
    @Throws(Exception::class)
    fun testAccentInsensitive() {
        val a: Analyzer = FrenchAnalyzer()
        checkOneTerm(a, "sécuritaires", "securitair")
        checkOneTerm(a, "securitaires", "securitair")
        a.close()
    }
}
