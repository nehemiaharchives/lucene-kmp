package org.gnit.lucenekmp.analysis.synonym

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionLengthAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TypeAttribute
import org.gnit.lucenekmp.store.ByteArrayDataInput
import org.gnit.lucenekmp.util.AttributeSource
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.CharsRefBuilder
import org.gnit.lucenekmp.util.RollingBuffer
import org.gnit.lucenekmp.util.fst.FST
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.jdkport.assert

/**
 * Applies single- or multi-token synonyms from a [SynonymMap] to an incoming [TokenStream].
 *
 * @lucene.experimental
 */
class SynonymGraphFilter(input: TokenStream, synonyms: SynonymMap, ignoreCase: Boolean) : TokenFilter(input) {
    companion object {
        const val TYPE_SYNONYM = "SYNONYM"
    }

    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val posIncrAtt: PositionIncrementAttribute = addAttribute(PositionIncrementAttribute::class)
    private val posLenAtt: PositionLengthAttribute = addAttribute(PositionLengthAttribute::class)
    private val typeAtt: TypeAttribute = addAttribute(TypeAttribute::class)
    private val offsetAtt: OffsetAttribute = addAttribute(OffsetAttribute::class)

    private val synonyms: SynonymMap = synonyms
    private val ignoreCase: Boolean = ignoreCase
    private val fst: FST<BytesRef> = synonyms.fst ?: throw IllegalArgumentException("fst must be non-null")
    private val fstReader: FST.BytesReader = fst.getBytesReader()
    private val scratchArc = FST.Arc<BytesRef>()
    private val bytesReader = ByteArrayDataInput()
    private val scratchBytes = BytesRef()
    private val scratchChars = CharsRefBuilder()
    private val outputBuffer: MutableList<BufferedOutputToken> = mutableListOf()

    private var nextNodeOut = 0
    private var lastNodeOut = 0
    private var maxLookaheadUsed = 0
    private var captureCount = 0
    private var liveToken = false
    private var matchStartOffset = 0
    private var matchEndOffset = 0
    private var finished = false
    private var lookaheadNextRead = 0
    private var lookaheadNextWrite = 0

    private val lookahead = object : RollingBuffer<BufferedInputToken>() {
        override fun newInstance(): BufferedInputToken = BufferedInputToken()
    }

    class BufferedInputToken : RollingBuffer.Resettable {
        val term: CharsRefBuilder = CharsRefBuilder()
        var state: AttributeSource.State? = null
        var startOffset: Int = -1
        var endOffset: Int = -1
        override fun reset() {
            state = null
            term.clear()
            startOffset = -1
            endOffset = -1
        }
    }

    data class BufferedOutputToken(val state: AttributeSource.State?, val term: String, val startNode: Int, val endNode: Int)

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        assert(lastNodeOut <= nextNodeOut)

        if (outputBuffer.isNotEmpty()) {
            releaseBufferedToken()
            assert(!liveToken)
            return true
        }

        if (parse()) {
            releaseBufferedToken()
            assert(!liveToken)
            return true
        }

        if (lookaheadNextRead == lookaheadNextWrite) {
            if (finished) {
                return false
            }
            assert(liveToken)
            liveToken = false
        } else {
            assert(lookaheadNextRead < lookaheadNextWrite)
            val token = lookahead.get(lookaheadNextRead)
            lookaheadNextRead++
            restoreState(token.state)
            lookahead.freeBefore(lookaheadNextRead)
            assert(!liveToken)
        }

        lastNodeOut += posIncrAtt.getPositionIncrement()
        nextNodeOut = lastNodeOut + posLenAtt.positionLength
        return true
    }

    @Throws(IOException::class)
    private fun releaseBufferedToken() {
        val token = outputBuffer.removeAt(0)
        if (token.state != null) {
            restoreState(token.state)
        } else {
            clearAttributes()
            termAtt.append(token.term)
            assert(matchStartOffset != -1)
            offsetAtt.setOffset(matchStartOffset, matchEndOffset)
            typeAtt.setType(TYPE_SYNONYM)
        }
        posIncrAtt.setPositionIncrement(token.startNode - lastNodeOut)
        lastNodeOut = token.startNode
        posLenAtt.positionLength = token.endNode - token.startNode
    }

    @Throws(IOException::class)
    private fun parse(): Boolean {
        var matchOutput: BytesRef? = null
        var matchInputLength = 0
        var pendingOutput = fst.outputs.noOutput
        fst.getFirstArc(scratchArc)
        var matchLength = 0
        var doFinalCapture = false
        var lookaheadUpto = lookaheadNextRead
        matchStartOffset = -1

        byToken@ while (true) {
            val buffer: CharArray
            val bufferLen: Int
            val inputEndOffset: Int
            if (lookaheadUpto <= lookahead.getMaxPos()) {
                val token = lookahead.get(lookaheadUpto)
                lookaheadUpto++
                buffer = token.term.chars()
                bufferLen = token.term.length()
                inputEndOffset = token.endOffset
                if (matchStartOffset == -1) matchStartOffset = token.startOffset
            } else {
                assert(finished || !liveToken)
                if (finished) {
                    break
                } else if (input.incrementToken()) {
                    liveToken = true
                    buffer = termAtt.buffer()
                    bufferLen = termAtt.length
                    if (matchStartOffset == -1) matchStartOffset = offsetAtt.startOffset()
                    inputEndOffset = offsetAtt.endOffset()
                    lookaheadUpto++
                } else {
                    finished = true
                    break
                }
            }

            matchLength++
            var bufUpto = 0
            while (bufUpto < bufferLen) {
                val codePoint = Character.codePointAt(buffer, bufUpto, bufferLen)
                if (fst.findTargetArc(if (ignoreCase) Character.toLowerCase(codePoint) else codePoint, scratchArc, scratchArc, fstReader) == null) {
                    break@byToken
                }
                pendingOutput = fst.outputs.add(pendingOutput, scratchArc.output()!!)
                bufUpto += Character.charCount(codePoint)
            }

            if (scratchArc.isFinal) {
                matchOutput = fst.outputs.add(pendingOutput, scratchArc.nextFinalOutput()!!)
                matchInputLength = matchLength
                matchEndOffset = inputEndOffset
            }

            if (fst.findTargetArc(SynonymMap.WORD_SEPARATOR.code, scratchArc, scratchArc, fstReader) == null) {
                break
            } else {
                pendingOutput = fst.outputs.add(pendingOutput, scratchArc.output()!!)
                doFinalCapture = true
                if (liveToken) capture()
            }
        }

        if (doFinalCapture && liveToken && !finished) {
            capture()
        }

        if (matchOutput != null) {
            if (liveToken) capture()
            bufferOutputTokens(matchOutput, matchInputLength)
            lookaheadNextRead += matchInputLength
            lookahead.freeBefore(lookaheadNextRead)
            return true
        }
        return false
    }

    @Throws(IOException::class)
    private fun bufferOutputTokens(bytes: BytesRef, matchInputLength: Int) {
        bytesReader.reset(bytes.bytes, bytes.offset, bytes.length)
        val code = bytesReader.readVInt()
        val keepOrig = (code and 0x1) == 0
        var totalPathNodes = if (keepOrig) matchInputLength - 1 else 0
        val count = code ushr 1
        val paths = ArrayList<List<String>>()
        for (outputIDX in 0 until count) {
            val wordID = bytesReader.readVInt()
            synonyms.words.get(wordID, scratchBytes)
            scratchChars.copyUTF8Bytes(scratchBytes)
            var lastStart = 0
            val path = ArrayList<String>()
            paths.add(path)
            val chEnd = scratchChars.length()
            for (chUpto in 0..chEnd) {
                if (chUpto == chEnd || scratchChars.charAt(chUpto) == SynonymMap.WORD_SEPARATOR) {
                    path.add(scratchChars.chars().concatToString(lastStart, chUpto))
                    lastStart = chUpto + 1
                }
            }
            totalPathNodes += path.size - 1
        }

        val startNode = nextNodeOut
        val endNode = startNode + totalPathNodes + 1
        var newNodeCount = 0
        for (path in paths) {
            val pathEndNode = if (path.size == 1) {
                endNode
            } else {
                val next = nextNodeOut + newNodeCount + 1
                newNodeCount += path.size - 1
                next
            }
            outputBuffer.add(BufferedOutputToken(null, path[0], startNode, pathEndNode))
        }

        if (keepOrig) {
            val token = lookahead.get(lookaheadNextRead)
            val inputEndNode = if (matchInputLength == 1) endNode else nextNodeOut + newNodeCount + 1
            outputBuffer.add(BufferedOutputToken(token.state, token.term.toString(), startNode, inputEndNode))
        }

        nextNodeOut = endNode

        for (pathID in paths.indices) {
            val path = paths[pathID]
            if (path.size > 1) {
                var lastNode = outputBuffer[pathID].endNode
                for (i in 1 until path.size - 1) {
                    outputBuffer.add(BufferedOutputToken(null, path[i], lastNode, lastNode + 1))
                    lastNode++
                }
                outputBuffer.add(BufferedOutputToken(null, path.last(), lastNode, endNode))
            }
        }

        if (keepOrig && matchInputLength > 1) {
            var lastNode = outputBuffer[paths.size].endNode
            for (i in 1 until matchInputLength - 1) {
                val token = lookahead.get(lookaheadNextRead + i)
                outputBuffer.add(BufferedOutputToken(token.state, token.term.toString(), lastNode, lastNode + 1))
                lastNode++
            }
            val token = lookahead.get(lookaheadNextRead + matchInputLength - 1)
            outputBuffer.add(BufferedOutputToken(token.state, token.term.toString(), lastNode, endNode))
        }
    }

    private fun capture() {
        assert(liveToken)
        liveToken = false
        val token = lookahead.get(lookaheadNextWrite)
        lookaheadNextWrite++
        token.state = requireNotNull(captureState())
        token.startOffset = offsetAtt.startOffset()
        token.endOffset = offsetAtt.endOffset()
        assert(token.term.length() == 0)
        token.term.append(termAtt)
        captureCount++
        maxLookaheadUsed = maxOf(maxLookaheadUsed, lookahead.getBufferSize())
    }

    @Throws(IOException::class)
    override fun reset() {
        super.reset()
        lookahead.reset()
        lookaheadNextWrite = 0
        lookaheadNextRead = 0
        captureCount = 0
        lastNodeOut = -1
        nextNodeOut = 0
        matchStartOffset = -1
        matchEndOffset = -1
        finished = false
        liveToken = false
        outputBuffer.clear()
        maxLookaheadUsed = 0
    }

    fun getCaptureCount(): Int = captureCount
    fun getMaxLookaheadUsed(): Int = maxLookaheadUsed
}

