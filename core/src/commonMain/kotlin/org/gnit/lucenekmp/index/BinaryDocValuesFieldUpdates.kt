package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.RamUsageEstimator
import org.gnit.lucenekmp.util.packed.PackedInts
import org.gnit.lucenekmp.util.packed.PagedGrowableWriter
import org.gnit.lucenekmp.util.packed.PagedMutable

/**
 * A [DocValuesFieldUpdates] which holds updates of documents, of a single [ ].
 *
 * @lucene.experimental
 */
internal class BinaryDocValuesFieldUpdates(delGen: Long, field: String, maxDoc: Int) :
    DocValuesFieldUpdates(maxDoc, delGen, field, DocValuesType.BINARY) {
    internal class Iterator(
        size: Int,
        private val offsets: PagedGrowableWriter,
        private val lengths: PagedGrowableWriter,
        docs: PagedMutable,
        values: BytesRef,
        delGen: Long
    ) : AbstractIterator(size, docs, delGen) {
        private val value: BytesRef = values.clone()
        private var offset = 0
        private var length = 0

        override fun binaryValue(): BytesRef {
            value.offset = offset
            value.length = length
            return value
        }

        override fun set(idx: Long) {
            offset = offsets.get(idx).toInt()
            length = lengths.get(idx).toInt()
        }

        override fun longValue(): Long {
            throw UnsupportedOperationException()
        }
    }

    private var offsets: PagedGrowableWriter
    private var lengths: PagedGrowableWriter
    private val values: BytesRefBuilder

    init {
        offsets = PagedGrowableWriter(
            1,
            PAGE_SIZE,
            1,
            PackedInts.FAST
        )
        lengths = PagedGrowableWriter(
            1,
            PAGE_SIZE,
            1,
            PackedInts.FAST
        )
        values = BytesRefBuilder()
    }

    override fun add(doc: Int, value: Long) {
        throw UnsupportedOperationException()
    }

    override fun add(docId: Int, iterator: DocValuesFieldUpdates.Iterator) {
        add(docId, iterator.binaryValue())
    }

    /*@Synchronized*/
    override fun add(doc: Int, value: BytesRef) {
        val index: Int = add(doc)
        offsets.set(index.toLong(), values.length().toLong())
        lengths.set(index.toLong(), value.length.toLong())
        values.append(value)
    }

    override fun swap(i: Int, j: Int) {
        super.swap(i, j)

        val tmpOffset: Long = offsets.get(j.toLong())
        offsets.set(j.toLong(), offsets.get(i.toLong()))
        offsets.set(i.toLong(), tmpOffset)

        val tmpLength: Long = lengths.get(j.toLong())
        lengths.set(j.toLong(), lengths.get(i.toLong()))
        lengths.set(i.toLong(), tmpLength)
    }

    override fun grow(size: Int) {
        super.grow(size)
        offsets = offsets.grow(size.toLong())
        lengths = lengths.grow(size.toLong())
    }

    override fun resize(size: Int) {
        super.resize(size)
        offsets = offsets.resize(size.toLong())
        lengths = lengths.resize(size.toLong())
    }

    override fun iterator(): Iterator {
        ensureFinished()
        return Iterator(size, offsets, lengths, docs, values.get(), delGen)
    }

    override fun ramBytesUsed(): Long {
        return (super.ramBytesUsed()
                + offsets.ramBytesUsed()
                + lengths.ramBytesUsed()
                + RamUsageEstimator.NUM_BYTES_OBJECT_HEADER
                + 2 * Int.SIZE_BYTES + 3 * RamUsageEstimator.NUM_BYTES_OBJECT_REF.toLong() + values.bytes().size)
    }
}
