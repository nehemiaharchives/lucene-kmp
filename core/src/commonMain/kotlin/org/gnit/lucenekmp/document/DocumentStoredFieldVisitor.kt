package org.gnit.lucenekmp.document

import okio.IOException
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.StoredFieldVisitor


/**
 * A [StoredFieldVisitor] that creates a [Document] from stored fields.
 *
 *
 * This visitor supports loading all stored fields, or only specific requested fields provided
 * from a [Set].
 *
 *
 * This is used by [StoredFields.document] to load a document.
 *
 * @lucene.experimental
 */
class DocumentStoredFieldVisitor : StoredFieldVisitor {
    /**
     * Retrieve the visited document.
     *
     * @return [Document] populated with stored fields. Note that only the stored information in
     * the field instances is valid, data such as indexing options, term vector options, etc is
     * not set.
     */
    val document: Document = Document()
    private val fieldsToAdd: MutableSet<String>

    /**
     * Load only fields named in the provided `Set<String>`.
     *
     * @param fieldsToAdd Set of fields to load, or `null` (all fields).
     */
    constructor(fieldsToAdd: MutableSet<String>) {
        this.fieldsToAdd = fieldsToAdd
    }

    /** Load only fields named in the provided fields.  */
    constructor(vararg fields: String) {
        fieldsToAdd = mutableSetOf(*fields)
    }

    /** Load all stored fields.  */
    constructor() {
        this.fieldsToAdd = mutableSetOf()
    }

    @Throws(IOException::class)
    override fun binaryField(fieldInfo: FieldInfo, value: ByteArray) {
        document.add(StoredField(fieldInfo.name, value))
    }

    @Throws(IOException::class)
    override fun stringField(fieldInfo: FieldInfo, value: String) {
        val ft: FieldType = FieldType(TextField.TYPE_STORED)
        ft.setStoreTermVectors(fieldInfo.hasTermVectors())
        ft.setOmitNorms(fieldInfo.omitsNorms())
        ft.setIndexOptions(fieldInfo.indexOptions)
        document.add(
            StoredField(fieldInfo.name, value, ft)
        )
    }

    override fun intField(fieldInfo: FieldInfo, value: Int) {
        document.add(StoredField(fieldInfo.name, value))
    }

    override fun longField(fieldInfo: FieldInfo, value: Long) {
        document.add(StoredField(fieldInfo.name, value))
    }

    override fun floatField(fieldInfo: FieldInfo, value: Float) {
        document.add(StoredField(fieldInfo.name, value))
    }

    override fun doubleField(fieldInfo: FieldInfo, value: Double) {
        document.add(StoredField(fieldInfo.name, value))
    }

    @Throws(IOException::class)
    override fun needsField(fieldInfo: FieldInfo): Status {
        return if (fieldsToAdd == null || fieldsToAdd.contains(fieldInfo.name)) Status.YES else Status.NO
    }
}
