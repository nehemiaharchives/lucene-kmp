package org.gnit.lucenekmp.analysis.payloads

import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PayloadAttribute
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestDelimitedPayloadTokenFilter : BaseTokenStreamTestCase() {
    @Test
    @Throws(Exception::class)
    fun testPayloads() {
        val test = "The quick|JJ red|JJ fox|NN jumped|VB over the lazy|JJ brown|JJ dogs|NN"
        val filter = DelimitedPayloadTokenFilter(
            whitespaceMockTokenizer(test),
            DelimitedPayloadTokenFilter.DEFAULT_DELIMITER,
            IdentityEncoder()
        )
        val termAtt = filter.getAttribute(CharTermAttribute::class)!!
        val payAtt = filter.getAttribute(PayloadAttribute::class)!!
        filter.reset()
        assertTermEquals("The", filter, termAtt, payAtt, null)
        assertTermEquals("quick", filter, termAtt, payAtt, "JJ".encodeToByteArray())
        assertTermEquals("red", filter, termAtt, payAtt, "JJ".encodeToByteArray())
        assertTermEquals("fox", filter, termAtt, payAtt, "NN".encodeToByteArray())
        assertTermEquals("jumped", filter, termAtt, payAtt, "VB".encodeToByteArray())
        assertTermEquals("over", filter, termAtt, payAtt, null)
        assertTermEquals("the", filter, termAtt, payAtt, null)
        assertTermEquals("lazy", filter, termAtt, payAtt, "JJ".encodeToByteArray())
        assertTermEquals("brown", filter, termAtt, payAtt, "JJ".encodeToByteArray())
        assertTermEquals("dogs", filter, termAtt, payAtt, "NN".encodeToByteArray())
        assertFalse(filter.incrementToken())
        filter.end()
        filter.close()
    }

    @Test
    @Throws(Exception::class)
    fun testNext() {
        val test = "The quick|JJ red|JJ fox|NN jumped|VB over the lazy|JJ brown|JJ dogs|NN"
        val filter = DelimitedPayloadTokenFilter(
            whitespaceMockTokenizer(test),
            DelimitedPayloadTokenFilter.DEFAULT_DELIMITER,
            IdentityEncoder()
        )
        filter.reset()
        assertTermEquals("The", filter, null)
        assertTermEquals("quick", filter, "JJ".encodeToByteArray())
        assertTermEquals("red", filter, "JJ".encodeToByteArray())
        assertTermEquals("fox", filter, "NN".encodeToByteArray())
        assertTermEquals("jumped", filter, "VB".encodeToByteArray())
        assertTermEquals("over", filter, null)
        assertTermEquals("the", filter, null)
        assertTermEquals("lazy", filter, "JJ".encodeToByteArray())
        assertTermEquals("brown", filter, "JJ".encodeToByteArray())
        assertTermEquals("dogs", filter, "NN".encodeToByteArray())
        assertFalse(filter.incrementToken())
        filter.end()
        filter.close()
    }

    @Test
    @Throws(Exception::class)
    fun testFloatEncoding() {
        val test = "The quick|1.0 red|2.0 fox|3.5 jumped|0.5 over the lazy|5 brown|99.3 dogs|83.7"
        val filter = DelimitedPayloadTokenFilter(whitespaceMockTokenizer(test), '|', FloatEncoder())
        val termAtt = filter.getAttribute(CharTermAttribute::class)!!
        val payAtt = filter.getAttribute(PayloadAttribute::class)!!
        filter.reset()
        assertTermEquals("The", filter, termAtt, payAtt, null)
        assertTermEquals("quick", filter, termAtt, payAtt, PayloadHelper.encodeFloat(1.0f))
        assertTermEquals("red", filter, termAtt, payAtt, PayloadHelper.encodeFloat(2.0f))
        assertTermEquals("fox", filter, termAtt, payAtt, PayloadHelper.encodeFloat(3.5f))
        assertTermEquals("jumped", filter, termAtt, payAtt, PayloadHelper.encodeFloat(0.5f))
        assertTermEquals("over", filter, termAtt, payAtt, null)
        assertTermEquals("the", filter, termAtt, payAtt, null)
        assertTermEquals("lazy", filter, termAtt, payAtt, PayloadHelper.encodeFloat(5.0f))
        assertTermEquals("brown", filter, termAtt, payAtt, PayloadHelper.encodeFloat(99.3f))
        assertTermEquals("dogs", filter, termAtt, payAtt, PayloadHelper.encodeFloat(83.7f))
        assertFalse(filter.incrementToken())
        filter.end()
        filter.close()
    }

    @Test
    @Throws(Exception::class)
    fun testIntEncoding() {
        val test = "The quick|1 red|2 fox|3 jumped over the lazy|5 brown|99 dogs|83"
        val filter = DelimitedPayloadTokenFilter(whitespaceMockTokenizer(test), '|', IntegerEncoder())
        val termAtt = filter.getAttribute(CharTermAttribute::class)!!
        val payAtt = filter.getAttribute(PayloadAttribute::class)!!
        filter.reset()
        assertTermEquals("The", filter, termAtt, payAtt, null)
        assertTermEquals("quick", filter, termAtt, payAtt, PayloadHelper.encodeInt(1))
        assertTermEquals("red", filter, termAtt, payAtt, PayloadHelper.encodeInt(2))
        assertTermEquals("fox", filter, termAtt, payAtt, PayloadHelper.encodeInt(3))
        assertTermEquals("jumped", filter, termAtt, payAtt, null)
        assertTermEquals("over", filter, termAtt, payAtt, null)
        assertTermEquals("the", filter, termAtt, payAtt, null)
        assertTermEquals("lazy", filter, termAtt, payAtt, PayloadHelper.encodeInt(5))
        assertTermEquals("brown", filter, termAtt, payAtt, PayloadHelper.encodeInt(99))
        assertTermEquals("dogs", filter, termAtt, payAtt, PayloadHelper.encodeInt(83))
        assertFalse(filter.incrementToken())
        filter.end()
        filter.close()
    }

    @Throws(Exception::class)
    private fun assertTermEquals(expected: String, stream: TokenStream, expectPay: ByteArray?) {
        val termAtt = stream.getAttribute(CharTermAttribute::class)!!
        val payloadAtt = stream.getAttribute(PayloadAttribute::class)!!
        assertTrue(stream.incrementToken())
        assertEquals(expected, termAtt.toString())
        val payload = payloadAtt.payload
        if (payload != null) {
            assertTrue(payload.length == expectPay!!.size, "${payload.length} does not equal: ${expectPay.size}")
            for (i in expectPay.indices) {
                assertTrue(
                    expectPay[i] == payload.bytes[i + payload.offset],
                    "${expectPay[i]} does not equal: ${payload.bytes[i + payload.offset]}"
                )
            }
        } else {
            assertTrue(expectPay == null, "expectPay is not null and it should be")
        }
    }

    @Throws(Exception::class)
    private fun assertTermEquals(
        expected: String,
        stream: TokenStream,
        termAtt: CharTermAttribute,
        payAtt: PayloadAttribute,
        expectPay: ByteArray?
    ) {
        assertTrue(stream.incrementToken())
        assertEquals(expected, termAtt.toString())
        val payload: BytesRef? = payAtt.payload
        if (payload != null) {
            assertTrue(payload.length == expectPay!!.size, "${payload.length} does not equal: ${expectPay.size}")
            for (i in expectPay.indices) {
                assertTrue(
                    expectPay[i] == payload.bytes[i + payload.offset],
                    "${expectPay[i]} does not equal: ${payload.bytes[i + payload.offset]}"
                )
            }
        } else {
            assertTrue(expectPay == null, "expectPay is not null and it should be")
        }
    }
}
