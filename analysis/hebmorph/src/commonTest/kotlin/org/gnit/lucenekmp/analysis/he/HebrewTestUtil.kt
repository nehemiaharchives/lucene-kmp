package org.gnit.lucenekmp.analysis.he

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionLengthAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TypeAttribute
import org.gnit.lucenekmp.jdkport.StringReader
import kotlin.test.assertEquals

internal object HebrewTestUtil {
    val dictionary: DictHebMorph by lazy { HSpellDictionaryLoader().loadDictionaryFromDefaultPath() }

    data class TokenData(val term: String, val startOffset: Int, val endOffset: Int, val positionIncrement: Int)
    data class FullTokenData(
        val term: String,
        val startOffset: Int,
        val endOffset: Int,
        val positionIncrement: Int,
        val positionLength: Int,
        val tokenType: String,
    )

    fun tokens(analyzer: Analyzer, text: String): List<String> {
        val result = ArrayList<String>()
        val stream = analyzer.tokenStream("foo", StringReader(text))
        stream.use {
            val termAtt = it.addAttribute(CharTermAttribute::class)
            it.reset()
            while (it.incrementToken()) {
                result.add(termAtt.toString())
            }
            it.end()
        }
        return result
    }

    fun assertAnalyzesTo(analyzer: Analyzer, text: String, expected: Array<String>) {
        assertEquals(expected.toList(), tokens(analyzer, text))
    }

    fun tokenData(analyzer: Analyzer, text: String): List<TokenData> {
        val result = ArrayList<TokenData>()
        val stream = analyzer.tokenStream("foo", StringReader(text))
        stream.use {
            val termAtt = it.addAttribute(CharTermAttribute::class)
            val offsetAtt = it.addAttribute(OffsetAttribute::class)
            val posIncAtt = it.addAttribute(PositionIncrementAttribute::class)
            it.reset()
            while (it.incrementToken()) {
                result.add(TokenData(termAtt.toString(), offsetAtt.startOffset(), offsetAtt.endOffset(), posIncAtt.getPositionIncrement()))
            }
            it.end()
        }
        return result
    }

    fun fullTokenData(analyzer: Analyzer, text: String): List<FullTokenData> {
        val result = ArrayList<FullTokenData>()
        val stream = analyzer.tokenStream("foo", StringReader(text))
        stream.use {
            val termAtt = it.addAttribute(CharTermAttribute::class)
            val offsetAtt = it.addAttribute(OffsetAttribute::class)
            val posIncAtt = it.addAttribute(PositionIncrementAttribute::class)
            val posLenAtt = it.addAttribute(PositionLengthAttribute::class)
            val typeAtt = it.addAttribute(TypeAttribute::class)
            it.reset()
            while (it.incrementToken()) {
                result.add(
                    FullTokenData(
                        termAtt.toString(),
                        offsetAtt.startOffset(),
                        offsetAtt.endOffset(),
                        posIncAtt.getPositionIncrement(),
                        posLenAtt.positionLength,
                        typeAtt.type(),
                    ),
                )
            }
            it.end()
        }
        return result
    }

    fun assertAnalyzesTo(analyzer: Analyzer, text: String, expected: Array<String>, startOffsets: IntArray, endOffsets: IntArray) {
        val actual = tokenData(analyzer, text)
        assertEquals(expected.toList(), actual.map { it.term })
        assertEquals(startOffsets.toList(), actual.map { it.startOffset })
        assertEquals(endOffsets.toList(), actual.map { it.endOffset })
    }

    fun assertAnalyzesTo(analyzer: Analyzer, text: String, expected: Array<String>, startOffsets: IntArray, endOffsets: IntArray, positionIncrements: IntArray) {
        val actual = tokenData(analyzer, text)
        assertEquals(expected.toList(), actual.map { it.term })
        assertEquals(startOffsets.toList(), actual.map { it.startOffset })
        assertEquals(endOffsets.toList(), actual.map { it.endOffset })
        assertEquals(positionIncrements.toList(), actual.map { it.positionIncrement })
    }

    fun checkOneTerm(analyzer: Analyzer, input: String, expected: String) {
        assertEquals(listOf(expected), tokens(analyzer, input))
    }
}
