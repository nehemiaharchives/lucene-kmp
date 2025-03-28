package org.gnit.lucenekmp.util.automaton

import org.gnit.lucenekmp.internal.hppc.BitMixer
import org.gnit.lucenekmp.internal.hppc.IntHashSet
import org.gnit.lucenekmp.internal.hppc.IntObjectHashMap
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.BitSet
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.jdkport.appendCodePoint
import org.gnit.lucenekmp.jdkport.codePointAt
import org.gnit.lucenekmp.jdkport.peek
import org.gnit.lucenekmp.jdkport.pop
import org.gnit.lucenekmp.jdkport.push
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.IntsRef
import org.gnit.lucenekmp.util.IntsRefBuilder
import org.gnit.lucenekmp.util.RamUsageEstimator
import org.gnit.lucenekmp.util.automaton.Automaton.Builder


/**
 * Automata operations.
 *
 * @lucene.experimental
 */
object Operations {
    /**
     * Default maximum effort that [Operations.determinize] should spend before giving up and
     * throwing [TooComplexToDeterminizeException].
     */
    const val DEFAULT_DETERMINIZE_WORK_LIMIT: Int = 10000

    /**
     * Returns an automaton that accepts the concatenation of the languages of the given automata.
     *
     *
     * Complexity: linear in total number of states.
     *
     */
    @Deprecated("use {@link #concatenate(List)} instead")
    fun concatenate(a1: Automaton, a2: Automaton): Automaton {
        return concatenate(mutableListOf(a1, a2))
    }

    /**
     * Returns an automaton that accepts the concatenation of the languages of the given automata.
     *
     *
     * Complexity: linear in total number of states.
     *
     * @param list List of automata to be joined
     */
    fun concatenate(list: MutableList<Automaton>): Automaton {
        val result = Automaton()

        // First pass: create all states
        for (a in list) {
            if (a.numStates == 0) {
                // concatenation with empty is empty
                return Automata.makeEmpty()
            }
            val numStates: Int = a.numStates
            for (s in 0..<numStates) {
                result.createState()
            }
        }

        // Second pass: add transitions, carefully linking accept
        // states of A to init state of next A:
        var stateOffset = 0
        val t = Transition()
        for (i in list.indices) {
            val a = list.get(i)
            val numStates: Int = a.numStates

            val nextA = if (i == list.size - 1) null else list.get(i + 1)

            for (s in 0..<numStates) {
                var numTransitions = a.initTransition(s, t)
                for (j in 0..<numTransitions) {
                    a.getNextTransition(t)
                    result.addTransition(stateOffset + s, stateOffset + t.dest, t.min, t.max)
                }

                if (a.isAccept(s)) {
                    var followA = nextA
                    var followOffset = stateOffset
                    var upto = i + 1
                    while (true) {
                        if (followA != null) {
                            // Adds a "virtual" epsilon transition:
                            numTransitions = followA.initTransition(0, t)
                            for (j in 0..<numTransitions) {
                                followA.getNextTransition(t)
                                result.addTransition(
                                    stateOffset + s, followOffset + numStates + t.dest, t.min, t.max
                                )
                            }
                            if (followA.isAccept(0)) {
                                // Keep chaining if followA accepts empty string
                                followOffset += followA.numStates
                                followA = if (upto == list.size - 1) null else list.get(upto + 1)
                                upto++
                            } else {
                                break
                            }
                        } else {
                            result.setAccept(stateOffset + s, true)
                            break
                        }
                    }
                }
            }

            stateOffset += numStates
        }

        if (result.numStates == 0) {
            result.createState()
        }

        result.finishState()
        return removeDeadStates(result)
    }

    /**
     * Returns an automaton that accepts the union of the empty string and the language of the given
     * automaton. This may create a dead state.
     *
     *
     * Complexity: linear in number of states.
     */
    fun optional(a: Automaton): Automaton {
        if (a.isAccept(0)) {
            // If the initial state is accepted, then the empty string is already accepted.
            return a
        }

        var hasTransitionsToInitialState = false
        val t = Transition()
        outer@ for (state in 0..<a.numStates) {
            val count = a.initTransition(state, t)
            for (i in 0..<count) {
                a.getNextTransition(t)
                if (t.dest == 0) {
                    hasTransitionsToInitialState = true
                    break@outer
                }
            }
        }

        if (hasTransitionsToInitialState == false) {
            // If the automaton has no transition to the initial state, we can simply mark the initial
            // state as accepted.
            val result = Automaton()
            result.copy(a)
            if (result.numStates == 0) {
                result.createState()
            }
            result.setAccept(0, true)
            return result
        }

        val result = Automaton()
        result.createState()
        result.setAccept(0, true)
        if (a.numStates > 0) {
            result.copy(a)
            result.addEpsilon(0, 1)
        }
        result.finishState()
        return result
    }

    /**
     * Returns an automaton that accepts the Kleene star (zero or more concatenated repetitions) of
     * the language of the given automaton. Never modifies the input automaton language.
     *
     *
     * Complexity: linear in number of states.
     */
    fun repeat(a: Automaton): Automaton {
        if (a.numStates == 0) {
            // Repeating the empty automata will still only accept the empty automata.
            return a
        }

        if (a.isAccept(0) && a.acceptStates.cardinality() == 1) {
            // If state 0 is the only accept state, then this automaton already repeats itself.
            return a
        }

        val builder: Automaton.Builder = Builder()
        // Create the initial state, which is accepted
        builder.createState()
        builder.setAccept(0, true)
        val t = Transition()

        val stateMap = IntArray(a.numStates)
        for (state in 0..<a.numStates) {
            if (a.isAccept(state) == false) {
                stateMap[state] = builder.createState()
            } else if (a.getNumTransitions(state) == 0) {
                // Accept states that have no transitions get merged into state 0.
                stateMap[state] = 0
            } else {
                val newState = builder.createState()
                stateMap[state] = newState
                builder.setAccept(newState, true)
            }
        }

        // Now copy the automaton while renumbering states.
        for (state in 0..<a.numStates) {
            val src = stateMap[state]
            val count = a.initTransition(state, t)
            for (i in 0..<count) {
                a.getNextTransition(t)
                val dest = stateMap[t.dest]
                builder.addTransition(src, dest, t.min, t.max)
            }
        }

        // Now copy transitions of the initial state to our new initial state.
        var count = a.initTransition(0, t)
        for (i in 0..<count) {
            a.getNextTransition(t)
            builder.addTransition(0, stateMap[t.dest], t.min, t.max)
        }

        // Now copy transitions of the initial state to final states to make the automaton repeat
        // itself.
        var s: Int = a.acceptStates.nextSetBit(0)
        while (s != -1
        ) {
            if (stateMap[s] != 0) {
                count = a.initTransition(0, t)
                for (i in 0..<count) {
                    a.getNextTransition(t)
                    builder.addTransition(stateMap[s], stateMap[t.dest], t.min, t.max)
                }
            }
            s = a.acceptStates.nextSetBit(s + 1)
        }

        return removeDeadStates(builder.finish())
    }

    /**
     * Returns an automaton that accepts `min` or more concatenated repetitions of the
     * language of the given automaton.
     *
     *
     * Complexity: linear in number of states and in `min`.
     */
    fun repeat(a: Automaton, count: Int): Automaton {
        var count = count
        if (count == 0) {
            return repeat(a)
        }
        val `as`: MutableList<Automaton> = mutableListOf<Automaton>()
        while (count-- > 0) {
            `as`.add(a)
        }
        `as`.add(repeat(a))
        return concatenate(`as`)
    }

    /**
     * Returns an automaton that accepts between `min` and `max` (including
     * both) concatenated repetitions of the language of the given automaton.
     *
     *
     * Complexity: linear in number of states and in `min` and `max`.
     */
    fun repeat(a: Automaton, min: Int, max: Int): Automaton {
        if (min > max) {
            return Automata.makeEmpty()
        }

        val b: Automaton
        if (min == 0) {
            b = Automata.makeEmptyString()
        } else if (min == 1) {
            b = Automaton()
            b.copy(a)
        } else {
            val `as`: MutableList<Automaton> = mutableListOf<Automaton>()
            for (i in 0..<min) {
                `as`.add(a)
            }
            b = concatenate(`as`)
        }

        var prevAcceptStates: IntHashSet = toSet(b, 0)
        val builder: Builder = Builder()
        builder.copy(b)
        for (i in min..<max) {
            val numStates: Int = builder.numStates
            builder.copy(a)
            for (s in prevAcceptStates) {
                builder.addEpsilon(s!!.value, numStates)
            }
            prevAcceptStates = toSet(a, numStates)
        }

        return removeDeadStates(builder.finish())
    }

    private fun toSet(a: Automaton, offset: Int): IntHashSet {
        val numStates: Int = a.numStates
        val isAccept: BitSet = a.acceptStates
        val result: IntHashSet = IntHashSet()
        var upto = 0
        while (upto < numStates && (isAccept.nextSetBit(upto).also { upto = it }) != -1) {
            result.add(offset + upto)
            upto++
        }
        return result
    }

    /**
     * Returns a (deterministic) automaton that accepts the complement of the language of the given
     * automaton.
     *
     *
     * Complexity: linear in number of states if already deterministic and exponential otherwise.
     *
     * @param determinizeWorkLimit maximum effort to spend determinizing the automaton. Set higher to
     * allow more complex queries and lower to prevent memory exhaustion. [     ][.DEFAULT_DETERMINIZE_WORK_LIMIT] is a good starting default.
     */
    fun complement(a: Automaton, determinizeWorkLimit: Int): Automaton {
        var a = a
        a = totalize(determinize(a, determinizeWorkLimit))
        val numStates: Int = a.numStates
        for (p in 0..<numStates) {
            a.setAccept(p, !a.isAccept(p))
        }
        return removeDeadStates(a)
    }

    /**
     * Returns a (deterministic) automaton that accepts the intersection of the language of `a1
    ` *  and the complement of the language of `a2`. As a side-effect, the automata
     * may be determinized, if not already deterministic.
     *
     *
     * Complexity: quadratic in number of states if a2 already deterministic and exponential in
     * number of a2's states otherwise.
     *
     * @param a1 the initial automaton
     * @param a2 the automaton to subtract
     * @param determinizeWorkLimit maximum effort to spend determinizing the automaton. Set higher to
     * allow more complex queries and lower to prevent memory exhaustion. [     ][.DEFAULT_DETERMINIZE_WORK_LIMIT] is a good starting default.
     */
    fun minus(a1: Automaton, a2: Automaton, determinizeWorkLimit: Int): Automaton {
        if (isEmpty(a1) || a1 == a2) {
            return Automata.makeEmpty()
        }
        if (isEmpty(a2)) {
            return a1
        }
        return intersection(a1, complement(a2, determinizeWorkLimit))
    }

    /**
     * Returns an automaton that accepts the intersection of the languages of the given automata.
     * Never modifies the input automata languages.
     *
     *
     * Complexity: quadratic in number of states.
     */
    fun intersection(a1: Automaton, a2: Automaton): Automaton {
        if (a1 == a2) {
            return a1
        }
        if (a1.numStates == 0) {
            return a1
        }
        if (a2.numStates == 0) {
            return a2
        }
        val transitions1: Array<Array<Transition>> = a1.sortedTransitions
        val transitions2: Array<Array<Transition>> = a2.sortedTransitions
        val c = Automaton()
        c.createState()
        val worklist: ArrayDeque<StatePair> = ArrayDeque<StatePair>()
        val newstates: MutableMap<StatePair?, StatePair?> = mutableMapOf<StatePair?, StatePair?>()
        var p: StatePair = StatePair(0, 0, 0)
        worklist.add(p)
        newstates.put(p, p)
        while (worklist.size > 0) {
            p = worklist.removeFirst()
            c.setAccept(p.s, a1.isAccept(p.s1) && a2.isAccept(p.s2))
            val t1 = transitions1[p.s1]
            val t2 = transitions2[p.s2]
            var n1 = 0
            var b2 = 0
            while (n1 < t1.size) {
                while (b2 < t2.size && t2[b2]!!.max < t1[n1]!!.min) b2++
                var n2 = b2
                while (n2 < t2.size && t1[n1]!!.max >= t2[n2]!!.min) {
                    if (t2[n2]!!.max >= t1[n1]!!.min) {
                        val q: StatePair = StatePair(t1[n1]!!.dest, t2[n2]!!.dest)
                        var r: StatePair? = newstates.get(q)
                        if (r == null) {
                            q.s = c.createState()
                            worklist.add(q)
                            newstates.put(q, q)
                            r = q
                        }
                        val min = if (t1[n1]!!.min > t2[n2]!!.min) t1[n1]!!.min else t2[n2]!!.min
                        val max = if (t1[n1]!!.max < t2[n2]!!.max) t1[n1]!!.max else t2[n2]!!.max
                        c.addTransition(p.s, r.s, min, max)
                    }
                    n2++
                }
                n1++
            }
        }
        c.finishState()

        return removeDeadStates(c)
    }

    // TODO: move to test-framework?
    /**
     * Returns true if this automaton has any states that cannot be reached from the initial state or
     * cannot reach an accept state. Cost is O(numTransitions+numStates).
     */
    fun hasDeadStates(a: Automaton): Boolean {
        val liveStates: BitSet = getLiveStates(a)
        val numLive: Int = liveStates.cardinality()
        val numStates: Int = a.numStates
        require(
            numLive <= numStates
        ) { "numLive=$numLive numStates=$numStates $liveStates" }
        return numLive < numStates
    }

    // TODO: move to test-framework?
    /** Returns true if there are dead states reachable from an initial state.  */
    fun hasDeadStatesFromInitial(a: Automaton): Boolean {
        val reachableFromInitial: BitSet = getLiveStatesFromInitial(a)
        val reachableFromAccept: BitSet = getLiveStatesToAccept(a)
        reachableFromInitial.andNot(reachableFromAccept)
        return reachableFromInitial.isEmpty == false
    }

    // TODO: move to test-framework?
    /** Returns true if there are dead states that reach an accept state.  */
    fun hasDeadStatesToAccept(a: Automaton): Boolean {
        val reachableFromInitial: BitSet = getLiveStatesFromInitial(a)
        val reachableFromAccept: BitSet = getLiveStatesToAccept(a)
        reachableFromAccept.andNot(reachableFromInitial)
        return reachableFromAccept.isEmpty == false
    }

    /**
     * Returns an automaton that accepts the union of the languages of the given automata.
     *
     *
     * Complexity: linear in number of states.
     *
     */
    @Deprecated("use {@link #union(Collection)} instead")
    fun union(a1: Automaton, a2: Automaton): Automaton {
        return union(mutableListOf(a1, a2))
    }

    /**
     * Returns an automaton that accepts the union of the languages of the given automata.
     *
     *
     * Complexity: linear in number of states.
     *
     * @param list List of automata to be unioned.
     */
    fun union(list: MutableList<Automaton>): Automaton {
        val result = Automaton()

        // Create initial state:
        result.createState()

        // Copy over all automata
        for (a in list) {
            result.copy(a)
        }

        // Add epsilon transition from new initial state
        var stateOffset = 1
        for (a in list) {
            if (a.numStates == 0) {
                continue
            }
            result.addEpsilon(0, stateOffset)
            stateOffset += a.numStates
        }

        result.finishState()

        return mergeAcceptStatesWithNoTransition(removeDeadStates(result))
    }

    /**
     * Determinizes the given automaton.
     *
     *
     * Worst case complexity: exponential in number of states.
     *
     * @param workLimit Maximum amount of "work" that the powerset construction will spend before
     * throwing [TooComplexToDeterminizeException]. Higher numbers allow this operation to
     * consume more memory and CPU but allow more complex automatons. Use [     ][.DEFAULT_DETERMINIZE_WORK_LIMIT] as a decent default if you don't otherwise know what to
     * specify.
     * @throws TooComplexToDeterminizeException if determinizing requires more than `workLimit`
     * "effort"
     */
    fun determinize(a: Automaton, workLimit: Int): Automaton {
        if (a.isDeterministic) {
            // Already determinized
            return a
        }
        if (a.numStates <= 1) {
            // Already determinized
            return a
        }

        // subset construction
        val b: Automaton.Builder = Builder()

        // System.out.println("DET:");
        // a.writeDot("/l/la/lucene/core/detin.dot");

        // Same initial values and state will always have the same hashCode
        val initialset: FrozenIntSet = FrozenIntSet(intArrayOf(0), (BitMixer.mix(0) + 1).toLong(), 0)

        // Create state 0:
        b.createState()

        val worklist: ArrayDeque<FrozenIntSet> = ArrayDeque<FrozenIntSet>()
        val newstate: MutableMap<IntSet?, Int?> = mutableMapOf<IntSet?, Int?>()

        worklist.add(initialset)

        b.setAccept(0, a.isAccept(0))
        newstate.put(initialset, 0)

        // like Set<Integer,PointTransitions>
        val points = PointTransitionSet()

        // like HashMap<Integer,Integer>, maps state to its count
        val statesSet: StateSet = StateSet(5)

        val t = Transition()

        var effortSpent: Long = 0

        // LUCENE-9981: approximate conversion from what used to be a limit on number of states, to
        // maximum "effort":
        val effortLimit = workLimit * 10L

        while (worklist.size > 0) {
            // TODO (LUCENE-9983): these int sets really do not need to be sorted, and we are paying
            // a high (unecessary) price for that!  really we just need a low-overhead Map<int,int>
            // that implements equals/hash based only on the keys (ignores the values).  fixing this
            // might be a bigspeedup for determinizing complex automata
            val s: FrozenIntSet = worklist.removeFirst()

            // LUCENE-9981: we more carefully aggregate the net work this automaton is costing us, instead
            // of (overly simplistically) counting number
            // of determinized states:
            effortSpent += s.values.size
            if (effortSpent >= effortLimit) {
                throw TooComplexToDeterminizeException(a, workLimit)
            }

            // Collate all outgoing transitions by min/1+max:
            for (i in 0..<s.values.size) {
                val s0: Int = s.values[i]!!
                val numTransitions = a.getNumTransitions(s0)
                a.initTransition(s0, t)
                for (j in 0..<numTransitions) {
                    a.getNextTransition(t)
                    points.add(t)
                }
            }

            if (points.count == 0) {
                // No outgoing transitions -- skip it
                continue
            }

            points.sort()

            var lastPoint = -1
            var accCount = 0

            val r: Int = s.state

            for (i in 0..<points.count) {
                val point = points.points[i]!!.point

                if (statesSet.size() > 0) {
                    require(lastPoint != -1)

                    var q = newstate.get(statesSet)
                    if (q == null) {
                        q = b.createState()
                        val p: FrozenIntSet = statesSet.freeze(q)
                        // System.out.println("  make new state=" + q + " -> " + p + " accCount=" + accCount);
                        worklist.add(p)
                        b.setAccept(q, accCount > 0)
                        newstate.put(p, q)
                    } else {
                        require(
                            (if (accCount > 0) true else false) == b.isAccept(q)
                        ) {
                            ("accCount="
                                    + accCount
                                    + " vs existing accept="
                                    + b.isAccept(q)
                                    + " states="
                                    + statesSet)
                        }
                    }

                    // System.out.println("  add trans src=" + r + " dest=" + q + " min=" + lastPoint + "
                    // max=" + (point-1));
                    b.addTransition(r, q, lastPoint, point - 1)
                }

                // process transitions that end on this point
                // (closes an overlapping interval)
                var transitions = points.points[i]!!.ends.transitions
                var limit = points.points[i]!!.ends.next
                run {
                    var j = 0
                    while (j < limit) {
                        val dest = transitions[j]
                        statesSet.decr(dest)
                        accCount -= if (a.isAccept(dest)) 1 else 0
                        j += 3
                    }
                }
                points.points[i]!!.ends.next = 0

                // process transitions that start on this point
                // (opens a new interval)
                transitions = points.points[i]!!.starts.transitions
                limit = points.points[i]!!.starts.next
                var j = 0
                while (j < limit) {
                    val dest = transitions[j]
                    statesSet.incr(dest)
                    accCount += if (a.isAccept(dest)) 1 else 0
                    j += 3
                }
                lastPoint = point
                points.points[i]!!.starts.next = 0
            }
            points.reset()
            require(statesSet.size() == 0) { "size=" + statesSet.size() }
        }

        val result = b.finish()
        require(result.isDeterministic)
        return result
    }

    /** Returns true if the given automaton accepts no strings.  */
    fun isEmpty(a: Automaton): Boolean {
        if (a.numStates == 0) {
            // Common case: no states
            return true
        }
        if (a.isAccept(0) == false && a.getNumTransitions(0) == 0) {
            // Common case: just one initial state
            return true
        }
        if (a.isAccept(0) == true) {
            // Apparently common case: it accepts the damned empty string
            return false
        }

        val workList: ArrayDeque<Int?> = ArrayDeque<Int?>()
        val seen: BitSet = BitSet(a.numStates)
        workList.add(0)
        seen.set(0)

        val t = Transition()
        while (workList.isEmpty() == false) {
            val state: Int = workList.removeFirst()!!
            if (a.isAccept(state)) {
                return false
            }
            val count = a.initTransition(state, t)
            for (i in 0..<count) {
                a.getNextTransition(t)
                if (seen.get(t.dest) == false) {
                    workList.add(t.dest)
                    seen.set(t.dest)
                }
            }
        }

        return true
    }

    /**
     * Returns true if the given automaton accepts all strings.
     *
     *
     * The automaton must be deterministic, or this method may return false.
     *
     *
     * Complexity: linear in number of states and transitions.
     */
    fun isTotal(a: Automaton): Boolean {
        return isTotal(a, Character.MIN_CODE_POINT, Character.MAX_CODE_POINT)
    }

    /**
     * Returns true if the given automaton accepts all strings for the specified min/max range of the
     * alphabet.
     *
     *
     * The automaton must be deterministic, or this method may return false.
     *
     *
     * Complexity: linear in number of states and transitions.
     */
    fun isTotal(a: Automaton, minAlphabet: Int, maxAlphabet: Int): Boolean {
        val states: BitSet = getLiveStates(a)
        val spare = Transition()
        var seenStates = 0
        var state: Int = states.nextSetBit(0)
        while (state >= 0) {
            // all reachable states must be accept states
            if (a.isAccept(state) == false) return false
            // all reachable states must contain transitions covering minAlphabet-maxAlphabet
            var previousLabel = minAlphabet - 1
            for (transition in 0..<a.getNumTransitions(state)) {
                a.getTransition(state, transition, spare)
                // no gaps are allowed
                if (spare.min > previousLabel + 1) return false
                previousLabel = spare.max
            }
            if (previousLabel < maxAlphabet) return false
            if (state == Int.Companion.MAX_VALUE) {
                break // or (state+1) would overflow
            }
            seenStates++
            state = states.nextSetBit(state + 1)
        }
        // we've checked all the states, automaton is either total or empty
        return seenStates > 0
    }

    /**
     * Returns true if the given string is accepted by the automaton. The input must be deterministic.
     *
     *
     * Complexity: linear in the length of the string.
     *
     *
     * **Note:** for full performance, use the [RunAutomaton] class.
     */
    fun run(a: Automaton, s: String): Boolean {
        require(a.isDeterministic)
        var state = 0
        var i = 0
        var cp = 0
        while (i < s.length) {
            val nextState = a.step(state, s.codePointAt(i).also { cp = it })
            if (nextState == -1) {
                return false
            }
            state = nextState
            i += Character.charCount(cp)
        }
        return a.isAccept(state)
    }

    /**
     * Returns true if the given string (expressed as unicode codepoints) is accepted by the
     * automaton. The input must be deterministic.
     *
     *
     * Complexity: linear in the length of the string.
     *
     *
     * **Note:** for full performance, use the [RunAutomaton] class.
     */
    fun run(a: Automaton, s: IntsRef): Boolean {
        require(a.isDeterministic)
        var state = 0
        for (i in 0..<s.length) {
            val nextState = a.step(state, s.ints[s.offset + i])
            if (nextState == -1) {
                return false
            }
            state = nextState
        }
        return a.isAccept(state)
    }

    /**
     * Returns the set of live states. A state is "live" if an accept state is reachable from it and
     * if it is reachable from the initial state.
     */
    private fun getLiveStates(a: Automaton): BitSet {
        val live: BitSet = getLiveStatesFromInitial(a)
        live.and(getLiveStatesToAccept(a))
        return live
    }

    /** Returns bitset marking states reachable from the initial state.  */
    private fun getLiveStatesFromInitial(a: Automaton): BitSet {
        val numStates: Int = a.numStates
        val live: BitSet = BitSet(numStates)
        if (numStates == 0) {
            return live
        }
        val workList: ArrayDeque<Int?> = ArrayDeque<Int?>()
        live.set(0)
        workList.add(0)

        val t = Transition()
        while (workList.isEmpty() == false) {
            val s: Int = workList.removeFirst()!!
            val count = a.initTransition(s, t)
            for (i in 0..<count) {
                a.getNextTransition(t)
                if (live.get(t.dest) == false) {
                    live.set(t.dest)
                    workList.add(t.dest)
                }
            }
        }

        return live
    }

    /** Returns bitset marking states that can reach an accept state.  */
    private fun getLiveStatesToAccept(a: Automaton): BitSet {
        val builder: Automaton.Builder = Builder()

        // NOTE: not quite the same thing as what reverse() does:
        val t = Transition()
        val numStates: Int = a.numStates
        for (s in 0..<numStates) {
            builder.createState()
        }
        for (s in 0..<numStates) {
            val count = a.initTransition(s, t)
            for (i in 0..<count) {
                a.getNextTransition(t)
                builder.addTransition(t.dest, s, t.min, t.max)
            }
        }
        val a2 = builder.finish()

        val workList: ArrayDeque<Int?> = ArrayDeque<Int?>()
        val live: BitSet = BitSet(numStates)
        val acceptBits: BitSet = a.acceptStates
        var s = 0
        while (s < numStates && (acceptBits.nextSetBit(s).also { s = it }) != -1) {
            live.set(s)
            workList.add(s)
            s++
        }

        while (workList.isEmpty() == false) {
            s = workList.removeFirst()!!
            val count = a2.initTransition(s, t)
            for (i in 0..<count) {
                a2.getNextTransition(t)
                if (live.get(t.dest) == false) {
                    live.set(t.dest)
                    workList.add(t.dest)
                }
            }
        }

        return live
    }

    /**
     * Removes transitions to dead states (a state is "dead" if it is not reachable from the initial
     * state or no accept state is reachable from it.)
     */
    fun removeDeadStates(a: Automaton): Automaton {
        val numStates: Int = a.numStates
        val liveSet: BitSet = getLiveStates(a)
        if (liveSet.cardinality() == numStates) {
            return a
        }

        val map = IntArray(numStates)

        val result = Automaton()
        // System.out.println("liveSet: " + liveSet + " numStates=" + numStates);
        for (i in 0..<numStates) {
            if (liveSet.get(i)) {
                map[i] = result.createState()
                result.setAccept(map[i], a.isAccept(i))
            }
        }

        val t = Transition()

        for (i in 0..<numStates) {
            if (liveSet.get(i)) {
                val numTransitions = a.initTransition(i, t)
                // filter out transitions to dead states:
                for (j in 0..<numTransitions) {
                    a.getNextTransition(t)
                    if (liveSet.get(t.dest)) {
                        result.addTransition(map[i], map[t.dest], t.min, t.max)
                    }
                }
            }
        }

        result.finishState()
        require(hasDeadStates(result) == false)
        return result
    }

    /**
     * Merge all accept states that don't have outgoing transitions to a single shared state. This is
     * a subset of minimization that is much cheaper. This helper is useful because operations like
     * concatenation need to connect accept states of an automaton with the start state of the next
     * one, so having fewer accept states makes the produced automata simpler.
     */
    fun mergeAcceptStatesWithNoTransition(a: Automaton): Automaton {
        val numStates: Int = a.numStates

        var numAcceptStatesWithNoTransition = 0
        var acceptStatesWithNoTransition = IntArray(0)

        val acceptStates: BitSet = a.acceptStates
        for (i in 0..<numStates) {
            if (acceptStates.get(i) && a.getNumTransitions(i) == 0) {
                acceptStatesWithNoTransition =
                    ArrayUtil.grow(acceptStatesWithNoTransition, 1 + numAcceptStatesWithNoTransition)
                acceptStatesWithNoTransition[numAcceptStatesWithNoTransition++] = i
            }
        }

        if (numAcceptStatesWithNoTransition <= 1) {
            // No states to merge
            return a
        }

        // Shrink for simplicity.
        acceptStatesWithNoTransition =
            ArrayUtil.copyOfSubArray(acceptStatesWithNoTransition, 0, numAcceptStatesWithNoTransition)

        // Now copy states, preserving accept states.
        val result = Automaton()
        for (s in 0..<numStates) {
            val remappedS = remap(s, acceptStatesWithNoTransition)
            while (result.numStates <= remappedS) {
                result.createState()
            }
            if (acceptStates.get(s)) {
                result.setAccept(remappedS, true)
            }
        }

        // Now copy transitions, making sure to remap states.
        val t = Transition()
        for (s in 0..<numStates) {
            val remappedSource = remap(s, acceptStatesWithNoTransition)
            val numTransitions = a.initTransition(s, t)
            for (j in 0..<numTransitions) {
                a.getNextTransition(t)
                val remappedDest = remap(t.dest, acceptStatesWithNoTransition)
                result.addTransition(remappedSource, remappedDest, t.min, t.max)
            }
        }

        result.finishState()
        return result
    }

    private fun remap(s: Int, combinedStates: IntArray): Int {
        var idx: Int = Arrays.binarySearch(combinedStates, s)
        if (idx >= 0) {
            // This state is part of the states that get combined, remap to the first one.
            return combinedStates[0]
        } else {
            idx = -1 - idx
            if (idx <= 1) {
                // There is either no combined state before the current state, or only the first one, which
                // we're preserving: no renumbering needed.
                return s
            } else {
                // Subtract the number of states that get combined into the first combined state.
                return s - (idx - 1)
            }
        }
    }

    /**
     * Returns the longest string that is a prefix of all accepted strings and visits each state at
     * most once. The automaton must not have dead states. If this automaton has already been
     * converted to UTF-8 (e.g. using [UTF32ToUTF8]) then you should use [ ][.getCommonPrefixBytesRef] instead.
     *
     * @throws IllegalArgumentException if the automaton has dead states reachable from the initial
     * state.
     * @return common prefix, which can be an empty (length 0) String (never null)
     */
    fun getCommonPrefix(a: Automaton): String {
        require(!hasDeadStatesFromInitial(a)) { "input automaton has dead states" }
        if (isEmpty(a)) {
            return ""
        }
        val builder: StringBuilder = StringBuilder()
        val scratch = Transition()
        val visited: FixedBitSet = FixedBitSet(a.numStates)
        var current: FixedBitSet = FixedBitSet(a.numStates)
        var next: FixedBitSet = FixedBitSet(a.numStates)
        current.set(0) // start with initial state
        algorithm@ while (true) {
            var label = -1
            // do a pass, stepping all current paths forward once
            var state: Int = current.nextSetBit(0)
            while (state != DocIdSetIterator.NO_MORE_DOCS
            ) {
                visited.set(state)
                // if it is an accept state, we are done
                if (a.isAccept(state)) {
                    break@algorithm
                }
                for (transition in 0..<a.getNumTransitions(state)) {
                    a.getTransition(state, transition, scratch)
                    if (label == -1) {
                        label = scratch.min
                    }
                    // either a range of labels, or label that doesn't match all the other paths this round
                    if (scratch.min !== scratch.max || scratch.min !== label) {
                        break@algorithm
                    }
                    // mark target state for next iteration
                    next.set(scratch.dest)
                }
                state =
                    if (state + 1 >= current.length())
                        DocIdSetIterator.NO_MORE_DOCS
                    else
                        current.nextSetBit(state + 1)
            }

            require(label != -1) { "we should not get here since we checked no dead-end states up front!?" }

            // add the label to the prefix
            builder.appendCodePoint(label)
            // swap "current" with "next", clear "next"
            val tmp: FixedBitSet = current
            current = next
            next = tmp
            next.clear()
        }
        return builder.toString()
    }

    /**
     * Returns the longest BytesRef that is a prefix of all accepted strings and visits each state at
     * most once.
     *
     * @return common prefix, which can be an empty (length 0) BytesRef (never null), and might
     * possibly include a UTF-8 fragment of a full Unicode character
     */
    fun getCommonPrefixBytesRef(a: Automaton): BytesRef {
        val prefix = getCommonPrefix(a)
        val builder: BytesRefBuilder = BytesRefBuilder()
        for (i in 0..<prefix.length) {
            val ch = prefix.get(i)
            check(ch.code <= 255) { "automaton is not binary" }
            builder.append(ch.code.toByte())
        }

        return builder.get()
    }

    /**
     * If this automaton accepts a single input, return it. Else, return null. The automaton must be
     * deterministic.
     */
    fun getSingleton(a: Automaton): IntsRef? {
        require(a.isDeterministic != false) { "input automaton must be deterministic" }
        val builder = IntsRefBuilder()
        val visited = IntHashSet()
        var s = 0
        val t = Transition()
        while (true) {
            visited.add(s)
            if (a.isAccept(s) == false) {
                if (a.getNumTransitions(s) == 1) {
                    a.getTransition(s, 0, t)
                    if (t.min == t.max && !visited.contains(t.dest)) {
                        builder.append(t.min)
                        s = t.dest
                        continue
                    }
                }
            } else if (a.getNumTransitions(s) == 0) {
                return builder.get()
            }

            // Automaton accepts more than one string:
            return null
        }
    }

    /**
     * Returns the longest BytesRef that is a suffix of all accepted strings. Worst case complexity:
     * quadratic with number of states+transitions.
     *
     * @return common suffix, which can be an empty (length 0) BytesRef (never null)
     */
    fun getCommonSuffixBytesRef(a: Automaton): BytesRef {
        // reverse the language of the automaton, then reverse its common prefix.
        val r = reverse(a)
        val ref: BytesRef = getCommonPrefixBytesRef(r)
        reverseBytes(ref)
        return ref
    }

    private fun reverseBytes(ref: BytesRef) {
        if (ref.length <= 1) return
        val num: Int = ref.length shr 1
        for (i in ref.offset..<(ref.offset + num)) {
            val b: Byte = ref.bytes[i]!!
            ref.bytes[i] = ref.bytes[ref.offset * 2 + ref.length - i - 1]
            ref.bytes[ref.offset * 2 + ref.length - i - 1] = b
        }
    }

    /** Returns an automaton accepting the reverse language.  */
    fun reverse(a: Automaton): Automaton {
        if (isEmpty(a)) {
            return Automaton()
        }

        val numStates: Int = a.numStates

        // Build a new automaton with all edges reversed
        val builder: Builder = Builder()

        // Initial node; we'll add epsilon transitions in the end:
        builder.createState()

        for (s in 0..<numStates) {
            builder.createState()
        }

        // Old initial state becomes new accept state:
        builder.setAccept(1, true)

        val t = Transition()
        for (s in 0..<numStates) {
            val numTransitions = a.getNumTransitions(s)
            a.initTransition(s, t)
            for (i in 0..<numTransitions) {
                a.getNextTransition(t)
                builder.addTransition(t.dest + 1, s + 1, t.min, t.max)
            }
        }

        val result = builder.finish()

        var s = 0
        val acceptStates: BitSet = a.acceptStates
        while (s < numStates && (acceptStates.nextSetBit(s).also { s = it }) != -1) {
            result.addEpsilon(0, s + 1)
            s++
        }

        result.finishState()

        return removeDeadStates(result)
    }

    /**
     * Returns a new automaton accepting the same language with added transitions to a dead state so
     * that from every state and every label there is a transition.
     */
    fun totalize(a: Automaton): Automaton {
        val result = Automaton()
        val numStates: Int = a.numStates
        for (i in 0..<numStates) {
            result.createState()
            result.setAccept(i, a.isAccept(i))
        }

        val deadState = result.createState()
        result.addTransition(
            deadState,
            deadState,
            Character.MIN_CODE_POINT,
            Character.MAX_CODE_POINT
        )

        val t = Transition()
        for (i in 0..<numStates) {
            var maxi: Int = Character.MIN_CODE_POINT
            val count = a.initTransition(i, t)
            for (j in 0..<count) {
                a.getNextTransition(t)
                result.addTransition(i, t.dest, t.min, t.max)
                if (t.min > maxi) {
                    result.addTransition(i, deadState, maxi, t.min - 1)
                }
                if (t.max + 1 > maxi) {
                    maxi = t.max + 1
                }
            }

            if (maxi <= Character.MAX_CODE_POINT) {
                result.addTransition(i, deadState, maxi, Character.MAX_CODE_POINT)
            }
        }

        result.finishState()
        return result
    }

    /**
     * Returns the topological sort of all states reachable from the initial state. This method
     * assumes that the automaton does not contain cycles, and will throw an IllegalArgumentException
     * if a cycle is detected. The CPU cost is O(numTransitions), and the implementation is
     * non-recursive, so it will not exhaust the java stack for automaton matching long strings. If
     * there are dead states in the automaton, they will be removed from the returned array.
     *
     *
     * Note: This method uses a deque to iterative the states, which could potentially consume a
     * lot of heap space for some automatons. Specifically, automatons with a deep level of states
     * (i.e., a large number of transitions from the initial state to the final state) may
     * particularly contribute to high memory usage. The memory consumption of this method can be
     * considered as O(N), where N is the depth of the automaton (the maximum number of transitions
     * from the initial state to any state). However, as this method detects cycles, it will never
     * attempt to use infinite RAM.
     *
     * @param a the Automaton to be sorted
     * @return the topologically sorted array of state ids
     */
    fun topoSortStates(a: Automaton): IntArray? {
        if (a.numStates == 0) {
            return IntArray(0)
        }
        val numStates: Int = a.numStates
        var states = IntArray(numStates)
        val upto = topoSortStates(a, states)

        if (upto < states.size) {
            // There were dead states
            val newStates = IntArray(upto)
            /*java.lang.System.arraycopy(states, 0, newStates, 0, upto)*/
            states.copyInto(
                newStates,
                destinationOffset = 0,
                startIndex = 0,
                endIndex = upto
            )
            states = newStates
        }

        // Reverse the order:
        for (i in 0..<states.size / 2) {
            val s = states[i]
            states[i] = states[states.size - 1 - i]
            states[states.size - 1 - i] = s
        }

        return states
    }

    /**
     * Performs a topological sort on the states of the given Automaton.
     *
     * @param a The automaton whose states are to be topologically sorted.
     * @param states An int array which stores the states.
     * @return the number of states in the final sorted list.
     * @throws IllegalArgumentException if the input automaton has a cycle.
     */
    private fun topoSortStates(a: Automaton, states: IntArray): Int {
        val onStack: BitSet = BitSet(a.numStates)
        val visited: BitSet = BitSet(a.numStates)
        val stack: ArrayDeque<Int> = ArrayDeque<Int>()
        stack.push(0) // Assuming that the initial state is 0.
        var upto = 0
        val t = Transition()

        while (!stack.isEmpty()) {
            val state: Int = stack.peek() // Just peek, don't remove the state yet

            val count = a.initTransition(state, t)
            var pushed = false
            for (i in 0..<count) {
                a.getNextTransition(t)
                if (!visited.get(t.dest)) {
                    visited.set(t.dest)
                    stack.push(t.dest) // Push the next unvisited state onto the stack
                    onStack.set(state)
                    pushed = true
                    break // Exit the loop, we'll continue from here in the next iteration
                } else require(!onStack.get(t.dest)) { "Input automaton has a cycle." }
            }

            // If we haven't pushed any new state onto the stack, we're done with this state
            if (!pushed) {
                onStack.clear(state) // remove the node from the current recursion stack
                stack.pop()
                states[upto] = state
                upto++
            }
        }
        return upto
    }

    // Simple custom ArrayList<Transition>
    internal class TransitionList {
        // dest, min, max
        var transitions: IntArray = IntArray(3)
        var next: Int = 0

        fun add(t: Transition) {
            if (transitions.size < next + 3) {
                transitions = ArrayUtil.grow(transitions, next + 3)
            }
            transitions[next] = t.dest
            transitions[next + 1] = t.min
            transitions[next + 2] = t.max
            next += 3
        }
    }

    // Holds all transitions that start on this int point, or
    // end at this point-1
    internal class PointTransitions : Comparable<PointTransitions> {
        var point: Int = 0
        val ends: TransitionList = TransitionList()
        val starts: TransitionList = TransitionList()

        override fun compareTo(other: PointTransitions): Int {
            return point - other.point
        }

        fun reset(point: Int) {
            this.point = point
            ends.next = 0
            starts.next = 0
        }

        override fun equals(other: Any?): Boolean {
            return (other as PointTransitions).point == point
        }

        override fun hashCode(): Int {
            return point
        }
    }

    internal class PointTransitionSet {
        var count: Int = 0
        var points: Array<PointTransitions?> = kotlin.arrayOfNulls<PointTransitions?>(5)

        private val map: IntObjectHashMap<PointTransitions?> = IntObjectHashMap()
        private var useHash = false

        private fun next(point: Int): PointTransitions {
            // 1st time we are seeing this point
            if (count == points.size) {
                val newArray =
                    kotlin.arrayOfNulls<PointTransitions>(
                        ArrayUtil.oversize(
                            1 + count,
                            RamUsageEstimator.NUM_BYTES_OBJECT_REF
                        )
                    )
                /*java.lang.System.arraycopy(points, 0, newArray, 0, count)*/
                points.copyInto(
                    newArray,
                    destinationOffset = 0,
                    startIndex = 0,
                    endIndex = count
                )
                points = newArray
            }
            var points0 = points[count]
            if (points0 == null) {
                points[count] = PointTransitions()
                points0 = points[count]
            }
            points0!!.reset(point)
            count++
            return points0
        }

        private fun find(point: Int): PointTransitions? {
            if (useHash) {
                val pi = point
                var p: PointTransitions? = map.get(pi)
                if (p == null) {
                    p = next(point)
                    map.put(pi, p)
                }
                return p
            } else {
                for (i in 0..<count) {
                    if (points[i]!!.point == point) {
                        return points[i]
                    }
                }

                val p = next(point)
                if (count == HASHMAP_CUTOVER) {
                    // switch to HashMap on the fly
                    require(map.size() == 0)
                    for (i in 0..<count) {
                        map.put(points[i]!!.point, points[i])
                    }
                    useHash = true
                }
                return p
            }
        }

        fun reset() {
            if (useHash) {
                map.clear()
                useHash = false
            }
            count = 0
        }

        fun sort() {
            // Tim sort performs well on already sorted arrays:
            if (count > 1) ArrayUtil.timSort<PointTransitions>(a = points as Array<PointTransitions>, fromIndex = 0, toIndex = count)
        }

        fun add(t: Transition) {
            find(t.min)!!.starts.add(t)
            find(1 + t.max)!!.ends.add(t)
        }

        override fun toString(): String {
            val s = StringBuilder()
            for (i in 0..<count) {
                if (i > 0) {
                    s.append(' ')
                }
                s.append(points[i]!!.point)
                    .append(':')
                    .append(points[i]!!.starts.next / 3)
                    .append(',')
                    .append(points[i]!!.ends.next / 3)
            }
            return s.toString()
        }

        companion object {
            private const val HASHMAP_CUTOVER = 30
        }
    }
}
