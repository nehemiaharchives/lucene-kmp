package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.DummyTotalHitCountCollector
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalAtomicApi::class)
class TestMultiCollector : LuceneTestCase() {

    private class TerminateAfterCollector(`in`: Collector, private val terminateAfter: Int) :
        FilterCollector(`in`) {

        private var count = 0

        @Throws(IOException::class)
        override fun getLeafCollector(context: LeafReaderContext): LeafCollector {
            if (count >= terminateAfter) {
                throw CollectionTerminatedException()
            }
            val `in` = super.getLeafCollector(context)
            return object : FilterLeafCollector(`in`) {
                @Throws(IOException::class)
                override fun collect(doc: Int) {
                    if (count >= terminateAfter) {
                        throw CollectionTerminatedException()
                    }
                    super.collect(doc)
                    count++
                }
            }
        }
    }

    private class SetScorerCollector(`in`: Collector, private val setScorerCalled: AtomicBoolean) :
        FilterCollector(`in`) {

        @Throws(IOException::class)
        override fun getLeafCollector(context: LeafReaderContext): LeafCollector {
            return object : FilterLeafCollector(super.getLeafCollector(context)) {
                override var scorer: Scorable?
                    get() = super.scorer
                    set(value) {
                        super.scorer = value
                        setScorerCalled.store(true)
                    }
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun testCollectionTerminatedExceptionHandling() {
        val iters = atLeast(3)
        repeat(iters) {
            val dir = newDirectory()
            val w = RandomIndexWriter(random(), dir)
            val numDocs = TestUtil.nextInt(random(), 100, 1000)
            val doc = Document()
            repeat(numDocs) {
                w.addDocument(doc)
            }
            val reader = w.getReader(true, false)
            w.close()
            val searcher = newSearcher(reader, true, true, false)
            val expectedCounts = mutableMapOf<DummyTotalHitCountCollector, Int>()
            val collectors = mutableListOf<Collector>()
            val numCollectors = TestUtil.nextInt(random(), 1, 5)
            repeat(numCollectors) {
                val terminateAfter = random().nextInt(numDocs + 10)
                val expectedCount = if (terminateAfter > numDocs) numDocs else terminateAfter
                val collector = DummyTotalHitCountCollector()
                expectedCounts[collector] = expectedCount
                collectors.add(TerminateAfterCollector(collector, terminateAfter))
            }
            searcher.search(
                MatchAllDocsQuery(),
                object : CollectorManager<Collector, Unit> {
                    override fun newCollector(): Collector {
                        return MultiCollector.wrap(collectors)
                    }

                    override fun reduce(collectors: MutableCollection<Collector>) {
                    }
                }
            )
            for ((collector, expectedCount) in expectedCounts) {
                assertEquals(expectedCount, collector.getTotalHits())
            }
            reader.close()
            dir.close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun testSetScorerAfterCollectionTerminated() {
        var collector1: Collector = DummyTotalHitCountCollector()
        var collector2: Collector = DummyTotalHitCountCollector()

        val setScorerCalled1 = AtomicBoolean(false)
        collector1 = SetScorerCollector(collector1, setScorerCalled1)

        val setScorerCalled2 = AtomicBoolean(false)
        collector2 = SetScorerCollector(collector2, setScorerCalled2)

        collector1 = TerminateAfterCollector(collector1, 1)
        collector2 = TerminateAfterCollector(collector2, 2)

        val scorer = Score()

        val collectors = mutableListOf(collector1, collector2)
        collectors.shuffle(random())
        val collector = MultiCollector.wrap(collectors)

        withOneDocLeafCollector(collector) { leafCollector ->
            leafCollector.scorer = scorer
            assertTrue(setScorerCalled1.load())
            assertTrue(setScorerCalled2.load())

            leafCollector.collect(0)
            leafCollector.collect(1)

            setScorerCalled1.store(false)
            setScorerCalled2.store(false)
            leafCollector.scorer = scorer
            assertFalse(setScorerCalled1.load())
            assertTrue(setScorerCalled2.load())

            expectThrows(CollectionTerminatedException::class) {
                leafCollector.collect(1)
            }

            setScorerCalled1.store(false)
            setScorerCalled2.store(false)
            leafCollector.scorer = scorer
            assertFalse(setScorerCalled1.load())
            assertFalse(setScorerCalled2.load())
        }
    }

    @Test
    @Throws(IOException::class)
    fun testDisablesSetMinScore() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig())
        w.addDocument(Document())
        val reader = DirectoryReader.open(w)
        w.close()

        val scorer = object : Scorable() {
            override fun score(): Float {
                return 0f
            }

            override var minCompetitiveScore: Float
                get() = super.minCompetitiveScore
                set(minScore) {
                    throw AssertionError()
                }
        }

        val collector = object : SimpleCollector() {
            private var scorerValue: Scorable? = null
            var minScore = 0f
            override var weight: Weight? = null

            override fun scoreMode(): ScoreMode {
                return ScoreMode.TOP_SCORES
            }

            override var scorer: Scorable?
                get() = scorerValue
                set(value) {
                    scorerValue = value
                }

            @Throws(IOException::class)
            override fun collect(doc: Int) {
                minScore = Math.nextUp(minScore)
                scorerValue!!.minCompetitiveScore = minScore
            }
        }
        val multiCollector = MultiCollector.wrap(collector, DummyTotalHitCountCollector())
        val leafCollector = multiCollector.getLeafCollector(reader.leaves()[0])
        leafCollector.scorer = scorer
        leafCollector.collect(0) // no exception

        reader.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testDisablesSetMinScoreWithEarlyTermination() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig())
        w.addDocument(Document())
        val reader = DirectoryReader.open(w)
        w.close()

        val scorer = object : Scorable() {
            override fun score(): Float {
                return 0f
            }

            override var minCompetitiveScore: Float
                get() = super.minCompetitiveScore
                set(minScore) {
                    throw AssertionError()
                }
        }

        val collector = object : SimpleCollector() {
            private var scorerValue: Scorable? = null
            var minScore = 0f
            override var weight: Weight? = null

            override fun scoreMode(): ScoreMode {
                return ScoreMode.TOP_SCORES
            }

            override var scorer: Scorable?
                get() = scorerValue
                set(value) {
                    scorerValue = value
                }

            @Throws(IOException::class)
            override fun collect(doc: Int) {
                minScore = Math.nextUp(minScore)
                scorerValue!!.minCompetitiveScore = minScore
            }
        }
        for (numCol in 1..<4) {
            val cols = mutableListOf<Collector>()
            cols.add(collector)
            repeat(numCol) {
                cols.add(TerminateAfterCollector(DummyTotalHitCountCollector(), 0))
            }
            cols.shuffle(random())
            val multiCollector = MultiCollector.wrap(cols)
            val leafCollector = multiCollector.getLeafCollector(reader.leaves()[0])
            leafCollector.scorer = scorer
            leafCollector.collect(0) // no exception
        }

        reader.close()
        dir.close()
    }

    private open class DummyCollector : SimpleCollector() {
        var collectCalled = false
        var setNextReaderCalled = false
        var setScorerCalled = false
        override var weight: Weight? = null

        @Throws(IOException::class)
        override fun collect(doc: Int) {
            collectCalled = true
        }

        @Throws(IOException::class)
        override fun doSetNextReader(context: LeafReaderContext) {
            setNextReaderCalled = true
        }

        override var scorer: Scorable?
            get() = null
            set(value) {
                setScorerCalled = true
            }

        override fun scoreMode(): ScoreMode {
            return ScoreMode.COMPLETE
        }
    }

    @Test
    @Throws(Exception::class)
    fun testNullCollectors() {
        // Tests that the collector rejects all null collectors.
        expectThrows(IllegalArgumentException::class) {
            MultiCollector.wrap(null, null)
        }

        // Tests that the collector handles some null collectors well. If it
        // doesn't, an NPE would be thrown.
        val c = MultiCollector.wrap(DummyCollector(), null, DummyCollector())
        assertTrue(c is MultiCollector)
        withOneDocLeafCollector(c) { ac ->
            ac.collect(1)
        }
        withOneDocLeafCollector(c) {
        }
        withOneDocLeafCollector(c) { leafCollector ->
            leafCollector.scorer = Score()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testSingleCollector() {
        // Tests that if a single Collector is input, it is returned (and not MultiCollector).
        val dc = DummyCollector()
        assertSame(dc, MultiCollector.wrap(dc))
        assertSame(dc, MultiCollector.wrap(dc, null))
    }

    @Test
    @Throws(Exception::class)
    fun testCollector() {
        // Tests that the collector delegates calls to input collectors properly.

        // Tests that the collector handles some null collectors well. If it
        // doesn't, an NPE would be thrown.
        val dcs = arrayOf(DummyCollector(), DummyCollector())
        val c = MultiCollector.wrap(*dcs)
        withOneDocLeafCollector(c) { ac ->
            ac.collect(1)
        }
        withOneDocLeafCollector(c) { ac ->
            ac.scorer = Score()
        }

        for (dc in dcs) {
            assertTrue(dc.collectCalled)
            assertTrue(dc.setNextReaderCalled)
            assertTrue(dc.setScorerCalled)
        }
    }

    private fun collector(scoreMode: ScoreMode, expectedScorer: KClass<out Scorable>): Collector {
        return object : Collector {
            @Throws(IOException::class)
            override fun getLeafCollector(context: LeafReaderContext): LeafCollector {
                return object : LeafCollector {
                    override var scorer: Scorable?
                        get() = null
                        set(newScorer) {
                            var scorer = newScorer!!
                            while (expectedScorer != scorer::class && scorer.children.size == 1) {
                                scorer = scorer.children.first().child
                            }
                            assertEquals(expectedScorer, scorer::class)
                        }

                    @Throws(IOException::class)
                    override fun collect(doc: Int) {
                    }
                }
            }

            override fun scoreMode(): ScoreMode {
                return scoreMode
            }

            override var weight: Weight? = null
        }
    }

    @Test
    @Throws(IOException::class)
    fun testCacheScoresIfNecessary() {
        val dir = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        iw.addDocument(Document())
        iw.commit()
        val reader = iw.getReader(true, false)
        iw.close()

        val ctx = reader.leaves()[0]

        expectThrows(AssertionError::class) {
            collector(ScoreMode.COMPLETE_NO_SCORES, ScoreCachingWrappingScorer::class)
                .getLeafCollector(ctx)
                .scorer = Score()
        }

        // no collector needs scores => no caching
        var c1 = collector(ScoreMode.COMPLETE_NO_SCORES, Score::class)
        var c2 = collector(ScoreMode.COMPLETE_NO_SCORES, Score::class)
        MultiCollector.wrap(c1, c2).getLeafCollector(ctx).scorer = Score()

        // only one collector needs scores => no caching
        c1 = collector(ScoreMode.COMPLETE, Score::class)
        c2 = collector(ScoreMode.COMPLETE_NO_SCORES, Score::class)
        MultiCollector.wrap(c1, c2).getLeafCollector(ctx).scorer = Score()

        // several collectors need scores => caching
        c1 = collector(ScoreMode.COMPLETE, ScoreCachingWrappingScorer::class)
        c2 = collector(ScoreMode.COMPLETE, ScoreCachingWrappingScorer::class)
        MultiCollector.wrap(c1, c2).getLeafCollector(ctx).scorer = Score()

        reader.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testScorerWrappingForTopScores() {
        val dir = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        iw.addDocument(Document())
        val reader = iw.getReader(true, false)
        iw.close()
        val ctx = reader.leaves()[0]
        var c1 = collector(ScoreMode.TOP_SCORES, MultiCollector.MinCompetitiveScoreAwareScorable::class)
        var c2 = collector(ScoreMode.TOP_SCORES, MultiCollector.MinCompetitiveScoreAwareScorable::class)
        MultiCollector.wrap(c1, c2).getLeafCollector(ctx).scorer = Score()

        c1 = collector(ScoreMode.TOP_SCORES, ScoreCachingWrappingScorer::class)
        c2 = collector(ScoreMode.COMPLETE, ScoreCachingWrappingScorer::class)
        MultiCollector.wrap(c1, c2).getLeafCollector(ctx).scorer = Score()

        reader.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testMinCompetitiveScore() {
        val currentMinScores = FloatArray(3)
        val minCompetitiveScore = FloatArray(1)
        val scorer = object : Scorable() {
            @Throws(IOException::class)
            override fun score(): Float {
                return 0f
            }

            override var minCompetitiveScore: Float
                get() = super.minCompetitiveScore
                set(minScore) {
                    minCompetitiveScore[0] = minScore
                }
        }
        val s0 = MultiCollector.MinCompetitiveScoreAwareScorable(scorer, 0, currentMinScores)
        val s1 = MultiCollector.MinCompetitiveScoreAwareScorable(scorer, 1, currentMinScores)
        val s2 = MultiCollector.MinCompetitiveScoreAwareScorable(scorer, 2, currentMinScores)
        assertEquals(0f, minCompetitiveScore[0], 0f)
        s0.minCompetitiveScore = 0.5f
        assertEquals(0f, minCompetitiveScore[0], 0f)
        s1.minCompetitiveScore = 0.8f
        assertEquals(0f, minCompetitiveScore[0], 0f)
        s2.minCompetitiveScore = 0.3f
        assertEquals(0.3f, minCompetitiveScore[0], 0f)
        s2.minCompetitiveScore = 0.1f
        assertEquals(0.3f, minCompetitiveScore[0], 0f)
        s1.minCompetitiveScore = Float.MAX_VALUE
        assertEquals(0.3f, minCompetitiveScore[0], 0f)
        s2.minCompetitiveScore = Float.MAX_VALUE
        assertEquals(0.5f, minCompetitiveScore[0], 0f)
        s0.minCompetitiveScore = Float.MAX_VALUE
        assertEquals(Float.MAX_VALUE, minCompetitiveScore[0], 0f)
    }

    @Test
    @Throws(IOException::class)
    fun testCollectionTermination() {
        val dir = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        iw.addDocument(Document())
        val reader = iw.getReader(true, false)
        iw.close()
        val ctx = reader.leaves()[0]
        val c1 = TerminatingDummyCollector(1, ScoreMode.COMPLETE)
        val c2 = TerminatingDummyCollector(2, ScoreMode.COMPLETE)

        val mc = MultiCollector.wrap(c1, c2)
        val lc = mc.getLeafCollector(ctx)
        lc.scorer = Score()
        lc.collect(0) // OK
        assertTrue(c1.collectCalled, "c1's collect should be called")
        assertTrue(c2.collectCalled, "c2's collect should be called")
        c1.collectCalled = false
        c2.collectCalled = false
        lc.collect(1) // OK, but c1 should terminate
        assertFalse(c1.collectCalled, "c1 should be removed already")
        assertTrue(c2.collectCalled, "c2's collect should be called")
        c2.collectCalled = false

        expectThrows(CollectionTerminatedException::class) {
            lc.collect(2)
        }
        assertFalse(c1.collectCalled, "c1 should be removed already")
        assertFalse(c2.collectCalled, "c2 should be removed already")

        reader.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testSetScorerOnCollectionTerminationSkipNonCompetitive() {
        doTestSetScorerOnCollectionTermination(true)
    }

    @Test
    @Throws(IOException::class)
    fun testSetScorerOnCollectionTerminationSkipNoSkips() {
        doTestSetScorerOnCollectionTermination(false)
    }

    @Throws(IOException::class)
    private fun doTestSetScorerOnCollectionTermination(allowSkipNonCompetitive: Boolean) {
        val dir = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        iw.addDocument(Document())
        val reader = iw.getReader(true, false)
        iw.close()
        val ctx = reader.leaves()[0]

        val c1 = TerminatingDummyCollector(
            1,
            if (allowSkipNonCompetitive) ScoreMode.TOP_SCORES else ScoreMode.COMPLETE
        )
        val c2 = TerminatingDummyCollector(
            2,
            if (allowSkipNonCompetitive) ScoreMode.TOP_SCORES else ScoreMode.COMPLETE
        )

        val mc = MultiCollector.wrap(c1, c2)
        val lc = mc.getLeafCollector(ctx)
        assertFalse(c1.setScorerCalled)
        assertFalse(c2.setScorerCalled)
        lc.scorer = Score()
        assertTrue(c1.setScorerCalled)
        assertTrue(c2.setScorerCalled)
        c1.setScorerCalled = false
        c2.setScorerCalled = false
        lc.collect(0) // OK

        lc.scorer = Score()
        assertTrue(c1.setScorerCalled)
        assertTrue(c2.setScorerCalled)
        c1.setScorerCalled = false
        c2.setScorerCalled = false

        lc.collect(1) // OK, but c1 should terminate
        lc.scorer = Score()
        assertFalse(c1.setScorerCalled)
        assertTrue(c2.setScorerCalled)
        c2.setScorerCalled = false

        expectThrows(CollectionTerminatedException::class) {
            lc.collect(2)
        }
        lc.scorer = Score()
        assertFalse(c1.setScorerCalled)
        assertFalse(c2.setScorerCalled)

        reader.close()
        dir.close()
    }

    @Test
    fun testMergeScoreModes() {
        for (sm1 in ScoreMode.entries) {
            for (sm2 in ScoreMode.entries) {
                val c1: Collector = TerminatingDummyCollector(0, sm1)
                val c2: Collector = TerminatingDummyCollector(0, sm2)
                val c = MultiCollector.wrap(c1, c2)
                if (sm1 == sm2) {
                    assertEquals(sm1, c.scoreMode())
                } else if (sm1.needsScores() || sm2.needsScores()) {
                    assertEquals(ScoreMode.COMPLETE, c.scoreMode())
                } else {
                    assertEquals(ScoreMode.COMPLETE_NO_SCORES, c.scoreMode())
                }
            }
        }
    }

    private class TerminatingDummyCollector(
        private val terminateOnDoc: Int,
        private val scoreMode: ScoreMode
    ) : DummyCollector() {

        @Throws(IOException::class)
        override fun collect(doc: Int) {
            if (doc == terminateOnDoc) {
                throw CollectionTerminatedException()
            }
            super.collect(doc)
        }

        override fun scoreMode(): ScoreMode {
            return scoreMode
        }
    }

    @Throws(IOException::class)
    private fun withOneDocLeafCollector(
        collector: Collector,
        block: (LeafCollector) -> Unit
    ) {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        w.addDocument(Document())
        val reader = w.getReader(true, false)
        w.close()
        try {
            val leafCollector = collector.getLeafCollector(reader.leaves()[0])
            block(leafCollector)
        } finally {
            reader.close()
            dir.close()
        }
    }
}
