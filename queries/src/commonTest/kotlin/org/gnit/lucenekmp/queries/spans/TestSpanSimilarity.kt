package org.gnit.lucenekmp.queries.spans

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.TopDocs
import org.gnit.lucenekmp.search.similarities.AfterEffect
import org.gnit.lucenekmp.search.similarities.AfterEffectB
import org.gnit.lucenekmp.search.similarities.AfterEffectL
import org.gnit.lucenekmp.search.similarities.AxiomaticF1EXP
import org.gnit.lucenekmp.search.similarities.AxiomaticF1LOG
import org.gnit.lucenekmp.search.similarities.AxiomaticF2EXP
import org.gnit.lucenekmp.search.similarities.AxiomaticF2LOG
import org.gnit.lucenekmp.search.similarities.AxiomaticF3EXP
import org.gnit.lucenekmp.search.similarities.AxiomaticF3LOG
import org.gnit.lucenekmp.search.similarities.BM25Similarity
import org.gnit.lucenekmp.search.similarities.BasicModel
import org.gnit.lucenekmp.search.similarities.BasicModelG
import org.gnit.lucenekmp.search.similarities.BasicModelIF
import org.gnit.lucenekmp.search.similarities.BasicModelIn
import org.gnit.lucenekmp.search.similarities.BasicModelIne
import org.gnit.lucenekmp.search.similarities.BooleanSimilarity
import org.gnit.lucenekmp.search.similarities.ClassicSimilarity
import org.gnit.lucenekmp.search.similarities.DFISimilarity
import org.gnit.lucenekmp.search.similarities.DFRSimilarity
import org.gnit.lucenekmp.search.similarities.Distribution
import org.gnit.lucenekmp.search.similarities.DistributionLL
import org.gnit.lucenekmp.search.similarities.DistributionSPL
import org.gnit.lucenekmp.search.similarities.IBSimilarity
import org.gnit.lucenekmp.search.similarities.Independence
import org.gnit.lucenekmp.search.similarities.IndependenceChiSquared
import org.gnit.lucenekmp.search.similarities.IndependenceSaturated
import org.gnit.lucenekmp.search.similarities.IndependenceStandardized
import org.gnit.lucenekmp.search.similarities.LMDirichletSimilarity
import org.gnit.lucenekmp.search.similarities.LMJelinekMercerSimilarity
import org.gnit.lucenekmp.search.similarities.Lambda
import org.gnit.lucenekmp.search.similarities.LambdaDF
import org.gnit.lucenekmp.search.similarities.LambdaTTF
import org.gnit.lucenekmp.search.similarities.Normalization
import org.gnit.lucenekmp.search.similarities.NormalizationH1
import org.gnit.lucenekmp.search.similarities.NormalizationH2
import org.gnit.lucenekmp.search.similarities.NormalizationH3
import org.gnit.lucenekmp.search.similarities.NormalizationZ
import org.gnit.lucenekmp.search.similarities.Similarity
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TestSpanSimilarity : LuceneTestCase() {
    private lateinit var sims: MutableList<Similarity>

    companion object {
        /** The DFR basic models to test. */
        private val BASIC_MODELS: Array<BasicModel> =
            arrayOf(BasicModelG(), BasicModelIF(), BasicModelIn(), BasicModelIne())

        /** The DFR aftereffects to test. */
        private val AFTER_EFFECTS: Array<AfterEffect> = arrayOf(AfterEffectB(), AfterEffectL())

        private val NORMALIZATIONS: Array<Normalization> =
            arrayOf(
                NormalizationH1(),
                NormalizationH2(),
                NormalizationH3(),
                NormalizationZ(),
                Normalization.NoNormalization(),
            )

        /** The distributions for IB. */
        private val DISTRIBUTIONS: Array<Distribution> = arrayOf(DistributionLL(), DistributionSPL())

        /** Lambdas for IB. */
        private val LAMBDAS: Array<Lambda> = arrayOf(LambdaDF(), LambdaTTF())

        /** Independence measures for DFI */
        private val INDEPENDENCE_MEASURES: Array<Independence> =
            arrayOf(IndependenceStandardized(), IndependenceSaturated(), IndependenceChiSquared())
    }

    @BeforeTest
    fun setUp() {
        sims = ArrayList()
        sims.add(ClassicSimilarity())
        sims.add(BM25Similarity())
        sims.add(BooleanSimilarity())
        sims.add(AxiomaticF1EXP())
        sims.add(AxiomaticF1LOG())
        sims.add(AxiomaticF2EXP())
        sims.add(AxiomaticF2LOG())
        sims.add(AxiomaticF3EXP(0.25f, 3))
        sims.add(AxiomaticF3LOG(0.25f, 3))
        // TODO: not great that we dup this all with TestSimilarityBase
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

    /** make sure all sims work with spanOR(termX, termY) where termY does not exist */
    @Test
    fun testCrazySpans() {
        // historically this was a problem, but sim's no longer have to score terms that dont exist
        val dir: Directory = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        val doc = Document()
        val ft = FieldType(TextField.TYPE_NOT_STORED)
        doc.add(newField("foo", "bar", ft))
        iw.addDocument(doc)
        val ir: IndexReader = iw.reader
        iw.close()
        val `is`: IndexSearcher = newSearcher(ir)

        for (sim in sims) {
            `is`.similarity = sim
            val s1 = SpanTermQuery(Term("foo", "bar"))
            val s2 = SpanTermQuery(Term("foo", "baz"))
            val query: Query = SpanOrQuery(s1, s2)
            val td: TopDocs = `is`.search(query, 10)
            assertEquals(1L, td.totalHits.value)
            val score = td.scoreDocs[0].score
            assertFalse(score < 0.0f, "negative score for $sim")
            assertFalse(score.isInfinite(), "inf score for $sim")
            assertFalse(score.isNaN(), "nan score for $sim")
        }
        ir.close()
        dir.close()
    }
}
