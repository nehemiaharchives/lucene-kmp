package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.codecs.NormsConsumer
import org.gnit.lucenekmp.codecs.NormsProducer
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.util.Counter
import org.gnit.lucenekmp.util.packed.PackedInts
import org.gnit.lucenekmp.util.packed.PackedLongValues


/** Buffers up pending long per doc, then flushes when segment flushes.  */
internal class NormValuesWriter(
    fieldInfo: FieldInfo,
    iwBytesUsed: Counter
) {
    private val docsWithField: DocsWithFieldSet = DocsWithFieldSet()
    private val pending: PackedLongValues.Builder = PackedLongValues.deltaPackedBuilder(PackedInts.COMPACT)
    private val iwBytesUsed: Counter
    private var bytesUsed: Long
    private val fieldInfo: FieldInfo
    private var lastDocID = -1

    init {
        bytesUsed = pending.ramBytesUsed() + docsWithField.ramBytesUsed()
        this.fieldInfo = fieldInfo
        this.iwBytesUsed = iwBytesUsed
        iwBytesUsed.addAndGet(bytesUsed)
    }

    fun addValue(docID: Int, value: Long) {
        require(docID > lastDocID) {
            ("Norm for \""
                    + fieldInfo.name
                    + "\" appears more than once in this document (only one value is allowed per field)")
        }

        pending.add(value)
        docsWithField.add(docID)

        updateBytesUsed()

        lastDocID = docID
    }

    private fun updateBytesUsed() {
        val newBytesUsed: Long = pending.ramBytesUsed() + docsWithField.ramBytesUsed()
        iwBytesUsed.addAndGet(newBytesUsed - bytesUsed)
        bytesUsed = newBytesUsed
    }

    fun finish(maxDoc: Int) {}

    @Throws(IOException::class)
    fun flush(
        state: SegmentWriteState,
        sortMap: Sorter.DocMap?,
        normsConsumer: NormsConsumer
    ) {
        val values: PackedLongValues = pending.build()
        val sorted = if (sortMap != null) {
            NumericDocValuesWriter.sortDocValues(
                state.segmentInfo.maxDoc(),
                sortMap,
                BufferedNorms(values, docsWithField.iterator()),
                sortMap.size() == docsWithField.cardinality()
            )
        } else {
            null
        }
        normsConsumer.addNormsField(
            fieldInfo,
            object : NormsProducer() {
                override fun getNorms(fieldInfo2: FieldInfo): NumericDocValues {
                    require(fieldInfo == this@NormValuesWriter.fieldInfo) { "wrong fieldInfo" }
                    return if (sorted == null) {
                        BufferedNorms(values, docsWithField.iterator())
                    } else {
                        NumericDocValuesWriter.SortingNumericDocValues(sorted)
                    }
                }

                override fun checkIntegrity() {}

                override fun close() {}
            })
    }

    // TODO: norms should only visit docs that had a field indexed!!
    // iterates over the values we have in ram
    private class BufferedNorms(
        values: PackedLongValues,
        docsWithFields: DocIdSetIterator
    ) : NumericDocValues() {
        val iter: PackedLongValues.Iterator = values.iterator()
        val docsWithField: DocIdSetIterator = docsWithFields
        private var value: Long = 0

        override fun docID(): Int {
            return docsWithField.docID()
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            val docID: Int = docsWithField.nextDoc()
            if (docID != NO_MORE_DOCS) {
                value = iter.next()
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

        override fun longValue(): Long {
            return value
        }
    }
}
