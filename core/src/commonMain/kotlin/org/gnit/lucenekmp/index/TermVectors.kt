package org.gnit.lucenekmp.index

import kotlinx.io.IOException


/**
 * API for reading term vectors.
 *
 *
 * **NOTE**: This class is not thread-safe and should only be consumed in the thread where it
 * was acquired.
 */
abstract class TermVectors
/** Sole constructor. (For invocation by subclass constructors, typically implicit.)  */
protected constructor() {
    /**
     * Optional method: Give a hint to this [TermVectors] instance that the given document will
     * be read in the near future. This typically delegates to [IndexInput.prefetch] and is
     * useful to parallelize I/O across multiple documents.
     *
     *
     * NOTE: This API is expected to be called on a small enough set of doc IDs that they could all
     * fit in the page cache. If you plan on retrieving a very large number of documents, it may be a
     * good idea to perform calls to [.prefetch] and [.get] in batches instead of
     * prefetching all documents up-front.
     */
    @Throws(IOException::class)
    open fun prefetch(docID: Int) {
    }

    /**
     * Returns term vectors for this document, or null if term vectors were not indexed.
     *
     *
     * The returned Fields instance acts like a single-document inverted index (the docID will be
     * 0). If offsets are available they are in an [OffsetAttribute] available from the [ ].
     */
    @Throws(IOException::class)
    abstract fun get(doc: Int): Fields?

    /**
     * Retrieve term vector for this document and field, or null if term vectors were not indexed.
     *
     *
     * The returned Terms instance acts like a single-document inverted index (the docID will be
     * 0). If offsets are available they are in an [OffsetAttribute] available from the [ ].
     */
    @Throws(IOException::class)
    fun get(doc: Int, field: String?): Terms? {
        val vectors: Fields? = get(doc)
        if (vectors == null) {
            return null
        }
        return vectors.terms(field)
    }

    companion object {
        /** Instance that never returns term vectors  */
        val EMPTY: TermVectors = object : TermVectors() {
            override fun get(doc: Int): Fields? {
                return null
            }
        }
    }
}
