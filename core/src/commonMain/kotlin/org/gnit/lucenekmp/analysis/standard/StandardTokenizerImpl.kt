package org.gnit.lucenekmp.analysis.standard

import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import kotlinx.io.IOException
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.fromCharArray
import kotlin.math.min

/**
 * This class implements Word Break rules from the Unicode Text Segmentation
 * algorithm, as specified in
 * [Unicode Standard Annex #29](http://unicode.org/reports/tr29/).
 *
 *
 * Tokens produced are of the following types:
 *
 *  * &lt;ALPHANUM&gt;: A sequence of alphabetic and numeric characters
 *  * &lt;NUM&gt;: A number
 *  * &lt;SOUTHEAST_ASIAN&gt;: A sequence of characters from South and Southeast
 * Asian languages, including Thai, Lao, Myanmar, and Khmer
 *  * &lt;IDEOGRAPHIC&gt;: A single CJKV ideographic character
 *  * &lt;HIRAGANA&gt;: A single hiragana character
 *  * &lt;KATAKANA&gt;: A sequence of katakana characters
 *  * &lt;HANGUL&gt;: A sequence of Hangul characters
 *  * &lt;EMOJI&gt;: A sequence of Emoji characters
 *
 */
// See https://github.com/jflex-de/jflex/issues/222
@Suppress("unused")
class StandardTokenizerImpl(`in`: Reader) {
    /** Initial size of the lookahead buffer.  */
    private var ZZ_BUFFERSIZE = 255

    /** Input device.  */
    private var zzReader: Reader

    /** Current state of the DFA.  */
    private var zzState = 0

    /** Current lexical state.  */
    private var zzLexicalState = YYINITIAL

    /**
     * This buffer contains the current text to be matched and is the source of the [.yytext]
     * string.
     */
    private var zzBuffer: CharArray = CharArray(ZZ_BUFFERSIZE)

    /** Text position at the last accepting state.  */
    private var zzMarkedPos = 0

    /** Current text position in the buffer.  */
    private var zzCurrentPos = 0

    /** Marks the beginning of the [.yytext] string in the buffer.  */
    private var zzStartRead = 0

    /** Marks the last character in the buffer, that has been read from input.  */
    private var zzEndRead = 0

    /**
     * Whether the scanner is at the end of file.
     * @see .yyatEOF
     */
    private var zzAtEOF = false

    /**
     * The number of occupied positions in [.zzBuffer] beyond [.zzEndRead].
     *
     *
     * When a lead/high surrogate has been read from the input stream into the final
     * [.zzBuffer] position, this will have a value of 1; otherwise, it will have a value of 0.
     */
    private var zzFinalHighSurrogate = 0

    /** Number of newlines encountered up to the start of the matched text.  */
    @Suppress("unused")
    private var yyline = 0

    /** Number of characters from the last newline up to the start of the matched text.  */
    @Suppress("unused")
    private var yycolumn = 0

    /** Number of characters up to the start of the matched text.  */
    private var yychar: Long = 0

    /** Whether the scanner is currently at the beginning of a line.  */
    @Suppress("unused")
    private var zzAtBOL = true

    /** Whether the user-EOF-code has already been executed.  */
    @Suppress("unused")
    private var zzEOFDone = false

    /** Character count processed so far  */
    fun yychar(): Int {
        // jflex supports > 2GB docs but not lucene
        return yychar.toInt()
    }

    /**
     * Fills CharTermAttribute with the current token text.
     */
    fun getText(t: CharTermAttribute) {
        t.copyBuffer(zzBuffer, zzStartRead, zzMarkedPos - zzStartRead)
    }

    /**
     * Sets the scanner buffer size in chars
     */
    fun setBufferSize(numChars: Int) {
        ZZ_BUFFERSIZE = numChars
        val newZzBuffer = CharArray(ZZ_BUFFERSIZE)
        System.arraycopy(zzBuffer, 0, newZzBuffer, 0, min(zzBuffer.size, ZZ_BUFFERSIZE))
        zzBuffer = newZzBuffer
    }


    /**
     * Creates a new scanner
     *
     * @param   in  the Reader to read input from.
     */
    init {
        this.zzReader = `in`
    }

    /**
     * Refills the input buffer.
     *
     * @return `false` iff there was new input.
     * @exception java.io.IOException  if any I/O-Error occurs
     */
    @Throws(IOException::class)
    private fun zzRefill(): Boolean {
        /* first: make room (if you can) */

        if (zzStartRead > 0) {
            zzEndRead += zzFinalHighSurrogate
            zzFinalHighSurrogate = 0
            System.arraycopy(
                zzBuffer, zzStartRead,
                zzBuffer, 0,
                zzEndRead - zzStartRead
            )

            /* translate stored positions */
            zzEndRead -= zzStartRead
            zzCurrentPos -= zzStartRead
            zzMarkedPos -= zzStartRead
            zzStartRead = 0
        }

        /* fill the buffer with new input */
        val requested = zzBuffer.size - zzEndRead - zzFinalHighSurrogate
        if (requested == 0) {
            return true
        }
        val numRead: Int = zzReader!!.read(zzBuffer, zzEndRead, requested)

        /* not supposed to occur according to specification of Reader */
        if (numRead == 0) {
            throw IOException(
                "Reader returned 0 characters. See JFlex examples/zero-reader for a workaround."
            )
        }
        if (numRead > 0) {
            zzEndRead += numRead
            if (Character.isHighSurrogate(zzBuffer[zzEndRead - 1])) {
                if (numRead == requested) { // We requested too few chars to encode a full Unicode character
                    --zzEndRead
                    zzFinalHighSurrogate = 1
                    if (numRead == 1) {
                        return true
                    }
                } else {                    // There is room in the buffer for at least one more char
                    val c: Int = zzReader!!.read() // Expecting to read a paired low surrogate char
                    if (c == -1) {
                        return true
                    } else {
                        zzBuffer[zzEndRead++] = c.toChar()
                    }
                }
            }
            /* potentially more input available */
            return false
        }

        /* numRead < 0 ==> end of stream */
        return true
    }


    /**
     * Closes the input reader.
     *
     * @throws java.io.IOException if the reader could not be closed.
     */
    @Throws(IOException::class)
    fun yyclose() {
        zzAtEOF = true // indicate end of file
        zzEndRead = zzStartRead // invalidate buffer

        if (zzReader != null) {
            zzReader!!.close()
        }
    }


    /**
     * Resets the scanner to read from a new input stream.
     *
     *
     * Does not close the old reader.
     *
     *
     * All internal variables are reset, the old input stream **cannot** be reused (internal
     * buffer is discarded and lost). Lexical state is set to `ZZ_INITIAL`.
     *
     *
     * Internal scan buffer is resized down to its initial length, if it has grown.
     *
     * @param reader The new input stream.
     */
    fun yyreset(reader: Reader) {
        zzReader = reader
        zzEOFDone = false
        yyResetPosition()
        zzLexicalState = YYINITIAL
        if (zzBuffer.size > ZZ_BUFFERSIZE) {
            zzBuffer = CharArray(ZZ_BUFFERSIZE)
        }
    }

    /**
     * Resets the input position.
     */
    private fun yyResetPosition() {
        zzAtBOL = true
        zzAtEOF = false
        zzCurrentPos = 0
        zzMarkedPos = 0
        zzStartRead = 0
        zzEndRead = 0
        zzFinalHighSurrogate = 0
        yyline = 0
        yycolumn = 0
        yychar = 0L
    }


    /**
     * Returns whether the scanner has reached the end of the reader it reads from.
     *
     * @return whether the scanner has reached EOF.
     */
    fun yyatEOF(): Boolean {
        return zzAtEOF
    }


    /**
     * Returns the current lexical state.
     *
     * @return the current lexical state.
     */
    fun yystate(): Int {
        return zzLexicalState
    }


    /**
     * Enters a new lexical state.
     *
     * @param newState the new lexical state
     */
    fun yybegin(newState: Int) {
        zzLexicalState = newState
    }


    /**
     * Returns the text matched by the current regular expression.
     *
     * @return the matched text.
     */
    fun yytext(): String {
        return String.fromCharArray(zzBuffer, zzStartRead, zzMarkedPos - zzStartRead)
    }


    /**
     * Returns the character at the given position from the matched text.
     *
     *
     * It is equivalent to `yytext().charAt(pos)`, but faster.
     *
     * @param position the position of the character to fetch. A value from 0 to `yylength()-1`.
     *
     * @return the character at `position`.
     */
    fun yycharat(position: Int): Char {
        return zzBuffer[zzStartRead + position]
    }


    /**
     * How many characters were matched.
     *
     * @return the length of the matched text region.
     */
    fun yylength(): Int {
        return zzMarkedPos - zzStartRead
    }


    /**
     * Pushes the specified amount of characters back into the input stream.
     *
     *
     * They will be read again by then next call of the scanning method.
     *
     * @param number the number of characters to be read again. This number must not be greater than
     * [.yylength].
     */
    fun yypushback(number: Int) {
        if (number > yylength()) zzScanError(ZZ_PUSHBACK_2BIG)

        zzMarkedPos -= number
    }


    @get:Throws(IOException::class)
    val nextToken: Int
        /**
         * Resumes scanning until the next regular expression is matched, the end of input is encountered
         * or an I/O-Error occurs.
         *
         * @return the next token.
         * @exception java.io.IOException if any I/O-Error occurs.
         */
        get() {
            var zzInput = 0 // not sure if 0 is ok here
            var zzAction: Int

            // cached fields:
            var zzCurrentPosL: Int
            var zzMarkedPosL: Int
            var zzEndReadL = zzEndRead
            var zzBufferL: CharArray = zzBuffer

            val zzTransL = ZZ_TRANS
            val zzRowMapL = ZZ_ROWMAP
            val zzAttrL = ZZ_ATTRIBUTE

            while (true) {
                zzMarkedPosL = zzMarkedPos

                yychar += (zzMarkedPosL - zzStartRead).toLong()

                zzAction = -1

                zzStartRead = zzMarkedPosL
                zzCurrentPos = zzStartRead
                zzCurrentPosL = zzCurrentPos

                zzState = ZZ_LEXSTATE!![zzLexicalState]

                // set up zzAction for empty match case:
                var zzAttributes = zzAttrL[zzState]
                if ((zzAttributes and 1) == 1) {
                    zzAction = zzState
                }


                zzForAction@{
                    zzForAction@ while (true) {
                        if (zzCurrentPosL < zzEndReadL) {
                            zzInput = Character.codePointAt(zzBufferL, zzCurrentPosL, zzEndReadL)
                            zzCurrentPosL += Character.charCount(zzInput)
                        } else if (zzAtEOF) {
                            zzInput = YYEOF
                            break@zzForAction
                        } else {
                            // store back cached positions
                            zzCurrentPos = zzCurrentPosL
                            zzMarkedPos = zzMarkedPosL
                            val eof = zzRefill()
                            // get translated positions and possibly new buffer
                            zzCurrentPosL = zzCurrentPos
                            zzMarkedPosL = zzMarkedPos
                            zzBufferL = zzBuffer
                            zzEndReadL = zzEndRead
                            if (eof) {
                                zzInput = YYEOF
                                break@zzForAction
                            } else {
                                zzInput = Character.codePointAt(zzBufferL, zzCurrentPosL, zzEndReadL)
                                zzCurrentPosL += Character.charCount(zzInput)
                            }
                        }
                        val zzNext = zzTransL[zzRowMapL[zzState] + zzCMap(zzInput)]
                        if (zzNext == -1) break@zzForAction
                        zzState = zzNext

                        zzAttributes = zzAttrL[zzState]
                        if ((zzAttributes and 1) == 1) {
                            zzAction = zzState
                            zzMarkedPosL = zzCurrentPosL
                            if ((zzAttributes and 8) == 8) break@zzForAction
                        }
                    }
                }

                // store back cached position
                zzMarkedPos = zzMarkedPosL

                if (zzInput == YYEOF && zzStartRead == zzCurrentPos) {
                    zzAtEOF = true
                    run {
                        return YYEOF
                    }
                } else {
                    when (if (zzAction < 0) zzAction else ZZ_ACTION[zzAction]) {
                        1 -> {}
                        10 -> {}
                        2 -> {
                            return NUMERIC_TYPE
                        }

                        11 -> {}
                        3 -> {
                            return WORD_TYPE
                        }

                        12 -> {}
                        4 -> {
                            return EMOJI_TYPE
                        }

                        13 -> {}
                        5 -> {
                            return SOUTH_EAST_ASIAN_TYPE
                        }

                        14 -> {}
                        6 -> {
                            return HANGUL_TYPE
                        }

                        15 -> {}
                        7 -> {
                            return IDEOGRAPHIC_TYPE
                        }

                        16 -> {}
                        8 -> {
                            return KATAKANA_TYPE
                        }

                        17 -> {}
                        9 -> {
                            return HIRAGANA_TYPE
                        }

                        18 -> {}
                        else -> zzScanError(ZZ_NO_MATCH)
                    }
                }
            }
        }

    fun getNextToken() = nextToken


    companion object {
        /** This character denotes the end of file.  */
        const val YYEOF: Int = -1

        /** Lexical States.  */
        const val YYINITIAL: Int = 0

        /**
         * ZZ_LEXSTATE[l] is the state in the DFA for the lexical state l
         * ZZ_LEXSTATE[l+1] is the state in the DFA for the lexical state l
         * at the beginning of a line
         * l is of the form l = 2*k, k a non negative integer
         */
        private val ZZ_LEXSTATE: IntArray? = intArrayOf(
            0, 0
        )

        /**
         * Top-level table for translating characters to character classes
         */
        private val ZZ_CMAP_TOP = zzUnpackcmap_top()

        private const val ZZ_CMAP_TOP_PACKED_0 =
            "\u0001\u0000\u0001\u0100\u0001\u0200\u0001\u0300\u0001\u0400\u0001\u0500\u0001\u0600\u0001\u0700" +
                    "\u0001\u0800\u0001\u0900\u0001\u0a00\u0001\u0b00\u0001\u0c00\u0001\u0d00\u0001\u0e00\u0001\u0f00" +
                    "\u0001\u1000\u0001\u1100\u0001\u1200\u0001\u1300\u0001\u1400\u0001\u0100\u0001\u1500\u0001\u1600" +
                    "\u0001\u1700\u0001\u1800\u0001\u1900\u0001\u1a00\u0001\u1b00\u0001\u1c00\u0001\u0100\u0001\u1d00" +
                    "\u0001\u1e00\u0001\u1f00\u0001\u2000\u0001\u2100\u0001\u2200\u0001\u2300\u0001\u2400\u0001\u2500" +
                    "\u0001\u2000\u0001\u2600\u0001\u2000\u0001\u2700\u0001\u2800\u0001\u2900\u0001\u2a00\u0001\u2b00" +
                    "\u0001\u2c00\u0001\u2d00\u0001\u2e00\u0001\u2f00\u0019\u3000\u0001\u3100\u0051\u3000\u0001\u3200" +
                    "\u0004\u0100\u0001\u3300\u0001\u0100\u0001\u3400\u0001\u3500\u0001\u3600\u0001\u3700\u0001\u3800" +
                    "\u0001\u3900\u002b\u1100\u0001\u3a00\u0021\u2000\u0001\u3000\u0001\u3b00\u0001\u3c00\u0001\u0100" +
                    "\u0001\u3d00\u0001\u3e00\u0001\u3f00\u0001\u4000\u0001\u4100\u0001\u4200\u0001\u4300\u0001\u4400" +
                    "\u0001\u4500\u0001\u0100\u0001\u4600\u0001\u4700\u0001\u4800\u0001\u4900\u0001\u4a00\u0001\u4b00" +
                    "\u0001\u4c00\u0001\u2000\u0001\u4d00\u0001\u4e00\u0001\u4f00\u0001\u5000\u0001\u5100\u0001\u5200" +
                    "\u0001\u5300\u0001\u5400\u0001\u5500\u0001\u5600\u0001\u5700\u0001\u5800\u0001\u2000\u0001\u5900" +
                    "\u0001\u5a00\u0001\u5b00\u0001\u2000\u0003\u0100\u0001\u5c00\u0001\u5d00\u0001\u5e00\u000a\u2000" +
                    "\u0004\u0100\u0001\u5f00\u000f\u2000\u0002\u0100\u0001\u6000\u0021\u2000\u0002\u0100\u0001\u6100" +
                    "\u0001\u6200\u0002\u2000\u0001\u6300\u0001\u6400\u0040\u2000\u0001\u6500\u0001\u6600\u000a\u2000" +
                    "\u0001\u6700\u0014\u2000\u0001\u6800\u0001\u6900\u0001\u2000\u0001\u6a00\u0001\u6b00\u0001\u6c00" +
                    "\u0001\u6d00\u0002\u2000\u0001\u6e00\u0005\u2000\u0001\u6f00\u0001\u7000\u0001\u7100\u0005\u2000" +
                    "\u0001\u7200\u0001\u7300\u0004\u2000\u0001\u7400\u0001\u2000\u0001\u7500\u0001\u7600\u0001\u7700" +
                    "\u0001\u7800\u0001\u7900\u0001\u7a00\u0001\u7b00\u0001\u7c00\u0001\u7d00\u0001\u7e00\u0001\u7f00" +
                    "\u0004\u8000\u0001\u8100\u00a6\u3000\u0001\u8200\u0010\u3000\u0001\u8300\u0001\u8400\u0015\u3000" +
                    "\u0001\u8500\u001c\u3000\u0001\u8600\u000c\u2000\u0002\u3000\u0001\u8700\u0b05\u2000\u0001\u8800" +
                    "\u0001\u8900\u02fe\u2000"

        private fun zzUnpackcmap_top(): IntArray {
            val result = IntArray(4352)
            var offset = 0
            offset = zzUnpackcmap_top(ZZ_CMAP_TOP_PACKED_0, offset, result)
            return result
        }

        private fun zzUnpackcmap_top(packed: String, offset: Int, result: IntArray): Int {
            var i = 0 /* index in packed string  */
            var j = offset /* index in unpacked array */
            val l = packed.length
            while (i < l) {
                var count = packed[i++].code
                val value = packed[i++].code
                do result[j++] = value while (--count > 0)
            }
            return j
        }


        /**
         * Second-level tables for translating characters to character classes
         */
        private val ZZ_CMAP_BLOCKS = zzUnpackcmap_blocks()

        private const val ZZ_CMAP_BLOCKS_PACKED_0 =
            "\u0022\u0000\u0001\u0001\u0001\u0002\u0003\u0000\u0001\u0003\u0002\u0000\u0001\u0002\u0001\u0000" +
                    "\u0001\u0004\u0001\u0000\u0001\u0005\u0001\u0000\u000a\u0006\u0001\u0007\u0001\u0004\u0005\u0000" +
                    "\u001a\u0008\u0004\u0000\u0001\u0009\u0001\u0000\u001a\u0008\u002e\u0000\u0001\u000a\u0001\u0008" +
                    "\u0002\u0000\u0001\u000b\u0001\u000a\u0006\u0000\u0001\u0008\u0001\u0000\u0001\u0007\u0002\u0000" +
                    "\u0001\u0008\u0005\u0000\u0017\u0008\u0001\u0000\u001f\u0008\u0001\u0000\u01e0\u0008\u0006\u0000" +
                    "\u0007\u0008\u0007\u0000\u0014\u0008\u0070\u000b\u0005\u0008\u0001\u0000\u0002\u0008\u0002\u0000" +
                    "\u0004\u0008\u0001\u0004\u0001\u0008\u0006\u0000\u0001\u0008\u0001\u0007\u0003\u0008\u0001\u0000" +
                    "\u0001\u0008\u0001\u0000\u0014\u0008\u0001\u0000\u0053\u0008\u0001\u0000\u008b\u0008\u0001\u0000" +
                    "\u0007\u000b\u00a6\u0008\u0001\u0000\u0026\u0008\u0002\u0000\u0001\u0008\u0001\u0000\u0002\u0008" +
                    "\u0001\u0000\u0001\u0008\u0001\u0000\u0029\u0008\u0001\u0004\u0007\u0000\u002d\u000b\u0001\u0000" +
                    "\u0001\u000b\u0001\u0000\u0002\u000b\u0001\u0000\u0002\u000b\u0001\u0000\u0001\u000b\u0008\u0000" +
                    "\u001b\u000c\u0004\u0000\u0004\u000c\u0001\u0008\u0001\u0007\u000b\u0000\u0006\u000b\u0006\u0000" +
                    "\u0002\u0004\u0002\u0000\u000b\u000b\u0001\u0000\u0001\u000b\u0003\u0000\u002b\u0008\u0015\u000b" +
                    "\u000a\u000d\u0001\u0000\u0001\u000d\u0001\u0004\u0001\u0000\u0002\u0008\u0001\u000b\u0063\u0008" +
                    "\u0001\u0000\u0001\u0008\u0008\u000b\u0001\u0000\u0006\u000b\u0002\u0008\u0002\u000b\u0001\u0000" +
                    "\u0004\u000b\u0002\u0008\u000a\u000d\u0003\u0008\u0002\u0000\u0001\u0008\u000f\u0000\u0001\u000b" +
                    "\u0001\u0008\u0001\u000b\u001e\u0008\u001b\u000b\u0002\u0000\u0059\u0008\u000b\u000b\u0001\u0008" +
                    "\u000e\u0000\u000a\u000d\u0021\u0008\u0009\u000b\u0002\u0008\u0002\u0000\u0001\u0004\u0001\u0000" +
                    "\u0001\u0008\u0002\u0000\u0001\u000b\u0002\u0000\u0016\u0008\u0004\u000b\u0001\u0008\u0009\u000b" +
                    "\u0001\u0008\u0003\u000b\u0001\u0008\u0005\u000b\u0012\u0000\u0019\u0008\u0003\u000b\u0004\u0000" +
                    "\u000b\u0008\u0035\u0000\u0015\u0008\u0001\u0000\u0008\u0008\u0015\u0000\u0031\u000b\u0036\u0008" +
                    "\u0003\u000b\u0001\u0008\u0012\u000b\u0001\u0008\u0007\u000b\u000a\u0008\u0002\u000b\u0002\u0000" +
                    "\u000a\u000d\u0001\u0000\u0010\u0008\u0003\u000b\u0001\u0000\u0008\u0008\u0002\u0000\u0002\u0008" +
                    "\u0002\u0000\u0016\u0008\u0001\u0000\u0007\u0008\u0001\u0000\u0001\u0008\u0003\u0000\u0004\u0008" +
                    "\u0002\u0000\u0001\u000b\u0001\u0008\u0007\u000b\u0002\u0000\u0002\u000b\u0002\u0000\u0003\u000b" +
                    "\u0001\u0008\u0008\u0000\u0001\u000b\u0004\u0000\u0002\u0008\u0001\u0000\u0003\u0008\u0002\u000b" +
                    "\u0002\u0000\u000a\u000d\u0002\u0008\u000a\u0000\u0001\u0008\u0001\u0000\u0001\u000b\u0002\u0000" +
                    "\u0003\u000b\u0001\u0000\u0006\u0008\u0004\u0000\u0002\u0008\u0002\u0000\u0016\u0008\u0001\u0000" +
                    "\u0007\u0008\u0001\u0000\u0002\u0008\u0001\u0000\u0002\u0008\u0001\u0000\u0002\u0008\u0002\u0000" +
                    "\u0001\u000b\u0001\u0000\u0005\u000b\u0004\u0000\u0002\u000b\u0002\u0000\u0003\u000b\u0003\u0000" +
                    "\u0001\u000b\u0007\u0000\u0004\u0008\u0001\u0000\u0001\u0008\u0007\u0000\u000a\u000d\u0002\u000b" +
                    "\u0003\u0008\u0001\u000b\u000b\u0000\u0003\u000b\u0001\u0000\u0009\u0008\u0001\u0000\u0003\u0008" +
                    "\u0001\u0000\u0016\u0008\u0001\u0000\u0007\u0008\u0001\u0000\u0002\u0008\u0001\u0000\u0005\u0008" +
                    "\u0002\u0000\u0001\u000b\u0001\u0008\u0008\u000b\u0001\u0000\u0003\u000b\u0001\u0000\u0003\u000b" +
                    "\u0002\u0000\u0001\u0008\u000f\u0000\u0002\u0008\u0002\u000b\u0002\u0000\u000a\u000d\u0009\u0000" +
                    "\u0001\u0008\u0006\u000b\u0001\u0000\u0003\u000b\u0001\u0000\u0008\u0008\u0002\u0000\u0002\u0008" +
                    "\u0002\u0000\u0016\u0008\u0001\u0000\u0007\u0008\u0001\u0000\u0002\u0008\u0001\u0000\u0005\u0008" +
                    "\u0002\u0000\u0001\u000b\u0001\u0008\u0007\u000b\u0002\u0000\u0002\u000b\u0002\u0000\u0003\u000b" +
                    "\u0008\u0000\u0002\u000b\u0004\u0000\u0002\u0008\u0001\u0000\u0003\u0008\u0002\u000b\u0002\u0000" +
                    "\u000a\u000d\u0001\u0000\u0001\u0008\u0010\u0000\u0001\u000b\u0001\u0008\u0001\u0000\u0006\u0008" +
                    "\u0003\u0000\u0003\u0008\u0001\u0000\u0004\u0008\u0003\u0000\u0002\u0008\u0001\u0000\u0001\u0008" +
                    "\u0001\u0000\u0002\u0008\u0003\u0000\u0002\u0008\u0003\u0000\u0003\u0008\u0003\u0000\u000c\u0008" +
                    "\u0004\u0000\u0005\u000b\u0003\u0000\u0003\u000b\u0001\u0000\u0004\u000b\u0002\u0000\u0001\u0008" +
                    "\u0006\u0000\u0001\u000b\u000e\u0000\u000a\u000d\u0010\u0000\u0005\u000b\u0008\u0008\u0001\u0000" +
                    "\u0003\u0008\u0001\u0000\u0017\u0008\u0001\u0000\u0010\u0008\u0003\u0000\u0001\u0008\u0007\u000b" +
                    "\u0001\u0000\u0003\u000b\u0001\u0000\u0004\u000b\u0007\u0000\u0002\u000b\u0001\u0000\u0003\u0008" +
                    "\u0005\u0000\u0002\u0008\u0002\u000b\u0002\u0000\u000a\u000d\u0010\u0000\u0001\u0008\u0003\u000b" +
                    "\u0001\u0000\u0008\u0008\u0001\u0000\u0003\u0008\u0001\u0000\u0017\u0008\u0001\u0000\u000a\u0008" +
                    "\u0001\u0000\u0005\u0008\u0002\u0000\u0001\u000b\u0001\u0008\u0007\u000b\u0001\u0000\u0003\u000b" +
                    "\u0001\u0000\u0004\u000b\u0007\u0000\u0002\u000b\u0007\u0000\u0001\u0008\u0001\u0000\u0002\u0008" +
                    "\u0002\u000b\u0002\u0000\u000a\u000d\u0001\u0000\u0002\u0008\u000d\u0000\u0004\u000b\u0001\u0000" +
                    "\u0008\u0008\u0001\u0000\u0003\u0008\u0001\u0000\u0029\u0008\u0002\u000b\u0001\u0008\u0007\u000b" +
                    "\u0001\u0000\u0003\u000b\u0001\u0000\u0004\u000b\u0001\u0008\u0005\u0000\u0003\u0008\u0001\u000b" +
                    "\u0007\u0000\u0003\u0008\u0002\u000b\u0002\u0000\u000a\u000d\u000a\u0000\u0006\u0008\u0002\u0000" +
                    "\u0002\u000b\u0001\u0000\u0012\u0008\u0003\u0000\u0018\u0008\u0001\u0000\u0009\u0008\u0001\u0000" +
                    "\u0001\u0008\u0002\u0000\u0007\u0008\u0003\u0000\u0001\u000b\u0004\u0000\u0006\u000b\u0001\u0000" +
                    "\u0001\u000b\u0001\u0000\u0008\u000b\u0006\u0000\u000a\u000d\u0002\u0000\u0002\u000b\u000d\u0000" +
                    "\u0030\u000e\u0001\u000f\u0002\u000e\u0007\u000f\u0005\u0000\u0007\u000e\u0008\u000f\u0001\u0000" +
                    "\u000a\u000d\u0027\u0000\u0002\u000e\u0001\u0000\u0001\u000e\u0001\u0000\u0005\u000e\u0001\u0000" +
                    "\u0018\u000e\u0001\u0000\u0001\u000e\u0001\u0000\u000a\u000e\u0001\u000f\u0002\u000e\u0009\u000f" +
                    "\u0001\u000e\u0002\u0000\u0005\u000e\u0001\u0000\u0001\u000e\u0001\u0000\u0006\u000f\u0002\u0000" +
                    "\u000a\u000d\u0002\u0000\u0004\u000e\u0020\u0000\u0001\u0008\u0017\u0000\u0002\u000b\u0006\u0000" +
                    "\u000a\u000d\u000b\u0000\u0001\u000b\u0001\u0000\u0001\u000b\u0001\u0000\u0001\u000b\u0004\u0000" +
                    "\u0002\u000b\u0008\u0008\u0001\u0000\u0024\u0008\u0004\u0000\u0014\u000b\u0001\u0000\u0002\u000b" +
                    "\u0005\u0008\u000b\u000b\u0001\u0000\u0024\u000b\u0009\u0000\u0001\u000b\u0039\u0000\u002b\u000e" +
                    "\u0014\u000f\u0001\u000e\u000a\u000d\u0006\u0000\u0006\u000e\u0004\u000f\u0004\u000e\u0003\u000f" +
                    "\u0001\u000e\u0003\u000f\u0002\u000e\u0007\u000f\u0003\u000e\u0004\u000f\u000d\u000e\u000c\u000f" +
                    "\u0001\u000e\u0001\u000f\u000a\u000d\u0004\u000f\u0002\u000e\u0026\u0008\u0001\u0000\u0001\u0008" +
                    "\u0005\u0000\u0001\u0008\u0002\u0000\u002b\u0008\u0001\u0000\u0004\u0008\u0100\u0010\u0049\u0008" +
                    "\u0001\u0000\u0004\u0008\u0002\u0000\u0007\u0008\u0001\u0000\u0001\u0008\u0001\u0000\u0004\u0008" +
                    "\u0002\u0000\u0029\u0008\u0001\u0000\u0004\u0008\u0002\u0000\u0021\u0008\u0001\u0000\u0004\u0008" +
                    "\u0002\u0000\u0007\u0008\u0001\u0000\u0001\u0008\u0001\u0000\u0004\u0008\u0002\u0000\u000f\u0008" +
                    "\u0001\u0000\u0039\u0008\u0001\u0000\u0004\u0008\u0002\u0000\u0043\u0008\u0002\u0000\u0003\u000b" +
                    "\u0020\u0000\u0010\u0008\u0010\u0000\u0056\u0008\u0002\u0000\u0006\u0008\u0003\u0000\u016c\u0008" +
                    "\u0002\u0000\u0011\u0008\u0001\u0000\u001a\u0008\u0005\u0000\u004b\u0008\u0003\u0000\u000b\u0008" +
                    "\u0007\u0000\u000d\u0008\u0001\u0000\u0004\u0008\u0003\u000b\u000b\u0000\u0012\u0008\u0003\u000b" +
                    "\u000b\u0000\u0012\u0008\u0002\u000b\u000c\u0000\u000d\u0008\u0001\u0000\u0003\u0008\u0001\u0000" +
                    "\u0002\u000b\u000c\u0000\u0034\u000e\u0020\u000f\u0003\u0000\u0001\u000e\u0004\u0000\u0001\u000e" +
                    "\u0001\u000f\u0002\u0000\u000a\u000d\u0021\u0000\u0004\u000b\u0001\u0000\u000a\u000d\u0006\u0000" +
                    "\u0059\u0008\u0007\u0000\u0005\u0008\u0002\u000b\u0022\u0008\u0001\u000b\u0001\u0008\u0005\u0000" +
                    "\u0046\u0008\u000a\u0000\u001f\u0008\u0001\u0000\u000c\u000b\u0004\u0000\u000c\u000b\u000a\u0000" +
                    "\u000a\u000d\u001e\u000e\u0002\u0000\u0005\u000e\u000b\u0000\u002c\u000e\u0004\u0000\u001a\u000e" +
                    "\u0006\u0000\u000a\u000d\u0001\u000e\u0003\u0000\u0002\u000e\u0020\u0000\u0017\u0008\u0005\u000b" +
                    "\u0004\u0000\u0035\u000e\u000a\u000f\u0001\u0000\u001d\u000f\u0002\u0000\u0001\u000b\u000a\u000d" +
                    "\u0006\u0000\u000a\u000d\u0006\u0000\u000e\u000e\u0002\u0000\u000f\u000b\u0041\u0000\u0005\u000b" +
                    "\u002f\u0008\u0011\u000b\u0007\u0008\u0004\u0000\u000a\u000d\u0011\u0000\u0009\u000b\u000c\u0000" +
                    "\u0003\u000b\u001e\u0008\u000d\u000b\u0002\u0008\u000a\u000d\u002c\u0008\u000e\u000b\u000c\u0000" +
                    "\u0024\u0008\u0014\u000b\u0008\u0000\u000a\u000d\u0003\u0000\u0003\u0008\u000a\u000d\u0024\u0008" +
                    "\u0002\u0000\u0009\u0008\u0007\u0000\u002b\u0008\u0002\u0000\u0003\u0008\u0010\u0000\u0003\u000b" +
                    "\u0001\u0000\u0015\u000b\u0004\u0008\u0001\u000b\u0006\u0008\u0001\u000b\u0002\u0008\u0003\u000b" +
                    "\u0001\u0008\u0005\u0000\u00c0\u0008\u003a\u000b\u0001\u0000\u0005\u000b\u0016\u0008\u0002\u0000" +
                    "\u0006\u0008\u0002\u0000\u0026\u0008\u0002\u0000\u0006\u0008\u0002\u0000\u0008\u0008\u0001\u0000" +
                    "\u0001\u0008\u0001\u0000\u0001\u0008\u0001\u0000\u0001\u0008\u0001\u0000\u001f\u0008\u0002\u0000" +
                    "\u0035\u0008\u0001\u0000\u0007\u0008\u0001\u0000\u0001\u0008\u0003\u0000\u0003\u0008\u0001\u0000" +
                    "\u0007\u0008\u0003\u0000\u0004\u0008\u0002\u0000\u0006\u0008\u0004\u0000\u000d\u0008\u0005\u0000" +
                    "\u0003\u0008\u0001\u0000\u0007\u0008\u000f\u0000\u0001\u000b\u0001\u0011\u0002\u000b\u0008\u0000" +
                    "\u0002\u0005\u000a\u0000\u0001\u0005\u0002\u0000\u0001\u0007\u0002\u0000\u0005\u000b\u0001\u0009" +
                    "\u000c\u0000\u0001\u000a\u0002\u0000\u0002\u0009\u0003\u0000\u0001\u0004\u0004\u0000\u0001\u000a" +
                    "\u000a\u0000\u0001\u0009\u000b\u0000\u0005\u000b\u0001\u0000\u000a\u000b\u0001\u0000\u0001\u0008" +
                    "\u000d\u0000\u0001\u0008\u0010\u0000\u000d\u0008\u0033\u0000\u0013\u000b\u0001\u0012\u000d\u000b" +
                    "\u0011\u0000\u0001\u0008\u0004\u0000\u0001\u0008\u0002\u0000\u000a\u0008\u0001\u0000\u0001\u0008" +
                    "\u0003\u0000\u0005\u0008\u0004\u0000\u0001\u000a\u0001\u0000\u0001\u0008\u0001\u0000\u0001\u0008" +
                    "\u0001\u0000\u0001\u0008\u0001\u0000\u0004\u0008\u0001\u0000\u000a\u0008\u0001\u0013\u0002\u0000" +
                    "\u0004\u0008\u0005\u0000\u0005\u0008\u0004\u0000\u0001\u0008\u0011\u0000\u0029\u0008\u000b\u0000" +
                    "\u0006\u000a\u000f\u0000\u0002\u000a\u016f\u0000\u0002\u000a\u000c\u0000\u0001\u000a\u005f\u0000" +
                    "\u0001\u000a\u0046\u0000\u0001\u000a\u0019\u0000\u000b\u000a\u0004\u0000\u0003\u000a\u00bb\u0000" +
                    "\u000c\u0008\u0001\u0013\u0027\u0008\u00c0\u0000\u0002\u000a\u000a\u0000\u0001\u000a\u0009\u0000" +
                    "\u0001\u000a\u003a\u0000\u0004\u000a\u0001\u0000\u0006\u000a\u0001\u0000\u000c\u000a\u0001\u0000" +
                    "\u0072\u000a\u000a\u0000\u0076\u000a\u0002\u0000\u000b\u000a\u0001\u0000\u0001\u000a\u0001\u0000" +
                    "\u0001\u000a\u0006\u0000\u0001\u000a\u0003\u0000\u0001\u000a\u0006\u0000\u0001\u000a\u000a\u0000" +
                    "\u0002\u000a\u000f\u0000\u0001\u000a\u0002\u0000\u0001\u000a\u0004\u0000\u0001\u000a\u0001\u0000" +
                    "\u0001\u000a\u0004\u0000\u0003\u000a\u0001\u0000\u0001\u000a\u000b\u0000\u0005\u000a\u002d\u0000" +
                    "\u0003\u000a\u0009\u0000\u0001\u000a\u000e\u0000\u0001\u000a\u000e\u0000\u0001\u000a\u0074\u0000" +
                    "\u0002\u000a\u00cf\u0000\u0003\u000a\u0013\u0000\u0002\u000a\u0033\u0000\u0001\u000a\u0004\u0000" +
                    "\u0001\u000a\u00aa\u0000\u002f\u0008\u0001\u0000\u002f\u0008\u0001\u0000\u0085\u0008\u0006\u0000" +
                    "\u0004\u0008\u0003\u000b\u0002\u0008\u000c\u0000\u0026\u0008\u0001\u0000\u0001\u0008\u0005\u0000" +
                    "\u0001\u0008\u0002\u0000\u0038\u0008\u0007\u0000\u0001\u0008\u000f\u0000\u0001\u000b\u0017\u0008" +
                    "\u0009\u0000\u0007\u0008\u0001\u0000\u0007\u0008\u0001\u0000\u0007\u0008\u0001\u0000\u0007\u0008" +
                    "\u0001\u0000\u0007\u0008\u0001\u0000\u0007\u0008\u0001\u0000\u0007\u0008\u0001\u0000\u0007\u0008" +
                    "\u0001\u0000\u0020\u000b\u002f\u0000\u0001\u0008\u0050\u0000\u001a\u0014\u0001\u0000\u0059\u0014" +
                    "\u000c\u0000\u00d6\u0014\u002f\u0000\u0001\u0008\u0001\u0000\u0001\u0014\u0019\u0000\u0009\u0014" +
                    "\u0006\u000b\u0001\u000a\u0005\u0015\u0002\u0000\u0003\u0014\u0002\u0008\u0001\u000a\u0003\u0000" +
                    "\u0056\u0016\u0002\u0000\u0002\u000b\u0002\u0015\u0003\u0016\u005b\u0015\u0001\u0000\u0004\u0015" +
                    "\u0005\u0000\u002b\u0008\u0001\u0000\u005e\u0010\u0011\u0000\u001b\u0008\u0035\u0000\u0010\u0015" +
                    "\u0097\u0000\u0001\u000a\u0001\u0000\u0001\u000a\u0036\u0000\u002f\u0015\u0001\u0000\u0058\u0015" +
                    "\u00a8\u0000\u01b6\u0014\u004a\u0000\u00f0\u0014\u0010\u0000\u008d\u0008\u0043\u0000\u002e\u0008" +
                    "\u0002\u0000\u000d\u0008\u0003\u0000\u0010\u0008\u000a\u000d\u0002\u0008\u0014\u0000\u002f\u0008" +
                    "\u0004\u000b\u0001\u0000\u000a\u000b\u0001\u0000\u001f\u0008\u0002\u000b\u0050\u0008\u0002\u000b" +
                    "\u0025\u0000\u00a9\u0008\u0002\u0000\u0005\u0008\u0030\u0000\u000b\u0008\u0001\u000b\u0003\u0008" +
                    "\u0001\u000b\u0004\u0008\u0001\u000b\u0017\u0008\u0005\u000b\u0018\u0000\u0034\u0008\u000c\u0000" +
                    "\u0002\u000b\u0032\u0008\u0012\u000b\u000a\u0000\u000a\u000d\u0006\u0000\u0012\u000b\u0006\u0008" +
                    "\u0003\u0000\u0001\u0008\u0001\u0000\u0002\u0008\u0001\u000b\u000a\u000d\u001c\u0008\u0008\u000b" +
                    "\u0002\u0000\u0017\u0008\u000d\u000b\u000c\u0000\u001d\u0010\u0003\u0000\u0004\u000b\u002f\u0008" +
                    "\u000e\u000b\u000e\u0000\u0001\u0008\u000a\u000d\u0006\u0000\u0005\u000e\u0001\u000f\u000a\u000e" +
                    "\u000a\u000d\u0005\u000e\u0001\u0000\u0029\u0008\u000e\u000b\u0009\u0000\u0003\u0008\u0001\u000b" +
                    "\u0008\u0008\u0002\u000b\u0002\u0000\u000a\u000d\u0006\u0000\u001b\u000e\u0003\u000f\u0032\u000e" +
                    "\u0001\u000f\u0001\u000e\u0003\u000f\u0002\u000e\u0002\u000f\u0005\u000e\u0002\u000f\u0001\u000e" +
                    "\u0001\u000f\u0001\u000e\u0018\u0000\u0005\u000e\u000b\u0008\u0005\u000b\u0002\u0000\u0003\u0008" +
                    "\u0002\u000b\u000a\u0000\u0006\u0008\u0002\u0000\u0006\u0008\u0002\u0000\u0006\u0008\u0009\u0000" +
                    "\u0007\u0008\u0001\u0000\u0007\u0008\u0001\u0000\u0038\u0008\u0008\u0000\u0073\u0008\u0008\u000b" +
                    "\u0001\u0000\u0002\u000b\u0002\u0000\u000a\u000d\u0006\u0000\u00a4\u0010\u000c\u0000\u0017\u0010" +
                    "\u0004\u0000\u0031\u0010\u0004\u0000\u006e\u0014\u0002\u0000\u006a\u0014\u0026\u0000\u0007\u0008" +
                    "\u000c\u0000\u0005\u0008\u0005\u0000\u0001\u000c\u0001\u000b\u000a\u000c\u0001\u0000\u000d\u000c" +
                    "\u0001\u0000\u0005\u000c\u0001\u0000\u0001\u000c\u0001\u0000\u0002\u000c\u0001\u0000\u0002\u000c" +
                    "\u0001\u0000\u000a\u000c\u0062\u0008\u0021\u0000\u006b\u0008\u0012\u0000\u0040\u0008\u0002\u0000" +
                    "\u0036\u0008\u0028\u0000\u000c\u0008\u0004\u0000\u000e\u000b\u0001\u0017\u0001\u0018\u0001\u0004" +
                    "\u0002\u0000\u0001\u0007\u0001\u0004\u000b\u0000\u0010\u000b\u0003\u0000\u0002\u0009\u0018\u0000" +
                    "\u0003\u0009\u0001\u0004\u0001\u0000\u0001\u0005\u0001\u0000\u0001\u0004\u0001\u0007\u001a\u0000" +
                    "\u0005\u0008\u0001\u0000\u0087\u0008\u0002\u0000\u0001\u000b\u0007\u0000\u0001\u0005\u0004\u0000" +
                    "\u0001\u0004\u0001\u0000\u0001\u0005\u0001\u0000\u000a\u000d\u0001\u0007\u0001\u0004\u0005\u0000" +
                    "\u001a\u0008\u0004\u0000\u0001\u0009\u0001\u0000\u001a\u0008\u000b\u0000\u0038\u0015\u0002\u000b" +
                    "\u001f\u0010\u0003\u0000\u0006\u0010\u0002\u0000\u0006\u0010\u0002\u0000\u0006\u0010\u0002\u0000" +
                    "\u0003\u0010\u001c\u0000\u0003\u000b\u0004\u0000\u000c\u0008\u0001\u0000\u001a\u0008\u0001\u0000" +
                    "\u0013\u0008\u0001\u0000\u0002\u0008\u0001\u0000\u000f\u0008\u0002\u0000\u000e\u0008\u0022\u0000" +
                    "\u007b\u0008\u0045\u0000\u0035\u0008\u0088\u0000\u0001\u000b\u0082\u0000\u001d\u0008\u0003\u0000" +
                    "\u0031\u0008\u000f\u0000\u0001\u000b\u001f\u0000\u0020\u0008\u000d\u0000\u001e\u0008\u0005\u0000" +
                    "\u0026\u0008\u0005\u000b\u0005\u0000\u001e\u0008\u0002\u0000\u0024\u0008\u0004\u0000\u0008\u0008" +
                    "\u0001\u0000\u0005\u0008\u002a\u0000\u009e\u0008\u0002\u0000\u000a\u000d\u0006\u0000\u0024\u0008" +
                    "\u0004\u0000\u0024\u0008\u0004\u0000\u0028\u0008\u0008\u0000\u0034\u0008\u009c\u0000\u0037\u0008" +
                    "\u0009\u0000\u0016\u0008\u000a\u0000\u0008\u0008\u0098\u0000\u0006\u0008\u0002\u0000\u0001\u0008" +
                    "\u0001\u0000\u002c\u0008\u0001\u0000\u0002\u0008\u0003\u0000\u0001\u0008\u0002\u0000\u0017\u0008" +
                    "\u000a\u0000\u0017\u0008\u0009\u0000\u001f\u0008\u0041\u0000\u0013\u0008\u0001\u0000\u0002\u0008" +
                    "\u000a\u0000\u0016\u0008\u000a\u0000\u001a\u0008\u0046\u0000\u0038\u0008\u0006\u0000\u0002\u0008" +
                    "\u0040\u0000\u0001\u0008\u0003\u000b\u0001\u0000\u0002\u000b\u0005\u0000\u0004\u000b\u0004\u0008" +
                    "\u0001\u0000\u0003\u0008\u0001\u0000\u001d\u0008\u0002\u0000\u0003\u000b\u0004\u0000\u0001\u000b" +
                    "\u0020\u0000\u001d\u0008\u0003\u0000\u001d\u0008\u0023\u0000\u0008\u0008\u0001\u0000\u001c\u0008" +
                    "\u0002\u000b\u0019\u0000\u0036\u0008\u000a\u0000\u0016\u0008\u000a\u0000\u0013\u0008\u000d\u0000" +
                    "\u0012\u0008\u006e\u0000\u0049\u0008\u0037\u0000\u0033\u0008\u000d\u0000\u0033\u0008\u000d\u0000" +
                    "\u0024\u0008\u0004\u000b\u0008\u0000\u000a\u000d\u00c6\u0000\u001d\u0008\u000a\u0000\u0001\u0008" +
                    "\u0008\u0000\u0016\u0008\u000b\u000b\u008f\u0000\u0017\u0008\u0009\u0000\u0003\u000b\u0035\u0008" +
                    "\u000f\u000b\u001f\u0000\u000a\u000d\u000f\u0000\u0004\u000b\u002d\u0008\u000b\u000b\u0002\u0000" +
                    "\u0001\u000b\u000f\u0000\u0001\u000b\u0002\u0000\u0019\u0008\u0007\u0000\u000a\u000d\u0006\u0000" +
                    "\u0003\u000b\u0024\u0008\u000e\u000b\u0001\u0000\u000a\u000d\u0004\u0000\u0001\u0008\u0002\u000b" +
                    "\u0009\u0000\u0023\u0008\u0001\u000b\u0002\u0000\u0001\u0008\u0009\u0000\u0003\u000b\u0030\u0008" +
                    "\u000e\u000b\u0004\u0008\u0004\u0000\u0004\u000b\u0003\u0000\u000a\u000d\u0001\u0008\u0001\u0000" +
                    "\u0001\u0008\u0023\u0000\u0012\u0008\u0001\u0000\u0019\u0008\u000c\u000b\u0006\u0000\u0001\u000b" +
                    "\u0041\u0000\u0007\u0008\u0001\u0000\u0001\u0008\u0001\u0000\u0004\u0008\u0001\u0000\u000f\u0008" +
                    "\u0001\u0000\u000a\u0008\u0007\u0000\u002f\u0008\u000c\u000b\u0005\u0000\u000a\u000d\u0006\u0000" +
                    "\u0004\u000b\u0001\u0000\u0008\u0008\u0002\u0000\u0002\u0008\u0002\u0000\u0016\u0008\u0001\u0000" +
                    "\u0007\u0008\u0001\u0000\u0002\u0008\u0001\u0000\u0005\u0008\u0001\u0000\u0002\u000b\u0001\u0008" +
                    "\u0007\u000b\u0002\u0000\u0002\u000b\u0002\u0000\u0003\u000b\u0002\u0000\u0001\u0008\u0006\u0000" +
                    "\u0001\u000b\u0005\u0000\u0005\u0008\u0002\u000b\u0002\u0000\u0007\u000b\u0003\u0000\u0005\u000b" +
                    "\u008b\u0000\u0035\u0008\u0012\u000b\u0004\u0008\u0005\u0000\u000a\u000d\u0004\u0000\u0001\u000b" +
                    "\u0001\u0008\u0020\u0000\u0030\u0008\u0014\u000b\u0002\u0008\u0001\u0000\u0001\u0008\u0008\u0000" +
                    "\u000a\u000d\u00a6\u0000\u002f\u0008\u0007\u000b\u0002\u0000\u0009\u000b\u0017\u0000\u0004\u0008" +
                    "\u0002\u000b\u0022\u0000\u0030\u0008\u0011\u000b\u0003\u0000\u0001\u0008\u000b\u0000\u000a\u000d" +
                    "\u0026\u0000\u002b\u0008\u000d\u000b\u0001\u0008\u0007\u0000\u000a\u000d\u0036\u0000\u001b\u000e" +
                    "\u0002\u0000\u000f\u000f\u0004\u0000\u000a\u000d\u0002\u000e\u0003\u0000\u0001\u000e\u00c0\u0000" +
                    "\u002c\u0008\u000f\u000b\u0065\u0000\u0040\u0008\u000a\u000d\u0015\u0000\u0001\u0008\u00a0\u0000" +
                    "\u0008\u0008\u0002\u0000\u0027\u0008\u0007\u000b\u0002\u0000\u0007\u000b\u0001\u0008\u0001\u0000" +
                    "\u0001\u0008\u0001\u000b\u001b\u0000\u0001\u0008\u000a\u000b\u0028\u0008\u0007\u000b\u0001\u0008" +
                    "\u0004\u000b\u0008\u0000\u0001\u000b\u0008\u0000\u0001\u0008\u000b\u000b\u002e\u0008\u0010\u000b" +
                    "\u0003\u0000\u0001\u0008\u0022\u0000\u0039\u0008\u0007\u0000\u0009\u0008\u0001\u0000\u0025\u0008" +
                    "\u0008\u000b\u0001\u0000\u0008\u000b\u0001\u0008\u000f\u0000\u000a\u000d\u0018\u0000\u001e\u0008" +
                    "\u0002\u0000\u0016\u000b\u0001\u0000\u000e\u000b\u0049\u0000\u0007\u0008\u0001\u0000\u0002\u0008" +
                    "\u0001\u0000\u0026\u0008\u0006\u000b\u0003\u0000\u0001\u000b\u0001\u0000\u0002\u000b\u0001\u0000" +
                    "\u0007\u000b\u0001\u0008\u0001\u000b\u0008\u0000\u000a\u000d\u0006\u0000\u0006\u0008\u0001\u0000" +
                    "\u0002\u0008\u0001\u0000\u0020\u0008\u0005\u000b\u0001\u0000\u0002\u000b\u0001\u0000\u0005\u000b" +
                    "\u0001\u0008\u0007\u0000\u000a\u000d\u0136\u0000\u0013\u0008\u0004\u000b\u0009\u0000\u009a\u0008" +
                    "\u0066\u0000\u006f\u0008\u0011\u0000\u00c4\u0008\u00bc\u0000\u002f\u0008\u0001\u0000\u0009\u000b" +
                    "\u00c7\u0000\u0047\u0008\u00b9\u0000\u0039\u0008\u0007\u0000\u001f\u0008\u0001\u0000\u000a\u000d" +
                    "\u0066\u0000\u001e\u0008\u0002\u0000\u0005\u000b\u000b\u0000\u0030\u0008\u0007\u000b\u0009\u0000" +
                    "\u0004\u0008\u000c\u0000\u000a\u000d\u0009\u0000\u0015\u0008\u0005\u0000\u0013\u0008\u00b0\u0000" +
                    "\u0040\u0008\u0080\u0000\u004b\u0008\u0004\u0000\u0001\u000b\u0001\u0008\u0037\u000b\u0007\u0000" +
                    "\u0004\u000b\u000d\u0008\u0040\u0000\u0002\u0008\u0001\u0000\u0001\u0008\u001c\u0000\u0001\u0015" +
                    "\u011e\u0016\u0031\u0000\u0003\u0016\u0011\u0000\u0004\u0015\u0098\u0000\u006b\u0008\u0005\u0000" +
                    "\u000d\u0008\u0003\u0000\u0009\u0008\u0007\u0000\u000a\u0008\u0003\u0000\u0002\u000b\u0001\u0000" +
                    "\u0004\u000b\u00c1\u0000\u0005\u000b\u0003\u0000\u0016\u000b\u0002\u0000\u0007\u000b\u001e\u0000" +
                    "\u0004\u000b\u0094\u0000\u0003\u000b\u00bb\u0000\u0055\u0008\u0001\u0000\u0047\u0008\u0001\u0000" +
                    "\u0002\u0008\u0002\u0000\u0001\u0008\u0002\u0000\u0002\u0008\u0002\u0000\u0004\u0008\u0001\u0000" +
                    "\u000c\u0008\u0001\u0000\u0001\u0008\u0001\u0000\u0007\u0008\u0001\u0000\u0041\u0008\u0001\u0000" +
                    "\u0004\u0008\u0002\u0000\u0008\u0008\u0001\u0000\u0007\u0008\u0001\u0000\u001c\u0008\u0001\u0000" +
                    "\u0004\u0008\u0001\u0000\u0005\u0008\u0001\u0000\u0001\u0008\u0003\u0000\u0007\u0008\u0001\u0000" +
                    "\u0154\u0008\u0002\u0000\u0019\u0008\u0001\u0000\u0019\u0008\u0001\u0000\u001f\u0008\u0001\u0000" +
                    "\u0019\u0008\u0001\u0000\u001f\u0008\u0001\u0000\u0019\u0008\u0001\u0000\u001f\u0008\u0001\u0000" +
                    "\u0019\u0008\u0001\u0000\u001f\u0008\u0001\u0000\u0019\u0008\u0001\u0000\u0008\u0008\u0002\u0000" +
                    "\u0032\u000d\u0037\u000b\u0004\u0000\u0032\u000b\u0008\u0000\u0001\u000b\u000e\u0000\u0001\u000b" +
                    "\u0016\u0000\u0005\u000b\u0001\u0000\u000f\u000b\u0050\u0000\u0007\u000b\u0001\u0000\u0011\u000b" +
                    "\u0002\u0000\u0007\u000b\u0001\u0000\u0002\u000b\u0001\u0000\u0005\u000b\u00d5\u0000\u002d\u0008" +
                    "\u0003\u0000\u0007\u000b\u0007\u0008\u0002\u0000\u000a\u000d\u0004\u0000\u0001\u0008\u0171\u0000" +
                    "\u002c\u0008\u0004\u000b\u000a\u000d\u0006\u0000\u00c5\u0008\u000b\u0000\u0007\u000b\u0029\u0000" +
                    "\u0044\u0008\u0007\u000b\u0001\u0008\u0004\u0000\u000a\u000d\u00a6\u0000\u0004\u0008\u0001\u0000" +
                    "\u001b\u0008\u0001\u0000\u0002\u0008\u0001\u0000\u0001\u0008\u0002\u0000\u0001\u0008\u0001\u0000" +
                    "\u000a\u0008\u0001\u0000\u0004\u0008\u0001\u0000\u0001\u0008\u0001\u0000\u0001\u0008\u0006\u0000" +
                    "\u0001\u0008\u0004\u0000\u0001\u0008\u0001\u0000\u0001\u0008\u0001\u0000\u0001\u0008\u0001\u0000" +
                    "\u0003\u0008\u0001\u0000\u0002\u0008\u0001\u0000\u0001\u0008\u0002\u0000\u0001\u0008\u0001\u0000" +
                    "\u0001\u0008\u0001\u0000\u0001\u0008\u0001\u0000\u0001\u0008\u0001\u0000\u0001\u0008\u0001\u0000" +
                    "\u0002\u0008\u0001\u0000\u0001\u0008\u0002\u0000\u0004\u0008\u0001\u0000\u0007\u0008\u0001\u0000" +
                    "\u0004\u0008\u0001\u0000\u0004\u0008\u0001\u0000\u0001\u0008\u0001\u0000\u000a\u0008\u0001\u0000" +
                    "\u0011\u0008\u0005\u0000\u0003\u0008\u0001\u0000\u0005\u0008\u0001\u0000\u0011\u0008\u0044\u0000" +
                    "\u0100\u000a\u000d\u0000\u0003\u000a\u001f\u0000\u0001\u000a\u001a\u0008\u0006\u0000\u001a\u0008" +
                    "\u0002\u0000\u0004\u000a\u0002\u0013\u000c\u0008\u0002\u0013\u000a\u0008\u0004\u0000\u0001\u000a" +
                    "\u0002\u0000\u000a\u000a\u0012\u0000\u0039\u000a\u001a\u0019\u0001\u0016\u000f\u000a\u000a\u0000" +
                    "\u0001\u000a\u0014\u0000\u0001\u000a\u0002\u0000\u0009\u000a\u0001\u0000\u0004\u000a\u0009\u0000" +
                    "\u01b2\u000a\u0005\u001a\u013e\u000a\u0008\u0000\u010a\u000a\u0030\u0000\u0080\u000a\u0074\u0000" +
                    "\u000c\u000a\u0055\u0000\u002b\u000a\u000c\u0000\u0004\u000a\u0038\u0000\u0008\u000a\u000a\u0000" +
                    "\u0006\u000a\u0028\u0000\u0008\u000a\u001e\u0000\u0052\u000a\u000c\u0000\u002f\u000a\u0001\u0000" +
                    "\u000a\u000a\u0001\u0000\u03b7\u000a\u0002\u0000\u00d7\u0014\u0029\u0000\u0035\u0014\u000b\u0000" +
                    "\u00de\u0014\u0002\u0000\u0182\u0014\u000e\u0000\u0131\u0014\u001f\u0000\u001e\u0014\u00e3\u0000" +
                    "\u0001\u000b\u001e\u0000\u005f\u001b\u0001\u001c\u0080\u0000\u00f0\u000b\u0010\u0000"

        private fun zzUnpackcmap_blocks(): IntArray {
            val result = IntArray(35328)
            var offset = 0
            offset = zzUnpackcmap_blocks(ZZ_CMAP_BLOCKS_PACKED_0, offset, result)
            return result
        }

        private fun zzUnpackcmap_blocks(packed: String, offset: Int, result: IntArray): Int {
            var i = 0 /* index in packed string  */
            var j = offset /* index in unpacked array */
            val l = packed.length
            while (i < l) {
                var count = packed[i++].code
                val value = packed[i++].code
                do result[j++] = value while (--count > 0)
            }
            return j
        }

        /**
         * Translates DFA states to action switch labels.
         */
        private val ZZ_ACTION = zzUnpackAction()

        private const val ZZ_ACTION_PACKED_0 =
            "\u0001\u0000\u0002\u0001\u0001\u0002\u0001\u0003\u0001\u0001\u0001\u0004\u0001\u0003\u0001\u0002" +
                    "\u0001\u0005\u0001\u0006\u0001\u0001\u0001\u0004\u0001\u0007\u0001\u0008\u0001\u0009\u0001\u0001" +
                    "\u0001\u0004\u0001\u0000\u0001\u0004\u0002\u0000\u0001\u0002\u0001\u0004\u0001\u0002\u0001\u0000" +
                    "\u0002\u0003\u0001\u0000\u0001\u0003\u0002\u0004\u0001\u0000\u0001\u0003\u0001\u0000\u0002\u0004" +
                    "\u0001\u0000\u0004\u0004\u0001\u0000\u0001\u0004\u0002\u0000\u0001\u0004\u0002\u0003\u0002\u0004" +
                    "\u0001\u0000\u0003\u0004\u0001\u0003\u0003\u0004"

        private fun zzUnpackAction(): IntArray {
            val result = IntArray(59)
            var offset = 0
            offset = zzUnpackAction(ZZ_ACTION_PACKED_0, offset, result)
            return result
        }

        private fun zzUnpackAction(packed: String, offset: Int, result: IntArray): Int {
            var i = 0 /* index in packed string  */
            var j = offset /* index in unpacked array */
            val l = packed.length
            while (i < l) {
                var count = packed[i++].code
                val value = packed[i++].code
                do result[j++] = value while (--count > 0)
            }
            return j
        }


        /**
         * Translates a state to a row index in the transition table
         */
        private val ZZ_ROWMAP = zzUnpackRowMap()

        private const val ZZ_ROWMAP_PACKED_0 =
            "\u0000\u0000\u0000\u001d\u0000\u003a\u0000\u0057\u0000\u0074\u0000\u0091\u0000\u00ae\u0000\u00cb" +
                    "\u0000\u00e8\u0000\u0105\u0000\u0122\u0000\u013f\u0000\u015c\u0000\u0179\u0000\u0196\u0000\u01b3" +
                    "\u0000\u01d0\u0000\u01ed\u0000\u003a\u0000\u020a\u0000\u0227\u0000\u0244\u0000\u0261\u0000\u027e" +
                    "\u0000\u029b\u0000\u02b8\u0000\u02d5\u0000\u02f2\u0000\u0091\u0000\u030f\u0000\u032c\u0000\u0349" +
                    "\u0000\u0366\u0000\u0383\u0000\u013f\u0000\u03a0\u0000\u03bd\u0000\u01d0\u0000\u03da\u0000\u03f7" +
                    "\u0000\u0414\u0000\u0431\u0000\u044e\u0000\u046b\u0000\u0488\u0000\u04a5\u0000\u04c2\u0000\u04df" +
                    "\u0000\u04fc\u0000\u0519\u0000\u0536\u0000\u0553\u0000\u001d\u0000\u0570\u0000\u058d\u0000\u05aa" +
                    "\u0000\u05c7\u0000\u0074\u0000\u05e4"

        private fun zzUnpackRowMap(): IntArray {
            val result = IntArray(59)
            var offset = 0
            offset = zzUnpackRowMap(ZZ_ROWMAP_PACKED_0, offset, result)
            return result
        }

        private fun zzUnpackRowMap(packed: String, offset: Int, result: IntArray): Int {
            var i = 0 /* index in packed string  */
            var j = offset /* index in unpacked array */
            val l = packed.length
            while (i < l) {
                val high = packed[i++].code shl 16
                result[j++] = high or packed[i++].code
            }
            return j
        }

        /**
         * The transition table of the DFA
         */
        private val ZZ_TRANS = zzUnpackTrans()

        private const val ZZ_TRANS_PACKED_0 =
            "\u0002\u0002\u0001\u0003\u0003\u0002\u0001\u0004\u0001\u0002\u0001\u0005\u0001\u0006\u0001\u0007" +
                    "\u0001\u0002\u0001\u0008\u0001\u0009\u0002\u000a\u0001\u000b\u0001\u000c\u0001\u0002\u0001\u000d" +
                    "\u0001\u000e\u0001\u000f\u0001\u0010\u0002\u0002\u0001\u0011\u0001\u0012\u0002\u0002\u0028\u0000" +
                    "\u0001\u0013\u0003\u0000\u0001\u0013\u0001\u0000\u0001\u0013\u0001\u0014\u0005\u0000\u0001\u0015" +
                    "\u0001\u0000\u0003\u0013\u0003\u0000\u0003\u0016\u0001\u0009\u0001\u0000\u0001\u0005\u0001\u0017" +
                    "\u0001\u0000\u0001\u0004\u0001\u0008\u0001\u0009\u0001\u0000\u0001\u0004\u0001\u0005\u0001\u0004" +
                    "\u0001\u0018\u0001\u0005\u0003\u0000\u0001\u0009\u0001\u0019\u0001\u0000\u0003\u0004\u0003\u0000" +
                    "\u0001\u001a\u0001\u0000\u0001\u001a\u0001\u001b\u0001\u001a\u0001\u0005\u0001\u001c\u0001\u0000" +
                    "\u0001\u0005\u0001\u0008\u0001\u001b\u0001\u0000\u0005\u0005\u0003\u0000\u0002\u0005\u0001\u0000" +
                    "\u0003\u0005\u0006\u0000\u0001\u0009\u0001\u0000\u0001\u0005\u0001\u001d\u0001\u0000\u0001\u001d" +
                    "\u0001\u0008\u0001\u0009\u0001\u0000\u0001\u001d\u0001\u0005\u0002\u001d\u0001\u0005\u0001\u0000" +
                    "\u0001\u001e\u0001\u0000\u0002\u001d\u0001\u0000\u0003\u001d\u000b\u0000\u0001\u0007\u0003\u0000" +
                    "\u0001\u0007\u0001\u0000\u0001\u001f\u0001\u0007\u0005\u0000\u0001\u0020\u0001\u0000\u0003\u0007" +
                    "\u0001\u0000\u0001\u0021\u0001\u0000\u0001\u0022\u0001\u0000\u0001\u001a\u0001\u001b\u0001\u001a" +
                    "\u0001\u0005\u0001\u001c\u0001\u0000\u0002\u0008\u0001\u001b\u0001\u0000\u0001\u0008\u0001\u0005" +
                    "\u0002\u0008\u0001\u0005\u0003\u0000\u0002\u0008\u0001\u0000\u0003\u0008\u0003\u0000\u0003\u0016" +
                    "\u0001\u0009\u0001\u0000\u0001\u0005\u0001\u0017\u0001\u0000\u0001\u0009\u0001\u0008\u0001\u0009" +
                    "\u0001\u0000\u0001\u0009\u0001\u0005\u0002\u0009\u0001\u0005\u0003\u0000\u0002\u0009\u0001\u0000" +
                    "\u0003\u0009\u000b\u0000\u0001\u000a\u0002\u0000\u0002\u000a\u0001\u0000\u0002\u000a\u0004\u0000" +
                    "\u0002\u000a\u0001\u0000\u0003\u000a\u0003\u0000\u0001\u001a\u0001\u0000\u0001\u001a\u0001\u001b" +
                    "\u0001\u001a\u0001\u0005\u0001\u001c\u0001\u0000\u0001\u000b\u0001\u0008\u0001\u001b\u0001\u0000" +
                    "\u0004\u000b\u0001\u0005\u0003\u0000\u0002\u000b\u0001\u0000\u0003\u000b\u000a\u0000\u0001\u0007" +
                    "\u0006\u0000\u0001\u0023\u0001\u0000\u0001\u0007\u000c\u0000\u0001\u001a\u0001\u0000\u0001\u001a" +
                    "\u0001\u001b\u0001\u001a\u0001\u0005\u0001\u001c\u0001\u0000\u0001\u000d\u0001\u0008\u0001\u001b" +
                    "\u0001\u0000\u0001\u000d\u0001\u0005\u0001\u0024\u0001\u000d\u0001\u0005\u0003\u0000\u0001\u0005" +
                    "\u0001\u0025\u0001\u0000\u0003\u000d\u000b\u0000\u0001\u000e\u0003\u0000\u0001\u000e\u0001\u0000" +
                    "\u0002\u000e\u0004\u0000\u0002\u000e\u0001\u0000\u0003\u000e\u0009\u0000\u0001\u001c\u0001\u0000" +
                    "\u0001\u000f\u0003\u0000\u0001\u000f\u0001\u0000\u0002\u000f\u0002\u0000\u0001\u000f\u0001\u0000" +
                    "\u0002\u000f\u0001\u0000\u0003\u000f\u000b\u0000\u0001\u0010\u0003\u0000\u0001\u0010\u0001\u0000" +
                    "\u0002\u0010\u0004\u0000\u0002\u0010\u0001\u0000\u0003\u0010\u000b\u0000\u0001\u0026\u0003\u0000" +
                    "\u0001\u0026\u0001\u0000\u0002\u0026\u0004\u0000\u0002\u0026\u0001\u0027\u0003\u0026\u000b\u0000" +
                    "\u0001\u0012\u0003\u0000\u0001\u0012\u0001\u0000\u0001\u0028\u0001\u0012\u0007\u0000\u0003\u0012" +
                    "\u000b\u0000\u0001\u0014\u0003\u0000\u0001\u0014\u0001\u0000\u0002\u0014\u0005\u0000\u0001\u0015" +
                    "\u0001\u0000\u0003\u0014\u0012\u0000\u0001\u0029\u0010\u0000\u0001\u0009\u0004\u0000\u0001\u0016" +
                    "\u0001\u0000\u0001\u0009\u0001\u0000\u0001\u0016\u0001\u0000\u0002\u0016\u0004\u0000\u0002\u0016" +
                    "\u0001\u0000\u0003\u0016\u0006\u0000\u0001\u0009\u0001\u0000\u0001\u0005\u0001\u0017\u0001\u0000" +
                    "\u0001\u0017\u0001\u0008\u0001\u0009\u0001\u0000\u0001\u0017\u0001\u0005\u0002\u0017\u0001\u0005" +
                    "\u0001\u0000\u0001\u001e\u0001\u0000\u0002\u0017\u0001\u0000\u0003\u0017\u0003\u0000\u0003\u0016" +
                    "\u0001\u0009\u0001\u0000\u0001\u0005\u0001\u0017\u0001\u0000\u0001\u0018\u0001\u0008\u0001\u0009" +
                    "\u0001\u0000\u0001\u0018\u0001\u0005\u0002\u0018\u0001\u0005\u0003\u0000\u0001\u0009\u0001\u0019" +
                    "\u0001\u0000\u0003\u0018\u0003\u0000\u0003\u0016\u0001\u0009\u0001\u0000\u0001\u0005\u0001\u0017" +
                    "\u0001\u0000\u0001\u0009\u0001\u0008\u0001\u0009\u0001\u0000\u0001\u0009\u0001\u0005\u0001\u0009" +
                    "\u0001\u002a\u0001\u0005\u0003\u0000\u0002\u0009\u0001\u0000\u0003\u0009\u0008\u0000\u0001\u0005" +
                    "\u0002\u0000\u0001\u001a\u0001\u0005\u0002\u0000\u0001\u001a\u0001\u0005\u0002\u001a\u0001\u0005" +
                    "\u0003\u0000\u0002\u001a\u0001\u0000\u0003\u001a\u0003\u0000\u0003\u002b\u0001\u001b\u0001\u0000" +
                    "\u0001\u0005\u0001\u001c\u0001\u0000\u0001\u001b\u0001\u0008\u0001\u001b\u0001\u0000\u0001\u001b" +
                    "\u0001\u0005\u0002\u001b\u0001\u0005\u0003\u0000\u0002\u001b\u0001\u0000\u0003\u001b\u0006\u0000" +
                    "\u0001\u001b\u0001\u0000\u0001\u0005\u0001\u001c\u0001\u0000\u0001\u001c\u0001\u0008\u0001\u001b" +
                    "\u0001\u0000\u0001\u001c\u0001\u0005\u0002\u001c\u0001\u0005\u0001\u0000\u0001\u001e\u0001\u0000" +
                    "\u0002\u001c\u0001\u0000\u0003\u001c\u0009\u0000\u0001\u001c\u0001\u0000\u0001\u001e\u0003\u0000" +
                    "\u0001\u001e\u0001\u0000\u0002\u001e\u0002\u0000\u0001\u001e\u0001\u0000\u0002\u001e\u0001\u0000" +
                    "\u0003\u001e\u000a\u0000\u0001\u002c\u0001\u0007\u0003\u0000\u0001\u0007\u0001\u0000\u0001\u001f" +
                    "\u0001\u0007\u0001\u002c\u0004\u0000\u0001\u0020\u0001\u0000\u0003\u0007\u0011\u0000\u0001\u002d" +
                    "\u0009\u0000\u0001\u002e\u000c\u0000\u0001\u0021\u0001\u0022\u0002\u0000\u0001\u0021\u0001\u0000" +
                    "\u0002\u0021\u0004\u0000\u0002\u0021\u0001\u0000\u0003\u0021\u0006\u0000\u0001\u001b\u0001\u0000" +
                    "\u0001\u0005\u0001\u001c\u0001\u0000\u0001\u0022\u0001\u0008\u0001\u001b\u0001\u0000\u0001\u0022" +
                    "\u0001\u0005\u0002\u0022\u0001\u0005\u0003\u0000\u0002\u0022\u0001\u0000\u0003\u0022\u0003\u0000" +
                    "\u0001\u001a\u0001\u0000\u0001\u001a\u0001\u001b\u0001\u001a\u0001\u0005\u0001\u001c\u0001\u002c" +
                    "\u0001\u000d\u0001\u0008\u0001\u001b\u0001\u0000\u0001\u000d\u0001\u0005\u0001\u0024\u0001\u000d" +
                    "\u0001\u002f\u0003\u0000\u0001\u0005\u0001\u0025\u0001\u0000\u0003\u000d\u0003\u0000\u0001\u001a" +
                    "\u0001\u0000\u0001\u001a\u0001\u001b\u0001\u001a\u0001\u0005\u0001\u001c\u0001\u0000\u0001\u0005" +
                    "\u0001\u0008\u0001\u001b\u0001\u0000\u0002\u0005\u0001\u0030\u0002\u0005\u0003\u0000\u0002\u0005" +
                    "\u0001\u0000\u0001\u0005\u0001\u0031\u0001\u0005\u000b\u0000\u0001\u0027\u0003\u0000\u0001\u0027" +
                    "\u0001\u0000\u0002\u0027\u0004\u0000\u0002\u0027\u0001\u0000\u0003\u0027\u000a\u0000\u0001\u002c" +
                    "\u0001\u0012\u0003\u0000\u0001\u0012\u0001\u0000\u0001\u0028\u0001\u0012\u0001\u002c\u0006\u0000" +
                    "\u0003\u0012\u000b\u0000\u0001\u0029\u0003\u0000\u0001\u0029\u0001\u0000\u0002\u0029\u0007\u0000" +
                    "\u0003\u0029\u0003\u0000\u0003\u0016\u0001\u0009\u0001\u0000\u0001\u0005\u0001\u0017\u0001\u0000" +
                    "\u0001\u002a\u0001\u0008\u0001\u0009\u0001\u0000\u0001\u002a\u0001\u0005\u0002\u002a\u0001\u0005" +
                    "\u0003\u0000\u0002\u0009\u0001\u0000\u0003\u002a\u0006\u0000\u0001\u001b\u0004\u0000\u0001\u002b" +
                    "\u0001\u0000\u0001\u001b\u0001\u0000\u0001\u002b\u0001\u0000\u0002\u002b\u0004\u0000\u0002\u002b" +
                    "\u0001\u0000\u0003\u002b\u000b\u0000\u0001\u002c\u0003\u0000\u0001\u002c\u0001\u0000\u0001\u0032" +
                    "\u0001\u002c\u0005\u0000\u0001\u0033\u0001\u0000\u0003\u002c\u000a\u0000\u0001\u002c\u0006\u0000" +
                    "\u0001\u0034\u0001\u0000\u0001\u002c\u0006\u0000\u0001\u0012\u001d\u0000\u0001\u002e\u0001\u0035" +
                    "\u0003\u0000\u0001\u001a\u0001\u0000\u0001\u001a\u0001\u001b\u0001\u001a\u0001\u0005\u0001\u001c" +
                    "\u0001\u0000\u0001\u002f\u0001\u0008\u0001\u001b\u0001\u0000\u0001\u002f\u0001\u0005\u0001\u0036" +
                    "\u0001\u002f\u0001\u0005\u0003\u0000\u0001\u0005\u0001\u0037\u0001\u0000\u0003\u002f\u0003\u0000" +
                    "\u0001\u001a\u0001\u0000\u0001\u001a\u0001\u001b\u0001\u001a\u0001\u0005\u0001\u001c\u0001\u002c" +
                    "\u0001\u0005\u0001\u0008\u0001\u001b\u0001\u0000\u0002\u0005\u0001\u0038\u0001\u0005\u0001\u002f" +
                    "\u0003\u0000\u0002\u0005\u0001\u0000\u0001\u0039\u0002\u0005\u0003\u0000\u0001\u001a\u0001\u0000" +
                    "\u0001\u001a\u0001\u001b\u0001\u001a\u0001\u0005\u0001\u001c\u0001\u0000\u0001\u0005\u0001\u0008" +
                    "\u0001\u001b\u0001\u0000\u0005\u0005\u0003\u0000\u0002\u0005\u0001\u0000\u0001\u0005\u0001\u0031" +
                    "\u0001\u003a\u000a\u0000\u0002\u002c\u0003\u0000\u0001\u002c\u0001\u0000\u0001\u0032\u0002\u002c" +
                    "\u0004\u0000\u0001\u0033\u0001\u0000\u0003\u002c\u0011\u0000\u0001\u002d\u0015\u0000\u0001\u002c" +
                    "\u0006\u0000\u0001\u0034\u0001\u0000\u0001\u002c\u000c\u0000\u0001\u001a\u0001\u0000\u0001\u001a" +
                    "\u0001\u001b\u0001\u001a\u0001\u0005\u0001\u001c\u0001\u002c\u0001\u002f\u0001\u0008\u0001\u001b" +
                    "\u0001\u0000\u0001\u002f\u0001\u0005\u0001\u0036\u0002\u002f\u0003\u0000\u0001\u0005\u0001\u0037" +
                    "\u0001\u0000\u0003\u002f\u0003\u0000\u0001\u001a\u0001\u0000\u0001\u001a\u0001\u001b\u0001\u001a" +
                    "\u0001\u0005\u0001\u001c\u0001\u0000\u0001\u0005\u0001\u0008\u0001\u001b\u0001\u0000\u0002\u0005" +
                    "\u0001\u0030\u0002\u0005\u0003\u0000\u0002\u0005\u0001\u0000\u0003\u0005\u0003\u0000\u0001\u001a" +
                    "\u0001\u0000\u0001\u001a\u0001\u001b\u0001\u001a\u0001\u0005\u0001\u001c\u0001\u002c\u0001\u0005" +
                    "\u0001\u0008\u0001\u001b\u0001\u0000\u0002\u0005\u0001\u0038\u0001\u0005\u0001\u002f\u0003\u0000" +
                    "\u0002\u0005\u0001\u0000\u0003\u0005\u0003\u0000\u0001\u001a\u0001\u0000\u0001\u001a\u0001\u001b" +
                    "\u0001\u001a\u0001\u0005\u0001\u001c\u0001\u0000\u0001\u0039\u0001\u0008\u0001\u001b\u0001\u0000" +
                    "\u0001\u0039\u0001\u0005\u0001\u003b\u0001\u0039\u0001\u0005\u0003\u0000\u0002\u0005\u0001\u0000" +
                    "\u0003\u0039\u0003\u0000\u0001\u001a\u0001\u0000\u0001\u001a\u0001\u001b\u0001\u001a\u0001\u0005" +
                    "\u0001\u001c\u0001\u002c\u0001\u0039\u0001\u0008\u0001\u001b\u0001\u0000\u0001\u0039\u0001\u0005" +
                    "\u0001\u003b\u0001\u0039\u0001\u002f\u0003\u0000\u0002\u0005\u0001\u0000\u0003\u0039"

        private fun zzUnpackTrans(): IntArray {
            val result = IntArray(1537)
            var offset = 0
            offset = zzUnpackTrans(ZZ_TRANS_PACKED_0, offset, result)
            return result
        }

        private fun zzUnpackTrans(packed: String, offset: Int, result: IntArray): Int {
            var i = 0 /* index in packed string  */
            var j = offset /* index in unpacked array */
            val l = packed.length
            while (i < l) {
                var count = packed[i++].code
                var value = packed[i++].code
                value--
                do result[j++] = value while (--count > 0)
            }
            return j
        }


        /** Error code for "Unknown internal scanner error".  */
        private const val ZZ_UNKNOWN_ERROR = 0

        /** Error code for "could not match input".  */
        private const val ZZ_NO_MATCH = 1

        /** Error code for "pushback value was too large".  */
        private const val ZZ_PUSHBACK_2BIG = 2

        /**
         * Error messages for [.ZZ_UNKNOWN_ERROR], [.ZZ_NO_MATCH], and
         * [.ZZ_PUSHBACK_2BIG] respectively.
         */
        private val ZZ_ERROR_MSG: Array<String?>? = arrayOf<String?>(
            "Unknown internal scanner error",
            "Error: could not match input",
            "Error: pushback value was too large"
        )

        /**
         * ZZ_ATTRIBUTE[aState] contains the attributes of state `aState`
         */
        private val ZZ_ATTRIBUTE = zzUnpackAttribute()

        private const val ZZ_ATTRIBUTE_PACKED_0 =
            "\u0001\u0000\u0001\u0009\u0010\u0001\u0001\u0000\u0001\u0001\u0002\u0000\u0003\u0001\u0001\u0000" +
                    "\u0002\u0001\u0001\u0000\u0003\u0001\u0001\u0000\u0001\u0001\u0001\u0000\u0002\u0001\u0001\u0000" +
                    "\u0004\u0001\u0001\u0000\u0001\u0001\u0002\u0000\u0005\u0001\u0001\u0000\u0001\u0009\u0006\u0001"

        private fun zzUnpackAttribute(): IntArray {
            val result = IntArray(59)
            var offset = 0
            offset = zzUnpackAttribute(ZZ_ATTRIBUTE_PACKED_0, offset, result)
            return result
        }

        private fun zzUnpackAttribute(packed: String, offset: Int, result: IntArray): Int {
            var i = 0 /* index in packed string  */
            var j = offset /* index in unpacked array */
            val l = packed.length
            while (i < l) {
                var count = packed[i++].code
                val value = packed[i++].code
                do result[j++] = value while (--count > 0)
            }
            return j
        }

        /* user code: */
        /** Alphanumeric sequences  */
        const val WORD_TYPE: Int = StandardTokenizer.ALPHANUM

        /** Numbers  */
        const val NUMERIC_TYPE: Int = StandardTokenizer.NUM

        /**
         * Chars in class \p{Line_Break = Complex_Context} are from South East Asian
         * scripts (Thai, Lao, Myanmar, Khmer, etc.).  Sequences of these are kept
         * together as as a single token rather than broken up, because the logic
         * required to break them at word boundaries is too complex for UAX#29.
         *
         *
         * See Unicode Line Breaking Algorithm: http://www.unicode.org/reports/tr14/#SA
         */
        const val SOUTH_EAST_ASIAN_TYPE: Int = StandardTokenizer.SOUTHEAST_ASIAN

        /** Ideographic token type  */
        const val IDEOGRAPHIC_TYPE: Int = StandardTokenizer.IDEOGRAPHIC

        /** Hiragana token type  */
        const val HIRAGANA_TYPE: Int = StandardTokenizer.HIRAGANA

        /** Katakana token type  */
        const val KATAKANA_TYPE: Int = StandardTokenizer.KATAKANA

        /** Hangul token type  */
        const val HANGUL_TYPE: Int = StandardTokenizer.HANGUL

        /** Emoji token type  */
        const val EMOJI_TYPE: Int = StandardTokenizer.EMOJI

        /**
         * Translates raw input code points to DFA table row
         */
        private fun zzCMap(input: Int): Int {
            val offset = input and 255
            return if (offset == input) ZZ_CMAP_BLOCKS[offset] else ZZ_CMAP_BLOCKS[ZZ_CMAP_TOP[input shr 8] or offset]
        }

        /**
         * Reports an error that occurred while scanning.
         *
         *
         * In a well-formed scanner (no or only correct usage of `yypushback(int)` and a
         * match-all fallback rule) this method will only be called with things that
         * "Can't Possibly Happen".
         *
         *
         * If this method is called, something is seriously wrong (e.g. a JFlex bug producing a faulty
         * scanner etc.).
         *
         *
         * Usual syntax/scanner level error handling should be done in error fallback rules.
         *
         * @param errorCode the code of the error message to display.
         */
        private fun zzScanError(errorCode: Int) {
            val message: String? = try {
                ZZ_ERROR_MSG!![errorCode]
            } catch (e: /*Array*/IndexOutOfBoundsException) {
                ZZ_ERROR_MSG!![ZZ_UNKNOWN_ERROR]
            }

            throw Error(message)
        }
    }
}
