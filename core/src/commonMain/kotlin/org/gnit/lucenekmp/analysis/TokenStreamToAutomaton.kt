package org.gnit.lucenekmp.analysis

import okio.IOException
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionLengthAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TermToBytesRefAttribute
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.codePointAt
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.RollingBuffer
import org.gnit.lucenekmp.util.automaton.Automaton
import org.gnit.lucenekmp.util.codePointCount
import kotlin.math.max


// TODO: maybe also toFST  then we can translate atts into FST outputs/weights
/** Sole constructor.  */
/**
 * Consumes a TokenStream and creates an [Automaton] where the transition labels are UTF8
 * bytes (or Unicode code points if unicodeArcs is true) from the [TermToBytesRefAttribute].
 * Between tokens we insert POS_SEP and for holes we insert HOLE.
 *
 * @lucene.experimental
 */
class TokenStreamToAutomaton {
    private var preservePositionIncrements = true
    private var finalOffsetGapAsHole = false
    private var unicodeArcs = false

    /**
     * Whether to generate holes in the automaton for missing positions, `true` by default.
     */
    fun setPreservePositionIncrements(enablePositionIncrements: Boolean) {
        this.preservePositionIncrements = enablePositionIncrements
    }

    /** If true, any final offset gaps will result in adding a position hole.  */
    fun setFinalOffsetGapAsHole(finalOffsetGapAsHole: Boolean) {
        this.finalOffsetGapAsHole = finalOffsetGapAsHole
    }

    /**
     * Whether to make transition labels Unicode code points instead of UTF8 bytes, `false`
     * by default
     */
    fun setUnicodeArcs(unicodeArcs: Boolean) {
        this.unicodeArcs = unicodeArcs
    }

    private class Position : RollingBuffer.Resettable {
        // Any tokens that ended at our position arrive to this state:
        var arriving: Int = -1

        // Any tokens that start at our position leave from this state:
        var leaving: Int = -1

        override fun reset() {
            arriving = -1
            leaving = -1
        }
    }

    private class Positions : RollingBuffer<Position>() {
        override fun newInstance(): Position {
            return Position()
        }
    }

    /**
     * Subclass and implement this if you need to change the token (such as escaping certain bytes)
     * before it's turned into a graph.
     */
    protected fun changeToken(`in`: BytesRef): BytesRef {
        return `in`
    }

    /**
     * Pulls the graph (including [PositionLengthAttribute]) from the provided [ ], and creates the corresponding automaton where arcs are bytes (or Unicode code
     * points if unicodeArcs = true) from each term.
     */
    @Throws(IOException::class)
    fun toAutomaton(`in`: TokenStream): Automaton {
        val builder: Automaton.Builder = Automaton.Builder()
        builder.createState()

        val termBytesAtt: TermToBytesRefAttribute = `in`.addAttribute(
                TermToBytesRefAttribute::class
            )
        val posIncAtt: PositionIncrementAttribute = `in`.addAttribute(
                PositionIncrementAttribute::class
            )
        val posLengthAtt: PositionLengthAttribute = `in`.addAttribute(
                PositionLengthAttribute::class
            )
        val offsetAtt: OffsetAttribute = `in`.addAttribute(OffsetAttribute::class)

        `in`.reset()

        // Only temporarily holds states ahead of our current
        // position:
        val positions: RollingBuffer<Position> = Positions()

        var pos = -1
        var freedPos = 0
        var posData: Position? = null
        var maxOffset = 0
        while (`in`.incrementToken()) {
            var posInc: Int = posIncAtt.getPositionIncrement()
            if (!preservePositionIncrements && posInc > 1) {
                posInc = 1
            }
            assert(pos > -1 || posInc > 0)

            if (posInc > 0) {
                // New node:

                pos += posInc

                posData = positions.get(pos)
                assert(posData.leaving == -1)

                if (posData.arriving == -1) {
                    // No token ever arrived to this position
                    if (pos == 0) {
                        // OK: this is the first token
                        posData.leaving = 0
                    } else {
                        // This means there's a hole (eg, StopFilter
                        // does this):
                        posData.leaving = builder.createState()
                        addHoles(builder, positions, pos)
                    }
                } else {
                    posData.leaving = builder.createState()
                    builder.addTransition(posData.arriving, posData.leaving, POS_SEP)
                    if (posInc > 1) {
                        // A token spanned over a hole; add holes
                        // "under" it:
                        addHoles(builder, positions, pos)
                    }
                }
                while (freedPos <= pos) {
                    val freePosData: Position = positions.get(freedPos)
                    // don't free this position yet if we may still need to fill holes over it:
                    if (freePosData.arriving == -1 || freePosData.leaving == -1) {
                        break
                    }
                    positions.freeBefore(freedPos)
                    freedPos++
                }
            }

            val endPos: Int = pos + posLengthAtt.positionLength

            val termUTF8: BytesRef = changeToken(termBytesAtt.bytesRef)
            var termUnicode: IntArray? = null
            val endPosData: Position = positions.get(endPos)
            if (endPosData.arriving == -1) {
                endPosData.arriving = builder.createState()
            }

            val termLen: Int
            if (unicodeArcs) {
                val utf16: String = termUTF8.utf8ToString()
                termUnicode = IntArray(utf16.codePointCount(0, utf16.length))
                termLen = termUnicode.size
                var cp: Int
                var i = 0
                var j = 0
                while (i < utf16.length) {
                    cp = utf16.codePointAt(i)
                    termUnicode[j++] = cp
                    i += Character.charCount(cp)
                }
            } else {
                termLen = termUTF8.length
            }

            var state = posData!!.leaving

            for (byteIDX in 0..<termLen) {
                val nextState =
                    if (byteIDX == termLen - 1) endPosData.arriving else builder.createState()
                val c: Int
                if (unicodeArcs) {
                    c = termUnicode!![byteIDX]
                } else {
                    c = termUTF8.bytes[termUTF8.offset + byteIDX].toInt() and 0xff
                }
                builder.addTransition(state, nextState, c)
                state = nextState
            }

            maxOffset = max(maxOffset, offsetAtt.endOffset())
        }

        `in`.end()

        var endPosInc: Int = posIncAtt.getPositionIncrement()
        if (endPosInc == 0 && finalOffsetGapAsHole && offsetAtt.endOffset() > maxOffset) {
            endPosInc = 1
        } else if (endPosInc > 0 && !preservePositionIncrements) {
            endPosInc = 0
        }

        val endState: Int
        if (endPosInc > 0) {
            // there were hole(s) after the last token
            endState = builder.createState()

            // add trailing holes now:
            var lastState = endState
            while (true) {
                val state1: Int = builder.createState()
                builder.addTransition(lastState, state1, HOLE)
                endPosInc--
                if (endPosInc == 0) {
                    builder.setAccept(state1, true)
                    break
                }
                val state2: Int = builder.createState()
                builder.addTransition(state1, state2, POS_SEP)
                lastState = state2
            }
        } else {
            endState = -1
        }

        pos++
        while (pos <= positions.getMaxPos()) {
            posData = positions.get(pos)
            if (posData.arriving != -1) {
                if (endState != -1) {
                    builder.addTransition(posData.arriving, endState, POS_SEP)
                } else {
                    builder.setAccept(posData.arriving, true)
                }
            }
            pos++
        }

        return builder.finish()
    }

    companion object {
        /** We create transition between two adjacent tokens.  */
        const val POS_SEP: Int = 0x001f

        /** We add this arc to represent a hole.  */
        const val HOLE: Int = 0x001e

        // for debugging!
        /*
  private static void toDot(Automaton a) throws IOException {
    final String s = a.toDot();
    Writer w = new OutputStreamWriter(new FileOutputStream("/tmp/out.dot"));
    w.write(s);
    w.close();
    System.out.println("TEST: saved to /tmp/out.dot");
  }
  */
        private fun addHoles(
            builder: Automaton.Builder,
            positions: RollingBuffer<Position>,
            pos: Int
        ) {
            var pos = pos
            var posData: Position = positions.get(pos)
            var prevPosData: Position = positions.get(pos - 1)

            while (posData.arriving == -1 || prevPosData.leaving == -1) {
                if (posData.arriving == -1) {
                    posData.arriving = builder.createState()
                    builder.addTransition(posData.arriving, posData.leaving, POS_SEP)
                }
                if (prevPosData.leaving == -1) {
                    if (pos == 1) {
                        prevPosData.leaving = 0
                    } else {
                        prevPosData.leaving = builder.createState()
                    }
                    if (prevPosData.arriving != -1) {
                        builder.addTransition(prevPosData.arriving, prevPosData.leaving, POS_SEP)
                    }
                }
                builder.addTransition(prevPosData.leaving, posData.arriving, HOLE)
                pos--
                if (pos <= 0) {
                    break
                }
                posData = prevPosData
                prevPosData = positions.get(pos - 1)
            }
        }
    }
}
