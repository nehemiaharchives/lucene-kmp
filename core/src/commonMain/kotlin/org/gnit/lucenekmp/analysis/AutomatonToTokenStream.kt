package org.gnit.lucenekmp.analysis

import okio.IOException
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionLengthAttribute
import org.gnit.lucenekmp.internal.hppc.IntArrayList
import org.gnit.lucenekmp.internal.hppc.IntIntHashMap
import org.gnit.lucenekmp.util.automaton.Automaton

/** Converts an Automaton into a TokenStream. */
class AutomatonToTokenStream private constructor() {
    companion object {
        /**
         * converts an automaton into a TokenStream. This is done by first Topo sorting the nodes in the
         * Automaton. Nodes that have the same distance from the start are grouped together to form the
         * position nodes for the TokenStream. The resulting TokenStream releases edges from the automaton
         * as tokens in order from the position nodes. This requires the automaton be a finite DAG.
         *
         * @param automaton automaton to convert. Must be a finite DAG to avoid infinite loops!
         * @return TokenStream representation of automaton.
         */
        fun toTokenStream(automaton: Automaton): TokenStream {
            val positionNodes = mutableListOf<IntArrayList>()

            val transitions = automaton.sortedTransitions

            val indegree = IntArray(transitions.size)

            for (i in transitions.indices) {
                for (edge in transitions[i].indices) {
                    indegree[transitions[i][edge].dest] += 1
                }
            }
            if (indegree[0] != 0) {
                throw IllegalArgumentException("Start node has incoming edges, creating cycle")
            }

            val noIncomingEdges = ArrayDeque<RemapNode>()
            val idToPos = IntIntHashMap()
            noIncomingEdges.addLast(RemapNode(0, 0))
            while (noIncomingEdges.isEmpty().not()) {
                val currState = noIncomingEdges.removeFirst()
                for (i in transitions[currState.id].indices) {
                    indegree[transitions[currState.id][i].dest] -= 1
                    if (indegree[transitions[currState.id][i].dest] == 0) {
                        noIncomingEdges.addLast(RemapNode(transitions[currState.id][i].dest, currState.pos + 1))
                    }
                }
                if (positionNodes.size == currState.pos) {
                    val posIncs = IntArrayList()
                    posIncs.add(currState.id)
                    positionNodes.add(posIncs)
                } else {
                    positionNodes[currState.pos].add(currState.id)
                }
                idToPos.put(currState.id, currState.pos)
            }

            for (i in indegree.indices) {
                if (indegree[i] != 0) {
                    throw IllegalArgumentException("Cycle found in automaton")
                }
            }

            val edgesByLayer = mutableListOf<MutableList<EdgeToken>>()
            for (layer in positionNodes) {
                val edges = mutableListOf<EdgeToken>()
                for (state in layer) {
                    for (t in transitions[state.value]) {
                        // each edge in the token stream can only be on value, though a transition takes a range.
                        for (value in t.min..t.max) {
                            val destLayer = idToPos.get(t.dest)
                            edges.add(EdgeToken(destLayer, value))
                            // If there's an intermediate accept state, add an edge to the terminal state.
                            if (automaton.isAccept(t.dest) && destLayer != positionNodes.size - 1) {
                                edges.add(EdgeToken(positionNodes.size - 1, value))
                            }
                        }
                    }
                }
                edgesByLayer.add(edges)
            }

            return TopoTokenStream(edgesByLayer)
        }
    }

    /** Token Stream that outputs tokens from a topo sorted graph. */
    private class TopoTokenStream(private val edgesByPos: List<List<EdgeToken>>) : TokenStream() {
        private var currentPos = 0
        private var currentEdgeIndex = 0
        private val charAttr = addAttribute(CharTermAttribute::class)
        private val incAttr = addAttribute(PositionIncrementAttribute::class)
        private val lenAttr = addAttribute(PositionLengthAttribute::class)
        private val offAttr = addAttribute(OffsetAttribute::class)

        @Throws(IOException::class)
        override fun incrementToken(): Boolean {
            clearAttributes()
            while (currentPos < edgesByPos.size && currentEdgeIndex == edgesByPos[currentPos].size) {
                currentEdgeIndex = 0
                currentPos += 1
            }
            if (currentPos == edgesByPos.size) {
                return false
            }
            val currentEdge = edgesByPos[currentPos][currentEdgeIndex]

            charAttr.append(currentEdge.value.toChar())

            incAttr.setPositionIncrement(if (currentEdgeIndex == 0) 1 else 0)

            lenAttr.positionLength = currentEdge.destination - currentPos

            offAttr.setOffset(currentPos, currentEdge.destination)

            currentEdgeIndex++

            return true
        }

        @Throws(IOException::class)
        override fun reset() {
            super.reset()
            clearAttributes()
            currentPos = 0
            currentEdgeIndex = 0
        }

        @Throws(IOException::class)
        override fun end() {
            clearAttributes()
            incAttr.setPositionIncrement(0)
            // -1 because we don't count the terminal state as a position in the TokenStream
            offAttr.setOffset(edgesByPos.size - 1, edgesByPos.size - 1)
        }
    }

    /** Edge between position nodes. These edges will be output as tokens in the TokenStream */
    private data class EdgeToken(val destination: Int, val value: Int)

    /** Node that contains original node id and position in TokenStream */
    private data class RemapNode(val id: Int, val pos: Int)
}
