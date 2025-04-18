package org.gnit.lucenekmp.index

import kotlinx.io.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.DocumentStoredFieldVisitor


/**
 * API for reading stored fields.
 *
 *
 * **NOTE**: This class is not thread-safe and should only be consumed in the thread where it
 * was acquired.
 */
abstract class StoredFields
/** Sole constructor. (For invocation by subclass constructors, typically implicit.)  */
protected constructor() {
    /**
     * Optional method: Give a hint to this [StoredFields] instance that the given document will
     * be read in the near future. This typically delegates to [IndexInput.prefetch] and is
     * useful to parallelize I/O across multiple documents.
     *
     *
     * NOTE: This API is expected to be called on a small enough set of doc IDs that they could all
     * fit in the page cache. If you plan on retrieving a very large number of documents, it may be a
     * good idea to perform calls to [.prefetch] and [.document] in batches instead of
     * prefetching all documents up-front.
     */
    @Throws(IOException::class)
    open fun prefetch(docID: Int) {
    }

    /**
     * Returns the stored fields of the `n`<sup>th</sup> `Document` in this
     * index. This is just sugar for using [DocumentStoredFieldVisitor].
     *
     *
     * **NOTE:** for performance reasons, this method does not check if the requested document
     * is deleted, and therefore asking for a deleted document may yield unspecified results. Usually
     * this is not required, however you can test if the doc is deleted by checking the [Bits]
     * returned from [MultiBits.getLiveDocs].
     *
     *
     * **NOTE:** only the content of a field is returned, if that field was stored during
     * indexing. Metadata like boost, omitNorm, IndexOptions, tokenized, etc., are not preserved.
     *
     * @throws CorruptIndexException if the index is corrupt
     * @throws IOException if there is a low-level IO error
     */
    // TODO: we need a separate StoredField, so that the
    // Document returned here contains that class not
    // IndexableField
    @Throws(IOException::class)
    fun document(docID: Int): Document {
        val visitor = DocumentStoredFieldVisitor()
        document(docID, visitor)
        return visitor.document
    }

    /**
     * Expert: visits the fields of a stored document, for custom processing/loading of each field. If
     * you simply want to load all fields, use [.document]. If you want to load a subset,
     * use [DocumentStoredFieldVisitor].
     */
    @Throws(IOException::class)
    abstract fun document(docID: Int, visitor: StoredFieldVisitor)

    /**
     * Like [.document] but only loads the specified fields. Note that this is simply sugar
     * for [DocumentStoredFieldVisitor.DocumentStoredFieldVisitor].
     */
    @Throws(IOException::class)
    fun document(docID: Int, fieldsToLoad: MutableSet<String>): Document {
        val visitor = DocumentStoredFieldVisitor(fieldsToLoad)
        document(docID, visitor)
        return visitor.document
    }
}
