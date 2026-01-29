package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.index.BaseTermsEnum
import org.gnit.lucenekmp.index.ImpactsEnum
import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.TermState
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.util.Attribute
import org.gnit.lucenekmp.util.AttributeImpl
import org.gnit.lucenekmp.util.AttributeReflector
import org.gnit.lucenekmp.util.AttributeSource
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.IOBooleanSupplier
import org.gnit.lucenekmp.util.UnicodeUtil
import org.gnit.lucenekmp.util.automaton.CompiledAutomaton
import kotlin.math.min

/**
 * Subclass of TermsEnum for enumerating all terms that are similar to the specified filter term.
 *
 *
 * Term enumerations are always ordered by [BytesRef.compareTo]. Each term in the
 * enumeration is greater than all that precede it.
 */
class FuzzyTermsEnum private constructor(
    private val terms: Terms,
    private val atts: AttributeSource,
    private val term: Term,
    automatonBuilder: () -> FuzzyAutomatonBuilder
) : BaseTermsEnum() {
    // NOTE: we can't subclass FilteredTermsEnum here because we need to sometimes change actualEnum:
    private var actualEnum: TermsEnum? = null

    // We use this to communicate the score (boost) of the current matched term we are on back to
    // MultiTermQuery.TopTermsBlendedFreqScoringRewrite that is collecting the best (default 50)
    // matched terms:
    private val boostAtt: BoostAttribute = atts.addAttribute(BoostAttribute::class)

    // MultiTermQuery.TopTermsBlendedFreqScoringRewrite tells us the worst boost still in its queue
    // using this att,
    // which we use to know when we can reduce the automaton from ed=2 to ed=1, or ed=0 if only single
    // top term is collected:
    private val maxBoostAtt: MaxNonCompetitiveBoostAttribute = atts.addAttribute(MaxNonCompetitiveBoostAttribute::class)

    private val automata: Array<CompiledAutomaton>
    private val termLength: Int

    private var bottom: Float
    private var bottomTerm: BytesRef?

    private var queuedBottom: BytesRef? = null

    // Maximum number of edits we will accept.  This is either 2 or 1 (or, degenerately, 0) passed by
    // the user originally,
    // but as we collect terms, we can lower this (e.g. from 2 to 1) if we detect that the term queue
    // is full, and all
    // collected terms are ed=1:
    private var maxEdits: Int

    /**
     * Constructor for enumeration of all terms from specified `reader` which share a
     * prefix of length `prefixLength` with `term` and which have at most `maxEdits` edits.
     *
     *
     * After calling the constructor the enumeration is already pointing to the first valid term if
     * such a term exists.
     *
     * @param terms Delivers terms.
     * @param term Pattern term.
     * @param maxEdits Maximum edit distance.
     * @param prefixLength the length of the required common prefix
     * @param transpositions whether transpositions should count as a single edit
     * @throws IOException if there is a low-level IO error
     */
    constructor(
        terms: Terms,
        term: Term,
        maxEdits: Int,
        prefixLength: Int,
        transpositions: Boolean
    ) : this(
        terms,
        AttributeSource(),
        term,
        {
            FuzzyAutomatonBuilder(
                term.text(),
                maxEdits,
                prefixLength,
                transpositions
            )
        })

    /**
     * Constructor for enumeration of all terms from specified `reader` which share a
     * prefix of length `prefixLength` with `term` and which have at most `maxEdits` edits.
     *
     *
     * After calling the constructor the enumeration is already pointing to the first valid term if
     * such a term exists.
     *
     * @param terms Delivers terms.
     * @param atts An AttributeSource used to share automata between segments
     * @param term Pattern term.
     * @param maxEdits Maximum edit distance.
     * @param prefixLength the length of the required common prefix
     * @param transpositions whether transpositions should count as a single edit
     * @throws IOException if there is a low-level IO error
     */
    internal constructor(
        terms: Terms,
        atts: AttributeSource,
        term: Term,
        maxEdits: Int,
        prefixLength: Int,
        transpositions: Boolean
    ) : this(
        terms,
        atts,
        term,
        {
            FuzzyAutomatonBuilder(
                term.text(),
                maxEdits,
                prefixLength,
                transpositions
            )
        })

    init {

        atts.addAttributeImpl(AutomatonAttributeImpl())
        val aa: AutomatonAttribute = atts.addAttribute<AutomatonAttribute>(AutomatonAttribute::class)
        aa.init(automatonBuilder)

        this.automata = aa.automata!!
        this.termLength = aa.termLength
        this.maxEdits = this.automata.size - 1

        bottom = maxBoostAtt.maxNonCompetitiveBoost
        bottomTerm = maxBoostAtt.competitiveTerm
        bottomChanged(null)
    }

    /**
     * Sets the maximum non-competitive boost, which may allow switching to a lower max-edit automaton
     * at run time
     */
    fun setMaxNonCompetitiveBoost(boost: Float) {
        this.maxBoostAtt.maxNonCompetitiveBoost = boost
    }

    val boost: Float
        /** Gets the boost of the current term  */
        get() = boostAtt.boost

    /** return an automata-based enum for matching up to editDistance from lastTerm, if possible  */
    @Throws(IOException::class)
    private fun getAutomatonEnum(
        editDistance: Int,
        lastTerm: BytesRef?
    ): TermsEnum {
        assert(editDistance < automata.size)
        val compiled: CompiledAutomaton = automata[editDistance]
        val initialSeekTerm: BytesRef? = if (lastTerm == null) {
            // This is the first enum we are pulling:
            null
        } else {
            // We are pulling this enum (e.g., ed=1) after iterating for a while already (e.g., ed=2):
            compiled.floor(lastTerm, BytesRefBuilder())
        }
        return terms.intersect(compiled, initialSeekTerm)
    }

    /**
     * fired when the max non-competitive boost has changed. this is the hook to swap in a smarter
     * actualEnum.
     */
    @Throws(IOException::class)
    private fun bottomChanged(lastTerm: BytesRef?) {
        val oldMaxEdits = maxEdits

        // true if the last term encountered is lexicographically equal or after the bottom term in the
        // PQ
        val termAfter =
            bottomTerm == null || (lastTerm != null && lastTerm >= bottomTerm!!)

        // as long as the max non-competitive boost is >= the max boost
        // for some edit distance, keep dropping the max edit distance.
        while (maxEdits > 0) {
            val maxBoost = 1.0f - (maxEdits.toFloat() / termLength.toFloat())
            if (bottom < maxBoost || (bottom == maxBoost && !termAfter)) {
                break
            }
            maxEdits--
        }

        if (oldMaxEdits != maxEdits || lastTerm == null) {
            // This is a very powerful optimization: the maximum edit distance has changed.  This happens
            // because we collect only the top scoring
            // N (= 50, by default) terms, and if e.g. maxEdits=2, and the queue is now full of matching
            // terms, and we notice that the worst entry
            // in that queue is ed=1, then we can switch the automata here to ed=1 which is a big speedup.
            actualEnum = getAutomatonEnum(maxEdits, lastTerm)
        }
    }

    @Throws(IOException::class)
    override fun next(): BytesRef? {
        if (queuedBottom != null) {
            bottomChanged(queuedBottom)
            queuedBottom = null
        }

        val term = actualEnum!!.next()
        if (term == null) {
            // end
            return null
        }

        var ed = maxEdits

        // we know the outer DFA always matches.
        // now compute exact edit distance
        while (ed > 0) {
            if (matches(term, ed - 1)) {
                ed--
            } else {
                break
            }
        }

        if (ed == 0) { // exact match
            boostAtt.boost = 1.0f
        } else {
            val codePointCount: Int = UnicodeUtil.codePointCount(term)
            val minTermLength = min(codePointCount, termLength)

            val similarity = 1.0f - ed.toFloat() / minTermLength.toFloat()
            boostAtt.boost = similarity
        }

        val bottom: Float = maxBoostAtt.maxNonCompetitiveBoost
        val bottomTerm: BytesRef? = maxBoostAtt.competitiveTerm
        if (bottom != this.bottom || bottomTerm !== this.bottomTerm) {
            this.bottom = bottom
            this.bottomTerm = bottomTerm

            // clone the term before potentially doing something with it
            // this is a rare but wonderful occurrence anyway

            // We must delay bottomChanged until the next next() call otherwise we mess up docFreq(),
            // etc., for the current term:
            queuedBottom = BytesRef.deepCopyOf(term)
        }

        return term
    }

    /** returns true if term is within k edits of the query term  */
    private fun matches(termIn: BytesRef, k: Int): Boolean {
        return if (k == 0)
            (termIn == term.bytes())
        else
            automata[k].runAutomaton!!.run(termIn.bytes, termIn.offset, termIn.length)
    }

    // proxy all other enum calls to the actual enum
    @Throws(IOException::class)
    override fun docFreq(): Int {
        return actualEnum!!.docFreq()
    }

    @Throws(IOException::class)
    override fun totalTermFreq(): Long {
        return actualEnum!!.totalTermFreq()
    }

    @Throws(IOException::class)
    override fun postings(
        reuse: PostingsEnum?,
        flags: Int
    ): PostingsEnum {
        return actualEnum!!.postings(reuse, flags)!!
    }

    @Throws(IOException::class)
    override fun impacts(flags: Int): ImpactsEnum {
        return actualEnum!!.impacts(flags)
    }

    @Throws(IOException::class)
    override fun seekExact(term: BytesRef, state: TermState) {
        actualEnum!!.seekExact(term, state)
    }

    @Throws(IOException::class)
    override fun termState(): TermState {
        return actualEnum!!.termState()
    }

    @Throws(IOException::class)
    override fun ord(): Long {
        return actualEnum!!.ord()
    }

    override fun attributes(): AttributeSource {
        return atts
    }

    @Throws(IOException::class)
    override fun seekExact(text: BytesRef): Boolean {
        return actualEnum!!.seekExact(text)
    }

    @Throws(IOException::class)
    override fun prepareSeekExact(text: BytesRef): IOBooleanSupplier {
        return actualEnum!!.prepareSeekExact(text)!!
    }

    @Throws(IOException::class)
    override fun seekCeil(text: BytesRef): SeekStatus {
        return actualEnum!!.seekCeil(text)
    }

    @Throws(IOException::class)
    override fun seekExact(ord: Long) {
        actualEnum!!.seekExact(ord)
    }

    @Throws(IOException::class)
    override fun term(): BytesRef {
        return actualEnum!!.term()!!
    }

    /**
     * Thrown to indicate that there was an issue creating a fuzzy query for a given term. Typically
     * occurs with terms longer than 220 UTF-8 characters, but also possible with shorter terms
     * consisting of UTF-32 code points.
     */
    class FuzzyTermsException internal constructor(term: String, cause: Throwable) :
        RuntimeException("Term too complex: $term", cause)

    /**
     * Used for sharing automata between segments
     *
     *
     * Levenshtein automata are large and expensive to build; we don't want to build them directly
     * on the query because this can blow up caches that use queries as keys; we also don't want to
     * rebuild them for every segment. This attribute allows the FuzzyTermsEnum to build the automata
     * once for its first segment and then share them for subsequent segment calls.
     */
    private interface AutomatonAttribute : Attribute {
        val automata: Array<CompiledAutomaton>?

        val termLength: Int

        fun init(builder: () -> FuzzyAutomatonBuilder)
    }

    private class AutomatonAttributeImpl : AttributeImpl(), AutomatonAttribute {
        override var automata: Array<CompiledAutomaton>? = null
        override var termLength = 0

        /*override fun getAutomata(): Array<CompiledAutomaton> {
            return automata
        }*/

        /*override fun getTermLength(): Int {
            return termLength
        }*/

        override fun init(supplier: () -> FuzzyAutomatonBuilder) {
            if (automata != null) {
                return
            }
            val builder: FuzzyAutomatonBuilder = supplier()
            this.termLength = builder.termLength
            this.automata = builder.buildAutomatonSet()
        }

        override fun clear() {
            this.automata = null
        }

        override fun reflectWith(reflector: AttributeReflector) {
            throw UnsupportedOperationException()
        }

        override fun copyTo(target: AttributeImpl) {
            throw UnsupportedOperationException()
        }

        override fun newInstance(): AttributeImpl {
            throw UnsupportedOperationException(
                "AutomatonAttributeImpl cannot be instantiated directly, use init() instead"
            )
        }
    }
}
