package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.codecs.DocValuesConsumer
import org.gnit.lucenekmp.codecs.DocValuesProducer
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.DocIdSetIterator.Companion.NO_MORE_DOCS
import org.gnit.lucenekmp.util.BitSet
import org.gnit.lucenekmp.util.Counter
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.packed.PackedInts
import org.gnit.lucenekmp.util.packed.PackedLongValues


/** Buffers up pending long per doc, then flushes when segment flushes.  */
internal class NumericDocValuesWriter(
    fieldInfo: FieldInfo,
    iwBytesUsed: Counter
) : DocValuesWriter<NumericDocValues>() {
    private val pending: PackedLongValues.Builder = PackedLongValues.deltaPackedBuilder(PackedInts.COMPACT)
    private var finalValues: PackedLongValues? = null
    private val iwBytesUsed: Counter
    private var bytesUsed: Long
    private val docsWithField: DocsWithFieldSet
    private val fieldInfo: FieldInfo
    private var lastDocID = -1

    init {
        docsWithField = DocsWithFieldSet()
        bytesUsed = pending.ramBytesUsed() + docsWithField.ramBytesUsed()
        this.fieldInfo = fieldInfo
        this.iwBytesUsed = iwBytesUsed
        iwBytesUsed.addAndGet(bytesUsed)
    }

    fun addValue(docID: Int, value: Long) {
        require(docID > lastDocID) {
            ("DocValuesField \""
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

    override val docValues: NumericDocValues
        get() {
            if (finalValues == null) {
                finalValues = pending.build()
            }
            return BufferedNumericDocValues(finalValues!!, docsWithField.iterator())
        }

    @Throws(IOException::class)
    override fun flush(
        state: SegmentWriteState,
        sortMap: Sorter.DocMap?,
        dvConsumer: DocValuesConsumer
    ) {
        if (finalValues == null) {
            finalValues = pending.build()
        }

        dvConsumer.addNumericField(
            fieldInfo, getDocValuesProducer(fieldInfo, finalValues!!, docsWithField, sortMap)
        )
    }

    // iterates over the values we have in ram
    internal class BufferedNumericDocValues(
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

    internal class SortingNumericDocValues(private val dvs: NumericDVs) : NumericDocValues() {
        private var docID = -1
        private var cost: Long = -1

        override fun docID(): Int {
            return docID
        }

        override fun nextDoc(): Int {
            docID = if (docID + 1 == dvs.maxDoc()) {
                NO_MORE_DOCS
            } else {
                dvs.advance(docID + 1)
            }
            return docID
        }

        override fun advance(target: Int): Int {
            throw UnsupportedOperationException("use nextDoc() instead")
        }

        @Throws(IOException::class)
        override fun advanceExact(target: Int): Boolean {
            // needed in IndexSorter#{Long|Int|Double|Float}Sorter
            docID = target
            return dvs.advanceExact(target)
        }

        override fun longValue(): Long {
            return dvs.values[docID]
        }

        override fun cost(): Long {
            if (cost == -1L) {
                cost = dvs.cost()
            }
            return cost
        }
    }

    internal class NumericDVs(val values: LongArray, private val docsWithField: BitSet?) {
        private val maxDoc: Int = values.size

        fun maxDoc(): Int {
            return maxDoc
        }

        fun advanceExact(target: Int): Boolean {
            if (docsWithField != null) {
                return docsWithField.get(target)
            }
            return true
        }

        fun advance(target: Int): Int {
            if (docsWithField != null) {
                return docsWithField.nextSetBit(target)
            }

            // Only called when target is less than maxDoc
            return target
        }

        fun cost(): Long {
            if (docsWithField != null) {
                return docsWithField.cardinality().toLong()
            }
            return maxDoc.toLong()
        }
    }

    companion object {
        @Throws(IOException::class)
        fun sortDocValues(
            maxDoc: Int,
            sortMap: Sorter.DocMap,
            oldDocValues: NumericDocValues,
            dense: Boolean
        ): NumericDVs {
            var docsWithField: FixedBitSet? = null
            if (!dense) {
                docsWithField = FixedBitSet(maxDoc)
            }

            val values = LongArray(maxDoc)
            while (true) {
                val docID: Int = oldDocValues.nextDoc()
                if (docID == NO_MORE_DOCS) {
                    break
                }
                val newDocID: Int = sortMap.oldToNew(docID)
                docsWithField?.set(newDocID)
                values[newDocID] = oldDocValues.longValue()
            }
            return NumericDVs(values, docsWithField)
        }

        @Throws(IOException::class)
        fun getDocValuesProducer(
            writerFieldInfo: FieldInfo,
            values: PackedLongValues,
            docsWithField: DocsWithFieldSet,
            sortMap: Sorter.DocMap?
        ): DocValuesProducer {
            val sorted: NumericDVs?
            if (sortMap != null) {
                val oldValues: NumericDocValues =
                    BufferedNumericDocValues(values, docsWithField.iterator())
                sorted =
                    sortDocValues(
                        sortMap.size(), sortMap, oldValues, sortMap.size() == docsWithField.cardinality()
                    )
            } else {
                sorted = null
            }

            return object : EmptyDocValuesProducer() {
                override fun getNumeric(fieldInfo: FieldInfo): NumericDocValues {
                    require(fieldInfo == writerFieldInfo) { "wrong fieldInfo" }
                    return if (sorted == null) {
                        BufferedNumericDocValues(values, docsWithField.iterator())
                    } else {
                        SortingNumericDocValues(sorted)
                    }
                }
            }
        }
    }
}
