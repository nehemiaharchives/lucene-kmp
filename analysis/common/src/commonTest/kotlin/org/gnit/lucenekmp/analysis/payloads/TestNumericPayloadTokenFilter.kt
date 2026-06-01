package org.gnit.lucenekmp.analysis.payloads

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PayloadAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TypeAttribute
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import kotlin.test.Test
import kotlin.test.assertTrue

class TestNumericPayloadTokenFilter : BaseTokenStreamTestCase() {
    @Test
    @Throws(IOException::class)
    fun test() {
        val test = "The quick red fox jumped over the lazy brown dogs"

        val input = MockTokenizer(MockTokenizer.WHITESPACE, false)
        input.setReader(StringReader(test))
        val nptf = NumericPayloadTokenFilter(WordTokenFilter(input), 3.0f, "D")
        var seenDogs = false
        val termAtt = nptf.getAttribute(CharTermAttribute::class)!!
        val typeAtt = nptf.getAttribute(TypeAttribute::class)!!
        val payloadAtt = nptf.getAttribute(PayloadAttribute::class)!!
        nptf.reset()
        while (nptf.incrementToken()) {
            if (termAtt.toString() == "dogs") {
                seenDogs = true
                assertTrue(typeAtt.type() == "D", "${typeAtt.type()} is not equal to D")
                assertTrue(payloadAtt.payload != null, "payloadAtt.payload is null and it shouldn't be")
                val bytes = payloadAtt.payload!!.bytes
                assertTrue(
                    bytes.size == payloadAtt.payload!!.length,
                    "${bytes.size} does not equal: ${payloadAtt.payload!!.length}"
                )
                assertTrue(
                    payloadAtt.payload!!.offset == 0,
                    "${payloadAtt.payload!!.offset} does not equal: 0"
                )
                val pay = PayloadHelper.decodeFloat(bytes)
                assertTrue(pay == 3.0f, "$pay does not equal: 3")
            } else {
                assertTrue(typeAtt.type() == "word", "${typeAtt.type()} is not null and it should be")
            }
        }
        assertTrue(seenDogs, "$seenDogs does not equal: true")
    }

    private class WordTokenFilter(input: TokenStream) : TokenFilter(input) {
        private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
        private val typeAtt: TypeAttribute = addAttribute(TypeAttribute::class)

        @Throws(IOException::class)
        override fun incrementToken(): Boolean {
            if (input.incrementToken()) {
                if (termAtt.toString() == "dogs") {
                    typeAtt.setType("D")
                }
                return true
            }
            return false
        }
    }
}
