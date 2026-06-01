package org.gnit.lucenekmp.analysis.synonym

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionLengthAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TypeAttribute
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.store.ByteArrayDataInput
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.AttributeSource
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.CharsRef
import org.gnit.lucenekmp.util.CharsRefBuilder
import org.gnit.lucenekmp.util.fst.FST

/**
 * Matches single or multi word synonyms in a token stream. This token stream cannot properly handle
 * position increments != 1, ie, you should place this filter before filtering out stop words.
 *
 * <p>Note that with the current implementation, parsing is greedy, so whenever multiple parses
 * would apply, the rule starting the earliest and parsing the most tokens wins. For example if you
 * have these rules:
 *
 * <pre>
 *   a -> x
 *   a b -> y
 *   b c d -> z
 * </pre>
 *
 * Then input <code>a b c d e</code> parses to <code>y b c
 * d</code>, ie the 2nd rule "wins" because it started earliest and matched the most input tokens of
 * other rules starting at that point.
 *
 * <p>A future improvement to this filter could allow non-greedy parsing, such that the 3rd rule
 * would win, and also separately allow multiple parses, such that all 3 rules would match, perhaps
 * even on a rule by rule basis.
 *
 * <p><b>NOTE</b>: when a match occurs, the output tokens associated with the matching rule are
 * "stacked" on top of the input stream (if the rule had <code>keepOrig=true</code>) and also on top
 * of another matched rule's output tokens. This is not a correct solution, as really the output
 * should be an arbitrary graph/lattice. For example, with the above match, you would expect an
 * exact <code>PhraseQuery</code> <code>"y b
 * c"</code> to match the parsed tokens, but it will fail to do so. This limitation is necessary
 * because Lucene's TokenStream (and index) cannot yet represent an arbitrary graph.
 *
 * <p><b>NOTE</b>: If multiple incoming tokens arrive on the same position, only the first token at
 * that position is used for parsing. Subsequent tokens simply pass through and are not parsed. A
 * future improvement would be to allow these tokens to also be matched.
 *
 * @deprecated Use [SynonymGraphFilter] instead, but be sure to also use
 * [org.gnit.lucenekmp.analysis.core.FlattenGraphFilter] at index time (not at search time) as
 * well.
 */

// TODO: maybe we should resolve token -> wordID then run
// FST on wordIDs, for better perf?

// TODO: a more efficient approach would be Aho/Corasick's
// algorithm
// http://en.wikipedia.org/wiki/Aho%E2%80%93Corasick_string_matching_algorithm
// It improves over the current approach here
// because it does not fully re-start matching at every
// token.  For example if one pattern is "a b c x"
// and another is "b c d" and the input is "a b c d", on
// trying to parse "a b c x" but failing when you got to x,
// rather than starting over again your really should
// immediately recognize that "b c d" matches at the next
// input.  I suspect this won't matter that much in
// practice, but it's possible on some set of synonyms it
// will.  We'd have to modify Aho/Corasick to enforce our
// conflict resolving (eg greedy matching) because that algo
// finds all matches.  This really amounts to adding a .*
// closure to the FST and then determinizing it.
//
// Another possible solution is described at
// http://www.cis.uni-muenchen.de/people/Schulz/Pub/dictle5.ps

@Deprecated("Use SynonymGraphFilter instead")
class SynonymFilter(input: TokenStream, synonyms: SynonymMap, ignoreCase: Boolean) : TokenFilter(input) {
    companion object {
        const val TYPE_SYNONYM: String = "SYNONYM"
    }

    private val synonyms: SynonymMap = synonyms
    private val ignoreCase: Boolean = ignoreCase
    private val rollBufferSize: Int

    private var captureCount = 0

    // TODO: we should set PositionLengthAttr too...

    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val posIncrAtt: PositionIncrementAttribute = addAttribute(PositionIncrementAttribute::class)
    private val posLenAtt: PositionLengthAttribute = addAttribute(PositionLengthAttribute::class)
    private val typeAtt: TypeAttribute = addAttribute(TypeAttribute::class)
    private val offsetAtt: OffsetAttribute = addAttribute(OffsetAttribute::class)

    // How many future input tokens have already been matched
    // to a synonym; because the matching is "greedy" we don't
    // try to do any more matching for such tokens:
    private var inputSkipCount = 0

    // Rolling buffer, holding pending input tokens we had to
    // clone because we needed to look ahead, indexed by
    // position:
    private val futureInputs: Array<PendingInput>

    private class PendingInput {
        val term: CharsRefBuilder = CharsRefBuilder()
        var state: AttributeSource.State? = null
        var keepOrig = false
        var matched = false
        var consumed = true
        var startOffset = 0
        var endOffset = 0

        fun reset() {
            state = null
            consumed = true
            keepOrig = false
            matched = false
        }
    }

    // Holds pending output synonyms for one future position:
    private class PendingOutputs {
        var outputs: Array<CharsRefBuilder?> = arrayOfNulls(1)
        var endOffsets: IntArray = IntArray(1)
        var posLengths: IntArray = IntArray(1)
        var upto = 0
        var count = 0
        var posIncr = 1
        private var lastEndOffset = 0
        private var lastPosLength = 0

        fun reset() {
            upto = 0
            count = 0
            posIncr = 1
        }

        fun pullNext(): CharsRef {
            assert(upto < count)
            lastEndOffset = endOffsets[upto]
            lastPosLength = posLengths[upto]
            val result = outputs[upto++]!!
            posIncr = 0
            if (upto == count) {
                reset()
            }
            return result.get()
        }

        fun getLastEndOffset(): Int = lastEndOffset

        fun getLastPosLength(): Int = lastPosLength

        fun add(output: CharArray, offset: Int, len: Int, endOffset: Int, posLength: Int) {
            if (count == outputs.size) {
                outputs = ArrayUtil.grow(outputs, count + 1)
            }
            if (count == endOffsets.size) {
                endOffsets = ArrayUtil.grow(endOffsets, count + 1)
            }
            if (count == posLengths.size) {
                posLengths = ArrayUtil.grow(posLengths, count + 1)
            }
            if (outputs[count] == null) {
                outputs[count] = CharsRefBuilder()
            }
            outputs[count]!!.copyChars(output, offset, len)
            // endOffset can be -1, in which case we should simply
            // use the endOffset of the input token, or X >= 0, in
            // which case we use X as the endOffset for this output
            endOffsets[count] = endOffset
            posLengths[count] = posLength
            count++
        }
    }

    private val bytesReader = ByteArrayDataInput()

    // Rolling buffer, holding stack of pending synonym
    // outputs, indexed by position:
    private val futureOutputs: Array<PendingOutputs>

    // Where (in rolling buffers) to write next input saved state:
    private var nextWrite = 0

    // Where (in rolling buffers) to read next input saved state:
    private var nextRead = 0

    // True once we've read last token
    private var finished = false

    private val scratchArc: FST.Arc<BytesRef>

    private val fst: FST<BytesRef>

    private val fstReader: FST.BytesReader

    private val scratchBytes = BytesRef()
    private val scratchChars = CharsRefBuilder()

    /**
     * @param input input tokenstream
     * @param synonyms synonym map
     * @param ignoreCase case-folds input for matching with [Character.toLowerCase]. Note,
     * if you set this to true, it's your responsibility to lowercase the input entries when you
     * create the [SynonymMap]
     */
    init {
        this.fst = synonyms.fst ?: throw IllegalArgumentException("fst must be non-null")
        this.fstReader = fst.getBytesReader()

        // Must be 1+ so that when roll buffer is at full
        // lookahead we can distinguish this full buffer from
        // the empty buffer:
        rollBufferSize = 1 + synonyms.maxHorizontalContext

        futureInputs = Array(rollBufferSize) { PendingInput() }
        futureOutputs = Array(rollBufferSize) { PendingOutputs() }

        // System.out.println("FSTFilt maxH=" + synonyms.maxHorizontalContext);

        scratchArc = FST.Arc()
    }

    private fun capture() {
        captureCount++
        // System.out.println("  capture slot=" + nextWrite);
        val input = futureInputs[nextWrite]

        input.state = captureState()
        input.consumed = false
        input.term.copyChars(termAtt.buffer(), 0, termAtt.length)

        nextWrite = rollIncr(nextWrite)

        // Buffer head should never catch up to tail:
        assert(nextWrite != nextRead)
    }

    /*
       This is the core of this TokenFilter: it locates the
       synonym matches and buffers up the results into
       futureInputs/Outputs.

       NOTE: this calls input.incrementToken and does not
       capture the state if no further tokens were checked.  So
       caller must then forward state to our caller, or capture:
    */
    private var lastStartOffset = 0
    private var lastEndOffset = 0

    @Throws(IOException::class)
    private fun parse() {
        // System.out.println("\nS: parse");

        assert(inputSkipCount == 0)

        var curNextRead = nextRead

        // Holds the longest match we've seen so far:
        var matchOutput: BytesRef? = null
        var matchInputLength = 0
        var matchEndOffset = -1

        var pendingOutput = fst.outputs.noOutput
        fst.getFirstArc(scratchArc)

        assert(scratchArc.output() == fst.outputs.noOutput)

        var tokenCount = 0

        byToken@ while (true) {
            // Pull next token's chars:
            val buffer: CharArray
            val bufferLen: Int
            // System.out.println("  cycle nextRead=" + curNextRead + " nextWrite=" + nextWrite);

            var inputEndOffset = 0

            if (curNextRead == nextWrite) {
                // We used up our lookahead buffer of input tokens
                // -- pull next real input token:

                if (finished) {
                    break
                } else {
                    // System.out.println("  input.incrToken");
                    assert(futureInputs[nextWrite].consumed)
                    // Not correct: a syn match whose output is longer
                    // than its input can set future inputs keepOrig
                    // to true:
                    // assert !futureInputs[nextWrite].keepOrig;
                    if (input.incrementToken()) {
                        buffer = termAtt.buffer()
                        bufferLen = termAtt.length
                        val input = futureInputs[nextWrite]
                        lastStartOffset = offsetAtt.startOffset()
                        input.startOffset = lastStartOffset
                        lastEndOffset = offsetAtt.endOffset()
                        input.endOffset = lastEndOffset
                        inputEndOffset = input.endOffset
                        // System.out.println("  new token=" + new String(buffer, 0, bufferLen));
                        if (nextRead != nextWrite) {
                            capture()
                        } else {
                            input.consumed = false
                        }
                    } else {
                        // No more input tokens
                        // System.out.println("      set end");
                        finished = true
                        break
                    }
                }
            } else {
                // Still in our lookahead
                buffer = futureInputs[curNextRead].term.chars()
                bufferLen = futureInputs[curNextRead].term.length()
                inputEndOffset = futureInputs[curNextRead].endOffset
                // System.out.println("  old token=" + new String(buffer, 0, bufferLen));
            }

            tokenCount++

            // Run each char in this token through the FST:
            var bufUpto = 0
            while (bufUpto < bufferLen) {
                val codePoint = Character.codePointAt(buffer, bufUpto, bufferLen)
                if (fst.findTargetArc(
                        if (ignoreCase) Character.toLowerCase(codePoint) else codePoint,
                        scratchArc,
                        scratchArc,
                        fstReader
                    ) == null
                ) {
                    // System.out.println("    stop");
                    break@byToken
                }

                // Accum the output
                pendingOutput = fst.outputs.add(pendingOutput, scratchArc.output()!!)
                // System.out.println("    char=" + buffer[bufUpto] + " output=" + pendingOutput + "
                // arc.output=" + scratchArc.output);
                bufUpto += Character.charCount(codePoint)
            }

            // OK, entire token matched; now see if this is a final
            // state:
            if (scratchArc.isFinal) {
                matchOutput = fst.outputs.add(pendingOutput, scratchArc.nextFinalOutput()!!)
                matchInputLength = tokenCount
                matchEndOffset = inputEndOffset
                // System.out.println("  found matchLength=" + matchInputLength + " output=" + matchOutput);
            }

            // See if the FST wants to continue matching (ie, needs to
            // see the next input token):
            if (fst.findTargetArc(SynonymMap.WORD_SEPARATOR.code, scratchArc, scratchArc, fstReader) == null) {
                // No further rules can match here; we're done
                // searching for matching rules starting at the
                // current input position.
                break
            } else {
                // More matching is possible -- accum the output (if
                // any) of the WORD_SEP arc:
                pendingOutput = fst.outputs.add(pendingOutput, scratchArc.output()!!)
                if (nextRead == nextWrite) {
                    capture()
                }
            }

            curNextRead = rollIncr(curNextRead)
        }

        if (nextRead == nextWrite && !finished) {
            // System.out.println("  skip write slot=" + nextWrite);
            nextWrite = rollIncr(nextWrite)
        }

        if (matchOutput != null) {
            // System.out.println("  add matchLength=" + matchInputLength + " output=" + matchOutput);
            inputSkipCount = matchInputLength
            addOutput(matchOutput, matchInputLength, matchEndOffset)
        } else if (nextRead != nextWrite) {
            // Even though we had no match here, we set to 1
            // because we need to skip current input token before
            // trying to match again:
            inputSkipCount = 1
        } else {
            assert(finished)
        }

        // System.out.println("  parse done inputSkipCount=" + inputSkipCount + " nextRead=" + nextRead
        // + " nextWrite=" + nextWrite);
    }

    // Interleaves all output tokens onto the futureOutputs:
    private fun addOutput(bytes: BytesRef, matchInputLength: Int, matchEndOffset: Int) {
        bytesReader.reset(bytes.bytes, bytes.offset, bytes.length)

        val code = bytesReader.readVInt()
        val keepOrig = (code and 0x1) == 0
        val count = code ushr 1
        // System.out.println("  addOutput count=" + count + " keepOrig=" + keepOrig);
        for (outputIDX in 0 until count) {
            synonyms.words.get(bytesReader.readVInt(), scratchBytes)
            // System.out.println("    outIDX=" + outputIDX + " bytes=" + scratchBytes.length);
            scratchChars.copyUTF8Bytes(scratchBytes)
            var lastStart = 0
            val chEnd = lastStart + scratchChars.length()
            var outputUpto = nextRead
            for (chIDX in lastStart..chEnd) {
                if (chIDX == chEnd || scratchChars.charAt(chIDX) == SynonymMap.WORD_SEPARATOR) {
                    val outputLen = chIDX - lastStart
                    // Caller is not allowed to have empty string in
                    // the output:
                    assert(outputLen > 0) { "output contains empty string: $scratchChars" }
                    val endOffset: Int
                    val posLen: Int
                    if (chIDX == chEnd && lastStart == 0) {
                        // This rule had a single output token, so, we set
                        // this output's endOffset to the current
                        // endOffset (ie, endOffset of the last input
                        // token it matched):
                        endOffset = matchEndOffset
                        posLen = if (keepOrig) matchInputLength else 1
                    } else {
                        // This rule has more than one output token; we
                        // can't pick any particular endOffset for this
                        // case, so, we inherit the endOffset for the
                        // input token which this output overlaps:
                        endOffset = -1
                        posLen = 1
                    }
                    futureOutputs[outputUpto].add(scratchChars.chars(), lastStart, outputLen, endOffset, posLen)
                    // System.out.println("      " + new String(scratchChars.chars, lastStart, outputLen) + "
                    // outputUpto=" + outputUpto);
                    lastStart = 1 + chIDX
                    // System.out.println("  slot=" + outputUpto + " keepOrig=" + keepOrig);
                    outputUpto = rollIncr(outputUpto)
                    assert(futureOutputs[outputUpto].posIncr == 1) {
                        "outputUpto=$outputUpto vs nextWrite=$nextWrite"
                    }
                }
            }
        }

        var upto = nextRead
        for (idx in 0 until matchInputLength) {
            futureInputs[upto].keepOrig = futureInputs[upto].keepOrig or keepOrig
            futureInputs[upto].matched = true
            upto = rollIncr(upto)
        }
    }

    // ++ mod rollBufferSize
    private fun rollIncr(count: Int): Int {
        val next = count + 1
        return if (next == rollBufferSize) {
            0
        } else {
            next
        }
    }

    // for testing
    fun getCaptureCount(): Int {
        return captureCount
    }

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        // System.out.println("\nS: incrToken inputSkipCount=" + inputSkipCount + " nextRead=" +
        // nextRead + " nextWrite=" + nextWrite);

        while (true) {
            // First play back any buffered future inputs/outputs
            // w/o running parsing again:
            while (inputSkipCount != 0) {
                // At each position, we first output the original
                // token

                // TODO: maybe just a PendingState class, holding
                // both input & outputs?
                val input = futureInputs[nextRead]
                val outputs = futureOutputs[nextRead]

                // System.out.println("  cycle nextRead=" + nextRead + " nextWrite=" + nextWrite + "
                // inputSkipCount="+ inputSkipCount + " input.keepOrig=" + input.keepOrig + "
                // input.consumed=" + input.consumed + " input.state=" + input.state);

                if (!input.consumed && (input.keepOrig || !input.matched)) {
                    if (input.state != null) {
                        // Return a previously saved token (because we
                        // had to lookahead):
                        restoreState(input.state)
                    } else {
                        // Pass-through case: return token we just pulled
                        // but didn't capture:
                        assert(inputSkipCount == 1) {
                            "inputSkipCount=$inputSkipCount nextRead=$nextRead"
                        }
                    }
                    input.reset()
                    if (outputs.count > 0) {
                        outputs.posIncr = 0
                    } else {
                        nextRead = rollIncr(nextRead)
                        inputSkipCount--
                    }
                    // System.out.println("  return token=" + termAtt.toString());
                    return true
                } else if (outputs.upto < outputs.count) {
                    // Still have pending outputs to replay at this
                    // position
                    input.reset()
                    val posIncr = outputs.posIncr
                    val output = outputs.pullNext()
                    clearAttributes()
                    termAtt.copyBuffer(output.chars, output.offset, output.length)
                    typeAtt.setType(TYPE_SYNONYM)
                    var endOffset = outputs.getLastEndOffset()
                    if (endOffset == -1) {
                        endOffset = input.endOffset
                    }
                    offsetAtt.setOffset(input.startOffset, endOffset)
                    posIncrAtt.setPositionIncrement(posIncr)
                    posLenAtt.positionLength = outputs.getLastPosLength()
                    if (outputs.count == 0) {
                        // Done with the buffered input and all outputs at
                        // this position
                        nextRead = rollIncr(nextRead)
                        inputSkipCount--
                    }
                    // System.out.println("  return token=" + termAtt.toString());
                    return true
                } else {
                    // Done with the buffered input and all outputs at
                    // this position
                    input.reset()
                    nextRead = rollIncr(nextRead)
                    inputSkipCount--
                }
            }

            if (finished && nextRead == nextWrite) {
                // End case: if any output syns went beyond end of
                // input stream, enumerate them now:
                val outputs = futureOutputs[nextRead]
                if (outputs.upto < outputs.count) {
                    val posIncr = outputs.posIncr
                    val output = outputs.pullNext()
                    futureInputs[nextRead].reset()
                    if (outputs.count == 0) {
                        val next = rollIncr(nextRead)
                        nextWrite = next
                        nextRead = next
                    }
                    clearAttributes()
                    // Keep offset from last input token:
                    offsetAtt.setOffset(lastStartOffset, lastEndOffset)
                    termAtt.copyBuffer(output.chars, output.offset, output.length)
                    typeAtt.setType(TYPE_SYNONYM)
                    // System.out.println("  set posIncr=" + outputs.posIncr + " outputs=" + outputs);
                    posIncrAtt.setPositionIncrement(posIncr)
                    // System.out.println("  return token=" + termAtt.toString());
                    return true
                } else {
                    return false
                }
            }

            // Find new synonym matches:
            parse()
        }
    }

    @Throws(IOException::class)
    override fun reset() {
        super.reset()
        captureCount = 0
        finished = false
        inputSkipCount = 0
        nextRead = 0
        nextWrite = 0

        // In normal usage these resets would not be needed,
        // since they reset-as-they-are-consumed, but the app
        // may not consume all input tokens (or we might hit an
        // exception), in which case we have leftover state
        // here:
        for (input in futureInputs) {
            input.reset()
        }
        for (output in futureOutputs) {
            output.reset()
        }
    }
}
