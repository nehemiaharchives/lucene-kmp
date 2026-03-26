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
package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestPositiveScoresOnlyCollector : LuceneTestCase() {
    private class SimpleScorer : Scorer() {
        private var idx = -1

        override fun score(): Float {
            return if (idx == scores.size) Float.NaN else scores[idx]
        }

        override fun getMaxScore(upTo: Int): Float {
            return Float.POSITIVE_INFINITY
        }

        override fun docID(): Int {
            return idx
        }

        override fun iterator(): DocIdSetIterator {
            return object : DocIdSetIterator() {
                override fun docID(): Int {
                    return idx
                }

                override fun nextDoc(): Int {
                    return if (++idx != scores.size) idx else NO_MORE_DOCS
                }

                override fun advance(target: Int): Int {
                    idx = target
                    return if (idx < scores.size) idx else NO_MORE_DOCS
                }

                override fun cost(): Long {
                    return scores.size.toLong()
                }
            }
        }
    }

    @Test
    fun testNegativeScores() {
        // The Top*Collectors previously filtered out documents with <= scores. This
        // behavior has changed. This test checks that if PositiveOnlyScoresFilter
        // wraps one of these collectors, documents with <= 0 scores are indeed
        // filtered.

        var numPositiveScores = 0
        for (i in scores.indices) {
            if (scores[i] > 0) {
                ++numPositiveScores
            }
        }

        val directory: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), directory)
        writer.addDocument(Document())
        writer.commit()
        val ir: IndexReader = writer.getReader(true, false)
        writer.close()
        val s: Scorer = SimpleScorer()
        val tdc: TopDocsCollector<ScoreDoc> =
            TopScoreDocCollectorManager(scores.size, null, Int.MAX_VALUE).newCollector()
        val c: Collector = PositiveScoresOnlyCollector(tdc)
        val ac = c.getLeafCollector(ir.leaves()[0])
        ac.scorer = s
        while (s.iterator().nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
            ac.collect(0)
        }
        val td = tdc.topDocs()
        val sd = td.scoreDocs
        assertEquals(numPositiveScores.toLong(), td.totalHits.value)
        for (i in sd.indices) {
            assertTrue(sd[i].score > 0, "only positive scores should return: ${sd[i].score}")
        }
        ir.close()
        directory.close()
    }

    companion object {
        // The scores must have positive as well as negative values
        private val scores = floatArrayOf(
            0.7767749f,
            -1.7839992f,
            8.9925785f,
            7.9608946f,
            -0.07948637f,
            2.6356435f,
            7.4950366f,
            7.1490803f,
            -8.108544f,
            4.961808f,
            2.2423935f,
            -7.285586f,
            4.6699767f
        )
    }
}
