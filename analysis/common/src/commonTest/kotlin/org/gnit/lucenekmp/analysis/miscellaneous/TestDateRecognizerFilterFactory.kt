package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

class TestDateRecognizerFilterFactory : BaseTokenStreamTestCase() {
    @Test
    fun testBadLanguageTagThrowsException() {
        expectThrows(Exception::class) {
            val args = mutableMapOf<String, String>()
            args[DateRecognizerFilterFactory.LOCALE] = "en_US"
            DateRecognizerFilterFactory(args)
        }
    }

    @Test
    fun testGoodLocaleParsesWell() {
        val args = mutableMapOf<String, String>()
        args[DateRecognizerFilterFactory.LOCALE] = "en-US"
        DateRecognizerFilterFactory(args)
    }
}
