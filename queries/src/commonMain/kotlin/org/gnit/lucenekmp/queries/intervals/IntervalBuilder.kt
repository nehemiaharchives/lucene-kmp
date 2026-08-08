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

/*
 * Code adopted from ASL-licensed Elasticsearch.
 * https://github.com/elastic/elasticsearch/blob/7.10/server/src/main/java/org/elasticsearch/index/query/IntervalBuilder.java
 *
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.gnit.lucenekmp.queries.intervals

import okio.IOException
import org.gnit.lucenekmp.analysis.CachingTokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionLengthAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TermToBytesRefAttribute
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.graph.GraphTokenStreamFiniteStrings

/**
 * Constructs an [IntervalsSource] based on analyzed text.
 *
 * <p>Code adopted from ASL-licensed [Elasticsearch](https://github.com/elastic/elasticsearch).
 *
 * @see
 *     "https://github.com/elastic/elasticsearch/blob/7.10/server/src/main/java/org/elasticsearch/index/query/IntervalBuilder.java"
 */
internal object IntervalBuilder {

    private val NO_INTERVALS: IntervalsSource =
        Intervals.noIntervals("No terms in analyzed text")

    @Throws(IOException::class)
    fun analyzeText(stream: CachingTokenFilter, maxGaps: Int, ordered: Boolean): IntervalsSource {
        assert(stream != null)

        val termAtt: TermToBytesRefAttribute? = stream.getAttribute(TermToBytesRefAttribute::class)
        val posIncAtt: PositionIncrementAttribute = stream.addAttribute(PositionIncrementAttribute::class)
        val posLenAtt: PositionLengthAttribute = stream.addAttribute(PositionLengthAttribute::class)

        if (termAtt == null) {
            return NO_INTERVALS
        }

        // phase 1: read through the stream and assess the situation:
        // counting the number of tokens/positions and marking if we have any synonyms.

        var numTokens = 0
        var hasSynonyms = false
        var isGraph = false

        stream.reset()
        while (stream.incrementToken()) {
            numTokens++
            val positionIncrement: Int = posIncAtt.getPositionIncrement()
            if (positionIncrement == 0) {
                hasSynonyms = true
            }
            val positionLength: Int = posLenAtt.positionLength
            if (positionLength > 1) {
                isGraph = true
            }
        }

        // phase 2: based on token count, presence of synonyms, and options
        // formulate a single term, boolean, or phrase.

        if (numTokens == 0) {
            return NO_INTERVALS
        } else if (numTokens == 1) {
            // single term
            return analyzeTerm(stream)
        } else if (isGraph) {
            // graph
            return combineSources(analyzeGraph(stream), maxGaps, ordered)
        } else {
            // phrase
            if (hasSynonyms) {
                // phrase with single-term synonyms
                return analyzeSynonyms(stream, maxGaps, ordered)
            } else {
                // simple phrase
                return combineSources(analyzeTerms(stream), maxGaps, ordered)
            }
        }
    }

    @Throws(IOException::class)
    private fun analyzeTerm(ts: TokenStream): IntervalsSource {
        val bytesAtt: TermToBytesRefAttribute = ts.addAttribute(TermToBytesRefAttribute::class)
        ts.reset()
        ts.incrementToken()
        return Intervals.term(BytesRef.deepCopyOf(bytesAtt.bytesRef))
    }

    private fun combineSources(
        sources: List<IntervalsSource>,
        maxGaps: Int,
        ordered: Boolean
    ): IntervalsSource {
        if (sources.isEmpty()) {
            return NO_INTERVALS
        }
        if (sources.size == 1) {
            return sources[0]
        }
        val sourcesArray: Array<IntervalsSource> = sources.toTypedArray()
        if (maxGaps == 0 && ordered) {
            return Intervals.phrase(*sourcesArray)
        }
        val inner: IntervalsSource =
            if (ordered) Intervals.ordered(*sourcesArray) else Intervals.unordered(*sourcesArray)
        if (maxGaps == -1) {
            return inner
        }
        return Intervals.maxgaps(maxGaps, inner)
    }

    @Throws(IOException::class)
    private fun analyzeTerms(ts: TokenStream): MutableList<IntervalsSource> {
        val terms: MutableList<IntervalsSource> = mutableListOf()
        val bytesAtt: TermToBytesRefAttribute = ts.addAttribute(TermToBytesRefAttribute::class)
        val posAtt: PositionIncrementAttribute = ts.addAttribute(PositionIncrementAttribute::class)
        ts.reset()
        while (ts.incrementToken()) {
            val term: BytesRef = bytesAtt.bytesRef
            val precedingSpaces: Int = posAtt.getPositionIncrement() - 1
            terms.add(extend(Intervals.term(BytesRef.deepCopyOf(term)), precedingSpaces))
        }
        ts.end()
        return terms
    }

    private fun extend(source: IntervalsSource, precedingSpaces: Int): IntervalsSource {
        if (precedingSpaces == 0) {
            return source
        }
        return Intervals.extend(source, precedingSpaces, 0)
    }

    @Throws(IOException::class)
    private fun analyzeSynonyms(ts: TokenStream, maxGaps: Int, ordered: Boolean): IntervalsSource {
        val terms: MutableList<IntervalsSource> = mutableListOf()
        val synonyms: MutableList<IntervalsSource> = mutableListOf()
        val bytesAtt: TermToBytesRefAttribute = ts.addAttribute(TermToBytesRefAttribute::class)
        val posAtt: PositionIncrementAttribute = ts.addAttribute(PositionIncrementAttribute::class)
        ts.reset()
        var spaces = 0
        while (ts.incrementToken()) {
            val posInc: Int = posAtt.getPositionIncrement()
            if (posInc > 0) {
                if (synonyms.size == 1) {
                    terms.add(extend(synonyms[0], spaces))
                } else if (synonyms.size > 1) {
                    terms.add(extend(Intervals.or(*synonyms.toTypedArray()), spaces))
                }
                synonyms.clear()
                spaces = posInc - 1
            }
            synonyms.add(Intervals.term(BytesRef.deepCopyOf(bytesAtt.bytesRef)))
        }
        if (synonyms.size == 1) {
            terms.add(extend(synonyms[0], spaces))
        } else {
            terms.add(extend(Intervals.or(*synonyms.toTypedArray()), spaces))
        }
        return combineSources(terms, maxGaps, ordered)
    }

    @Throws(IOException::class)
    private fun analyzeGraph(source: TokenStream): MutableList<IntervalsSource> {
        source.reset()
        val graph = GraphTokenStreamFiniteStrings(source)
        val clauses: MutableList<IntervalsSource> = mutableListOf()
        val articulationPoints: IntArray = graph.articulationPoints()
        var lastState = 0
        val maxClauseCount: Int = IndexSearcher.maxClauseCount
        for (i in 0..articulationPoints.size) {
            val start = lastState
            var end = -1
            if (i < articulationPoints.size) {
                end = articulationPoints[i]
            }
            lastState = end
            if (graph.hasSidePath(start)) {
                val paths: MutableList<IntervalsSource> = mutableListOf()
                val it: MutableIterator<TokenStream> = graph.getFiniteStrings(start, end)
                while (it.hasNext()) {
                    val ts: TokenStream = it.next()
                    val phrase: IntervalsSource = combineSources(analyzeTerms(ts), 0, true)
                    if (paths.size >= maxClauseCount) {
                        throw IndexSearcher.TooManyClauses()
                    }
                    paths.add(phrase)
                }
                if (paths.isNotEmpty()) {
                    clauses.add(Intervals.or(*paths.toTypedArray()))
                }
            } else {
                val it: MutableIterator<TokenStream> = graph.getFiniteStrings(start, end)
                val ts: TokenStream = it.next()
                clauses.addAll(analyzeTerms(ts))
                assert(it.hasNext() == false)
            }
        }
        return clauses
    }
}
