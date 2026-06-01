package org.gnit.lucenekmp.analysis.payloads

import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef

/**
 * Encode a character array Integer as a [BytesRef].
 *
 * See [org.gnit.lucenekmp.analysis.payloads.PayloadHelper.encodeInt].
 */
class IntegerEncoder : AbstractEncoder() {
    override fun encode(buffer: CharArray, offset: Int, length: Int): BytesRef {
        // TODO: improve this so that we don't have to new Strings
        val payload = ArrayUtil.parseInt(buffer, offset, length)
        val bytes = PayloadHelper.encodeInt(payload)
        return BytesRef(bytes)
    }
}
