package org.gnit.lucenekmp.tests.index

import okio.IOException
import org.gnit.lucenekmp.index.BinaryDocValues
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.FieldInfos
import org.gnit.lucenekmp.index.Fields
import org.gnit.lucenekmp.index.FilterLeafReader
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.SortedDocValues
import org.gnit.lucenekmp.index.SortedNumericDocValues
import org.gnit.lucenekmp.index.SortedSetDocValues
import org.gnit.lucenekmp.index.StoredFieldVisitor
import org.gnit.lucenekmp.index.StoredFields
import org.gnit.lucenekmp.index.TermVectors
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.util.FilterIterator

/**
 * A [FilterLeafReader] that exposes only a subset of fields from the underlying wrapped
 * reader.
 */
class FieldFilterLeafReader(
    val `in`: LeafReader,
    private val fields: MutableSet<String>,
    private val negate: Boolean
) : FilterLeafReader(`in`) {
    override val fieldInfos: FieldInfos

    init {
        val filteredInfos: ArrayList<FieldInfo> = ArrayList()
        for (fi in `in`.fieldInfos) {
            if (hasField(fi.name)) {
                filteredInfos.add(fi)
            }
        }
        fieldInfos =
            FieldInfos(filteredInfos.toTypedArray<FieldInfo>())
    }

    fun hasField(field: String?): Boolean {
        return negate xor fields.contains(field)
    }

    /*override fun getFieldInfos(): FieldInfos {
        return fieldInfos
    }*/

    @Throws(IOException::class)
    override fun termVectors(): TermVectors {
        val orig: TermVectors = super.termVectors()
        return object : TermVectors() {
            @Throws(IOException::class)
            override fun get(docID: Int): Fields? {
                var f: Fields? = orig.get(docID)
                if (f == null) {
                    return null
                }
                f = FieldFilterFields(f)
                // we need to check for emptyness, so we can return
                // null:
                return if (f.iterator().hasNext()) f else null
            }
        }
    }

    @Throws(IOException::class)
    override fun storedFields(): StoredFields {
        val orig: StoredFields = super.storedFields()
        return object : StoredFields() {
            @Throws(IOException::class)
            override fun document(docID: Int, visitor: StoredFieldVisitor) {
                orig.document(
                    docID,
                    object : StoredFieldVisitor() {
                        @Throws(IOException::class)
                        override fun binaryField(
                            fieldInfo: FieldInfo,
                            value: ByteArray
                        ) {
                            visitor.binaryField(fieldInfo, value)
                        }

                        @Throws(IOException::class)
                        override fun stringField(
                            fieldInfo: FieldInfo,
                            value: String
                        ) {
                            visitor.stringField(
                                fieldInfo,
                                value
                            )
                        }

                        @Throws(IOException::class)
                        override fun intField(
                            fieldInfo: FieldInfo,
                            value: Int
                        ) {
                            visitor.intField(fieldInfo, value)
                        }

                        @Throws(IOException::class)
                        override fun longField(
                            fieldInfo: FieldInfo,
                            value: Long
                        ) {
                            visitor.longField(fieldInfo, value)
                        }

                        @Throws(IOException::class)
                        override fun floatField(
                            fieldInfo: FieldInfo,
                            value: Float
                        ) {
                            visitor.floatField(fieldInfo, value)
                        }

                        @Throws(IOException::class)
                        override fun doubleField(
                            fieldInfo: FieldInfo,
                            value: Double
                        ) {
                            visitor.doubleField(fieldInfo, value)
                        }

                        @Throws(IOException::class)
                        override fun needsField(fieldInfo: FieldInfo): Status? {
                            return if (hasField(fieldInfo.name)) visitor.needsField(fieldInfo) else Status.NO
                        }
                    })
            }
        }
    }

    @Throws(IOException::class)
    override fun terms(field: String?): Terms? {
        return if (hasField(field)) super.terms(field) else null
    }

    @Throws(IOException::class)
    override fun getBinaryDocValues(field: String): BinaryDocValues? {
        return if (hasField(field)) super.getBinaryDocValues(field) else null
    }

    @Throws(IOException::class)
    override fun getSortedDocValues(field: String): SortedDocValues? {
        return if (hasField(field)) super.getSortedDocValues(field) else null
    }

    @Throws(IOException::class)
    override fun getSortedNumericDocValues(field: String): SortedNumericDocValues? {
        return if (hasField(field)) super.getSortedNumericDocValues(field) else null
    }

    @Throws(IOException::class)
    override fun getSortedSetDocValues(field: String): SortedSetDocValues? {
        return if (hasField(field)) super.getSortedSetDocValues(field) else null
    }

    @Throws(IOException::class)
    override fun getNormValues(field: String): NumericDocValues? {
        return if (hasField(field)) super.getNormValues(field) else null
    }

    override fun toString(): String {
        val sb = StringBuilder("FieldFilterLeafReader(reader=")
        sb.append(`in`).append(", fields=")
        if (negate) sb.append('!')
        return sb.append(fields).append(')').toString()
    }

    @Suppress("unused")
    private inner class FieldFilterFields(`in`: Fields) :
        FilterLeafReader.FilterFields(`in`) {
        override fun size(): Int {
            // this information is not cheap, return -1 like MultiFields does:
            return -1
        }

        override fun iterator(): MutableIterator<String> {
            return object : FilterIterator<String, String>(super.iterator()) {
                override fun predicateFunction(field: String): Boolean {
                    return hasField(field)
                }
            }
        }

        @Throws(IOException::class)
        override fun terms(field: String?): Terms? {
            return if (hasField(field)) super.terms(field) else null
        }
    }

    override val coreCacheHelper: CacheHelper?
        get() = null

    override val readerCacheHelper: CacheHelper?
        get() = null
}
