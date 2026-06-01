package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

class TestDateRecognizerFilter : BaseTokenStreamTestCase() {
    @Test
    fun test() {
        val test =
            "The red fox jumped over the lazy dogs on 7/11/2006 The dogs finally reacted on 7/12/2006"
        val dateRecognizer: DateRecognizer = PatternDateRecognizer("MM/dd/yyyy")
        val ts: TokenStream = DateRecognizerFilter(whitespaceMockTokenizer(test), dateRecognizer)
        assertStreamHasNumberOfTokens(ts, 2)
    }
}
