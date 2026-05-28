package org.gnit.lucenekmp.analysis.uz

import okio.IOException
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.jdkport.Reader

/**
 * Tokenizer for Uzbek.
 *
 * Uzbek Latin orthography uses apostrophe-like marks in letters such as o' and g', so this tokenizer
 * keeps apostrophe variants inside adjacent letter runs.
 */
class UzbekTokenizer : Tokenizer() {
    private val termAtt = addAttribute(CharTermAttribute::class)
    private val offsetAtt = addAttribute(OffsetAttribute::class)
    private val posIncrAtt = addAttribute(PositionIncrementAttribute::class)

    private var tokens: List<UzbekToken> = emptyList()
    private var index: Int = 0
    private var finalOffset: Int = 0

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        clearAttributes()
        if (index >= tokens.size) return false

        val token = tokens[index]
        index += 1
        posIncrAtt.setPositionIncrement(1)
        termAtt.copyBuffer(token.text.toCharArray(), 0, token.text.length)
        offsetAtt.setOffset(correctOffset(token.startOffset), correctOffset(token.endOffset))
        return true
    }

    @Throws(IOException::class)
    override fun end() {
        super.end()
        val correctedFinalOffset = correctOffset(finalOffset)
        offsetAtt.setOffset(correctedFinalOffset, correctedFinalOffset)
    }

    @Throws(IOException::class)
    override fun reset() {
        super.reset()
        val text = readAll(input)
        tokens = tokenize(text)
        index = 0
        finalOffset = text.length
    }

    private fun readAll(reader: Reader): String {
        val builder = StringBuilder()
        val buffer = CharArray(2048)
        while (true) {
            val read = reader.read(buffer, 0, buffer.size)
            if (read == -1) break
            builder.appendRange(buffer, 0, read)
        }
        return builder.toString()
    }

    internal fun tokenize(text: String): List<UzbekToken> {
        if (text.isEmpty()) return emptyList()
        val result = mutableListOf<UzbekToken>()
        var i = 0
        while (i < text.length) {
            if (text[i].isLetterOrDigit()) {
                val start = i
                i += 1
                while (i < text.length && isTokenPart(text, i)) {
                    i += 1
                }
                result.add(UzbekToken(text.substring(start, i), start, i))
            } else {
                i += 1
            }
        }
        return result
    }

    private fun isTokenPart(text: String, index: Int): Boolean {
        val ch = text[index]
        if (ch.isLetterOrDigit()) return true
        if (!UzbekNormalizer.isApostropheVariant(ch)) return false
        return index > 0 && index + 1 < text.length && text[index - 1].isLetter() && text[index + 1].isLetter()
    }
}
