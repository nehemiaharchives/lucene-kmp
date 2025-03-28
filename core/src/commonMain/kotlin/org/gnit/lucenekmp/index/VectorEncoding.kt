package org.gnit.lucenekmp.index


/** The numeric datatype of the vector values.  */
enum class VectorEncoding(
    /**
     * The number of bytes required to encode a scalar in this format. A vector will nominally require
     * dimension * byteSize bytes of storage.
     */
    val byteSize: Int
) {
    /**
     * Encodes vector using 8 bits of precision per sample. Values provided with higher precision (eg:
     * queries provided as float) *must* be in the range [-128, 127]. NOTE: this can enable
     * significant storage savings and faster searches, at the cost of some possible loss of
     * precision.
     */
    BYTE(1),

    /** Encodes vector using 32 bits of precision per sample in IEEE floating point format.  */
    FLOAT32(4)
}
