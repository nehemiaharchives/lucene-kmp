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

import org.gnit.lucenekmp.index.LeafReaderContext
import kotlin.random.Random

/**
 * Query that matches all docs by returning a DenseConjunctionBulkScorer over a single clause. This
 * helps make sure that the competitive iterators produced by TopFieldCollector are compatible with
 * the read-ahead of this bulk scorer.
 */
class ReadAheadMatchAllDocsQuery(random: Random) : Query() {

    private val random: Random = Random(random.nextLong())

    /** Sole constructor */

    override fun toString(field: String?): String {
        return "ReadAheadMatchAllDocsQuery"
    }

    override fun visit(visitor: QueryVisitor) {
        // no-op
    }

    override fun equals(other: Any?): Boolean {
        return sameClassAs(other)
    }

    override fun hashCode(): Int {
        return classHash()
    }

    override fun createWeight(
        searcher: IndexSearcher,
        scoreMode: ScoreMode,
        boost: Float
    ): Weight {
        return object : ConstantScoreWeight(this, boost) {

            override fun isCacheable(ctx: LeafReaderContext): Boolean {
                return true
            }

            override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier {
                // Don't use ConstantScoreScoreSupplier, which only uses DenseConjunctionBulkScorer on
                // larg-ish segments.
                return object : ScorerSupplier() {

                    override fun get(leadCost: Long): Scorer {
                        return ConstantScoreScorer(
                            score(),
                            scoreMode,
                            DocIdSetIterator.all(context.reader().maxDoc())
                        )
                    }

                    override fun bulkScorer(): BulkScorer {
                        val clauses: MutableList<DocIdSetIterator> = if (random.nextBoolean()) {
                            mutableListOf()
                        } else {
                            mutableListOf(DocIdSetIterator.all(context.reader().maxDoc()))
                        }

                        return DenseConjunctionBulkScorer(clauses, context.reader().maxDoc(), score())
                    }

                    override fun cost(): Long {
                        return context.reader().maxDoc().toLong()
                    }
                }
            }
        }
    }
}
