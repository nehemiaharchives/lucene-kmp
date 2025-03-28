package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.jdkport.ByteBuffer
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.RamUsageEstimator
import kotlin.jvm.JvmOverloads


/**
 * A Term represents a word from text. This is the unit of search. It is composed of two elements,
 * the text of the word, as a string, and the name of the field that the text occurred in.
 *
 *
 * Note that terms may represent more than words from text fields, but also things like dates,
 * email addresses, urls, etc.
 */
class Term : Comparable<Term>, Accountable {
    var field: String
    var bytes: BytesRef

    /**
     * Constructs a Term with the given field and bytes.
     *
     *
     * Note that a null field or null bytes value results in undefined behavior for most Lucene
     * APIs that accept a Term parameter.
     *
     *
     * The provided BytesRef is copied when it is non null.
     */
    /**
     * Constructs a Term with the given field and empty text. This serves two purposes: 1) reuse of a
     * Term with the same field. 2) pattern for a query.
     *
     * @param fld field's name
     */
    @JvmOverloads
    constructor(fld: String, bytes: BytesRef = BytesRef()) {
        field = fld
        this.bytes = BytesRef.deepCopyOf(bytes)
    }

    /**
     * Constructs a Term with the given field and the bytes from a builder.
     *
     *
     * Note that a null field value results in undefined behavior for most Lucene APIs that accept
     * a Term parameter.
     */
    constructor(fld: String, bytesBuilder: BytesRefBuilder) {
        field = fld
        this.bytes = bytesBuilder.toBytesRef()
    }

    /**
     * Constructs a Term with the given field and text.
     *
     *
     * Note that a null field or null text value results in undefined behavior for most Lucene APIs
     * that accept a Term parameter.
     */
    constructor(fld: String, text: String) : this(fld, BytesRef(text))

    /**
     * Returns the field of this term. The field indicates the part of a document which this term came
     * from.
     */
    fun field(): String {
        return field
    }

    /**
     * Returns the text of this term. In the case of words, this is simply the text of the word. In
     * the case of dates and other types, this is an encoding of the object as a string.
     */
    fun text(): String {
        return bytes.utf8ToString()
    }

    /** Returns the bytes of this term, these should not be modified.  */
    fun bytes(): BytesRef {
        return bytes
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) return true
        if (obj == null) return false
        if (this::class != obj::class) return false
        val other = obj as Term
        if (field == null) {
            if (other.field != null) return false
        } else if (field != other.field) return false
        if (bytes == null) {
            if (other.bytes != null) return false
        } else if (!bytes!!.equals(other.bytes)) return false
        return true
    }

    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        result = prime * result + (if (field == null) 0 else field.hashCode())
        result = prime * result + (if (bytes == null) 0 else bytes.hashCode())
        return result
    }

    /**
     * Compares two terms, returning a negative integer if this term belongs before the argument, zero
     * if this term is equal to the argument, and a positive integer if this term belongs after the
     * argument.
     *
     *
     * The ordering of terms is first by field, then by text.
     */
    override fun compareTo(other: Term): Int {
        if (field == other.field) {
            return bytes!!.compareTo(other.bytes!!)
        } else {
            return field!!.compareTo(other.field!!)
        }
    }

    /**
     * Resets the field and text of a Term.
     *
     *
     * WARNING: the provided BytesRef is not copied, but used directly. Therefore the bytes should
     * not be modified after construction, for example, you should clone a copy rather than pass
     * reused bytes from a TermsEnum.
     */
    fun set(fld: String, bytes: BytesRef) {
        field = fld
        this.bytes = bytes
    }

    override fun toString(): String {
        return field + ":" + text()
    }

    public override fun ramBytesUsed(): Long {
        return (BASE_RAM_BYTES
                + RamUsageEstimator.sizeOfObject(field)
                + (if (bytes != null)
            RamUsageEstimator.alignObjectSize(
                (bytes!!.bytes.size + RamUsageEstimator.NUM_BYTES_ARRAY_HEADER).toLong()
            )
        else
            0L))
    }

    companion object {
        private val BASE_RAM_BYTES: Long = (RamUsageEstimator.shallowSizeOfInstance(Term::class)
                + RamUsageEstimator.shallowSizeOfInstance(BytesRef::class))

        /**
         * Returns a human-readable form of the term text.
         * If the bytes are not valid UTF-8 (i.e. decoding does not round-trip), then the fallback BytesRef.toString() is returned.
         */
        fun toString(termText: BytesRef): String {
            // Wrap the term's bytes in our ByteBuffer
            val bb = ByteBuffer.wrap(termText.bytes, termText.offset, termText.length)
            // Read the remaining bytes into an array
            val byteArray = ByteArray(bb.remaining())
            bb.get(byteArray)
            // Attempt to decode using UTF-8
            val decoded = byteArray.toString()
            // Check that re-encoding gives the same byte sequence.
            // (If the input had malformed sequences, replacement characters would appear.)
            return if (decoded.encodeToByteArray().contentEquals(byteArray)) {
                decoded
            } else {
                termText.toString()
            }
        }
    }
}
