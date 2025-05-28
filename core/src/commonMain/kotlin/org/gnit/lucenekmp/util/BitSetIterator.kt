package org.gnit.lucenekmp.util

import okio.IOException
import org.gnit.lucenekmp.search.DocIdSetIterator
import kotlin.math.min
import kotlin.reflect.KClass
import kotlin.reflect.cast


/**
 * A [DocIdSetIterator] which iterates over set bits in a bit set.
 *
 * @lucene.internal
 */
class BitSetIterator(bits: BitSet, cost: Long) : DocIdSetIterator() {
    /** Return the wrapped [BitSet].  */
    val bitSet: BitSet
    private val length: Int
    private val cost: Long
    private var doc = -1

    /** Sole constructor.  */
    init {
        require(cost >= 0) { "cost must be >= 0, got $cost" }
        this.bitSet = bits
        this.length = bits.length()
        this.cost = cost
    }

    override fun docID(): Int {
        return doc
    }

    /** Set the current doc id that this iterator is on.  */
    fun setDocId(docId: Int) {
        this.doc = docId
    }

    override fun nextDoc(): Int {
        return advance(doc + 1)
    }

    override fun advance(target: Int): Int {
        if (target >= length) {
            return NO_MORE_DOCS.also { doc = it }
        }
        return bitSet.nextSetBit(target).also { doc = it }
    }

    override fun cost(): Long {
        return cost
    }

    @Throws(IOException::class)
    public override fun intoBitSet(upTo: Int, bitSet: FixedBitSet, offset: Int) {
        if (upTo > doc && this.bitSet is FixedBitSet) {
            var actualUpto = min(upTo, length)
            // The destination bit set may be shorter than this bit set. This is only legal if all bits
            // beyond offset + bitSet.length() are clear. If not, the below call to `super.intoBitSet`
            // will throw an exception.
            actualUpto = min(actualUpto, offset + bitSet.length())
            FixedBitSet.orRange(this.bitSet, doc, bitSet, doc - offset, actualUpto - doc)
            advance(actualUpto) // set the current doc
        }
        super.intoBitSet(upTo, bitSet, offset)
    }

    companion object {
        private fun <T : BitSet> getBitSet(
            iterator: DocIdSetIterator, clazz: KClass<out T>
        ): T? {
            if (iterator is BitSetIterator) {
                val bits = checkNotNull(iterator.bitSet)
                if (clazz.isInstance(bits)) {
                    return clazz.cast(bits)
                }
            }
            return null
        }

        /** If the provided iterator wraps a [FixedBitSet], returns it, otherwise returns null.  */
        fun getFixedBitSetOrNull(iterator: DocIdSetIterator): FixedBitSet? {
            return getBitSet(iterator, FixedBitSet::class)
        }

        /**
         * If the provided iterator wraps a [SparseFixedBitSet], returns it, otherwise returns null.
         */
        fun getSparseFixedBitSetOrNull(iterator: DocIdSetIterator): SparseFixedBitSet? {
            return getBitSet(iterator, SparseFixedBitSet::class)
        }
    }
}
