package org.gnit.lucenekmp.codecs

import okio.IOException
import org.gnit.lucenekmp.index.TermVectors
import org.gnit.lucenekmp.jdkport.Cloneable

/**
 * Codec API for reading term vectors:
 *
 * @lucene.experimental
 */
abstract class TermVectorsReader
/** Sole constructor. (For invocation by subclass constructors, typically implicit.)  */
protected constructor() : TermVectors(), Cloneable<TermVectorsReader>, AutoCloseable {
    /**
     * Checks consistency of this reader.
     *
     *
     * Note that this may be costly in terms of I/O, e.g. may involve computing a checksum value
     * against large data files.
     *
     * @lucene.internal
     */
    @Throws(IOException::class)
    abstract fun checkIntegrity()

    /** Create a clone that one caller at a time may use to read term vectors.  */
    abstract override fun clone(): TermVectorsReader

    open val mergeInstance: TermVectorsReader
        /**
         * Returns an instance optimized for merging. This instance may only be consumed in the thread
         * that called [.getMergeInstance].
         *
         *
         * The default implementation returns `this`
         */
        get() = this
}
