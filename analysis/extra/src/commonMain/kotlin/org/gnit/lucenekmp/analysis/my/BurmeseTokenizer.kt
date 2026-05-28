package org.gnit.lucenekmp.analysis.my

import okio.IOException
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TypeAttribute
import org.gnit.lucenekmp.jdkport.Reader

/**
 * Tokenizer for Burmese text.
 *
 * Burmese text commonly omits spaces between words. Without a dictionary segmenter, this tokenizer
 * uses Myanmar-script syllable clusters and explicitly separates common grammatical particles.
 */
class BurmeseTokenizer : Tokenizer() {
    private val termAtt = addAttribute(CharTermAttribute::class)
    private val offsetAtt = addAttribute(OffsetAttribute::class)
    private val typeAtt = addAttribute(TypeAttribute::class)
    private val posIncrAtt = addAttribute(PositionIncrementAttribute::class)

    private var tokens: List<BurmeseToken> = emptyList()
    private var index: Int = 0
    private var offset: Int = 0
    private var finalOffset: Int = 0

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        clearAttributes()
        if (index >= tokens.size) return false

        val token = tokens[index]
        index += 1
        posIncrAtt.setPositionIncrement(1)
        typeAtt.setType("<${token.type.name.lowercase()}>")
        termAtt.copyBuffer(token.text.toCharArray(), 0, token.text.length)
        offsetAtt.setOffset(correctOffset(token.startOffset), correctOffset(token.endOffset))
        offset = token.endOffset
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
        offset = 0
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

    internal fun tokenize(text: String): List<BurmeseToken> {
        if (text.isEmpty()) return emptyList()
        val result = mutableListOf<BurmeseToken>()
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                isMyanmarDigit(ch) -> {
                    val start = i
                    i += 1
                    while (i < text.length && isMyanmarDigit(text[i])) {
                        i += 1
                    }
                    result.add(BurmeseToken(text.substring(start, i), BurmeseToken.Type.ALPHANUM, start, i))
                }
                isMyanmarChar(ch) -> {
                    val start = i
                    i += 1
                    while (i < text.length && isMyanmarChar(text[i])) {
                        i += 1
                    }
                    segmentMyanmarRun(text, start, i, result)
                }
                ch.isLetterOrDigit() -> {
                    val start = i
                    i += 1
                    while (i < text.length && text[i].isLetterOrDigit()) {
                        i += 1
                    }
                    result.add(BurmeseToken(text.substring(start, i).lowercase(), BurmeseToken.Type.ALPHANUM, start, i))
                }
                else -> i += 1
            }
        }
        return result
    }

    private fun segmentMyanmarRun(text: String, start: Int, end: Int, result: MutableList<BurmeseToken>) {
        var i = start
        while (i < end) {
            val particleEnd = longestParticleEnd(text, i, end)
            if (particleEnd > i) {
                result.add(BurmeseToken(text.substring(i, particleEnd), BurmeseToken.Type.MYANMAR, i, particleEnd))
                i = particleEnd
                continue
            }

            val tokenEnd = nextSyllableEnd(text, i, end)
            result.add(BurmeseToken(text.substring(i, tokenEnd), BurmeseToken.Type.MYANMAR, i, tokenEnd))
            i = tokenEnd
        }
    }

    private fun longestParticleEnd(text: String, start: Int, end: Int): Int {
        for (particle in PARTICLES) {
            val particleEnd = start + particle.length
            if (particleEnd <= end && text.regionMatches(start, particle, 0, particle.length)) {
                return particleEnd
            }
        }
        return start
    }

    private fun nextSyllableEnd(text: String, start: Int, end: Int): Int {
        var i = start + 1
        while (i < end) {
            val current = text[i]
            if (isBaseLetter(current) && shouldBreakBeforeBase(text, i, start)) {
                break
            }
            i += 1
        }
        return i
    }

    private fun shouldBreakBeforeBase(text: String, index: Int, start: Int): Boolean {
        if (index <= start) return false
        val previous = text[index - 1]
        if (previous == VIRAMA) return false
        if (index + 1 < text.length && text[index + 1] == ASAT) return false
        return true
    }

    companion object {
        private const val ASAT = '\u103A'
        private const val VIRAMA = '\u1039'

        private val PARTICLES: Array<String> = arrayOf(
            "ကတည်းက",
            "အတွက်",
            "ကြောင့်",
            "တွင်",
            "တွေ",
            "များ",
            "တို့",
            "သည်",
            "တဲ့",
            "သော",
            "နှင့်",
            "နဲ့",
            "မှာ",
            "မှ",
            "ကို",
            "က",
            "၏",
            "ပါ",
            "လည်း",
            "ပဲ",
            "သာ",
            "တော့"
        ).sortedByDescending { it.length }.toTypedArray()

        internal fun isMyanmarChar(ch: Char): Boolean {
            return ch in '\u1000'..'\u109F' || ch in '\uAA60'..'\uAA7F' || ch in '\uA9E0'..'\uA9FF'
        }

        internal fun isMyanmarDigit(ch: Char): Boolean {
            return ch in '\u1040'..'\u1049' || ch in '\u1090'..'\u1099' || ch in '\uA9F0'..'\uA9F9'
        }

        private fun isBaseLetter(ch: Char): Boolean {
            return ch in '\u1000'..'\u102A' ||
                ch == '\u103F' ||
                ch in '\u1050'..'\u1055' ||
                ch in '\u105A'..'\u105D' ||
                ch == '\u1061' ||
                ch in '\u1065'..'\u1066' ||
                ch in '\u106E'..'\u1070' ||
                ch in '\u1075'..'\u1081' ||
                ch == '\u108E' ||
                ch in '\uAA60'..'\uAA6F' ||
                ch in '\uA9E0'..'\uA9E4'
        }
    }
}
