package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.index.Impact
import org.gnit.lucenekmp.index.Impacts
import org.gnit.lucenekmp.index.ImpactsSource
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.internal.hppc.IntHashSet
import org.gnit.lucenekmp.search.similarities.Similarity.SimScorer
import org.gnit.lucenekmp.util.FixedBitSet
import okio.IOException
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.assert
import kotlin.math.max
import kotlin.math.min

/**
 * Find all slop-valid position-combinations (matches) encountered while traversing/hopping the
 * PhrasePositions. <br></br>
 * The sloppy frequency contribution of a match depends on the distance: <br></br>
 * - highest freq for distance=0 (exact match). <br></br>
 * - freq gets lower as distance gets higher. <br></br>
 * Example: for query "a b"~2, a document "x a b a y" can be matched twice: once for "a b"
 * (distance=0), and once for "b a" (distance=2). <br></br>
 * Possibly not all valid combinations are encountered, because for efficiency we always propagate
 * the least PhrasePosition. This allows to base on PriorityQueue and move forward faster. As
 * result, for example, document "a b c b a" would score differently for queries "a b c"~4 and "c b
 * a"~4, although they really are equivalent. Similarly, for doc "a b c b a f g", query "c b"~2
 * would get same score as "g f"~2, although "c b"~2 could be matched twice. We may want to fix this
 * in the future (currently not, for performance reasons).
 *
 * @lucene.internal
 */
class SloppyPhraseMatcher(
    postings: Array<PhraseQuery.PostingsAndFreq>,
    private val slop: Int,
    scoreMode: ScoreMode,
    scorer: SimScorer,
    matchCost: Float,
    private val captureLeadMatch: Boolean
) : PhraseMatcher(matchCost) {
    private val phrasePositions: Array<PhrasePositions> = Array(postings.size) { i ->
        PhrasePositions(
            postings[i].postings,
            postings[i].position,
            i,
            postings[i].terms
        )
    }

    private val numPostings: Int = postings.size
    private val pq: PhraseQueue = PhraseQueue(postings.size) // for advancing min position

    private val approximation: DocIdSetIterator = ConjunctionUtils.intersectIterators(postings.map { p: PhraseQuery.PostingsAndFreq -> p.postings }
        .toMutableList())
    private val impactsApproximation: ImpactsDISI

    private var end = 0 // current largest phrase position

    private var leadPosition = 0
    private var leadOffset = 0
    private var leadEndOffset = 0
    private var leadOrd = 0

    private var hasRpts = false // flag indicating that there are repetitions (as checked in first candidate doc)
    private var checkedRpts = false // flag to only check for repetitions in first candidate doc
    private var hasMultiTermRpts = false //
    private lateinit var rptGroups: Array<Array<PhrasePositions>>

    // in each group are PPs that repeats each other (i.e. same term), sorted by
    // (query) offset
    private lateinit var rptStack: Array<PhrasePositions> // temporary stack for switching colliding repeating pps

    private var positioned = false
    private var matchLength = 0

    init {
        // What would be a good upper bound of the sloppy frequency A sum of the
        // sub frequencies would be correct, but it is usually so much higher than
        // the actual sloppy frequency that it doesn't help skip irrelevant
        // documents. As a consequence for now, sloppy phrase queries use dummy
        // impacts:
        val impactsSource: ImpactsSource =
            object : ImpactsSource {
                override val impacts: Impacts
                    get() = object : Impacts() {
                        override fun numLevels(): Int {
                            return 1
                        }

                        override fun getImpacts(level: Int): MutableList<Impact> {
                            return mutableListOf(
                                Impact(
                                    Int.Companion.MAX_VALUE,
                                    1L
                                )
                            )
                        }

                        override fun getDocIdUpTo(level: Int): Int {
                            return DocIdSetIterator.NO_MORE_DOCS
                        }
                    }

                @Throws(IOException::class)
                override fun advanceShallow(target: Int) {
                }
            }
        impactsApproximation = ImpactsDISI(approximation, MaxScoreCache(impactsSource, scorer))
    }

    override fun approximation(): DocIdSetIterator {
        return approximation
    }

    override fun impactsApproximation(): ImpactsDISI {
        return impactsApproximation
    }

    @Throws(IOException::class)
    override fun maxFreq(): Float {
        // every term position in each postings list can be at the head of at most
        // one matching phrase, so the maximum possible phrase freq is the sum of
        // the freqs of the postings lists.
        var maxFreq = 0f
        for (phrasePosition in phrasePositions) {
            maxFreq += phrasePosition.postings.freq().toFloat()
        }
        return maxFreq
    }

    @Throws(IOException::class)
    override fun reset() {
        this.positioned = initPhrasePositions()
        this.matchLength = Int.Companion.MAX_VALUE
        this.leadPosition = Int.Companion.MAX_VALUE
    }

    override fun sloppyWeight(): Float {
        return 1f / (1f + matchLength)
    }

    @Throws(IOException::class)
    override fun nextMatch(): Boolean {
        if (!positioned) {
            return false
        }
        var pp: PhrasePositions = checkNotNull(pq.pop())
        captureLead(pp)
        matchLength = end - pp.position
        var next: Int = pq.top().position
        while (advancePP(pp)) {
            if (hasRpts && !advanceRpts(pp)) {
                break // pps exhausted
            }
            if (pp.position > next) { // done minimizing current match-length
                pq.add(pp)
                if (matchLength <= slop) {
                    return true
                }
                pp = pq.pop()!!
                next = pq.top().position
                checkNotNull(pp) // if the pq is not full, then positioned == false
                matchLength = end - pp.position
            } else {
                val matchLength2: Int = end - pp.position
                if (matchLength2 < matchLength) {
                    matchLength = matchLength2
                }
            }
            captureLead(pp)
        }
        positioned = false
        return matchLength <= slop
    }

    @Throws(IOException::class)
    private fun captureLead(pp: PhrasePositions) {
        if (!captureLeadMatch) {
            return
        }
        leadOrd = pp.ord
        leadPosition = pp.position + pp.offset
        leadOffset = pp.postings.startOffset()
        leadEndOffset = pp.postings.endOffset()
    }

    override fun startPosition(): Int {
        // when a match is detected, the top postings is advanced until it has moved
        // beyond its successor, to ensure that the match is of minimal width.  This
        // means that we need to record the lead position before it is advanced.
        // However, the priority queue doesn't guarantee that the top postings is in fact the
        // earliest in the list, so we need to cycle through all terms to check.
        // this is slow, but Matches is slow anyway...
        var leadPosition = this.leadPosition
        for (pp in phrasePositions) {
            leadPosition = min(leadPosition, pp.position + pp.offset)
        }
        return leadPosition
    }

    override fun endPosition(): Int {
        var endPosition = leadPosition
        for (pp in phrasePositions) {
            if (pp.ord != leadOrd) {
                endPosition = max(endPosition, pp.position + pp.offset)
            }
        }
        return endPosition
    }

    @Throws(IOException::class)
    override fun startOffset(): Int {
        // when a match is detected, the top postings is advanced until it has moved
        // beyond its successor, to ensure that the match is of minimal width.  This
        // means that we need to record the lead offset before it is advanced.
        // However, the priority queue doesn't guarantee that the top postings is in fact the
        // earliest in the list, so we need to cycle through all terms to check
        // this is slow, but Matches is slow anyway...
        var leadOffset = this.leadOffset
        for (pp in phrasePositions) {
            leadOffset = min(leadOffset, pp.postings.startOffset())
        }
        return leadOffset
    }

    @Throws(IOException::class)
    override fun endOffset(): Int {
        var endOffset = leadEndOffset
        for (pp in phrasePositions) {
            if (pp.ord != leadOrd) {
                endOffset = max(endOffset, pp.postings.endOffset())
            }
        }
        return endOffset
    }

    /** advance a PhrasePosition and update 'end', return false if exhausted  */
    @Throws(IOException::class)
    private fun advancePP(pp: PhrasePositions): Boolean {
        if (!pp.nextPosition()) {
            return false
        }
        if (pp.position > end) {
            end = pp.position
        }
        return true
    }

    /**
     * pp was just advanced. If that caused a repeater collision, resolve by advancing the lesser of
     * the two colliding pps. Note that there can only be one collision, as by the initialization
     * there were no collisions before pp was advanced.
     */
    @Throws(IOException::class)
    private fun advanceRpts(pp: PhrasePositions): Boolean {
        var pp: PhrasePositions = pp
        if (pp.rptGroup < 0) {
            return true // not a repeater
        }
        val rg: Array<PhrasePositions> = rptGroups[pp.rptGroup]
        var bits = FixedBitSet(rg.size) // for re-queuing after collisions are resolved
        val k0: Int = pp.rptInd
        var k: Int
        while ((collide(pp).also { k = it }) >= 0) {
            pp = lesser(pp, rg[k]) // always advance the lesser of the (only) two colliding pps
            if (!advancePP(pp)) {
                return false // exhausted
            }
            if (k != k0) { // careful: mark only those currently in the queue
                bits = FixedBitSet.ensureCapacity(bits, k)
                bits.set(k) // mark that pp2 need to be re-queued
            }
        }
        // collisions resolved, now re-queue
        // empty (partially) the queue until seeing all pps advanced for resolving collisions
        var n = 0
        // TODO would be good if we can avoid calling cardinality() in each iteration!
        val numBits: Int = bits.length() // larges bit we set
        while (bits.cardinality() > 0) {
            val pp2: PhrasePositions = pq.pop()!!
            rptStack[n++] = pp2
            if (pp2.rptGroup >= 0 && pp2.rptInd < numBits // this bit may not have been set
                && bits.get(pp2.rptInd)
            ) {
                bits.clear(pp2.rptInd)
            }
        }
        // add back to queue
        for (i in n - 1 downTo 0) {
            pq.add(rptStack[i])
        }
        return true
    }

    /** compare two pps, but only by position and offset  */
    private fun lesser(
        pp: PhrasePositions,
        pp2: PhrasePositions
    ): PhrasePositions {
        if (pp.position < pp2.position || (pp.position == pp2.position && pp.offset < pp2.offset)) {
            return pp
        }
        return pp2
    }

    /** index of a pp2 colliding with pp, or -1 if none  */
    private fun collide(pp: PhrasePositions): Int {
        val tpPos = tpPos(pp)
        val rg: Array<PhrasePositions> = rptGroups[pp.rptGroup]
        for (pp2 in rg) {
            if (pp2 != pp && tpPos(pp2) == tpPos) {
                return pp2.rptInd
            }
        }
        return -1
    }

    /**
     * Initialize PhrasePositions in place. A one time initialization for this scorer (on first doc
     * matching all terms):
     *
     *
     *  * Check if there are repetitions
     *  * If there are, find groups of repetitions.
     *
     *
     * Examples:
     *
     *
     *  1. no repetitions: **"ho my"~2**
     *  1. repetitions: **"ho my my"~2**
     *  1. repetitions: **"my ho my"~2**
     *
     *
     * @return false if PPs are exhausted (and so current doc will not be a match)
     */
    @Throws(IOException::class)
    private fun initPhrasePositions(): Boolean {
        end = Int.Companion.MIN_VALUE
        if (!checkedRpts) {
            return initFirstTime()
        }
        if (!hasRpts) {
            initSimple()
            return true // PPs available
        }
        return initComplex()
    }

    /**
     * no repeats: simplest case, and most common. It is important to keep this piece of the code
     * simple and efficient
     */
    @Throws(IOException::class)
    private fun initSimple() {
        // System.err.println("initSimple: doc: "+min.doc);
        pq.clear()
        // position pps and build queue from list
        for (pp in phrasePositions) {
            pp.firstPosition()
            if (pp.position > end) {
                end = pp.position
            }
            pq.add(pp)
        }
    }

    /** with repeats: not so simple.  */
    @Throws(IOException::class)
    private fun initComplex(): Boolean {
        // System.err.println("initComplex: doc: "+min.doc);
        placeFirstPositions()
        if (!advanceRepeatGroups()) {
            return false // PPs exhausted
        }
        fillQueue()
        return true // PPs available
    }

    /** move all PPs to their first position  */
    @Throws(IOException::class)
    private fun placeFirstPositions() {
        for (pp in phrasePositions) {
            pp.firstPosition()
        }
    }

    /** Fill the queue (all pps are already placed  */
    private fun fillQueue() {
        pq.clear()
        for (pp in phrasePositions) { // iterate cyclic list: done once handled max
            if (pp.position > end) {
                end = pp.position
            }
            pq.add(pp)
        }
    }

    /**
     * At initialization (each doc), each repetition group is sorted by (query) offset. This provides
     * the start condition: no collisions.
     *
     *
     * Case 1: no multi-term repeats<br></br>
     * It is sufficient to advance each pp in the group by one less than its group index. So lesser pp
     * is not advanced, 2nd one advance once, 3rd one advanced twice, etc.
     *
     *
     * Case 2: multi-term repeats<br></br>
     *
     * @return false if PPs are exhausted.
     */
    @Throws(IOException::class)
    private fun advanceRepeatGroups(): Boolean {
        for (rg in rptGroups) {
            if (hasMultiTermRpts) {
                // more involved, some may not collide
                var incr: Int
                var i = 0
                while (i < rg.size) {
                    incr = 1
                    val pp: PhrasePositions = rg[i]
                    var k: Int
                    while ((collide(pp).also { k = it }) >= 0) {
                        val pp2: PhrasePositions = lesser(pp, rg[k])
                        if (!advancePP(pp2)) { // at initialization always advance pp with higher offset
                            return false // exhausted
                        }
                        if (pp2.rptInd < i) { // should not happen
                            incr = 0
                            break
                        }
                    }
                    i += incr
                }
            } else {
                // simpler, we know exactly how much to advance
                for (j in 1..<rg.size) {
                    for (k in 0..<j) {
                        if (!rg[j].nextPosition()) {
                            return false // PPs exhausted
                        }
                    }
                }
            }
        }
        return true // PPs available
    }

    /**
     * initialize with checking for repeats. Heavy work, but done only for the first candidate doc.
     *
     *
     * If there are repetitions, check if multi-term postings (MTP) are involved.
     *
     *
     * Without MTP, once PPs are placed in the first candidate doc, repeats (and groups) are
     * visible.<br></br>
     * With MTP, a more complex check is needed, up-front, as there may be "hidden collisions".<br></br>
     * For example P1 has {A,B}, P1 has {B,C}, and the first doc is: "A C B". At start, P1 would point
     * to "A", p2 to "C", and it will not be identified that P1 and P2 are repetitions of each other.
     *
     *
     * The more complex initialization has two parts:<br></br>
     * (1) identification of repetition groups.<br></br>
     * (2) advancing repeat groups at the start of the doc.<br></br>
     * For (1), a possible solution is to just create a single repetition group, made of all repeating
     * pps. But this would slow down the check for collisions, as all pps would need to be checked.
     * Instead, we compute "connected regions" on the bipartite graph of postings and terms.
     */
    @Throws(IOException::class)
    private fun initFirstTime(): Boolean {
        // System.err.println("initFirstTime: doc: "+min.doc);
        checkedRpts = true
        placeFirstPositions()

        val rptTerms: LinkedHashMap<Term, Int> = repeatingTerms()
        hasRpts = !rptTerms.isEmpty()

        if (hasRpts) {
            rptStack =
                kotlin.arrayOfNulls<PhrasePositions>(numPostings) as Array<PhrasePositions> // needed with repetitions
            val rgs: ArrayList<ArrayList<PhrasePositions>> =
                gatherRptGroups(rptTerms)
            sortRptGroups(rgs)
            if (!advanceRepeatGroups()) {
                return false // PPs exhausted
            }
        }

        fillQueue()
        return true // PPs available
    }

    /**
     * sort each repetition group by (query) offset. Done only once (at first doc) and allows to
     * initialize faster for each doc.
     */
    private fun sortRptGroups(rgs: ArrayList<ArrayList<PhrasePositions>>) {
        rptGroups = kotlin.arrayOfNulls<Array<PhrasePositions>>(rgs.size) as Array<Array<PhrasePositions>>
        val cmprtr: Comparator<PhrasePositions> = compareBy { it.offset }
        for (i in rptGroups.indices) {
            val rg: Array<PhrasePositions> =
                rgs[i].toTypedArray<PhrasePositions>()
            Arrays.sort(rg, cmprtr)
            rptGroups[i] = rg
            for (j in rg.indices) {
                rg[j].rptInd = j // we use this index for efficient re-queuing
            }
        }
    }

    /** Detect repetition groups. Done once - for first doc  */
    @Throws(IOException::class)
    private fun gatherRptGroups(
        rptTerms: LinkedHashMap<Term, Int>
    ): ArrayList<ArrayList<PhrasePositions>> {
        val rpp: Array<PhrasePositions> = repeatingPPs(rptTerms)
        val res: ArrayList<ArrayList<PhrasePositions>> = ArrayList()
        if (!hasMultiTermRpts) {
            // simpler - no multi-terms - can base on positions in first doc
            for (i in rpp.indices) {
                val pp: PhrasePositions = rpp[i]
                if (pp.rptGroup >= 0) continue  // already marked as a repetition

                val tpPos = tpPos(pp)
                for (j in i + 1..<rpp.size) {
                    val pp2: PhrasePositions = rpp[j]
                    if (pp2.rptGroup >= 0 // already marked as a repetition
                        || pp2.offset == pp.offset // not a repetition: two PPs are originally in same offset
                        || tpPos(pp2) != tpPos
                    ) { // not a repetition
                        continue
                    }
                    // a repetition
                    var g: Int = pp.rptGroup
                    if (g < 0) {
                        g = res.size
                        pp.rptGroup = g
                        val rl: ArrayList<PhrasePositions> = ArrayList(2)
                        rl.add(pp)
                        res.add(rl)
                    }
                    pp2.rptGroup = g
                    res[g].add(pp2)
                }
            }
        } else {
            // more involved - has multi-terms
            val tmp: ArrayList<HashSet<PhrasePositions>> = ArrayList()
            val bb: ArrayList<FixedBitSet> = ppTermsBitSets(rpp, rptTerms)
            unionTermGroups(bb)
            val tg: HashMap<Term, Int> = termGroups(rptTerms, bb)
            val numDistinctGroupIds: Int = IntHashSet(tg.values).size()
            for (i in 0..<numDistinctGroupIds) {
                tmp.add(HashSet())
            }
            for (pp in rpp) {
                for (t in pp.terms!!) {
                    if (rptTerms.containsKey(t)) {
                        val g: Int = tg[t]!!
                        tmp[g].add(pp)
                        assert(pp.rptGroup == -1 || pp.rptGroup == g)
                        pp.rptGroup = g
                    }
                }
            }
            for (hs in tmp) {
                res.add(ArrayList(hs))
            }
        }
        return res
    }

    /** Actual position in doc of a PhrasePosition, relies on that position = tpPos - offset  */
    private fun tpPos(pp: PhrasePositions): Int {
        return pp.position + pp.offset
    }

    /** find repeating terms and assign them ordinal values  */
    private fun repeatingTerms(): LinkedHashMap<Term, Int> {
        val tord: LinkedHashMap<Term, Int> = LinkedHashMap()
        val tcnt: HashMap<Term, Int> = HashMap()
        for (pp in phrasePositions) {
            for (t in pp.terms!!) {
                val cnt: Int = (tcnt[t] ?: 0) + 1
                tcnt[t] = cnt
                if (cnt == 2) {
                    tord.put(t, tord.size)
                }
            }
        }
        return tord
    }

    /** find repeating pps, and for each, if has multi-terms, update this.hasMultiTermRpts  */
    private fun repeatingPPs(rptTerms: LinkedHashMap<Term, Int>): Array<PhrasePositions> {
        val rp: ArrayList<PhrasePositions> = ArrayList()
        for (pp in phrasePositions) {
            for (t in pp.terms!!) {
                if (rptTerms.containsKey(t)) {
                    rp.add(pp)
                    hasMultiTermRpts = hasMultiTermRpts or (pp.terms.size > 1)
                    break
                }
            }
        }
        return rp.toTypedArray<PhrasePositions>()
    }

    /**
     * bit-sets - for each repeating pp, for each of its repeating terms, the term ordinal values is
     * set
     */
    private fun ppTermsBitSets(
        rpp: Array<PhrasePositions>, tord: LinkedHashMap<Term, Int>
    ): ArrayList<FixedBitSet> {
        val bb: ArrayList<FixedBitSet> =
            ArrayList(rpp.size)
        for (pp in rpp) {
            val b = FixedBitSet(tord.size)
            var ord: Int
            for (t in pp.terms!!) {
                if ((tord[t].also { ord = it!! }) != null) {
                    b.set(ord)
                }
            }
            bb.add(b)
        }
        return bb
    }

    /**
     * union (term group) bit-sets until they are disjoint (O(n^^2)), and each group have different
     * terms
     */
    private fun unionTermGroups(bb: ArrayList<FixedBitSet>) {
        var incr: Int
        var i = 0
        while (i < bb.size - 1) {
            incr = 1
            var j = i + 1
            while (j < bb.size) {
                if (bb[i].intersects(bb[j])) {
                    bb[i].or(bb[j])
                    bb.removeAt(j)
                    incr = 0
                } else {
                    ++j
                }
            }
            i += incr
        }
    }

    /** map each term to the single group that contains it  */
    @Throws(IOException::class)
    private fun termGroups(
        tord: LinkedHashMap<Term, Int>,
        bb: ArrayList<FixedBitSet>
    ): HashMap<Term, Int> {
        val tg: HashMap<Term, Int> = HashMap()
        val t: Array<Term> = tord.keys.toTypedArray<Term>()
        for (i in bb.indices) { // i is the group no.
            val bits: FixedBitSet = bb[i]
            var ord: Int = bits.nextSetBit(0)
            while (ord != DocIdSetIterator.NO_MORE_DOCS
            ) {
                tg.put(t[ord], i)
                ord =
                    if (ord + 1 >= bits.length()) DocIdSetIterator.NO_MORE_DOCS else bits.nextSetBit(
                        ord + 1
                    )
            }
        }
        return tg
    }
}
