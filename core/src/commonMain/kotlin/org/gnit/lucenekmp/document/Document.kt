package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.index.IndexableField
import org.gnit.lucenekmp.util.BytesRef


/**
 * Documents are the unit of indexing and search.
 *
 *
 * A Document is a set of fields. Each field has a name and a textual value. A field may be
 * [stored][org.apache.lucene.index.IndexableFieldType.stored] with the document, in which
 * case it is returned with search hits on the document. Thus each document should typically contain
 * one or more stored fields which uniquely identify it.
 *
 *
 * Note that fields which are *not* [ ][org.apache.lucene.index.IndexableFieldType.stored] are *not* available in documents
 * retrieved from the index, e.g. with [ScoreDoc.doc] or [StoredFields.document].
 */
class Document
/** Constructs a new document with no fields.  */
    : Iterable<IndexableField> {
    private val fields: MutableList<IndexableField> = mutableListOf()

    override fun iterator(): MutableIterator<IndexableField> {
        return fields.iterator()
    }

    /**
     * Adds a field to a document. Several fields may be added with the same name. In this case, if
     * the fields are indexed, their text is treated as though appended for the purposes of search.
     *
     *
     * Note that add like the removeField(s) methods only makes sense prior to adding a document to
     * an index. These methods cannot be used to change the content of an existing index! In order to
     * achieve this, a document has to be deleted from an index and a new changed version of that
     * document has to be added.
     */
    fun add(field: IndexableField) {
        fields.add(field)
    }

    /**
     * Removes field with the specified name from the document. If multiple fields exist with this
     * name, this method removes the first field that has been added. If there is no field with the
     * specified name, the document remains unchanged.
     *
     *
     * Note that the removeField(s) methods like the add method only make sense prior to adding a
     * document to an index. These methods cannot be used to change the content of an existing index!
     * In order to achieve this, a document has to be deleted from an index and a new changed version
     * of that document has to be added.
     */
    fun removeField(name: String) {
        val it: MutableIterator<IndexableField> = fields.iterator()
        while (it.hasNext()) {
            val field: IndexableField = it.next()
            if (field.name() == name) {
                it.remove()
                return
            }
        }
    }

    /**
     * Removes all fields with the given name from the document. If there is no field with the
     * specified name, the document remains unchanged.
     *
     *
     * Note that the removeField(s) methods like the add method only make sense prior to adding a
     * document to an index. These methods cannot be used to change the content of an existing index!
     * In order to achieve this, a document has to be deleted from an index and a new changed version
     * of that document has to be added.
     */
    fun removeFields(name: String) {
        val it: MutableIterator<IndexableField> = fields.iterator()
        while (it.hasNext()) {
            val field: IndexableField = it.next()
            if (field.name() == name) {
                it.remove()
            }
        }
    }

    /**
     * Returns an array of byte arrays for of the fields that have the name specified as the method
     * parameter. This method returns an empty array when there are no matching fields. It never
     * returns null.
     *
     * @param name the name of the field
     * @return a `BytesRef[]` of binary field values
     */
    fun getBinaryValues(name: String): Array<BytesRef> {
        val result: MutableList<BytesRef> = mutableListOf()
        for (field in fields) {
            if (field.name() == name) {
                val bytes: BytesRef? = field.binaryValue()
                if (bytes != null) {
                    result.add(bytes)
                }
            }
        }

        return result.toTypedArray<BytesRef>()
    }

    /**
     * Returns an array of bytes for the first (or only) field that has the name specified as the
     * method parameter. This method will return `null` if no binary fields with the
     * specified name are available. There may be non-binary fields with the same name.
     *
     * @param name the name of the field.
     * @return a `BytesRef` containing the binary field value or `null`
     */
    fun getBinaryValue(name: String): BytesRef? {
        for (field in fields) {
            if (field.name() == name) {
                val bytes: BytesRef? = field.binaryValue()
                if (bytes != null) {
                    return bytes
                }
            }
        }
        return null
    }

    /**
     * Returns a field with the given name if any exist in this document, or null. If multiple fields
     * exists with this name, this method returns the first value added.
     */
    fun getField(name: String?): IndexableField? {
        for (field in fields) {
            if (field.name() == name) {
                return field
            }
        }
        return null
    }

    /**
     * Returns an array of [IndexableField]s with the given name. This method returns an empty
     * array when there are no matching fields. It never returns null.
     *
     * @param name the name of the field
     * @return a `Field[]` array
     */
    fun getFields(name: String): Array<IndexableField> {
        val result: MutableList<IndexableField> = mutableListOf()
        for (field in fields) {
            if (field.name() == name) {
                result.add(field)
            }
        }

        return result.toTypedArray<IndexableField>()
    }

    /**
     * Returns a List of all the fields in a document.
     *
     *
     * Note that fields which are *not* stored are *not* available in documents retrieved
     * from the index, e.g. [StoredFields.document].
     *
     * @return an immutable `List<Field>`
     */
    fun getFields(): MutableList<IndexableField> {
        return fields
    }

    /**
     * Returns an array of values of the field specified as the method parameter. This method returns
     * an empty array when there are no matching fields. It never returns null. For a numeric [ ] it returns the string value of the number. If you want the actual numeric field
     * instances back, use [.getFields].
     *
     * @param name the name of the field
     * @return a `String[]` of field values
     */
    fun getValues(name: String): Array<String?> {
        val result: MutableList<String> = mutableListOf()
        for (field in fields) {
            if (field.name() == name && field.stringValue() != null) {
                result.add(field.stringValue()!!)
            }
        }

        if (result.isEmpty()) {
            return NO_STRINGS
        }

        return result.toTypedArray<String?>()
    }

    /**
     * Returns the string value of the field with the given name if any exist in this document, or
     * null. If multiple fields exist with this name, this method returns the first value added. If
     * only binary fields with this name exist, returns null. For a numeric [StoredField] it
     * returns the string value of the number. If you want the actual numeric field instance back, use
     * [.getField].
     */
    fun get(name: String?): String? {
        for (field in fields) {
            if (field.name() == name && field.stringValue() != null) {
                return field.stringValue()
            }
        }
        return null
    }

    /** Prints the fields of a document for human consumption.  */
    override fun toString(): String {
        val buffer = StringBuilder()
        buffer.append("Document<")
        for (i in fields.indices) {
            val field: IndexableField = fields[i]
            buffer.append(field.toString())
            if (i != fields.size - 1) {
                buffer.append(" ")
            }
        }
        buffer.append(">")
        return buffer.toString()
    }

    /** Removes all the fields from document.  */
    fun clear() {
        fields.clear()
    }

    companion object {
        private val NO_STRINGS = kotlin.arrayOfNulls<String>(0)
    }
}
