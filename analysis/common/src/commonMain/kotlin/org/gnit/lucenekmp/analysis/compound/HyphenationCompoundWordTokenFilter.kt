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
import kotlin.math.max
import kotlin.math.min
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.compound.hyphenation.HyphenationTree
import org.gnit.lucenekmp.jdkport.InputSource

/**
 * A [org.gnit.lucenekmp.analysis.TokenFilter] that decomposes compound words found in many
 * Germanic languages.
 *
 * <p>"Donaudampfschiff" becomes Donau, dampf, schiff so that you can find "Donaudampfschiff" even
 * when you only enter "schiff". It uses a hyphenation grammar and a word dictionary to achieve
 * this.
 */
class HyphenationCompoundWordTokenFilter : CompoundWordTokenFilterBase {
    private val hyphenator: HyphenationTree
    private val noSubMatches: Boolean
    private val noOverlappingMatches: Boolean
    private val calcSubMatches: Boolean

    /**
     * Creates a new [HyphenationCompoundWordTokenFilter] instance.
     *
     * @param input the [org.gnit.lucenekmp.analysis.TokenStream] to process
     * @param hyphenator the hyphenation pattern tree to use for hyphenation
     * @param dictionary the word dictionary to match against.
     */
    constructor(input: TokenStream, hyphenator: HyphenationTree, dictionary: CharArraySet?) : this(
        input,
        hyphenator,
        dictionary,
        DEFAULT_MIN_WORD_SIZE,
        DEFAULT_MIN_SUBWORD_SIZE,
        DEFAULT_MAX_SUBWORD_SIZE,
        false,
        false,
        false
    )

    /**
     * Creates a new [HyphenationCompoundWordTokenFilter] instance.
     *
     * @param input the [org.gnit.lucenekmp.analysis.TokenStream] to process
     * @param hyphenator the hyphenation pattern tree to use for hyphenation
     * @param dictionary the word dictionary to match against.
     * @param minWordSize only words longer than this get processed
     * @param minSubwordSize only subwords longer than this get to the output stream
     * @param maxSubwordSize only subwords shorter than this get to the output stream
     * @param onlyLongestMatch Add only the longest matching subword to the stream
     */
    constructor(
        input: TokenStream,
        hyphenator: HyphenationTree,
        dictionary: CharArraySet?,
        minWordSize: Int,
        minSubwordSize: Int,
        maxSubwordSize: Int,
        onlyLongestMatch: Boolean
    ) : this(
        input,
        hyphenator,
        dictionary,
        minWordSize,
        minSubwordSize,
        maxSubwordSize,
        onlyLongestMatch,
        false,
        false
    )

    /**
     * Creates a new [HyphenationCompoundWordTokenFilter] instance.
     *
     * @param input the [org.gnit.lucenekmp.analysis.TokenStream] to process
     * @param hyphenator the hyphenation pattern tree to use for hyphenation
     * @param dictionary the word dictionary to match against.
     * @param minWordSize only words longer than this get processed
     * @param minSubwordSize only subwords longer than this get to the output stream
     * @param maxSubwordSize only subwords shorter than this get to the output stream
     * @param onlyLongestMatch Add only the longest matching subword to the stream
     * @param noSubMatches Excludes subwords that are enclosed by an other token
     * @param noOverlappingMatches Excludes subwords that overlap with an other subword
     */
    constructor(
        input: TokenStream,
        hyphenator: HyphenationTree,
        dictionary: CharArraySet?,
        minWordSize: Int,
        minSubwordSize: Int,
        maxSubwordSize: Int,
        onlyLongestMatch: Boolean,
        noSubMatches: Boolean,
        noOverlappingMatches: Boolean
    ) : super(input, dictionary, minWordSize, minSubwordSize, maxSubwordSize, onlyLongestMatch) {
        this.hyphenator = requireNotNull(hyphenator) { "hyphenator" }
        this.noSubMatches = noSubMatches
        this.noOverlappingMatches = noOverlappingMatches
        this.calcSubMatches = !onlyLongestMatch && !noSubMatches && !noOverlappingMatches
    }

    /**
     * Create a HyphenationCompoundWordTokenFilter with no dictionary.
     */
    constructor(
        input: TokenStream,
        hyphenator: HyphenationTree,
        minWordSize: Int,
        minSubwordSize: Int,
        maxSubwordSize: Int
    ) : this(input, hyphenator, null, minWordSize, minSubwordSize, maxSubwordSize, false)

    /**
     * Create a HyphenationCompoundWordTokenFilter with no dictionary.
     */
    constructor(input: TokenStream, hyphenator: HyphenationTree) : this(
        input,
        hyphenator,
        DEFAULT_MIN_WORD_SIZE,
        DEFAULT_MIN_SUBWORD_SIZE,
        DEFAULT_MAX_SUBWORD_SIZE
    )

    override fun decompose() {
        // if the token is in the dictionary and we are not interested in subMatches
        // we can skip decomposing this token (see testNoSubAndTokenInDictionary unit test)
        // NOTE:
        // we check against token and the token that is one character
        // shorter to avoid problems with genitive 's characters and other binding characters
        if (dictionary != null
            && !this.calcSubMatches
            && (dictionary.contains(termAtt.buffer(), 0, termAtt.length)
                    || termAtt.length > 1
                    && dictionary.contains(termAtt.buffer(), 0, termAtt.length - 1))
        ) {
            return // the whole token is in the dictionary - do not decompose
        }

        // get the hyphenation points
        val hyphens = hyphenator.hyphenate(termAtt.buffer(), 0, termAtt.length, 1, 1)
        // No hyphen points found -> exit
        if (hyphens == null) {
            return
        }
        val maxSubwordSize = min(this.maxSubwordSize, termAtt.length - 1)

        var consumed = -1 // hyp of the longest token added (for noSub)

        val hyp = hyphens.getHyphenationPoints()
        var lastTokenStart = -1
        var lastTokenLength = -1

        var i = 0
        while (i < hyp.size) {
            if (noOverlappingMatches) { // if we do not want overlapping subwords
                i = max(i, consumed) // skip over consumed hyp
            }
            val start = hyp[i]
            val until = if (noSubMatches) max(consumed, i) else i
            var j = hyp.size - 1
            while (j > until) {
                val partLength = hyp[j] - start

                // if the part is longer than maxSubwordSize we
                // are done with this round
                if (partLength > maxSubwordSize) {
                    j--
                    continue
                }

                // we only put subwords to the token stream
                // that are longer than minPartSize
                if (partLength < this.minSubwordSize) {
                    // BOGUS/BROKEN/FUNKY/WACKO: somehow we have negative 'parts' according to the
                    // calculation above, and we rely upon minSubwordSize being >=0 to filter them out...
                    break
                }

                // check the dictionary
                if (dictionary == null || dictionary.contains(termAtt.buffer(), start, partLength)) {
                    if (start != lastTokenStart || partLength != lastTokenLength) {
                        tokens.add(CompoundToken(start, partLength))
                        lastTokenStart = start
                        lastTokenLength = partLength
                    }
                    consumed = j // mark the current hyp as consumed
                    if (!calcSubMatches) {
                        break // do not search for shorter matches
                    }
                } else if (dictionary.contains(termAtt.buffer(), start, partLength - 1)) {
                    // check the dictionary again with a word that is one character
                    // shorter to avoid problems with genitive 's characters and
                    // other binding characters
                    if (start != lastTokenStart || partLength - 1 != lastTokenLength) {
                        tokens.add(CompoundToken(start, partLength - 1))
                        lastTokenStart = start
                        lastTokenLength = partLength - 1
                    }
                    consumed = j // mark the current hyp as consumed
                    if (!calcSubMatches) {
                        break // do not search for shorter matches
                    }
                } // else dictionary is present but does not contain the part
                j--
            }
            i++
        }
    }

    companion object {
        /**
         * Create a hyphenator tree
         *
         * @param hyphenationFilename the filename of the XML grammar to load
         * @return An object representing the hyphenation patterns
         * @throws okio.IOException If there is a low-level I/O error.
         */
        @Throws(IOException::class)
        fun getHyphenationTree(hyphenationFilename: String): HyphenationTree {
            return getHyphenationTree(InputSource(hyphenationFilename))
        }

        /**
         * Create a hyphenator tree
         *
         * @param hyphenationSource the InputSource pointing to the XML grammar
         * @return An object representing the hyphenation patterns
         * @throws okio.IOException If there is a low-level I/O error.
         */
        @Throws(IOException::class)
        fun getHyphenationTree(hyphenationSource: InputSource): HyphenationTree {
            val tree = HyphenationTree()
            tree.loadPatterns(hyphenationSource)
            return tree
        }
    }
}
