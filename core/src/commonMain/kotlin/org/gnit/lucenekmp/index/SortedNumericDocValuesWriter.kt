package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.codecs.DocValuesConsumer
import org.gnit.lucenekmp.codecs.DocValuesProducer
import org.gnit.lucenekmp.index.NumericDocValuesWriter.BufferedNumericDocValues
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.DocIdSetIterator.Companion.NO_MORE_DOCS
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.Counter
import org.gnit.lucenekmp.util.RamUsageEstimator
import org.gnit.lucenekmp.util.packed.PackedInts
import org.gnit.lucenekmp.util.packed.PackedLongValues


/** Buffers up pending long[] per doc, sorts, then flushes when segment flushes.  */
internal class SortedNumericDocValuesWriter(
    private val fieldInfo: FieldInfo,
    private val iwBytesUsed: Counter
) : DocValuesWriter<SortedNumericDocValues>() {
    private val pending: PackedLongValues.Builder =
        PackedLongValues.deltaPackedBuilder(PackedInts.COMPACT) // stream of all values
    private var pendingCounts: PackedLongValues.Builder? = null // count of values per doc
    private val docsWithField: DocsWithFieldSet = DocsWithFieldSet()
    private var bytesUsed: Long // this only tracks differences in 'pending' and 'pendingCounts'
    private var currentDoc = -1
    private var currentValues = LongArray(8)
    private var currentUpto = 0

    private var finalValues: PackedLongValues? = null
    private var finalValuesCount: PackedLongValues? = null

    init {
        bytesUsed =
            (pending.ramBytesUsed()
                    + docsWithField.ramBytesUsed()
                    + RamUsageEstimator.sizeOf(currentValues))
        iwBytesUsed.addAndGet(bytesUsed)
    }

    fun addValue(docID: Int, value: Long) {
        assert(docID >= currentDoc)
        if (docID != currentDoc) {
            finishCurrentDoc()
            currentDoc = docID
        }

        addOneValue(value)
        updateBytesUsed()
    }

    // finalize currentDoc: this sorts the values in the current doc
    private fun finishCurrentDoc() {
        if (currentDoc == -1) {
            return
        }
        if (currentUpto > 1) {
            Arrays.sort(currentValues, 0, currentUpto)
        }
        for (i in 0..<currentUpto) {
            pending.add(currentValues[i])
        }
        // record the number of values for this doc
        if (pendingCounts != null) {
            pendingCounts!!.add(currentUpto.toLong())
        } else if (currentUpto != 1) {
            pendingCounts =
                PackedLongValues.deltaPackedBuilder(PackedInts.COMPACT)
            for (i in 0..<docsWithField.cardinality()) {
                pendingCounts!!.add(1)
            }
            pendingCounts!!.add(currentUpto.toLong())
        }
        currentUpto = 0

        docsWithField.add(currentDoc)
    }

    private fun addOneValue(value: Long) {
        if (currentUpto == currentValues.size) {
            currentValues = ArrayUtil.grow(currentValues, currentValues.size + 1)
        }

        currentValues[currentUpto] = value
        currentUpto++
    }

    private fun updateBytesUsed() {
        val newBytesUsed: Long =
            (pending.ramBytesUsed()
                    + (if (pendingCounts == null) 0 else pendingCounts!!.ramBytesUsed())
                    + docsWithField.ramBytesUsed()
                    + RamUsageEstimator.sizeOf(currentValues))
        iwBytesUsed.addAndGet(newBytesUsed - bytesUsed)
        bytesUsed = newBytesUsed
    }

    override val docValues: SortedNumericDocValues
        get() {
            if (finalValues == null) {
                assert(finalValuesCount == null)
                finishCurrentDoc()
                finalValues = pending.build()
                finalValuesCount = if (pendingCounts == null) null else pendingCounts!!.build()
            }
            return getValues(finalValues!!, finalValuesCount!!, docsWithField)
        }

    internal class LongValues(
        maxDoc: Int,
        sortMap: Sorter.DocMap,
        oldValues: SortedNumericDocValues,
        acceptableOverheadRatio: Float
    ) {
        val offsets: LongArray = LongArray(maxDoc)
        val values: PackedLongValues

        init {
            val valuesBuiler: PackedLongValues.Builder =
                PackedLongValues.packedBuilder(acceptableOverheadRatio)
            var docID: Int
            var offsetIndex: Long = 1 // 0 means the doc has no values
            while ((oldValues.nextDoc()
                    .also { docID = it }) != NO_MORE_DOCS
            ) {
                val newDocID: Int = sortMap.oldToNew(docID)
                val numValues: Int = oldValues.docValueCount()
                valuesBuiler.add(numValues.toLong())
                offsets[newDocID] = offsetIndex++
                for (i in 0..<numValues) {
                    valuesBuiler.add(oldValues.nextValue())
                    offsetIndex++
                }
            }
            values = valuesBuiler.build()
        }
    }

    private fun getValues(
        values: PackedLongValues,
        valueCounts: PackedLongValues,
        docsWithField: DocsWithFieldSet
    ): SortedNumericDocValues {
        return if (valueCounts == null) {
            DocValues.singleton(
                BufferedNumericDocValues(
                    values,
                    docsWithField.iterator()
                )
            )
        } else {
            BufferedSortedNumericDocValues(values, valueCounts, docsWithField.iterator())
        }
    }

    @Throws(IOException::class)
    override fun flush(
        state: SegmentWriteState,
        sortMap: Sorter.DocMap?,
        dvConsumer: DocValuesConsumer
    ) {
        val values: PackedLongValues?
        val valueCounts: PackedLongValues?
        if (finalValues == null) {
            finishCurrentDoc()
            values = pending.build()
            valueCounts = if (pendingCounts == null) null else pendingCounts!!.build()
        } else {
            values = finalValues
            valueCounts = finalValuesCount
        }

        if (valueCounts == null) {
            val singleValueProducer: DocValuesProducer =
                NumericDocValuesWriter.getDocValuesProducer(
                    fieldInfo,
                    values!!,
                    docsWithField,
                    sortMap
                )
            dvConsumer.addSortedNumericField(
                fieldInfo,
                object : EmptyDocValuesProducer() {
                    @Throws(IOException::class)
                    override fun getSortedNumeric(fieldInfo: FieldInfo): SortedNumericDocValues {
                        return DocValues.singleton(singleValueProducer.getNumeric(fieldInfo))
                    }
                })
            return
        }
        val sorted = if (sortMap != null) {
            LongValues(
                state.segmentInfo.maxDoc(),
                sortMap,
                getValues(values!!, valueCounts, docsWithField),
                PackedInts.FASTEST
            )
        } else {
            null
        }

        dvConsumer.addSortedNumericField(
            fieldInfo,
            object : EmptyDocValuesProducer() {
                override fun getSortedNumeric(fieldInfoIn: FieldInfo): SortedNumericDocValues {
                    require(fieldInfoIn == fieldInfo) { "wrong fieldInfo" }
                    val buf: SortedNumericDocValues =
                        getValues(values!!, valueCounts, docsWithField)
                    return if (sorted == null) {
                        buf
                    } else {
                        SortingSortedNumericDocValues(buf, sorted)
                    }
                }
            })
    }

    private class BufferedSortedNumericDocValues(
        values: PackedLongValues,
        valueCounts: PackedLongValues,
        val docsWithField: DocIdSetIterator
    ) : SortedNumericDocValues() {
        val valuesIter: PackedLongValues.Iterator = values.iterator()
        val valueCountsIter: PackedLongValues.Iterator = valueCounts.iterator()
        private var valueCount = 0
        private var valueUpto = 0

        override fun docID(): Int {
            return docsWithField.docID()
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            for (i in valueUpto..<valueCount) {
                valuesIter.next()
            }

            val docID: Int = docsWithField.nextDoc()
            if (docID != NO_MORE_DOCS) {
                valueCount = Math.toIntExact(valueCountsIter.next())
                valueUpto = 0
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

        override fun docValueCount(): Int {
            return valueCount
        }

        override fun nextValue(): Long {
            valueUpto++
            return valuesIter.next()
        }

        override fun cost(): Long {
            return docsWithField.cost()
        }
    }

    internal class SortingSortedNumericDocValues(
        private val `in`: SortedNumericDocValues,
        private val values: LongValues
    ) : SortedNumericDocValues() {
        private var docID = -1
        private var upto: Long = 0
        private var numValues = -1

        override fun docID(): Int {
            return docID
        }

        override fun nextDoc(): Int {
            do {
                docID++
                if (docID >= values.offsets.size) {
                    return NO_MORE_DOCS.also { docID = it }
                }
            } while (values.offsets[docID] <= 0)
            upto = values.offsets[docID]
            numValues = Math.toIntExact(values.values.get(upto - 1))
            return docID
        }

        override fun advance(target: Int): Int {
            throw UnsupportedOperationException("use nextDoc instead")
        }

        @Throws(IOException::class)
        override fun advanceExact(target: Int): Boolean {
            docID = target
            upto = values.offsets[docID]
            if (values.offsets[docID] > 0) {
                numValues = Math.toIntExact(values.values.get(upto - 1))
                return true
            }
            return false
        }

        override fun nextValue(): Long {
            return values.values.get(upto++)
        }

        override fun cost(): Long {
            return `in`.cost()
        }

        override fun docValueCount(): Int {
            return numValues
        }
    }
}
