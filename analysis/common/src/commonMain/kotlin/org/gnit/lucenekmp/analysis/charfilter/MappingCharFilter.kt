package org.gnit.lucenekmp.analysis.charfilter

import okio.IOException
import org.gnit.lucenekmp.analysis.util.RollingCharBuffer
import org.gnit.lucenekmp.internal.hppc.CharObjectHashMap
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.util.CharsRef
import org.gnit.lucenekmp.util.fst.CharSequenceOutputs
import org.gnit.lucenekmp.util.fst.FST
import org.gnit.lucenekmp.util.fst.Outputs

/**
 * Simplistic CharFilter that applies the mappings contained in a [NormalizeCharMap]
 * to the character stream, correcting resulting changes to the offsets.
 */
class MappingCharFilter(normMap: NormalizeCharMap, override val input: Reader) : BaseCharFilter(input) {
    private val outputs: Outputs<CharsRef> = CharSequenceOutputs.singleton
    private val map: FST<CharsRef>? = normMap.map
    private val fstReader: FST.BytesReader? = map?.getBytesReader()
    private val buffer = RollingCharBuffer()
    private val scratchArc = FST.Arc<CharsRef>()
    private val cachedRootArcs: CharObjectHashMap<FST.Arc<CharsRef>> = normMap.cachedRootArcs

    private var replacement: CharsRef? = null
    private var replacementPointer = 0
    private var inputOff = 0

    init {
        buffer.reset(input)
    }

    @Throws(IOException::class)
    override fun reset() {
        input.reset()
        buffer.reset(input)
        replacement = null
        inputOff = 0
    }

    @Throws(IOException::class)
    override fun read(): Int {
        while (true) {
            val repl = replacement
            if (repl != null && replacementPointer < repl.length) {
                return repl.chars[repl.offset + replacementPointer++].code
            }

            var lastMatchLen = -1
            var lastMatch: CharsRef? = null

            val firstCH = buffer.get(inputOff)
            if (firstCH != -1) {
                val arc = cachedRootArcs.get(firstCH.toChar())
                if (arc != null && map != null && fstReader != null) {
                    if (!FST.targetHasArcs(arc)) {
                        // Single character match
                        lastMatchLen = 1
                        lastMatch = arc.output() ?: outputs.noOutput
                    } else {
                        var lookahead = 0
                        var output = arc.output() ?: outputs.noOutput
                        var currentArc: FST.Arc<CharsRef>? = arc
                        while (currentArc != null) {
                            lookahead++

                            if (currentArc.isFinal) {
                                lastMatchLen = lookahead
                                lastMatch = outputs.add(output, currentArc.nextFinalOutput() ?: outputs.noOutput)
                            }

                            if (!FST.targetHasArcs(currentArc)) {
                                break
                            }

                            val ch = buffer.get(inputOff + lookahead)
                            if (ch == -1) {
                                break
                            }
                            currentArc = map.findTargetArc(ch, currentArc, scratchArc, fstReader)
                            if (currentArc == null) {
                                break
                            }
                            output = outputs.add(output, currentArc.output() ?: outputs.noOutput)
                        }
                    }
                }
            }

            if (lastMatch != null) {
                inputOff += lastMatchLen

                val diff = lastMatchLen - lastMatch.length
                if (diff != 0) {
                    val prevCumulativeDiff = getLastCumulativeDiff()
                    if (diff > 0) {
                        addOffCorrectMap(inputOff - diff - prevCumulativeDiff, prevCumulativeDiff + diff)
                    } else {
                        val outputStart = inputOff - prevCumulativeDiff
                        for (extraIdx in 0 until -diff) {
                            addOffCorrectMap(outputStart + extraIdx, prevCumulativeDiff - extraIdx - 1)
                        }
                    }
                }

                replacement = lastMatch
                replacementPointer = 0
            } else {
                val ret = buffer.get(inputOff)
                if (ret != -1) {
                    inputOff++
                    buffer.freeBefore(inputOff)
                }
                return ret
            }
        }
    }

    @Throws(IOException::class)
    override fun read(cbuf: CharArray, off: Int, len: Int): Int {
        var numRead = 0
        for (i in off until off + len) {
            val c = read()
            if (c == -1) break
            cbuf[i] = c.toChar()
            numRead++
        }
        return if (numRead == 0) -1 else numRead
    }
}
