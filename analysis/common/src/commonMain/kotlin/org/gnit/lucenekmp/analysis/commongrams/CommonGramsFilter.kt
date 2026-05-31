/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnit.lucenekmp.analysis.commongrams

import okio.IOException
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionLengthAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TypeAttribute
import org.gnit.lucenekmp.util.AttributeSource

/*
 * TODO: Consider implementing https://issues.apache.org/jira/browse/LUCENE-1688 changes to stop list and associated constructors
 */

/**
 * Construct bigrams for frequently occurring terms while indexing. Single terms are still indexed
 * too, with bigrams overlaid. This is achieved through the use of [PositionIncrementAttribute.setPositionIncrement].
 * Bigrams have a type of [GRAM_TYPE]
 * Example:
 *
 * <ul>
 *   <li>input:"the quick brown fox"
 *   <li>output:|"the","the-quick"|"brown"|"fox"|
 *   <li>"the-quick" has a position increment of 0 so it is in the same position as "the"
 *       "the-quick" has a term.type() of "gram"
 * </ul>
 */
class CommonGramsFilter(input: TokenStream, private val commonWords: CharArraySet?) : TokenFilter(input) {

    private val buffer = StringBuilder()

    private val termAttribute: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val offsetAttribute: OffsetAttribute = addAttribute(OffsetAttribute::class)
    private val typeAttribute: TypeAttribute = addAttribute(TypeAttribute::class)
    private val posIncAttribute: PositionIncrementAttribute =
        addAttribute(PositionIncrementAttribute::class)
    private val posLenAttribute: PositionLengthAttribute =
        addAttribute(PositionLengthAttribute::class)

    private var lastStartOffset = 0
    private var lastWasCommon = false
    private var savedState: AttributeSource.State? = null

    /**
     * Inserts bigrams for common words into a token stream. For each input token, output the token.
     * If the token and/or the following token are in the list of common words also output a bigram
     * with position increment 0 and type="gram"
     *
     * <p>TODO:Consider adding an option to not emit unigram stopwords as in CDL XTF BigramStopFilter,
     * CommonGramsQueryFilter would need to be changed to work with this.
     *
     * <p>TODO: Consider optimizing for the case of three commongrams i.e "man of the year" normally
     * produces 3 bigrams: "man-of", "of-the", "the-year" but with proper management of positions we
     * could eliminate the middle bigram "of-the"and save a disk seek and a whole set of position
     * lookups.
     */
    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        // get the next piece of input
        if (savedState != null) {
            restoreState(savedState)
            savedState = null
            saveTermBuffer()
            return true
        } else if (!input.incrementToken()) {
            return false
        }

        /* We build n-grams before and after stopwords.
         * When valid, the buffer always contains at least the separator.
         * If it's empty, there is nothing before this stopword.
         */
        if (lastWasCommon || (isCommon() && buffer.length > 0)) {
            savedState = captureState()
            gramToken()
            return true
        }

        saveTermBuffer()
        return true
    }

    @Throws(IOException::class)
    override fun reset() {
        super.reset()
        lastWasCommon = false
        savedState = null
        buffer.setLength(0)
    }

    /**
     * Determines if the current token is a common term
     *
     * @return `true` if the current token is a common term, `false` otherwise
     */
    private fun isCommon(): Boolean {
        return commonWords != null
                && commonWords.contains(termAttribute.buffer(), 0, termAttribute.length)
    }

    /** Saves this information to form the left part of a gram */
    private fun saveTermBuffer() {
        buffer.setLength(0)
        buffer.appendRange(termAttribute.buffer(), 0, termAttribute.length)
        buffer.append(SEPARATOR)
        lastStartOffset = offsetAttribute.startOffset()
        lastWasCommon = isCommon()
    }

    /** Constructs a compound token. */
    private fun gramToken() {
        buffer.appendRange(termAttribute.buffer(), 0, termAttribute.length)
        val endOffset = offsetAttribute.endOffset()

        clearAttributes()

        val length = buffer.length
        var termText = termAttribute.buffer()
        if (length > termText.size) {
            termText = termAttribute.resizeBuffer(length)
        }

        for (i in 0..<length) {
            termText[i] = buffer[i]
        }
        termAttribute.setLength(length)
        posIncAttribute.setPositionIncrement(0)
        posLenAttribute.positionLength = 2 // bigram
        offsetAttribute.setOffset(lastStartOffset, endOffset)
        typeAttribute.setType(GRAM_TYPE)
        buffer.setLength(0)
    }

    companion object {
        const val GRAM_TYPE: String = "gram"
        private const val SEPARATOR = '_'
    }
}
