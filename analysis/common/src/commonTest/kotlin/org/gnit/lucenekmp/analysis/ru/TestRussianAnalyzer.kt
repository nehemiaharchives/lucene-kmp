package org.gnit.lucenekmp.analysis.ru

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

/** Test case for RussianAnalyzer. */
class TestRussianAnalyzer : BaseTokenStreamTestCase() {
    /** Check that RussianAnalyzer doesnt discard any numbers */
    @Test
    @Throws(IOException::class)
    fun testDigitsInRussianCharset() {
        val ra = RussianAnalyzer()
        assertAnalyzesTo(ra, "text 1000", arrayOf("text", "1000"))
        ra.close()
    }

    @Test
    @Throws(Exception::class)
    fun testReusableTokenStream() {
        val a: Analyzer = RussianAnalyzer()
        assertAnalyzesTo(
            a,
            "Вместе с тем о силе электромагнитной энергии имели представление еще",
            arrayOf("вмест", "сил", "электромагнитн", "энерг", "имел", "представлен")
        )
        assertAnalyzesTo(
            a,
            "Но знание это хранилось в тайне",
            arrayOf("знан", "эт", "хран", "тайн")
        )
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testWithStemExclusionSet() {
        val set = CharArraySet(1, true)
        set.add("представление")
        val a = RussianAnalyzer(RussianAnalyzer.getDefaultStopSet(), set)
        assertAnalyzesTo(
            a,
            "Вместе с тем о силе электромагнитной энергии имели представление еще",
            arrayOf("вмест", "сил", "электромагнитн", "энерг", "имел", "представление")
        )
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val analyzer: Analyzer = RussianAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }
}
