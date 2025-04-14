package org.gnit.lucenekmp.codecs

import kotlinx.io.IOException
import org.gnit.lucenekmp.index.Fields


/**
 * Abstract API that produces terms, doc, freq, prox, offset and payloads postings.
 *
 * @lucene.experimental
 */
abstract class FieldsProducer
/** Sole constructor. (For invocation by subclass constructors, typically implicit.)  */
protected constructor() : Fields(), AutoCloseable {
    @Throws(IOException::class)
    abstract override fun close()

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

    open val mergeInstance: FieldsProducer
        /**
         * Returns an instance optimized for merging. This instance may only be consumed in the thread
         * that called [.getMergeInstance].
         *
         *
         * The default implementation returns `this`
         */
        get() = this
}
