package org.gnit.lucenekmp.search

import kotlinx.io.IOException
import org.gnit.lucenekmp.util.FixedBitSet
import kotlin.math.min

/**
 * This abstract class defines methods to iterate over a set of non-decreasing doc ids. Note that
 * this class assumes it iterates on doc Ids, and therefore [.NO_MORE_DOCS] is set to {@value
 * * #NO_MORE_DOCS} in order to be used as a sentinel object. Implementations of this class are
 * expected to consider [Integer.MAX_VALUE] as an invalid value.
 */
abstract class DocIdSetIterator {
    /**
     * Returns the following:
     *
     *
     *  * `-1` if [.nextDoc] or [.advance] were not called yet.
     *  * [.NO_MORE_DOCS] if the iterator has exhausted.
     *  * Otherwise it should return the doc ID it is currently on.
     *
     *
     * @since 2.9
     */
    abstract fun docID(): Int

    /**
     * Advances to the next document in the set and returns the doc it is currently on, or [ ][.NO_MORE_DOCS] if there are no more docs in the set.<br></br>
     * **NOTE:** after the iterator has exhausted you should not call this method, as it may result
     * in unpredicted behavior.
     *
     * @since 2.9
     */
    @Throws(IOException::class)
    abstract fun nextDoc(): Int

    /**
     * Advances to the first beyond the current whose document number is greater than or equal to
     * *target*, and returns the document number itself. Exhausts the iterator and returns [ ][.NO_MORE_DOCS] if *target* is greater than the highest document number in the set.
     *
     *
     * The behavior of this method is **undefined** when called with ` target  current
    ` * , or after the iterator has exhausted. Both cases may result in unpredicted behavior.
     *
     *
     * When ` target > current` it behaves as if written:
     *
     * <pre class="prettyprint">
     * int advance(int target) {
     * int doc;
     * while ((doc = nextDoc()) &lt; target) {
     * }
     * return doc;
     * }
    </pre> *
     *
     * Some implementations are considerably more efficient than that.
     *
     *
     * **NOTE:** this method may be called with [.NO_MORE_DOCS] for efficiency by some
     * Scorers. If your implementation cannot efficiently determine that it should exhaust, it is
     * recommended that you check for that value in each call to this method.
     *
     * @since 2.9
     */
    @Throws(IOException::class)
    abstract fun advance(target: Int): Int

    /**
     * Slow (linear) implementation of [.advance] relying on [.nextDoc] to advance
     * beyond the target position.
     */
    @Throws(IOException::class)
    protected fun slowAdvance(target: Int): Int {
        require(docID() < target)
        var doc: Int
        do {
            doc = nextDoc()
        } while (doc < target)
        return doc
    }

    /**
     * Returns the estimated cost of this [DocIdSetIterator].
     *
     *
     * This is generally an upper bound of the number of documents this iterator might match, but
     * may be a rough heuristic, hardcoded value, or otherwise completely inaccurate.
     */
    abstract fun cost(): Long

    /**
     * Load doc IDs into a [FixedBitSet]. This should behave exactly as if implemented as below,
     * which is the default implementation:
     *
     * <pre class="prettyprint">
     * for (int doc = docID(); doc &lt; upTo; doc = nextDoc()) {
     * bitSet.set(doc - offset);
     * }
    </pre> *
     *
     *
     * **Note**: `offset` must be less than or equal to the [current doc][.docID]. Behaviour is undefined if this iterator is unpositioned.
     *
     *
     * **Note**: It is important not to clear bits from `bitSet` that may be already set.
     *
     *
     * **Note**: `offset` may be negative.
     *
     * @lucene.internal
     */
    @Throws(IOException::class)
    open fun intoBitSet(upTo: Int, bitSet: FixedBitSet, offset: Int) {
        require(offset <= docID())
        var doc = docID()
        while (doc < upTo) {
            bitSet.set(doc - offset)
            doc = nextDoc()
        }
    }

    companion object {
        /** An empty `DocIdSetIterator` instance  */
        fun empty(): DocIdSetIterator {
            return object : DocIdSetIterator() {
                var exhausted: Boolean = false

                override fun advance(target: Int): Int {
                    require(!exhausted)
                    require(target >= 0)
                    exhausted = true
                    return NO_MORE_DOCS
                }

                override fun docID(): Int {
                    return if (exhausted) NO_MORE_DOCS else -1
                }

                override fun nextDoc(): Int {
                    require(!exhausted)
                    exhausted = true
                    return NO_MORE_DOCS
                }

                override fun cost(): Long {
                    return 0
                }
            }
        }

        /** A [DocIdSetIterator] that matches all documents up to `maxDoc - 1`.  */
        fun all(maxDoc: Int): DocIdSetIterator {
            return object : DocIdSetIterator() {
                var doc: Int = -1

                override fun docID(): Int {
                    return doc
                }

                override fun nextDoc(): Int {
                    return advance(doc + 1)
                }

                override fun advance(target: Int): Int {
                    doc = target
                    if (doc >= maxDoc) {
                        doc = NO_MORE_DOCS
                    }
                    return doc
                }

                override fun cost(): Long {
                    return maxDoc.toLong()
                }

                override fun intoBitSet(upTo: Int, bitSet: FixedBitSet, offset: Int) {
                    var upTo = upTo
                    require(offset <= doc)
                    upTo = min(upTo, maxDoc)
                    if (upTo > doc) {
                        bitSet.set(doc - offset, upTo - offset)
                        advance(upTo)
                    }
                }
            }
        }

        /**
         * A [DocIdSetIterator] that matches a range documents from minDocID (inclusive) to maxDocID
         * (exclusive).
         */
        fun range(minDoc: Int, maxDoc: Int): DocIdSetIterator {
            require(minDoc < maxDoc) { "minDoc must be < maxDoc but got minDoc=" + minDoc + " maxDoc=" + maxDoc }
            require(minDoc >= 0) { "minDoc must be >= 0 but got minDoc=" + minDoc }
            return object : DocIdSetIterator() {
                private var doc = -1

                override fun docID(): Int {
                    return doc
                }

                override fun nextDoc(): Int {
                    return advance(doc + 1)
                }

                override fun advance(target: Int): Int {
                    if (target < minDoc) {
                        doc = minDoc
                    } else if (target >= maxDoc) {
                        doc = NO_MORE_DOCS
                    } else {
                        doc = target
                    }
                    return doc
                }

                override fun cost(): Long {
                    return (maxDoc - minDoc).toLong()
                }

                override fun intoBitSet(upTo: Int, bitSet: FixedBitSet, offset: Int) {
                    var upTo = upTo
                    require(offset <= doc)
                    upTo = min(upTo, maxDoc)
                    if (upTo > doc) {
                        bitSet.set(doc - offset, upTo - offset)
                        advance(upTo)
                    }
                }
            }
        }

        /**
         * When returned by [.nextDoc], [.advance] and [.docID] it means there
         * are no more docs in the iterator.
         */
        val NO_MORE_DOCS: Int = Int.Companion.MAX_VALUE
    }
}
