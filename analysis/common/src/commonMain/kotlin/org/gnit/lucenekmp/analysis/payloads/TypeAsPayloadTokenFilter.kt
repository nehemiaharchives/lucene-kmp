package org.gnit.lucenekmp.analysis.payloads

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.PayloadAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TypeAttribute
import org.gnit.lucenekmp.util.BytesRef

/**
 * Makes the [TypeAttribute] a payload.
 */
class TypeAsPayloadTokenFilter(input: TokenStream) : TokenFilter(input) {
    private val payloadAtt: PayloadAttribute = addAttribute(PayloadAttribute::class)
    private val typeAtt: TypeAttribute = addAttribute(TypeAttribute::class)

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (input.incrementToken()) {
            val type = typeAtt.type()
            if (type.isNotEmpty()) {
                payloadAtt.payload = BytesRef(type)
            }
            return true
        }
        return false
    }
}
