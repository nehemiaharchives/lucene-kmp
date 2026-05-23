package org.gnit.lucenekmp.analysis.ar

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

/** Test the Arabic Analyzer */
class TestArabicAnalyzer : BaseTokenStreamTestCase() {
    /** This test fails with NPE when the stopwords file is missing in classpath */
    @Test
    fun testResourcesAvailable() {
        ArabicAnalyzer().close()
    }

    /**
     * Some simple tests showing some features of the analyzer, how some regular forms will conflate
     */
    @Test
    @Throws(Exception::class)
    fun testBasicFeatures() {
        val a: Analyzer = ArabicAnalyzer()
        assertAnalyzesTo(a, "كبير", arrayOf("كبير"))
        assertAnalyzesTo(a, "كبيرة", arrayOf("كبير")) // feminine marker

        assertAnalyzesTo(a, "مشروب", arrayOf("مشروب"))
        assertAnalyzesTo(a, "مشروبات", arrayOf("مشروب")) // plural -at

        assertAnalyzesTo(a, "أمريكيين", arrayOf("امريك")) // plural -in
        assertAnalyzesTo(a, "امريكي", arrayOf("امريك")) // singular with bare alif

        assertAnalyzesTo(a, "كتاب", arrayOf("كتاب"))
        assertAnalyzesTo(a, "الكتاب", arrayOf("كتاب")) // definite article

        assertAnalyzesTo(a, "ما ملكت أيمانكم", arrayOf("ملكت", "ايمانكم"))
        assertAnalyzesTo(a, "الذين ملكت أيمانكم", arrayOf("ملكت", "ايمانكم")) // stopwords
        a.close()
    }

    /** Simple tests to show things are getting reset correctly, etc. */
    @Test
    @Throws(Exception::class)
    fun testReusableTokenStream() {
        val a: Analyzer = ArabicAnalyzer()
        assertAnalyzesTo(a, "كبير", arrayOf("كبير"))
        assertAnalyzesTo(a, "كبيرة", arrayOf("كبير")) // feminine marker
        a.close()
    }

    /** Non-arabic text gets treated in a similar way as SimpleAnalyzer. */
    @Test
    @Throws(Exception::class)
    fun testEnglishInput() {
        val a: Analyzer = ArabicAnalyzer()
        assertAnalyzesTo(a, "English text.", arrayOf("english", "text"))
        a.close()
    }

    /** Test that custom stopwords work, and are not case-sensitive. */
    @Test
    @Throws(Exception::class)
    fun testCustomStopwords() {
        val set = CharArraySet(mutableSetOf<Any>("the", "and", "a"), false)
        val a: Analyzer = ArabicAnalyzer(set)
        assertAnalyzesTo(a, "The quick brown fox.", arrayOf("quick", "brown", "fox"))
        a.close()
    }

    @Test
    @Throws(IOException::class)
    fun testWithStemExclusionSet() {
        val set = CharArraySet(mutableSetOf<Any>("ساهدهات"), false)
        var a: Analyzer = ArabicAnalyzer(CharArraySet.EMPTY_SET, set)
        assertAnalyzesTo(a, "كبيرة the quick ساهدهات", arrayOf("كبير", "the", "quick", "ساهدهات"))
        assertAnalyzesTo(a, "كبيرة the quick ساهدهات", arrayOf("كبير", "the", "quick", "ساهدهات"))
        a.close()

        a = ArabicAnalyzer(CharArraySet.EMPTY_SET, CharArraySet.EMPTY_SET)
        assertAnalyzesTo(a, "كبيرة the quick ساهدهات", arrayOf("كبير", "the", "quick", "ساهد"))
        assertAnalyzesTo(a, "كبيرة the quick ساهدهات", arrayOf("كبير", "the", "quick", "ساهد"))
        a.close()
    }

    /** test we fold digits to latin-1 */
    @Test
    @Throws(Exception::class)
    fun testDigits() {
        val a: Analyzer = ArabicAnalyzer()
        checkOneTerm(a, "١٢٣٤", "1234")
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val a: Analyzer = ArabicAnalyzer()
        checkRandomData(random(), a, 200 * RANDOM_MULTIPLIER)
        a.close()
    }
}

