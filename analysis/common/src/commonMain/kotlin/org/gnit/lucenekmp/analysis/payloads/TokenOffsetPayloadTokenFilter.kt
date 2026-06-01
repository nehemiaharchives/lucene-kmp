package org.gnit.lucenekmp.analysis.payloads

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PayloadAttribute
import org.gnit.lucenekmp.util.BytesRef

/**
 * Adds the [OffsetAttribute.startOffset] and [OffsetAttribute.endOffset]. First 4 bytes are the
 * start.
 */
class TokenOffsetPayloadTokenFilter(input: TokenStream) : TokenFilter(input) {
    private val offsetAtt: OffsetAttribute = addAttribute(OffsetAttribute::class)
    private val payAtt: PayloadAttribute = addAttribute(PayloadAttribute::class)

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (input.incrementToken()) {
            val data = ByteArray(8)
            PayloadHelper.encodeInt(offsetAtt.startOffset(), data, 0)
            PayloadHelper.encodeInt(offsetAtt.endOffset(), data, 4)
            val payload = BytesRef(data)
            payAtt.payload = payload
            return true
        }
        return false
    }
}
