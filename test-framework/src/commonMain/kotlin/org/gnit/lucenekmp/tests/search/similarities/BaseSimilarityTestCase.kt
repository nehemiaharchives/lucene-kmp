package org.gnit.lucenekmp.tests.search.similarities

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.IndexableField
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.isFinite
import org.gnit.lucenekmp.jdkport.isNaN
import org.gnit.lucenekmp.search.CollectionStatistics
import org.gnit.lucenekmp.search.Explanation
import org.gnit.lucenekmp.search.TermStatistics
import org.gnit.lucenekmp.search.similarities.IndriDirichletSimilarity
import org.gnit.lucenekmp.search.similarities.Similarity
import org.gnit.lucenekmp.search.similarities.Similarity.SimScorer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.CheckHits
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.SmallFloat
import kotlin.math.min
import kotlin.random.Random
import kotlin.test.DefaultAsserter.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Abstract class to do basic tests for a similarity. NOTE: This test focuses on the similarity
 * impl, nothing else. The [stretch] goal is for this test to be so thorough in testing a new
 * Similarity that if this test passes, then all Lucene tests should also pass. Ie, if there is some
 * bug in a given Similarity that this test fails to catch then this test needs to be improved!
 */
abstract class BaseSimilarityTestCase : LuceneTestCase() {
    /** Return a new similarity with all parameters randomized within valid ranges.  */
    protected abstract fun getSimilarity(random: Random): Similarity

    private inline fun <T> withSingleDocReader(
        omitNorms: Boolean,
        crossinline block: (LeafReader) -> T
    ): T {
        val dir = newDirectory()
        var reader: LeafReader? = null
        try {
            val writer = RandomIndexWriter(random(), dir)
            val doc = Document()

            val ft = FieldType(TextField.TYPE_NOT_STORED)
            ft.setOmitNorms(omitNorms)

            doc.add(newField("field", "value", ft))
            writer.addDocument(doc)

            reader = getOnlyLeafReader(writer.reader)
            writer.close()

            return block(reader)
        } finally {
            IOUtils.close(reader, dir)
        }
    }

    /**
     * Tests scoring across a bunch of random terms/corpora/frequencies for each possible document
     * length. It does the following checks:
     *
     *
     *  * scores are non-negative and finite.
     *  * score matches the explanation exactly.
     *  * internal explanations calculations are sane (e.g. sum of: and so on actually compute
     * sums)
     *  * scores don't decrease as term frequencies increase: e.g. score(freq=N + 1) &gt;=
     * score(freq=N)
     *  * scores don't decrease as documents get shorter, e.g. score(len=M) &gt;= score(len=M+1)
     *  * scores don't decrease as terms get rarer, e.g. score(term=N) &gt;= score(term=N+1)
     *  * scoring works for floating point frequencies (e.g. sloppy phrase and span queries will
     * work)
     *  * scoring works for reasonably large 64-bit statistic values (e.g. distributed search will
     * work)
     *  * scoring works for reasonably large boost values (0 .. Integer.MAX_VALUE, e.g. query
     * boosts will work)
     *  * scoring works for parameters randomized within valid ranges (see [       ][.getSimilarity])
     *
     */
    @Throws(Exception::class)
    open fun testRandomScoring() {
        val random: Random = random()
        val iterations: Int = atLeast(1)
        for (i in 0..<iterations) {
            // pull a new similarity to switch up parameters
            val similarity: Similarity = getSimilarity(random)
            for (j in 0..2) {
                // for each norm value...
                for (k in 1..255) {
                    val corpus: CollectionStatistics = newCorpus(random, k)
                    for (l in 0..9) {
                        val term: TermStatistics = newTerm(random, corpus)
                        val freq: Float
                        if (term.totalTermFreq == term.docFreq) {
                            // omit TF
                            freq = 1f
                        } else if (term.docFreq == 1L) {
                            // only one document, all the instances must be here.
                            freq = Math.toIntExact(term.totalTermFreq).toFloat()
                        } else {
                            // there is at least one other document, and those must have at least 1 instance each.
                            val upperBound: Int =
                                Math.toIntExact(
                                    min(
                                        term.totalTermFreq - term.docFreq + 1,
                                        Int.MAX_VALUE.toLong()
                                    )
                                )
                            if (random.nextBoolean()) {
                                // integer freq
                                when (random.nextInt(3)) {
                                    0 ->                     // smallest freq
                                        freq = 1f

                                    1 ->                     // largest freq
                                        freq = upperBound.toFloat()

                                    else ->                     // random freq
                                        freq = TestUtil.nextInt(
                                            random,
                                            1,
                                            upperBound
                                        ).toFloat()
                                }
                            } else {
                                // float freq
                                var freqCandidate: Float
                                when (random.nextInt(2)) {
                                    0 ->                     // smallest freq
                                        freqCandidate = Float.MIN_VALUE

                                    else ->                     // random freq
                                        freqCandidate = upperBound * random.nextFloat()
                                }
                                // we need to be 2nd float value at a minimum, the pairwise test will check
                                // MIN_VALUE in this case.
                                // this avoids testing frequencies of 0 which seem wrong to allow (we should enforce
                                // computeSlopFactor etc)
                                if (freqCandidate <= Float.MIN_VALUE) {
                                    freqCandidate = Math.nextUp(Float.MIN_VALUE)
                                }
                                freq = freqCandidate
                            }
                        }
                        // we just limit the test to "reasonable" boost values but don't enforce this anywhere.
                        // too big, and you are asking for overflow. that's hard for a sim to enforce (but
                        // definitely possible)
                        // for now, we just want to detect overflow where its a real bug/hazard in the
                        // computation with reasonable inputs.
                        val boost: Float
                        when (random.nextInt(5)) {
                            0 ->                 // minimum value (not enforced)
                                boost = 0f

                            1 ->                 // tiny value
                                boost = Float.MIN_VALUE

                            2 ->                 // no-op value (sometimes treated special in explanations)
                                boost = 1f

                            3 ->                 // maximum value (not enforceD)
                                boost = Int.MAX_VALUE.toFloat()

                            else ->                 // random value
                                boost = random.nextFloat() * Int.MAX_VALUE
                        }
                        doTestScoring(similarity, corpus, term, boost, freq, k)
                    }
                }
            }
        }
    }

    companion object {
        /*var READER: LeafReader? = null
        var DIR: Directory? = null*/

        // TODO need to figure out what to do with BeforeClass in common module
        /*@org.junit.BeforeClass*/
        /*@Throws(Exception::class)
        fun beforeClass() {
            // with norms
            /*DIR = LuceneTestCase.newDirectory()
            val writer =
                RandomIndexWriter(
                    random(),
                    DIR
                )
            val doc = Document()
            val fieldType: FieldType =
                FieldType(TextField.TYPE_NOT_STORED)
            fieldType.setOmitNorms(true)
            doc.add(
                LuceneTestCase.newField(
                    "field",
                    "value",
                    fieldType
                )
            )
            writer.addDocument<IndexableField>(doc)
            READER =
                LuceneTestCase.getOnlyLeafReader(writer.reader)
            writer.close()*/
        }*/

        // TODO need to figure out what to do with AfterClass in common module
        /*@org.junit.AfterClass*/
        /*@Throws(Exception::class)
        fun afterClass() {
            IOUtils.close(READER, DIR)
            READER = null
            DIR = null
        }*/

        const val MAXDOC_FORTESTING: Long = 1L shl 48

        // must be at least MAXDOC_FORTESTING + Integer.MAX_VALUE
        const val MAXTOKENS_FORTESTING: Long = 1L shl 49

        /**
         * returns a random corpus that is at least possible given the norm value for a single document.
         */
        fun newCorpus(
            random: Random,
            norm: Int
        ): CollectionStatistics {
            // lower bound of tokens in the collection (you produced this norm somehow)
            val lowerBound: Int
            if (norm == 0) {
                // norms are omitted, but there must have been at least one token to produce that norm
                lowerBound = 1
            } else {
                // minimum value that would decode to such a norm
                lowerBound = SmallFloat.byte4ToInt(norm.toByte())
            }
            val maxDoc: Long
            when (random.nextInt(6)) {
                0 ->         // 1 doc collection
                    maxDoc = 1

                1 ->         // 2 doc collection
                    maxDoc = 2

                2 ->         // tiny collection
                    maxDoc = TestUtil.nextLong(random, 3, 16)

                3 ->         // small collection
                    maxDoc = TestUtil.nextLong(random, 16, 100000)

                4 ->         // big collection
                    maxDoc = TestUtil.nextLong(
                        random,
                        100000,
                        MAXDOC_FORTESTING
                    )

                else ->         // yuge collection
                    maxDoc = MAXDOC_FORTESTING
            }
            val docCount: Long
            when (random.nextInt(3)) {
                0 ->         // sparsest field
                    docCount = 1

                1 ->         // sparse field
                    docCount = TestUtil.nextLong(random, 1, maxDoc)

                else ->         // fully populated
                    docCount = maxDoc
            }
            // random docsize: but can't require docs to have > 2B tokens
            var upperBound: Long
            try {
                upperBound = min(
                    MAXTOKENS_FORTESTING,
                    Math.multiplyExact(docCount, Int.MAX_VALUE.toLong())
                )
            } catch (overflow: ArithmeticException) {
                upperBound = MAXTOKENS_FORTESTING
            }
            val sumDocFreq: Long
            when (random.nextInt(3)) {
                0 ->         // shortest possible docs
                    sumDocFreq = docCount

                1 ->         // biggest possible docs
                    sumDocFreq = upperBound + 1 - lowerBound

                else ->         // random docsize
                    sumDocFreq = TestUtil.nextLong(
                        random,
                        docCount,
                        upperBound + 1 - lowerBound
                    )
            }
            val sumTotalTermFreq: Long
            when (random.nextInt(4)) {
                0 ->         // term frequencies were omitted
                    sumTotalTermFreq = sumDocFreq

                1 ->         // no repetition of terms (except to satisfy this norm)
                    sumTotalTermFreq = sumDocFreq - 1 + lowerBound

                2 ->         // maximum repetition of terms
                    sumTotalTermFreq = upperBound

                else -> {
                    // random repetition
                    assert(sumDocFreq - 1 + lowerBound <= upperBound)
                    sumTotalTermFreq = TestUtil.nextLong(
                        random,
                        sumDocFreq - 1 + lowerBound,
                        upperBound
                    )
                }
            }
            return CollectionStatistics(
                "field",
                maxDoc,
                docCount,
                sumTotalTermFreq,
                sumDocFreq
            )
        }

        private val TERM: BytesRef = BytesRef("term")

        /** returns new random term, that fits within the bounds of the corpus  */
        fun newTerm(
            random: Random,
            corpus: CollectionStatistics
        ): TermStatistics {
            val docFreq: Long
            when (random.nextInt(3)) {
                0 ->         // rare term
                    docFreq = 1

                1 ->         // common term
                    docFreq = corpus.docCount

                else ->         // random specificity
                    docFreq =
                        TestUtil.nextLong(random, 1, corpus.docCount)
            }
            val totalTermFreq: Long
            // can't require docs to have > 2B tokens
            var upperBound: Long
            try {
                upperBound = min(
                    corpus.sumTotalTermFreq,
                    Math.multiplyExact(docFreq, Int.MAX_VALUE.toLong())
                )
            } catch (overflow: ArithmeticException) {
                upperBound = corpus.sumTotalTermFreq
            }
            if (corpus.sumTotalTermFreq == corpus.sumDocFreq) {
                // omitTF
                totalTermFreq = docFreq
            } else {
                when (random.nextInt(3)) {
                    0 ->           // no repetition
                        totalTermFreq = docFreq

                    1 ->           // maximum repetition
                        totalTermFreq = upperBound

                    else ->           // random repetition
                        totalTermFreq = TestUtil.nextLong(
                            random,
                            docFreq,
                            upperBound
                        )
                }
            }
            return TermStatistics(TERM, docFreq, totalTermFreq)
        }

        /**
         * runs for a single test case, so that if you hit a test failure you can write a reproducer just
         * for that scenario
         */
        @Throws(IOException::class)
        private fun doTestScoring(
            similarity: Similarity,
            corpus: CollectionStatistics,
            term: TermStatistics,
            boost: Float,
            freq: Float,
            norm: Int
        ) {
            var success = false
            val scorer: SimScorer =
                similarity.scorer(boost, corpus, term)
            try {
                val maxScore: Float = scorer.score(Float.MAX_VALUE, 1)
                assertFalse(Float.isNaN(maxScore), message = "maxScore is NaN")

                val score: Float = scorer.score(freq, norm.toLong())
                // check that score isn't infinite or negative
                assertTrue(
                    "infinite/NaN score: $score",
                    Float.isFinite(score)
                )
                if (similarity !is IndriDirichletSimilarity) {
                    assertTrue("negative score: $score", score >= 0)
                }
                assertTrue(
                    "greater than maxScore: $score>$maxScore",
                    score <= maxScore
                )
                // check explanation matches
                val explanation: Explanation =
                    scorer.explain(
                        Explanation.match(
                            freq,
                            "freq, occurrences of term within document"
                        ), norm.toLong()
                    )
                if (score.toDouble() != explanation.value.toDouble()) {
                    fail("expected: $score, got: $explanation")
                }
                if (rarely()) {
                    CheckHits.verifyExplanation(
                        "<test query>",
                        0,
                        score,
                        true,
                        explanation
                    )
                }

                // check score(freq-1), given the same norm it should be <= score(freq) [scores non-decreasing
                // for more term occurrences]
                val prevFreq: Float
                if (random()
                        .nextBoolean() && freq == freq.toInt()
                        .toFloat() && freq > 1 && term.docFreq > 1
                ) {
                    // previous in integer space
                    prevFreq = freq - 1
                } else {
                    // previous in float space (e.g. for sloppyPhrase)
                    prevFreq = Math.nextDown(freq)
                }

                val prevScore: Float = scorer.score(prevFreq, norm.toLong())
                // check that score isn't infinite or negative
                assertTrue(Float.isFinite(prevScore))
                if (similarity !is IndriDirichletSimilarity) {
                    assertTrue(prevScore >= 0)
                }
                // check explanation matches
                val prevExplanation: Explanation =
                    scorer.explain(
                        Explanation.match(
                            prevFreq,
                            "freq, occurrences of term within document"
                        ), norm.toLong()
                    )
                if (prevScore.toDouble() != prevExplanation.value.toDouble()) {
                    fail("expected: $prevScore, got: $prevExplanation")
                }
                if (rarely()) {
                    CheckHits.verifyExplanation(
                        "test query (prevFreq)",
                        0,
                        prevScore,
                        true,
                        prevExplanation
                    )
                }

                if (prevScore > score) {
                    println(prevExplanation)
                    println(explanation)
                    fail("score($prevFreq)=$prevScore > score($freq)=$score")
                }

                // check score(norm-1), given the same freq it should be >= score(norm) [scores non-decreasing
                // as docs get shorter]
                if (norm > 1) {
                    val prevNormScore: Float = scorer.score(freq, (norm - 1).toLong())
                    // check that score isn't infinite or negative
                    assertTrue(Float.isFinite(prevNormScore))
                    if (similarity !is IndriDirichletSimilarity) {
                        assertTrue(prevNormScore >= 0)
                    }
                    // check explanation matches
                    val prevNormExplanation: Explanation =
                        scorer.explain(
                            Explanation.match(
                                freq,
                                "freq, occurrences of term within document"
                            ), (norm - 1).toLong()
                        )
                    if (prevNormScore.toDouble() != prevNormExplanation.value.toDouble()) {
                        fail("expected: $prevNormScore, got: $prevNormExplanation")
                    }
                    if (rarely()) {
                        CheckHits.verifyExplanation(
                            "test query (prevNorm)", 0, prevNormScore, true, prevNormExplanation
                        )
                    }
                    if (prevNormScore < score) {
                        println(prevNormExplanation)
                        println(explanation)
                        fail(
                            ("score("
                                    + freq
                                    + ","
                                    + (norm - 1)
                                    + ")="
                                    + prevNormScore
                                    + " < score("
                                    + freq
                                    + ","
                                    + norm
                                    + ")="
                                    + score)
                        )
                    }
                }

                // check score(term-1), given the same freq/norm it should be >= score(term) [scores
                // non-decreasing as terms get rarer]
                if (term.docFreq > 1 && freq < term.totalTermFreq) {
                    val prevTerm = TermStatistics(
                            term.term,
                            term.docFreq - 1,
                            term.totalTermFreq - 1
                        )
                    val prevTermScorer: SimScorer = similarity.scorer(boost, corpus, term)
                    val prevTermScore: Float = prevTermScorer.score(freq, norm.toLong())
                    // check that score isn't infinite or negative
                    assertTrue(Float.isFinite(prevTermScore))
                    if (similarity !is IndriDirichletSimilarity) {
                        assertTrue(prevTermScore >= 0)
                    }
                    // check explanation matches
                    val prevTermExplanation: Explanation =
                        prevTermScorer.explain(
                            Explanation.match(
                                freq,
                                "freq, occurrences of term within document"
                            ), norm.toLong()
                        )
                    if (prevTermScore.toDouble() != prevTermExplanation.value.toDouble()) {
                        fail("expected: $prevTermScore, got: $prevTermExplanation")
                    }
                    if (rarely()) {
                        CheckHits.verifyExplanation(
                            "test query (prevTerm)", 0, prevTermScore, true, prevTermExplanation
                        )
                    }

                    if (prevTermScore < score) {
                        println(prevTermExplanation)
                        println(explanation)
                        fail(
                            ("score("
                                    + freq
                                    + ","
                                    + (prevTerm)
                                    + ")="
                                    + prevTermScore
                                    + " < score("
                                    + freq
                                    + ","
                                    + term
                                    + ")="
                                    + score)
                        )
                    }
                }

                success = true
            } finally {
                if (!success) {
                    println(similarity)
                    println(corpus)
                    println(term)
                    if (norm == 0) {
                        println("norms=omitted")
                    } else {
                        println(
                            "norm=$norm (doc length ~ " + SmallFloat.byte4ToInt(
                                norm.toByte()
                            ) + ")"
                        )
                    }
                    println("freq=$freq")
                }
            }
        }
    }
}
