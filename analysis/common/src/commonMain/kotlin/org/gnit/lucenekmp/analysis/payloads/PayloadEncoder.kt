package org.gnit.lucenekmp.analysis.payloads

import org.gnit.lucenekmp.util.BytesRef

/**
 * Mainly for use with the DelimitedPayloadTokenFilter, converts char buffers to [BytesRef].
 *
 * NOTE: This interface is subject to change
 */
interface PayloadEncoder {
    fun encode(buffer: CharArray): BytesRef

    /**
     * Convert a char array to a [BytesRef]
     *
     * @return encoded [BytesRef]
     */
    fun encode(buffer: CharArray, offset: Int, length: Int): BytesRef
}
