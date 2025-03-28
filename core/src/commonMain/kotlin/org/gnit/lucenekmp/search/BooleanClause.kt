package org.gnit.lucenekmp.search

import kotlin.jvm.JvmRecord


/** A clause in a BooleanQuery.  */
@JvmRecord
data class BooleanClause(val query: Query, val occur: Occur) {
    /** Specifies how clauses are to occur in matching documents.  */
    enum class Occur {
        /** Use this operator for clauses that *must* appear in the matching documents.  */
        MUST {
            override fun toString(): String {
                return "+"
            }
        },

        /** Like [.MUST] except that these clauses do not participate in scoring.  */
        FILTER {
            override fun toString(): String {
                return "#"
            }
        },

        /**
         * Use this operator for clauses that *should* appear in the matching documents. For a
         * BooleanQuery with no `MUST` clauses one or more `SHOULD` clauses must
         * match a document for the BooleanQuery to match.
         *
         * @see BooleanQuery.Builder.setMinimumNumberShouldMatch
         */
        SHOULD {
            override fun toString(): String {
                return ""
            }
        },

        /**
         * Use this operator for clauses that *must not* appear in the matching documents. Note
         * that it is not possible to search for queries that only consist of a `MUST_NOT`
         * clause. These clauses do not contribute to the score of documents.
         */
        MUST_NOT {
            override fun toString(): String {
                return "-"
            }
        }
    }

    val isProhibited: Boolean
        get() = Occur.MUST_NOT === occur

    val isRequired: Boolean
        get() = occur === Occur.MUST || occur === Occur.FILTER

    val isScoring: Boolean
        get() = occur === Occur.MUST || occur === Occur.SHOULD

}
