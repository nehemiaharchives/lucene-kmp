package org.gnit.lucenekmp.analysis.payloads

import org.gnit.lucenekmp.jdkport.fromCharArray
import org.gnit.lucenekmp.util.BytesRef

/**
 * Encode a character array Float as a [BytesRef].
 *
 * @see org.gnit.lucenekmp.analysis.payloads.PayloadHelper.encodeFloat
 */
class FloatEncoder : AbstractEncoder() {
    override fun encode(buffer: CharArray, offset: Int, length: Int): BytesRef {
        // TODO: improve this so that we don't have to new Strings
        val payload = String.fromCharArray(buffer, offset, length).toFloat()
        val bytes = PayloadHelper.encodeFloat(payload)
        return BytesRef(bytes)
    }
}
