package org.gnit.lucenekmp.analysis.payloads

import okio.IOException
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PayloadAttribute
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test
import kotlin.test.assertTrue

class TestTokenOffsetPayloadTokenFilter : BaseTokenStreamTestCase() {
    @Test
    @Throws(IOException::class)
    fun test() {
        val test = "The quick red fox jumped over the lazy brown dogs"

        val nptf = TokenOffsetPayloadTokenFilter(whitespaceMockTokenizer(test))
        var count = 0
        val payloadAtt = nptf.getAttribute(PayloadAttribute::class)!!
        val offsetAtt = nptf.getAttribute(OffsetAttribute::class)!!
        nptf.reset()
        while (nptf.incrementToken()) {
            val pay: BytesRef? = payloadAtt.payload
            assertTrue(pay != null, "pay is null and it shouldn't be")
            val data = pay!!.bytes
            val start = PayloadHelper.decodeInt(data, 0)
            assertTrue(start == offsetAtt.startOffset(), "$start does not equal: ${offsetAtt.startOffset()}")
            val end = PayloadHelper.decodeInt(data, 4)
            assertTrue(end == offsetAtt.endOffset(), "$end does not equal: ${offsetAtt.endOffset()}")
            count++
        }
        assertTrue(count == 10, "$count does not equal: 10")
    }
}
