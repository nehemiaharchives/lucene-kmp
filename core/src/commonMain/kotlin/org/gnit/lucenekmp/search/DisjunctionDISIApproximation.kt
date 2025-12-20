package org.gnit.lucenekmp.search

import io.github.oshai.kotlinlogging.KotlinLogging
import okio.IOException
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.FixedBitSet
import kotlin.math.min

private val logger = KotlinLogging.logger { }


/**
 * A [DocIdSetIterator] which is a disjunction of the approximations of the provided
 * iterators.
 *
 * @lucene.internal
 */
class DisjunctionDISIApproximation(subIterators: MutableCollection<out DisiWrapper>, leadCost: Long) :
    DocIdSetIterator() {
    // Heap of iterators that lead iteration.
    private val leadIterators: DisiPriorityQueue

    // List of iterators that will likely advance on every call to nextDoc() / advance()
    private val otherIterators: Array<DisiWrapper>
    private val cost: Long
    private var leadTop: DisiWrapper
    private var minOtherDoc: Int
    private var debugCalls: Long = 0

    init {
        // Using a heap to store disjunctive clauses is great for exhaustive evaluation, when a single
        // clause needs to move through the heap on every iteration on average. However, when
        // intersecting with a selective filter, it is possible that all clauses need advancing, which
        // makes the reordering cost scale in O(N * log(N)) per advance() call when checking clauses
        // linearly would scale in O(N).
        // To protect against this reordering overhead, we try to have 1.5 clauses or less that advance
        // on every advance() call by only putting clauses into the heap as long as Σ min(1, cost /
        // leadCost) <= 1.5, or Σ min(leadCost, cost) <= 1.5 * leadCost. Other clauses are checked
        // linearly.

        val wrappers: Array<DisiWrapper> =
            subIterators.toList().toTypedArray()
        // Sort by descending cost.
        // Sort by descending cost.
        wrappers.sortWith(compareByDescending { it.cost })

        var reorderThreshold = leadCost + (leadCost shr 1)
        if (reorderThreshold < 0) { // overflow
            reorderThreshold = Long.Companion.MAX_VALUE
        }

        var cost: Long = 0 // track total cost
        // Split `wrappers` into those that will remain out of the PQ, and those that will go in
        // (PQ entries at the end). `lastIdx` is the last index of the wrappers that will remain out.
        var reorderCost: Long = 0
        var lastIdx = wrappers.size - 1
        while (lastIdx >= 0) {
            val lastCost: Long = wrappers[lastIdx].cost
            val inc = min(lastCost, leadCost)
            if (reorderCost + inc < 0 || reorderCost + inc > reorderThreshold) {
                break
            }
            reorderCost += inc
            cost += lastCost
            lastIdx--
        }

        // Make leadIterators not empty. This helps save conditionals in the implementation which are
        // rarely tested.
        if (lastIdx == wrappers.size - 1) {
            cost += wrappers[lastIdx].cost
            lastIdx--
        }

        // Build the PQ:
        require(lastIdx >= -1 && lastIdx < wrappers.size - 1)
        val pqLen = wrappers.size - lastIdx - 1
        leadIterators = DisiPriorityQueue.ofMaxSize(pqLen)
        leadIterators.addAll(wrappers, lastIdx + 1, pqLen)

        // Build the non-PQ list:
        otherIterators = ArrayUtil.copyOfSubArray(wrappers, 0, lastIdx + 1)
        minOtherDoc = Int.Companion.MAX_VALUE
        for (w in otherIterators) {
            cost += w.cost
            minOtherDoc = kotlin.math.min(minOtherDoc, w.doc)
        }

        this.cost = cost
        leadTop = leadIterators.top()!!
    }

    override fun cost(): Long {
        return cost
    }

    override fun docID(): Int {
        return min(minOtherDoc, leadTop.doc)
    }

    @Throws(IOException::class)
    override fun nextDoc(): Int {
        debugCalls++
        if (debugCalls <= 5 || debugCalls % LOG_INTERVAL == 0L) {
            logger.debug {
                "[DisjunctionDISIApproximation.nextDoc] enter leadTop=${leadTop.doc} minOtherDoc=$minOtherDoc " +
                    "leadApprox=${leadTop.approximation!!::class.simpleName}"
            }
        }
        if (leadTop.doc == NO_MORE_DOCS && minOtherDoc == NO_MORE_DOCS) {
            return NO_MORE_DOCS
        }
        var iterations = 0
        if (leadTop.doc < minOtherDoc) {
            val curDoc: Int = leadTop.doc
            do {
                val prevDoc = leadTop.doc
                if (debugCalls <= 5 || debugCalls % LOG_INTERVAL == 0L) {
                    logger.debug {
                        "[DisjunctionDISIApproximation.nextDoc] lead advance prevDoc=$prevDoc " +
                            "approx=${leadTop.approximation!!::class.simpleName}"
                    }
                }
                leadTop.doc = leadTop.approximation!!.nextDoc()
                if (debugCalls <= 5 || debugCalls % LOG_INTERVAL == 0L) {
                    logger.debug {
                        "[DisjunctionDISIApproximation.nextDoc] lead advanced newDoc=${leadTop.doc} " +
                            "approx=${leadTop.approximation!!::class.simpleName}"
                    }
                }
                if (leadTop.doc == prevDoc) {
                    iterations++
                    if (iterations >= STALL_THRESHOLD) {
                        throw IllegalStateException(
                            "DisjunctionDISIApproximation.nextDoc stuck prevDoc=$prevDoc minOtherDoc=$minOtherDoc " +
                                "leadTop=${leadTop.approximation!!::class.simpleName}"
                        )
                    }
                }
                leadTop = leadIterators.updateTop()
            } while (leadTop.doc == curDoc)
            return min(leadTop.doc, minOtherDoc)
        } else {
            if (minOtherDoc == NO_MORE_DOCS) {
                return NO_MORE_DOCS
            }
            return advance(minOtherDoc + 1)
        }
    }

    @Throws(IOException::class)
    override fun advance(target: Int): Int {
        debugCalls++
        if (debugCalls <= 5 || debugCalls % LOG_INTERVAL == 0L) {
            logger.debug {
                "[DisjunctionDISIApproximation.advance] enter target=$target leadTop=${leadTop.doc} minOtherDoc=$minOtherDoc " +
                    "leadApprox=${leadTop.approximation!!::class.simpleName}"
            }
        }
        if (leadTop.doc == NO_MORE_DOCS && minOtherDoc == NO_MORE_DOCS) {
            return NO_MORE_DOCS
        }
        if (target == NO_MORE_DOCS) {
            return NO_MORE_DOCS
        }
        var leadIterations = 0
        while (leadTop.doc < target) {
            val prevDoc = leadTop.doc
            if (debugCalls <= 5 || debugCalls % LOG_INTERVAL == 0L) {
                logger.debug {
                    "[DisjunctionDISIApproximation.advance] lead advance prevDoc=$prevDoc target=$target " +
                        "approx=${leadTop.approximation!!::class.simpleName}"
                }
            }
            leadTop.doc = leadTop.approximation!!.advance(target)
            if (debugCalls <= 5 || debugCalls % LOG_INTERVAL == 0L) {
                logger.debug {
                    "[DisjunctionDISIApproximation.advance] lead advanced newDoc=${leadTop.doc} target=$target " +
                        "approx=${leadTop.approximation!!::class.simpleName}"
                }
            }
            if (leadTop.doc == prevDoc || leadTop.doc < target) {
                leadIterations++
                if (leadIterations >= STALL_THRESHOLD) {
                    throw IllegalStateException(
                        "DisjunctionDISIApproximation.advance stuck target=$target prevDoc=$prevDoc " +
                            "newDoc=${leadTop.doc} minOtherDoc=$minOtherDoc leadTop=${leadTop.approximation!!::class.simpleName}"
                    )
                }
            }
            leadTop = leadIterators.updateTop()
        }

        minOtherDoc = Int.Companion.MAX_VALUE
        for (w in otherIterators) {
            if (w.doc < target) {
                val prevDoc = w.doc
                w.doc = w.approximation!!.advance(target)
                if (w.doc == prevDoc || w.doc < target) {
                    logger.debug {
                        "[DisjunctionDISIApproximation.advance] otherIterator stalled " +
                            "target=$target prevDoc=$prevDoc newDoc=${w.doc} approx=${w.approximation!!::class.simpleName}"
                    }
                }
            }
            minOtherDoc = min(minOtherDoc, w.doc)
        }

        return min(leadTop.doc, minOtherDoc)
    }

    @Throws(IOException::class)
    public override fun intoBitSet(upTo: Int, bitSet: FixedBitSet, offset: Int) {
        while (leadTop.doc < upTo) {
            leadTop.approximation!!.intoBitSet(upTo, bitSet, offset)
            leadTop.doc = leadTop.approximation!!.docID()
            leadTop = leadIterators.updateTop()
        }

        minOtherDoc = Int.Companion.MAX_VALUE
        for (w in otherIterators) {
            w.approximation!!.intoBitSet(upTo, bitSet, offset)
            w.doc = w.approximation!!.docID()
            minOtherDoc = kotlin.math.min(minOtherDoc, w.doc)
        }
    }

    /** Return the linked list of iterators positioned on the current doc.  */
    fun topList(): DisiWrapper? {
        if (leadTop.doc < minOtherDoc) {
            return leadIterators.topList()!!
        } else {
            return computeTopList()
        }
    }

    private fun computeTopList(): DisiWrapper? {
        require(leadTop.doc >= minOtherDoc)
        var topList: DisiWrapper? = null
        if (leadTop.doc == minOtherDoc) {
            topList = leadIterators.topList()!!
        }
        for (w in otherIterators) {
            if (w.doc == minOtherDoc) {
                w.next = topList
                topList = w
            }
        }
        return topList
    }

    companion object {
        private const val LOG_INTERVAL = 1000L
        private const val STALL_THRESHOLD = 1000

        fun of(
            subIterators: MutableCollection<out DisiWrapper>, leadCost: Long
        ): DisjunctionDISIApproximation {
            return DisjunctionDISIApproximation(subIterators, leadCost)
        }
    }
}
