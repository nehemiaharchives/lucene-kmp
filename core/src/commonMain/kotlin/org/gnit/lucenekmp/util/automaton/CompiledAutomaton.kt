package org.gnit.lucenekmp.util.automaton

import kotlinx.io.IOException
import org.gnit.lucenekmp.index.SingleTermsEnum
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.internal.hppc.IntArrayList
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.QueryVisitor
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.IntsRef
import org.gnit.lucenekmp.util.RamUsageEstimator
import org.gnit.lucenekmp.util.StringHelper
import org.gnit.lucenekmp.util.UnicodeUtil
import kotlin.experimental.and


/**
 * Immutable class holding compiled details for a given Automaton. The Automaton could either be
 * deterministic or non-deterministic, For deterministic automaton, it must not have dead states but
 * is not necessarily minimal. And will be executed using [ByteRunAutomaton] For
 * non-deterministic automaton, it will be executed using [NFARunAutomaton]
 *
 * @lucene.experimental
 */
class CompiledAutomaton(automaton: Automaton, finite: Boolean, simplify: Boolean, isBinary: Boolean) : Accountable {
    /**
     * Automata are compiled into different internal forms for the most efficient execution depending
     * upon the language they accept.
     */
    enum class AUTOMATON_TYPE {
        /** Automaton that accepts no strings.  */
        NONE,

        /** Automaton that accepts all possible strings.  */
        ALL,

        /** Automaton that accepts only a single fixed string.  */
        SINGLE,

        /** Catch-all for any other automata.  */
        NORMAL
    }

    /** If simplify is true this will be the "simplified" type; else, this is NORMAL  */
    var type: AUTOMATON_TYPE

    /** For [AUTOMATON_TYPE.SINGLE] this is the singleton term.  */
    var term: BytesRef?

    /**
     * Matcher for quickly determining if a byte[] is accepted. only valid for [ ][AUTOMATON_TYPE.NORMAL].
     */
    var runAutomaton: ByteRunAutomaton? = null

    /**
     * Two dimensional array of transitions, indexed by state number for traversal. The state
     * numbering is consistent with [.runAutomaton]. Only valid for [ ][AUTOMATON_TYPE.NORMAL].
     */
    var automaton: Automaton? = null

    /**
     * Matcher directly run on a NFA, it will determinize the state on need and caches it, note that
     * this field and [.runAutomaton] will not be non-null at the same time
     *
     *
     * TODO: merge this with runAutomaton
     */
    var nfaRunAutomaton: NFARunAutomaton? = null

    /**
     * Shared common suffix accepted by the automaton. Only valid for [AUTOMATON_TYPE.NORMAL],
     * and only when the automaton accepts an infinite language. This will be null if the common
     * prefix is length 0.
     */
    var commonSuffixRef: BytesRef? = null

    /**
     * Indicates if the automaton accepts a finite set of strings. Only valid for [ ][AUTOMATON_TYPE.NORMAL].
     */
    var finite: kotlin.Boolean

    /** Which state, if any, accepts all suffixes, else -1.  */
    var sinkState: kotlin.Int = 0

    /** Create this, passing simplify=true, so that we try to simplify the automaton.  */
    constructor(automaton: Automaton) : this(automaton, false, true)

    /**
     * Create this. If simplify is true, we run possibly expensive operations to determine if the
     * automaton is one the cases in [CompiledAutomaton.AUTOMATON_TYPE]. Set finite to true if
     * the automaton is finite, otherwise set to false if infinite or you don't know.
     */
    constructor(automaton: Automaton, finite: kotlin.Boolean, simplify: kotlin.Boolean) : this(
        automaton,
        finite,
        simplify,
        false
    )

    private val transition: Transition = Transition()

    /**
     * Create this. If simplify is true, we run possibly expensive operations to determine if the
     * automaton is one the cases in [CompiledAutomaton.AUTOMATON_TYPE]. Set finite to true if
     * the automaton is finite, otherwise set to false if infinite or you don't know.
     */
    init {

        var automaton: Automaton = automaton
        if (automaton.numStates == 0) {
            automaton = Automaton()
            automaton.createState()
        }

        // simplify requires a DFA
        if (simplify && automaton.isDeterministic) {

            // Test whether the automaton is a "simple" form and
            // if so, don't create a runAutomaton.  Note that on a
            // large automaton these tests could be costly:

            if (Operations.isEmpty(automaton)) {
                // matches nothing
                type = CompiledAutomaton.AUTOMATON_TYPE.NONE
                term = null
                commonSuffixRef = null
                runAutomaton = null
                this.automaton = null
                this.finite = true
                sinkState = -1
                nfaRunAutomaton = null

            }

            val isTotal: kotlin.Boolean

            // NOTE: only approximate, because automaton may not be minimal:
            if (isBinary) {
                isTotal = Operations.isTotal(automaton, 0, 0xff)
            } else {
                isTotal = Operations.isTotal(automaton)
            }

            if (isTotal) {
                // matches all possible strings
                type = CompiledAutomaton.AUTOMATON_TYPE.ALL
                term = null
                commonSuffixRef = null
                runAutomaton = null
                this.automaton = null
                this.finite = false
                sinkState = -1
                nfaRunAutomaton = null

            }

            val singleton: IntsRef? = Operations.getSingleton(automaton)

            if (singleton != null) {
                // matches a fixed string
                type = CompiledAutomaton.AUTOMATON_TYPE.SINGLE
                commonSuffixRef = null
                runAutomaton = null
                this.automaton = null
                this.finite = true

                if (isBinary) {
                    term = StringHelper.intsRefToBytesRef(singleton)
                } else {
                    term =
                        BytesRef(
                            UnicodeUtil.newString(singleton.ints, singleton.offset, singleton.length)
                        )
                }
                sinkState = -1
                nfaRunAutomaton = null

            }
        }

        type = CompiledAutomaton.AUTOMATON_TYPE.NORMAL
        term = null

        this.finite = finite

        var binary: Automaton
        if (isBinary) {
            // Caller already built binary automaton themselves, e.g. PrefixQuery
            // does this since it can be provided with a binary (not necessarily
            // UTF8!) term:
            binary = automaton
        } else {
            // Incoming automaton is unicode, and we must convert to UTF8 to match what's in the index:
            binary = UTF32ToUTF8().convert(automaton)
        }

        // compute a common suffix for infinite DFAs, this is an optimization for "leading wildcard"
        // so don't burn cycles on it if the DFA is finite, or largeish
        if (this.finite || automaton.numStates + automaton.numTransitions > 1000) {
            commonSuffixRef = null
        } else {
            val suffix: BytesRef = Operations.getCommonSuffixBytesRef(binary)
            if (suffix.length == 0) {
                commonSuffixRef = null
            } else {
                commonSuffixRef = suffix
            }
        }

        if (automaton.isDeterministic == false && binary.isDeterministic == false) {
            this.automaton = null
            this.runAutomaton = null
            this.sinkState = -1
            this.nfaRunAutomaton = NFARunAutomaton(binary, 0xff)
        } else {
            // We already had a DFA (or threw exception), according to mike UTF32toUTF8 won't "blow up"
            binary = Operations.determinize(binary, kotlin.Int.Companion.MAX_VALUE)
            runAutomaton = ByteRunAutomaton(binary, true)

            this.automaton = runAutomaton!!.automaton

            // TODO: this is a bit fragile because if the automaton is not minimized there could be more
            // than 1 sink state but auto-prefix will fail
            // to run for those:
            sinkState = CompiledAutomaton.Companion.findSinkState(this.automaton!!)
            nfaRunAutomaton = null
        }
    }

    // private static final boolean DEBUG = BlockTreeTermsWriter.DEBUG;
    private fun addTail(state: kotlin.Int, term: BytesRefBuilder, idx: kotlin.Int, leadLabel: kotlin.Int): BytesRef {
        // System.out.println("addTail state=" + state + " term=" + term.utf8ToString() + " idx=" + idx
        // + " leadLabel=" + (char) leadLabel);
        // System.out.println(automaton.toDot());
        // Find biggest transition that's < label
        // TODO: use binary search here
        var state: kotlin.Int = state
        var idx: kotlin.Int = idx
        var maxIndex: kotlin.Int = -1
        var numTransitions: kotlin.Int = automaton!!.initTransition(state, transition)
        for (i in 0..<numTransitions) {
            automaton!!.getNextTransition(transition)
            if (transition.min < leadLabel) {
                maxIndex = i
            } else {
                // Transitions are always sorted
                break
            }
        }

        // System.out.println("  maxIndex=" + maxIndex);
        require(maxIndex != -1)
        automaton!!.getTransition(state, maxIndex, transition)

        // Append floorLabel
        val floorLabel: kotlin.Int
        if (transition.max > leadLabel - 1) {
            floorLabel = leadLabel - 1
        } else {
            floorLabel = transition.max
        }
        // System.out.println("  floorLabel=" + (char) floorLabel);
        term.grow(1 + idx)
        // if (DEBUG) System.out.println("  add floorLabel=" + (char) floorLabel + " idx=" + idx);
        term.setByteAt(idx, floorLabel.toByte())

        state = transition.dest
        // System.out.println("  dest: " + state);
        idx++

        // Push down to last accept state
        while (true) {
            numTransitions = automaton!!.getNumTransitions(state)
            if (numTransitions == 0) {
                // System.out.println("state=" + state + " 0 trans");
                require(runAutomaton!!.isAccept(state))
                term.setLength(idx)
                // if (DEBUG) System.out.println("  return " + term.utf8ToString());
                return term.get()
            } else {
                // We are pushing "top" -- so get last label of
                // last transition:
                // System.out.println("get state=" + state + " numTrans=" + numTransitions);
                automaton!!.getTransition(state, numTransitions - 1, transition)
                term.grow(1 + idx)
                // if (DEBUG) System.out.println("  push maxLabel=" + (char) lastTransition.max + " idx=" +
                // idx);
                // System.out.println("  add trans dest=" + scratch.dest + " label=" + (char) scratch.max);
                term.setByteAt(idx, transition.max as kotlin.Byte)
                state = transition.dest
                idx++
            }
        }
    }

    // TODO: should this take startTerm too?  This way
    // Terms.intersect could forward to this method if type !=
    // NORMAL:
    /**
     * Return a [TermsEnum] intersecting the provided [Terms] with the terms accepted by
     * this automaton.
     */
    @kotlin.Throws(IOException::class)
    fun getTermsEnum(terms: Terms): TermsEnum? {
        when (type) {
            AUTOMATON_TYPE.NONE -> return TermsEnum.EMPTY
            AUTOMATON_TYPE.ALL -> return terms.iterator()
            AUTOMATON_TYPE.SINGLE -> return SingleTermsEnum(terms.iterator(), term)
            AUTOMATON_TYPE.NORMAL -> return terms.intersect(this, null)
            else ->         // unreachable
                throw RuntimeException("unhandled case")
        }
    }

    /** Report back to a QueryVisitor how this automaton matches terms  */
    fun visit(visitor: QueryVisitor, parent: Query, field: String) {
        if (visitor.acceptField(field)) {
            when (type) {
                AUTOMATON_TYPE.NORMAL -> visitor.consumeTermsMatching(parent, field, { runAutomaton!! })
                AUTOMATON_TYPE.NONE -> {}
                AUTOMATON_TYPE.ALL -> visitor.consumeTermsMatching(
                    parent, field, { ByteRunAutomaton(Automata.makeAnyString()) })

                AUTOMATON_TYPE.SINGLE -> visitor.consumeTerms(parent, Term(field, term!!))
            }
        }
    }

    /**
     * Finds largest term accepted by this Automaton, that's &lt;= the provided input term. The result
     * is placed in output; it's fine for output and input to point to the same bytes. The returned
     * result is either the provided output, or null if there is no floor term (ie, the provided input
     * term is before the first term accepted by this Automaton).
     */
    fun floor(input: BytesRef, output: BytesRefBuilder): BytesRef? {

        // if (DEBUG) System.out.println("CA.floor input=" + input.utf8ToString());

        var state = 0

        // Special case empty string:
        if (input.length == 0) {
            if (runAutomaton!!.isAccept(state)) {
                output.clear()
                return output.get()
            } else {
                return null
            }
        }

        val stack = IntArrayList()

        var idx = 0
        while (true) {
            var label: Int = (input.bytes[input.offset + idx] and 0xff.toByte()).toInt()
            var nextState: Int = runAutomaton!!.step(state, label)

            // if (DEBUG) System.out.println("  cycle label=" + (char) label + " nextState=" + nextState);
            if (idx == input.length - 1) {
                if (nextState != -1 && runAutomaton!!.isAccept(nextState)) {
                    // Input string is accepted
                    output.grow(1 + idx)
                    output.setByteAt(idx, label.toByte())
                    output.setLength(input.length)
                    // if (DEBUG) System.out.println("  input is accepted; return term=" +
                    // output.utf8ToString());
                    return output.get()
                } else {
                    nextState = -1
                }
            }

            if (nextState == -1) {

                // Pop back to a state that has a transition
                // <= our label:

                while (true) {
                    val numTransitions: kotlin.Int = automaton!!.getNumTransitions(state)
                    if (numTransitions == 0) {
                        require(runAutomaton!!.isAccept(state))
                        output.setLength(idx)
                        // if (DEBUG) System.out.println("  return " + output.utf8ToString());
                        return output.get()
                    } else {
                        automaton!!.getTransition(state, 0, transition)

                        if (label - 1 < transition.min) {

                            if (runAutomaton!!.isAccept(state)) {
                                output.setLength(idx)
                                // if (DEBUG) System.out.println("  return " + output.utf8ToString());
                                return output.get()
                            }
                            // pop
                            if (stack.size() == 0) {
                                // if (DEBUG) System.out.println("  pop ord=" + idx + " return null");
                                return null
                            } else {
                                state = stack.removeLast()
                                idx--
                                // if (DEBUG) System.out.println("  pop ord=" + (idx+1) + " label=" + (char) label +
                                // " first trans.min=" + (char) transitions[0].min);
                                label = (input.bytes[input.offset + idx] and 0xff.toByte()).toInt()
                            }
                        } else {
                            // if (DEBUG) System.out.println("  stop pop ord=" + idx + " first trans.min=" +
                            // (char) transitions[0].min);
                            break
                        }
                    }
                }

                // if (DEBUG) System.out.println("  label=" + (char) label + " idx=" + idx);
                return addTail(state, output, idx, label)

            } else {
                output.grow(1 + idx)
                output.setByteAt(idx, label.toByte())
                stack.add(state)
                state = nextState
                idx++
            }
        }
    }

    /**
     * Get a [ByteRunnable] instance, it will be different depending on whether a NFA or DFA is
     * passed in, and does not guarantee returning non-null object
     */
    fun getByteRunnable(): ByteRunnable? {
        // they can be both null but not both non-null
        require(nfaRunAutomaton == null || runAutomaton == null)
        if (nfaRunAutomaton == null) {
            return runAutomaton
        }
        return nfaRunAutomaton
    }

    /**
     * Get a [TransitionAccessor] instance, it will be different depending on whether a NFA or
     * DFA is passed in, and does not guarantee returning non-null object
     */
    fun getTransitionAccessor(): TransitionAccessor? {
        // they can be both null but not both non-null
        require(nfaRunAutomaton == null || automaton == null)
        return if (nfaRunAutomaton == null) automaton else nfaRunAutomaton
    }

    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        result = prime * result + (if (runAutomaton == null) 0 else runAutomaton.hashCode())
        result = prime * result + (if (nfaRunAutomaton == null) 0 else nfaRunAutomaton.hashCode())
        result = prime * result + (if (term == null) 0 else term.hashCode())
        result = prime * result + (if (type == null) 0 else type.hashCode())
        return result
    }

    override fun equals(obj: Any?): Boolean {
        if (this == obj) return true
        if (obj == null) return false
        if (this::class != obj::class) return false
        val other: CompiledAutomaton = obj as CompiledAutomaton
        if (type != other.type) return false
        if (type == AUTOMATON_TYPE.SINGLE) {
            if (!term!!.equals(other.term)) return false
        } else if (type == AUTOMATON_TYPE.NORMAL) {
            return runAutomaton == other.runAutomaton
                    && nfaRunAutomaton == other.nfaRunAutomaton
        }

        return true
    }

    override fun ramBytesUsed(): kotlin.Long {
        return (CompiledAutomaton.Companion.BASE_RAM_BYTES
                + RamUsageEstimator.sizeOfObject(automaton)
                + RamUsageEstimator.sizeOfObject(commonSuffixRef)
                + RamUsageEstimator.sizeOfObject(runAutomaton)
                + RamUsageEstimator.sizeOfObject(nfaRunAutomaton)
                + RamUsageEstimator.sizeOfObject(term)
                + RamUsageEstimator.sizeOfObject(transition))
    }

    companion object {
        private val BASE_RAM_BYTES: kotlin.Long = RamUsageEstimator.shallowSizeOfInstance(CompiledAutomaton::class)

        /** Returns sink state, if present, else -1.  */
        private fun findSinkState(automaton: Automaton): Int {
            val numStates: Int = automaton.numStates
            val t = Transition()
            var foundState: Int = -1
            for (s in 0..<numStates) {
                if (automaton.isAccept(s)) {
                    val count: Int = automaton.initTransition(s, t)
                    var isSinkState = false
                    for (i in 0..<count) {
                        automaton.getNextTransition(t)
                        if (t.dest == s && t.min == 0 && t.max == 0xff) {
                            isSinkState = true
                            break
                        }
                    }
                    if (isSinkState) {
                        foundState = s
                        break
                    }
                }
            }

            return foundState
        }

    }
}
