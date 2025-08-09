package org.gnit.lucenekmp.util.graph

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionLengthAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TermToBytesRefAttribute
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.internal.hppc.IntArrayList
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.BitSet
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.AttributeSource
import org.gnit.lucenekmp.util.IntsRef
import org.gnit.lucenekmp.util.automaton.Automaton
import org.gnit.lucenekmp.util.automaton.FiniteStringsIterator
import org.gnit.lucenekmp.util.automaton.Operations
import org.gnit.lucenekmp.util.automaton.Operations.DEFAULT_DETERMINIZE_WORK_LIMIT
import org.gnit.lucenekmp.util.automaton.Transition
import kotlin.math.min

/**
 * Consumes a TokenStream and creates an [Automaton] where the transition labels are terms
 * from the [TermToBytesRefAttribute]. This class also provides helpers to explore the
 * different paths of the [Automaton].
 */
class GraphTokenStreamFiniteStrings(`in`: TokenStream) {
    private var tokens: Array<AttributeSource> =
        kotlin.arrayOfNulls<AttributeSource>(4) as Array<AttributeSource>
    private val det: Automaton
    private val transition: Transition = Transition()

    private inner class FiniteStringsTokenStream(private val ids: IntsRef) :
        TokenStream(tokens[0].cloneAttributes()) {
        //checkNotNull(ids)
        private val end: Int = ids.offset + ids.length
        private var offset: Int = ids.offset

        @Throws(IOException::class)
        override fun incrementToken(): Boolean {
            if (offset < end) {
                clearAttributes()
                val id: Int = ids.ints[offset]
                tokens[id].copyTo(this)
                offset++
                return true
            }

            return false
        }
    }

    init {
        val aut: Automaton = build(`in`)
        this.det =
            Operations.removeDeadStates(
                Operations.determinize(
                    aut,
                    DEFAULT_DETERMINIZE_WORK_LIMIT
                )
            )
    }

    /**
     * Returns whether the provided state is the start of multiple side paths of different length (eg:
     * new york, ny)
     */
    fun hasSidePath(state: Int): Boolean {
        val numT: Int = det.initTransition(state, transition)
        if (numT <= 1) {
            return false
        }
        det.getNextTransition(transition)
        val dest: Int = transition.dest
        for (i in 1..<numT) {
            det.getNextTransition(transition)
            if (dest != transition.dest) {
                return true
            }
        }
        return false
    }

    /** Returns the list of tokens that start at the provided state  */
    fun getTerms(state: Int): MutableList<AttributeSource> {
        val numT: Int = det.initTransition(state, transition)
        val tokens: MutableList<AttributeSource> = mutableListOf()
        for (i in 0..<numT) {
            det.getNextTransition(transition)
            tokens.addAll(this.tokens.asList().subList(transition.min, transition.max + 1))
        }
        return tokens
    }

    /** Returns the list of terms that start at the provided state  */
    fun getTerms(field: String, state: Int): Array<Term> {
        return getTerms(state)
            .map { s: AttributeSource ->
                Term(
                    field,
                    s.addAttribute(TermToBytesRefAttribute::class)
                        .bytesRef
                )
            }.toTypedArray()
    }

    val finiteStrings: MutableIterator<TokenStream>
        /** Get all finite strings from the automaton.  */
        get() = getFiniteStrings(0, -1)

    /** Get all finite strings that start at `startState` and end at `endState`.  */
    fun getFiniteStrings(startState: Int, endState: Int): MutableIterator<TokenStream> {
        val it =            FiniteStringsIterator(det, startState, endState)
        return object : MutableIterator<TokenStream> {
            var current: IntsRef? = null
            var finished: Boolean = false

            override fun hasNext(): Boolean {
                if (!finished && current == null) {
                    current = it.next()
                    if (current == null) {
                        finished = true
                    }
                }
                return current != null
            }

            override fun next(): TokenStream {
                if (current == null) {
                    hasNext()
                }
                val next: TokenStream =
                    this@GraphTokenStreamFiniteStrings.FiniteStringsTokenStream(current!!)
                current = null
                return next
            }

            override fun remove() {
                throw UnsupportedOperationException(
                    "remove() is not supported by this iterator"
                )
            }
        }
    }

    /**
     * Returns the articulation points (or cut vertices) of the graph:
     * https://en.wikipedia.org/wiki/Biconnected_component
     */
    fun articulationPoints(): IntArray {
        if (det.numStates == 0) {
            return IntArray(0)
        }
        //
        val undirect: Automaton.Builder =
            Automaton.Builder()
        undirect.copy(det)
        for (i in 0..<det.numStates) {
            val numT: Int = det.initTransition(i, transition)
            for (j in 0..<numT) {
                det.getNextTransition(transition)
                undirect.addTransition(transition.dest, i, transition.min)
            }
        }
        val numStates: Int = det.numStates
        val visited = BitSet(numStates)
        val depth = IntArray(det.numStates)
        val low = IntArray(det.numStates)
        val parent = IntArray(det.numStates)
        Arrays.fill(parent, -1)
        val points = IntArrayList()
        articulationPointsRecurse(undirect.finish(), 0, 0, depth, low, parent, visited, points)
        return points.reverse().toArray()
    }

    /** Build an automaton from the provided [TokenStream].  */
    @Throws(IOException::class)
    private fun build(`in`: TokenStream): Automaton {
        val builder: Automaton.Builder =
            Automaton.Builder()

        val posIncAtt: PositionIncrementAttribute =
            `in`.addAttribute(PositionIncrementAttribute::class)
        val posLengthAtt: PositionLengthAttribute =
            `in`.addAttribute(PositionLengthAttribute::class)

        `in`.reset()

        var pos = -1
        var prevIncr = 1
        var state = -1
        var id = -1
        var gap = 0
        while (`in`.incrementToken()) {
            val currentIncr: Int = posIncAtt.getPositionIncrement()
            check(!(pos == -1 && currentIncr < 1)) { "Malformed TokenStream, start token can't have increment less than 1" }

            if (currentIncr == 0) {
                if (gap > 0) {
                    pos -= gap
                }
            } else {
                pos++
                gap = currentIncr - 1
            }

            val endPos: Int = pos + posLengthAtt.positionLength + gap
            while (state < endPos) {
                state = builder.createState()
            }

            id++
            if (tokens.size < id + 1) {
                tokens = ArrayUtil.grow<AttributeSource>(tokens, id + 1)
            }

            tokens[id] = `in`.cloneAttributes()
            builder.addTransition(pos, endPos, id)
            pos += gap

            // we always produce linear token graphs from getFiniteStrings(), so we need to adjust
            // posLength and posIncrement accordingly
            tokens[id].addAttribute(PositionLengthAttribute::class).positionLength = 1
            if (currentIncr == 0) {
                // stacked token should have the same increment as original token at this position
                tokens[id].addAttribute(PositionIncrementAttribute::class)
                    .setPositionIncrement(prevIncr)
            }

            // only save last increment on non-zero increment in case we have multiple stacked tokens
            if (currentIncr > 0) {
                prevIncr = currentIncr
            }
        }

        `in`.end()
        if (state != -1) {
            builder.setAccept(state, true)
        }
        return builder.finish()
    }

    companion object {
        /** Maximum level of recursion allowed in recursive operations.  */
        private const val MAX_RECURSION_LEVEL = 1000

        private fun articulationPointsRecurse(
            a: Automaton,
            state: Int,
            d: Int,
            depth: IntArray,
            low: IntArray,
            parent: IntArray,
            visited: BitSet,
            points: IntArrayList
        ) {
            visited.set(state)
            depth[state] = d
            low[state] = d
            var childCount = 0
            var isArticulation = false
            val t = Transition()
            val numT: Int = a.initTransition(state, t)
            for (i in 0..<numT) {
                a.getNextTransition(t)
                if (!visited[t.dest]) {
                    parent[t.dest] = state
                    if (d < MAX_RECURSION_LEVEL) {
                        articulationPointsRecurse(a, t.dest, d + 1, depth, low, parent, visited, points)
                    } else {
                        throw IllegalArgumentException(
                            "Exceeded maximum recursion level during graph analysis"
                        )
                    }
                    childCount++
                    if (low[t.dest] >= depth[state]) {
                        isArticulation = true
                    }
                    low[state] = min(low[state], low[t.dest])
                } else if (t.dest != parent[state]) {
                    low[state] = min(low[state], depth[t.dest])
                }
            }
            if ((parent[state] != -1 && isArticulation) || (parent[state] == -1 && childCount > 1)) {
                points.add(state)
            }
        }
    }
}
