package org.gnit.lucenekmp.analysis.tokenattributes

import org.gnit.lucenekmp.util.Attribute
import org.gnit.lucenekmp.util.BytesRef


/**
 * The payload of a Token.
 *
 *
 * The payload is stored in the index at each position, and can be used to influence scoring when
 * using Payload-based queries.
 *
 *
 * NOTE: because the payload will be stored at each position, it's usually best to use the
 * minimum number of bytes necessary. Some codec implementations may optimize payload storage when
 * all payloads have the same length.
 *
 * @see org.apache.lucene.index.PostingsEnum
 */
interface PayloadAttribute : Attribute {
    /**
     * Returns this Token's payload.
     *
     * @see .setPayload
     */
    val payload: BytesRef

    /**
     * Sets this Token's payload.
     *
     * @see .getPayload
     */
    fun setPayload(payload: BytesRef)
}
