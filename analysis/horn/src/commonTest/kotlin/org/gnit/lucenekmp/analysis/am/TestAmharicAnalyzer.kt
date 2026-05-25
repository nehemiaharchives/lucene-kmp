package org.gnit.lucenekmp.analysis.am

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

/** Tests the AmharicAnalyzer. */
class TestAmharicAnalyzer : BaseTokenStreamTestCase() {
    @Test
    fun testResourcesAvailable() {
        AmharicAnalyzer().close()
    }

    @Test
    @Throws(Exception::class)
    fun testHornExamples() {
        val a: Analyzer = AmharicAnalyzer()
        checkOneTerm(a, "የማያስፈልጋትስ", "አስፈለገ")
        checkOneTerm(a, "አይደለችም", "ነው")
        checkOneTerm(a, "ይመጣሉ", "መጣ")
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testLightStemming() {
        val a: Analyzer = AmharicAnalyzer()
        checkOneTerm(a, "መጽሐፎችን", "መፅሀፍ")
        checkOneTerm(a, "ለዘመዶቻችንም", "ዘመድ")
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testGeneratedDictionaryData() {
        val a: Analyzer = AmharicAnalyzer()
        checkOneTerm(a, "ሀገር", "hager")
        checkOneTerm(a, "na", "mT'")
        checkOneTerm(a, "yehagerocn", "hager")
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testExclusionSet() {
        val exclusionSet = CharArraySet(mutableSetOf<Any>("ይመጣሉ"), true)
        val a: Analyzer = AmharicAnalyzer(AmharicAnalyzer.getDefaultStopSet(), exclusionSet)
        checkOneTerm(a, "ይመጣሉ", "ይመጣሉ")
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val analyzer: Analyzer = AmharicAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }
}
