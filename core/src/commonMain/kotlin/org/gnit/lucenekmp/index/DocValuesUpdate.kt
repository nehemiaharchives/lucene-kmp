package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.util.RamUsageEstimator.Companion.NUM_BYTES_ARRAY_HEADER
import org.gnit.lucenekmp.util.RamUsageEstimator.Companion.NUM_BYTES_OBJECT_HEADER
import org.gnit.lucenekmp.util.RamUsageEstimator.Companion.NUM_BYTES_OBJECT_REF

import okio.IOException
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef


/** An in-place update to a DocValues field.  */
abstract class DocValuesUpdate protected constructor(
    type: DocValuesType,
    term: Term,
    field: String,
    docIDUpTo: Int,
    hasValue: Boolean
) {
    val type: DocValuesType
    val term: Term
    val field: String

    // used in BufferedDeletes to apply this update only to a slice of docs. It's initialized to
    // BufferedUpdates.MAX_INT
    // since it's safe and most often used this way we save object creations.
    val docIDUpTo: Int
    val hasValue: Boolean

    /**
     * Constructor.
     *
     * @param term the [Term] which determines the documents that will be updated
     * @param field the [NumericDocValuesField] to update
     */
    init {
        require(docIDUpTo >= 0) { "$docIDUpTo must be >= 0" }
        this.type = type
        this.term = term
        this.field = field
        this.docIDUpTo = docIDUpTo
        this.hasValue = hasValue
    }

    abstract fun valueSizeInBytes(): Long

    fun sizeInBytes(): Long {
        var sizeInBytes = RAW_SIZE_IN_BYTES.toLong()
        sizeInBytes += term.field.length * Character.BYTES.toLong()
        sizeInBytes += term.bytes.bytes.size
        sizeInBytes += field.length * Character.BYTES.toLong()
        sizeInBytes += valueSizeInBytes()
        sizeInBytes += 1 // hasValue
        return sizeInBytes
    }

    abstract fun valueToString(): String

    @Throws(IOException::class)
    abstract fun writeTo(output: DataOutput)

    fun hasValue(): Boolean {
        return hasValue
    }

    override fun toString(): String {
        return ("term="
                + term
                + ",field="
                + field
                + ",value="
                + valueToString()
                + ",docIDUpTo="
                + docIDUpTo)
    }

    /** An in-place update to a binary DocValues field  */
    class BinaryDocValuesUpdate private constructor(
        term: Term,
        field: String,
        private val value: BytesRef?,
        docIDUpTo: Int
    ) : DocValuesUpdate(DocValuesType.BINARY, term, field, docIDUpTo, value != null) {

        constructor(term: Term, field: String, value: BytesRef) : this(term, field, value, BufferedUpdates.MAX_INT)

        fun prepareForApply(docIDUpTo: Int): BinaryDocValuesUpdate {
            if (docIDUpTo == this.docIDUpTo) {
                return this // it's a final value so we can safely reuse this instance
            }
            return BinaryDocValuesUpdate(term, field, value, docIDUpTo)
        }

        override fun valueSizeInBytes(): Long {
            return RAW_VALUE_SIZE_IN_BYTES + (value?.bytes?.size ?: 0)
        }

        override fun valueToString(): String {
            return value.toString()
        }

        fun getValue(): BytesRef? {
            require(hasValue) { "getValue should only be called if this update has a value" }
            return value
        }

        @Throws(IOException::class)
        override fun writeTo(out: DataOutput) {
            require(hasValue)
            out.writeVInt(value!!.length)
            out.writeBytes(value.bytes, value.offset, value.length)
        }

        companion object {
            /* Size of BytesRef: 2*INT + ARRAY_HEADER + PTR */
            private const val RAW_VALUE_SIZE_IN_BYTES: Long =
                2L * Int.SIZE_BYTES + NUM_BYTES_ARRAY_HEADER + NUM_BYTES_OBJECT_REF

            @Throws(IOException::class)
            fun readFrom(`in`: DataInput, scratch: BytesRef): BytesRef {
                scratch.length = `in`.readVInt()
                if (scratch.bytes.size < scratch.length) {
                    scratch.bytes = ArrayUtil.grow(scratch.bytes, scratch.length)
                }
                `in`.readBytes(scratch.bytes, 0, scratch.length)
                return scratch
            }
        }
    }

    /** An in-place update to a numeric DocValues field  */
    class NumericDocValuesUpdate private constructor(
        term: Term,
        field: String,
        private val value: Long,
        docIDUpTo: Int,
        hasValue: Boolean
    ) : DocValuesUpdate(DocValuesType.NUMERIC, term, field, docIDUpTo, hasValue) {
        constructor(term: Term, field: String, value: Long) : this(term, field, value, BufferedUpdates.MAX_INT, true)

        constructor(term: Term, field: String, value: Long?) : this(
            term,
            field,
            value ?: -1,
            BufferedUpdates.MAX_INT,
            value != null
        )

        fun prepareForApply(docIDUpTo: Int): NumericDocValuesUpdate {
            if (docIDUpTo == this.docIDUpTo) {
                return this
            }
            return NumericDocValuesUpdate(term, field, value, docIDUpTo, hasValue)
        }

        override fun valueSizeInBytes(): Long {
            return Long.SIZE_BYTES.toLong()
        }

        override fun valueToString(): String {
            return if (hasValue) value.toString() else "null"
        }

        @Throws(IOException::class)
        override fun writeTo(out: DataOutput) {
            require(hasValue)
            out.writeZLong(value)
        }

        fun getValue(): Long {
            require(hasValue) { "getValue should only be called if this update has a value" }
            return value
        }

        companion object {
            @Throws(IOException::class)
            fun readFrom(`in`: DataInput): Long {
                return `in`.readZLong()
            }
        }
    }

    companion object {
        /* Rough logic: OBJ_HEADER + 3*PTR + INT
   * Term: OBJ_HEADER + 2*PTR
   *   Term.field: 2*OBJ_HEADER + 4*INT + PTR + string.length*CHAR
   *   Term.bytes: 2*OBJ_HEADER + 2*INT + PTR + bytes.length
   * String: 2*OBJ_HEADER + 4*INT + PTR + string.length*CHAR
   * T: OBJ_HEADER
   */
        private const val RAW_SIZE_IN_BYTES: Int =
            8 * NUM_BYTES_OBJECT_HEADER + 8 * NUM_BYTES_OBJECT_REF + 8 * Int.SIZE_BYTES
    }
}
