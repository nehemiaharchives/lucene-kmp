package org.gnit.lucenekmp.queries.spans

import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.QueryVisitor
import org.gnit.lucenekmp.search.ScoreMode

/**
 * Holds all implementations of classes in the o.a.l.s.spans package as a back-compatibility test.
 * It does not run any tests per-se, however if someone adds a method to an interface or abstract
 * method to an abstract class, one of the implementations here will fail to compile and so we know
 * back-compat policy was violated.
 */
internal class JustCompileSearchSpans {
    companion object {
        private const val UNSUPPORTED_MSG = "unsupported: used for back-compat testing only !"
    }

    internal class JustCompileSpans : Spans() {
        override fun docID(): Int {
            throw UnsupportedOperationException(UNSUPPORTED_MSG)
        }

        override fun nextDoc(): Int {
            throw UnsupportedOperationException(UNSUPPORTED_MSG)
        }

        override fun advance(target: Int): Int {
            throw UnsupportedOperationException(UNSUPPORTED_MSG)
        }

        override fun startPosition(): Int {
            throw UnsupportedOperationException(UNSUPPORTED_MSG)
        }

        override fun endPosition(): Int {
            throw UnsupportedOperationException(UNSUPPORTED_MSG)
        }

        override fun width(): Int {
            throw UnsupportedOperationException(UNSUPPORTED_MSG)
        }

        override fun collect(collector: SpanCollector) {}

        override fun nextStartPosition(): Int {
            throw UnsupportedOperationException(UNSUPPORTED_MSG)
        }

        override fun cost(): Long {
            throw UnsupportedOperationException(UNSUPPORTED_MSG)
        }

        override fun positionsCost(): Float {
            throw UnsupportedOperationException(UNSUPPORTED_MSG)
        }
    }

    internal class JustCompileSpanQuery : SpanQuery() {
        override fun getField(): String {
            throw UnsupportedOperationException(UNSUPPORTED_MSG)
        }

        override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): SpanWeight {
            throw UnsupportedOperationException(UNSUPPORTED_MSG)
        }

        override fun visit(visitor: QueryVisitor) {
            throw UnsupportedOperationException(UNSUPPORTED_MSG)
        }

        override fun toString(field: String?): String {
            throw UnsupportedOperationException(UNSUPPORTED_MSG)
        }

        override fun equals(other: Any?): Boolean {
            throw UnsupportedOperationException(UNSUPPORTED_MSG)
        }

        override fun hashCode(): Int {
            throw UnsupportedOperationException(UNSUPPORTED_MSG)
        }
    }
}
