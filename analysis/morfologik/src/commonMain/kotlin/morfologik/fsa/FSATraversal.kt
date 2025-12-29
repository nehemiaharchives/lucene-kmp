package morfologik.fsa

import morfologik.fsa.MatchResult.Companion.AUTOMATON_HAS_PREFIX
import morfologik.fsa.MatchResult.Companion.EXACT_MATCH
import morfologik.fsa.MatchResult.Companion.NO_MATCH
import morfologik.fsa.MatchResult.Companion.SEQUENCE_IS_A_PREFIX

/**
 * This class implements some common matching and scanning operations on a
 * generic FSA.
 */
class FSATraversal(private val fsa: FSA) {
    fun perfectHash(sequence: ByteArray, start: Int, length: Int, node: Int): Int {
        check(fsa.getFlags().contains(FSAFlags.NUMBERS)) { "FSA not built with NUMBERS option." }
        check(length > 0) { "Must be a non-empty sequence." }

        var hash = 0
        val end = start + length - 1
        var seqIndex = start
        var label = sequence[seqIndex]

        var arc = fsa.getFirstArc(node)
        while (arc != 0) {
            if (fsa.getArcLabel(arc) == label) {
                if (fsa.isArcFinal(arc)) {
                    if (seqIndex == end) {
                        return hash
                    }
                    hash++
                }

                if (fsa.isArcTerminal(arc)) {
                    return AUTOMATON_HAS_PREFIX
                }

                if (seqIndex == end) {
                    return SEQUENCE_IS_A_PREFIX
                }

                arc = fsa.getFirstArc(fsa.getEndNode(arc))
                label = sequence[++seqIndex]
                continue
            } else {
                if (fsa.isArcFinal(arc)) {
                    hash++
                }
                if (!fsa.isArcTerminal(arc)) {
                    hash += fsa.getRightLanguageCount(fsa.getEndNode(arc))
                }
            }

            arc = fsa.getNextArc(arc)
        }

        return if (seqIndex > start) {
            AUTOMATON_HAS_PREFIX
        } else {
            NO_MATCH
        }
    }

    fun perfectHash(sequence: ByteArray): Int {
        return perfectHash(sequence, 0, sequence.size, fsa.getRootNode())
    }

    fun match(reuse: MatchResult, sequence: ByteArray, start: Int, length: Int, node: Int): MatchResult {
        if (node == 0) {
            reuse.reset(NO_MATCH, start, node)
            return reuse
        }

        var currentNode = node
        val end = start + length
        for (i in start until end) {
            val arc = fsa.getArc(currentNode, sequence[i])
            if (arc != 0) {
                if (i + 1 == end && fsa.isArcFinal(arc)) {
                    reuse.reset(EXACT_MATCH, i, currentNode)
                    return reuse
                }
                if (fsa.isArcTerminal(arc)) {
                    reuse.reset(AUTOMATON_HAS_PREFIX, i + 1, currentNode)
                    return reuse
                }
                currentNode = fsa.getEndNode(arc)
            } else {
                if (i > start) {
                    reuse.reset(AUTOMATON_HAS_PREFIX, i, currentNode)
                } else {
                    reuse.reset(NO_MATCH, i, currentNode)
                }
                return reuse
            }
        }

        reuse.reset(SEQUENCE_IS_A_PREFIX, 0, currentNode)
        return reuse
    }

    fun match(sequence: ByteArray, start: Int, length: Int, node: Int): MatchResult {
        return match(MatchResult(), sequence, start, length, node)
    }

    fun match(sequence: ByteArray, node: Int): MatchResult {
        return match(sequence, 0, sequence.size, node)
    }

    fun match(sequence: ByteArray): MatchResult {
        return match(sequence, fsa.getRootNode())
    }
}
