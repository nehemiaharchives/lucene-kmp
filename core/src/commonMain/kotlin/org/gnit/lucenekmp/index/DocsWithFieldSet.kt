package org.gnit.lucenekmp.index


import org.gnit.lucenekmp.search.DocIdSet
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.util.BitSetIterator
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.RamUsageEstimator

/**
 * Accumulator for documents that have a value for a field. This is optimized for the case that all
 * documents have a value.
 */
class DocsWithFieldSet
/** Creates an empty DocsWithFieldSet.  */
    : DocIdSet() {
    private var set: FixedBitSet? = null
    private var cardinality = 0
    private var lastDocId = -1

    /**
     * Add a document to the set
     *
     * @param docID – document ID to be added
     */
    fun add(docID: Int) {
        require(docID > lastDocId) { "Out of order doc ids: last=$lastDocId, next=$docID" }
        if (set != null) {
            set = FixedBitSet.ensureCapacity(set!!, docID)
            set!!.set(docID)
        } else if (docID != cardinality) {
            // migrate to a sparse encoding using a bit set
            set = FixedBitSet(docID + 1)
            set!!.set(0, cardinality)
            set!!.set(docID)
        }
        lastDocId = docID
        cardinality++
    }

    override fun ramBytesUsed(): Long {
        return BASE_RAM_BYTES_USED + (if (set == null) 0 else set!!.ramBytesUsed())
    }

    override fun iterator(): DocIdSetIterator {
        return if (set != null) BitSetIterator(set!!, cardinality.toLong()) else DocIdSetIterator.all(cardinality)
    }

    /** Return the number of documents of this set.  */
    fun cardinality(): Int {
        return cardinality
    }

    companion object {
        private val BASE_RAM_BYTES_USED: Long = RamUsageEstimator.shallowSizeOfInstance(DocsWithFieldSet::class)
    }
}
