package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.util.BytesRef


/** This class contains utility methods and constants for DocValues  */
object DocValues {
    /** An empty [BinaryDocValues] which returns no documents  */
    fun emptyBinary(): BinaryDocValues {
        return object : BinaryDocValues() {
            private var doc = -1

            override fun advance(target: Int): Int {
                return NO_MORE_DOCS.also { doc = it }
            }

            @Throws(IOException::class)
            override fun advanceExact(target: Int): Boolean {
                doc = target
                return false
            }

            override fun docID(): Int {
                return doc
            }

            override fun nextDoc(): Int {
                return NO_MORE_DOCS.also { doc = it }
            }

            override fun cost(): Long {
                return 0
            }

            override fun binaryValue(): BytesRef? {
                require(false)
                return null
            }
        }
    }

    /** An empty NumericDocValues which returns no documents  */
    fun emptyNumeric(): NumericDocValues {
        return object : NumericDocValues() {
            private var doc = -1

            override fun advance(target: Int): Int {
                return NO_MORE_DOCS.also { doc = it }
            }

            @Throws(IOException::class)
            override fun advanceExact(target: Int): Boolean {
                doc = target
                return false
            }

            override fun docID(): Int {
                return doc
            }

            override fun nextDoc(): Int {
                return NO_MORE_DOCS.also { doc = it }
            }

            override fun cost(): Long {
                return 0
            }

            override fun longValue(): Long {
                require(false)
                return 0
            }
        }
    }

    /** An empty SortedDocValues which returns [BytesRef.EMPTY_BYTES] for every document  */
    fun emptySorted(): SortedDocValues {
        val empty = BytesRef()
        return object : SortedDocValues() {
            private var doc = -1

            override fun advance(target: Int): Int {
                return NO_MORE_DOCS.also { doc = it }
            }

            @Throws(IOException::class)
            override fun advanceExact(target: Int): Boolean {
                doc = target
                return false
            }

            override fun docID(): Int {
                return doc
            }

            override fun nextDoc(): Int {
                return NO_MORE_DOCS.also { doc = it }
            }

            override fun cost(): Long {
                return 0
            }

            override fun ordValue(): Int {
                require(false)
                return -1
            }

            override fun lookupOrd(ord: Int): BytesRef {
                return empty
            }

            override val valueCount: Int
                get() = 0
        }
    }

    /** An empty SortedNumericDocValues which returns zero values for every document  */
    fun emptySortedNumeric(): SortedNumericDocValues {
        return singleton(emptyNumeric())
    }

    /** An empty SortedDocValues which returns [BytesRef.EMPTY_BYTES] for every document  */
    fun emptySortedSet(): SortedSetDocValues {
        return singleton(emptySorted())
    }

    /** Returns a multi-valued view over the provided SortedDocValues  */
    fun singleton(dv: SortedDocValues): SortedSetDocValues {
        return SingletonSortedSetDocValues(dv)
    }

    /**
     * Returns a single-valued view of the SortedSetDocValues, if it was previously wrapped with
     * [.singleton], or null.
     */
    fun unwrapSingleton(dv: SortedSetDocValues): SortedDocValues? {
        return if (dv is SingletonSortedSetDocValues) {
            dv.sortedDocValues
        } else {
            null
        }
    }

    /**
     * Returns a single-valued view of the SortedNumericDocValues, if it was previously wrapped with
     * [.singleton], or null.
     */
    fun unwrapSingleton(dv: SortedNumericDocValues): NumericDocValues? {
        return if (dv is SingletonSortedNumericDocValues) {
            dv.numericDocValues
        } else {
            null
        }
    }

    /** Returns a multi-valued view over the provided NumericDocValues  */
    fun singleton(dv: NumericDocValues): SortedNumericDocValues {
        return SingletonSortedNumericDocValues(dv)
    }

    // some helpers, for transition from fieldcache apis.
    // as opposed to the LeafReader apis (which must be strict for consistency), these are lenient
    // helper method: to give a nice error when LeafReader.getXXXDocValues returns null.
    private fun checkField(`in`: LeafReader, field: String, vararg expected: DocValuesType) {
        val fi: FieldInfo? = `in`.fieldInfos.fieldInfo(field)
        if (fi != null) {
            val actual: DocValuesType = fi.docValuesType
            throw IllegalStateException(
                ("unexpected docvalues type "
                        + actual
                        + " for field '"
                        + field
                        + "' "
                        + (if (expected.size == 1)
                    "(expected=" + expected[0]
                else
                    "(expected one of " + expected.contentToString())
                        + "). "
                        + "Re-index with correct docvalues type.")
            )
        }
    }

    /**
     * Returns NumericDocValues for the field, or [.emptyNumeric] if it has none.
     *
     * @return docvalues instance, or an empty instance if `field` does not exist in this
     * reader.
     * @throws IllegalStateException if `field` exists, but was not indexed with docvalues.
     * @throws IllegalStateException if `field` has docvalues, but the type is not [     ][DocValuesType.NUMERIC].
     * @throws IOException if an I/O error occurs.
     */
    @Throws(IOException::class)
    fun getNumeric(reader: LeafReader, field: String): NumericDocValues {
        val dv: NumericDocValues? = reader.getNumericDocValues(field)
        if (dv == null) {
            checkField(reader, field, DocValuesType.NUMERIC)
            return emptyNumeric()
        } else {
            return dv
        }
    }

    /**
     * Returns BinaryDocValues for the field, or [.emptyBinary] if it has none.
     *
     * @return docvalues instance, or an empty instance if `field` does not exist in this
     * reader.
     * @throws IllegalStateException if `field` exists, but was not indexed with docvalues.
     * @throws IllegalStateException if `field` has docvalues, but the type is not [     ][DocValuesType.BINARY].
     * @throws IOException if an I/O error occurs.
     */
    @Throws(IOException::class)
    fun getBinary(reader: LeafReader, field: String): BinaryDocValues {
        val dv: BinaryDocValues? = reader.getBinaryDocValues(field)
        if (dv == null) {
            checkField(reader, field, DocValuesType.BINARY)
            return emptyBinary()
        }
        return dv
    }

    /**
     * Returns SortedDocValues for the field, or [.emptySorted] if it has none.
     *
     * @return docvalues instance, or an empty instance if `field` does not exist in this
     * reader.
     * @throws IllegalStateException if `field` exists, but was not indexed with docvalues.
     * @throws IllegalStateException if `field` has docvalues, but the type is not [     ][DocValuesType.SORTED].
     * @throws IOException if an I/O error occurs.
     */
    @Throws(IOException::class)
    fun getSorted(reader: LeafReader, field: String): SortedDocValues {
        val dv: SortedDocValues? = reader.getSortedDocValues(field)
        if (dv == null) {
            checkField(reader, field, DocValuesType.SORTED)
            return emptySorted()
        } else {
            return dv
        }
    }

    /**
     * Returns SortedNumericDocValues for the field, or [.emptySortedNumeric] if it has none.
     *
     * @return docvalues instance, or an empty instance if `field` does not exist in this
     * reader.
     * @throws IllegalStateException if `field` exists, but was not indexed with docvalues.
     * @throws IllegalStateException if `field` has docvalues, but the type is not [     ][DocValuesType.SORTED_NUMERIC] or [DocValuesType.NUMERIC].
     * @throws IOException if an I/O error occurs.
     */
    @Throws(IOException::class)
    fun getSortedNumeric(reader: LeafReader, field: String): SortedNumericDocValues {
        val dv: SortedNumericDocValues? = reader.getSortedNumericDocValues(field)
        if (dv == null) {
            val single: NumericDocValues? = reader.getNumericDocValues(field)
            if (single == null) {
                checkField(reader, field, DocValuesType.SORTED_NUMERIC, DocValuesType.NUMERIC)
                return emptySortedNumeric()
            }
            return singleton(single)
        }
        return dv
    }

    /**
     * Returns SortedSetDocValues for the field, or [.emptySortedSet] if it has none.
     *
     * @return docvalues instance, or an empty instance if `field` does not exist in this
     * reader.
     * @throws IllegalStateException if `field` exists, but was not indexed with docvalues.
     * @throws IllegalStateException if `field` has docvalues, but the type is not [     ][DocValuesType.SORTED_SET] or [DocValuesType.SORTED].
     * @throws IOException if an I/O error occurs.
     */
    @Throws(IOException::class)
    fun getSortedSet(reader: LeafReader, field: String): SortedSetDocValues {
        var dv: SortedSetDocValues? = reader.getSortedSetDocValues(field)
        if (dv == null) {
            val sorted: SortedDocValues? = reader.getSortedDocValues(field)
            if (sorted == null) {
                checkField(reader, field, DocValuesType.SORTED, DocValuesType.SORTED_SET)
                return emptySortedSet()
            }
            dv = singleton(sorted)
        }
        return dv
    }

    /** Returns `true` if the specified docvalues fields have not been updated  */
    fun isCacheable(ctx: LeafReaderContext, vararg fields: String): Boolean {
        for (field in fields) {
            val fi: FieldInfo? = ctx.reader().fieldInfos.fieldInfo(field)
            if (fi != null && fi.docValuesGen > -1) return false
        }
        return true
    }
}
