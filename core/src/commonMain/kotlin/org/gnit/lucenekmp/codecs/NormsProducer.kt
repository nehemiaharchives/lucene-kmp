package org.gnit.lucenekmp.codecs

import kotlinx.io.IOException
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.NumericDocValues


/**
 * Abstract API that produces field normalization values
 *
 * @lucene.experimental
 */
abstract class NormsProducer
/** Sole constructor. (For invocation by subclass constructors, typically implicit.)  */
protected constructor() : AutoCloseable {
    /**
     * Returns [NumericDocValues] for this field. The returned instance need not be thread-safe:
     * it will only be used by a single thread. The behavior is undefined if the given field doesn't
     * have norms enabled on its [FieldInfo]. The return value is never `null`.
     */
    @Throws(IOException::class)
    abstract fun getNorms(field: FieldInfo): NumericDocValues

    /**
     * Checks consistency of this producer
     *
     *
     * Note that this may be costly in terms of I/O, e.g. may involve computing a checksum value
     * against large data files.
     *
     * @lucene.internal
     */
    @Throws(IOException::class)
    abstract fun checkIntegrity()

    val mergeInstance: NormsProducer
        /**
         * Returns an instance optimized for merging. This instance may only be used from the thread that
         * acquires it.
         *
         *
         * The default implementation returns `this`
         */
        get() = this
}
