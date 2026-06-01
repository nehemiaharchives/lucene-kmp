package org.gnit.lucenekmp.analysis.tr

import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

class TestApostropheFilter : BaseTokenStreamTestCase() {

    @Test
    @Throws(Exception::class)
    fun testApostropheFilter() {
        var stream: TokenStream = whitespaceMockTokenizer("Türkiye'de 2003'te Van Gölü'nü gördüm")
        stream = TurkishLowerCaseFilter(stream)
        stream = ApostropheFilter(stream)
        assertTokenStreamContents(stream, arrayOf("türkiye", "2003", "van", "gölü", "gördüm"))
    }
}
