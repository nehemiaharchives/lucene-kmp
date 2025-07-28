package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.codecs.DocValuesConsumer
import org.gnit.lucenekmp.codecs.DocValuesProducer
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.DocIdSetIterator.Companion.NO_MORE_DOCS
import org.gnit.lucenekmp.util.ByteBlockPool
import org.gnit.lucenekmp.util.ByteBlockPool.Companion.BYTE_BLOCK_SIZE
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefHash
import org.gnit.lucenekmp.util.BytesRefHash.DirectBytesStartArray
import org.gnit.lucenekmp.util.Counter
import org.gnit.lucenekmp.util.packed.PackedInts
import org.gnit.lucenekmp.util.packed.PackedLongValues


/**
 * Buffers up pending byte[] per doc, deref and sorting via int ord, then flushes when segment
 * flushes.
 */
internal class SortedDocValuesWriter(
    private val fieldInfo: FieldInfo,
    private val iwBytesUsed: Counter,
    pool: ByteBlockPool
) : DocValuesWriter<SortedDocValues>() {
    val hash: BytesRefHash = BytesRefHash(
        pool,
        BytesRefHash.DEFAULT_CAPACITY,
        DirectBytesStartArray(
            BytesRefHash.DEFAULT_CAPACITY,
            iwBytesUsed
        )
    )
    private val pending: PackedLongValues.Builder = PackedLongValues.deltaPackedBuilder(PackedInts.COMPACT)
    private val docsWithField: DocsWithFieldSet = DocsWithFieldSet()
    private var bytesUsed: Long // this currently only tracks differences in 'pending'
    private var lastDocID = -1

    private var finalOrds: PackedLongValues? = null
    private lateinit var finalSortedValues: IntArray
    private lateinit var finalOrdMap: IntArray

    init {
        bytesUsed = pending.ramBytesUsed() + docsWithField.ramBytesUsed()
        iwBytesUsed.addAndGet(bytesUsed)
    }

    fun addValue(docID: Int, value: BytesRef) {
        require(docID > lastDocID) {
            ("DocValuesField \""
                    + fieldInfo.name
                    + "\" appears more than once in this document (only one value is allowed per field)")
        }
        requireNotNull(value) { "field \"" + fieldInfo.name + "\": null value not allowed" }
        require(value.length <= (BYTE_BLOCK_SIZE - 2)) {
            ("DocValuesField \""
                    + fieldInfo.name
                    + "\" is too large, must be <= "
                    + (BYTE_BLOCK_SIZE - 2))
        }

        addOneValue(value)
        docsWithField.add(docID)

        lastDocID = docID
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

        pending.add(termID.toLong())
        updateBytesUsed()
    }

    private fun updateBytesUsed() {
        val newBytesUsed: Long = pending.ramBytesUsed() + docsWithField.ramBytesUsed()
        iwBytesUsed.addAndGet(newBytesUsed - bytesUsed)
        bytesUsed = newBytesUsed
    }

    private fun finish() {
        if (finalSortedValues == null) {
            val valueCount: Int = hash.size()
            updateBytesUsed()
            assert(finalOrdMap == null && finalOrds == null)
            finalSortedValues = hash.sort()
            finalOrds = pending.build()
            finalOrdMap = IntArray(valueCount)
            for (ord in 0..<valueCount) {
                finalOrdMap[finalSortedValues[ord]] = ord
            }
        }
    }

    override val docValues: SortedDocValues
        get() {
            finish()
            return BufferedSortedDocValues(
                hash, finalOrds!!, finalSortedValues, finalOrdMap, docsWithField.iterator()
            )
        }

    @Throws(IOException::class)
    override fun flush(
        state: SegmentWriteState,
        sortMap: Sorter.DocMap,
        dvConsumer: DocValuesConsumer
    ) {
        finish()

        dvConsumer.addSortedField(
            fieldInfo,
            getDocValuesProducer(
                fieldInfo, hash, finalOrds!!, finalSortedValues, finalOrdMap, docsWithField, sortMap
            )
        )
    }

    internal class BufferedSortedDocValues(
        val hash: BytesRefHash,
        docToOrd: PackedLongValues,
        val sortedValues: IntArray,
        val ordMap: IntArray,
        val docsWithField: DocIdSetIterator
    ) : SortedDocValues() {
        val scratch: BytesRef = BytesRef()
        private var ord = 0
        val iter: PackedLongValues.Iterator = docToOrd.iterator()

        override fun docID(): Int {
            return docsWithField.docID()
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            val docID: Int = docsWithField.nextDoc()
            if (docID != NO_MORE_DOCS) {
                ord = Math.toIntExact(iter.next())
                ord = ordMap[ord]
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

        override fun ordValue(): Int {
            return ord
        }

        override fun lookupOrd(ord: Int): BytesRef {
            assert(ord >= 0 && ord < sortedValues.size)
            assert(sortedValues[ord] >= 0 && sortedValues[ord] < sortedValues.size)
            hash.get(sortedValues[ord], scratch)
            return scratch
        }

        override val valueCount: Int
            get() = hash.size()
    }

    internal class SortingSortedDocValues(private val `in`: SortedDocValues, private val ords: IntArray) :
        SortedDocValues() {
        private var docID = -1

        override fun docID(): Int {
            return docID
        }

        override fun nextDoc(): Int {
            while (true) {
                docID++
                if (docID == ords.size) {
                    docID = NO_MORE_DOCS
                    break
                }
                if (ords[docID] != -1) {
                    break
                }
                // skip missing docs
            }

            return docID
        }

        override fun advance(target: Int): Int {
            throw UnsupportedOperationException("use nextDoc instead")
        }

        @Throws(IOException::class)
        override fun advanceExact(target: Int): Boolean {
            // needed in IndexSorter#StringSorter
            docID = target
            return ords[target] != -1
        }

        override fun ordValue(): Int {
            return ords[docID]
        }

        override fun cost(): Long {
            return `in`.cost()
        }

        @Throws(IOException::class)
        override fun lookupOrd(ord: Int): BytesRef? {
            return `in`.lookupOrd(ord)
        }

        override val valueCount: Int
            get() = `in`.valueCount
    }

    companion object {
        @Throws(IOException::class)
        private fun sortDocValues(
            maxDoc: Int,
            sortMap: Sorter.DocMap,
            oldValues: SortedDocValues
        ): IntArray {
            val ords = IntArray(maxDoc)
            Arrays.fill(ords, -1)
            var docID: Int
            while ((oldValues.nextDoc()
                    .also { docID = it }) != NO_MORE_DOCS
            ) {
                val newDocID: Int = sortMap.oldToNew(docID)
                ords[newDocID] = oldValues.ordValue()
            }
            return ords
        }

        @Throws(IOException::class)
        fun getDocValuesProducer(
            writerFieldInfo: FieldInfo,
            hash: BytesRefHash,
            ords: PackedLongValues,
            sortedValues: IntArray,
            ordMap: IntArray,
            docsWithField: DocsWithFieldSet,
            sortMap: Sorter.DocMap
        ): DocValuesProducer {
            val sorted: IntArray?
            if (sortMap != null) {
                sorted =
                    sortDocValues(
                        sortMap.size(),
                        sortMap,
                        BufferedSortedDocValues(
                            hash, ords, sortedValues, ordMap, docsWithField.iterator()
                        )
                    )
            } else {
                sorted = null
            }
            return object : EmptyDocValuesProducer() {
                override fun getSorted(fieldInfoIn: FieldInfo): SortedDocValues {
                    require(fieldInfoIn == writerFieldInfo) { "wrong fieldInfo" }
                    val buf: SortedDocValues =
                        BufferedSortedDocValues(hash, ords, sortedValues, ordMap, docsWithField.iterator())
                    if (sorted == null) {
                        return buf
                    }
                    return SortingSortedDocValues(buf, sorted)
                }
            }
        }
    }
}
