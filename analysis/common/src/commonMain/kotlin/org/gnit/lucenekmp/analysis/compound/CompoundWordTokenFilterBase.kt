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
package org.gnit.lucenekmp.analysis.compound

import okio.IOException
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.util.AttributeSource

/** Base class for decomposition token filters. */
abstract class CompoundWordTokenFilterBase : TokenFilter {
    protected val dictionary: CharArraySet?
    protected val tokens: ArrayDeque<CompoundToken>
    protected val minWordSize: Int
    protected val minSubwordSize: Int
    protected val maxSubwordSize: Int
    protected val onlyLongestMatch: Boolean

    protected val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    protected val offsetAtt: OffsetAttribute = addAttribute(OffsetAttribute::class)
    private val posIncAtt: PositionIncrementAttribute =
        addAttribute(PositionIncrementAttribute::class)

    private var current: AttributeSource.State? = null

    protected constructor(
        input: TokenStream, dictionary: CharArraySet?, onlyLongestMatch: Boolean
    ) : this(
        input,
        dictionary,
        DEFAULT_MIN_WORD_SIZE,
        DEFAULT_MIN_SUBWORD_SIZE,
        DEFAULT_MAX_SUBWORD_SIZE,
        onlyLongestMatch
    )

    protected constructor(input: TokenStream, dictionary: CharArraySet?) : this(
        input,
        dictionary,
        DEFAULT_MIN_WORD_SIZE,
        DEFAULT_MIN_SUBWORD_SIZE,
        DEFAULT_MAX_SUBWORD_SIZE,
        false
    )

    protected constructor(
        input: TokenStream,
        dictionary: CharArraySet?,
        minWordSize: Int,
        minSubwordSize: Int,
        maxSubwordSize: Int,
        onlyLongestMatch: Boolean
    ) : super(input) {
        this.tokens = ArrayDeque()
        if (minWordSize < 0) {
            throw IllegalArgumentException("minWordSize cannot be negative")
        }
        this.minWordSize = minWordSize
        if (minSubwordSize < 0) {
            throw IllegalArgumentException("minSubwordSize cannot be negative")
        }
        this.minSubwordSize = minSubwordSize
        if (maxSubwordSize < 0) {
            throw IllegalArgumentException("maxSubwordSize cannot be negative")
        }
        this.maxSubwordSize = maxSubwordSize
        this.onlyLongestMatch = onlyLongestMatch
        this.dictionary = dictionary
    }

    @Throws(IOException::class)
    final override fun incrementToken(): Boolean {
        if (!tokens.isEmpty()) {
            check(current != null)
            val token = tokens.removeFirst()
            restoreState(current) // keep all other attributes untouched
            termAtt.setEmpty()!!.append(token.txt)
            offsetAtt.setOffset(token.startOffset, token.endOffset)
            posIncAtt.setPositionIncrement(0)
            return true
        }

        current = null // not really needed, but for safety
        return if (input.incrementToken()) {
            // Only words longer than minWordSize get processed
            if (termAtt.length >= this.minWordSize) {
                decompose()
                // only capture the state if we really need it for producing new tokens
                if (!tokens.isEmpty()) {
                    current = captureState()
                }
            }
            // return original token:
            true
        } else {
            false
        }
    }

    /**
     * Decomposes the current [termAtt] and places [CompoundToken] instances in the
     * [tokens] list. The original token may not be placed in the list, as it is automatically
     * passed through this filter.
     */
    protected abstract fun decompose()

    @Throws(IOException::class)
    override fun reset() {
        super.reset()
        tokens.clear()
        current = null
    }

    /** Helper class to hold decompounded token information */
    protected inner class CompoundToken {
        val txt: CharSequence
        val startOffset: Int
        val endOffset: Int

        /**
         * Construct the compound token based on a slice of the current
         * [CompoundWordTokenFilterBase.termAtt].
         */
        constructor(offset: Int, length: Int) {
            this.txt = this@CompoundWordTokenFilterBase.termAtt.subSequence(offset, offset + length)

            // offsets of the original word
            this.startOffset = this@CompoundWordTokenFilterBase.offsetAtt.startOffset()
            this.endOffset = this@CompoundWordTokenFilterBase.offsetAtt.endOffset()
        }
    }

    companion object {
        /** The default for minimal word length that gets decomposed */
        const val DEFAULT_MIN_WORD_SIZE: Int = 5

        /** The default for minimal length of subwords that get propagated to the output of this filter */
        const val DEFAULT_MIN_SUBWORD_SIZE: Int = 2

        /** The default for maximal length of subwords that get propagated to the output of this filter */
        const val DEFAULT_MAX_SUBWORD_SIZE: Int = 15
    }
}
