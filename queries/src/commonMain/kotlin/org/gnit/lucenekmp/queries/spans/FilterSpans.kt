package org.gnit.lucenekmp.queries.spans

import okio.IOException
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.TwoPhaseIterator

/**
 * A [Spans] implementation wrapping another spans instance, allowing to filter spans matches
 * easily by implementing [accept]
 */
abstract class FilterSpans(
    protected val `in`: Spans,
) : Spans() {
    private var atFirstInCurrentDoc = false
    private var startPos = -1

    /**
     * Returns YES if the candidate should be an accepted match, NO if it should not, and
     * NO_MORE_IN_CURRENT_DOC if iteration should move on to the next document.
     */
    @Throws(IOException::class)
    protected abstract fun accept(candidate: Spans): AcceptStatus

    @Throws(IOException::class)
    override fun nextDoc(): Int {
        while (true) {
            val doc = `in`.nextDoc()
            if (doc == NO_MORE_DOCS) {
                return NO_MORE_DOCS
            } else if (twoPhaseCurrentDocMatches()) {
                return doc
            }
        }
    }

    @Throws(IOException::class)
    override fun advance(target: Int): Int {
        var doc = `in`.advance(target)
        while (doc != NO_MORE_DOCS) {
            if (twoPhaseCurrentDocMatches()) {
                break
            }
            doc = `in`.nextDoc()
        }
        return doc
    }

    override fun docID(): Int {
        return `in`.docID()
    }

    @Throws(IOException::class)
    override fun nextStartPosition(): Int {
        if (atFirstInCurrentDoc) {
            atFirstInCurrentDoc = false
            return startPos
        }
        while (true) {
            startPos = `in`.nextStartPosition()
            if (startPos == NO_MORE_POSITIONS) {
                return NO_MORE_POSITIONS
            }
            when (accept(`in`)) {
                AcceptStatus.YES -> return startPos
                AcceptStatus.NO -> {}
                AcceptStatus.NO_MORE_IN_CURRENT_DOC -> {
                    startPos = NO_MORE_POSITIONS
                    return startPos
                }
            }
        }
    }

    override fun startPosition(): Int {
        return if (atFirstInCurrentDoc) -1 else startPos
    }

    override fun endPosition(): Int {
        return if (atFirstInCurrentDoc) {
            -1
        } else if (startPos != NO_MORE_POSITIONS) {
            `in`.endPosition()
        } else {
            NO_MORE_POSITIONS
        }
    }

    override fun width(): Int {
        return `in`.width()
    }

    @Throws(IOException::class)
    override fun collect(collector: SpanCollector) {
        `in`.collect(collector)
    }

    override fun cost(): Long {
        return `in`.cost()
    }

    override fun toString(): String {
        return "Filter(${`in`})"
    }

    override fun asTwoPhaseIterator(): TwoPhaseIterator {
        val inner = `in`.asTwoPhaseIterator()
        return if (inner != null) {
            object : TwoPhaseIterator(inner.approximation()) {
                @Throws(IOException::class)
                override fun matches(): Boolean {
                    return inner.matches() && twoPhaseCurrentDocMatches()
                }

                override fun matchCost(): Float {
                    return inner.matchCost()
                }

                override fun toString(): String {
                    return "FilterSpans@asTwoPhaseIterator(inner=$inner, in=${`in`})"
                }
            }
        } else {
            object : TwoPhaseIterator(`in`) {
                @Throws(IOException::class)
                override fun matches(): Boolean {
                    return twoPhaseCurrentDocMatches()
                }

                override fun matchCost(): Float {
                    return `in`.positionsCost()
                }

                override fun toString(): String {
                    return "FilterSpans@asTwoPhaseIterator(in=${`in`})"
                }
            }
        }
    }

    override fun positionsCost(): Float {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    private fun twoPhaseCurrentDocMatches(): Boolean {
        atFirstInCurrentDoc = false
        startPos = `in`.nextStartPosition()
        assert(startPos != NO_MORE_POSITIONS)
        while (true) {
            when (accept(`in`)) {
                AcceptStatus.YES -> {
                    atFirstInCurrentDoc = true
                    return true
                }
                AcceptStatus.NO -> {
                    startPos = `in`.nextStartPosition()
                    if (startPos != NO_MORE_POSITIONS) {
                        continue
                    }
                    startPos = -1
                    return false
                }
                AcceptStatus.NO_MORE_IN_CURRENT_DOC -> {
                    startPos = -1
                    return false
                }
            }
        }
    }

    /**
     * Status returned from [FilterSpans.accept] that indicates whether a candidate match
     * should be accepted, rejected, or rejected and move on to the next document.
     */
    enum class AcceptStatus {
        /** Indicates the match should be accepted */
        YES,

        /** Indicates the match should be rejected */
        NO,

        /**
         * Indicates the match should be rejected, and the enumeration may continue with the next
         * document.
         */
        NO_MORE_IN_CURRENT_DOC,
    }
}
