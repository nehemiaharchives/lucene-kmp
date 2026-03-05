package org.gnit.lucenekmp.index

import io.github.oshai.kotlinlogging.KotlinLogging
import okio.IOException
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.TimeUnit
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.IOBooleanSupplier
import org.gnit.lucenekmp.util.IOSupplier


/**
 * Maintains a [IndexReader] [TermState] view over [IndexReader] instances
 * containing a single term. The [TermStates] doesn't track if the given [TermState]
 * objects are valid, neither if the [TermState] instances refer to the same terms in the
 * associated readers.
 *
 * @lucene.experimental
 */
class TermStates private constructor(term: Term?, context: IndexReaderContext?) {
    // Important: do NOT keep hard references to index readers
    private val topReaderContextIdentity: Any
    private val states: Array<TermState?>
    private val term: Term? // null if stats are to be used
    private var docFreq: Int
    private var totalTermFreq: Long

    // public static boolean DEBUG = BlockTreeTermsWriter.DEBUG;
    init {
        require(context != null && context.isTopLevel)
        topReaderContextIdentity = context.identity
        docFreq = 0
        totalTermFreq = 0
        states = arrayOfNulls<TermState>(context.leaves().size)
        this.term = term
    }

    /** Creates an empty [TermStates] from a [IndexReaderContext]  */
    constructor(context: IndexReaderContext?) : this(null, context)

    /**
     * Expert: Return whether this [TermStates] was built for the given [ ]. This is typically used for assertions.
     *
     * @lucene.internal
     */
    fun wasBuiltFor(context: IndexReaderContext): Boolean {
        return topReaderContextIdentity === context.identity
    }

    /** Creates a [TermStates] with an initial [TermState], [IndexReader] pair.  */
    constructor(context: IndexReaderContext, state: TermState, ord: Int, docFreq: Int, totalTermFreq: Long) : this(
        null,
        context
    ) {
        register(state, ord, docFreq, totalTermFreq)
    }

    private class PendingTermLookup(val termsEnum: TermsEnum, val supplier: IOBooleanSupplier)

    /** Clears the [TermStates] internal state and removes all registered [TermState]s  */
    fun clear() {
        docFreq = 0
        totalTermFreq = 0
        /*java.util.Arrays.fill(states, null)*/
        // TODO fill with null ?
        states.fill(null)
    }

    /**
     * Registers and associates a [TermState] with an leaf ordinal. The leaf ordinal should be
     * derived from a [IndexReaderContext]'s leaf ord.
     */
    fun register(
        state: TermState, ord: Int, docFreq: Int, totalTermFreq: Long
    ) {
        register(state, ord)
        accumulateStatistics(docFreq, totalTermFreq)
    }

    /**
     * Expert: Registers and associates a [TermState] with an leaf ordinal. The leaf ordinal
     * should be derived from a [IndexReaderContext]'s leaf ord. On the contrary to [ ][.register] this method does NOT update term statistics.
     */
    fun register(state: TermState, ord: Int) {
        checkNotNull(state) { "state must not be null" }
        require(ord >= 0 && ord < states.size)
        require(states[ord] == null) { "state for ord: $ord already registered" }
        states[ord] = state
    }

    /** Expert: Accumulate term statistics.  */
    fun accumulateStatistics(docFreq: Int, totalTermFreq: Long) {
        require(docFreq >= 0)
        require(totalTermFreq >= 0)
        require(docFreq <= totalTermFreq)
        this.docFreq += docFreq
        this.totalTermFreq += totalTermFreq
    }

    /**
     * Returns a [Supplier] for a [TermState] for the given [LeafReaderContext].
     * This may return `null` if some cheap checks help figure out that this term doesn't exist
     * in this leaf. The [Supplier] may then also return `null` if the term doesn't exist.
     *
     *
     * Calling this method typically schedules some I/O in the background, so it is recommended to
     * retrieve [Supplier]s across all required terms first before calling [Supplier.get]
     * on all [Supplier]s so that the I/O for these terms can be performed in parallel.
     *
     * @param ctx the [LeafReaderContext] to get the [TermState] for.
     * @return a Supplier for a TermState.
     */
    @Throws(IOException::class)
    fun get(ctx: LeafReaderContext): IOSupplier<TermState?>? {
        require(ctx.ord >= 0 && ctx.ord < states.size)
        val probeEnabled = logger.isDebugEnabled()
        val stats = if (probeEnabled) getPerfStats else null
        if (stats != null) {
            stats.calls++
        }
        if (term == null) {
            if (stats != null) {
                stats.termNullCalls++
            }
            return if (states[ctx.ord] == null) {
                null
            } else {
                IOSupplier { states[ctx.ord] }
            }
        }
        if (states[ctx.ord] == null && stats != null) {
            stats.coldPathCalls++
        }
        if (states[ctx.ord] == null) {
            var stepStartNs = if (stats != null) System.nanoTime() else 0L
            val terms: Terms? = ctx.reader().terms(term.field())
            if (stats != null) {
                stats.termsLookupNs += System.nanoTime() - stepStartNs
            }
            if (terms == null) {
                this.states[ctx.ord] = EMPTY_TERMSTATE
                return null
            }
            stepStartNs = if (stats != null) System.nanoTime() else 0L
            val termsEnum = terms.iterator()
            if (stats != null) {
                stats.iteratorNs += System.nanoTime() - stepStartNs
            }
            stepStartNs = if (stats != null) System.nanoTime() else 0L
            val termExistsSupplier: IOBooleanSupplier? = termsEnum.prepareSeekExact(term.bytes())
            if (stats != null) {
                stats.prepareSeekNs += System.nanoTime() - stepStartNs
            }
            if (termExistsSupplier == null) {
                this.states[ctx.ord] = EMPTY_TERMSTATE
                return null
            }
            return IOSupplier {
                var supplierStepStartNs = if (stats != null) System.nanoTime() else 0L
                if (this.states[ctx.ord] == null) {
                    var state: TermState? = null
                    var exists = false
                    var existsCheckNs = 0L
                    if (stats != null) {
                        supplierStepStartNs = System.nanoTime()
                    }
                    exists = termExistsSupplier.get()
                    if (stats != null) {
                        existsCheckNs = System.nanoTime() - supplierStepStartNs
                    }
                    if (exists) {
                        if (stats != null) {
                            supplierStepStartNs = System.nanoTime()
                        }
                        state = termsEnum.termState()
                        if (stats != null) {
                            stats.termStateNs += System.nanoTime() - supplierStepStartNs
                        }
                        this.states[ctx.ord] = state
                    } else {
                        this.states[ctx.ord] = EMPTY_TERMSTATE
                    }
                    if (stats != null) {
                        stats.termExistsCheckNs += existsCheckNs
                    }
                }
                val state = this.states[ctx.ord]
                if (state === EMPTY_TERMSTATE) {
                    if (stats != null) {
                        stats.supplierCalls++
                        stats.supplierNs += System.nanoTime() - supplierStepStartNs
                    }
                    return@IOSupplier null
                }
                if (stats != null) {
                    stats.supplierCalls++
                    stats.supplierNs += System.nanoTime() - supplierStepStartNs
                }
                state
            }
        }
        val state = this.states[ctx.ord]
        if (state === EMPTY_TERMSTATE) {
            if (stats != null) {
                stats.cacheHitCalls++
            }
            return null
        }
        if (stats != null) {
            stats.cacheHitCalls++
        }
        return IOSupplier { state }
    }

    /**
     * Returns the accumulated document frequency of all [TermState] instances passed to [ ][.register].
     *
     * @return the accumulated document frequency of all [TermState] instances passed to [     ][.register].
     */
    fun docFreq(): Int {
        check(term == null) { "Cannot call docFreq() when needsStats=false" }
        return docFreq
    }

    /**
     * Returns the accumulated term frequency of all [TermState] instances passed to [ ][.register].
     *
     * @return the accumulated term frequency of all [TermState] instances passed to [     ][.register].
     */
    fun totalTermFreq(): Long {
        check(term == null) { "Cannot call totalTermFreq() when needsStats=false" }
        return totalTermFreq
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("TermStates\n")
        for (termState in states) {
            sb.append("  state=")
            sb.append(termState)
            sb.append('\n')
        }

        return sb.toString()
    }

    private class GetPerfStats {
        var calls: Long = 0
        var coldPathCalls: Long = 0
        var cacheHitCalls: Long = 0
        var termNullCalls: Long = 0
        var termsLookupNs: Long = 0
        var iteratorNs: Long = 0
        var prepareSeekNs: Long = 0
        var supplierCalls: Long = 0
        var supplierNs: Long = 0
        var termExistsCheckNs: Long = 0
        var termStateNs: Long = 0

        fun reset() {
            calls = 0
            coldPathCalls = 0
            cacheHitCalls = 0
            termNullCalls = 0
            termsLookupNs = 0
            iteratorNs = 0
            prepareSeekNs = 0
            supplierCalls = 0
            supplierNs = 0
            termExistsCheckNs = 0
            termStateNs = 0
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
        private val getPerfStats = GetPerfStats()

        fun resetGetPerfStats() {
            getPerfStats.reset()
        }

        fun logGetPerfStats(tag: String = "") {
            if (!logger.isDebugEnabled()) {
                return
            }
            val calls = getPerfStats.calls
            if (calls == 0L) {
                logger.debug { "phase=termStates.get.stats tag=$tag calls=0" }
                return
            }
            fun toMs(ns: Long): Long = TimeUnit.NANOSECONDS.toMillis(ns)
            logger.debug {
                "phase=termStates.get.stats tag=$tag calls=$calls " +
                    "coldPathCalls=${getPerfStats.coldPathCalls} " +
                    "cacheHitCalls=${getPerfStats.cacheHitCalls} " +
                    "termNullCalls=${getPerfStats.termNullCalls} " +
                    "termsLookupMs=${toMs(getPerfStats.termsLookupNs)} " +
                    "iteratorMs=${toMs(getPerfStats.iteratorNs)} " +
                    "prepareSeekMs=${toMs(getPerfStats.prepareSeekNs)} " +
                    "supplierCalls=${getPerfStats.supplierCalls} " +
                    "supplierMs=${toMs(getPerfStats.supplierNs)} " +
                    "termExistsCheckMs=${toMs(getPerfStats.termExistsCheckNs)} " +
                    "termStateMs=${toMs(getPerfStats.termStateNs)}"
            }
        }

        private val EMPTY_TERMSTATE: TermState = object : TermState() {
            override fun copyFrom(other: TermState) {}
        }

        /**
         * Creates a [TermStates] from a top-level [IndexReaderContext] and the given [ ]. This method will lookup the given term in all context's leaf readers and register each
         * of the readers containing the term in the returned [TermStates] using the leaf reader's
         * ordinal.
         *
         *
         * Note: the given context must be a top-level context.
         *
         * @param needsStats if `true` then all leaf contexts will be visited up-front to collect
         * term statistics. Otherwise, the [TermState] objects will be built only when requested
         */
        @Throws(IOException::class)
        fun build(indexSearcher: IndexSearcher, term: Term, needsStats: Boolean): TermStates {
            val context: IndexReaderContext = checkNotNull(indexSearcher.topReaderContext)
            val perReaderTermState = TermStates(if (needsStats) null else term, context)
            if (needsStats) {
                var pendingTermLookups = kotlin.arrayOfNulls<PendingTermLookup>(0)
                for (ctx in context.leaves()) {
                    val terms = Terms.getTerms(ctx.reader(), term.field())
                    val termsEnum = terms.iterator()
                    // Schedule the I/O in the terms dictionary in the background.
                    val termExistsSupplier: IOBooleanSupplier? = termsEnum.prepareSeekExact(term.bytes())
                    if (termExistsSupplier != null) {
                        pendingTermLookups = ArrayUtil.grow(pendingTermLookups, ctx.ord + 1)
                        pendingTermLookups[ctx.ord] = PendingTermLookup(termsEnum, termExistsSupplier)
                    }
                }
                for (ord in pendingTermLookups.indices) {
                    val pendingTermLookup = pendingTermLookups[ord]
                    if (pendingTermLookup != null && pendingTermLookup.supplier.get()) {
                        val termsEnum = pendingTermLookup.termsEnum
                        perReaderTermState.register(
                            termsEnum.termState(), ord, termsEnum.docFreq(), termsEnum.totalTermFreq()
                        )
                    }
                }
            }
            return perReaderTermState
        }
    }
}
