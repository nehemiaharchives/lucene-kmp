package org.gnit.lucenekmp.tests.analysis

import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.util.AttributeFactory
import org.gnit.lucenekmp.util.automaton.CharacterRunAutomaton
import org.gnit.lucenekmp.util.automaton.Operations
import org.gnit.lucenekmp.util.automaton.RegExp

// TODO this needs to be implemented fully to copy all the functions of Java Lucene's MockTokenizer
/**
 * Simplified tokenizer used for testing.
 * This is a light-weight port of Lucene's MockTokenizer supporting only basic features.
 */
class MockTokenizer(
    private val runAutomaton: CharacterRunAutomaton = WHITESPACE,
    private val lowerCase: Boolean = true,
    private val maxTokenLength: Int = DEFAULT_MAX_TOKEN_LENGTH
) : Tokenizer() {

    private val termAtt = addAttribute(CharTermAttribute::class)
    private val offsetAtt = addAttribute(OffsetAttribute::class)
    var enableChecks = true

    private var tokens: List<String> = emptyList()
    private var starts: IntArray = intArrayOf()
    private var index = 0

    constructor(factory: AttributeFactory, runAutomaton: CharacterRunAutomaton, lowerCase: Boolean) :
            this(runAutomaton, lowerCase) {
        setReaderTestPoint()
    }

    override fun reset() {
        super.reset()
        val buffer = CharArray(1024)
        val textBuilder = StringBuilder()
        while (true) {
            val cnt = input.read(buffer, 0, buffer.size)
            if (cnt == -1) break
            textBuilder.appendRange(buffer, 0, cnt)
        }
        val text = textBuilder.toString()
        val resultTokens = mutableListOf<String>()
        val startList = mutableListOf<Int>()
        var i = 0
        while (i < text.length) {
            if (!text[i].isWhitespace()) {
                val start = i
                val sb = StringBuilder()
                while (i < text.length && !text[i].isWhitespace() && sb.length < maxTokenLength) {
                    sb.append(if (lowerCase) text[i].lowercaseChar() else text[i])
                    i++
                }
                resultTokens.add(sb.toString())
                startList.add(start)
                while (i < text.length && !text[i].isWhitespace()) i++
            } else {
                i++
            }
        }
        tokens = resultTokens
        starts = startList.toIntArray()
        index = 0
    }

    override fun incrementToken(): Boolean {
        clearAttributes()
        if (index >= tokens.size) return false
        val token = tokens[index]
        termAtt.setEmpty()
        termAtt.append(token)
        val start = starts[index]
        val end = start + token.length
        offsetAtt.setOffset(correctOffset(start), correctOffset(end))
        index++
        return true
    }

    companion object {
        val WHITESPACE = CharacterRunAutomaton(
            Operations.determinize(RegExp("[^ \t\r\n]+").toAutomaton(), Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
        )
        val KEYWORD = CharacterRunAutomaton(
            Operations.determinize(RegExp(".*").toAutomaton(), Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
        )
        val SIMPLE = CharacterRunAutomaton(
            Operations.determinize(RegExp("[A-Za-zªµºÀ-ÖØ-öø-ˁ一-鿌]+").toAutomaton(), Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
        )
        const val DEFAULT_MAX_TOKEN_LENGTH = 255
    }
}

