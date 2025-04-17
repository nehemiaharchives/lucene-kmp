package org.gnit.lucenekmp.codecs.compressing

import kotlinx.io.IOException
import org.gnit.lucenekmp.jdkport.Cloneable
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.util.BytesRef

/** A decompressor.  */
abstract class Decompressor
/** Sole constructor, typically called from sub-classes.  */
constructor() : Cloneable<Decompressor> {
    /**
     * Decompress bytes that were stored between offsets `offset` and `offset+length
    ` *  in the original stream from the compressed stream `in` to `bytes`
     * . After returning, the length of `bytes` (`bytes.length`) must be equal
     * to `length`. Implementations of this method are free to resize `bytes`
     * depending on their needs.
     *
     * @param in the input that stores the compressed stream
     * @param originalLength the length of the original data (before compression)
     * @param offset bytes before this offset do not need to be decompressed
     * @param length bytes after `offset+length` do not need to be decompressed
     * @param bytes a [BytesRef] where to store the decompressed data
     */
    @Throws(IOException::class)
    abstract fun decompress(
        `in`: DataInput, originalLength: Int, offset: Int, length: Int, bytes: BytesRef
    )

    abstract override fun clone(): Decompressor
}
