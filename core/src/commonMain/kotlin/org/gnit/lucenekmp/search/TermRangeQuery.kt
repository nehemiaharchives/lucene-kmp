package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.automaton.Automata
import org.gnit.lucenekmp.util.automaton.Automaton

/**
 * A Query that matches documents within an range of terms.
 *
 *
 * This query matches the documents looking for terms that fall into the supplied range according
 * to [BytesRef.compareTo].
 *
 *
 * **NOTE**: [TermRangeQuery] performs significantly slower than [ point-based ranges][PointRangeQuery] as it needs to visit all terms that match the range and merges their matches.
 *
 *
 * This query uses the [MultiTermQuery.CONSTANT_SCORE_BLENDED_REWRITE] rewrite method.
 *
 * @since 2.9
 */
class TermRangeQuery(
    field: String,
    lowerTerm: BytesRef,
    upperTerm: BytesRef?,
    includeLower: Boolean,
    includeUpper: Boolean,
    rewriteMethod: RewriteMethod = CONSTANT_SCORE_BLENDED_REWRITE
) : AutomatonQuery(
    Term(field, lowerTerm),
    toAutomaton(lowerTerm, upperTerm, includeLower, includeUpper),
    true,
    rewriteMethod
) {
    val lowerTerm: BytesRef?
    val upperTerm: BytesRef?
    private val includeLower: Boolean
    private val includeUpper: Boolean

    /**
     * Constructs a query selecting all terms greater/equal than `lowerTerm` but less/equal
     * than `upperTerm`.
     *
     *
     * If an endpoint is null, it is said to be "open". Either or both endpoints may be open. Open
     * endpoints may not be exclusive (you can't select all but the first or last term without
     * explicitly specifying the term to exclude.)
     *
     * @param field The field that holds both lower and upper terms.
     * @param lowerTerm The term text at the lower end of the range
     * @param upperTerm The term text at the upper end of the range
     * @param includeLower If true, the `lowerTerm` is included in the range.
     * @param includeUpper If true, the `upperTerm` is included in the range.
     * @param rewriteMethod the rewrite method to use when building the final query
     */
    /**
     * Constructs a query selecting all terms greater/equal than `lowerTerm` but less/equal
     * than `upperTerm`.
     *
     *
     * If an endpoint is null, it is said to be "open". Either or both endpoints may be open. Open
     * endpoints may not be exclusive (you can't select all but the first or last term without
     * explicitly specifying the term to exclude.)
     *
     * @param field The field that holds both lower and upper terms.
     * @param lowerTerm The term text at the lower end of the range
     * @param upperTerm The term text at the upper end of the range
     * @param includeLower If true, the `lowerTerm` is included in the range.
     * @param includeUpper If true, the `upperTerm` is included in the range.
     */
    init {
        this.lowerTerm = lowerTerm
        this.upperTerm = upperTerm
        this.includeLower = includeLower
        this.includeUpper = includeUpper
    }

    /** Returns the lower value of this range query  */
    /*fun getLowerTerm(): BytesRef {
        return lowerTerm
    }*/

    /** Returns the upper value of this range query  */
    /*fun getUpperTerm(): BytesRef {
        return upperTerm
    }*/

    /** Returns `true` if the lower endpoint is inclusive  */
    fun includesLower(): Boolean {
        return includeLower
    }

    /** Returns `true` if the upper endpoint is inclusive  */
    fun includesUpper(): Boolean {
        return includeUpper
    }

    /** Prints a user-readable version of this query.  */
    override fun toString(field: String?): String {
        val buffer = StringBuilder()
        if (field != field) {
            buffer.append(field)
            buffer.append(":")
        }
        buffer.append(if (includeLower) '[' else '{')
        // TODO: all these toStrings for queries should just output the bytes, it might not be UTF-8!
        buffer.append(
            if (lowerTerm != null)
                (if ("*" == Term.toString(lowerTerm)) "\\*" else Term.toString(
                    lowerTerm
                ))
            else
                "*"
        )
        buffer.append(" TO ")
        buffer.append(
            if (upperTerm != null)
                (if ("*" == Term.toString(upperTerm)) "\\*" else Term.toString(
                    upperTerm
                ))
            else
                "*"
        )
        buffer.append(if (includeUpper) ']' else '}')
        return buffer.toString()
    }

    override fun hashCode(): Int {
        val prime = 31
        var result = super.hashCode()
        result = prime * result + (if (includeLower) 1231 else 1237)
        result = prime * result + (if (includeUpper) 1231 else 1237)
        result = prime * result + (if (lowerTerm == null) 0 else lowerTerm.hashCode())
        result = prime * result + (if (upperTerm == null) 0 else upperTerm.hashCode())
        return result
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) return true
        if (!super.equals(obj)) return false
        if (this::class != obj!!::class) return false
        val other = obj as TermRangeQuery
        if (includeLower != other.includeLower) return false
        if (includeUpper != other.includeUpper) return false
        if (lowerTerm == null) {
            if (other.lowerTerm != null) return false
        } else if (lowerTerm != other.lowerTerm) return false
        if (upperTerm == null) {
            if (other.upperTerm != null) return false
        } else if (upperTerm != other.upperTerm) return false
        return true
    }

    companion object {
        fun toAutomaton(
            lowerTerm: BytesRef?,
            upperTerm: BytesRef?,
            includeLower: Boolean,
            includeUpper: Boolean
        ): Automaton {
            var includeLower = includeLower
            var includeUpper = includeUpper
            if (lowerTerm == null) {
                // makeBinaryInterval is more picky than we are:
                includeLower = true
            }

            if (upperTerm == null) {
                // makeBinaryInterval is more picky than we are:
                includeUpper = true
            }

            return Automata.makeBinaryInterval(
                lowerTerm,
                includeLower,
                upperTerm,
                includeUpper
            )
        }

        /** Factory that creates a new TermRangeQuery using Strings for term text.  */
        fun newStringRange(
            field: String,
            lowerTerm: String?,
            upperTerm: String?,
            includeLower: Boolean,
            includeUpper: Boolean,
            rewriteMethod: RewriteMethod = CONSTANT_SCORE_BLENDED_REWRITE
        ): TermRangeQuery {
            val lower: BytesRef? =
                if (lowerTerm == null) null else BytesRef(lowerTerm)
            val upper: BytesRef? =
                if (upperTerm == null) null else BytesRef(upperTerm)
            return TermRangeQuery(field, lower!!, upper, includeLower, includeUpper, rewriteMethod)
        }
    }
}
