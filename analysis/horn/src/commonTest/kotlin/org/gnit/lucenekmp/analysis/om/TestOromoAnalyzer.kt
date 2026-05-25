package org.gnit.lucenekmp.analysis.om

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

/** Tests the OromoAnalyzer. */
class TestOromoAnalyzer : BaseTokenStreamTestCase() {
    @Test
    fun testResourcesAvailable() {
        OromoAnalyzer().close()
    }

    @Test
    @Throws(Exception::class)
    fun testHornExamples() {
        val a: Analyzer = OromoAnalyzer()
        checkOneTerm(a, "afeeramaniiru", "afeeramuu")
        checkOneTerm(a, "dubbanne", "dubbachuu")
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testLightStemming() {
        val a: Analyzer = OromoAnalyzer()
        checkOneTerm(a, "Namoota", "nama")
        checkOneTerm(a, "manaan", "mana")
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testGeneratedDictionaryData() {
        val a: Analyzer = OromoAnalyzer()
        checkOneTerm(a, "fedhi", "fedh")
        checkOneTerm(a, "Ameerikaanummaa", "ameerikaanummaa")
        checkOneTerm(a, "Ameerikaatti", "ameerikaa")
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testExclusionSet() {
        val exclusionSet = CharArraySet(mutableSetOf<Any>("namoota"), true)
        val a: Analyzer = OromoAnalyzer(OromoAnalyzer.getDefaultStopSet(), exclusionSet)
        checkOneTerm(a, "Namoota", "namoota")
        a.close()
    }

    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val analyzer: Analyzer = OromoAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }
}
