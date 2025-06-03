package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.codecs.DocValuesConsumer
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefArray
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.Counter
import org.gnit.lucenekmp.util.PagedBytes
import org.gnit.lucenekmp.util.packed.PackedInts
import org.gnit.lucenekmp.util.packed.PackedLongValues
import okio.IOException
import org.gnit.lucenekmp.jdkport.Math
import kotlin.math.max


/** Buffers up pending byte[] per doc, then flushes when segment flushes.  */
internal class BinaryDocValuesWriter(
    private val fieldInfo: FieldInfo,
    private val iwBytesUsed: Counter
) : DocValuesWriter<BinaryDocValues>() {
    private val bytes: PagedBytes = PagedBytes(BLOCK_BITS)
    private val bytesOut: DataOutput = bytes.dataOutput

    private val lengths: PackedLongValues.Builder = PackedLongValues.deltaPackedBuilder(PackedInts.COMPACT)
    private val docsWithField: DocsWithFieldSet
    private var bytesUsed: Long
    private var lastDocID = -1
    private var maxLength = 0

    private var finalLengths: PackedLongValues? = null

    init {
        this.docsWithField = DocsWithFieldSet()
        this.bytesUsed = lengths.ramBytesUsed() + docsWithField.ramBytesUsed()
        iwBytesUsed.addAndGet(bytesUsed)
    }

    fun addValue(docID: Int, value: BytesRef) {
        require(docID > lastDocID) {
            ("DocValuesField \""
                    + fieldInfo.name
                    + "\" appears more than once in this document (only one value is allowed per field)")
        }
        requireNotNull(value) { "field=\"" + fieldInfo.name + "\": null value not allowed" }
        require(value.length <= MAX_LENGTH) { "DocValuesField \"" + fieldInfo.name + "\" is too large, must be <= " + MAX_LENGTH }

        maxLength = max(value.length, maxLength)
        lengths.add(value.length.toLong())
        try {
            bytesOut.writeBytes(value.bytes, value.offset, value.length)
        } catch (ioe: IOException) {
            // Should never happen!
            throw RuntimeException(ioe)
        }
        docsWithField.add(docID)
        updateBytesUsed()

        lastDocID = docID
    }

    private fun updateBytesUsed() {
        val newBytesUsed: Long =
            lengths.ramBytesUsed() + bytes.ramBytesUsed() + docsWithField.ramBytesUsed()
        iwBytesUsed.addAndGet(newBytesUsed - bytesUsed)
        bytesUsed = newBytesUsed
    }

    override val docValues: BinaryDocValues
        get() {
            if (finalLengths == null) {
                finalLengths = this.lengths.build()
            }
            return BufferedBinaryDocValues(
                finalLengths!!, maxLength, bytes.dataInput, docsWithField.iterator()
            )
        }

    @Throws(IOException::class)
    override fun flush(
        state: SegmentWriteState,
        sortMap: Sorter.DocMap,
        dvConsumer: DocValuesConsumer
    ) {
        bytes.freeze(false)
        if (finalLengths == null) {
            finalLengths = this.lengths.build()
        }
        val sorted = if (sortMap != null) {
            BinaryDVs(
                state.segmentInfo.maxDoc(),
                sortMap,
                BufferedBinaryDocValues(
                    finalLengths!!, maxLength, bytes.dataInput, docsWithField.iterator()
                )
            )
        } else {
            null
        }
        dvConsumer.addBinaryField(
            fieldInfo,
            object : EmptyDocValuesProducer() {
                override fun getBinary(fieldInfoIn: FieldInfo): BinaryDocValues {
                    require(fieldInfoIn == fieldInfo) { "wrong fieldInfo" }
                    return if (sorted == null) {
                        BufferedBinaryDocValues(
                            finalLengths!!, maxLength, bytes.dataInput, docsWithField.iterator()
                        )
                    } else {
                        SortingBinaryDocValues(sorted)
                    }
                }
            })
    }

    // iterates over the values we have in ram
    private class BufferedBinaryDocValues(
        lengths: PackedLongValues,
        maxLength: Int,
        bytesIterator: DataInput,
        docsWithFields: DocIdSetIterator
    ) : BinaryDocValues() {
        val value: BytesRefBuilder = BytesRefBuilder()
        val lengthsIterator: PackedLongValues.Iterator
        val docsWithField: DocIdSetIterator
        val bytesIterator: DataInput

        init {
            this.value.grow(maxLength)
            this.lengthsIterator = lengths.iterator()
            this.bytesIterator = bytesIterator
            this.docsWithField = docsWithFields
        }

        override fun docID(): Int {
            return docsWithField.docID()
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            val docID: Int = docsWithField.nextDoc()
            if (docID != NO_MORE_DOCS) {
                val length: Int = Math.toIntExact(lengthsIterator.next())
                value.setLength(length)
                bytesIterator.readBytes(value.bytes(), 0, length)
            }
            return docID
        }

        override fun advance(target: Int): Int {
            throw UnsupportedOperationException()
        }

        @Throws(IOException::class)
        override fun advanceExact(target: Int): Boolean {
            throw UnsupportedOperationException()
        }

        override fun cost(): Long {
            return docsWithField.cost()
        }

        override fun binaryValue(): BytesRef {
            return value.get()
        }
    }

    internal class SortingBinaryDocValues(private val dvs: BinaryDVs) : BinaryDocValues() {
        private val spare: BytesRefBuilder = BytesRefBuilder()
        private var docID = -1

        override fun nextDoc(): Int {
            do {
                docID++
                if (docID == dvs.offsets.size) {
                    return NO_MORE_DOCS.also { docID = it }
                }
            } while (dvs.offsets[docID] <= 0)
            return docID
        }

        override fun docID(): Int {
            return docID
        }

        override fun advance(target: Int): Int {
            throw UnsupportedOperationException("use nextDoc instead")
        }

        @Throws(IOException::class)
        override fun advanceExact(target: Int): Boolean {
            throw UnsupportedOperationException("use nextDoc instead")
        }

        override fun binaryValue(): BytesRef {
            dvs.values.get(spare, dvs.offsets[docID] - 1)
            return spare.get()
        }

        override fun cost(): Long {
            return dvs.values.size().toLong()
        }
    }

    internal class BinaryDVs(
        maxDoc: Int,
        sortMap: Sorter.DocMap,
        oldValues: BinaryDocValues
    ) {
        val offsets: IntArray = IntArray(maxDoc)
        val values: BytesRefArray = BytesRefArray(Counter.newCounter())

        init {
            var offset = 1 // 0 means no values for this document
            var docID: Int
            while ((oldValues.nextDoc()
                    .also { docID = it }) != DocIdSetIterator.NO_MORE_DOCS
            ) {
                val newDocID: Int = sortMap.oldToNew(docID)
                values.append(oldValues.binaryValue()!!)
                offsets[newDocID] = offset++
            }
        }
    }

    companion object {
        /** Maximum length for a binary field.  */
        private val MAX_LENGTH: Int = ArrayUtil.MAX_ARRAY_LENGTH

        // 4 kB block sizes for PagedBytes storage:
        private const val BLOCK_BITS = 12
    }
}
