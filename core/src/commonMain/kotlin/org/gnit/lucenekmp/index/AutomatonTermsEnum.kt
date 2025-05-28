package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.IntsRefBuilder
import org.gnit.lucenekmp.util.StringHelper
import org.gnit.lucenekmp.util.automaton.ByteRunnable
import org.gnit.lucenekmp.util.automaton.CompiledAutomaton
import org.gnit.lucenekmp.util.automaton.Transition
import org.gnit.lucenekmp.util.automaton.TransitionAccessor
import kotlin.experimental.and


/**
 * A FilteredTermsEnum that enumerates terms based upon what is accepted by a DFA.
 *
 *
 * The algorithm is such:
 *
 *
 *  1. As long as matches are successful, keep reading sequentially.
 *  1. When a match fails, skip to the next string in lexicographic order that does not enter a
 * reject state.
 *
 *
 *
 * The algorithm does not attempt to actually skip to the next string that is completely
 * accepted. This is not possible when the language accepted by the FSM is not finite (i.e. *
 * operator).
 *
 * @lucene.internal
 */
open class AutomatonTermsEnum(tenum: TermsEnum?, compiled: CompiledAutomaton) : FilteredTermsEnum(tenum!!) {
    // a tableized array-based form of the DFA
    private val byteRunnable: ByteRunnable

    // common suffix of the automaton
    private val commonSuffixRef: BytesRef?

    // true if the automaton accepts a finite language
    private val finite: Boolean

    // array of sorted transitions for each state, indexed by state number
    private val transitionAccessor: TransitionAccessor

    // Used for visited state tracking: each short records gen when we last
    // visited the state; we use gens to avoid having to clear
    private var visited: ShortArray?
    private var curGen: Short = 0

    // the reference used for seeking forwards through the term dictionary
    private val seekBytesRef: BytesRefBuilder = BytesRefBuilder()

    // true if we are enumerating an infinite portion of the DFA.
    // in this case it is faster to drive the query based on the terms dictionary.
    // when this is true, linearUpperBound indicate the end of range
    // of terms where we should simply do sequential reads instead.
    private var linear = false
    private val linearUpperBound: BytesRef = BytesRef()
    private val transition: Transition = Transition()
    private val savedStates: IntsRefBuilder = IntsRefBuilder()

    /**
     * Construct an enumerator based upon an automaton, enumerating the specified field, working on a
     * supplied TermsEnum
     *
     * @lucene.experimental
     * @param compiled CompiledAutomaton
     */
    init {
        require(compiled.type === CompiledAutomaton.AUTOMATON_TYPE.NORMAL) { "please use CompiledAutomaton.getTermsEnum instead" }
        this.finite = compiled.finite
        this.byteRunnable = compiled.getByteRunnable()!!
        this.transitionAccessor = compiled.getTransitionAccessor()!!
        this.commonSuffixRef = compiled.commonSuffixRef!!

        // No need to track visited states for a finite language without loops.
        visited = if (finite) null else ShortArray(byteRunnable.size)
    }

    /** Records the given state has been visited.  */
    private fun setVisited(state: Int) {
        if (!finite) {
            if (state >= visited!!.size) {
                visited = ArrayUtil.grow(visited!!, state + 1)
            }
            visited!![state] = curGen
        }
    }

    /** Indicates whether the given state has been visited.  */
    private fun isVisited(state: Int): Boolean {
        return !finite && state < visited!!.size && visited!![state] == curGen
    }

    /**
     * Returns true if the term matches the automaton. Also stashes away the term to assist with smart
     * enumeration.
     */
    override fun accept(term: BytesRef): AcceptStatus {
        if (commonSuffixRef == null || StringHelper.endsWith(term, commonSuffixRef)) {
            return if (byteRunnable.run(
                    term.bytes,
                    term.offset,
                    term.length
                )
            ) if (linear) AcceptStatus.YES else AcceptStatus.YES_AND_SEEK
            else if (linear && term < linearUpperBound)
                AcceptStatus.NO
            else
                AcceptStatus.NO_AND_SEEK
        } else {
            return if (linear && term < linearUpperBound)
                AcceptStatus.NO
            else
                AcceptStatus.NO_AND_SEEK
        }
    }

    @Throws(IOException::class)
    override fun nextSeekTerm(term: BytesRef?): BytesRef? {
        // System.out.println("ATE.nextSeekTerm term=" + term);
        if (term == null) {
            require(seekBytesRef.length() == 0)
            // return the empty term, as it's valid
            if (byteRunnable.isAccept(0)) {
                return seekBytesRef.get()
            }
        } else {
            seekBytesRef.copyBytes(term)
        }

        // seek to the next possible string;
        return if (nextString()) {
            seekBytesRef.get() // reposition
        } else {
            null // no more possible strings can match
        }
    }

    /**
     * Sets the enum to operate in linear fashion, as we have found a looping transition at position:
     * we set an upper bound and act like a TermRangeQuery for this portion of the term space.
     */
    private fun setLinear(position: Int) {
        require(!linear)

        var state = 0
        var maxInterval = 0xff
        // System.out.println("setLinear pos=" + position + " seekbytesRef=" + seekBytesRef);
        for (i in 0..<position) {
            state = byteRunnable.step(state, (seekBytesRef.byteAt(i) and 0xff.toByte()).toInt())
            require(state >= 0) { "state=$state" }
        }
        val numTransitions: Int = transitionAccessor.getNumTransitions(state)
        transitionAccessor.initTransition(state, transition)
        for (i in 0..<numTransitions) {
            transitionAccessor.getNextTransition(transition)
            if (transition.min <= (seekBytesRef.byteAt(position) and 0xff.toByte())
                && (seekBytesRef.byteAt(position) and 0xff.toByte()) <= transition.max
            ) {
                maxInterval = transition.max
                break
            }
        }
        // 0xff terms don't get the optimization... not worth the trouble.
        if (maxInterval != 0xff) maxInterval++
        val length = position + 1 /* position + maxTransition */
        if (linearUpperBound.bytes.size < length) {
            linearUpperBound.bytes = ByteArray(ArrayUtil.oversize(length, Byte.SIZE_BYTES))
        }
        /*java.lang.System.arraycopy(seekBytesRef.bytes(), 0, linearUpperBound.bytes, 0, position)*/
        seekBytesRef.bytes().copyInto(
            linearUpperBound.bytes,
            destinationOffset = 0,
            startIndex = 0,
            endIndex = position
        )

        linearUpperBound.bytes[position] = maxInterval.toByte()
        linearUpperBound.length = length

        linear = true
    }

    /**
     * Increments the byte buffer to the next String in binary order after s that will not put the
     * machine into a reject state. If such a string does not exist, returns false.
     *
     *
     * The correctness of this method depends upon the automaton being deterministic, and having no
     * transitions to dead states.
     *
     * @return true if more possible solutions exist for the DFA
     */
    private fun nextString(): Boolean {
        var state: Int
        var pos = 0
        savedStates.grow(seekBytesRef.length() + 1)
        savedStates.setIntAt(0, 0)

        while (true) {
            if (!finite && (++curGen).toInt() == 0) {
                // Clear the visited states every time curGen wraps (so very infrequently to not impact
                // average perf).
                /*java.util.Arrays.fill(visited, -1.toShort())*/
                visited!!.fill(element = -1)
            }
            linear = false
            // walk the automaton until a character is rejected.
            state = savedStates.intAt(pos)
            while (pos < seekBytesRef.length()) {
                setVisited(state)
                val nextState: Int = byteRunnable.step(state, (seekBytesRef.byteAt(pos) and 0xff.toByte()).toInt())
                if (nextState == -1) break
                savedStates.setIntAt(pos + 1, nextState)
                // we found a loop, record it for faster enumeration
                if (!linear && isVisited(nextState)) {
                    setLinear(pos)
                }
                state = nextState
                pos++
            }

            // take the useful portion, and the last non-reject state, and attempt to
            // append characters that will match.
            if (nextString(state, pos)) {
                return true
            } else {
                /* no more solutions exist from this useful portion, backtrack */
                if ((backtrack(pos).also { pos = it }) < 0) {
                    /* no more solutions at all */
                    return false
                }
                val newState: Int =
                    byteRunnable.step(savedStates.intAt(pos), (seekBytesRef.byteAt(pos) and 0xff.toByte()).toInt())
                if (newState >= 0 && byteRunnable.isAccept(newState)) {
                    /* String is good to go as-is */
                    return true
                }
                /* else advance further */
                // TODO: paranoia? if we backtrack thru an infinite DFA, the loop detection is important!
                // for now, restart from scratch for all infinite DFAs
                if (!finite) pos = 0
            }
        }
    }

    /**
     * Returns the next String in lexicographic order that will not put the machine into a reject
     * state.
     *
     *
     * This method traverses the DFA from the given position in the String, starting at the given
     * state.
     *
     *
     * If this cannot satisfy the machine, returns false. This method will walk the minimal path,
     * in lexicographic order, as long as possible.
     *
     *
     * If this method returns false, then there might still be more solutions, it is necessary to
     * backtrack to find out.
     *
     * @param state current non-reject state
     * @param position useful portion of the string
     * @return true if more possible solutions exist for the DFA from this position
     */
    private fun nextString(state: Int, position: Int): Boolean {
        /*
     * the next lexicographic character must be greater than the existing
     * character, if it exists.
     */
        var state = state
        var c = 0
        if (position < seekBytesRef.length()) {
            c = (seekBytesRef.byteAt(position) and 0xff.toByte()).toInt()
            // if the next byte is 0xff and is not part of the useful portion,
            // then by definition it puts us in a reject state, and therefore this
            // path is dead. there cannot be any higher transitions. backtrack.
            if (c++ == 0xff) return false
        }

        seekBytesRef.setLength(position)
        setVisited(state)

        val numTransitions: Int = transitionAccessor.getNumTransitions(state)
        transitionAccessor.initTransition(state, transition)

        // find the minimal path (lexicographic order) that is >= c
        for (i in 0..<numTransitions) {
            transitionAccessor.getNextTransition(transition)
            if (transition.max >= c) {
                val nextChar: Int = kotlin.math.max(c, transition.min)
                // append either the next sequential char, or the minimum transition
                seekBytesRef.append(nextChar.toByte())
                state = transition.dest
                /*
         * as long as is possible, continue down the minimal path in
         * lexicographic order. if a loop or accept state is encountered, stop.
         */
                while (!isVisited(state) && !byteRunnable.isAccept(state)) {
                    setVisited(state)
                    /*
           * Note: we work with a DFA with no transitions to dead states.
           * so the below is ok, if it is not an accept state,
           * then there MUST be at least one transition.
           */
                    transitionAccessor.initTransition(state, transition)
                    transitionAccessor.getNextTransition(transition)
                    state = transition.dest

                    // append the minimum transition
                    seekBytesRef.append(transition.min.toByte())

                    // we found a loop, record it for faster enumeration
                    if (!linear && isVisited(state)) {
                        setLinear(seekBytesRef.length() - 1)
                    }
                }
                return true
            }
        }
        return false
    }

    /**
     * Attempts to backtrack thru the string after encountering a dead end at some given position.
     * Returns false if no more possible strings can match.
     *
     * @param position current position in the input String
     * @return `position >= 0` if more possible solutions exist for the DFA
     */
    private fun backtrack(position: Int): Int {
        var position = position
        while (position-- > 0) {
            var nextChar: Int = (seekBytesRef.byteAt(position) and 0xff.toByte()).toInt()
            // if a character is 0xff it's a dead-end too,
            // because there is no higher character in binary sort order.
            if (nextChar++ != 0xff) {
                seekBytesRef.setByteAt(position, nextChar.toByte())
                seekBytesRef.setLength(position + 1)
                return position
            }
        }
        return -1 /* all solutions exhausted */
    }
}
