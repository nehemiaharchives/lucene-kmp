package org.gnit.lucenekmp.document

import okio.IOException
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.jdkport.floatToIntBits
import org.gnit.lucenekmp.jdkport.intBitsToFloat
import org.gnit.lucenekmp.jdkport.isFinite
import org.gnit.lucenekmp.search.BooleanClause
import org.gnit.lucenekmp.search.BooleanQuery
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.ScoreMode
import org.gnit.lucenekmp.search.Scorer
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.search.TopDocs
import org.gnit.lucenekmp.search.Weight
import org.gnit.lucenekmp.search.similarities.BM25Similarity
import org.gnit.lucenekmp.search.similarities.Similarity
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.junitport.MatcherAssert.assertThat
import org.gnit.lucenekmp.tests.search.QueryUtils
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.junitport.Matchers
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOUtils
import kotlin.math.ln
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TestFeatureField : LuceneTestCase() {
    @Test
    @Throws(Exception::class)
    fun testBasics() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir, newIndexWriterConfig().setMergePolicy(newLogMergePolicy(random().nextBoolean())))
        val doc = Document()
        val pagerank = FeatureField("features", "pagerank", 1f)
        val urlLength = FeatureField("features", "urlLen", 1f)
        doc.add(pagerank)
        doc.add(urlLength)

        pagerank.setFeatureValue(10f)
        urlLength.setFeatureValue(1f / 24)
        writer.addDocument(doc)

        pagerank.setFeatureValue(100f)
        urlLength.setFeatureValue(1f / 20)
        writer.addDocument(doc)

        writer.addDocument(Document()) // gap

        pagerank.setFeatureValue(1f)
        urlLength.setFeatureValue(1f / 100)
        writer.addDocument(doc)

        pagerank.setFeatureValue(42f)
        urlLength.setFeatureValue(1f / 23)
        writer.addDocument(doc)

        writer.forceMerge(1)
        val reader: DirectoryReader = writer.reader
        writer.close()

        val searcher: IndexSearcher = newSearcher(reader)
        val context: LeafReaderContext = searcher.indexReader.leaves()[0]

        val fieldInfo: FieldInfo = context.reader().fieldInfos.fieldInfo("features")!!
        assertFalse(fieldInfo.hasTermVectors())

        var q: Query = FeatureField.newLogQuery("features", "pagerank", 3f, 4.5f)
        var w: Weight = q.createWeight(searcher, ScoreMode.TOP_SCORES, 2f)
        var s: Scorer = w.scorer(context)!!

        assertEquals(0, s.iterator().nextDoc().toLong())
        assertEquals((6.0 * ln((4.5f + 10).toDouble())).toFloat(), s.score(), 0f)

        assertEquals(1, s.iterator().nextDoc().toLong())
        assertEquals((6.0 * ln((4.5f + 100).toDouble())).toFloat(), s.score(), 0f)

        assertEquals(3, s.iterator().nextDoc().toLong())
        assertEquals((6.0 * ln((4.5f + 1).toDouble())).toFloat(), s.score(), 0f)

        assertEquals(4, s.iterator().nextDoc().toLong())
        assertEquals((6.0 * ln((4.5f + 42).toDouble())).toFloat(), s.score(), 0f)

        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), s.iterator().nextDoc().toLong())

        q = FeatureField.newLinearQuery("features", "pagerank", 3f)
        w = q.createWeight(searcher, ScoreMode.TOP_SCORES, 2f)
        s = w.scorer(context)!!

        assertEquals(0, s.iterator().nextDoc().toLong())
        assertEquals((6.0 * 10).toFloat(), s.score(), 0f)

        assertEquals(1, s.iterator().nextDoc().toLong())
        assertEquals((6.0 * 100).toFloat(), s.score(), 0f)

        assertEquals(3, s.iterator().nextDoc().toLong())
        assertEquals((6.0 * 1).toFloat(), s.score(), 0f)

        assertEquals(4, s.iterator().nextDoc().toLong())
        assertEquals((6.0 * 42).toFloat(), s.score(), 0f)

        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), s.iterator().nextDoc().toLong())

        q = FeatureField.newSaturationQuery("features", "pagerank", 3f, 4.5f)
        w = q.createWeight(searcher, ScoreMode.TOP_SCORES, 2f)
        s = w.scorer(context)!!

        assertEquals(0, s.iterator().nextDoc().toLong())
        assertEquals(6f * (1 - 4.5f / (4.5f + 10)), s.score(), 0f)

        assertEquals(1, s.iterator().nextDoc().toLong())
        assertEquals(6f * (1 - 4.5f / (4.5f + 100)), s.score(), 0f)

        assertEquals(3, s.iterator().nextDoc().toLong())
        assertEquals(6f * (1 - 4.5f / (4.5f + 1)), s.score(), 0f)

        assertEquals(4, s.iterator().nextDoc().toLong())
        assertEquals(6f * (1 - 4.5f / (4.5f + 42)), s.score(), 0f)

        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), s.iterator().nextDoc().toLong())

        q = FeatureField.newSigmoidQuery("features", "pagerank", 3f, 4.5f, 0.6f)
        w = q.createWeight(searcher, ScoreMode.TOP_SCORES, 2f)
        s = w.scorer(context)!!
        val kPa = 4.5.pow(0.6)

        assertEquals(0, s.iterator().nextDoc().toLong())
        assertEquals((6 * (1 - kPa / (kPa + 10.0.pow(0.6)))).toFloat(), s.score(), 0f)

        assertEquals(1, s.iterator().nextDoc().toLong())
        assertEquals((6 * (1 - kPa / (kPa + 100.0.pow(0.6)))).toFloat(), s.score(), 0f)

        assertEquals(3, s.iterator().nextDoc().toLong())
        assertEquals((6 * (1 - kPa / (kPa + 1.0.pow(0.6)))).toFloat(), s.score(), 0f)

        assertEquals(4, s.iterator().nextDoc().toLong())
        assertEquals((6 * (1 - kPa / (kPa + 42.0.pow(0.6)))).toFloat(), s.score(), 0f)

        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), s.iterator().nextDoc().toLong())

        q = FeatureField.newSaturationQuery("features", "urlLen", 3f, 1f / 24)
        w = q.createWeight(searcher, ScoreMode.TOP_SCORES, 2f)
        s = w.scorer(context)!!

        assertEquals(0, s.iterator().nextDoc().toLong())
        assertEquals(6f * (1 - (1f / 24) / (1f / 24 + round(1f / 24))), s.score(), 0f)

        assertEquals(1, s.iterator().nextDoc().toLong())
        assertEquals(6f * (1 - 1f / 24 / (1f / 24 + round(1f / 20))), s.score(), 0f)

        assertEquals(3, s.iterator().nextDoc().toLong())
        assertEquals(6f * (1 - 1f / 24 / (1f / 24 + round(1f / 100))), s.score(), 0f)

        assertEquals(4, s.iterator().nextDoc().toLong())
        assertEquals(6f * (1 - 1f / 24 / (1f / 24 + round(1f / 23))), s.score(), 0f)

        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), s.iterator().nextDoc().toLong())

        IOUtils.close(reader, dir)
    }

    @Test
    @Throws(Exception::class)
    fun testExplanations() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir, newIndexWriterConfig().setMergePolicy(newLogMergePolicy(random().nextBoolean())))
        val doc = Document()
        val pagerank = FeatureField("features", "pagerank", 1f)
        doc.add(pagerank)

        pagerank.setFeatureValue(10f)
        writer.addDocument(doc)

        pagerank.setFeatureValue(100f)
        writer.addDocument(doc)

        writer.addDocument(Document()) // gap

        pagerank.setFeatureValue(1f)
        writer.addDocument(doc)

        pagerank.setFeatureValue(42f)
        writer.addDocument(doc)

        val reader: DirectoryReader = writer.reader
        writer.close()

        val searcher: IndexSearcher = newSearcher(reader)

        QueryUtils.check(random(), FeatureField.newLogQuery("features", "pagerank", 1f, 4.5f), searcher)
        QueryUtils.check(random(), FeatureField.newLinearQuery("features", "pagerank", 1f), searcher)
        QueryUtils.check(random(), FeatureField.newSaturationQuery("features", "pagerank", 1f, 12f), searcher)
        QueryUtils.check(random(), FeatureField.newSigmoidQuery("features", "pagerank", 1f, 12f, 0.6f), searcher)

        // Test boosts that are > 1
        QueryUtils.check(random(), FeatureField.newLogQuery("features", "pagerank", 3f, 4.5f), searcher)
        QueryUtils.check(random(), FeatureField.newLinearQuery("features", "pagerank", 3f), searcher)
        QueryUtils.check(random(), FeatureField.newSaturationQuery("features", "pagerank", 3f, 12f), searcher)
        QueryUtils.check(random(), FeatureField.newSigmoidQuery("features", "pagerank", 3f, 12f, 0.6f), searcher)

        // Test boosts that are < 1
        QueryUtils.check(random(), FeatureField.newLogQuery("features", "pagerank", .2f, 4.5f), searcher)
        QueryUtils.check(random(), FeatureField.newLinearQuery("features", "pagerank", .2f), searcher)
        QueryUtils.check(random(), FeatureField.newSaturationQuery("features", "pagerank", .2f, 12f), searcher)
        QueryUtils.check(random(), FeatureField.newSigmoidQuery("features", "pagerank", .2f, 12f, 0.6f), searcher)

        IOUtils.close(reader, dir)
    }

    @Test
    fun testLogSimScorer() {
        doTestSimScorer(FeatureField.LogFunction(4.5f).scorer(3f))
    }

    @Test
    fun testLinearSimScorer() {
        doTestSimScorer(FeatureField.LinearFunction().scorer(1f))
    }

    @Test
    fun testSatuSimScorer() {
        doTestSimScorer(FeatureField.SaturationFunction("foo", "bar", 20f).scorer(3f))
    }

    @Test
    fun testSigmSimScorer() {
        doTestSimScorer(FeatureField.SigmoidFunction(20f, 0.6f).scorer(3f))
    }

    private fun doTestSimScorer(s: Similarity.SimScorer) {
        val maxScore: Float = s.score(Float.MAX_VALUE, 1)
        assertTrue(Float.isFinite(maxScore)) // used to compute max scores
        // Test that the score doesn't decrease with freq
        for (freq in 2..65535) {
            assertTrue(s.score((freq - 1).toFloat(), 1L) <= s.score(freq.toFloat(), 1L))
        }
        assertTrue(s.score(65535f, 1L) <= maxScore)
    }

    @Test
    @Throws(IOException::class)
    fun testComputePivotFeatureValue() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir, newIndexWriterConfig())

        // Make sure that we create a legal pivot on missing features
        var reader: DirectoryReader = writer.reader
        var searcher: IndexSearcher = newSearcher(reader)
        var pivot: Float = FeatureField.computePivotFeatureValue(searcher, "features", "pagerank")
        assertTrue(Float.isFinite(pivot))
        assertTrue(pivot > 0)
        reader.close()

        val doc = Document()
        val pagerank = FeatureField("features", "pagerank", 1f)
        doc.add(pagerank)

        pagerank.setFeatureValue(10f)
        writer.addDocument(doc)

        pagerank.setFeatureValue(100f)
        writer.addDocument(doc)

        writer.addDocument(Document()) // gap

        pagerank.setFeatureValue(1f)
        writer.addDocument(doc)

        pagerank.setFeatureValue(42f)
        writer.addDocument(doc)

        reader = writer.reader
        writer.close()

        searcher = newSearcher(reader)
        pivot = FeatureField.computePivotFeatureValue(searcher, "features", "pagerank")
        val expected = (10 * 100 * 1 * 42).toDouble().pow(1 / 4.0) // geometric mean
        assertEquals(expected, pivot.toDouble(), 0.1)

        IOUtils.close(reader, dir)
    }

    @Test
    @Throws(IOException::class)
    fun testDemo() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir, newIndexWriterConfig().setMergePolicy(newLogMergePolicy(random().nextBoolean())))
        val doc = Document()
        val pagerank = FeatureField("features", "pagerank", 1f)
        doc.add(pagerank)
        val body = TextField("body", "", Field.Store.NO)
        doc.add(body)

        pagerank.setFeatureValue(10f)
        body.setStringValue("Apache Lucene")
        writer.addDocument(doc)

        pagerank.setFeatureValue(1000f)
        body.setStringValue("Apache Web HTTP server")
        writer.addDocument(doc)

        pagerank.setFeatureValue(1f)
        body.setStringValue("Lucene is a search engine")
        writer.addDocument(doc)

        pagerank.setFeatureValue(42f)
        body.setStringValue("Lucene in the sky with diamonds")
        writer.addDocument(doc)

        val reader: DirectoryReader = writer.reader
        writer.close()

        // NOTE: If you need to make changes below, then you likely also need to
        // update javadocs of FeatureField.
        val searcher: IndexSearcher = newSearcher(reader)
        searcher.similarity = BM25Similarity()
        val query: Query =
            BooleanQuery.Builder()
                .add(TermQuery(Term("body", "apache")), BooleanClause.Occur.SHOULD)
                .add(TermQuery(Term("body", "lucene")), BooleanClause.Occur.SHOULD)
                .build()
        val boost: Query = FeatureField.newSaturationQuery("features", "pagerank")
        val boostedQuery: Query =
            BooleanQuery.Builder().add(query, BooleanClause.Occur.MUST).add(boost, BooleanClause.Occur.SHOULD).build()
        val topDocs: TopDocs = searcher.search(boostedQuery, 10)
        assertEquals(4, topDocs.scoreDocs.size.toLong())
        assertEquals(1, topDocs.scoreDocs[0].doc.toLong())
        assertEquals(0, topDocs.scoreDocs[1].doc.toLong())
        assertEquals(3, topDocs.scoreDocs[2].doc.toLong())
        assertEquals(2, topDocs.scoreDocs[3].doc.toLong())

        IOUtils.close(reader, dir)
    }

    @Test
    @Throws(IOException::class)
    fun testBasicsNonScoringCase() {
        newDirectory().use { dir ->
            val reader: DirectoryReader
            RandomIndexWriter(
                random(),
                dir,
                newIndexWriterConfig().setMergePolicy(newLogMergePolicy(random().nextBoolean()))
            ).use { writer ->
                val doc = Document()
                val pagerank = FeatureField("features", "pagerank", 1f)
                val urlLength = FeatureField("features", "urlLen", 1f)
                doc.add(pagerank)
                doc.add(urlLength)

                pagerank.setFeatureValue(10f)
                urlLength.setFeatureValue(1f / 24)
                writer.addDocument(doc)

                pagerank.setFeatureValue(100f)
                urlLength.setFeatureValue(1f / 20)
                writer.addDocument(doc)

                writer.addDocument(Document()) // gap

                pagerank.setFeatureValue(1f)
                urlLength.setFeatureValue(1f / 100)
                writer.addDocument(doc)

                pagerank.setFeatureValue(42f)
                urlLength.setFeatureValue(1f / 23)
                writer.addDocument(doc)

                val urlLenDoc = Document()
                urlLenDoc.add(urlLength)
                writer.addDocument(urlLenDoc)

                val pageRankDoc = Document()
                pageRankDoc.add(pagerank)
                writer.addDocument(pageRankDoc)

                writer.forceMerge(1)
                reader = writer.reader
            }
            val searcher: IndexSearcher = newSearcher(reader)
            val context: LeafReaderContext = searcher.indexReader.leaves()[0]

            for (q in listOf(
                FeatureField.newLogQuery("features", "pagerank", 3f, 4.5f),
                FeatureField.newLinearQuery("features", "pagerank", 2f),
                FeatureField.newSaturationQuery("features", "pagerank", 3f, 4.5f),
                FeatureField.newSigmoidQuery("features", "pagerank", 3f, 4.5f, 0.6f)
            )) {
                val w: Weight = q.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1f)
                val s: Scorer = w.scorer(context)!!

                assertEquals(0, s.iterator().nextDoc().toLong(), q.toString())
                assertEquals(1, s.iterator().nextDoc().toLong(), q.toString())
                assertEquals(3, s.iterator().nextDoc().toLong(), q.toString())
                assertEquals(4, s.iterator().nextDoc().toLong(), q.toString())
                assertEquals(6, s.iterator().nextDoc().toLong(), q.toString())
                assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), s.iterator().nextDoc().toLong(), q.toString())
            }

            for (q in listOf(
                FeatureField.newLogQuery("features", "urlLen", 3f, 4.5f),
                FeatureField.newLinearQuery("features", "urlLen", 2f),
                FeatureField.newSaturationQuery("features", "urlLen", 3f, 4.5f),
                FeatureField.newSigmoidQuery("features", "urlLen", 3f, 4.5f, 0.6f)
            )) {
                val w: Weight = q.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1f)
                val s: Scorer = w.scorer(context)!!

                assertEquals(0, s.iterator().nextDoc().toLong(), q.toString())
                assertEquals(1, s.iterator().nextDoc().toLong(), q.toString())
                assertEquals(3, s.iterator().nextDoc().toLong(), q.toString())
                assertEquals(4, s.iterator().nextDoc().toLong(), q.toString())
                assertEquals(5, s.iterator().nextDoc().toLong(), q.toString())
                assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), s.iterator().nextDoc().toLong(), q.toString())
            }
            reader.close()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testStoreTermVectors() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir, newIndexWriterConfig().setMergePolicy(newLogMergePolicy(random().nextBoolean())))
        val doc = Document()
        val pagerank = FeatureField("features", "pagerank", 1f, true)
        val urlLength = FeatureField("features", "urlLen", 1f, true)
        doc.add(pagerank)
        doc.add(urlLength)

        pagerank.setFeatureValue(10f)
        urlLength.setFeatureValue(0.5f)
        writer.addDocument(doc)

        writer.addDocument(Document()) // gap

        pagerank.setFeatureValue(42f)
        urlLength.setFeatureValue(1.5f)
        writer.addDocument(doc)

        doc.clear()
        val invalid = FeatureField("features", "pagerank", 1f, false)
        doc.add(invalid)
        val exc: Exception = expectThrows(Exception::class) { writer.addDocument(doc) }
        assertThat(exc.message ?: "", Matchers.anyOf(Matchers.containsString("store term vector"), Matchers.containsString("storeTermVector")))

        writer.forceMerge(1)
        val reader: DirectoryReader = writer.reader
        writer.close()

        val searcher: IndexSearcher = newSearcher(reader)
        val context: LeafReaderContext = searcher.indexReader.leaves()[0]

        val fieldInfo: FieldInfo = context.reader().fieldInfos.fieldInfo("features")!!
        assertTrue(fieldInfo.hasTermVectors())

        var terms: Terms? = context.reader().termVectors().get(0, "features")!!
        var termsEnum: TermsEnum = terms!!.iterator()
        assertThat("", termsEnum.next()!!, Matchers.equalTo(BytesRef("pagerank")))
        var postings: PostingsEnum = termsEnum.postings(null)!!
        assertThat("", postings.nextDoc(), Matchers.equalTo(0))
        assertThat("", FeatureField.decodeFeatureValue(postings.freq().toFloat()), Matchers.equalTo(10f))
        assertThat("", postings.nextDoc(), Matchers.equalTo(DocIdSetIterator.NO_MORE_DOCS))

        assertThat("", termsEnum.next()!!, Matchers.equalTo(BytesRef("urlLen")))
        postings = termsEnum.postings(postings)!!
        assertThat("", postings.nextDoc(), Matchers.equalTo(0))
        assertThat("", FeatureField.decodeFeatureValue(postings.freq().toFloat()), Matchers.equalTo(0.5f))
        assertThat("", postings.nextDoc(), Matchers.equalTo(DocIdSetIterator.NO_MORE_DOCS))

        terms = context.reader().termVectors().get(1, "features")
        assertNull(terms)

        terms = context.reader().termVectors().get(2, "features")
        termsEnum = terms!!.iterator()
        assertThat("", termsEnum.next()!!, Matchers.equalTo(BytesRef("pagerank")))
        postings = termsEnum.postings(postings)!!
        assertThat("", postings.nextDoc(), Matchers.equalTo(0))
        assertThat("", FeatureField.decodeFeatureValue(postings.freq().toFloat()), Matchers.equalTo(42f))
        assertThat("", postings.nextDoc(), Matchers.equalTo(DocIdSetIterator.NO_MORE_DOCS))

        assertThat("", termsEnum.next()!!, Matchers.equalTo(BytesRef("urlLen")))
        postings = termsEnum.postings(null)!!
        assertThat("", postings.nextDoc(), Matchers.equalTo(0))
        assertThat("", FeatureField.decodeFeatureValue(postings.freq().toFloat()), Matchers.equalTo(1.5f))
        assertThat("", postings.nextDoc(), Matchers.equalTo(DocIdSetIterator.NO_MORE_DOCS))

        IOUtils.close(reader, dir)
    }

    companion object {
        /** Round a float value the same way that [FeatureField] rounds feature values.  */
        private fun round(f: Float): Float {
            var bits: Int = Float.floatToIntBits(f)
            bits = bits and (0.inv() shl 15) // clear last 15 bits
            return Float.intBitsToFloat(bits)
        }
    }
}
