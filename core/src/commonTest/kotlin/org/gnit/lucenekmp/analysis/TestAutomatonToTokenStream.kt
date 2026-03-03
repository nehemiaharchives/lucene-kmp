package org.gnit.lucenekmp.analysis

import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.automaton.Automata
import org.gnit.lucenekmp.util.automaton.Automaton
import kotlin.test.Test

class TestAutomatonToTokenStream : BaseTokenStreamTestCase() {

    @Test
    fun testSinglePath() {
        val acceptStrings = mutableListOf<BytesRef>()
        acceptStrings.add(BytesRef("abc"))

        val flatPathAutomaton = Automata.makeStringUnion(acceptStrings)
        val ts = AutomatonToTokenStream.toTokenStream(flatPathAutomaton)
        assertTokenStreamContents(
            ts,
            arrayOf("a", "b", "c"),
            intArrayOf(0, 1, 2),
            intArrayOf(1, 2, 3),
            intArrayOf(1, 1, 1),
            intArrayOf(1, 1, 1),
            3
        )
    }

    @Test
    fun testParallelPaths() {
        val acceptStrings = mutableListOf<BytesRef>()
        acceptStrings.add(BytesRef("123"))
        acceptStrings.add(BytesRef("abc"))

        val flatPathAutomaton = Automata.makeStringUnion(acceptStrings)
        val ts = AutomatonToTokenStream.toTokenStream(flatPathAutomaton)
        assertTokenStreamContents(
            ts,
            arrayOf("1", "a", "2", "b", "3", "c"),
            intArrayOf(0, 0, 1, 1, 2, 2),
            intArrayOf(1, 1, 2, 2, 3, 3),
            intArrayOf(1, 0, 1, 0, 1, 0),
            intArrayOf(1, 1, 1, 1, 1, 1),
            3
        )
    }

    @Test
    fun testForkedPath() {
        val acceptStrings = mutableListOf<BytesRef>()
        acceptStrings.add(BytesRef("ab3"))
        acceptStrings.add(BytesRef("abc"))

        val flatPathAutomaton = Automata.makeStringUnion(acceptStrings)
        val ts = AutomatonToTokenStream.toTokenStream(flatPathAutomaton)
        assertTokenStreamContents(
            ts,
            arrayOf("a", "b", "3", "c"),
            intArrayOf(0, 1, 2, 2),
            intArrayOf(1, 2, 3, 3),
            intArrayOf(1, 1, 1, 0),
            intArrayOf(1, 1, 1, 1),
            3
        )
    }

    @Test
    fun testNonDeterministicGraph() {
        val builder = Automaton.Builder()
        val start = builder.createState()
        val middle1 = builder.createState()
        val middle2 = builder.createState()
        val accept = builder.createState()

        builder.addTransition(start, middle1, 'a'.code)
        builder.addTransition(start, middle2, 'a'.code)
        builder.addTransition(middle1, accept, 'b'.code)
        builder.addTransition(middle2, accept, 'c'.code)
        builder.setAccept(accept, true)

        val nfa = builder.finish()
        val ts = AutomatonToTokenStream.toTokenStream(nfa)
        assertTokenStreamContents(
            ts,
            arrayOf("a", "a", "b", "c"),
            intArrayOf(0, 0, 1, 1),
            intArrayOf(1, 1, 2, 2),
            intArrayOf(1, 0, 1, 0),
            intArrayOf(1, 1, 1, 1),
            2
        )
    }

    @Test
    fun testGraphWithStartNodeCycle() {
        val builder = Automaton.Builder()
        val start = builder.createState()
        val middle = builder.createState()
        val accept = builder.createState()

        builder.addTransition(start, middle, 'a'.code)
        builder.addTransition(middle, accept, 'b'.code)
        builder.addTransition(middle, start, '1'.code)

        builder.setAccept(accept, true)

        val cycleGraph = builder.finish()
        expectThrows(IllegalArgumentException::class) {
            AutomatonToTokenStream.toTokenStream(cycleGraph)
        }
    }

    @Test
    fun testGraphWithNonStartCycle() {
        val builder = Automaton.Builder()
        val start = builder.createState()
        val middle = builder.createState()
        val accept = builder.createState()

        builder.addTransition(start, middle, 'a'.code)
        builder.addTransition(middle, accept, 'b'.code)
        builder.addTransition(accept, middle, 'c'.code)
        builder.setAccept(accept, true)

        val cycleGraph = builder.finish()
        expectThrows(IllegalArgumentException::class) {
            AutomatonToTokenStream.toTokenStream(cycleGraph)
        }
    }
}
