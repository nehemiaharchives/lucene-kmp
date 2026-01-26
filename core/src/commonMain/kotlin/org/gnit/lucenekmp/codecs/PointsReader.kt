package org.gnit.lucenekmp.codecs

import okio.IOException
import org.gnit.lucenekmp.index.PointValues


/**
 * Abstract API to visit point values.
 *
 * @lucene.experimental
 */
abstract class PointsReader  /** Sole constructor. (For invocation by subclass constructors, typically implicit.)  */
protected constructor() : AutoCloseable {

    /**
     * Checks consistency of this reader.
     *
     *
     * Note that this may be costly in terms of I/O, e.g. may involve computing a checksum value
     * against large data files.
     *
     * @lucene.internal
     */
    @Throws(IOException::class) abstract fun checkIntegrity()

    /**
     * Return [PointValues] for the given `field`. The behavior is undefined if the given
     * field doesn't have points enabled on its [FieldInfo].
     */
    @Throws(IOException::class) abstract fun getValues(field: String): PointValues?

    open val mergeInstance: PointsReader
        /**
         * Returns an instance optimized for merging. This instance may only be used in the thread that
         * acquires it.
         *
         *
         * The default implementation returns `this`
         */
        get() = this
}
