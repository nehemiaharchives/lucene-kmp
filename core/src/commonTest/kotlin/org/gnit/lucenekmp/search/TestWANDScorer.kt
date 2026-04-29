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
import org.gnit.lucenekmp.document.Field.Store
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.BooleanClause.Occur
import org.gnit.lucenekmp.tests.search.AssertingQuery
import org.gnit.lucenekmp.tests.search.BlockScoreQueryWrapper
import org.gnit.lucenekmp.tests.search.CheckHits
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestWANDScorer : LuceneTestCase() {
    @Test
    fun testScalingFactor() {
        doTestScalingFactor(1f)
        doTestScalingFactor(2f)
        doTestScalingFactor(Math.nextDown(1f))
        doTestScalingFactor(Math.nextUp(1f))
        doTestScalingFactor(Float.MIN_VALUE)
        doTestScalingFactor(Math.nextUp(Float.MIN_VALUE))
        doTestScalingFactor(Float.MAX_VALUE)
        doTestScalingFactor(Math.nextDown(Float.MAX_VALUE))
        assertEquals(WANDScorer.scalingFactor(Float.MIN_VALUE) + 1, WANDScorer.scalingFactor(0f))
        assertEquals(
            WANDScorer.scalingFactor(Float.MAX_VALUE) - 1,
            WANDScorer.scalingFactor(Float.POSITIVE_INFINITY)
        )

        // Greater scores produce lower scaling factors
        assertTrue(WANDScorer.scalingFactor(1f) > WANDScorer.scalingFactor(10f))
        assertTrue(
            WANDScorer.scalingFactor(Float.MAX_VALUE) > WANDScorer.scalingFactor(Float.POSITIVE_INFINITY)
        )
        assertTrue(WANDScorer.scalingFactor(0f) > WANDScorer.scalingFactor(Float.MIN_VALUE))
    }

    private fun doTestScalingFactor(f: Float) {
        val scalingFactor = WANDScorer.scalingFactor(f)
        val scaled = Math.scalb(f.toDouble(), scalingFactor).toFloat()
        assertTrue(scaled >= (1 shl (WANDScorer.FLOAT_MANTISSA_BITS - 1)), scaled.toString())
        assertTrue(scaled < (1 shl WANDScorer.FLOAT_MANTISSA_BITS), scaled.toString())
    }

    @Test
    fun testScaleMaxScore() {
        assertEquals(
            (1 shl (WANDScorer.FLOAT_MANTISSA_BITS - 1)).toLong(),
            WANDScorer.scaleMaxScore(32f, WANDScorer.scalingFactor(32f))
        )
        assertEquals(
            1L,
            WANDScorer.scaleMaxScore(32f, WANDScorer.scalingFactor(Math.scalb(1.0, 60).toFloat()))
        )
        assertEquals(
            1L,
            WANDScorer.scaleMaxScore(32f, WANDScorer.scalingFactor(Float.POSITIVE_INFINITY))
        )
    }

    private fun maybeWrap(query: Query): Query {
        var query = query
        if (random().nextBoolean()) {
            query = BlockScoreQueryWrapper(query, TestUtil.nextInt(random(), 2, 8))
            query = AssertingQuery(random(), query)
        }
        return query
    }

    @Test
    @Throws(Exception::class)
    fun testBasics() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig().setMergePolicy(newLogMergePolicy()))
        try {
            for (values in listOf(
                arrayOf("A", "B"), // 0
                arrayOf("A"), // 1
                emptyArray<String>(), // 2
                arrayOf("A", "B", "C"), // 3
                arrayOf("B"), // 4
                arrayOf("B", "C") // 5
            )) {
                val doc = Document()
                for (value in values) {
                    doc.add(StringField("foo", value, Store.NO))
                }
                w.addDocument(doc)
            }
            w.forceMerge(1)
        } finally {
            w.close()
        }

        val reader = DirectoryReader.open(dir)
        try {
            val searcher = newSearcher(reader)

            var query: Query =
                WANDScorerQuery(
                    BooleanQuery.Builder()
                        .add(
                            BoostQuery(ConstantScoreQuery(TermQuery(Term("foo", "A"))), 2f),
                            Occur.SHOULD
                        )
                        .add(ConstantScoreQuery(TermQuery(Term("foo", "B"))), Occur.SHOULD)
                        .add(
                            BoostQuery(ConstantScoreQuery(TermQuery(Term("foo", "C"))), 3f),
                            Occur.SHOULD
                        )
                        .build(),
                    random().nextBoolean()
                )

            var weight = searcher.createWeight(searcher.rewrite(query), ScoreMode.TOP_SCORES, 1f)
            var ss = weight.scorerSupplier(searcher.indexReader.leaves()[0])!!
            ss.setTopLevelScoringClause()
            var scorer = ss.get(Long.MAX_VALUE)!!

            assertEquals(0, scorer.iterator().nextDoc())
            assertEquals(3f, scorer.score(), 0f)

            assertEquals(1, scorer.iterator().nextDoc())
            assertEquals(2f, scorer.score(), 0f)

            assertEquals(3, scorer.iterator().nextDoc())
            assertEquals(6f, scorer.score(), 0f)

            assertEquals(4, scorer.iterator().nextDoc())
            assertEquals(1f, scorer.score(), 0f)

            assertEquals(5, scorer.iterator().nextDoc())
            assertEquals(4f, scorer.score(), 0f)

            assertEquals(DocIdSetIterator.NO_MORE_DOCS, scorer.iterator().nextDoc())

            ss = weight.scorerSupplier(searcher.indexReader.leaves()[0])!!
            ss.setTopLevelScoringClause()
            scorer = ss.get(Long.MAX_VALUE)!!
            scorer.minCompetitiveScore = 4f

            assertEquals(3, scorer.iterator().nextDoc())
            assertEquals(6f, scorer.score(), 0f)

            assertEquals(5, scorer.iterator().nextDoc())
            assertEquals(4f, scorer.score(), 0f)

            assertEquals(DocIdSetIterator.NO_MORE_DOCS, scorer.iterator().nextDoc())

            ss = weight.scorerSupplier(searcher.indexReader.leaves()[0])!!
            ss.setTopLevelScoringClause()
            scorer = ss.get(Long.MAX_VALUE)!!

            assertEquals(0, scorer.iterator().nextDoc())
            assertEquals(3f, scorer.score(), 0f)

            scorer.minCompetitiveScore = 10f

            assertEquals(DocIdSetIterator.NO_MORE_DOCS, scorer.iterator().nextDoc())

            //  test a filtered disjunction
            query =
                BooleanQuery.Builder()
                    .add(
                        WANDScorerQuery(
                            BooleanQuery.Builder()
                                .add(
                                    BoostQuery(
                                        ConstantScoreQuery(TermQuery(Term("foo", "A"))),
                                        2f
                                    ),
                                    Occur.SHOULD
                                )
                                .add(
                                    ConstantScoreQuery(TermQuery(Term("foo", "B"))),
                                    Occur.SHOULD
                                )
                                .build(),
                            random().nextBoolean()
                        ),
                        Occur.MUST
                    )
                    .add(TermQuery(Term("foo", "C")), Occur.FILTER)
                    .build()

            weight = searcher.createWeight(searcher.rewrite(query), ScoreMode.TOP_SCORES, 1f)
            ss = weight.scorerSupplier(searcher.indexReader.leaves()[0])!!
            ss.setTopLevelScoringClause()
            scorer = ss.get(Long.MAX_VALUE)!!

            assertEquals(3, scorer.iterator().nextDoc())
            assertEquals(3f, scorer.score(), 0f)

            assertEquals(5, scorer.iterator().nextDoc())
            assertEquals(1f, scorer.score(), 0f)

            assertEquals(DocIdSetIterator.NO_MORE_DOCS, scorer.iterator().nextDoc())

            ss = weight.scorerSupplier(searcher.indexReader.leaves()[0])!!
            ss.setTopLevelScoringClause()
            scorer = ss.get(Long.MAX_VALUE)!!

            scorer.minCompetitiveScore = 2f

            assertEquals(3, scorer.iterator().nextDoc())
            assertEquals(3f, scorer.score(), 0f)

            assertEquals(DocIdSetIterator.NO_MORE_DOCS, scorer.iterator().nextDoc())

            // Now test a filtered disjunction with a MUST_NOT
            query =
                BooleanQuery.Builder()
                    .add(
                        WANDScorerQuery(
                            BooleanQuery.Builder()
                                .add(
                                    BoostQuery(
                                        ConstantScoreQuery(TermQuery(Term("foo", "A"))),
                                        2f
                                    ),
                                    Occur.SHOULD
                                )
                                .add(
                                    ConstantScoreQuery(TermQuery(Term("foo", "B"))),
                                    Occur.SHOULD
                                )
                                .build(),
                            random().nextBoolean()
                        ),
                        Occur.MUST
                    )
                    .add(TermQuery(Term("foo", "C")), Occur.MUST_NOT)
                    .build()

            weight = searcher.createWeight(searcher.rewrite(query), ScoreMode.TOP_SCORES, 1f)
            ss = weight.scorerSupplier(searcher.indexReader.leaves()[0])!!
            ss.setTopLevelScoringClause()
            scorer = ss.get(Long.MAX_VALUE)!!

            assertEquals(0, scorer.iterator().nextDoc())
            assertEquals(3f, scorer.score(), 0f)

            assertEquals(1, scorer.iterator().nextDoc())
            assertEquals(2f, scorer.score(), 0f)

            assertEquals(4, scorer.iterator().nextDoc())
            assertEquals(1f, scorer.score(), 0f)

            assertEquals(DocIdSetIterator.NO_MORE_DOCS, scorer.iterator().nextDoc())

            ss = weight.scorerSupplier(searcher.indexReader.leaves()[0])!!
            ss.setTopLevelScoringClause()
            scorer = ss.get(Long.MAX_VALUE)!!

            scorer.minCompetitiveScore = 3f

            assertEquals(0, scorer.iterator().nextDoc())
            assertEquals(3f, scorer.score(), 0f)

            assertEquals(DocIdSetIterator.NO_MORE_DOCS, scorer.iterator().nextDoc())
        } finally {
            reader.close()
            dir.close()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testBasicsWithDisjunctionAndMinShouldMatch() {
        val dir = newDirectory()
        try {
            val w = IndexWriter(dir, newIndexWriterConfig().setMergePolicy(newLogMergePolicy()))
            try {
                for (values in listOf(
                    arrayOf("A", "B"), // 0
                    arrayOf("A"), // 1
                    emptyArray<String>(), // 2
                    arrayOf("A", "B", "C"), // 3
                    arrayOf("B"), // 4
                    arrayOf("B", "C") // 5
                )) {
                    val doc = Document()
                    for (value in values) {
                        doc.add(StringField("foo", value, Store.NO))
                    }
                    w.addDocument(doc)
                }

                w.forceMerge(1)
            } finally {
                w.close()
            }

            val reader = DirectoryReader.open(dir)
            try {
                val searcher = newSearcher(reader)

                val query: Query =
                    WANDScorerQuery(
                        BooleanQuery.Builder()
                            .add(
                                BoostQuery(ConstantScoreQuery(TermQuery(Term("foo", "A"))), 2f),
                                Occur.SHOULD
                            )
                            .add(ConstantScoreQuery(TermQuery(Term("foo", "B"))), Occur.SHOULD)
                            .add(
                                BoostQuery(ConstantScoreQuery(TermQuery(Term("foo", "C"))), 3f),
                                Occur.SHOULD
                            )
                            .setMinimumNumberShouldMatch(2)
                            .build(),
                        random().nextBoolean()
                    )

                val weight = searcher.createWeight(searcher.rewrite(query), ScoreMode.TOP_SCORES, 1f)
                var ss = weight.scorerSupplier(searcher.indexReader.leaves()[0])!!
                ss.setTopLevelScoringClause()
                var scorer = ss.get(Long.MAX_VALUE)!!

                assertEquals(0, scorer.iterator().nextDoc())
                assertEquals(3f, scorer.score(), 0f)

                assertEquals(3, scorer.iterator().nextDoc())
                assertEquals(6f, scorer.score(), 0f)

                assertEquals(5, scorer.iterator().nextDoc())
                assertEquals(4f, scorer.score(), 0f)

                assertEquals(DocIdSetIterator.NO_MORE_DOCS, scorer.iterator().nextDoc())

                ss = weight.scorerSupplier(searcher.indexReader.leaves()[0])!!
                ss.setTopLevelScoringClause()
                scorer = ss.get(Long.MAX_VALUE)!!
                scorer.minCompetitiveScore = 4f

                assertEquals(3, scorer.iterator().nextDoc())
                assertEquals(6f, scorer.score(), 0f)

                assertEquals(5, scorer.iterator().nextDoc())
                assertEquals(4f, scorer.score(), 0f)

                assertEquals(DocIdSetIterator.NO_MORE_DOCS, scorer.iterator().nextDoc())

                ss = weight.scorerSupplier(searcher.indexReader.leaves()[0])!!
                ss.setTopLevelScoringClause()
                scorer = ss.get(Long.MAX_VALUE)!!

                assertEquals(0, scorer.iterator().nextDoc())
                assertEquals(3f, scorer.score(), 0f)

                scorer.minCompetitiveScore = 10f

                assertEquals(DocIdSetIterator.NO_MORE_DOCS, scorer.iterator().nextDoc())
            } finally {
                reader.close()
            }
        } finally {
            dir.close()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testBasicsWithDisjunctionAndMinShouldMatchAndTailSizeCondition() {
        val dir = newDirectory()
        try {
            val w = IndexWriter(dir, newIndexWriterConfig().setMergePolicy(newLogMergePolicy()))
            try {
                for (values in listOf(
                    arrayOf("A", "B"), // 0
                    arrayOf("A"), // 1
                    emptyArray<String>(), // 2
                    arrayOf("A", "B", "C"), // 3
                    // 2 "B"s here and the non constant score term query below forces the
                    // tailMaxScore >= minCompetitiveScore && tailSize < minShouldMatch condition
                    arrayOf("B", "B"), // 4
                    arrayOf("B", "C") // 5
                )) {
                    val doc = Document()
                    for (value in values) {
                        doc.add(StringField("foo", value, Store.NO))
                    }
                    w.addDocument(doc)
                }

                w.forceMerge(1)
            } finally {
                w.close()
            }

            val reader = DirectoryReader.open(dir)
            try {
                val searcher = newSearcher(reader)

                val query: Query =
                    WANDScorerQuery(
                        BooleanQuery.Builder()
                            .add(TermQuery(Term("foo", "A")), Occur.SHOULD)
                            .add(TermQuery(Term("foo", "B")), Occur.SHOULD)
                            .add(TermQuery(Term("foo", "C")), Occur.SHOULD)
                            .setMinimumNumberShouldMatch(2)
                            .build(),
                        random().nextBoolean()
                    )

                val weight = searcher.createWeight(searcher.rewrite(query), ScoreMode.TOP_SCORES, 1f)
                val ss = weight.scorerSupplier(searcher.indexReader.leaves()[0])!!
                ss.setTopLevelScoringClause()
                val scorer = ss.get(Long.MAX_VALUE)!!

                assertEquals(0, scorer.iterator().nextDoc())
                scorer.minCompetitiveScore = scorer.score()

                assertEquals(3, scorer.iterator().nextDoc())
            } finally {
                reader.close()
            }
        } finally {
            dir.close()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testBasicsWithDisjunctionAndMinShouldMatchAndNonScoringMode() {
        val dir = newDirectory()
        try {
            val w = IndexWriter(dir, newIndexWriterConfig().setMergePolicy(newLogMergePolicy()))
            try {
                for (values in listOf(
                    arrayOf("A", "B"), // 0
                    arrayOf("A"), // 1
                    emptyArray<String>(), // 2
                    arrayOf("A", "B", "C"), // 3
                    arrayOf("B"), // 4
                    arrayOf("B", "C") // 5
                )) {
                    val doc = Document()
                    for (value in values) {
                        doc.add(StringField("foo", value, Store.NO))
                    }
                    w.addDocument(doc)
                }

                w.forceMerge(1)
            } finally {
                w.close()
            }

            val reader = DirectoryReader.open(dir)
            try {
                val searcher = newSearcher(reader)

                val query: Query =
                    WANDScorerQuery(
                        BooleanQuery.Builder()
                            .add(
                                BoostQuery(ConstantScoreQuery(TermQuery(Term("foo", "A"))), 2f),
                                Occur.SHOULD
                            )
                            .add(ConstantScoreQuery(TermQuery(Term("foo", "B"))), Occur.SHOULD)
                            .add(
                                BoostQuery(ConstantScoreQuery(TermQuery(Term("foo", "C"))), 3f),
                                Occur.SHOULD
                            )
                            .setMinimumNumberShouldMatch(2)
                            .build(),
                        random().nextBoolean()
                    )

                val scorer =
                    searcher
                        .createWeight(searcher.rewrite(query), ScoreMode.COMPLETE_NO_SCORES, 1f)
                        .scorer(searcher.indexReader.leaves()[0])!!

                assertEquals(0, scorer.iterator().nextDoc())
                assertEquals(3, scorer.iterator().nextDoc())
                assertEquals(5, scorer.iterator().nextDoc())
                assertEquals(DocIdSetIterator.NO_MORE_DOCS, scorer.iterator().nextDoc())
            } finally {
                reader.close()
            }
        } finally {
            dir.close()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testBasicsWithFilteredDisjunctionAndMinShouldMatch() {
        val dir = newDirectory()
        try {
            val w = IndexWriter(dir, newIndexWriterConfig().setMergePolicy(newLogMergePolicy()))
            try {
                for (values in listOf(
                    arrayOf("A", "B"), // 0
                    arrayOf("A", "C", "D"), // 1
                    emptyArray<String>(), // 2
                    arrayOf("A", "B", "C", "D"), // 3
                    arrayOf("B"), // 4
                    arrayOf("C", "D") // 5
                )) {
                    val doc = Document()
                    for (value in values) {
                        doc.add(StringField("foo", value, Store.NO))
                    }
                    w.addDocument(doc)
                }

                w.forceMerge(1)
            } finally {
                w.close()
            }

            val reader = DirectoryReader.open(dir)
            try {
                val searcher = newSearcher(reader)

                val query: Query =
                    BooleanQuery.Builder()
                        .add(
                            WANDScorerQuery(
                                BooleanQuery.Builder()
                                    .add(
                                        BoostQuery(
                                            ConstantScoreQuery(TermQuery(Term("foo", "A"))),
                                            2f
                                        ),
                                        Occur.SHOULD
                                    )
                                    .add(
                                        ConstantScoreQuery(TermQuery(Term("foo", "B"))),
                                        Occur.SHOULD
                                    )
                                    .add(
                                        BoostQuery(
                                            ConstantScoreQuery(TermQuery(Term("foo", "D"))),
                                            4f
                                        ),
                                        Occur.SHOULD
                                    )
                                    .setMinimumNumberShouldMatch(2)
                                    .build(),
                                random().nextBoolean()
                            ),
                            Occur.MUST
                        )
                        .add(TermQuery(Term("foo", "C")), Occur.FILTER)
                        .build()

                val weight = searcher.createWeight(searcher.rewrite(query), ScoreMode.TOP_SCORES, 1f)
                var ss = weight.scorerSupplier(searcher.indexReader.leaves()[0])!!
                ss.setTopLevelScoringClause()
                var scorer = ss.get(Long.MAX_VALUE)!!

                assertEquals(1, scorer.iterator().nextDoc())
                assertEquals(6f, scorer.score(), 0f)

                assertEquals(3, scorer.iterator().nextDoc())
                assertEquals(7f, scorer.score(), 0f)

                assertEquals(DocIdSetIterator.NO_MORE_DOCS, scorer.iterator().nextDoc())

                ss = weight.scorerSupplier(searcher.indexReader.leaves()[0])!!
                ss.setTopLevelScoringClause()
                scorer = ss.get(Long.MAX_VALUE)!!

                scorer.minCompetitiveScore = 7f

                assertEquals(3, scorer.iterator().nextDoc())
                assertEquals(7f, scorer.score(), 0f)

                assertEquals(DocIdSetIterator.NO_MORE_DOCS, scorer.iterator().nextDoc())
            } finally {
                reader.close()
            }
        } finally {
            dir.close()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testBasicsWithFilteredDisjunctionAndMinShouldMatchAndNonScoringMode() {
        val dir = newDirectory()
        try {
            val w = IndexWriter(dir, newIndexWriterConfig().setMergePolicy(newLogMergePolicy()))
            try {
                for (values in listOf(
                    arrayOf("A", "B"), // 0
                    arrayOf("A", "C", "D"), // 1
                    emptyArray<String>(), // 2
                    arrayOf("A", "B", "C", "D"), // 3
                    arrayOf("B"), // 4
                    arrayOf("C", "D") // 5
                )) {
                    val doc = Document()
                    for (value in values) {
                        doc.add(StringField("foo", value, Store.NO))
                    }
                    w.addDocument(doc)
                }

                w.forceMerge(1)
            } finally {
                w.close()
            }

            val reader = DirectoryReader.open(dir)
            try {
                val searcher = newSearcher(reader)

                val query: Query =
                    BooleanQuery.Builder()
                        .add(
                            WANDScorerQuery(
                                BooleanQuery.Builder()
                                    .add(
                                        BoostQuery(
                                            ConstantScoreQuery(TermQuery(Term("foo", "A"))),
                                            2f
                                        ),
                                        Occur.SHOULD
                                    )
                                    .add(
                                        ConstantScoreQuery(TermQuery(Term("foo", "B"))),
                                        Occur.SHOULD
                                    )
                                    .add(
                                        BoostQuery(
                                            ConstantScoreQuery(TermQuery(Term("foo", "D"))),
                                            4f
                                        ),
                                        Occur.SHOULD
                                    )
                                    .setMinimumNumberShouldMatch(2)
                                    .build(),
                                random().nextBoolean()
                            ),
                            Occur.MUST
                        )
                        .add(TermQuery(Term("foo", "C")), Occur.FILTER)
                        .build()

                val scorer =
                    searcher
                        .createWeight(searcher.rewrite(query), ScoreMode.TOP_DOCS, 1f)
                        .scorer(searcher.indexReader.leaves()[0])!!

                assertEquals(1, scorer.iterator().nextDoc())
                assertEquals(3, scorer.iterator().nextDoc())
                assertEquals(DocIdSetIterator.NO_MORE_DOCS, scorer.iterator().nextDoc())
            } finally {
                reader.close()
            }
        } finally {
            dir.close()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testBasicsWithFilteredDisjunctionAndMustNotAndMinShouldMatch() {
        val dir = newDirectory()
        try {
            val w = IndexWriter(dir, newIndexWriterConfig().setMergePolicy(newLogMergePolicy()))
            try {
                for (values in listOf(
                    arrayOf("A", "B"), // 0
                    arrayOf("A", "C", "D"), // 1
                    emptyArray<String>(), // 2
                    arrayOf("A", "B", "C", "D"), // 3
                    arrayOf("B", "D"), // 4
                    arrayOf("C", "D") // 5
                )) {
                    val doc = Document()
                    for (value in values) {
                        doc.add(StringField("foo", value, Store.NO))
                    }
                    w.addDocument(doc)
                }

                w.forceMerge(1)
            } finally {
                w.close()
            }

            val reader = DirectoryReader.open(dir)
            try {
                val searcher = newSearcher(reader)

                val query: Query =
                    BooleanQuery.Builder()
                        .add(
                            WANDScorerQuery(
                                BooleanQuery.Builder()
                                    .add(
                                        BoostQuery(
                                            ConstantScoreQuery(TermQuery(Term("foo", "A"))),
                                            2f
                                        ),
                                        Occur.SHOULD
                                    )
                                    .add(
                                        ConstantScoreQuery(TermQuery(Term("foo", "B"))),
                                        Occur.SHOULD
                                    )
                                    .add(
                                        BoostQuery(
                                            ConstantScoreQuery(TermQuery(Term("foo", "D"))),
                                            4f
                                        ),
                                        Occur.SHOULD
                                    )
                                    .setMinimumNumberShouldMatch(2)
                                    .build(),
                                random().nextBoolean()
                            ),
                            Occur.MUST
                        )
                        .add(TermQuery(Term("foo", "C")), Occur.MUST_NOT)
                        .build()

                val weight = searcher.createWeight(searcher.rewrite(query), ScoreMode.TOP_SCORES, 1f)
                var scorer = weight.scorer(searcher.indexReader.leaves()[0])!!

                assertEquals(0, scorer.iterator().nextDoc())
                assertEquals(3f, scorer.score(), 0f)

                assertEquals(4, scorer.iterator().nextDoc())
                assertEquals(5f, scorer.score(), 0f)

                assertEquals(DocIdSetIterator.NO_MORE_DOCS, scorer.iterator().nextDoc())

                val ss = weight.scorerSupplier(searcher.indexReader.leaves()[0])!!
                ss.setTopLevelScoringClause()
                scorer = ss.get(Long.MAX_VALUE)!!

                scorer.minCompetitiveScore = 4f

                assertEquals(4, scorer.iterator().nextDoc())
                assertEquals(5f, scorer.score(), 0f)

                assertEquals(DocIdSetIterator.NO_MORE_DOCS, scorer.iterator().nextDoc())
            } finally {
                reader.close()
            }
        } finally {
            dir.close()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testBasicsWithFilteredDisjunctionAndMustNotAndMinShouldMatchAndNonScoringMode() {
        val dir = newDirectory()
        try {
            val w = IndexWriter(dir, newIndexWriterConfig().setMergePolicy(newLogMergePolicy()))
            try {
                for (values in listOf(
                    arrayOf("A", "B"), // 0
                    arrayOf("A", "C", "D"), // 1
                    emptyArray<String>(), // 2
                    arrayOf("A", "B", "C", "D"), // 3
                    arrayOf("B", "D"), // 4
                    arrayOf("C", "D") // 5
                )) {
                    val doc = Document()
                    for (value in values) {
                        doc.add(StringField("foo", value, Store.NO))
                    }
                    w.addDocument(doc)
                }

                w.forceMerge(1)
            } finally {
                w.close()
            }

            val reader = DirectoryReader.open(dir)
            try {
                val searcher = newSearcher(reader)

                val query: Query =
                    BooleanQuery.Builder()
                        .add(
                            WANDScorerQuery(
                                BooleanQuery.Builder()
                                    .add(
                                        BoostQuery(
                                            ConstantScoreQuery(TermQuery(Term("foo", "A"))),
                                            2f
                                        ),
                                        Occur.SHOULD
                                    )
                                    .add(
                                        ConstantScoreQuery(TermQuery(Term("foo", "B"))),
                                        Occur.SHOULD
                                    )
                                    .add(
                                        BoostQuery(
                                            ConstantScoreQuery(TermQuery(Term("foo", "D"))),
                                            4f
                                        ),
                                        Occur.SHOULD
                                    )
                                    .setMinimumNumberShouldMatch(2)
                                    .build(),
                                random().nextBoolean()
                            ),
                            Occur.MUST
                        )
                        .add(TermQuery(Term("foo", "C")), Occur.MUST_NOT)
                        .build()

                val scorer =
                    searcher
                        .createWeight(searcher.rewrite(query), ScoreMode.COMPLETE_NO_SCORES, 1f)
                        .scorer(searcher.indexReader.leaves()[0])!!

                assertEquals(0, scorer.iterator().nextDoc())
                assertEquals(4, scorer.iterator().nextDoc())
                assertEquals(DocIdSetIterator.NO_MORE_DOCS, scorer.iterator().nextDoc())
            } finally {
                reader.close()
            }
        } finally {
            dir.close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun testRandom() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig())
        val numDocs = atLeast(1000)
        for (i in 0..<numDocs) {
            val doc = Document()
            val numValues = random().nextInt(1 shl random().nextInt(5))
            val start = random().nextInt(10)
            for (j in 0..<numValues) {
                doc.add(StringField("foo", (start + j).toString(), Store.NO))
            }
            w.addDocument(doc)
        }
        val reader = DirectoryReader.open(w)
        w.close()
        // turn off concurrent search to avoid Random object used across threads resulting into
        // RuntimeException, as WANDScorerQuery#createWeight has reference to this searcher,
        // but will be called during searching
        val searcher = newSearcher(reader, true, true, false)

        try {
            for (iter in 0..<100) {
                val start = random().nextInt(10)
                val numClauses = random().nextInt(1 shl random().nextInt(5))
                val builder = BooleanQuery.Builder()
                for (i in 0..<numClauses) {
                    builder.add(
                        maybeWrap(TermQuery(Term("foo", (start + i).toString()))),
                        Occur.SHOULD
                    )
                }
                val query: Query = WANDScorerQuery(builder.build(), random().nextBoolean())

                CheckHits.checkTopScores(random(), query, searcher)

                val filterTerm = random().nextInt(30)
                val filteredQuery: Query =
                    BooleanQuery.Builder()
                        .add(query, Occur.MUST)
                        .add(TermQuery(Term("foo", filterTerm.toString())), Occur.FILTER)
                        .build()

                CheckHits.checkTopScores(random(), filteredQuery, searcher)
            }
        } finally {
            reader.close()
            dir.close()
        }
    }

    /** Degenerate case: all clauses produce a score of 0. */
    @Test
    @Throws(IOException::class)
    fun testRandomWithZeroScores() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig())
        val numDocs = atLeast(1000)
        for (i in 0..<numDocs) {
            val doc = Document()
            val numValues = random().nextInt(1 shl random().nextInt(5))
            val start = random().nextInt(10)
            for (j in 0..<numValues) {
                doc.add(StringField("foo", (start + j).toString(), Store.NO))
            }
            w.addDocument(doc)
        }
        val reader = DirectoryReader.open(w)
        w.close()
        // turn off concurrent search to avoid Random object used across threads resulting into
        // RuntimeException, as WANDScorerQuery#createWeight has reference to this searcher,
        // but will be called during searching
        val searcher = newSearcher(reader, true, true, false)

        try {
            for (iter in 0..<100) {
                val start = random().nextInt(10)
                val numClauses = random().nextInt(1 shl random().nextInt(5))
                val builder = BooleanQuery.Builder()
                for (i in 0..<numClauses) {
                    builder.add(
                        maybeWrap(
                            BoostQuery(
                                ConstantScoreQuery(TermQuery(Term("foo", (start + i).toString()))),
                                0f
                            )
                        ),
                        Occur.SHOULD
                    )
                }
                val query: Query = WANDScorerQuery(builder.build(), random().nextBoolean())

                CheckHits.checkTopScores(random(), query, searcher)

                val filterTerm = random().nextInt(30)
                val filteredQuery: Query =
                    BooleanQuery.Builder()
                        .add(query, Occur.MUST)
                        .add(TermQuery(Term("foo", filterTerm.toString())), Occur.FILTER)
                        .build()

                CheckHits.checkTopScores(random(), filteredQuery, searcher)
            }
        } finally {
            reader.close()
            dir.close()
        }
    }

    /** Test the case when some clauses produce infinite max scores. */
    @Test
    @Throws(IOException::class)
    fun testRandomWithInfiniteMaxScore() {
        doTestRandomSpecialMaxScore(Float.POSITIVE_INFINITY)
    }

    /** Test the case when some clauses produce finite max scores, but their sum overflows. */
    @Test
    @Throws(IOException::class)
    fun testRandomWithMaxScoreOverflow() {
        doTestRandomSpecialMaxScore(Float.MAX_VALUE)
    }

    @Throws(IOException::class)
    private fun doTestRandomSpecialMaxScore(maxScore: Float) {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig())
        val numDocs = atLeast(1000)
        for (i in 0..<numDocs) {
            val doc = Document()
            val numValues = random().nextInt(1 shl random().nextInt(5))
            val start = random().nextInt(10)
            for (j in 0..<numValues) {
                doc.add(StringField("foo", (start + j).toString(), Store.NO))
            }
            w.addDocument(doc)
        }
        val reader = DirectoryReader.open(w)
        w.close()
        // turn off concurrent search to avoid Random object used across threads resulting into
        // RuntimeException, as WANDScorerQuery#createWeight has reference to this searcher,
        // but will be called during searching
        val searcher = newSearcher(reader, true, true, false)

        try {
            for (iter in 0..<100) {
                val start = random().nextInt(10)
                val numClauses = random().nextInt(1 shl random().nextInt(5))
                val builder = BooleanQuery.Builder()
                for (i in 0..<numClauses) {
                    var query: Query = TermQuery(Term("foo", (start + i).toString()))
                    if (random().nextBoolean()) {
                        query = MaxScoreWrapperQuery(
                            query,
                            numDocs / TestUtil.nextInt(random(), 1, 100),
                            maxScore
                        )
                    }
                    builder.add(query, Occur.SHOULD)
                }
                val query: Query = WANDScorerQuery(builder.build(), random().nextBoolean())

                CheckHits.checkTopScores(random(), query, searcher)

                val filterTerm = random().nextInt(30)
                val filteredQuery: Query =
                    BooleanQuery.Builder()
                        .add(query, Occur.MUST)
                        .add(TermQuery(Term("foo", filterTerm.toString())), Occur.FILTER)
                        .build()

                CheckHits.checkTopScores(random(), filteredQuery, searcher)
            }
        } finally {
            reader.close()
            dir.close()
        }
    }

    private class MaxScoreWrapperScorer(
        private val scorer: Scorer,
        private val maxRange: Int,
        private val maxScore: Float
    ) : Scorer() {
        private var lastShallowTarget = -1

        override fun docID(): Int {
            return scorer.docID()
        }

        override fun iterator(): DocIdSetIterator {
            return scorer.iterator()
        }

        override fun twoPhaseIterator(): TwoPhaseIterator? {
            return scorer.twoPhaseIterator()
        }

        @Throws(IOException::class)
        override fun score(): Float {
            return scorer.score()
        }

        override var minCompetitiveScore: Float
            get() = scorer.minCompetitiveScore
            set(value) {
                scorer.minCompetitiveScore = value
            }

        override val children: MutableCollection<Scorable.ChildScorable>
            get() = scorer.children

        @Throws(IOException::class)
        override fun advanceShallow(target: Int): Int {
            lastShallowTarget = target
            return scorer.advanceShallow(target)
        }

        @Throws(IOException::class)
        override fun getMaxScore(upTo: Int): Float {
            if (upTo - max(docID(), lastShallowTarget) >= maxRange) {
                return maxScore
            }
            return scorer.getMaxScore(upTo)
        }
    }

    private class MaxScoreWrapperQuery(
        private val query: Query,
        private val maxRange: Int,
        private val maxScore: Float
    ) : Query() {
        /**
         * If asked for the maximum score over a range of doc IDs that is greater than or equal to
         * maxRange, this query will return the provided maxScore.
         */

        override fun toString(field: String?): String {
            return query.toString(field)
        }

        override fun equals(obj: Any?): Boolean {
            if (!sameClassAs(obj)) {
                return false
            }
            val that = obj as MaxScoreWrapperQuery
            return query == that.query && maxRange == that.maxRange && maxScore == that.maxScore
        }

        override fun hashCode(): Int {
            var hash = classHash()
            hash = 31 * hash + query.hashCode()
            hash = 31 * hash + maxRange.hashCode()
            hash = 31 * hash + maxScore.hashCode()
            return hash
        }

        @Throws(Exception::class)
        override fun rewrite(indexSearcher: IndexSearcher): Query {
            val rewritten = query.rewrite(indexSearcher)
            if (rewritten !== query) {
                return MaxScoreWrapperQuery(rewritten, maxRange, maxScore)
            }
            return super.rewrite(indexSearcher)
        }

        override fun visit(visitor: QueryVisitor) {}

        @Throws(Exception::class)
        override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
            val weight = query.createWeight(searcher, scoreMode, boost)
            return object : Weight(this@MaxScoreWrapperQuery) {
                @Throws(IOException::class)
                override fun explain(context: LeafReaderContext, doc: Int): Explanation {
                    return weight.explain(context, doc)
                }

                @Throws(IOException::class)
                override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
                    val supplier = weight.scorerSupplier(context) ?: return null
                    return object : ScorerSupplier() {
                        @Throws(IOException::class)
                        override fun get(leadCost: Long): Scorer {
                            return MaxScoreWrapperScorer(supplier.get(leadCost)!!, maxRange, maxScore)
                        }

                        override fun cost(): Long {
                            return supplier.cost()
                        }
                    }
                }

                override fun isCacheable(ctx: LeafReaderContext): Boolean {
                    return weight.isCacheable(ctx)
                }
            }
        }
    }

    private class WANDScorerQuery(
        private val query: BooleanQuery,
        private val doBlocks: Boolean
    ) : Query() {
        init {
            assert(query.clauses().size == query.getClauses(Occur.SHOULD).size) {
                "This test utility query is only used to create WANDScorer for disjunctions."
            }
        }

        @Throws(Exception::class)
        override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
            return object : Weight(query) {
                @Throws(IOException::class)
                override fun explain(context: LeafReaderContext, doc: Int): Explanation {
                    // no-ops
                    throw UnsupportedOperationException()
                }

                @Throws(IOException::class)
                override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
                    val weight = query.createWeight(searcher, scoreMode, boost) as BooleanWeight
                    val optionalScorers: MutableList<Scorer> = ArrayList()
                    for (wc in weight.weightedClauses) {
                        val ss = wc.weight.scorerSupplier(context)
                        if (ss != null) {
                            optionalScorers.add(ss.get(Long.MAX_VALUE)!!)
                        }
                    }
                    val scorer: Scorer = if (optionalScorers.isNotEmpty()) {
                        WANDScorer(
                            optionalScorers,
                            this@WANDScorerQuery.query.minimumNumberShouldMatch,
                            scoreMode,
                            if (doBlocks) Long.MAX_VALUE else 0L
                        )
                    } else {
                        weight.scorer(context) ?: return null
                    }
                    return object : ScorerSupplier() {
                        @Throws(IOException::class)
                        override fun get(leadCost: Long): Scorer {
                            return scorer
                        }

                        override fun cost(): Long {
                            return scorer.iterator().cost()
                        }
                    }
                }

                override fun isCacheable(ctx: LeafReaderContext): Boolean {
                    return false
                }
            }
        }

        override fun toString(field: String?): String {
            return "WANDScorerQuery"
        }

        override fun visit(visitor: QueryVisitor) {
            // no-ops
        }

        override fun equals(other: Any?): Boolean {
            return sameClassAs(other) && query == (other as WANDScorerQuery).query
        }

        override fun hashCode(): Int {
            return 31 * classHash() + query.hashCode()
        }
    }
}

