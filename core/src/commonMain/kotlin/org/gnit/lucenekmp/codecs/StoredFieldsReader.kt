package org.gnit.lucenekmp.codecs

import kotlinx.io.IOException
import org.gnit.lucenekmp.index.StoredFields


/**
 * Codec API for reading stored fields.
 *
 *
 * You need to implement [.document] to read the stored fields for
 * a document, implement [.clone] (creating clones of any IndexInputs used, etc), and [ ][.close]
 *
 * @lucene.experimental
 */
abstract class StoredFieldsReader
/** Sole constructor. (For invocation by subclass constructors, typically implicit.)  */
protected constructor() : StoredFields(), Cloneable, AutoCloseable {
    public abstract override fun clone(): StoredFieldsReader

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

    open val mergeInstance: StoredFieldsReader
        /**
         * Returns an instance optimized for merging. This instance may not be cloned.
         *
         *
         * The default implementation returns `this`
         */
        get() = this
}
