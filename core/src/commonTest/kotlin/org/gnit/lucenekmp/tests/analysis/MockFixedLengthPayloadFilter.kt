package org.gnit.lucenekmp.tests.analysis

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.PayloadAttribute
import org.gnit.lucenekmp.util.BytesRef
import kotlin.random.Random

/** TokenFilter that adds random fixed-length payloads. */
class MockFixedLengthPayloadFilter(
    private val random: Random,
    `in`: TokenStream,
    length: Int
) : TokenFilter(`in`) {
    private val payloadAtt: PayloadAttribute = addAttribute(PayloadAttribute::class)
    private val bytes: ByteArray
    private val payload: BytesRef

    init {
        require(length >= 0) { "length must be >= 0" }
        bytes = ByteArray(length)
        payload = BytesRef(bytes)
    }

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (input.incrementToken()) {
            random.nextBytes(bytes)
            payloadAtt.setPayload(payload)
            return true
        }
        return false
    }
}
