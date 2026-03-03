package org.gnit.lucenekmp.search.similarities

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.FieldInvertState
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.CollectionStatistics
import org.gnit.lucenekmp.search.Explanation
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.search.TermStatistics
import org.gnit.lucenekmp.search.TopDocs
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.SmallFloat
import org.gnit.lucenekmp.util.Version
import kotlin.math.ln
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests the [SimilarityBase]-based Similarities. Contains unit tests and integration tests
 * for all Similarities and correctness tests for a select few.
 *
 * This class maintains a list of `SimilarityBase` subclasses. Each test case performs its
 * test on all items in the list. If a test case fails, the name of the Similarity that caused the
 * failure is returned as part of the assertion error message.
 *
 * Unit testing is performed by constructing statistics manually and calling the
 * [SimilarityBase.score] method of the Similarities. The statistics
 * represent corner cases of corpus distributions.
 *
 * For the integration tests, a small (8-document) collection is indexed. The tests verify that
 * for a specific query, all relevant documents are returned in the correct order. The collection
 * consists of two poems of English poet [William Blake](http://en.wikipedia.org/wiki/William_blake).
 *
 * Note: the list of Similarities is maintained by hand. If a new Similarity is added to the
 * `org.apache.lucene.search.similarities` package, the list should be updated accordingly.
 *
 * In the correctness tests, the score is verified against the result of manual computation.
 * Since it would be impossible to test all Similarities (e.g. all possible DFR combinations, all
 * parameter values for LM), only the best performing setups in the original papers are verified.
 */
class TestSimilarityBase : LuceneTestCase() {
    private var searcher: IndexSearcher? = null
    private var dir: Directory? = null
    private var reader: IndexReader? = null

    /** The list of similarities to test. */
    private lateinit var sims: MutableList<SimilarityBase>

    @BeforeTest
    @Throws(Exception::class)
    fun setUp() {
        dir = newDirectory()
        val writer = RandomIndexWriter(random(), dir!!)

        for (i in docs.indices) {
            val d = Document()
            val ft = FieldType(TextField.TYPE_STORED)
            ft.setIndexOptions(IndexOptions.NONE)
            d.add(newField(FIELD_ID, i.toString(), ft))
            d.add(newTextField(FIELD_BODY, docs[i], Field.Store.YES))
            writer.addDocument(d)
        }

        reader = writer.reader
        searcher = newSearcher(reader!!)
        writer.close()

        sims = mutableListOf()
        for (basicModel in BASIC_MODELS) {
            for (afterEffect in AFTER_EFFECTS) {
                for (normalization in NORMALIZATIONS) {
                    sims.add(DFRSimilarity(basicModel, afterEffect, normalization))
                }
            }
        }
        for (distribution in DISTRIBUTIONS) {
            for (lambda in LAMBDAS) {
                for (normalization in NORMALIZATIONS) {
                    sims.add(IBSimilarity(distribution, lambda, normalization))
                }
            }
        }
        sims.add(LMDirichletSimilarity())
        sims.add(LMJelinekMercerSimilarity(0.1f))
        sims.add(LMJelinekMercerSimilarity(0.7f))
        for (independence in INDEPENDENCE_MEASURES) {
            sims.add(DFISimilarity(independence))
        }
    }

    /** Creates the default statistics object that the specific tests modify. */
    private fun createStats(): BasicStats {
        val stats = BasicStats("spoof", 1.0)
        stats.numberOfDocuments = NUMBER_OF_DOCUMENTS.toLong()
        stats.numberOfFieldTokens = NUMBER_OF_FIELD_TOKENS
        stats.avgFieldLength = AVG_FIELD_LENGTH.toDouble()
        stats.docFreq = DOC_FREQ.toLong()
        stats.totalTermFreq = TOTAL_TERM_FREQ
        return stats
    }

    private fun toCollectionStats(stats: BasicStats): CollectionStatistics {
        val sumTtf = stats.numberOfFieldTokens
        val sumDf =
            if (sumTtf == -1L) {
                TestUtil.nextLong(random(), stats.numberOfDocuments, 2L * stats.numberOfDocuments)
            } else {
                TestUtil.nextLong(random(), minOf(stats.numberOfDocuments, sumTtf), sumTtf)
            }
        val docCount = minOf(sumDf, stats.numberOfDocuments).toInt()
        val maxDoc = TestUtil.nextInt(random(), docCount, docCount + 10)
        return CollectionStatistics(stats.field, maxDoc.toLong(), docCount.toLong(), sumTtf, sumDf)
    }

    private fun toTermStats(stats: BasicStats): TermStatistics {
        return TermStatistics(BytesRef("spoofyText"), stats.docFreq, stats.totalTermFreq)
    }

    /**
     * The generic test core called by all unit test methods. It calls the
     * [SimilarityBase.score] method of all Similarities in [sims]
     * and checks if the score is valid; i.e. it is a finite positive real number.
     */
    private fun unitTestCore(stats: BasicStats, freq: Float, docLen: Int) {
        for (sim in sims) {
            val scorer = sim.scorer(stats.boost.toFloat(), toCollectionStats(stats), toTermStats(stats))
            val norm = SmallFloat.intToByte4(docLen).toLong()
            val score = scorer.score(freq, norm)
            val explScore = scorer.explain(Explanation.match(freq, "freq"), norm).value.toFloat()
            assertFalse(score.isInfinite(), "Score infinite: $sim")
            assertFalse(score.isNaN(), "Score NaN: $sim")
            assertTrue(score >= 0, "Score negative: $sim")
            assertEquals(score, explScore, FLOAT_EPSILON, "score() and explain() return different values: $sim")
        }
    }

    /** Runs the unit test with the default statistics. */
    @Test
    @Throws(IOException::class)
    fun testDefault() {
        unitTestCore(createStats(), FREQ, DOC_LEN)
    }

    /** Tests correct behavior when `numberOfDocuments = numberOfFieldTokens`. */
    @Test
    @Throws(IOException::class)
    fun testSparseDocuments() {
        val stats = createStats()
        stats.numberOfFieldTokens = stats.numberOfDocuments
        stats.totalTermFreq = stats.docFreq
        stats.avgFieldLength = stats.numberOfFieldTokens.toDouble() / stats.numberOfDocuments
        unitTestCore(stats, FREQ, DOC_LEN)
    }

    /** Tests correct behavior when `numberOfDocuments > numberOfFieldTokens`. */
    @Test
    @Throws(IOException::class)
    fun testVerySparseDocuments() {
        val stats = createStats()
        stats.numberOfFieldTokens = stats.numberOfDocuments * 2 / 3
        stats.totalTermFreq = stats.docFreq
        stats.avgFieldLength = stats.numberOfFieldTokens.toDouble() / stats.numberOfDocuments
        unitTestCore(stats, FREQ, DOC_LEN)
    }

    /** Tests correct behavior when `NumberOfDocuments = 1`. */
    @Test
    @Throws(IOException::class)
    fun testOneDocument() {
        val stats = createStats()
        stats.numberOfDocuments = 1
        stats.numberOfFieldTokens = DOC_LEN.toLong()
        stats.avgFieldLength = DOC_LEN.toDouble()
        stats.docFreq = 1
        stats.totalTermFreq = FREQ.toLong()
        unitTestCore(stats, FREQ, DOC_LEN)
    }

    /** Tests correct behavior when `docFreq = numberOfDocuments`. */
    @Test
    @Throws(IOException::class)
    fun testAllDocumentsRelevant() {
        val stats = createStats()
        val mult = (0.0f + stats.numberOfDocuments) / stats.docFreq
        stats.totalTermFreq = (stats.totalTermFreq * mult).toLong()
        stats.docFreq = stats.numberOfDocuments
        unitTestCore(stats, FREQ, DOC_LEN)
    }

    /** Tests correct behavior when `docFreq > numberOfDocuments / 2`. */
    @Test
    @Throws(IOException::class)
    fun testMostDocumentsRelevant() {
        val stats = createStats()
        val mult = (0.6f * stats.numberOfDocuments) / stats.docFreq
        stats.totalTermFreq = (stats.totalTermFreq * mult).toLong()
        stats.docFreq = (stats.numberOfDocuments * 0.6).toLong()
        unitTestCore(stats, FREQ, DOC_LEN)
    }

    /** Tests correct behavior when `docFreq = 1`. */
    @Test
    @Throws(IOException::class)
    fun testOnlyOneRelevantDocument() {
        val stats = createStats()
        stats.docFreq = 1
        stats.totalTermFreq = FREQ.toLong() + 3
        unitTestCore(stats, FREQ, DOC_LEN)
    }

    /** Tests correct behavior when `totalTermFreq = numberOfFieldTokens`. */
    @Test
    @Throws(IOException::class)
    fun testAllTermsRelevant() {
        val stats = createStats()
        stats.totalTermFreq = stats.numberOfFieldTokens
        unitTestCore(stats, DOC_LEN.toFloat(), DOC_LEN)
        stats.avgFieldLength = (DOC_LEN + 10).toDouble()
        unitTestCore(stats, DOC_LEN.toFloat(), DOC_LEN)
    }

    /** Tests correct behavior when `totalTermFreq > numberOfDocuments`. */
    @Test
    @Throws(IOException::class)
    fun testMoreTermsThanDocuments() {
        val stats = createStats()
        stats.totalTermFreq = stats.totalTermFreq + stats.numberOfDocuments
        unitTestCore(stats, 2 * FREQ, DOC_LEN)
    }

    /** Tests correct behavior when `totalTermFreq = numberOfDocuments`. */
    @Test
    @Throws(IOException::class)
    fun testNumberOfTermsAsDocuments() {
        val stats = createStats()
        stats.totalTermFreq = stats.numberOfDocuments
        unitTestCore(stats, FREQ, DOC_LEN)
    }

    /** Tests correct behavior when `totalTermFreq = 1`. */
    @Test
    @Throws(IOException::class)
    fun testOneTerm() {
        val stats = createStats()
        stats.docFreq = 1
        stats.totalTermFreq = 1
        unitTestCore(stats, 1f, DOC_LEN)
    }

    /** Tests correct behavior when `totalTermFreq = freq`. */
    @Test
    @Throws(IOException::class)
    fun testOneRelevantDocument() {
        val stats = createStats()
        stats.docFreq = 1
        stats.totalTermFreq = FREQ.toLong()
        unitTestCore(stats, FREQ, DOC_LEN)
    }

    /** Tests correct behavior when `numberOfFieldTokens = freq`. */
    @Test
    @Throws(IOException::class)
    fun testAllTermsRelevantOnlyOneDocument() {
        val stats = createStats()
        stats.numberOfDocuments = 10
        stats.numberOfFieldTokens = 50
        stats.avgFieldLength = 5.0
        stats.docFreq = 1
        stats.totalTermFreq = 50
        unitTestCore(stats, 50f, 50)
    }

    /**
     * Tests correct behavior when there is only one document with a single term in the collection.
     */
    @Test
    @Throws(IOException::class)
    fun testOnlyOneTermOneDocument() {
        val stats = createStats()
        stats.numberOfDocuments = 1
        stats.numberOfFieldTokens = 1
        stats.avgFieldLength = 1.0
        stats.docFreq = 1
        stats.totalTermFreq = 1
        unitTestCore(stats, 1f, 1)
    }

    /**
     * Tests correct behavior when there is only one term in the field, but more than one documents.
     */
    @Test
    @Throws(IOException::class)
    fun testOnlyOneTerm() {
        val stats = createStats()
        stats.numberOfFieldTokens = 1
        stats.avgFieldLength = 1.0 / stats.numberOfDocuments
        stats.docFreq = 1
        stats.totalTermFreq = 1
        unitTestCore(stats, 1f, DOC_LEN)
    }

    /** Tests correct behavior when `avgFieldLength = docLen`. */
    @Test
    @Throws(IOException::class)
    fun testDocumentLengthAverage() {
        val stats = createStats()
        unitTestCore(stats, FREQ, stats.avgFieldLength.toInt())
    }

    // ---------------------------- Correctness tests ----------------------------

    /** Correctness test for the Dirichlet LM model. */
    @Test
    @Throws(IOException::class)
    fun testLMDirichlet() {
        val p = (FREQ + 2000.0f * (TOTAL_TERM_FREQ + 1) / (NUMBER_OF_FIELD_TOKENS + 1.0f)) / (DOC_LEN + 2000.0f)
        val a = 2000.0f / (DOC_LEN + 2000.0f)
        val gold = (ln((p / (a * (TOTAL_TERM_FREQ + 1) / (NUMBER_OF_FIELD_TOKENS + 1.0f))).toDouble()) + ln(a.toDouble())).toFloat()
        correctnessTestCore(LMDirichletSimilarity(), gold)
    }

    /** Correctness test for the Jelinek-Mercer LM model. */
    @Test
    @Throws(IOException::class)
    fun testLMJelinekMercer() {
        val p = (1 - 0.1f) * FREQ / DOC_LEN + 0.1f * (TOTAL_TERM_FREQ + 1) / (NUMBER_OF_FIELD_TOKENS + 1.0f)
        val gold = ln((p / (0.1f * (TOTAL_TERM_FREQ + 1) / (NUMBER_OF_FIELD_TOKENS + 1.0f))).toDouble()).toFloat()
        correctnessTestCore(LMJelinekMercerSimilarity(0.1f), gold)
    }

    /** Correctness test for the LL IB model with DF-based lambda and no normalization. */
    @Test
    @Throws(IOException::class)
    fun testLLForIB() {
        val sim: SimilarityBase = IBSimilarity(DistributionLL(), LambdaDF(), Normalization.NoNormalization())
        correctnessTestCore(sim, 4.178574562072754f)
    }

    /** Correctness test for the SPL IB model with TTF-based lambda and no normalization. */
    @Test
    @Throws(IOException::class)
    fun testSPLForIB() {
        val sim: SimilarityBase = IBSimilarity(DistributionSPL(), LambdaTTF(), Normalization.NoNormalization())
        correctnessTestCore(sim, 2.2387237548828125f)
    }

    /** Correctness test for the IneB2 DFR model. */
    @Test
    @Throws(IOException::class)
    fun testIneB2() {
        val sim: SimilarityBase = DFRSimilarity(BasicModelIne(), AfterEffectB(), NormalizationH2())
        correctnessTestCore(sim, 5.747603416442871f)
    }

    /** Correctness test for the GL1 DFR model. */
    @Test
    @Throws(IOException::class)
    fun testGL1() {
        val sim: SimilarityBase = DFRSimilarity(BasicModelG(), AfterEffectL(), NormalizationH1())
        correctnessTestCore(sim, 1.6390540599822998f)
    }

    /** Correctness test for the In2 DFR model with no aftereffect. */
    @Test
    @Throws(IOException::class)
    fun testIn2() {
        val sim: SimilarityBase = DFRSimilarity(BasicModelIn(), AfterEffectL(), NormalizationH2())
        val tfn = (FREQ * SimilarityBase.log2(1.0 + AVG_FIELD_LENGTH.toDouble() / DOC_LEN)).toFloat()
        val gold = (tfn * SimilarityBase.log2((NUMBER_OF_DOCUMENTS + 1.0) / (DOC_FREQ + 0.5)) / (1 + tfn)).toFloat()
        correctnessTestCore(sim, gold)
    }

    /** Correctness test for the IFB DFR model with no normalization. */
    @Test
    @Throws(IOException::class)
    fun testIFB() {
        val sim: SimilarityBase = DFRSimilarity(BasicModelIF(), AfterEffectB(), Normalization.NoNormalization())
        val b = (TOTAL_TERM_FREQ + 1 + 1).toFloat() / ((DOC_FREQ + 1) * (FREQ + 1))
        val `if` = (FREQ * SimilarityBase.log2(1 + (NUMBER_OF_DOCUMENTS + 1.0) / (TOTAL_TERM_FREQ + 0.5))).toFloat()
        val gold = b * `if`
        correctnessTestCore(sim, gold)
    }

    /**
     * The generic test core called by all correctness test methods. It calls the
     * [SimilarityBase.score] method and compares the score against the manually computed `gold`.
     */
    private fun correctnessTestCore(sim: SimilarityBase, gold: Float) {
        val stats = createStats()
        val scorer = sim.scorer(stats.boost.toFloat(), toCollectionStats(stats), toTermStats(stats))
        val norm = SmallFloat.intToByte4(DOC_LEN).toLong()
        val score = scorer.score(FREQ, norm)
        assertEquals(gold, score, FLOAT_EPSILON, "${sim} score not correct.")
    }

    // ---------------------------- Integration tests ----------------------------

    /** Tests whether all similarities return three documents for the query word "heart". */
    @Test
    @Throws(IOException::class)
    fun testHeartList() {
        val q: Query = TermQuery(Term(FIELD_BODY, "heart"))
        for (sim in sims) {
            searcher!!.similarity = sim
            val topDocs: TopDocs = searcher!!.search(q, 1000)
            assertEquals(3, topDocs.totalHits.value, "Failed: $sim")
        }
    }

    /** Test whether all similarities return document 3 before documents 7 and 8. */
    @Test
    @Throws(IOException::class)
    fun testHeartRanking() {
        val q: Query = TermQuery(Term(FIELD_BODY, "heart"))
        for (sim in sims) {
            searcher!!.similarity = sim
            val topDocs: TopDocs = searcher!!.search(q, 1000)
            assertEquals("2", reader!!.storedFields().document(topDocs.scoreDocs[0].doc).get(FIELD_ID), "Failed: $sim")
        }
    }

    // LUCENE-5221
    @Test
    @Throws(IOException::class)
    fun testDiscountOverlapsBoost() {
        val expected0 = BM25Similarity(false)
        val actual0: SimilarityBase = DFRSimilarity(BasicModelIne(), AfterEffectB(), NormalizationH2(), false)
        val state = FieldInvertState(Version.LATEST.major, "foo", IndexOptions.DOCS_AND_FREQS)
        state.length = 5
        state.numOverlap = 2
        assertEquals(expected0.computeNorm(state), actual0.computeNorm(state))
        val expected1 = BM25Similarity(true)
        val actual1: SimilarityBase = DFRSimilarity(BasicModelIne(), AfterEffectB(), NormalizationH2(), true)
        assertEquals(expected1.computeNorm(state), actual1.computeNorm(state))
    }

    @AfterTest
    @Throws(Exception::class)
    fun tearDown() {
        reader?.close()
        dir?.close()
    }

    companion object {
        private const val FIELD_BODY = "body"
        private const val FIELD_ID = "id"

        /** The tolerance range for float equality. */
        private const val FLOAT_EPSILON = 1e-5f

        /** The DFR basic models to test. */
        val BASIC_MODELS: Array<BasicModel> = arrayOf(BasicModelG(), BasicModelIF(), BasicModelIn(), BasicModelIne())

        /** The DFR aftereffects to test. */
        val AFTER_EFFECTS: Array<AfterEffect> = arrayOf(AfterEffectB(), AfterEffectL())

        /** The DFR normalizations to test. */
        val NORMALIZATIONS: Array<Normalization> = arrayOf(
            NormalizationH1(),
            NormalizationH2(),
            NormalizationH3(),
            NormalizationZ(),
            Normalization.NoNormalization()
        )

        /** The distributions for IB. */
        val DISTRIBUTIONS: Array<Distribution> = arrayOf(DistributionLL(), DistributionSPL())

        /** Lambdas for IB. */
        val LAMBDAS: Array<Lambda> = arrayOf(LambdaDF(), LambdaTTF())

        /** Independence measures for DFI */
        val INDEPENDENCE_MEASURES: Array<Independence> = arrayOf(
            IndependenceStandardized(),
            IndependenceSaturated(),
            IndependenceChiSquared()
        )

        /** The default number of documents in the unit tests. */
        private const val NUMBER_OF_DOCUMENTS = 100

        /** The default total number of tokens in the field in the unit tests. */
        private const val NUMBER_OF_FIELD_TOKENS = 5000L

        /** The default average field length in the unit tests. */
        private const val AVG_FIELD_LENGTH = 50f

        /** The default document frequency in the unit tests. */
        private const val DOC_FREQ = 10

        /** The default total number of occurrences of this term across all documents in the unit tests. */
        private const val TOTAL_TERM_FREQ = 70L

        /** The default tf in the unit tests. */
        private const val FREQ = 7f

        /** The default document length in the unit tests. */
        private const val DOC_LEN = 40
    }

    /** The "collection" for the integration tests. */
    private val docs = arrayOf(
        "Tiger, tiger burning bright   In the forest of the night   What immortal hand or eye   Could frame thy fearful symmetry ?",
        "In what distant depths or skies   Burnt the fire of thine eyes ?   On what wings dare he aspire ?   What the hands the seize the fire ?",
        "And what shoulder and what art   Could twist the sinews of thy heart ?   And when thy heart began to beat What dread hand ? And what dread feet ?",
        "What the hammer? What the chain ?   In what furnace was thy brain ?   What the anvil ? And what dread grasp   Dare its deadly terrors clasp ?",
        "And when the stars threw down their spears   And water'd heaven with their tear   Did he smile his work to see ?   Did he, who made the lamb, made thee ?",
        "Tiger, tiger burning bright   In the forest of the night   What immortal hand or eye   Dare frame thy fearful symmetry ?",
        "Cruelty has a human heart   And jealousy a human face   Terror the human form divine   And Secrecy the human dress .",
        "The human dress is forg'd iron   The human form a fiery forge   The human face a furnace seal'd   The human heart its fiery gorge ."
    )
}
