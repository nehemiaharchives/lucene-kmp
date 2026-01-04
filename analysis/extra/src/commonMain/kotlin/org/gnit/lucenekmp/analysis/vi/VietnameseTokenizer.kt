package org.gnit.lucenekmp.analysis.vi

import okio.IOException
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TypeAttribute
import org.gnit.lucenekmp.jdkport.Reader

class VietnameseTokenizer(config: VietnameseConfig) : Tokenizer() {
    private val termAtt = addAttribute(CharTermAttribute::class)
    private val offsetAtt = addAttribute(OffsetAttribute::class)
    private val typeAtt = addAttribute(TypeAttribute::class)
    private val posIncrAtt = addAttribute(PositionIncrementAttribute::class)

    private val option: TokenizeOption = when {
        config.splitURL -> TokenizeOption.URL
        config.splitHost -> TokenizeOption.HOST
        else -> TokenizeOption.NORMAL
    }
    private val keepPunctuation: Boolean = config.keepPunctuation
    private var tokens: List<VietnameseToken> = emptyList()
    private var index: Int = 0
    private var offset = 0

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        clearAttributes()
        if (index >= tokens.size) return false
        val vietnameseToken = tokens[index]
        index += 1
        if (vietnameseToken != null) {
            posIncrAtt.setPositionIncrement(1)
            typeAtt.setType("<${vietnameseToken.getType()}>")
            val text = vietnameseToken.getText()
            termAtt.copyBuffer(text.toCharArray(), 0, text.length)
            offsetAtt.setOffset(correctOffset(vietnameseToken.getPos()), correctOffset(vietnameseToken.getEndPos()))
            offset = vietnameseToken.getEndPos()
            return true
        }
        return false
    }

    @Throws(IOException::class)
    override fun end() {
        super.end()
        val finalOffset = correctOffset(offset)
        offsetAtt.setOffset(finalOffset, finalOffset)
    }

    @Throws(IOException::class)
    override fun reset() {
        super.reset()
        tokens = tokenize(input)
        index = 0
        offset = 0
    }

    private fun tokenize(reader: Reader): List<VietnameseToken> {
        return tokenize(readAll(reader))
    }

    private fun tokenize(text: String): List<VietnameseToken> {
        return segment(text, option, keepPunctuation)
    }

    private fun readAll(reader: Reader): String {
        val sb = StringBuilder()
        val buffer = CharArray(2048)
        while (true) {
            val read = reader.read(buffer, 0, buffer.size)
            if (read == -1) break
            sb.appendRange(buffer, 0, read)
        }
        return sb.toString()
    }

    private fun segment(text: String, option: TokenizeOption, keepPunctuation: Boolean): List<VietnameseToken> {
        if (text.isEmpty()) return emptyList()

        val vietnameseTokens = mutableListOf<VietnameseToken>()
        var i = 0
        while (i < text.length) {
            if (startsWithHttpUrl(text, i)) {
                val end = findUrlEnd(text, i)
                vietnameseTokens.addAll(tokenizeUrl(text, i, end))
                i = end
                continue
            }

            val ch = text[i]
            if (ch.isLetterOrDigit()) {
                val start = i
                i += 1
                while (i < text.length && text[i].isLetterOrDigit()) {
                    i += 1
                }
                val term = text.substring(start, i).lowercase()
                vietnameseTokens.add(VietnameseToken(term, VietnameseToken.Type.WORD, start, i))
                continue
            }

            if (keepPunctuation && !ch.isWhitespace()) {
                vietnameseTokens.add(
                    VietnameseToken(
                        ch.toString(),
                        VietnameseToken.Type.PUNCT,
                        i,
                        i + 1
                    )
                )
            }
            i += 1
        }

        val merged = mergeWordBigrams(vietnameseTokens, text)
        return merged
    }

    private fun mergeWordBigrams(vietnameseTokens: List<VietnameseToken>, text: String): List<VietnameseToken> {
        if (vietnameseTokens.isEmpty()) return vietnameseTokens
        val merged = mutableListOf<VietnameseToken>()
        var i = 0
        while (i < vietnameseTokens.size) {
            val current = vietnameseTokens[i]
            if (current.getType() == VietnameseToken.Type.WORD && i + 1 < vietnameseTokens.size) {
                val next = vietnameseTokens[i + 1]
                if (next.getType() == VietnameseToken.Type.WORD && onlyWhitespaceBetween(text, current, next)) {
                    val combinedText = current.getText() + SPACE + next.getText()
                    merged.add(
                        VietnameseToken(
                            combinedText,
                            VietnameseToken.Type.WORD,
                            current.getPos(),
                            next.getEndPos()
                        )
                    )
                    i += 2
                    continue
                }
            }
            merged.add(current)
            i += 1
        }
        return merged
    }

    private fun onlyWhitespaceBetween(text: String, left: VietnameseToken, right: VietnameseToken): Boolean {
        val start = left.getEndPos()
        val end = right.getPos()
        if (start >= end) return false
        var i = start
        while (i < end) {
            if (!text[i].isWhitespace()) return false
            i += 1
        }
        return true
    }

    private fun tokenizeUrl(text: String, start: Int, end: Int): List<VietnameseToken> {
        val vietnameseTokens = mutableListOf<VietnameseToken>()
        var i = start
        while (i < end) {
            while (i < end && !text[i].isLetterOrDigit()) {
                i += 1
            }
            val runStart = i
            while (i < end && text[i].isLetterOrDigit()) {
                i += 1
            }
            if (runStart < i) {
                val term = text.substring(runStart, i).lowercase()
                vietnameseTokens.add(
                    VietnameseToken(
                        term,
                        VietnameseToken.Type.WHOLE_URL,
                        runStart,
                        i
                    )
                )
            }
        }
        return vietnameseTokens
    }

    private fun startsWithHttpUrl(text: String, index: Int): Boolean {
        return text.regionMatches(index, "http://", 0, 7, ignoreCase = true) ||
            text.regionMatches(index, "https://", 0, 8, ignoreCase = true)
    }

    private fun findUrlEnd(text: String, start: Int): Int {
        var i = start
        while (i < text.length && !text[i].isWhitespace()) {
            i += 1
        }
        return i
    }

    private enum class TokenizeOption {
        NORMAL,
        HOST,
        URL
    }

    private companion object {
        const val SPACE = " "
    }
}
