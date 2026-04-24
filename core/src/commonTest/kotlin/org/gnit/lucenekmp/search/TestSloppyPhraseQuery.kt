package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.AssertingScorable
import org.gnit.lucenekmp.tests.search.QueryUtils
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestSloppyPhraseQuery : LuceneTestCase() {
    companion object {
        private const val S_1 = "A A A"
        private const val S_2 = "A 1 2 3 A 4 5 6 A"
        private val DOC_1 = makeDocument("X $S_1 Y")
        private val DOC_2 = makeDocument("X $S_2 Y")
        private val DOC_3 = makeDocument("X $S_1 A Y")
        private val DOC_1_B = makeDocument("X $S_1 Y N N N N $S_1 Z")
        private val DOC_2_B = makeDocument("X $S_2 Y N N N N $S_2 Z")
        private val DOC_3_B = makeDocument("X $S_1 A Y N N N N $S_1 A Y")
        private val DOC_4 = makeDocument("A A X A X B A X B B A A X B A A")
        private val DOC_5_3 = makeDocument("H H H X X X H H H X X X H H H")
        private val DOC_5_4 = makeDocument("H H H H")

        private val QUERY_1 = makePhraseQuery(S_1)
        private val QUERY_2 = makePhraseQuery(S_2)
        private val QUERY_4 = makePhraseQuery("X A A")
        private val QUERY_5_4 = makePhraseQuery("H H H H")

        private fun makeDocument(docText: String): Document {
            val doc = Document()
            val customType = FieldType(TextField.TYPE_NOT_STORED)
            customType.setOmitNorms(true)
            doc.add(Field("f", docText, customType))
            return doc
        }

        private fun makePhraseQuery(terms: String): PhraseQuery {
            return PhraseQuery("f", *terms.split(" +".toRegex()).toTypedArray())
        }
    }

    /**
     * Test DOC_4 and QUERY_4. QUERY_4 has a fuzzy (len=1) match to DOC_4, so all slop values > 0
     * should succeed. But only the 3rd sequence of A's in DOC_4 will do.
     */
    @Test
    fun testDoc4_Query4_All_Slops_Should_match() {
        for (slop in 0..<30) {
            checkPhraseQuery(DOC_4, QUERY_4, slop, if (slop < 1) 0 else 1)
        }
    }

    /**
     * Test DOC_1 and QUERY_1. QUERY_1 has an exact match to DOC_1, so all slop values should succeed.
     * Before LUCENE-1310, a slop value of 1 did not succeed.
     */
    @Test
    fun testDoc1_Query1_All_Slops_Should_match() {
        for (slop in 0..<30) {
            val freq1 = checkPhraseQuery(DOC_1, QUERY_1, slop, 1)
            val freq2 = checkPhraseQuery(DOC_1_B, QUERY_1, slop, 1)
            assertTrue(freq2 > freq1, "slop=$slop freq2=$freq2 should be greater than score1 $freq1")
        }
    }

    /**
     * Test DOC_2 and QUERY_1. 6 should be the minimum slop to make QUERY_1 match DOC_2. Before
     * LUCENE-1310, 7 was the minimum.
     */
    @Test
    fun testDoc2_Query1_Slop_6_or_more_Should_match() {
        for (slop in 0..<30) {
            val expected = if (slop < 6) 0 else 1
            val freq1 = checkPhraseQuery(DOC_2, QUERY_1, slop, expected)
            if (expected > 0) {
                val freq2 = checkPhraseQuery(DOC_2_B, QUERY_1, slop, 1)
                assertTrue(freq2 > freq1, "slop=$slop freq2=$freq2 should be greater than freq1 $freq1")
            }
        }
    }

    /**
     * Test DOC_2 and QUERY_2. QUERY_2 has an exact match to DOC_2, so all slop values should succeed.
     * Before LUCENE-1310, 0 succeeds, 1 through 7 fail, and 8 or greater succeeds.
     */
    @Test
    fun testDoc2_Query2_All_Slops_Should_match() {
        for (slop in 0..<30) {
            val freq1 = checkPhraseQuery(DOC_2, QUERY_2, slop, 1)
            val freq2 = checkPhraseQuery(DOC_2_B, QUERY_2, slop, 1)
            assertTrue(freq2 > freq1, "slop=$slop freq2=$freq2 should be greater than freq1 $freq1")
        }
    }

    /**
     * Test DOC_3 and QUERY_1. QUERY_1 has an exact match to DOC_3, so all slop values should succeed.
     */
    @Test
    fun testDoc3_Query1_All_Slops_Should_match() {
        for (slop in 0..<30) {
            val freq1 = checkPhraseQuery(DOC_3, QUERY_1, slop, 1)
            val freq2 = checkPhraseQuery(DOC_3_B, QUERY_1, slop, 1)
            assertTrue(freq2 > freq1, "slop=$slop freq2=$freq2 should be greater than freq1 $freq1")
        }
    }

    /** LUCENE-3412 */
    @Test
    fun testDoc5_Query5_Any_Slop_Should_be_consistent() {
        val nRepeats = 5
        for (slop in 0..<3) {
            repeat(nRepeats) {
                // should steadily always find this one
                checkPhraseQuery(DOC_5_4, QUERY_5_4, slop, 1)
            }
            repeat(nRepeats) {
                // should steadily never find this one
                checkPhraseQuery(DOC_5_3, QUERY_5_4, slop, 0)
            }
        }
    }

    private fun checkPhraseQuery(doc: Document, query: PhraseQuery, slop: Int, expectedNumResults: Int): Float {
        val builder = PhraseQuery.Builder()
        val terms = query.terms
        val positions = query.positions
        for (i in terms.indices) {
            builder.add(terms[i], positions[i])
        }
        builder.setSlop(slop)
        val queryWithSlop = builder.build()

        val ramDir = MockDirectoryWrapper(random(), ByteBuffersDirectory())
        val writer = RandomIndexWriter(random(), ramDir, MockAnalyzer(random(), MockTokenizer.WHITESPACE, false))
        writer.addDocument(doc)
        val reader = writer.reader
        val searcher = newSearcher(reader)
        val result = searcher.search(queryWithSlop, MaxFreqCollectorManager())
        assertEquals(expectedNumResults, result.totalHits, "slop: $slop  query: $queryWithSlop  doc: $doc  Wrong number of hits")
        // QueryUtils.check(query,searcher);
        writer.close()
        reader.close()
        ramDir.close()

        // returns the max Scorer.freq() found, because even though norms are omitted, many index stats
        // are different
        // with these different tokens/distributions/lengths.. otherwise this test is very fragile.
        return result.max
    }

    class Result {
        var max = 0f
        var totalHits = 0
    }

    class MaxFreqCollectorManager : CollectorManager<MaxFreqCollector, Result> {
        override fun newCollector(): MaxFreqCollector = MaxFreqCollector()

        override fun reduce(collectors: MutableCollection<MaxFreqCollector>): Result {
            val result = Result()
            for (collector in collectors) {
                result.max = max(result.max, collector.max)
                result.totalHits += collector.totalHits
            }
            return result
        }
    }

    class MaxFreqCollector : SimpleCollector() {
        var max = 0f
        var totalHits = 0
        private var phraseScorer: PhraseScorer? = null

        override var scorer: Scorable?
            get() = phraseScorer
            set(value) {
                phraseScorer = AssertingScorable.unwrap(value!!) as PhraseScorer
            }

        override var weight: Weight? = null

        override fun collect(doc: Int) {
            totalHits++
            val scorer = phraseScorer!!
            var freq = scorer.matcher.sloppyWeight()
            while (scorer.matcher.nextMatch()) {
                freq += scorer.matcher.sloppyWeight()
            }
            max = max(max, freq)
        }

        override fun scoreMode(): ScoreMode = ScoreMode.COMPLETE
    }

    /** checks that no scores are infinite */
    private fun assertSaneScoring(pq: PhraseQuery, searcher: IndexSearcher) {
        searcher.search(
            pq,
            object : CollectorManager<SimpleCollector, Unit> {
                override fun newCollector(): SimpleCollector {
                    return object : SimpleCollector() {
                        private var actualScorer: Scorer? = null

                        override var weight: Weight? = null

                        override var scorer: Scorable?
                            get() = actualScorer
                            set(value) {
                                actualScorer = AssertingScorable.unwrap(value!!) as Scorer
                            }

                        override fun collect(doc: Int) {
                            assertFalse(actualScorer!!.score().isInfinite())
                        }

                        override fun scoreMode(): ScoreMode = ScoreMode.COMPLETE
                    }
                }

                override fun reduce(collectors: MutableCollection<SimpleCollector>) = Unit
            },
        )

        QueryUtils.check(random(), pq, searcher)
    }

    // LUCENE-3215
    @Test
    fun testSlopWithHoles() {
        val dir = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        val customType = FieldType(TextField.TYPE_NOT_STORED)
        customType.setOmitNorms(true)
        val f = Field("lyrics", "", customType)
        val doc = Document()
        doc.add(f)
        f.setStringValue("drug drug")
        iw.addDocument(doc)
        f.setStringValue("drug druggy drug")
        iw.addDocument(doc)
        f.setStringValue("drug druggy druggy drug")
        iw.addDocument(doc)
        f.setStringValue("drug druggy drug druggy drug")
        iw.addDocument(doc)
        val ir = iw.reader
        iw.close()
        val searcher = newSearcher(ir)

        val builder = PhraseQuery.Builder()
        builder.add(Term("lyrics", "drug"), 1)
        builder.add(Term("lyrics", "drug"), 4)
        var pq = builder.build()
        // "drug the drug"~1
        assertEquals(1L, searcher.search(pq, 4).totalHits.value)
        builder.setSlop(1)
        pq = builder.build()
        assertEquals(3L, searcher.search(pq, 4).totalHits.value)
        builder.setSlop(2)
        pq = builder.build()
        assertEquals(4L, searcher.search(pq, 4).totalHits.value)
        ir.close()
        dir.close()
    }

    // LUCENE-3215
    @Test
    fun testInfiniteFreq1() {
        val dir = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        val doc = Document()
        doc.add(newField("lyrics", "drug druggy drug drug drug", FieldType(TextField.TYPE_NOT_STORED)))
        iw.addDocument(doc)
        val ir = iw.reader
        iw.close()

        val searcher = newSearcher(ir)
        val builder = PhraseQuery.Builder()
        builder.add(Term("lyrics", "drug"), 1)
        builder.add(Term("lyrics", "drug"), 3)
        builder.setSlop(1)
        // "drug the drug"~1
        assertSaneScoring(builder.build(), searcher)
        ir.close()
        dir.close()
    }

    // LUCENE-3215
    @Test
    fun testInfiniteFreq2() {
        val document =
            ("So much fun to be had in my head "
                    + "No more sunshine "
                    + "So much fun just lying in my bed "
                    + "No more sunshine "
                    + "I can't face the sunlight and the dirt outside "
                    + "Wanna stay in 666 where this darkness don't lie "
                    + "Drug drug druggy "
                    + "Got a feeling sweet like honey "
                    + "Drug drug druggy "
                    + "Need sensation like my baby "
                    + "Show me your scars you're so aware "
                    + "I'm not barbaric I just care "
                    + "Drug drug drug "
                    + "I need a reflection to prove I exist "
                    + "No more sunshine "
                    + "I am a victim of designer blitz "
                    + "No more sunshine "
                    + "Dance like a robot when you're chained at the knee "
                    + "The C.I.A say you're all they'll ever need "
                    + "Drug drug druggy "
                    + "Got a feeling sweet like honey "
                    + "Drug drug druggy "
                    + "Need sensation like my baby "
                    + "Snort your lines you're so aware "
                    + "I'm not barbaric I just care "
                    + "Drug drug druggy "
                    + "Got a feeling sweet like honey "
                    + "Drug drug druggy "
                    + "Need sensation like my baby")

        val dir = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        val doc = Document()
        doc.add(newField("lyrics", document, FieldType(TextField.TYPE_NOT_STORED)))
        iw.addDocument(doc)
        val ir = iw.reader
        iw.close()

        val searcher = newSearcher(ir)
        val builder = PhraseQuery.Builder()
        builder.add(Term("lyrics", "drug"), 1)
        builder.add(Term("lyrics", "drug"), 4)
        builder.setSlop(5)
        // "drug the drug"~5
        assertSaneScoring(builder.build(), searcher)
        ir.close()
        dir.close()
    }
}
