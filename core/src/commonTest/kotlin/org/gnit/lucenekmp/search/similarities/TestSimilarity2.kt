package org.gnit.lucenekmp.search.similarities

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.BooleanClause
import org.gnit.lucenekmp.search.BooleanQuery
import org.gnit.lucenekmp.search.Explanation
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Tests against all the similarities we have */
class TestSimilarity2 : LuceneTestCase() {
    private lateinit var sims: MutableList<Similarity>

    @BeforeTest
    @Throws(Exception::class)
    fun setUp() {
        sims = mutableListOf()
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

    /**
     * because of stupid things like querynorm, it's possible we computeStats on a field that doesnt
     * exist at all test this against a totally empty index, to make sure sims handle it
     */
    @Test
    @Throws(Exception::class)
    fun testEmptyIndex() {
        val dir: Directory = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        val ir: IndexReader = iw.reader
        iw.close()
        val `is`: IndexSearcher = newSearcher(ir)

        for (sim in sims) {
            `is`.similarity = sim
            assertEquals(0, `is`.search(TermQuery(Term("foo", "bar")), 10).totalHits.value)
        }
        ir.close()
        dir.close()
    }

    /** similar to the above, but ORs the query with a real field */
    @Test
    @Throws(Exception::class)
    fun testEmptyField() {
        val dir: Directory = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        val doc = Document()
        doc.add(newTextField("foo", "bar", Field.Store.NO))
        iw.addDocument(doc)
        val ir: IndexReader = iw.reader
        iw.close()
        val `is`: IndexSearcher = newSearcher(ir)

        for (sim in sims) {
            `is`.similarity = sim
            val query = BooleanQuery.Builder()
            query.add(TermQuery(Term("foo", "bar")), BooleanClause.Occur.SHOULD)
            query.add(TermQuery(Term("bar", "baz")), BooleanClause.Occur.SHOULD)
            assertEquals(1, `is`.search(query.build(), 10).totalHits.value)
        }
        ir.close()
        dir.close()
    }

    /**
     * similar to the above, however the field exists, but we query with a term that doesnt exist too
     */
    @Test
    @Throws(Exception::class)
    fun testEmptyTerm() {
        val dir: Directory = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        val doc = Document()
        doc.add(newTextField("foo", "bar", Field.Store.NO))
        iw.addDocument(doc)
        val ir: IndexReader = iw.reader
        iw.close()
        val `is`: IndexSearcher = newSearcher(ir)

        for (sim in sims) {
            `is`.similarity = sim
            val query = BooleanQuery.Builder()
            query.add(TermQuery(Term("foo", "bar")), BooleanClause.Occur.SHOULD)
            query.add(TermQuery(Term("foo", "baz")), BooleanClause.Occur.SHOULD)
            assertEquals(1, `is`.search(query.build(), 10).totalHits.value)
        }
        ir.close()
        dir.close()
    }

    /** make sure we can retrieve when norms are disabled */
    @Test
    @Throws(Exception::class)
    fun testNoNorms() {
        val dir: Directory = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        val doc = Document()
        val ft = FieldType(TextField.TYPE_NOT_STORED)
        ft.setOmitNorms(true)
        ft.freeze()
        doc.add(newField("foo", "bar", ft))
        iw.addDocument(doc)
        val ir: IndexReader = iw.reader
        iw.close()
        val `is`: IndexSearcher = newSearcher(ir)

        for (sim in sims) {
            `is`.similarity = sim
            val query = BooleanQuery.Builder()
            query.add(TermQuery(Term("foo", "bar")), BooleanClause.Occur.SHOULD)
            assertEquals(1, `is`.search(query.build(), 10).totalHits.value)
        }
        ir.close()
        dir.close()
    }

    /** make sure scores are not skewed by docs not containing the field */
    @Test
    @Throws(Exception::class)
    fun testNoFieldSkew() {
        val dir: Directory = newDirectory()
        // an evil merge policy could reorder our docs for no reason
        val iwConfig: IndexWriterConfig = newIndexWriterConfig().setMergePolicy(newLogMergePolicy())
        val iw = RandomIndexWriter(random(), dir, iwConfig)
        val doc = Document()
        doc.add(newTextField("foo", "bar baz somethingelse", Field.Store.NO))
        iw.addDocument(doc)
        var ir: IndexReader = iw.reader
        var `is`: IndexSearcher = newSearcher(ir)

        val queryBuilder = BooleanQuery.Builder()
        queryBuilder.add(TermQuery(Term("foo", "bar")), BooleanClause.Occur.SHOULD)
        queryBuilder.add(TermQuery(Term("foo", "baz")), BooleanClause.Occur.SHOULD)
        val query: Query = queryBuilder.build()

        // collect scores
        val scores: MutableList<Explanation> = mutableListOf()
        for (sim in sims) {
            `is`.similarity = sim
            scores.add(`is`.explain(query, 0))
        }
        ir.close()

        // add some additional docs without the field
        val numExtraDocs: Int = TestUtil.nextInt(random(), 1, 1000)
        for (i in 0..<numExtraDocs) {
            iw.addDocument(Document())
        }

        // check scores are the same
        ir = iw.reader
        `is` = newSearcher(ir)
        for (i in sims.indices) {
            `is`.similarity = sims[i]
            val expected: Explanation = scores[i]
            val actual: Explanation = `is`.explain(query, 0)
            assertEquals(
                expected.value,
                actual.value,
                "${sims[i]}: actual=$actual,expected=$expected"
            )
        }

        iw.close()
        ir.close()
        dir.close()
    }

    /** make sure all sims work if TF is omitted */
    @Test
    @Throws(Exception::class)
    fun testOmitTF() {
        val dir: Directory = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        val doc = Document()
        val ft = FieldType(TextField.TYPE_NOT_STORED)
        ft.setIndexOptions(IndexOptions.DOCS)
        ft.freeze()
        val f = newField("foo", "bar", ft)
        doc.add(f)
        iw.addDocument(doc)
        val ir: IndexReader = iw.reader
        iw.close()
        val `is`: IndexSearcher = newSearcher(ir)

        for (sim in sims) {
            `is`.similarity = sim
            val query = BooleanQuery.Builder()
            query.add(TermQuery(Term("foo", "bar")), BooleanClause.Occur.SHOULD)
            assertEquals(1, `is`.search(query.build(), 10).totalHits.value)
        }
        ir.close()
        dir.close()
    }

    /** make sure all sims work if TF and norms is omitted */
    @Test
    @Throws(Exception::class)
    fun testOmitTFAndNorms() {
        val dir: Directory = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        val doc = Document()
        val ft = FieldType(TextField.TYPE_NOT_STORED)
        ft.setIndexOptions(IndexOptions.DOCS)
        ft.setOmitNorms(true)
        ft.freeze()
        val f = newField("foo", "bar", ft)
        doc.add(f)
        iw.addDocument(doc)
        val ir: IndexReader = iw.reader
        iw.close()
        val `is`: IndexSearcher = newSearcher(ir)

        for (sim in sims) {
            `is`.similarity = sim
            val query = BooleanQuery.Builder()
            query.add(TermQuery(Term("foo", "bar")), BooleanClause.Occur.SHOULD)
            assertEquals(1, `is`.search(query.build(), 10).totalHits.value)
        }
        ir.close()
        dir.close()
    }

    companion object {
        /** The DFR basic models to test. */
        private val BASIC_MODELS: Array<BasicModel> = arrayOf(
            BasicModelG(),
            BasicModelIF(),
            BasicModelIn(),
            BasicModelIne()
        )

        /** The DFR aftereffects to test. */
        private val AFTER_EFFECTS: Array<AfterEffect> = arrayOf(AfterEffectB(), AfterEffectL())

        /** The DFR normalizations to test. */
        private val NORMALIZATIONS: Array<Normalization> = arrayOf(
            NormalizationH1(),
            NormalizationH2(),
            NormalizationH3(),
            NormalizationZ(),
            Normalization.NoNormalization()
        )

        /** The distributions for IB. */
        private val DISTRIBUTIONS: Array<Distribution> = arrayOf(DistributionLL(), DistributionSPL())

        /** Lambdas for IB. */
        private val LAMBDAS: Array<Lambda> = arrayOf(LambdaDF(), LambdaTTF())

        /** Independence measures for DFI */
        private val INDEPENDENCE_MEASURES: Array<Independence> = arrayOf(
            IndependenceStandardized(),
            IndependenceSaturated(),
            IndependenceChiSquared()
        )
    }
}
