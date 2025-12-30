package org.gnit.lucenekmp.analysis.cn.smart.hhmm

import org.gnit.lucenekmp.analysis.cn.smart.CharType
import org.gnit.lucenekmp.analysis.cn.smart.Utility
import org.gnit.lucenekmp.analysis.cn.smart.WordType
import org.gnit.lucenekmp.jdkport.Character

/**
 * Finds the optimal segmentation of a sentence into Chinese words
 *
 * @lucene.experimental
 */
class HHMMSegmenter {
    companion object {
        private val wordDict: WordDictionary = WordDictionary.getInstance()
    }

    private fun createSegGraph(sentence: String): SegGraph {
        var i = 0
        val length = sentence.length
        val charTypeArray = getCharTypes(sentence)
        val wordBuf = StringBuilder()
        var frequency: Int
        val segGraph = SegGraph()

        while (i < length) {
            var hasFullWidth = false
            when (charTypeArray[i]) {
                CharType.SPACE_LIKE -> {
                    i++
                }
                CharType.SURROGATE -> {
                    val state = Character.codePointAt(sentence, i)
                    val count = Character.charCount(state)
                    val charArray = CharArray(count)
                    for (k in 0 until count) {
                        charArray[k] = sentence[i + k]
                    }
                    val token = SegToken(charArray, i, i + count, WordType.CHINESE_WORD, 0)
                    segGraph.addToken(token)
                    i += count
                }
                CharType.HANZI -> {
                    var j = i + 1
                    wordBuf.setLength(0)
                    wordBuf.append(sentence[i])
                    var charArray = charArrayOf(sentence[i])
                    frequency = wordDict.getFrequency(charArray)
                    var token = SegToken(charArray, i, j, WordType.CHINESE_WORD, frequency)
                    segGraph.addToken(token)

                    var foundIndex = wordDict.getPrefixMatch(charArray)
                    while (j <= length && foundIndex != -1) {
                        if (wordDict.isEqual(charArray, foundIndex) && charArray.size > 1) {
                            frequency = wordDict.getFrequency(charArray)
                            token = SegToken(charArray, i, j, WordType.CHINESE_WORD, frequency)
                            segGraph.addToken(token)
                        }

                        while (j < length && charTypeArray[j] == CharType.SPACE_LIKE) {
                            j++
                        }

                        if (j < length && charTypeArray[j] == CharType.HANZI) {
                            wordBuf.append(sentence[j])
                            charArray = wordBuf.toString().toCharArray()
                            foundIndex = wordDict.getPrefixMatch(charArray, foundIndex)
                            j++
                        } else {
                            break
                        }
                    }
                    i++
                }
                CharType.FULLWIDTH_LETTER, CharType.LETTER -> {
                    var j = i + 1
                    if (charTypeArray[i] == CharType.FULLWIDTH_LETTER) {
                        hasFullWidth = true
                    }
                    while (j < length && (charTypeArray[j] == CharType.LETTER || charTypeArray[j] == CharType.FULLWIDTH_LETTER)) {
                        if (charTypeArray[j] == CharType.FULLWIDTH_LETTER) hasFullWidth = true
                        j++
                    }
                    val charArray = Utility.STRING_CHAR_ARRAY
                    frequency = wordDict.getFrequency(charArray)
                    val wordType = if (hasFullWidth) WordType.FULLWIDTH_STRING else WordType.STRING
                    val token = SegToken(charArray, i, j, wordType, frequency)
                    segGraph.addToken(token)
                    i = j
                }
                CharType.FULLWIDTH_DIGIT, CharType.DIGIT -> {
                    var j = i + 1
                    if (charTypeArray[i] == CharType.FULLWIDTH_DIGIT) {
                        hasFullWidth = true
                    }
                    while (j < length && (charTypeArray[j] == CharType.DIGIT || charTypeArray[j] == CharType.FULLWIDTH_DIGIT)) {
                        if (charTypeArray[j] == CharType.FULLWIDTH_DIGIT) hasFullWidth = true
                        j++
                    }
                    val charArray = Utility.NUMBER_CHAR_ARRAY
                    frequency = wordDict.getFrequency(charArray)
                    val wordType = if (hasFullWidth) WordType.FULLWIDTH_NUMBER else WordType.NUMBER
                    val token = SegToken(charArray, i, j, wordType, frequency)
                    segGraph.addToken(token)
                    i = j
                }
                CharType.DELIMITER -> {
                    val j = i + 1
                    frequency = Utility.MAX_FREQUENCE
                    val charArray = charArrayOf(sentence[i])
                    val token = SegToken(charArray, i, j, WordType.DELIMITER, frequency)
                    segGraph.addToken(token)
                    i = j
                }
                else -> {
                    val j = i + 1
                    val charArray = Utility.STRING_CHAR_ARRAY
                    frequency = wordDict.getFrequency(charArray)
                    val token = SegToken(charArray, i, j, WordType.STRING, frequency)
                    segGraph.addToken(token)
                    i = j
                }
            }
        }

        val startArray = Utility.START_CHAR_ARRAY
        frequency = wordDict.getFrequency(startArray)
        segGraph.addToken(SegToken(startArray, -1, 0, WordType.SENTENCE_BEGIN, frequency))

        val endArray = Utility.END_CHAR_ARRAY
        frequency = wordDict.getFrequency(endArray)
        segGraph.addToken(SegToken(endArray, length, length + 1, WordType.SENTENCE_END, frequency))

        return segGraph
    }

    private fun getCharTypes(sentence: String): IntArray {
        val length = sentence.length
        val charTypeArray = IntArray(length)
        for (i in 0 until length) {
            charTypeArray[i] = Utility.getCharType(sentence[i])
        }
        return charTypeArray
    }

    fun process(sentence: String): List<SegToken> {
        val segGraph = createSegGraph(sentence)
        val biSegGraph = BiSegGraph(segGraph)
        return biSegGraph.getShortPath()
    }
}
