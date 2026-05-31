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
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.commongrams.CommonGramsFilter.Companion.GRAM_TYPE
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionLengthAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TypeAttribute
import org.gnit.lucenekmp.util.AttributeSource

/**
 * Wrap a CommonGramsFilter optimizing phrase queries by only returning single words when they are
 * not a member of a bigram.
 *
 * <p>Example:
 *
 * <ul>
 *   <li>query input to CommonGramsFilter: "the rain in spain falls mainly"
 *   <li>output of CommomGramsFilter/input to CommonGramsQueryFilter: |"the, "the-rain"|"rain"
 *       "rain-in"|"in, "in-spain"|"spain"|"falls"|"mainly"
 *   <li>output of CommonGramsQueryFilter:"the-rain", "rain-in" ,"in-spain", "falls", "mainly"
 * </ul>
 */

/*
 * See:http://hudson.zones.apache.org/hudson/job/Lucene-trunk/javadoc//all/org/apache/lucene/analysis/TokenStream.html and
 * http://svn.apache.org/viewvc/lucene/dev/trunk/lucene/src/java/org/apache/lucene/analysis/package.html?revision=718798
 */
class CommonGramsQueryFilter(input: CommonGramsFilter) : TokenFilter(input) {

    private val typeAttribute: TypeAttribute = addAttribute(TypeAttribute::class)
    private val posIncAttribute: PositionIncrementAttribute =
        addAttribute(PositionIncrementAttribute::class)
    private val posLengthAttribute: PositionLengthAttribute =
        addAttribute(PositionLengthAttribute::class)

    private var previous: AttributeSource.State? = null
    private var previousType: String? = null
    private var exhausted = false

    @Throws(IOException::class)
    override fun reset() {
        super.reset()
        previous = null
        previousType = null
        exhausted = false
    }

    /**
     * Output bigrams whenever possible to optimize queries. Only output unigrams when they are not a
     * member of a bigram. Example:
     *
     * <ul>
     *   <li>input: "the rain in spain falls mainly"
     *   <li>output:"the-rain", "rain-in" ,"in-spain", "falls", "mainly"
     * </ul>
     */
    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        while (!exhausted && input.incrementToken()) {
            val current = captureState()

            if (previous != null && !isGramType()) {
                restoreState(previous)
                previous = current
                previousType = typeAttribute.type()

                if (isGramType()) {
                    posIncAttribute.setPositionIncrement(1)
                    // We must set this back to 1 (from e.g. 2 or higher) otherwise the token graph is
                    // disconnected:
                    posLengthAttribute.positionLength = 1
                }
                return true
            }

            previous = current
        }

        exhausted = true

        if (previous == null || GRAM_TYPE == previousType) {
            return false
        }

        restoreState(previous)
        previous = null

        if (isGramType()) {
            posIncAttribute.setPositionIncrement(1)
            // We must set this back to 1 (from e.g. 2 or higher) otherwise the token graph is
            // disconnected:
            posLengthAttribute.positionLength = 1
        }
        return true
    }

    /**
     * Convenience method to check if the current type is a gram type
     *
     * @return `true` if the current type is a gram type, `false` otherwise
     */
    fun isGramType(): Boolean {
        return GRAM_TYPE == typeAttribute.type()
    }
}
