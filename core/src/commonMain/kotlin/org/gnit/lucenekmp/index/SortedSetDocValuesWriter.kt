package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.codecs.DocValuesConsumer
import org.gnit.lucenekmp.codecs.DocValuesProducer
import org.gnit.lucenekmp.index.SortedDocValuesWriter.BufferedSortedDocValues
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.DocIdSetIterator.Companion.NO_MORE_DOCS
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.ByteBlockPool
import org.gnit.lucenekmp.util.ByteBlockPool.Companion.BYTE_BLOCK_SIZE
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefHash
import org.gnit.lucenekmp.util.BytesRefHash.DirectBytesStartArray
import org.gnit.lucenekmp.util.Counter
import org.gnit.lucenekmp.util.RamUsageEstimator
import org.gnit.lucenekmp.util.packed.GrowableWriter
import org.gnit.lucenekmp.util.packed.PackedInts
import org.gnit.lucenekmp.util.packed.PackedLongValues
import kotlin.math.max


/**
 * Buffers up pending byte[]s per doc, deref and sorting via int ord, then flushes when segment
 * flushes.
 */
internal class SortedSetDocValuesWriter(
    private val fieldInfo: FieldInfo,
    private val iwBytesUsed: Counter,
    pool: ByteBlockPool
) : DocValuesWriter<SortedSetDocValues>() {
    val hash: BytesRefHash = BytesRefHash(
        pool,
        BytesRefHash.DEFAULT_CAPACITY,
        DirectBytesStartArray(
            BytesRefHash.DEFAULT_CAPACITY,
            iwBytesUsed
        )
    )
    private val pending: PackedLongValues.Builder =
        PackedLongValues.packedBuilder(PackedInts.COMPACT) // stream of all termIDs
    private var pendingCounts: PackedLongValues.Builder? = null // termIDs per doc
    private val docsWithField: DocsWithFieldSet = DocsWithFieldSet()
    private var bytesUsed: Long // this only tracks differences in 'pending' and 'pendingCounts'
    private var currentDoc = -1
    private var currentValues = IntArray(8)
    private var currentUpto = 0
    private var maxCount = 0

    private var finalOrds: PackedLongValues? = null
    private var finalOrdCounts: PackedLongValues? = null
    private lateinit var finalSortedValues: IntArray
    private lateinit var finalOrdMap: IntArray

    init {
        bytesUsed =
            (pending.ramBytesUsed()
                    + docsWithField.ramBytesUsed()
                    + RamUsageEstimator.sizeOf(currentValues))
        iwBytesUsed.addAndGet(bytesUsed)
    }

    fun addValue(docID: Int, value: BytesRef) {
        assert(docID >= currentDoc)
        requireNotNull(value) { "field \"" + fieldInfo.name + "\": null value not allowed" }
        require(value.length <= (BYTE_BLOCK_SIZE - 2)) {
            ("DocValuesField \""
                    + fieldInfo.name
                    + "\" is too large, must be <= "
                    + (BYTE_BLOCK_SIZE - 2))
        }

        if (docID != currentDoc) {
            finishCurrentDoc()
            currentDoc = docID
        }

        addOneValue(value)
        updateBytesUsed()
    }

    // finalize currentDoc: this deduplicates the current term ids
    private fun finishCurrentDoc() {
        if (currentDoc == -1) {
            return
        }
        if (currentUpto > 1) {
            Arrays.sort(currentValues, 0, currentUpto)
        }
        var lastValue = -1
        var count = 0
        for (i in 0..<currentUpto) {
            val termID = currentValues[i]
            // if it's not a duplicate
            if (termID != lastValue) {
                pending.add(termID.toLong()) // record the term id
                count++
            }
            lastValue = termID
        }
        // record the number of unique term ids for this doc
        if (pendingCounts != null) {
            pendingCounts!!.add(count.toLong())
        } else if (count != 1) {
            pendingCounts =
                PackedLongValues.deltaPackedBuilder(PackedInts.COMPACT)
            for (i in 0..<docsWithField.cardinality()) {
                pendingCounts!!.add(1)
            }
            pendingCounts!!.add(count.toLong())
        }
        maxCount = max(maxCount, count)
        currentUpto = 0
        docsWithField.add(currentDoc)
    }

    private fun addOneValue(value: BytesRef) {
        var termID: Int = hash.add(value)
        if (termID < 0) {
            termID = -termID - 1
        } else {
            // reserve additional space for each unique value:
            // 1. when indexing, when hash is 50% full, rehash() suddenly needs 2*size ints.
            //    TODO: can this same OOM happen in THPF
            // 2. when flushing, we need 1 int per value (slot in the ordMap).
            iwBytesUsed.addAndGet((2 * Int.SIZE_BYTES).toLong())
        }

        if (currentUpto == currentValues.size) {
            currentValues = ArrayUtil.grow(currentValues, currentValues.size + 1)
            iwBytesUsed.addAndGet((currentValues.size - currentUpto) * Int.SIZE_BYTES.toLong())
        }

        currentValues[currentUpto] = termID
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

    private fun finish() {
        if (finalOrds == null) {
            assert(finalOrdCounts == null && finalSortedValues == null && finalOrdMap == null)
            finishCurrentDoc()
            val valueCount: Int = hash.size()
            finalOrds = pending.build()
            finalOrdCounts = if (pendingCounts == null) null else pendingCounts!!.build()
            finalSortedValues = hash.sort()
            finalOrdMap = IntArray(valueCount)
            for (ord in finalOrdMap.indices) {
                finalOrdMap[finalSortedValues[ord]] = ord
            }
        }
    }

    override val docValues: SortedSetDocValues
        get() {
            finish()
            return getValues(
                finalSortedValues, finalOrdMap, hash, finalOrds!!, finalOrdCounts!!, maxCount, docsWithField
            )
        }

    private fun getValues(
        sortedValues: IntArray,
        ordMap: IntArray,
        hash: BytesRefHash,
        ords: PackedLongValues,
        ordCounts: PackedLongValues,
        maxCount: Int,
        docsWithField: DocsWithFieldSet
    ): SortedSetDocValues {
        return if (ordCounts == null) {
            DocValues.singleton(
                BufferedSortedDocValues(
                    hash,
                    ords,
                    sortedValues,
                    ordMap,
                    docsWithField.iterator()
                )
            )
        } else {
            BufferedSortedSetDocValues(
                sortedValues, ordMap, hash, ords, ordCounts, maxCount, docsWithField.iterator()
            )
        }
    }

    @Throws(IOException::class)
    override fun flush(
        state: SegmentWriteState,
        sortMap: Sorter.DocMap?,
        dvConsumer: DocValuesConsumer
    ) {
        finish()
        val ords: PackedLongValues? = finalOrds
        val ordCounts: PackedLongValues? = finalOrdCounts
        val sortedValues = finalSortedValues
        val ordMap = finalOrdMap

        if (ordCounts == null) {
            val singleValueProducer: DocValuesProducer =
                SortedDocValuesWriter.getDocValuesProducer(
                    fieldInfo, hash, ords!!, sortedValues, ordMap, docsWithField, sortMap
                )
            dvConsumer.addSortedSetField(
                fieldInfo,
                object : EmptyDocValuesProducer() {
                    @Throws(IOException::class)
                    override fun getSortedSet(fieldInfo: FieldInfo): SortedSetDocValues {
                        return DocValues.singleton(singleValueProducer.getSorted(fieldInfo)!!)
                    }
                })
            return
        }
        val docOrds = if (sortMap != null) {
            DocOrds(
                state.segmentInfo.maxDoc(),
                sortMap,
                getValues(sortedValues, ordMap, hash, ords!!, ordCounts, maxCount, docsWithField),
                PackedInts.FASTEST,
                PackedInts.bitsRequired(maxCount.toLong())
            )
        } else {
            null
        }
        dvConsumer.addSortedSetField(
            fieldInfo,
            object : EmptyDocValuesProducer() {
                override fun getSortedSet(fieldInfoIn: FieldInfo): SortedSetDocValues {
                    require(fieldInfoIn == fieldInfo) { "wrong fieldInfo" }
                    val buf: SortedSetDocValues =
                        getValues(sortedValues, ordMap, hash, ords!!, ordCounts, maxCount, docsWithField)
                    return if (docOrds == null) {
                        buf
                    } else {
                        SortingSortedSetDocValues(buf, docOrds)
                    }
                }
            })
    }

    private class BufferedSortedSetDocValues(
        val sortedValues: IntArray,
        val ordMap: IntArray,
        val hash: BytesRefHash,
        ords: PackedLongValues,
        ordCounts: PackedLongValues,
        maxCount: Int,
        val docsWithField: DocIdSetIterator
    ) : SortedSetDocValues() {
        val scratch: BytesRef = BytesRef()
        val ordsIter: PackedLongValues.Iterator = ords.iterator()
        val ordCountsIter: PackedLongValues.Iterator = ordCounts.iterator()
        val currentDoc: IntArray = IntArray(maxCount)

        private var ordCount = 0
        private var ordUpto = 0

        override fun docID(): Int {
            return docsWithField.docID()
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            val docID: Int = docsWithField.nextDoc()
            if (docID != NO_MORE_DOCS) {
                ordCount = ordCountsIter.next().toInt()
                assert(ordCount > 0)
                for (i in 0..<ordCount) {
                    currentDoc[i] = ordMap[Math.toIntExact(ordsIter.next())]
                }
                Arrays.sort(currentDoc, 0, ordCount)
                ordUpto = 0
            }
            return docID
        }

        override fun nextOrd(): Long {
            return currentDoc[ordUpto++].toLong()
        }

        override fun docValueCount(): Int {
            return ordCount
        }

        override fun cost(): Long {
            return docsWithField.cost()
        }

        override fun advance(target: Int): Int {
            throw UnsupportedOperationException()
        }

        @Throws(IOException::class)
        override fun advanceExact(target: Int): Boolean {
            throw UnsupportedOperationException()
        }

        override val valueCount: Long
            get() = ordMap.size.toLong()

        override fun lookupOrd(ord: Long): BytesRef {
            assert(
                ord >= 0 && ord < ordMap.size
            ) { "ord=" + ord + " is out of bounds 0 .. " + (ordMap.size - 1) }
            hash.get(sortedValues[Math.toIntExact(ord)], scratch)
            return scratch
        }
    }

    internal class SortingSortedSetDocValues(private val `in`: SortedSetDocValues, private val ords: DocOrds) :
        SortedSetDocValues() {
        private var docID = -1
        private var ordUpto: Long = 0
        private var count = 0

        override fun docID(): Int {
            return docID
        }

        override fun nextDoc(): Int {
            do {
                docID++
                if (docID == ords.offsets.size) {
                    return NO_MORE_DOCS.also { docID = it }
                }
            } while (ords.offsets[docID] <= 0)
            initCount()
            return docID
        }

        override fun advance(target: Int): Int {
            throw UnsupportedOperationException("use nextDoc instead")
        }

        @Throws(IOException::class)
        override fun advanceExact(target: Int): Boolean {
            // needed in IndexSorter#StringSorter
            docID = target
            initCount()
            return ords.offsets[docID] > 0
        }

        override fun nextOrd(): Long {
            return ords.ords.get(ordUpto++)
        }

        override fun docValueCount(): Int {
            assert(docID >= 0)
            return count
        }

        override fun cost(): Long {
            return `in`.cost()
        }

        @Throws(IOException::class)
        override fun lookupOrd(ord: Long): BytesRef? {
            return `in`.lookupOrd(ord)
        }

        override val valueCount: Long
            get() = `in`.valueCount

        private fun initCount() {
            assert(docID >= 0)
            ordUpto = ords.offsets[docID] - 1
            count = ords.docValueCounts.get(docID).toInt()
        }
    }

    internal class DocOrds(
        maxDoc: Int,
        sortMap: Sorter.DocMap,
        oldValues: SortedSetDocValues,
        acceptableOverheadRatio: Float,
        bitsPerValue: Int
    ) {
        val offsets: LongArray = LongArray(maxDoc)
        val ords: PackedLongValues
        val docValueCounts: GrowableWriter

        init {
            val builder: PackedLongValues.Builder =
                PackedLongValues.packedBuilder(acceptableOverheadRatio)
            docValueCounts = GrowableWriter(bitsPerValue, maxDoc, acceptableOverheadRatio)
            var ordOffset: Long = 1
            var docID: Int
            while ((oldValues.nextDoc()
                    .also { docID = it }) != NO_MORE_DOCS
            ) {
                val newDocID: Int = sortMap.oldToNew(docID)
                val startOffset = ordOffset
                val docValueCount: Int = oldValues.docValueCount()
                ordOffset += docValueCount.toLong()
                for (i in 0..<docValueCount) {
                    builder.add(oldValues.nextOrd())
                }
                docValueCounts.set(newDocID, ordOffset - startOffset)
                if (startOffset != ordOffset) { // do we have any values?
                    offsets[newDocID] = startOffset
                }
            }
            ords = builder.build()
        }

        companion object {
            const val START_BITS_PER_VALUE: Int = 2
        }
    }
}
