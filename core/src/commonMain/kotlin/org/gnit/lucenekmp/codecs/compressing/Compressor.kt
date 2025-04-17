package org.gnit.lucenekmp.codecs.compressing

import kotlinx.io.IOException
import org.gnit.lucenekmp.store.ByteBuffersDataInput
import org.gnit.lucenekmp.store.DataOutput

/** A data compressor.  */
abstract class Compressor
/** Sole constructor, typically called from sub-classes.  */
protected constructor() : AutoCloseable {
    /**
     * Compress bytes into `out`. It is the responsibility of the compressor to add all
     * necessary information so that a [Decompressor] will know when to stop decompressing bytes
     * from the stream.
     */
    @Throws(IOException::class)
    abstract fun compress(buffersInput: ByteBuffersDataInput, out: DataOutput)
}
