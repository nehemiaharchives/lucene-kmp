package org.gnit.lucenekmp.analysis.km

import okio.IOException
import org.gnit.lucenekmp.analysis.CharacterUtils
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.jdkport.Character

/**
 * Tokenizes a string in Khmer grapheme clusters (not phonetic syllables), for instance:
 * "ខ្ញុំចង់ធ្វើការ" will be tokenized as "ខ្ញុំ", "ច", "ង់", "ធ្វើ", "កា", "រ",
 * not "ខ្ញុំ", "ចង់", "ធ្វើ", "ការ". It uses a simple state machine to do so.
 */
class GraphemeClusterTokenizer : Tokenizer() {

    private var offset = 0
    private var bufferIndex = 0
    private var dataLen = 0
    private var finalOffset = 0
    val DEFAULT_MAX_WORD_LEN: Int = 255
    private val IO_BUFFER_SIZE: Int = 4096
    private val maxTokenLen = 255

    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val offsetAtt: OffsetAttribute = addAttribute(OffsetAttribute::class)

    private val ioBuffer = CharacterUtils.newCharacterBuffer(IO_BUFFER_SIZE)

    // states
    val ST_INSIDESYL: Int = 1
    val ST_AFTERCOENG: Int = 2
    val ST_AFTERDIGIT: Int = 3
    val ST_INIT: Int = 4

    // char categories
    val CHCAT_BASE: Int = 1     // consonants or independent vowels ("base")
    val CHCAT_INSIDE: Int = 2   // anything that can be inside a syllable after the base
    val CHCAT_COENG: Int = 3    // coeng
    val CHCAT_DIGIT: Int = 4    // digit
    val CHCAT_IGNORE: Int = 5   // ignore (punctuation and the rest)

    fun category(c: Int): Int {
        if (('\u17E0'.code <= c && c <= '\u17F9'.code) || ('0'.code <= c && c <= '9'.code)) return CHCAT_DIGIT
        if ('\u1780'.code <= c && c <= '\u17B3'.code) return CHCAT_BASE
        if (c == '\u17D2'.code) return CHCAT_COENG
        if (('\u17B6'.code <= c && c <= '\u17D3'.code) || c == '\u17DD'.code || c == '\u200C'.code || c == '\u200D'.code) return CHCAT_INSIDE
        return CHCAT_IGNORE
    }

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        clearAttributes()
        var length = 0
        var start = -1 // this variable is always initialized
        var end = -1
        var buffer = termAtt.buffer()
        var state = ST_INIT
        while (true) {
            if (bufferIndex >= dataLen) {
                offset += dataLen
                CharacterUtils.fill(ioBuffer, input) // read supplementary char aware with CharacterUtils
                if (ioBuffer.length == 0) {
                    dataLen = 0 // so next offset += dataLen won't decrement offset
                    if (length > 0) {
                        break
                    } else {
                        finalOffset = correctOffset(offset)
                        return false
                    }
                }
                dataLen = ioBuffer.length
                bufferIndex = 0
            }
            // use CharacterUtils here to support < 3.1 UTF-16 code unit behavior if the char based
            // methods are gone
            val c = Character.codePointAt(ioBuffer.buffer, bufferIndex, ioBuffer.length)
            val charCount = Character.charCount(c)
            bufferIndex += charCount

            val charcat = category(c)

            if (charcat == CHCAT_IGNORE) {
                if (length > 0)
                    break
                else
                    continue
            }

            // break before the next character
            var breakB = false

            /*
             * afterother+other=nocut,other
             * afterother+base=cut,inside
             * afterother+digit=cut,afterdigit
             * afterother+coeng=nocut,other
             *
             * inside+coeng=nocut,aftercoeng
             * inside+other=cut,other
             * inside+base=cut,inside
             * inside+digit=cut,afterdigit
             * inside+inside=nocut,inside
             *
             * aftercoeng+base=nocut,inside
             * aftercoeng+digit=cut,afterdigit
             * aftercoeng+other=cut,other
             * aftercoeng+inside=nocut,inside
             * aftercoeng+coeng=nocut,inside
             *
             * afterdigit+digit=nocut,afterdigit
             * afterdigit+base=cut,inside
             * afterdigit+other=cut,other
             * afterdigit+inside=cut,inside(error?)
             * afterdigit+coeng=cut,other(error?)
             */

            when (state) {
                ST_INIT -> {
                    if (charcat == CHCAT_BASE) {
                        breakB = true
                        state = ST_INSIDESYL
                    } else if (charcat == CHCAT_DIGIT) {
                        breakB = true
                        state = ST_AFTERDIGIT
                    }
                }
                ST_INSIDESYL -> {
                    if (charcat == CHCAT_COENG)
                        state = ST_AFTERCOENG
                    else if (charcat != CHCAT_INSIDE)
                        breakB = true
                }
                ST_AFTERCOENG -> {
                    if (charcat == CHCAT_DIGIT)
                        breakB = true
                    else if (charcat != CHCAT_COENG)
                        state = ST_INSIDESYL
                }
                ST_AFTERDIGIT -> {
                    if (charcat != CHCAT_DIGIT)
                        breakB = true
                }
            }

            if (breakB && length > 0) {
                bufferIndex -= charCount
                break
            }

            if (length == 0) { // start of token
                start = offset + bufferIndex - charCount
                end = start
                when (charcat) {
                    CHCAT_BASE -> state = ST_INSIDESYL
                    CHCAT_DIGIT -> state = ST_AFTERDIGIT
                }
            } else if (length >= buffer.size - 1) { // supplementary could run out of bounds?
                // make sure a supplementary fits in the buffer
                buffer = termAtt.resizeBuffer(2 + length)
            }

            end += charCount
            length += Character.toChars(c, buffer, length) // buffer it, normalized
            // buffer overflow! make sure to check for >= surrogate pair could break == test
            if (length >= maxTokenLen) {
                break
            }
        }

        termAtt.setLength(length)
        offsetAtt.setOffset(correctOffset(start), run { finalOffset = correctOffset(end); finalOffset })
        return true
    }

    @Throws(IOException::class)
    override fun end() {
        super.end()
        // set final offset
        offsetAtt.setOffset(finalOffset, finalOffset)
    }

    @Throws(IOException::class)
    override fun reset() {
        super.reset()
        bufferIndex = 0
        offset = 0
        dataLen = 0
        finalOffset = 0
        ioBuffer.reset() // make sure to reset the IO buffer!!
    }
}
