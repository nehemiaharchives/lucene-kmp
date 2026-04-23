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
import org.gnit.lucenekmp.codecs.lucene90.IndexedDISI
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.KnnVectorValues
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.QueryTimeout
import org.gnit.lucenekmp.jdkport.Objects
import org.gnit.lucenekmp.search.knn.KnnCollectorManager
import org.gnit.lucenekmp.search.knn.KnnSearchStrategy
import org.gnit.lucenekmp.util.Bits

/**
 * This is a version of knn vector query that provides a query seed to initiate the vector search.
 * NOTE: The underlying format is free to ignore the provided seed
 *
 * See <a href="https://dl.acm.org/doi/10.1145/3539618.3591715">"Lexically-Accelerated Dense
 * Retrieval"</a> (Kulkarni, Hrishikesh and MacAvaney, Sean and Goharian, Nazli and Frieder, Ophir).
 * In SIGIR '23: Proceedings of the 46th International ACM SIGIR Conference on Research and
 * Development in Information Retrieval Pages 152 - 162
 *
 * @lucene.experimental
 */
open class SeededKnnVectorQuery internal constructor(
    internal val delegate: AbstractKnnVectorQuery,
    internal val seed: Query,
    internal val seedWeight: Weight?
) : AbstractKnnVectorQuery(delegate.field, delegate.k, delegate.filter, delegate.searchStrategy) {
    override fun toString(field: String?): String {
        return "SeededKnnVectorQuery{seed=$seed, seedWeight=$seedWeight, delegate=$delegate}"
    }

    override fun rewrite(indexSearcher: IndexSearcher): Query {
        if (seedWeight != null) {
            return super.rewrite(indexSearcher)
        }
        val rewritten = SeededKnnVectorQuery(delegate, seed, createSeedWeight(indexSearcher))
        return rewritten.rewrite(indexSearcher)
    }

    @Throws(IOException::class)
    internal fun createSeedWeight(indexSearcher: IndexSearcher): Weight {
        val booleanSeedQueryBuilder =
            BooleanQuery.Builder()
                .add(seed, BooleanClause.Occur.MUST)
                .add(FieldExistsQuery(field), BooleanClause.Occur.FILTER)
        if (filter != null) {
            booleanSeedQueryBuilder.add(filter, BooleanClause.Occur.FILTER)
        }
        val seedRewritten = indexSearcher.rewrite(booleanSeedQueryBuilder.build())
        return indexSearcher.createWeight(seedRewritten, ScoreMode.TOP_SCORES, 1f)
    }

    @Throws(IOException::class)
    override fun approximateSearch(
        context: LeafReaderContext,
        acceptDocs: Bits?,
        visitedLimit: Int,
        knnCollectorManager: KnnCollectorManager
    ): TopDocs {
        return delegate.approximateSearch(
            context,
            acceptDocs,
            visitedLimit,
            SeededCollectorManager(knnCollectorManager)
        )
    }

    override fun getKnnCollectorManager(k: Int, searcher: IndexSearcher): KnnCollectorManager {
        return delegate.getKnnCollectorManager(k, searcher)
    }

    @Throws(IOException::class)
    override fun exactSearch(
        context: LeafReaderContext,
        acceptIterator: DocIdSetIterator,
        queryTimeout: QueryTimeout?
    ): TopDocs {
        return delegate.exactSearch(context, acceptIterator, queryTimeout)
    }

    override fun mergeLeafResults(perLeafResults: Array<TopDocs>): TopDocs {
        return delegate.mergeLeafResults(perLeafResults)
    }

    override fun visit(visitor: QueryVisitor) {
        delegate.visit(visitor)
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || this::class != o::class) return false
        if (!super.equals(o)) return false
        val that = o as SeededKnnVectorQuery
        return seed == that.seed && seedWeight == that.seedWeight && delegate == that.delegate
    }

    override fun hashCode(): Int {
        return Objects.hash(super.hashCode(), seed, seedWeight, delegate)
    }

    @Throws(IOException::class)
    override fun createVectorScorer(context: LeafReaderContext, fi: FieldInfo): VectorScorer? {
        return delegate.createVectorScorer(context, fi)
    }

    private class MappedDISI(
        private val indexedDISI: KnnVectorValues.DocIndexIterator,
        private val sourceDISI: DocIdSetIterator
    ) : DocIdSetIterator() {
        /**
         * Advances the source iterator to the first document number that is greater than or equal to
         * the provided target and returns the corresponding index.
         */
        @Throws(IOException::class)
        override fun advance(target: Int): Int {
            val newTarget = sourceDISI.advance(target)
            if (newTarget != NO_MORE_DOCS) {
                indexedDISI.advance(newTarget)
            }
            return docID()
        }

        override fun cost(): Long {
            return sourceDISI.cost()
        }

        override fun docID(): Int {
            if (indexedDISI.docID() == NO_MORE_DOCS || sourceDISI.docID() == NO_MORE_DOCS) {
                return NO_MORE_DOCS
            }
            return indexedDISI.index()
        }

        /** Advances to the next document in the source iterator and returns the corresponding index. */
        @Throws(IOException::class)
        override fun nextDoc(): Int {
            val newTarget = sourceDISI.nextDoc()
            if (newTarget != NO_MORE_DOCS) {
                indexedDISI.advance(newTarget)
            }
            return docID()
        }
    }

    private class TopDocsDISI(topDocs: TopDocs, ctx: LeafReaderContext) : DocIdSetIterator() {
        private val sortedDocIds: IntArray = IntArray(topDocs.scoreDocs.size)
        private var idx = -1

        init {
            for (i in topDocs.scoreDocs.indices) {
                // Remove the doc base as added by the collector
                sortedDocIds[i] = topDocs.scoreDocs[i].doc - ctx.docBase
            }
            sortedDocIds.sort()
        }

        @Throws(IOException::class)
        override fun advance(target: Int): Int {
            return slowAdvance(target)
        }

        override fun cost(): Long {
            return sortedDocIds.size.toLong()
        }

        override fun docID(): Int {
            return when {
                idx == -1 -> -1
                idx >= sortedDocIds.size -> DocIdSetIterator.NO_MORE_DOCS
                else -> sortedDocIds[idx]
            }
        }

        override fun nextDoc(): Int {
            idx += 1
            return docID()
        }
    }

    open inner class SeededCollectorManager(internal val knnCollectorManager: KnnCollectorManager) :
        KnnCollectorManager {
        @Throws(IOException::class)
        override fun newCollector(
            visitedLimit: Int,
            searchStrategy: KnnSearchStrategy,
            context: LeafReaderContext
        ): KnnCollector {
            val seedCollector =
                TopScoreDocCollectorManager(k, null, Int.MAX_VALUE).newCollector()
            val leafReader: LeafReader = context.reader()
            val leafCollector = seedCollector.getLeafCollector(context)
            if (leafCollector != null) {
                try {
                    val scorer = seedWeight!!.bulkScorer(context)
                    if (scorer != null) {
                        scorer.score(
                            leafCollector,
                            leafReader.liveDocs,
                            0,
                            DocIdSetIterator.NO_MORE_DOCS
                        )
                    }
                } catch (_: CollectionTerminatedException) {
                }
                leafCollector.finish()
            }
            val delegateCollector =
                knnCollectorManager.newCollector(visitedLimit, searchStrategy, context)
            val seedTopDocs = seedCollector.topDocs()
            if (seedTopDocs.totalHits.value <= 0) {
                return delegateCollector
            }
            val fieldInfo = leafReader.fieldInfos.fieldInfo(field) ?: return delegateCollector
            val scorer = delegate.createVectorScorer(context, fieldInfo)
            if (scorer == null) {
                return delegateCollector
            }
            var vectorIterator = scorer.iterator()
            // Handle sparse
            if (vectorIterator is IndexedDISI) {
                vectorIterator = IndexedDISI.asDocIndexIterator(vectorIterator)
            }
            // Most underlying iterators are indexed, so we can map the seed docs to the vector docs
            if (vectorIterator is KnnVectorValues.DocIndexIterator) {
                val seedDocs = MappedDISI(vectorIterator, TopDocsDISI(seedTopDocs, context))
                return knnCollectorManager.newCollector(
                    visitedLimit,
                    KnnSearchStrategy.Seeded(
                        seedDocs,
                        seedTopDocs.scoreDocs.size,
                        searchStrategy
                    ),
                    context
                )
            }
            return delegateCollector
        }
    }

    companion object {
        /**
         * Construct a new SeededKnnVectorQuery instance for a float vector field
         *
         * @param knnQuery the knn query to be seeded
         * @param seed a query seed to initiate the vector format search
         * @return a new SeededKnnVectorQuery instance
         * @lucene.experimental
         */
        fun fromFloatQuery(knnQuery: KnnFloatVectorQuery, seed: Query): SeededKnnVectorQuery {
            return SeededKnnVectorQuery(knnQuery, seed, null)
        }

        /**
         * Construct a new SeededKnnVectorQuery instance for a byte vector field
         *
         * @param knnQuery the knn query to be seeded
         * @param seed a query seed to initiate the vector format search
         * @return a new SeededKnnVectorQuery instance
         * @lucene.experimental
         */
        fun fromByteQuery(knnQuery: KnnByteVectorQuery, seed: Query): SeededKnnVectorQuery {
            return SeededKnnVectorQuery(knnQuery, seed, null)
        }
    }
}
