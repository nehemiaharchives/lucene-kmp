package org.gnit.lucenekmp.util.bkd

import org.gnit.lucenekmp.util.BytesRef

/**
 * Represents a dimensional point value written in the BKD tree.
 *
 * @lucene.internal
 */
interface PointValue {
    /** Returns the packed values for the dimensions  */
    fun packedValue(): BytesRef

    /** Returns the docID  */
    fun docID(): Int

    /** Returns the byte representation of the packed value together with the docID  */
    fun packedValueDocIDBytes(): BytesRef
}
