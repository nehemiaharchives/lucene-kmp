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
package org.gnit.lucenekmp.tests.search

import okio.IOException
import org.gnit.lucenekmp.search.BulkScorer
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.LeafCollector
import org.gnit.lucenekmp.search.Scorable
import org.gnit.lucenekmp.search.Scorer
import org.gnit.lucenekmp.jdkport.Arrays

/** A [BulkScorer]-backed scorer. */
class BulkScorerWrapperScorer(
    private val scorer: BulkScorer,
    bufferSize: Int,
) : Scorer() {
    private var i = -1
    private var doc = -1
    private var next = 0

    private val docs = IntArray(bufferSize)
    private val scores = FloatArray(bufferSize)
    private var bufferLength = 0

    @Throws(IOException::class)
    private fun refill(target: Int) {
        bufferLength = 0
        while (next != DocIdSetIterator.NO_MORE_DOCS && bufferLength == 0) {
            val min = kotlin.math.max(target, next)
            val max = min + docs.size
            next =
                scorer.score(
                    object : LeafCollector {
                        override var scorer: Scorable? = null

                        override fun collect(doc: Int) {
                            docs[bufferLength] = doc
                            scores[bufferLength] = scorer!!.score()
                            bufferLength += 1
                        }
                    },
                    null,
                    min,
                    max,
                )
        }
        i = -1
    }

    @Throws(IOException::class)
    override fun score(): Float {
        return scores[i]
    }

    @Throws(IOException::class)
    override fun getMaxScore(upTo: Int): Float {
        return Float.POSITIVE_INFINITY
    }

    override fun docID(): Int {
        return doc
    }

    override fun iterator(): DocIdSetIterator {
        return object : DocIdSetIterator() {
            override fun docID(): Int {
                return doc
            }

            @Throws(IOException::class)
            override fun nextDoc(): Int {
                return advance(docID() + 1)
            }

            @Throws(IOException::class)
            override fun advance(target: Int): Int {
                if (bufferLength == 0 || docs[bufferLength - 1] < target) {
                    refill(target)
                }

                i = Arrays.binarySearch(docs, i + 1, bufferLength, target)
                if (i < 0) {
                    i = -1 - i
                }
                if (i == bufferLength) {
                    doc = DocIdSetIterator.NO_MORE_DOCS
                    return doc
                }
                doc = docs[i]
                return doc
            }

            override fun cost(): Long {
                return scorer.cost()
            }
        }
    }
}
