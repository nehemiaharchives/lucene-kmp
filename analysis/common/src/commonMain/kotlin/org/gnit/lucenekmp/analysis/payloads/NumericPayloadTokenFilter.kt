package org.gnit.lucenekmp.analysis.payloads

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.PayloadAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TypeAttribute
import org.gnit.lucenekmp.util.BytesRef

/**
 * Assigns a payload to a token based on the [TypeAttribute].
 */
class NumericPayloadTokenFilter(input: TokenStream, payload: Float, typeMatch: String) : TokenFilter(input) {
    private val typeMatch: String = requireNotNull(typeMatch) { "typeMatch" }
    private val thePayload: BytesRef = BytesRef(PayloadHelper.encodeFloat(payload))

    private val payloadAtt: PayloadAttribute = addAttribute(PayloadAttribute::class)
    private val typeAtt: TypeAttribute = addAttribute(TypeAttribute::class)

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (input.incrementToken()) {
            if (typeAtt.type() == typeMatch) {
                payloadAtt.payload = thePayload
            }
            return true
        }
        return false
    }
}
