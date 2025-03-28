package org.gnit.lucenekmp.search.comparators

import kotlinx.io.IOException
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.jdkport.compare
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.FieldComparator
import org.gnit.lucenekmp.search.LeafFieldComparator
import org.gnit.lucenekmp.search.Pruning
import org.gnit.lucenekmp.search.Scorable


/** Comparator that sorts by asc _doc  */
class DocComparator(numHits: Int, reverse: Boolean, pruning: Pruning) : FieldComparator<Int>() {
    private val docIDs: IntArray
    private val enableSkipping: Boolean // if skipping functionality should be enabled
    private var bottom = 0
    private var topValue = 0
    private var topValueSet = false
    private var bottomValueSet = false
    private var hitsThresholdReached = false

    /** Creates a new comparator based on document ids for `numHits`  */
    init {
        this.docIDs = IntArray(numHits)
        // skipping functionality is enabled if we are sorting by _doc in asc order as a primary sort
        this.enableSkipping = (reverse == false && pruning !== Pruning.NONE)
    }

    override fun compare(slot1: Int, slot2: Int): Int {
        // No overflow risk because docIDs are non-negative
        return docIDs[slot1] - docIDs[slot2]
    }

    override fun getLeafComparator(context: LeafReaderContext): LeafFieldComparator {
        // TODO: can we "map" our docIDs to the current
        // reader saves having to then subtract on every
        // compare call
        return this.DocLeafComparator(context)
    }

    override fun setTopValue(value: Int) {
        topValue = value
        topValueSet = true
    }

    override fun value(slot: Int): Int {
        return docIDs[slot]
    }

    /**
     * DocLeafComparator with skipping functionality. When sort by _doc asc, after collecting top N
     * matches and enough hits, the comparator can skip all the following documents. When sort by _doc
     * asc and "top" document is set after which search should start, the comparator provides an
     * iterator that can quickly skip to the desired "top" document.
     */
    private inner class DocLeafComparator(context: LeafReaderContext) : LeafFieldComparator {
        private val docBase: Int
        private var minDoc = 0
        private var maxDoc = 0
        private var competitiveIterator: DocIdSetIterator? = null // iterator that starts from topValue

        init {
            this.docBase = context.docBase
            if (enableSkipping) {
                // Skip docs before topValue, but include docs starting with topValue.
                // Including topValue is necessary when doing sort on [_doc, other fields]
                // in a distributed search where there are docs from different indices
                // with the same docID.
                this.minDoc = topValue
                this.maxDoc = context.reader().maxDoc()
                this.competitiveIterator = DocIdSetIterator.all(maxDoc)
            } else {
                this.minDoc = -1
                this.maxDoc = -1
                this.competitiveIterator = null
            }
        }

        override fun setBottom(slot: Int) {
            bottom = docIDs[slot]
            bottomValueSet = true
            updateIterator()
        }

        override fun compareBottom(doc: Int): Int {
            // No overflow risk because docIDs are non-negative
            return bottom - (docBase + doc)
        }

        override fun compareTop(doc: Int): Int {
            val docValue = docBase + doc
            return Int.compare(topValue, docValue)
        }

        @Throws(IOException::class)
        override fun copy(slot: Int, doc: Int) {
            docIDs[slot] = docBase + doc
        }

        @Throws(IOException::class)
        override fun setScorer(scorer: Scorable) {
            // update an iterator on a new segment
            updateIterator()
        }

        override fun competitiveIterator(): DocIdSetIterator? {
            if (enableSkipping == false) {
                return null
            } else {
                return object : DocIdSetIterator() {
                    private var docID: Int = competitiveIterator!!.docID()

                    @Throws(IOException::class)
                    override fun nextDoc(): Int {
                        return advance(docID + 1)
                    }

                    override fun docID(): Int {
                        return docID
                    }

                    override fun cost(): Long {
                        return competitiveIterator!!.cost()
                    }

                    @Throws(IOException::class)
                    override fun advance(target: Int): Int {
                        return competitiveIterator!!.advance(target).also { docID = it }
                    }
                }
            }
        }

        override fun setHitsThresholdReached() {
            hitsThresholdReached = true
            updateIterator()
        }

        fun updateIterator() {
            if (enableSkipping == false || hitsThresholdReached == false) return
            if (bottomValueSet) {
                // since we've collected top N matches, we can early terminate
                // Currently early termination on _doc is also implemented in TopFieldCollector, but this
                // will be removed
                // once all bulk scores uses collectors' iterators
                competitiveIterator = DocIdSetIterator.empty()
            } else if (topValueSet) {
                // skip to the desired top doc
                if (docBase + maxDoc <= minDoc) {
                    competitiveIterator = DocIdSetIterator.empty() // skip this segment
                } else {
                    val segmentMinDoc: Int = kotlin.math.max(competitiveIterator!!.docID(), minDoc - docBase)
                    competitiveIterator = MinDocIterator(segmentMinDoc, maxDoc)
                }
            }
        }
    }
}
