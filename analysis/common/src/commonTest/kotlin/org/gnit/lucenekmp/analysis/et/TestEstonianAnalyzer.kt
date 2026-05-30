package org.gnit.lucenekmp.analysis.et

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

class TestEstonianAnalyzer : BaseTokenStreamTestCase() {
    /** This test fails with NPE when the stopwords file is missing in classpath */
    @Test
    fun testResourcesAvailable() {
        EstonianAnalyzer().close()
    }

    /** test stopwords and stemming */
    @Test
    @Throws(IOException::class)
    fun testBasics() {
        val a: Analyzer = EstonianAnalyzer()
        // stemming
        checkOneTerm(a, "teadaolevalt", "teadaole")
        checkOneTerm(a, "teadaolevaid", "teadaole")
        checkOneTerm(a, "teadaolevatest", "teadaole")
        checkOneTerm(a, "teadaolevail", "teadaole")
        checkOneTerm(a, "teadaolevatele", "teadaole")
        checkOneTerm(a, "teadaolevatel", "teadaole")
        checkOneTerm(a, "teadaolevateks", "teadaole")
        checkOneTerm(a, "teadaolevate", "teadaole")
        checkOneTerm(a, "teadaolevaks", "teadaole")
        checkOneTerm(a, "teadaoleval", "teadaole")
        checkOneTerm(a, "teadaolevates", "teadaole")
        checkOneTerm(a, "teadaolevat", "teadaole")
        checkOneTerm(a, "teadaolevast", "teadaole")
        checkOneTerm(a, "teadaoleva", "teadaole")
        checkOneTerm(a, "teadaolevais", "teadaole")
        checkOneTerm(a, "teadaolevas", "teadaole")
        checkOneTerm(a, "teadaolevad", "teadaole")
        checkOneTerm(a, "teadaolevale", "teadaole")
        checkOneTerm(a, "teadaolevatesse", "teadaole")
        // stopword
        assertAnalyzesTo(a, "alla", arrayOf())
        a.close()
    }
}
