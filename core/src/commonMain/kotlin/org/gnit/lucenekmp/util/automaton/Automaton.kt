package org.gnit.lucenekmp.util.automaton

import org.gnit.lucenekmp.internal.hppc.IntHashSet
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.jdkport.BitSet
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.jdkport.Objects
import org.gnit.lucenekmp.jdkport.appendCodePoint
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.RamUsageEstimator
import org.gnit.lucenekmp.util.Sorter
import org.gnit.lucenekmp.util.InPlaceMergeSorter
import kotlin.jvm.JvmOverloads
import kotlin.math.max


// TODO
//   - could use packed int arrays instead
//   - could encode dest w/ delta from to?
/**
 * Represents an automaton and all its states and transitions. States are integers and must be
 * created using [.createState]. Mark a state as an accept state using [.setAccept]. Add
 * transitions using [.addTransition]. Each state must have all of its transitions added at
 * once; if this is too restrictive then use [Automaton.Builder] instead. State 0 is always
 * the initial state. Once a state is finished, either because you've starting adding transitions to
 * another state or you call [.finishState], then that states transitions are sorted (first by
 * min, then max, then dest) and reduced (transitions with adjacent labels going to the same dest
 * are combined).
 *
 * @lucene.experimental
 */
class Automaton @JvmOverloads constructor(numStates: Int = 2, numTransitions: Int = 2) : Accountable,
    TransitionAccessor {
    /**
     * Where we next write to the int[] states; this increments by 2 for each added state because we
     * pack a pointer to the transitions array and a count of how many transitions leave the state.
     */
    private var nextState = 0

    /**
     * Where we next write to in int[] transitions; this increments by 3 for each added transition
     * because we pack min, max, dest in sequence.
     */
    private var nextTransition = 0

    /**
     * Current state we are adding transitions to; the caller must add all transitions for this state
     * before moving onto another state.
     */
    private var curState = -1

    /**
     * Index in the transitions array, where this states leaving transitions are stored, or -1 if this
     * state has not added any transitions yet, followed by number of transitions.
     */
    private var states: IntArray

    private var isAccept: BitSet

    /** Holds toState, min, max for each transition.  */
    private var transitions: IntArray

    /**
     * Returns true if this automaton is deterministic (for ever state there is only one transition
     * for each label).
     */
    /** True if no state has two transitions leaving with the same label.  */
    var isDeterministic: Boolean = true
        private set

    /** Create a new state.  */
    fun createState(): Int {
        growStates()
        if (isAccept.size() < nextState / 2 + 1) {
            val newBits = BitSet(nextState / 2 + 1)
            newBits.or(isAccept)
            isAccept = newBits
        }
        val state = nextState / 2
        states[nextState] = -1
        nextState += 2
        return state
    }

    /** Set or clear this state as an accept state.  */
    fun setAccept(state: Int, accept: Boolean) {
        Objects.checkIndex(state, this.numStates)
        // ensure bitset is sized to current number of states
        if (isAccept.size() < numStates) {
            val newBits = BitSet(numStates)
            newBits.or(isAccept)
            isAccept = newBits
        }
        isAccept.set(state, accept)
    }

    val sortedTransitions: Array<Array<Transition>>
        /**
         * Sugar to get all transitions for all states. This is object-heavy; it's better to iterate state
         * by state instead.
         */
        get() {
            val numStates = this.numStates
            val transitions: Array<Array<Transition?>?> = kotlin.arrayOfNulls<Array<Transition?>>(numStates)
            for (s in 0..<numStates) {
                val numTransitions = getNumTransitions(s)
                transitions[s] = kotlin.arrayOfNulls<Transition>(numTransitions)
                for (t in 0..<numTransitions) {
                    val transition: Transition = Transition()
                    getTransition(s, t, transition)
                    transitions[s]!![t] = transition
                }
            }

            return transitions as Array<Array<Transition>>
        }

    val acceptStates: BitSet
        /**
         * Returns accept states. If the bit is set then that state is an accept state.
         *
         *
         * expert: Use [.isAccept] instead, unless you really need to scan bits.
         *
         * @lucene.internal This method signature may change in the future
         */
        get() = isAccept

    /** Returns true if this state is an accept state.  */
    fun isAccept(state: Int): Boolean {
        return isAccept.get(state)
    }

    /** Add a new transition with min = max = label.  */
    fun addTransition(source: Int, dest: Int, label: Int) {
        addTransition(source, dest, label, label)
    }

    /** Add a new transition with the specified source, dest, min, max.  */
    fun addTransition(source: Int, dest: Int, min: Int, max: Int) {
        require(nextTransition % 3 == 0)

        val bounds = nextState / 2
        Objects.checkIndex(source, bounds)
        Objects.checkIndex(dest, bounds)

        growTransitions()
        if (curState != source) {
            if (curState != -1) {
                finishCurrentState()
            }

            // Move to next source:
            curState = source
            check(states[2 * curState] == -1) { "from state (" + source + ") already had transitions added" }
            require(states[2 * curState + 1] == 0)
            states[2 * curState] = nextTransition
        }

        transitions[nextTransition++] = dest
        transitions[nextTransition++] = min
        transitions[nextTransition++] = max

        // Increment transition count for this state
        states[2 * curState + 1]++
    }

    /**
     * Add a [virtual] epsilon transition between source and dest. Dest state must already have all
     * transitions added because this method simply copies those same transitions over to source.
     */
    fun addEpsilon(source: Int, dest: Int) {
        val t: Transition = Transition()
        val count = initTransition(dest, t)
        for (i in 0..<count) {
            getNextTransition(t)
            addTransition(source, t.dest, t.min, t.max)
        }
        if (isAccept(dest)) {
            setAccept(source, true)
        }
    }

    /**
     * Copies over all states/transitions from other. The states numbers are sequentially assigned
     * (appended).
     */
    fun copy(other: Automaton) {
        // Bulk copy and then fixup the state pointers:

        val stateOffset = this.numStates
        states = ArrayUtil.grow(states, nextState + other.nextState)
        /*java.lang.System.arraycopy(other.states, 0, states, nextState, other.nextState)*/
        other.states.copyInto(
            destination = states,
            destinationOffset = nextState,
            startIndex = 0,
            endIndex = other.nextState
        )

        run {
            var i = 0
            while (i < other.nextState) {
                if (states[nextState + i] != -1) {
                    states[nextState + i] += nextTransition
                }
                i += 2
            }
        }
        nextState += other.nextState
        val otherNumStates = other.numStates
        val otherAcceptStates: BitSet = other.acceptStates
        var state = 0
        while (state < otherNumStates && (otherAcceptStates.nextSetBit(state).also { state = it }) != -1) {
            setAccept(stateOffset + state, true)
            state++
        }

        // Bulk copy and then fixup dest for each transition:
        transitions = ArrayUtil.grow(transitions, nextTransition + other.nextTransition)
        /*java.lang.System.arraycopy(other.transitions, 0, transitions, nextTransition, other.nextTransition)*/
        other.transitions.copyInto(
            destination = transitions,
            destinationOffset = nextTransition,
            startIndex = 0,
            endIndex = other.nextTransition
        )

        var i = 0
        while (i < other.nextTransition) {
            transitions[nextTransition + i] += stateOffset
            i += 3
        }
        nextTransition += other.nextTransition

        if (other.isDeterministic == false) {
            this.isDeterministic = false
        }
    }

    /** Freezes the last state, sorting and reducing the transitions.  */
    private fun finishCurrentState() {
        val numTransitions = states[2 * curState + 1]
        require(numTransitions > 0)

        val offset = states[2 * curState]
        val start = offset / 3
        destMinMaxSorter.sort(start, start + numTransitions)

        // Reduce any "adjacent" transitions:
        var upto = 0
        var min = -1
        var max = -1
        var dest = -1

        for (i in 0..<numTransitions) {
            val tDest = transitions[offset + 3 * i]
            val tMin = transitions[offset + 3 * i + 1]
            val tMax = transitions[offset + 3 * i + 2]

            if (dest == tDest) {
                if (tMin <= max + 1) {
                    if (tMax > max) {
                        max = tMax
                    }
                } else {
                    if (dest != -1) {
                        transitions[offset + 3 * upto] = dest
                        transitions[offset + 3 * upto + 1] = min
                        transitions[offset + 3 * upto + 2] = max
                        upto++
                    }
                    min = tMin
                    max = tMax
                }
            } else {
                if (dest != -1) {
                    transitions[offset + 3 * upto] = dest
                    transitions[offset + 3 * upto + 1] = min
                    transitions[offset + 3 * upto + 2] = max
                    upto++
                }
                dest = tDest
                min = tMin
                max = tMax
            }
        }

        if (dest != -1) {
            // Last transition
            transitions[offset + 3 * upto] = dest
            transitions[offset + 3 * upto + 1] = min
            transitions[offset + 3 * upto + 2] = max
            upto++
        }

        nextTransition -= (numTransitions - upto) * 3
        states[2 * curState + 1] = upto

        // Sort transitions by min/max/dest:
        minMaxDestSorter.sort(start, start + upto)

        if (this.isDeterministic && upto > 1) {
            var lastMax = transitions[offset + 2]
            for (i in 1..<upto) {
                min = transitions[offset + 3 * i + 1]
                if (min <= lastMax) {
                    this.isDeterministic = false
                    break
                }
                lastMax = transitions[offset + 3 * i + 2]
            }
        }
    }

    /**
     * Finishes the current state; call this once you are done adding transitions for a state. This is
     * automatically called if you start adding transitions to a new source state, but for the last
     * state you add you need to this method yourself.
     */
    fun finishState() {
        if (curState != -1) {
            finishCurrentState()
            curState = -1
        }
    }

    // TODO: add finish() to shrink wrap the arrays?
    val numStates: Int
        /** How many states this automaton has.  */
        get() = nextState / 2

    val numTransitions: Int
        /** How many transitions this automaton has.  */
        get() = nextTransition / 3

    public override fun getNumTransitions(state: Int): Int {
        require(state >= 0)
        require(state < this.numStates)
        val count = states[2 * state + 1]
        if (count == -1) {
            return 0
        } else {
            return count
        }
    }

    private fun growStates() {
        if (nextState + 2 > states.size) {
            states = ArrayUtil.grow(states, nextState + 2)
        }
    }

    private fun growTransitions() {
        if (nextTransition + 3 > transitions.size) {
            transitions = ArrayUtil.grow(transitions, nextTransition + 3)
        }
    }

    /** Sorts transitions by dest, ascending, then min label ascending, then max label ascending  */
    private val destMinMaxSorter: Sorter = object : InPlaceMergeSorter() {
        private fun swapOne(i: Int, j: Int) {
            val x = transitions[i]
            transitions[i] = transitions[j]
            transitions[j] = x
        }

        protected override fun swap(i: Int, j: Int) {
            val iStart = 3 * i
            val jStart = 3 * j
            swapOne(iStart, jStart)
            swapOne(iStart + 1, jStart + 1)
            swapOne(iStart + 2, jStart + 2)
        }

        protected override fun compare(i: Int, j: Int): Int {
            val iStart = 3 * i
            val jStart = 3 * j

            // First dest:
            val iDest = transitions[iStart]
            val jDest = transitions[jStart]
            if (iDest < jDest) {
                return -1
            } else if (iDest > jDest) {
                return 1
            }

            // Then min:
            val iMin = transitions[iStart + 1]
            val jMin = transitions[jStart + 1]
            if (iMin < jMin) {
                return -1
            } else if (iMin > jMin) {
                return 1
            }

            // Then max:
            val iMax = transitions[iStart + 2]
            val jMax = transitions[jStart + 2]
            if (iMax < jMax) {
                return -1
            } else if (iMax > jMax) {
                return 1
            }

            return 0
        }
    }

    /** Sorts transitions by min label, ascending, then max label ascending, then dest ascending  */
    private val minMaxDestSorter: Sorter = object : InPlaceMergeSorter() {
        private fun swapOne(i: Int, j: Int) {
            val x = transitions[i]
            transitions[i] = transitions[j]
            transitions[j] = x
        }

        override fun swap(i: Int, j: Int) {
            val iStart = 3 * i
            val jStart = 3 * j
            swapOne(iStart, jStart)
            swapOne(iStart + 1, jStart + 1)
            swapOne(iStart + 2, jStart + 2)
        }

        override fun compare(i: Int, j: Int): Int {
            val iStart = 3 * i
            val jStart = 3 * j

            // First min:
            val iMin = transitions[iStart + 1]
            val jMin = transitions[jStart + 1]
            if (iMin < jMin) {
                return -1
            } else if (iMin > jMin) {
                return 1
            }

            // Then max:
            val iMax = transitions[iStart + 2]
            val jMax = transitions[jStart + 2]
            if (iMax < jMax) {
                return -1
            } else if (iMax > jMax) {
                return 1
            }

            // Then dest:
            val iDest = transitions[iStart]
            val jDest = transitions[jStart]
            if (iDest < jDest) {
                return -1
            } else if (iDest > jDest) {
                return 1
            }

            return 0
        }
    }

    /**
     * Constructor which creates an automaton with enough space for the given number of states and
     * transitions.
     *
     * @param numStates Number of states.
     * @param numTransitions Number of transitions.
     */
    /** Sole constructor; creates an automaton with no states.  */
    init {
        states = IntArray(numStates * 2)
        isAccept = BitSet(numStates)
        transitions = IntArray(numTransitions * 3)
    }

    public override fun initTransition(state: Int, t: Transition): Int {
        require(state < nextState / 2) { "state=" + state + " nextState=" + nextState }
        t.source = state
        t.transitionUpto = states[2 * state]
        return getNumTransitions(state)
    }

    public override fun getNextTransition(t: Transition) {
        // Make sure there is still a transition left:
        require((t.transitionUpto + 3 - states[2 * t.source]) <= 3 * states[2 * t.source + 1])

        // Make sure transitions are in fact sorted:
        require(transitionSorted(t))

        t.dest = transitions[t.transitionUpto++]
        t.min = transitions[t.transitionUpto++]
        t.max = transitions[t.transitionUpto++]
    }

    private fun transitionSorted(t: Transition): Boolean {
        val upto: Int = t.transitionUpto
        if (upto == states[2 * t.source]) {
            // Transition isn't initialized yet (this is the first transition); don't check:
            return true
        }

        val nextDest = transitions[upto]
        val nextMin = transitions[upto + 1]
        val nextMax = transitions[upto + 2]
        if (nextMin > t.min) {
            return true
        } else if (nextMin < t.min) {
            return false
        }

        // Min is equal, now test max:
        if (nextMax > t.max) {
            return true
        } else if (nextMax < t.max) {
            return false
        }

        // Max is also equal, now test dest:
        if (nextDest > t.dest) {
            return true
        } else if (nextDest < t.dest) {
            return false
        }

        // We should never see fully equal transitions here:
        return false
    }

    public override fun getTransition(state: Int, index: Int, t: Transition) {
        var i = states[2 * state] + 3 * index
        t.source = state
        t.dest = transitions[i++]
        t.min = transitions[i++]
        t.max = transitions[i++]
    }

    /*
  public void writeDot(String fileName) {
    if (fileName.indexOf('/') == -1) {
      fileName = "/l/la/lucene/core/" + fileName + ".dot";
    }
    try {
      PrintWriter pw = new PrintWriter(fileName);
      pw.println(toDot());
      pw.close();
    } catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }
  }
  */
    /**
     * Returns the dot (graphviz) representation of this automaton. This is extremely useful for
     * visualizing the automaton.
     */
    fun toDot(): String {
        val b: StringBuilder = StringBuilder()
        b.append("digraph Automaton {\n")
        b.append("  rankdir = LR\n")
        b.append("  node [width=0.2, height=0.2, fontsize=8]\n")
        val numStates = this.numStates
        if (numStates > 0) {
            b.append("  initial [shape=plaintext,label=\"\"]\n")
            b.append("  initial -> 0\n")
        }

        val t: Transition = Transition()

        for (state in 0..<numStates) {
            b.append("  ")
            b.append(state)
            if (isAccept(state)) {
                b.append(" [shape=doublecircle,label=\"").append(state).append("\"]\n")
            } else {
                b.append(" [shape=circle,label=\"").append(state).append("\"]\n")
            }
            val numTransitions = initTransition(state, t)
            // System.out.println("toDot: state " + state + " has " + numTransitions + " transitions;
            // t.nextTrans=" + t.transitionUpto);
            for (i in 0..<numTransitions) {
                getNextTransition(t)
                // System.out.println("  t.nextTrans=" + t.transitionUpto + " t=" + t);
                require(t.max >= t.min)
                b.append("  ")
                b.append(state)
                b.append(" -> ")
                b.append(t.dest)
                b.append(" [label=\"")
                appendCharString(t.min, b)
                if (t.max !== t.min) {
                    b.append('-')
                    appendCharString(t.max, b)
                }
                b.append("\"]\n")
                // System.out.println("  t=" + t);
            }
        }
        b.append('}')
        return b.toString()
    }


    /** Returns sorted array of all interval start points.  */
    fun getStartPoints(): IntArray {
        val pointset = IntHashSet()
        pointset.add(Character.MIN_CODE_POINT)
        // System.out.println("getStartPoints");
        var s = 0
        while (s < nextState) {
            var trans = states[s]
            val limit = trans + 3 * states[s + 1]
            // System.out.println("  state=" + (s/2) + " trans=" + trans + " limit=" + limit);
            while (trans < limit) {
                val min = transitions[trans + 1]
                val max = transitions[trans + 2]
                // System.out.println("    min=" + min);
                pointset.add(min)
                if (max < Character.MAX_CODE_POINT) {
                    pointset.add(max + 1)
                }
                trans += 3
            }
            s += 2
        }
        val points: IntArray = pointset.toArray()
        /*java.util.Arrays.sort(points)*/
        points.sort()
        return points
    }

    /**
     * Performs lookup in transitions, assuming determinism.
     *
     * @param state starting state
     * @param label codepoint to look up
     * @return destination state, -1 if no matching outgoing transition
     */
    fun step(state: Int, label: Int): Int {
        return next(state, 0, label, null)
    }

    /**
     * Looks for the next transition that matches the provided label, assuming determinism.
     *
     *
     * This method is similar to [.step] but is used more efficiently when
     * iterating over multiple transitions from the same source state. It keeps the latest reached
     * transition index in `transition.transitionUpto` so the next call to this method can
     * continue from there instead of restarting from the first transition.
     *
     * @param transition The transition to start the lookup from (inclusive, using its [     ][Transition.source] and [Transition.transitionUpto]). It is updated with the matched
     * transition; or with [Transition.dest] = -1 if no match.
     * @param label The codepoint to look up.
     * @return The destination state; or -1 if no matching outgoing transition.
     */
    fun next(transition: Transition, label: Int): Int {
        return next(transition.source, transition.transitionUpto, label, transition)
    }

    /**
     * Looks for the next transition that matches the provided label, assuming determinism.
     *
     * @param state The source state.
     * @param fromTransitionIndex The transition index to start the lookup from (inclusive); negative
     * interpreted as 0.
     * @param label The codepoint to look up.
     * @param transition The output transition to update with the matching transition; or null for no
     * update.
     * @return The destination state; or -1 if no matching outgoing transition.
     */
    private fun next(state: Int, fromTransitionIndex: Int, label: Int, transition: Transition?): Int {
        require(state >= 0)
        require(label >= 0)
        val stateIndex = 2 * state
        val firstTransitionIndex = states[stateIndex]
        val numTransitions = states[stateIndex + 1]

        // Since transitions are sorted,
        // binary search the transition for which label is within [minLabel, maxLabel].
        var low = max(fromTransitionIndex, 0)
        var high = numTransitions - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val transitionIndex = firstTransitionIndex + 3 * mid
            val minLabel = transitions[transitionIndex + 1]
            if (minLabel > label) {
                high = mid - 1
            } else {
                val maxLabel = transitions[transitionIndex + 2]
                if (maxLabel < label) {
                    low = mid + 1
                } else {
                    val destState = transitions[transitionIndex]
                    if (transition != null) {
                        transition.dest = destState
                        transition.min = minLabel
                        transition.max = maxLabel
                        transition.transitionUpto = mid
                    }
                    return destState
                }
            }
        }
        val destState = -1
        if (transition != null) {
            transition.dest = destState
            transition.transitionUpto = low
        }
        return destState
    }

    /**
     * Records new states and transitions and then [.finish] creates the [Automaton]. Use
     * this when you cannot create the Automaton directly because it's too restrictive to have to add
     * all transitions leaving each state at once.
     */
    class Builder @JvmOverloads constructor(numStates: Int = 16, numTransitions: Int = 16) {
        /** How many states this automaton has.  */
        var numStates: Int = 0
            private set
        private val isAccept: BitSet
        private var transitions: IntArray
        private var nextTransition = 0

        /** Add a new transition with min = max = label.  */
        fun addTransition(source: Int, dest: Int, label: Int) {
            addTransition(source, dest, label, label)
        }

        /** Add a new transition with the specified source, dest, min, max.  */
        fun addTransition(source: Int, dest: Int, min: Int, max: Int) {
            if (transitions.size < nextTransition + 4) {
                transitions = ArrayUtil.grow(transitions, nextTransition + 4)
            }
            transitions[nextTransition++] = source
            transitions[nextTransition++] = dest
            transitions[nextTransition++] = min
            transitions[nextTransition++] = max
        }

        /**
         * Add a [virtual] epsilon transition between source and dest. Dest state must already have all
         * transitions added because this method simply copies those same transitions over to source.
         */
        fun addEpsilon(source: Int, dest: Int) {
            var upto = 0
            while (upto < nextTransition) {
                if (transitions[upto] == dest) {
                    addTransition(
                        source, transitions[upto + 1], transitions[upto + 2], transitions[upto + 3]
                    )
                }
                upto += 4
            }
            if (isAccept(dest)) {
                setAccept(source, true)
            }
        }

        /**
         * Sorts transitions first then min label ascending, then max label ascending, then dest
         * ascending
         */
        private val sorter: Sorter = object : InPlaceMergeSorter() {
            private fun swapOne(i: Int, j: Int) {
                val x = transitions[i]
                transitions[i] = transitions[j]
                transitions[j] = x
            }

            protected override fun swap(i: Int, j: Int) {
                val iStart = 4 * i
                val jStart = 4 * j
                swapOne(iStart, jStart)
                swapOne(iStart + 1, jStart + 1)
                swapOne(iStart + 2, jStart + 2)
                swapOne(iStart + 3, jStart + 3)
            }

            protected override fun compare(i: Int, j: Int): Int {
                val iStart = 4 * i
                val jStart = 4 * j

                // First src:
                val iSrc = transitions[iStart]
                val jSrc = transitions[jStart]
                if (iSrc < jSrc) {
                    return -1
                } else if (iSrc > jSrc) {
                    return 1
                }

                // Then min:
                val iMin = transitions[iStart + 2]
                val jMin = transitions[jStart + 2]
                if (iMin < jMin) {
                    return -1
                } else if (iMin > jMin) {
                    return 1
                }

                // Then max:
                val iMax = transitions[iStart + 3]
                val jMax = transitions[jStart + 3]
                if (iMax < jMax) {
                    return -1
                } else if (iMax > jMax) {
                    return 1
                }

                // First dest:
                val iDest = transitions[iStart + 1]
                val jDest = transitions[jStart + 1]
                if (iDest < jDest) {
                    return -1
                } else if (iDest > jDest) {
                    return 1
                }

                return 0
            }
        }

        /**
         * Constructor which creates a builder with enough space for the given number of states and
         * transitions.
         *
         * @param numStates Number of states.
         * @param numTransitions Number of transitions.
         */
        /** Default constructor, pre-allocating for 16 states and transitions.  */
        init {
            isAccept = BitSet(numStates)
            transitions = IntArray(numTransitions * 4)
        }

        /** Compiles all added states and transitions into a new `Automaton` and returns it.  */
        fun finish(): Automaton {
            // Create automaton with the correct size.
            val numStates = this.numStates
            val numTransitions = nextTransition / 4
            val a = Automaton(numStates, numTransitions)

            // Create all states.
            for (state in 0..<numStates) {
                a.createState()
                a.setAccept(state, isAccept(state))
            }

            // Create all transitions
            sorter.sort(0, numTransitions)
            var upto = 0
            while (upto < nextTransition) {
                a.addTransition(
                    transitions[upto], transitions[upto + 1], transitions[upto + 2], transitions[upto + 3]
                )
                upto += 4
            }

            a.finishState()

            return a
        }

        /** Create a new state.  */
        fun createState(): Int {
            return this.numStates++
        }

        /** Set or clear this state as an accept state.  */
        fun setAccept(state: Int, accept: Boolean) {
            Objects.checkIndex(state, this.numStates)
            this.isAccept.set(state, accept)
        }

        /** Returns true if this state is an accept state.  */
        fun isAccept(state: Int): Boolean {
            return this.isAccept.get(state)
        }

        /** Copies over all states/transitions from other.  */
        fun copy(other: Automaton) {
            val offset = this.numStates
            val otherNumStates = other.numStates

            // Copy all states
            copyStates(other)

            // Copy all transitions
            val t: Transition = Transition()
            for (s in 0..<otherNumStates) {
                val count = other.initTransition(s, t)
                for (i in 0..<count) {
                    other.getNextTransition(t)
                    addTransition(offset + s, offset + t.dest, t.min, t.max)
                }
            }
        }

        /** Copies over all states from other.  */
        fun copyStates(other: Automaton) {
            val otherNumStates = other.numStates
            for (s in 0..<otherNumStates) {
                val newState = createState()
                setAccept(newState, other.isAccept(s))
            }
        }
    }

    public override fun ramBytesUsed(): Long {
        // TODO: BitSet RAM usage (isAccept.size()/8) isn't fully accurate...
        return (RamUsageEstimator.NUM_BYTES_OBJECT_HEADER
                + RamUsageEstimator.sizeOf(states)
                + RamUsageEstimator.sizeOf(transitions)
                + RamUsageEstimator.NUM_BYTES_OBJECT_HEADER
                + (isAccept.size() / 8)
                + RamUsageEstimator.NUM_BYTES_OBJECT_REF
                + 2L * RamUsageEstimator.NUM_BYTES_OBJECT_REF + 3 * Int.SIZE_BYTES + 1)
    }

    companion object {
        @OptIn(ExperimentalStdlibApi::class)
        fun appendCharString(c: Int, b: StringBuilder) {
            if (c >= 0x21 && c <= 0x7e && c != '\\'.code && c != '"'.code) b.appendCodePoint(c)
            else {
                b.append("\\\\U")
                val s: String = c.toHexString()
                if (c < 0x10) b.append("0000000").append(s)
                else if (c < 0x100) b.append("000000").append(s)
                else if (c < 0x1000) b.append("00000").append(s)
                else if (c < 0x10000) b.append("0000").append(s)
                else if (c < 0x100000) b.append("000").append(s)
                else if (c < 0x1000000) b.append("00").append(s)
                else if (c < 0x10000000) b.append("0").append(s)
                else b.append(s)
            }
        }
    }
}
