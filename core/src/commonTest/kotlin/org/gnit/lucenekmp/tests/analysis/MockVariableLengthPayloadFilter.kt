package org.gnit.lucenekmp.tests.analysis

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.PayloadAttribute
import org.gnit.lucenekmp.util.BytesRef
import kotlin.random.Random

/** TokenFilter that adds random variable-length payloads. */
class MockVariableLengthPayloadFilter(
    private val random: Random,
    `in`: TokenStream
) : TokenFilter(`in`) {
    companion object {
        private const val MAXLENGTH = 129
    }

    private val payloadAtt: PayloadAttribute = addAttribute(PayloadAttribute::class)
    private val bytes = ByteArray(MAXLENGTH)
    private val payload = BytesRef(bytes)

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (input.incrementToken()) {
            random.nextBytes(bytes)
            payload.length = random.nextInt(MAXLENGTH)
            payloadAtt.payload = payload
            return true
        }
        return false
    }
}
