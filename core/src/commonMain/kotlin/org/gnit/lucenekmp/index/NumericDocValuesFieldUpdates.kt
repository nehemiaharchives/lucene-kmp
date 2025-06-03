package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.util.BitSet
import org.gnit.lucenekmp.util.BitSetIterator
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.RamUsageEstimator
import org.gnit.lucenekmp.util.SparseFixedBitSet
import org.gnit.lucenekmp.util.packed.AbstractPagedMutable
import org.gnit.lucenekmp.util.packed.PackedInts
import org.gnit.lucenekmp.util.packed.PagedGrowableWriter
import org.gnit.lucenekmp.util.packed.PagedMutable


/**
 * A [DocValuesFieldUpdates] which holds updates of documents, of a single [ ].
 *
 * @lucene.experimental
 */
internal class NumericDocValuesFieldUpdates : DocValuesFieldUpdates {
    // TODO: can't this just be NumericDocValues now  avoid boxing the long value...
    internal class Iterator(
        size: Int,
        private val minValue: Long,
        private val values: AbstractPagedMutable<*>,
        docs: PagedMutable,
        delGen: Long
    ) : AbstractIterator(size, docs, delGen) {
        private var value: Long = 0

        override fun longValue(): Long {
            return value
        }

        override fun binaryValue(): BytesRef {
            throw UnsupportedOperationException()
        }

        override fun set(idx: Long) {
            value = values.get(idx) + minValue
        }
    }

    private var values: AbstractPagedMutable<*>
    private val minValue: Long

    constructor(delGen: Long, field: String, maxDoc: Int) : super(
        maxDoc,
        delGen,
        field,
        DocValuesType.NUMERIC
    ) {
        // we don't know the min/max range so we use the growable writer here to adjust as we go.
        values = PagedGrowableWriter(
            1,
            PAGE_SIZE,
            1,
            PackedInts.DEFAULT
        )
        minValue = 0
    }

    constructor(delGen: Long, field: String, minValue: Long, maxValue: Long, maxDoc: Int) : super(
        maxDoc,
        delGen,
        field,
        DocValuesType.NUMERIC
    ) {
        assert(
            minValue <= maxValue
        ) { "minValue must be <= maxValue [$minValue > $maxValue]" }
        val bitsPerValue: Int = PackedInts.unsignedBitsRequired(maxValue - minValue)
        values = PagedMutable(
            1,
            PAGE_SIZE,
            bitsPerValue,
            PackedInts.DEFAULT
        )
        this.minValue = minValue
    }

    override fun add(doc: Int, value: BytesRef) {
        throw UnsupportedOperationException()
    }

    override fun add(docId: Int, iterator: DocValuesFieldUpdates.Iterator) {
        add(docId, iterator.longValue())
    }

    /*@Synchronized*/
    override fun add(doc: Int, value: Long) {
        val add: Int = add(doc)
        values.set(add.toLong(), value - minValue)
    }

    override fun swap(i: Int, j: Int) {
        super.swap(i, j)
        val tmpVal: Long = values.get(j.toLong())
        values.set(j.toLong(), values.get(i.toLong()))
        values.set(i.toLong(), tmpVal)
    }

    override fun grow(size: Int) {
        super.grow(size)
        values = values.grow(size.toLong())
    }

    override fun resize(size: Int) {
        super.resize(size)
        values = values.resize(size.toLong())
    }

    override fun iterator(): Iterator {
        ensureFinished()
        return Iterator(size, minValue, values, docs, delGen)
    }

    override fun ramBytesUsed(): Long {
        return (values.ramBytesUsed()
                + super.ramBytesUsed()
                + Long.SIZE_BYTES
                + RamUsageEstimator.NUM_BYTES_OBJECT_REF)
    }

    internal class SingleValueNumericDocValuesFieldUpdates(
        delGen: Long,
        field: String,
        maxDoc: Int,
        private val value: Long
    ) : DocValuesFieldUpdates(
        maxDoc,
        delGen,
        field,
        DocValuesType.NUMERIC
    ) {
        private val bitSet: BitSet = SparseFixedBitSet(maxDoc)
        private var hasNoValue: BitSet? = null
        private var hasAtLeastOneValue = false

        // pkg private for testing
        fun longValue(): Long {
            return value
        }

        override fun add(doc: Int, value: Long) {
            assert(this.value == value)
            bitSet.set(doc)
            this.hasAtLeastOneValue = true
            if (hasNoValue != null) {
                hasNoValue!!.clear(doc)
            }
        }

        override fun add(doc: Int, value: BytesRef) {
            throw UnsupportedOperationException()
        }

        /*@Synchronized*/
        override fun reset(doc: Int) {
            bitSet.set(doc)
            this.hasAtLeastOneValue = true
            if (hasNoValue == null) {
                hasNoValue = SparseFixedBitSet(maxDoc)
            }
            hasNoValue!!.set(doc)
        }

        override fun add(docId: Int, iterator: DocValuesFieldUpdates.Iterator) {
            throw UnsupportedOperationException()
        }

        /*@Synchronized*/
        override fun any(): Boolean {
            return super.any() || hasAtLeastOneValue
        }

        override fun ramBytesUsed(): Long {
            return (super.ramBytesUsed()
                    + bitSet.ramBytesUsed()
                    + (if (hasNoValue == null) 0 else hasNoValue!!.ramBytesUsed()))
        }

        override fun iterator(): DocValuesFieldUpdates.Iterator {
            val iterator = BitSetIterator(bitSet, maxDoc.toLong())
            return object : DocValuesFieldUpdates.Iterator() {
                override fun docID(): Int {
                    return iterator.docID()
                }

                override fun nextDoc(): Int {
                    return iterator.nextDoc()
                }

                override fun longValue(): Long {
                    return value
                }

                override fun binaryValue(): BytesRef {
                    throw UnsupportedOperationException()
                }

                override fun delGen(): Long {
                    return delGen
                }

                override fun hasValue(): Boolean {
                    if (hasNoValue != null) {
                        return !hasNoValue!!.get(docID())
                    }
                    return true
                }
            }
        }
    }
}
