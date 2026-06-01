package org.gnit.lucenekmp.analysis.payloads

import org.gnit.lucenekmp.jdkport.fromCharArray
import org.gnit.lucenekmp.util.BytesRef

/** Does nothing other than convert the char array to a byte array using the specified encoding. */
class IdentityEncoder : AbstractEncoder() {
    override fun encode(buffer: CharArray, offset: Int, length: Int): BytesRef {
        return BytesRef(String.fromCharArray(buffer, offset, length).encodeToByteArray())
    }
}
