package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.jdkport.TreeSet
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TestMultiCollectorManager : LuceneTestCase() {

    @Test
    @Throws(IOException::class)
    fun testCollection() {
        val dir = newDirectory()
        val reader = reader(dir)
        val ctx = reader.leaves()[0]

        // Setup two collector managers, one that will only collect even doc ids and one that
        // only collects odd. Create some random doc ids and keep track of the ones that we
        // expect each collector manager to collect:
        val evenPredicate: (Int) -> Boolean = { value -> value % 2 == 0 }
        val oddPredicate: (Int) -> Boolean = { value -> value % 2 == 1 }

        val cm1 = SimpleCollectorManager(evenPredicate)
        val cm2 = SimpleCollectorManager(oddPredicate)

        repeat(100) {
            val docs = 1000 + random().nextInt(9001)
            val expected = generateDocIds(docs, random())
            val expectedEven = expected.filter(evenPredicate)
            val expectedOdd = expected.filter(oddPredicate)

            // Test only wrapping one of the collector managers:
            var mcm = MultiCollectorManager(cm1)
            var results = collectAll(ctx, expected, mcm) as Array<Any?>
            assertEquals(1, results.size)
            var intResults = results[0] as List<Int>
            assertContentEquals(expectedEven.toTypedArray(), intResults.toTypedArray())

            // Test wrapping both collector managers:
            mcm = MultiCollectorManager(cm1, cm2)
            results = collectAll(ctx, expected, mcm) as Array<Any?>
            assertEquals(2, results.size)
            intResults = results[0] as List<Int>
            assertContentEquals(expectedEven.toTypedArray(), intResults.toTypedArray())
            intResults = results[1] as List<Int>
            assertContentEquals(expectedOdd.toTypedArray(), intResults.toTypedArray())
        }

        reader.close()
        dir.close()
    }

    @Test
    fun testNullCollectorManagers() {
        assertFailsWith(IllegalArgumentException::class) { MultiCollectorManager() }
        assertFailsWith(IllegalArgumentException::class) {
            MultiCollectorManager(SimpleCollectorManager(), null as CollectorManager<out Collector, *>?)
        }
    }

    @Test
    @Throws(IOException::class)
    fun testCacheScoresIfNecessary() {
        val dir = newDirectory()
        val reader = reader(dir)
        val ctx = reader.leaves()[0]

        // no collector needs scores => no caching
        var cm1 = collectorManager(ScoreMode.COMPLETE_NO_SCORES, Score::class)
        var cm2 = collectorManager(ScoreMode.COMPLETE_NO_SCORES, Score::class)
        MultiCollectorManager(cm1, cm2).newCollector().getLeafCollector(ctx).scorer = Score()

        // only one collector needs scores => no caching
        cm1 = collectorManager(ScoreMode.COMPLETE, Score::class)
        cm2 = collectorManager(ScoreMode.COMPLETE_NO_SCORES, Score::class)
        MultiCollectorManager(cm1, cm2).newCollector().getLeafCollector(ctx).scorer = Score()

        // several collectors need scores => caching
        cm1 = collectorManager(ScoreMode.COMPLETE, ScoreCachingWrappingScorer::class)
        cm2 = collectorManager(ScoreMode.COMPLETE, ScoreCachingWrappingScorer::class)
        MultiCollectorManager(cm1, cm2).newCollector().getLeafCollector(ctx).scorer = Score()

        reader.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testScoreWrapping() {
        val dir = newDirectory()
        val reader = reader(dir)
        val ctx = reader.leaves()[0]

        // all wrapped collector managers are TOP_SCORE score mode, so they should see a
        // MinCompetitiveScoreAwareScorable passed in as their scorer:
        var cm1 = collectorManager(ScoreMode.TOP_SCORES, MultiCollector.MinCompetitiveScoreAwareScorable::class)
        var cm2 = collectorManager(ScoreMode.TOP_SCORES, MultiCollector.MinCompetitiveScoreAwareScorable::class)
        MultiCollectorManager(cm1, cm2).newCollector().getLeafCollector(ctx).scorer = Score()

        // both wrapped collector managers need scores, but one is exhaustive, so they should
        // see a ScoreCachingWrappingScorer pass in as their scorer:
        cm1 = collectorManager(ScoreMode.COMPLETE, ScoreCachingWrappingScorer::class)
        cm2 = collectorManager(ScoreMode.TOP_SCORES, ScoreCachingWrappingScorer::class)
        MultiCollectorManager(cm1, cm2).newCollector().getLeafCollector(ctx).scorer = Score()

        reader.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testEarlyTermination() {
        val dir = newDirectory()
        val reader = reader(dir)
        val ctx = reader.leaves()[0]

        val docs = 1000 + random().nextInt(9001)
        val expected = generateDocIds(docs, random())

        // The first collector manager should collect all docs even though the second throws
        // CollectionTerminatedException immediately:
        val cm1 = SimpleCollectorManager()
        val cm2 = TerminatingCollectorManager()
        val mcm = MultiCollectorManager(cm1, cm2)
        val results = collectAll(ctx, expected, mcm) as Array<Any?>
        assertEquals(2, results.size)
        val intResults = results[0] as List<Int>
        assertContentEquals(expected.toTypedArray(), intResults.toTypedArray())
        assertNull(results[1])

        // If we wrap multiple collector managers that throw CollectionTerminatedException, the
        // exception should be thrown by the MultiCollectorManager's collector:
        val cm3 = TerminatingCollectorManager()
        expectThrows(CollectionTerminatedException::class) {
            collectAll(ctx, expected, MultiCollectorManager(cm2, cm3))
        }

        reader.close()
        dir.close()
    }

    companion object {
        @Throws(IOException::class)
        private fun reader(dir: Directory): DirectoryReader {
            val iw = RandomIndexWriter(random(), dir)
            iw.addDocument(Document())
            iw.commit()
            val reader = iw.getReader(true, false)
            iw.close()

            return reader
        }

        @Throws(IOException::class)
        private fun <C : Collector> collectAll(
            ctx: LeafReaderContext,
            values: Collection<Int>,
            collectorManager: CollectorManager<C, *>
        ): Any? {
            val collectors = mutableListOf<C>()
            var collector = collectorManager.newCollector()
            collectors.add(collector)
            var leafCollector = collector.getLeafCollector(ctx)
            for (v in values) {
                if (random().nextInt(10) == 1) {
                    collector = collectorManager.newCollector()
                    collectors.add(collector)
                    leafCollector = collector.getLeafCollector(ctx)
                }
                leafCollector.collect(v)
            }
            return collectorManager.reduce(collectors)
        }

        /**
         * Generate test doc ids. This will de-dupe and create a sorted collection to be more realistic
         * with real-world use-cases. Note that it's possible this will generate fewer than 'count'
         * entries because of de-duping, but that should be quite rare and probably isn't worth worrying
         * about for these testing purposes.
         */
        private fun generateDocIds(count: Int, random: kotlin.random.Random): MutableSet<Int> {
            val generated = TreeSet<Int>()
            repeat(count) {
                generated.add(random.nextInt())
            }
            return generated
        }

        private fun collectorManager(
            scoreMode: ScoreMode,
            expectedScorer: KClass<out Scorable>
        ): CollectorManager<Collector, Any?> {
            return object : CollectorManager<Collector, Any?> {
                @Throws(IOException::class)
                override fun newCollector(): Collector {
                    return object : Collector {
                        override fun scoreMode(): ScoreMode {
                            return scoreMode
                        }

                        @Throws(IOException::class)
                        override fun getLeafCollector(context: LeafReaderContext): LeafCollector {
                            return object : LeafCollector {
                                override var scorer: Scorable?
                                    get() = null
                                    set(value) {
                                        var scorer = value!!
                                        while (expectedScorer != scorer::class && scorer.children.size == 1) {
                                            scorer = scorer.children.first().child
                                        }
                                        assertEquals(expectedScorer, scorer::class)
                                    }

                                @Throws(IOException::class)
                                override fun collect(doc: Int) {}
                            }
                        }

                        override var weight: Weight? = null
                    }
                }

                @Throws(IOException::class)
                override fun reduce(collectors: MutableCollection<Collector>): Any? {
                    return null
                }
            }
        }
    }

    private class SimpleCollectorManager(private val predicate: (Int) -> Boolean) :
        CollectorManager<SimpleListCollector, List<Int>> {

        constructor() : this({ _: Int -> true })

        @Throws(IOException::class)
        override fun newCollector(): SimpleListCollector {
            return SimpleListCollector(predicate)
        }

        @Throws(IOException::class)
        override fun reduce(collectors: MutableCollection<SimpleListCollector>): List<Int> {
            val all = mutableListOf<Int>()
            for (c in collectors) {
                all.addAll(c.collected)
            }

            return all
        }
    }

    private class SimpleListCollector(private val predicate: (Int) -> Boolean) : Collector {
        val collected = mutableListOf<Int>()
        override var weight: Weight? = null

        @Throws(IOException::class)
        override fun getLeafCollector(context: LeafReaderContext): LeafCollector {
            return object : LeafCollector {
                override var scorer: Scorable?
                    get() = null
                    set(value) {}

                @Throws(IOException::class)
                override fun collect(doc: Int) {
                    if (predicate(doc)) {
                        collected.add(doc)
                    }
                }
            }
        }

        override fun scoreMode(): ScoreMode {
            return ScoreMode.COMPLETE
        }
    }

    private class TerminatingCollectorManager : CollectorManager<Collector, Any?> {

        @Throws(IOException::class)
        override fun newCollector(): Collector {
            return object : SimpleCollector() {
                override var weight: Weight? = null

                @Throws(IOException::class)
                override fun collect(doc: Int) {
                    throw CollectionTerminatedException()
                }

                override fun scoreMode(): ScoreMode {
                    return ScoreMode.COMPLETE
                }
            }
        }

        @Throws(IOException::class)
        override fun reduce(collectors: MutableCollection<Collector>): Any? {
            return null
        }
    }
}
