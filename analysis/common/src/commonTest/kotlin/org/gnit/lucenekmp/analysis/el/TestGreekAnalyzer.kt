package org.gnit.lucenekmp.analysis.el

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

/** A unit test class for verifying the correct operation of the GreekAnalyzer. */
class TestGreekAnalyzer : BaseTokenStreamTestCase() {

    /**
     * Test the analysis of various greek strings.
     */
    @Test
    fun testAnalyzer() {
        val a: Analyzer = GreekAnalyzer()
        // Verify the correct analysis of capitals and small accented letters, and
        // stemming
        assertAnalyzesTo(
            a,
            "Μία εξαιρετικά καλή και πλούσια σειρά χαρακτήρων της Ελληνικής γλώσσας",
            arrayOf("μια", "εξαιρετ", "καλ", "πλουσ", "σειρ", "χαρακτηρ", "ελληνικ", "γλωσσ")
        )
        // Verify the correct analysis of small letters with diaeresis and the elimination
        // of punctuation marks
        assertAnalyzesTo(
            a,
            "Προϊόντα (και)     [πολλαπλές] - ΑΝΑΓΚΕΣ",
            arrayOf("προιοντ", "πολλαπλ", "αναγκ")
        )
        // Verify the correct analysis of capital accented letters and capital letters with diaeresis,
        // as well as the elimination of stop words
        assertAnalyzesTo(
            a,
            "ΠΡΟΫΠΟΘΕΣΕΙΣ  Άψογος, ο μεστός και οι άλλοι",
            arrayOf("προυποθεσ", "αψογ", "μεστ", "αλλ")
        )
        a.close()
    }

    @Test
    fun testReusableTokenStream() {
        val a: Analyzer = GreekAnalyzer()
        // Verify the correct analysis of capitals and small accented letters, and
        // stemming
        assertAnalyzesTo(
            a,
            "Μία εξαιρετικά καλή και πλούσια σειρά χαρακτήρων της Ελληνικής γλώσσας",
            arrayOf("μια", "εξαιρετ", "καλ", "πλουσ", "σειρ", "χαρακτηρ", "ελληνικ", "γλωσσ")
        )
        // Verify the correct analysis of small letters with diaeresis and the elimination
        // of punctuation marks
        assertAnalyzesTo(
            a,
            "Προϊόντα (και)     [πολλαπλές] - ΑΝΑΓΚΕΣ",
            arrayOf("προιοντ", "πολλαπλ", "αναγκ")
        )
        // Verify the correct analysis of capital accented letters and capital letters with diaeresis,
        // as well as the elimination of stop words
        assertAnalyzesTo(
            a,
            "ΠΡΟΫΠΟΘΕΣΕΙΣ  Άψογος, ο μεστός και οι άλλοι",
            arrayOf("προυποθεσ", "αψογ", "μεστ", "αλλ")
        )
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    fun testRandomStrings() {
        val a: Analyzer = GreekAnalyzer()
        checkRandomData(random(), a, 200 * RANDOM_MULTIPLIER)
        a.close()
    }
}

