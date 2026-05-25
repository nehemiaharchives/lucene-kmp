package org.gnit.lucenekmp.analysis.or

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

/** Tests the OdiaAnalyzer. */
class TestOdiaAnalyzer : BaseTokenStreamTestCase() {
    /** This test fails with NPE when the stopwords file is missing in classpath. */
    @Test
    fun testResourcesAvailable() {
        OdiaAnalyzer().close()
    }

    @Test
    @Throws(Exception::class)
    fun testBasics() {
        val a: Analyzer = OdiaAnalyzer()
        checkOneTerm(a, "ଘରକୁ", "ଘର")
        checkOneTerm(a, "ପିଲାମାନଙ୍କର", "ପିଲା")
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testExclusionSet() {
        val exclusionSet = CharArraySet(mutableSetOf<Any>("ଘରକୁ"), false)
        val a: Analyzer = OdiaAnalyzer(OdiaAnalyzer.getDefaultStopSet(), exclusionSet)
        checkOneTerm(a, "ଘରକୁ", "ଘରକୁ")
        a.close()
    }

    /** test we fold digits to latin-1 */
    @Test
    @Throws(Exception::class)
    fun testDigits() {
        val a = OdiaAnalyzer()
        checkOneTerm(a, "୧୨୩୪", "1234")
        a.close()
    }

    /** test stopword removal */
    @Test
    @Throws(Exception::class)
    fun testStopwords() {
        val a = OdiaAnalyzer()
        assertAnalyzesTo(a, "ଏହି ଓ ଘରକୁ", arrayOf("ଘର"))
        a.close()
    }

    /** test stopwords from openodia.common.constants.STOPWORDS */
    @Test
    @Throws(Exception::class)
    fun testOpenOdiaStopwords() {
        val a = OdiaAnalyzer()
        assertAnalyzesTo(a, "ରାମ ଓ ସୀତା ଆମକୁ ଆଶୀର୍ବାଦ ଦେଇଛନ୍ତି", arrayOf("ରାମ", "ସୀତା", "ଆମ", "ଆଶୀର୍ବାଦ"))
        assertAnalyzesTo(a, "ଏହା ପାଇଁ ଦେଇଛନ୍ତି", emptyArray())
        a.close()
    }

    /** test tokenization and stemming using openodia tokenizer examples */
    @Test
    @Throws(Exception::class)
    fun testOpenOdiaTokenizerExample() {
        val a = OdiaAnalyzer()
        assertAnalyzesTo(
            a,
            "କ୍ୱାଣ୍ଟମ କମ୍ପ୍ୟୁଟିଙ୍ଗ, ହେଉଛି ଏକ ଉଦୀୟମାନ ହାର୍ଡ଼ୱେର ଏବଂ ସଫ୍ଟୱେରର ପ୍ରଯୁକ୍ତିବିଦ୍ୟା, ଯାହା କଠିନ ଗାଣିତିକ ସମସ୍ୟାଗୁଡ଼ିକର ସମାଧାନ ପାଇଁ ଉପ-ପାରମାଣବିକ ଘଟଣାଗୁଡ଼ିକର ଉପଯୋଗ କରିଥାଏ ।[୧]",
            arrayOf(
                "କ୍ୱାଣ୍ଟମ",
                "କମ୍ପ୍ୟୁଟିଙ୍ଗ",
                "ହେଉଛି",
                "ଏକ",
                "ଉଦୀୟମାନ",
                "ହାର୍ଡ଼ୱେ",
                "ସଫ୍ଟୱେର",
                "ପ୍ରଯୁକ୍ତିବିଦ୍ୟା",
                "ଯାହା",
                "କଠିନ",
                "ଗାଣିତିକ",
                "ସମସ୍ୟା",
                "ସମାଧାନ",
                "ଉପ",
                "ପାରମାଣବିକ",
                "ଘଟଣା",
                "ଉପଯୋଗ",
                "କରିଥାଏ",
                "1"
            )
        )
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val analyzer: Analyzer = OdiaAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }
}
