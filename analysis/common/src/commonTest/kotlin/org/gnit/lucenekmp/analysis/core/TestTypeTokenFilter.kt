package org.gnit.lucenekmp.analysis.core

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.standard.StandardTokenizer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TypeAttribute
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.util.English
import kotlin.test.Test
import kotlin.test.assertEquals

class TestTypeTokenFilter : BaseTokenStreamTestCase() {
    @Test
    @Throws(IOException::class)
    fun testTypeFilter() {
        val reader = StringReader("121 is palindrome, while 123 is not")
        val stopTypes = setOf("<NUM>")
        val input = StandardTokenizer(newAttributeFactory())
        input.setReader(reader)
        val stream: TokenStream = TypeTokenFilter(input, stopTypes)
        assertTokenStreamContents(stream, arrayOf("is", "palindrome", "while", "is", "not"))
    }

    /** Test Position increments applied by TypeTokenFilter with and without enabling this option. */
    @Test
    @Throws(IOException::class)
    fun testStopPositons() {
        val sb = StringBuilder()
        for (i in 10..<20) {
            if (i % 3 != 0) {
                sb.append(i).append(" ")
            } else {
                val w = English.intToEnglish(i).trim()
                sb.append(w).append(" ")
            }
        }
        log(sb.toString())
        val stopSet = setOf("<NUM>")

        // with increments
        val reader = StringReader(sb.toString())
        val input = StandardTokenizer()
        input.setReader(reader)
        val typeTokenFilter = TypeTokenFilter(input, stopSet)
        testPositons(typeTokenFilter)
    }

    @Throws(IOException::class)
    private fun testPositons(stpf: TypeTokenFilter) {
        val typeAtt = stpf.getAttribute(TypeAttribute::class)!!
        val termAttribute = stpf.getAttribute(CharTermAttribute::class)!!
        val posIncrAtt = stpf.getAttribute(PositionIncrementAttribute::class)!!
        stpf.reset()
        while (stpf.incrementToken()) {
            log(
                "Token: ${termAttribute}: ${typeAtt.type()} - ${posIncrAtt.getPositionIncrement()}"
            )
            assertEquals(
                3,
                posIncrAtt.getPositionIncrement(),
                "if position increment is enabled the positionIncrementAttribute value should be 3, otherwise 1"
            )
        }
        stpf.end()
        stpf.close()
    }

    @Test
    @Throws(IOException::class)
    fun testTypeFilterWhitelist() {
        val reader = StringReader("121 is palindrome, while 123 is not")
        val stopTypes = setOf("<NUM>")
        val input = StandardTokenizer(newAttributeFactory())
        input.setReader(reader)
        val stream: TokenStream = TypeTokenFilter(input, stopTypes, true)
        assertTokenStreamContents(stream, arrayOf("121", "123"))
    }

    // print debug info depending on VERBOSE
    private fun log(s: String) {
        if (VERBOSE) {
            println(s)
        }
    }
}
