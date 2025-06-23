package org.gnit.lucenekmp.util.automaton

import org.gnit.lucenekmp.internal.hppc.BitMixer
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.RamUsageEstimator
import org.gnit.lucenekmp.util.automaton.Operations.PointTransitionSet
import kotlin.math.min


/**
 * A RunAutomaton that does not require DFA. It will lazily determinize on-demand, memorizing the
 * generated DFA states that has been explored. Note: the current implementation is NOT thread-safe
 *
 *
 * implemented based on: https://swtch.com/~rsc/regexp/regexp1.html
 *
 * @lucene.internal
 */
class NFARunAutomaton(private val automaton: Automaton, private val alphabetSize: Int) : ByteRunnable,
    TransitionAccessor, Accountable {
    private val points: IntArray
    private val dStateToOrd: MutableMap<DState, Int> = mutableMapOf<DState, Int>() // could init lazily?
    private var dStates: Array<DState?>
    val classmap: IntArray // map from char number to class

    private val transitionSet: PointTransitionSet = PointTransitionSet() // reusable
    private val statesSet = StateSet(5) // reusable

    /**
     * Constructor, assuming alphabet size is the whole Unicode code point space
     *
     * @param automaton incoming automaton, should be NFA, for DFA please use [RunAutomaton] for
     * better efficiency
     */
    constructor(automaton: Automaton) : this(automaton, Character.MAX_CODE_POINT + 1)

    /**
     * Constructor
     *
     * @param automaton incoming automaton, should be NFA, for DFA please use [RunAutomaton] *
     * for better efficiency
     * @param alphabetSize alphabet size
     */
    init {
        points = automaton.getStartPoints()
        dStates = kotlin.arrayOfNulls<DState>(10)
        findDState(DState(intArrayOf(0)))

        /*
     * Set alphabet table for optimal run performance.
     */
        classmap = IntArray(min(256, alphabetSize))
        var i = 0
        for (j in classmap.indices) {
            if (i + 1 < points.size && j == points[i + 1]) {
                i++
            }
            classmap[j] = i
        }
    }

    /**
     * For a given state and an incoming character (codepoint), return the next state
     *
     * @param state incoming state, should either be 0 or some state that is returned previously by
     * this function
     * @param c codepoint
     * @return the next state or [.MISSING] if the transition doesn't exist
     */
    public override fun step(state: Int, c: Int): Int {
        checkNotNull(dStates[state])
        return step(dStates[state]!!, c)
    }

    public override fun isAccept(state: Int): Boolean {
        checkNotNull(dStates[state])
        return dStates[state]!!.isAccept
    }

    override val size: Int
        get() = dStates.size

    /**
     * Run through a given codepoint array, return accepted or not, should only be used in test
     *
     * @param s String represented by an int array
     * @return accept or not
     */
    fun run(s: IntArray): Boolean {
        var p = 0
        for (c in s) {
            p = step(p, c)
            if (p == MISSING) return false
        }
        return dStates[p]!!.isAccept
    }

    /**
     * From an existing DFA state, step to next DFA state given character c if the transition is
     * previously tried then this operation will just use the cached result, otherwise it will call
     * [DState.step] to get the next state and cache the result
     */
    private fun step(dState: DState, c: Int): Int {
        val charClass = getCharClass(c)
        return dState.nextState(charClass)
    }

    /**
     * return the ordinal of given DFA state, generate a new ordinal if the given DFA state is a new
     * one
     */
    private fun findDState(dState: DState?): Int {
        if (dState == null) {
            return MISSING
        }
        var ord: Int = dStateToOrd.getOrElse(dState) { -1 }
        if (ord >= 0) {
            return ord
        }
        ord = dStateToOrd.size
        dStateToOrd.put(dState, ord)
        require(ord >= dStates.size || dStates[ord] == null)
        if (ord >= dStates.size) {
            dStates = ArrayUtil.grow(dStates, ord + 1)
        }
        dStates[ord] = dState
        return ord
    }

    /** Gets character class of given codepoint  */
    fun getCharClass(c: Int): Int {
        require(c < alphabetSize)

        if (c < classmap.size) {
            return classmap[c]
        }

        // binary search
        var a = 0
        var b = points.size
        while (b - a > 1) {
            val d = (a + b) ushr 1
            if (points[d] > c) b = d
            else if (points[d] < c) a = d
            else return d
        }
        return a
    }

    public override fun initTransition(state: Int, t: Transition): Int {
        t.source = state
        t.transitionUpto = -1
        return getNumTransitions(state)
    }

    public override fun getNextTransition(t: Transition) {
        require(t.transitionUpto < points.size - 1 && t.transitionUpto >= -1)
        while (dStates[t.source]!!.transitions!![++t.transitionUpto] == MISSING) {
            // this shouldn't throw AIOOBE as long as this function is only called
            // numTransitions times
        }
        require(dStates[t.source]!!.transitions!![t.transitionUpto] != NOT_COMPUTED)

        setTransitionAccordingly(t)
    }

    private fun setTransitionAccordingly(t: Transition) {
        t.dest = dStates[t.source]!!.transitions!![t.transitionUpto]
        t.min = points[t.transitionUpto]
        if (t.transitionUpto == points.size - 1) {
            t.max = alphabetSize - 1
        } else {
            t.max = points[t.transitionUpto + 1] - 1
        }
    }

    public override fun getNumTransitions(state: Int): Int {
        dStates[state]!!.determinize()
        return dStates[state]!!.outgoingTransitions
    }

    public override fun getTransition(state: Int, index: Int, t: Transition) {
        dStates[state]!!.determinize()
        var outgoingTransitions = -1
        t.transitionUpto = -1
        t.source = state
        while (outgoingTransitions < index && t.transitionUpto < points.size - 1) {
            if (dStates[t.source]!!.transitions!![++t.transitionUpto] != MISSING) {
                outgoingTransitions++
            }
        }
        require(outgoingTransitions == index)

        setTransitionAccordingly(t)
    }

    public override fun ramBytesUsed(): Long {
        return (BASE_RAM_BYTES
                + RamUsageEstimator.sizeOfObject(automaton)
                + RamUsageEstimator.sizeOfObject(points)
                + RamUsageEstimator.sizeOfMap(dStateToOrd)
                + RamUsageEstimator.sizeOfObject(dStates)
                + RamUsageEstimator.sizeOfObject(classmap))
    }

    private inner class DState(nfaStates: IntArray) : Accountable {
        private val nfaStates: IntArray

        // this field is lazily init'd when first time caller wants to add a new transition
        internal var transitions: IntArray? = null
        private val hash: Int
        internal val isAccept: Boolean
        private val stepTransition = Transition()
        private var minimalTransition: Transition? = null
        private var computedTransitions = 0
        internal var outgoingTransitions = 0

        init {
            require(nfaStates != null && nfaStates.size > 0)
            this.nfaStates = nfaStates
            var hash = nfaStates.size
            var isAccept = false
            for (s in nfaStates) {
                hash += BitMixer.mix(s)
                if (automaton.isAccept(s)) {
                    isAccept = true
                }
            }
            this.isAccept = isAccept
            this.hash = hash
        }

        fun nextState(charClass: Int): Int {
            initTransitions()
            require(charClass < transitions!!.size)
            if (transitions!![charClass] == NOT_COMPUTED) {
                assignTransition(charClass, findDState(step(points[charClass])))
                // we could potentially update more than one char classes
                if (minimalTransition != null) {
                    // to the left
                    var cls = charClass
                    while (cls > 0 && points[--cls] >= minimalTransition!!.min) {
                        require(transitions!![cls] == NOT_COMPUTED || transitions!![cls] == transitions!![charClass])
                        assignTransition(cls, transitions!![charClass])
                    }
                    // to the right
                    cls = charClass
                    while (cls < points.size - 1 && points[++cls] <= minimalTransition!!.max) {
                        require(transitions!![cls] == NOT_COMPUTED || transitions!![cls] == transitions!![charClass])
                        assignTransition(cls, transitions!![charClass])
                    }
                    minimalTransition = null
                }
            }
            return transitions!![charClass]
        }

        fun assignTransition(charClass: Int, dest: Int) {
            if (transitions!![charClass] == NOT_COMPUTED) {
                computedTransitions++
                transitions!![charClass] = dest
                if (transitions!![charClass] != MISSING) {
                    outgoingTransitions++
                }
            }
        }

        /**
         * given a list of NFA states and a character c, compute the output list of NFA state which is
         * wrapped as a DFA state
         */
        fun step(c: Int): DState? {
            statesSet.reset() // TODO: fork IntHashSet from hppc instead?
            var numTransitions: Int
            var left = -1
            var right = alphabetSize
            for (nfaState in nfaStates) {
                numTransitions = automaton.initTransition(nfaState, stepTransition)
                // TODO: binary search should be faster, since transitions are sorted
                for (i in 0..<numTransitions) {
                    automaton.getNextTransition(stepTransition)
                    if (stepTransition.min <= c && stepTransition.max >= c) {
                        statesSet.incr(stepTransition.dest)
                        left = kotlin.math.max(stepTransition.min, left)
                        right = kotlin.math.min(stepTransition.max, right)
                    }
                    if (stepTransition.max < c) {
                        left = kotlin.math.max(stepTransition.max + 1, left)
                    }
                    if (stepTransition.min > c) {
                        right = kotlin.math.min(stepTransition.min - 1, right)
                        // transitions in automaton are sorted
                        break
                    }
                }
            }
            if (statesSet.size() == 0) {
                return null
            }
            minimalTransition = Transition()
            minimalTransition!!.min = left
            minimalTransition!!.max = right
            return DState(statesSet.array)
        }

        // determinize this state only
        fun determinize() {
            if (transitions != null && computedTransitions == transitions!!.size) {
                // already determinized
                return
            }
            initTransitions()
            // Mostly forked from Operations.determinize
            transitionSet.reset()
            for (nfaState in nfaStates) {
                val numTransitions = automaton.initTransition(nfaState, stepTransition)
                for (i in 0..<numTransitions) {
                    automaton.getNextTransition(stepTransition)
                    transitionSet.add(stepTransition)
                }
            }
            if (transitionSet.count == 0) {
                // no outgoing transitions
                /*java.util.Arrays.fill(transitions, MISSING)*/
                transitions!!.fill(MISSING)
                computedTransitions = transitions!!.size
                return
            }

            transitionSet
                .sort() // TODO: could use a PQ (heap) instead, since transitions for each state are
            // sorted
            statesSet.reset()
            var lastPoint = -1
            var charClass = 0
            for (i in 0..<transitionSet.count) {
                val point: Int = transitionSet.points[i]!!.point
                if (statesSet.size() > 0) {
                    require(lastPoint != -1)
                    val ord = findDState(DState(statesSet.array))
                    while (points[charClass] < lastPoint) {
                        assignTransition(charClass++, MISSING)
                    }
                    require(points[charClass] == lastPoint)
                    while (charClass < points.size && points[charClass] < point) {
                        require(transitions!![charClass] == NOT_COMPUTED || transitions!![charClass] == ord)
                        assignTransition(charClass++, ord)
                    }
                    require(
                        (charClass == points.size && point == alphabetSize)
                                || points[charClass] == point
                    )
                }

                // process transitions that end on this point
                // (closes an overlapping interval)
                var transitions: IntArray = transitionSet.points[i]!!.ends.transitions
                var limit: Int = transitionSet.points[i]!!.ends.next
                run {
                    var j = 0
                    while (j < limit) {
                        val dest = transitions[j]
                        statesSet.decr(dest)
                        j += 3
                    }
                }
                transitionSet.points[i]!!.ends.next = 0

                // process transitions that start on this point
                // (opens a new interval)
                transitions = transitionSet.points[i]!!.starts.transitions
                limit = transitionSet.points[i]!!.starts.next
                var j = 0
                while (j < limit) {
                    val dest = transitions[j]
                    statesSet.incr(dest)
                    j += 3
                }

                lastPoint = point
                transitionSet.points[i]!!.starts.next = 0
            }
            require(statesSet.size() == 0)
            require(
                computedTransitions
                        >= charClass
            ) // it's also possible that some transitions after the charClass has already
            // been explored
            // no more outgoing transitions, set rest of transition to MISSING
            require(charClass == transitions!!.size || transitions!![charClass] == MISSING || transitions!![charClass] == NOT_COMPUTED)
            /*java.util.Arrays.fill(transitions, charClass, transitions!!.size, MISSING)*/
            transitions!!.fill(MISSING, charClass, transitions!!.size)
            computedTransitions = transitions!!.size
        }

        fun initTransitions() {
            if (transitions == null) {
                transitions = IntArray(points.size)
                /*java.util.Arrays.fill(transitions, NOT_COMPUTED)*/
                transitions!!.fill(NOT_COMPUTED)
            }
        }

        override fun hashCode(): Int {
            return hash
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) return true
            if (o !is DState) return false
            return hash == o.hash && nfaStates.contentEquals(o.nfaStates)
        }

        public override fun ramBytesUsed(): Long {
            return (RamUsageEstimator.alignObjectSize(
                Int.SIZE_BYTES * 3 + 1
                        + Transition.BYTES_USED * 2 + RamUsageEstimator.NUM_BYTES_OBJECT_HEADER
                        + RamUsageEstimator.NUM_BYTES_OBJECT_REF * 4L
            )
                    + RamUsageEstimator.sizeOfObject(nfaStates)
                    + RamUsageEstimator.sizeOfObject(transitions))
        }
    }

    companion object {
        /** state ordinal of "no such state"  */
        private val MISSING = -1

        private val NOT_COMPUTED = -2

        private val BASE_RAM_BYTES: Long = RamUsageEstimator.shallowSizeOfInstance(NFARunAutomaton::class)
    }
}
