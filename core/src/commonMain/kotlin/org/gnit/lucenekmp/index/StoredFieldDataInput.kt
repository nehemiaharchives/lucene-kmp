package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.store.ByteArrayDataInput
import org.gnit.lucenekmp.store.DataInput

/**
 * A fixed size DataInput which includes the length of the input. For use as a StoredField.
 *
 * @param in the data input
 * @param length the length of the data input
 * @lucene.experimental
 */
class StoredFieldDataInput(val `in`: DataInput, val length: Int) {
    /** Creates a StoredFieldDataInput from a ByteArrayDataInput  */
    constructor(byteArrayDataInput: ByteArrayDataInput) : this(byteArrayDataInput, byteArrayDataInput.length())

    /** Returns the data input  */
    fun getDataInput(): DataInput{
        return `in`
    }
}
