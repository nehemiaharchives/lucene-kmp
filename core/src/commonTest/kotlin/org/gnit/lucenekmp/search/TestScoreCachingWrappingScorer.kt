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

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals

class TestScoreCachingWrappingScorer : LuceneTestCase() {

    private class SimpleScorer : Scorer() {
        private var idx = 0
        private var doc = -1

        override fun score(): Float {
            // advance idx on purpose, so that consecutive calls to score will get
            // different results. This is to emulate computation of a score. If
            // ScoreCachingWrappingScorer is used, this should not be called more than
            // once per document.
            return if (idx == scores.size) Float.NaN else scores[idx++]
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

                override fun nextDoc(): Int {
                    return if (++doc < scores.size) doc else NO_MORE_DOCS
                }

                override fun advance(target: Int): Int {
                    doc = target
                    return if (doc < scores.size) doc else NO_MORE_DOCS
                }

                override fun cost(): Long {
                    return scores.size.toLong()
                }
            }
        }
    }

    private class ScoreCachingCollector(numToCollect: Int) : Collector {
        private var idx = 0
        private var scorer: Scorable? = null
        val mscores: FloatArray = FloatArray(numToCollect)
        override var weight: Weight? = null

        @Throws(IOException::class)
        override fun getLeafCollector(context: LeafReaderContext): LeafCollector {
            return ScoreCachingWrappingScorer.wrap(
                object : LeafCollector {
                    override var scorer: Scorable?
                        get() = this@ScoreCachingCollector.scorer
                        set(scorer) {
                            this@ScoreCachingCollector.scorer = scorer
                        }

                    @Throws(IOException::class)
                    override fun collect(doc: Int) {
                        // just a sanity check to avoid IOOB.
                        if (idx == mscores.size) {
                            return
                        }

                        // just call score() a couple of times and record the score.
                        mscores[idx] = scorer!!.score()
                        mscores[idx] = scorer!!.score()
                        mscores[idx] = scorer!!.score()
                        ++idx
                    }
                }
            )
        }

        override fun scoreMode(): ScoreMode {
            return ScoreMode.COMPLETE
        }
    }

    @Test
    @Throws(Exception::class)
    fun testGetScores() {
        val directory: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), directory)
        writer.addDocument(Document())
        writer.commit()
        val ir: IndexReader = writer.getReader(true, false)
        writer.close()
        val s: Scorer = SimpleScorer()
        val scc = ScoreCachingCollector(scores.size)
        val lc = scc.getLeafCollector(ir.leaves()[0])
        lc.scorer = s

        // We need to iterate on the scorer so that its doc() advances.
        var doc: Int
        while (s.iterator().nextDoc().also { doc = it } != DocIdSetIterator.NO_MORE_DOCS) {
            lc.collect(doc)
        }

        for (i in scores.indices) {
            assertEquals(scores[i], scc.mscores[i], 0f)
        }
        ir.close()
        directory.close()
    }

    companion object {
        private val scores = floatArrayOf(
            0.7767749f,
            1.7839992f,
            8.9925785f,
            7.9608946f,
            0.07948637f,
            2.6356435f,
            7.4950366f,
            7.1490803f,
            8.108544f,
            4.961808f,
            2.2423935f,
            7.285586f,
            4.6699767f
        )
    }
}
