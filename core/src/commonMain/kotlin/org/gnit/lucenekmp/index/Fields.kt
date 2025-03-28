package org.gnit.lucenekmp.index

import kotlinx.io.IOException


/**
 * Provides a [Terms] index for fields that have it, and lists which fields do. This is
 * primarily an internal/experimental API (see [FieldsProducer]), although it is also used to
 * expose the set of term vectors per document.
 *
 * @lucene.experimental
 */
abstract class Fields
/** Sole constructor. (For invocation by subclass constructors, typically implicit.)  */
protected constructor() : Iterable<String?> {
    /** Returns an iterator that will step through all fields names. This will not return null.  */
    abstract override fun iterator(): MutableIterator<String?>

    /** Get the [Terms] for this field. This will return null if the field does not exist.  */
    @Throws(IOException::class)
    abstract fun terms(field: String?): Terms?

    /**
     * Returns the number of fields or -1 if the number of distinct field names is unknown. If &gt;=
     * 0, [.iterator] will return as many field names.
     */
    abstract fun size(): Int

    companion object {
        /** Zero-length `Fields` array.  */
        val EMPTY_ARRAY: Array<Fields?> = kotlin.arrayOfNulls<Fields>(0)
    }
}
