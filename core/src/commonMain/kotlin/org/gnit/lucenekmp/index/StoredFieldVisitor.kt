package org.gnit.lucenekmp.index

import kotlinx.io.IOException


/**
 * Expert: provides a low-level means of accessing the stored field values in an index. See [ ][StoredFields.document].
 *
 *
 * **NOTE**: a `StoredFieldVisitor` implementation should not try to load or visit other
 * stored documents in the same reader because the implementation of stored fields for most codecs
 * is not reentrant and you will see strange exceptions as a result.
 *
 *
 * See [DocumentStoredFieldVisitor], which is a `StoredFieldVisitor` that builds
 * the [Document] containing all stored fields. This is used by [ ][StoredFields.document].
 *
 * @lucene.experimental
 */
abstract class StoredFieldVisitor
/** Sole constructor. (For invocation by subclass constructors, typically implicit.)  */
protected constructor() {
    /**
     * Expert: Process a binary field directly from the [StoredFieldDataInput]. Implementors of
     * this method must read `StoredFieldDataInput#length` bytes from the given [ ]. The default implementation reads all bytes in a newly created byte array
     * and calls [.binaryField].
     *
     * @param value the stored field data input.
     */
    @Throws(IOException::class)
    open fun binaryField(fieldInfo: FieldInfo, value: StoredFieldDataInput) {
        val length: Int = value.getLength()
        val data = ByteArray(length)
        value.getDataInput().readBytes(data, 0, value.getLength())
        binaryField(fieldInfo, data)
    }

    /**
     * Process a binary field.
     *
     * @param value newly allocated byte array with the binary contents.
     */
    @Throws(IOException::class)
    open fun binaryField(fieldInfo: FieldInfo, value: ByteArray) {
    }

    /** Process a string field.  */
    @Throws(IOException::class)
    open fun stringField(fieldInfo: FieldInfo, value: String) {
    }

    /** Process an int numeric field.  */
    @Throws(IOException::class)
    open fun intField(fieldInfo: FieldInfo, value: Int) {
    }

    /** Process a long numeric field.  */
    @Throws(IOException::class)
    open fun longField(fieldInfo: FieldInfo, value: Long) {
    }

    /** Process a float numeric field.  */
    @Throws(IOException::class)
    open fun floatField(fieldInfo: FieldInfo, value: Float) {
    }

    /** Process a double numeric field.  */
    @Throws(IOException::class)
    open fun doubleField(fieldInfo: FieldInfo, value: Double) {
    }

    /**
     * Hook before processing a field. Before a field is processed, this method is invoked so that
     * subclasses can return a [Status] representing whether they need that particular field or
     * not, or to stop processing entirely.
     */
    @Throws(IOException::class)
    abstract fun needsField(fieldInfo: FieldInfo): Status?

    /** Enumeration of possible return values for [.needsField].  */
    enum class Status {
        /** YES: the field should be visited.  */
        YES,

        /** NO: don't visit this field, but continue processing fields for this document.  */
        NO,

        /** STOP: don't visit this field and stop processing any other fields for this document.  */
        STOP
    }
}
