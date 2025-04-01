package org.gnit.lucenekmp.search

import kotlinx.io.IOException


/**
 * Returned by [Scorer.twoPhaseIterator] to expose an approximation of a [ ]. When the [.approximation]'s [DocIdSetIterator.nextDoc] or
 * [DocIdSetIterator.advance] return, [.matches] needs to be checked in order to
 * know whether the returned doc ID actually matches.
 *
 * @lucene.experimental
 */
abstract class TwoPhaseIterator protected constructor(approximation: DocIdSetIterator) {
    open val approximation: DocIdSetIterator

    /** Takes the approximation to be returned by [.approximation]. Not null.  */
    init {
        this.approximation = requireNotNull<DocIdSetIterator>(approximation)
    }

    private class TwoPhaseIteratorAsDocIdSetIterator(twoPhaseIterator: TwoPhaseIterator) : DocIdSetIterator() {
        val twoPhaseIterator: TwoPhaseIterator
        val approximation: DocIdSetIterator

        init {
            this.twoPhaseIterator = twoPhaseIterator
            this.approximation = twoPhaseIterator.approximation
        }

        override fun docID(): Int {
            return approximation.docID()
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            return doNext(approximation.nextDoc())
        }

        @Throws(IOException::class)
        override fun advance(target: Int): Int {
            return doNext(approximation.advance(target))
        }

        @Throws(IOException::class)
        fun doNext(doc: Int): Int {
            var doc = doc
            while (true) {
                if (doc == NO_MORE_DOCS) {
                    return NO_MORE_DOCS
                } else if (twoPhaseIterator.matches()) {
                    return doc
                }
                doc = approximation.nextDoc()
            }
        }

        override fun cost(): Long {
            return approximation.cost()
        }
    }

    /**
     * Return an approximation. The returned [DocIdSetIterator] is a superset of the matching
     * documents, and each match needs to be confirmed with [.matches] in order to know
     * whether it matches or not.
     */
    fun approximation(): DocIdSetIterator {
        return approximation
    }

    /**
     * Return whether the current doc ID that [.approximation] is on matches. This method
     * should only be called when the iterator is positioned -- ie. not when [ ][DocIdSetIterator.docID] is `-1` or [DocIdSetIterator.NO_MORE_DOCS] -- and at most
     * once.
     */
    @Throws(IOException::class)
    abstract fun matches(): Boolean

    /**
     * An estimate of the expected cost to determine that a single document [.matches]. This
     * can be called before iterating the documents of [.approximation]. Returns an expected
     * cost in number of simple operations like addition, multiplication, comparing two numbers and
     * indexing an array. The returned value must be positive.
     */
    abstract fun matchCost(): Float

    companion object {
        /** Return a [DocIdSetIterator] view of the provided [TwoPhaseIterator].  */
        fun asDocIdSetIterator(twoPhaseIterator: TwoPhaseIterator): DocIdSetIterator {
            return TwoPhaseIteratorAsDocIdSetIterator(twoPhaseIterator)
        }

        /**
         * If the given [DocIdSetIterator] has been created with [.asDocIdSetIterator], then
         * this will return the wrapped [TwoPhaseIterator]. Otherwise this returns `null`.
         */
        fun unwrap(iterator: DocIdSetIterator): TwoPhaseIterator? {
            if (iterator is TwoPhaseIteratorAsDocIdSetIterator) {
                return iterator.twoPhaseIterator
            } else {
                return null
            }
        }
    }
}
