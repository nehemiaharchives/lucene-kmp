package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.jdkport.ByteBuffer
import org.gnit.lucenekmp.store.ByteBuffersDataInput
import org.gnit.lucenekmp.store.ByteBuffersDataOutput
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.StringHelper

/**
 * Prefix codes term instances (prefixes are shared). This is expected to be faster to build than a
 * FST and might also be more compact if there are no common suffixes.
 *
 * @lucene.internal
 */
class PrefixCodedTerms private constructor(private val content: MutableList<ByteBuffer>, private val size: Long) :
    Accountable {
    private var delGen: Long = 0
    private var lazyHash = 0

    override fun ramBytesUsed(): Long {
        return content.sumOf { buf: ByteBuffer -> buf.capacity.toLong() } + 2 * Long.SIZE_BYTES
    }

    /** Records del gen for this packet.  */
    fun setDelGen(delGen: Long) {
        this.delGen = delGen
    }

    /** Builds a PrefixCodedTerms: call add repeatedly, then finish.  */
    class Builder
    /** Sole constructor.  */
    {
        private val output: ByteBuffersDataOutput = ByteBuffersDataOutput()
        private val lastTerm = Term("")
        private val lastTermBytes: BytesRefBuilder = BytesRefBuilder()
        private var size: Long = 0

        /** add a term  */
        fun add(term: Term) {
            add(term.field(), term.bytes())
        }

        /** add a term. This fully consumes in the incoming [BytesRef].  */
        fun add(field: String, bytes: BytesRef) {
            require(lastTerm == Term("") || Term(field, bytes) > lastTerm)

            try {
                val prefix: Int
                if (size > 0 && field == lastTerm.field) {
                    // same field as the last term
                    prefix = StringHelper.bytesDifference(lastTerm.bytes, bytes)
                    output.writeVInt(prefix shl 1)
                } else {
                    // field change
                    prefix = 0
                    output.writeVInt(1)
                    output.writeString(field)
                }

                val suffix: Int = bytes.length - prefix
                output.writeVInt(suffix)
                output.writeBytes(bytes.bytes, bytes.offset + prefix, suffix)
                lastTermBytes.copyBytes(bytes)
                lastTerm.bytes = lastTermBytes.get()
                lastTerm.field = field
                size += 1
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }

        /** return finalized form  */
        fun finish(): PrefixCodedTerms {
            return PrefixCodedTerms(output.toBufferList(), size)
        }
    }

    /** An iterator over the list of terms stored in a [PrefixCodedTerms].  */
    class TermIterator internal constructor(val delGen: Long, val input: ByteBuffersDataInput) : FieldTermIterator() {
        val builder: BytesRefBuilder = BytesRefBuilder()
        val bytes: BytesRef = builder.get()
        val end: Long = input.length()
        var field: String? = ""

        override fun next(): BytesRef? {
            if (input.position() < end) {
                try {
                    val code: Int = input.readVInt()
                    val newField = (code and 1) != 0
                    if (newField) {
                        field = input.readString()
                    }
                    val prefix = code ushr 1
                    val suffix: Int = input.readVInt()
                    readTermBytes(prefix, suffix)
                    return bytes
                } catch (e: IOException) {
                    throw RuntimeException(e)
                }
            } else {
                field = null
                return null
            }
        }

        // TODO: maybe we should freeze to FST or automaton instead
        @Throws(IOException::class)
        private fun readTermBytes(prefix: Int, suffix: Int) {
            builder.grow(prefix + suffix)
            input.readBytes(builder.bytes(), prefix, suffix)
            builder.setLength(prefix + suffix)
        }

        // Copied from parent-class because javadoc doesn't do it for some reason
        /**
         * Returns current field. This method should not be called after iteration is done. Note that
         * you may use == to detect a change in field.
         */
        override fun field(): String? {
            return field
        }

        // Copied from parent-class because javadoc doesn't do it for some reason
        /** Del gen of the current term.  */
        override fun delGen(): Long {
            return delGen
        }
    }

    /** Return an iterator over the terms stored in this [PrefixCodedTerms].  */
    fun iterator(): TermIterator {
        return TermIterator(delGen, ByteBuffersDataInput(content))
    }

    /** Return the number of terms stored in this [PrefixCodedTerms].  */
    fun size(): Long {
        return size
    }

    override fun hashCode(): Int {
        if (lazyHash == 0) {
            var h = 1
            for (bb in content) {
                h = h + 31 * bb.hashCode()
            }
            h = 31 * h + (delGen xor (delGen ushr 32)).toInt()
            lazyHash = h
        }
        return lazyHash
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) {
            return true
        }

        if (obj == null || this::class != obj::class) {
            return false
        }

        val other = obj as PrefixCodedTerms
        return delGen == other.delGen && size() == other.size() && this.content == other.content
    }
}
