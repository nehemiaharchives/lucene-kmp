package org.gnit.lucenekmp.analysis.payloads

import org.gnit.lucenekmp.util.BytesRef

/** Base class for payload encoders. */
abstract class AbstractEncoder : PayloadEncoder {
    override fun encode(buffer: CharArray): BytesRef {
        return encode(buffer, 0, buffer.size)
    }
}
