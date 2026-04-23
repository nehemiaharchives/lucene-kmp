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
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.BitSetIterator
import org.gnit.lucenekmp.util.FixedBitSet
import kotlin.test.Test
import kotlin.test.assertEquals

class TestScorerPerf : LuceneTestCase() {
    private val validate = true // set to false when doing performance testing

    private class CountingHitCollectorManager : CollectorManager<CountingHitCollector, CountingHitCollector> {
        @Throws(IOException::class)
        override fun newCollector(): CountingHitCollector {
            return CountingHitCollector()
        }

        @Throws(IOException::class)
        override fun reduce(collectors: MutableCollection<CountingHitCollector>): CountingHitCollector {
            val result = CountingHitCollector()
            for (collector in collectors) {
                result.count += collector.count
                result.sum += collector.sum
            }
            return result
        }
    }

    private class CountingHitCollector : SimpleCollector() {
        var count = 0
        var sum = 0
        protected var docBase = 0
        override var weight: Weight? = null

        override fun collect(doc: Int) {
            count++
            sum += docBase + doc // use it to avoid any possibility of being eliminated by hotspot
        }

        override fun doSetNextReader(context: LeafReaderContext) {
            docBase = context.docBase
        }

        override fun scoreMode(): ScoreMode {
            return ScoreMode.COMPLETE_NO_SCORES
        }
    }

    private inner class BitSetQuery(private val docs: FixedBitSet) : Query() {
        override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
            return object : ConstantScoreWeight(this, boost) {
                override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier {
                    val scorer = ConstantScoreScorer(
                        score(),
                        scoreMode,
                        BitSetIterator(docs, docs.approximateCardinality().toLong())
                    )
                    return DefaultScorerSupplier(scorer)
                }

                override fun isCacheable(ctx: LeafReaderContext): Boolean {
                    return false
                }
            }
        }

        override fun visit(visitor: QueryVisitor) {}

        override fun toString(field: String?): String {
            return "randomBitSetFilter"
        }

        override fun equals(other: Any?): Boolean {
            return sameClassAs(other) && docs == (other as BitSetQuery).docs
        }

        override fun hashCode(): Int {
            return 31 * classHash() + docs.hashCode()
        }
    }

    private fun addClause(sets: Array<FixedBitSet>, bq: BooleanQuery.Builder, result: FixedBitSet?): FixedBitSet {
        val rnd = sets[random().nextInt(sets.size)]
        val q: Query = BitSetQuery(rnd)
        bq.add(q, BooleanClause.Occur.MUST)
        var actualResult = result
        if (validate) {
            actualResult =
                if (actualResult == null) {
                    rnd.clone()
                } else {
                    actualResult.and(rnd)
                    actualResult
                }
        }
        return actualResult!!
    }

    @Throws(IOException::class)
    private fun doConjunctions(s: IndexSearcher, sets: Array<FixedBitSet>, iter: Int, maxClauses: Int) {
        for (i in 0..<iter) {
            val nClauses = random().nextInt(maxClauses - 1) + 2 // min 2 clauses
            val bq = BooleanQuery.Builder()
            var result: FixedBitSet? = null
            for (j in 0..<nClauses) {
                result = addClause(sets, bq, result)
            }
            val hc = s.search(bq.build(), CountingHitCollectorManager())

            if (validate) {
                assertEquals(result!!.cardinality(), hc.count)
            }
        }
    }

    @Throws(IOException::class)
    private fun doNestedConjunctions(
        s: IndexSearcher,
        sets: Array<FixedBitSet>,
        iter: Int,
        maxOuterClauses: Int,
        maxClauses: Int
    ) {
        var nMatches = 0L

        for (i in 0..<iter) {
            val oClauses = random().nextInt(maxOuterClauses - 1) + 2
            val oq = BooleanQuery.Builder()
            var result: FixedBitSet? = null

            for (o in 0..<oClauses) {
                val nClauses = random().nextInt(maxClauses - 1) + 2 // min 2 clauses
                val bq = BooleanQuery.Builder()
                for (j in 0..<nClauses) {
                    result = addClause(sets, bq, result)
                }

                oq.add(bq.build(), BooleanClause.Occur.MUST)
            } // outer

            val hc = s.search(oq.build(), CountingHitCollectorManager())
            nMatches += hc.count.toLong()
            if (validate) {
                assertEquals(result!!.cardinality(), hc.count)
            }
        }
        if (VERBOSE) {
            println("Average number of matches=${nMatches / iter}")
        }
    }

    @Test
    @Throws(Exception::class)
    fun testConjunctions() {
        // test many small sets... the bugs will be found on boundary conditions
        newDirectory().use { d ->
            IndexWriter(d, newIndexWriterConfig(MockAnalyzer(random()))).use { iw ->
                iw.addDocument(Document())
            }

            DirectoryReader.open(d).use { r ->
                val s = newSearcher(r)
                s.queryCache = null

                val sets = randBitSets(atLeast(1000), atLeast(10))

                val iterations = if (TEST_NIGHTLY) atLeast(10000) else atLeast(500)
                doConjunctions(s, sets, iterations, atLeast(5))
                doNestedConjunctions(s, sets, iterations, atLeast(3), atLeast(3))
            }
        }
    }

    companion object {
        private fun randBitSet(sz: Int, numBitsToSet: Int): FixedBitSet {
            val set = FixedBitSet(sz)
            for (i in 0..<numBitsToSet) {
                set.set(random().nextInt(sz))
            }
            return set
        }

        private fun randBitSets(numSets: Int, setSize: Int): Array<FixedBitSet> {
            val sets = arrayOfNulls<FixedBitSet>(numSets)
            for (i in sets.indices) {
                sets[i] = randBitSet(setSize, random().nextInt(setSize))
            }
            return Array(numSets) { i -> sets[i]!! }
        }
    }
}
