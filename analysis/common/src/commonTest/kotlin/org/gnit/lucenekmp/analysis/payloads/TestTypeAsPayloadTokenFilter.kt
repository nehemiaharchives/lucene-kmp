package org.gnit.lucenekmp.analysis.payloads

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PayloadAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TypeAttribute
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test
import kotlin.test.assertTrue

class TestTypeAsPayloadTokenFilter : BaseTokenStreamTestCase() {
    @Test
    @Throws(IOException::class)
    fun test() {
        val test = "The quick red fox jumped over the lazy brown dogs"

        val nptf = TypeAsPayloadTokenFilter(WordTokenFilter(whitespaceMockTokenizer(test)))
        var count = 0
        val termAtt = nptf.getAttribute(CharTermAttribute::class)!!
        val typeAtt = nptf.getAttribute(TypeAttribute::class)!!
        val payloadAtt = nptf.getAttribute(PayloadAttribute::class)!!
        nptf.reset()
        while (nptf.incrementToken()) {
            val expectedType = termAtt.buffer()[0].uppercaseChar().toString()
            assertTrue(typeAtt.type() == expectedType, "${typeAtt.type()} is not equal to $expectedType")
            assertTrue(payloadAtt.payload != null, "nextToken.payload is null and it shouldn't be")
            val type = payloadAtt.payload!!.utf8ToString()
            assertTrue(type == typeAtt.type(), "$type is not equal to ${typeAtt.type()}")
            count++
        }

        assertTrue(count == 10, "$count does not equal: 10")
    }

    private class WordTokenFilter(input: TokenStream) : TokenFilter(input) {
        private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
        private val typeAtt: TypeAttribute = addAttribute(TypeAttribute::class)

        @Throws(IOException::class)
        override fun incrementToken(): Boolean {
            if (input.incrementToken()) {
                typeAtt.setType(termAtt.buffer()[0].uppercaseChar().toString())
                return true
            }
            return false
        }
    }
}
