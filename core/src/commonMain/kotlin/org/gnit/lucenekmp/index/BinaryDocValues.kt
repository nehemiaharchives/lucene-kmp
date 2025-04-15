package org.gnit.lucenekmp.index

import kotlinx.io.IOException
import org.gnit.lucenekmp.util.BytesRef

/** A per-document numeric value.  */
abstract class BinaryDocValues
/** Sole constructor. (For invocation by subclass constructors, typically implicit.)  */
protected constructor() : DocValuesIterator() {
    /**
     * Returns the binary value for the current document ID. It is illegal to call this method after
     * [.advanceExact] returned `false`.
     *
     * @return binary value
     */
    @Throws(IOException::class)
    abstract fun binaryValue(): BytesRef
}
