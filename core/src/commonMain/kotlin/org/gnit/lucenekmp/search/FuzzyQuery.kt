package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.index.SingleTermsEnum
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.util.AttributeSource
import org.gnit.lucenekmp.util.automaton.CompiledAutomaton
import org.gnit.lucenekmp.util.automaton.LevenshteinAutomata
import kotlin.math.min

/**
 * Implements the fuzzy search query. The similarity measurement is based on the Damerau-Levenshtein
 * (optimal string alignment) algorithm, though you can explicitly choose classic Levenshtein by
 * passing `false` to the `transpositions` parameter.
 *
 *
 * This query uses [MultiTermQuery.TopTermsBlendedFreqScoringRewrite] as default. So terms
 * will be collected and scored according to their edit distance. Only the top terms are used for
 * building the [BooleanQuery]. It is not recommended to change the rewrite mode for fuzzy
 * queries.
 *
 *
 * At most, this query will match terms up to {@value
 * * LevenshteinAutomata#MAXIMUM_SUPPORTED_DISTANCE} edits. Higher
 * distances (especially with transpositions enabled), are generally not useful and will match a
 * significant amount of the term dictionary. If you really want this, consider using an n-gram
 * indexing technique (such as the SpellChecker in the [suggest module]({@docRoot}/../suggest/overview-summary.html)) instead.
 *
 *
 * NOTE: terms of length 1 or 2 will sometimes not match because of how the scaled distance
 * between two terms is computed. For a term to match, the edit distance between the terms must be
 * less than the minimum length term (either the input term, or the candidate term). For example,
 * FuzzyQuery on term "abcd" with maxEdits=2 will not match an indexed term "ab", and FuzzyQuery on
 * term "a" with maxEdits=2 will not match an indexed term "abc".
 */
class FuzzyQuery(
    term: Term,
    maxEdits: Int = defaultMaxEdits,
    prefixLength: Int = defaultPrefixLength,
    maxExpansions: Int = defaultMaxExpansions,
    transpositions: Boolean = defaultTranspositions,
    rewriteMethod: RewriteMethod = defaultRewriteMethod(maxExpansions)
) : MultiTermQuery(term.field(), rewriteMethod) {
    /**
     * @return the maximum number of edit distances allowed for this query to match.
     */
    val maxEdits: Int
    private val maxExpansions: Int

    /**
     * Returns true if transpositions should be treated as a primitive edit operation. If this is
     * false, comparisons will implement the classic Levenshtein algorithm.
     */
    val transpositions: Boolean

    /**
     * Returns the non-fuzzy prefix length. This is the number of characters at the start of a term
     * that must be identical (not fuzzy) to the query term if the query is to match that term.
     */
    val prefixLength: Int
    private val term: Term

    /**
     * Create a new FuzzyQuery that will match terms with an edit distance of at most `maxEdits
    ` *  to `term`. If a `prefixLength` &gt; 0 is specified, a common
     * prefix of that length is also required.
     *
     * @param term the term to search for
     * @param maxEdits must be `>= 0` and `<=` [     ][LevenshteinAutomata.MAXIMUM_SUPPORTED_DISTANCE].
     * @param prefixLength length of common (non-fuzzy) prefix
     * @param maxExpansions the maximum number of terms to match. If this number is greater than
     * [IndexSearcher.getMaxClauseCount] when the query is rewritten, then the
     * maxClauseCount will be used instead.
     * @param transpositions true if transpositions should be treated as a primitive edit operation.
     * If this is false, comparisons will implement the classic Levenshtein algorithm.
     * @param rewriteMethod the rewrite method to use to build the final query
     */
    /**
     * Calls [.FuzzyQuery] FuzzyQuery(term, maxEdits,
     * prefixLength, maxExpansions, defaultRewriteMethod(maxExpansions))
     */
    /**
     * Calls [FuzzyQuery(term, maxEdits, prefixLength,][.FuzzyQuery].
     */
    /** Calls [FuzzyQuery(term, maxEdits, defaultPrefixLength)][.FuzzyQuery].  */
    /** Calls [FuzzyQuery(term, defaultMaxEdits)][.FuzzyQuery].  */
    init {
        require(!(maxEdits < 0 || maxEdits > LevenshteinAutomata.MAXIMUM_SUPPORTED_DISTANCE)) { "maxEdits must be between 0 and " + LevenshteinAutomata.MAXIMUM_SUPPORTED_DISTANCE }
        require(prefixLength >= 0) { "prefixLength cannot be negative." }
        require(maxExpansions > 0) { "maxExpansions must be positive." }

        this.term = term
        this.maxEdits = maxEdits
        this.prefixLength = prefixLength
        this.transpositions = transpositions
        this.maxExpansions = maxExpansions
    }

    val automata: CompiledAutomaton
        /** Returns the compiled automata used to match terms  */
        get() = getFuzzyAutomaton(term.text(), maxEdits, prefixLength, transpositions)

    override fun visit(visitor: QueryVisitor) {
        if (visitor.acceptField(field)) {
            visitor.consumeTermsMatching(this, term.field()) { this.automata.runAutomaton!! }
        }
    }

    @Throws(IOException::class)
    override fun getTermsEnum(
        terms: Terms,
        atts: AttributeSource
    ): TermsEnum {
        if (maxEdits == 0) { // can only match if it's exact
            return SingleTermsEnum(terms.iterator(), term.bytes())
        }
        return FuzzyTermsEnum(terms, atts, getTerm(), maxEdits, prefixLength, transpositions)
    }

    /** Returns the pattern term.  */
    fun getTerm(): Term {
        return term
    }

    override fun toString(field: String?): String {
        val buffer = StringBuilder()
        if (term.field() != field) {
            buffer.append(term.field())
            buffer.append(":")
        }
        buffer.append(term.text())
        buffer.append('~')
        buffer.append(maxEdits)
        return buffer.toString()
    }

    override fun hashCode(): Int {
        val prime = 31
        var result = super.hashCode()
        result = prime * result + maxEdits
        result = prime * result + prefixLength
        result = prime * result + maxExpansions
        result = prime * result + (if (transpositions) 0 else 1)
        result = prime * result + (if (term == null) 0 else term.hashCode())
        return result
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) return true
        if (!super.equals(obj)) return false
        if (this::class != obj!!::class) return false
        val other = obj as FuzzyQuery
        return maxEdits == other.maxEdits && prefixLength == other.prefixLength && maxExpansions == other.maxExpansions && transpositions == other.transpositions && term == other.term
    }

    companion object {
        const val defaultMaxEdits: Int = LevenshteinAutomata.MAXIMUM_SUPPORTED_DISTANCE
        const val defaultPrefixLength: Int = 0
        const val defaultMaxExpansions: Int = 50
        const val defaultTranspositions: Boolean = true

        /** Creates a default top-terms blended frequency scoring rewrite with the given max expansions  */
        fun defaultRewriteMethod(maxExpansions: Int): RewriteMethod {
            return TopTermsBlendedFreqScoringRewrite(maxExpansions)
        }

        /**
         * Returns the [CompiledAutomaton] internally used by [FuzzyQuery] to match terms.
         * This is a very low-level method and may no longer exist in case the implementation of
         * fuzzy-matching changes in the future.
         *
         * @lucene.internal
         * @param term the term to search for
         * @param maxEdits must be `>= 0` and `<=` [     ][LevenshteinAutomata.MAXIMUM_SUPPORTED_DISTANCE].
         * @param prefixLength length of common (non-fuzzy) prefix
         * @param transpositions true if transpositions should be treated as a primitive edit operation.
         * If this is false, comparisons will implement the classic Levenshtein algorithm.
         * @return A [CompiledAutomaton] that matches terms that satisfy input parameters.
         */
        fun getFuzzyAutomaton(
            term: String, maxEdits: Int, prefixLength: Int, transpositions: Boolean
        ): CompiledAutomaton {
            val builder = FuzzyAutomatonBuilder(term, maxEdits, prefixLength, transpositions)
            return builder.buildMaxEditAutomaton()
        }

        /**
         * Helper function to convert from "minimumSimilarity" fractions to raw edit distances.
         *
         * @param minimumSimilarity scaled similarity
         * @param termLen length (in unicode codepoints) of the term.
         * @return equivalent number of maxEdits
         */
        fun floatToEdits(minimumSimilarity: Float, termLen: Int): Int {
            return if (minimumSimilarity >= 1f) {
                min(
                    minimumSimilarity,
                    LevenshteinAutomata.MAXIMUM_SUPPORTED_DISTANCE.toFloat()
                ).toInt()
            } else if (minimumSimilarity == 0.0f) {
                0 // 0 means exact, not infinite # of edits!
            } else {
                min(
                    ((1.0 - minimumSimilarity) * termLen).toInt(),
                    LevenshteinAutomata.MAXIMUM_SUPPORTED_DISTANCE
                )
            }
        }
    }
}
