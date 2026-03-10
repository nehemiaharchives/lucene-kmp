package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.jdkport.TreeSet
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals

class TestCollectorManager : LuceneTestCase() {
    @Test
    @Throws(IOException::class)
    fun testCollection() {
        val dir = newDirectory()
        val reader = reader(dir)
        val ctx = reader.leaves()[0]

        // Setup two collectors, one that will only collect even doc ids and one that
        // only collects odd. Create some random doc ids and keep track of the ones that we
        // expect each collector manager to collect:
        val evenPredicate: (Int) -> Boolean = { value -> value % 2 == 0 }
        val oddPredicate: (Int) -> Boolean = { value -> value % 2 == 1 }

        val cm = CompositeCollectorManager(listOf(evenPredicate, oddPredicate))

        for (iter in 0..<100) {
            val docs = 1000 + random().nextInt(9001)
            val expected = generateDocIds(docs, random())
            val expectedEven = expected.filter(evenPredicate)
            val expectedOdd = expected.filter(oddPredicate)

            // Test only wrapping one of the collector managers:
            val result = collectAll(ctx, expected, cm)
            val intResults = (result as MutableList<Int>).sorted()
            assertEquals((expectedEven + expectedOdd).sorted(), intResults)
        }

        reader.close()
        dir.close()
    }

    @Test
    fun testEmptyCollectors() {
        expectThrows(IllegalArgumentException::class) {
            CompositeCollectorManager(emptyList()).newCollector()
        }
    }

    companion object {
        @Throws(IOException::class)
        private fun reader(dir: Directory): DirectoryReader {
            val iw = RandomIndexWriter(LuceneTestCase.random(), dir)
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
                if (LuceneTestCase.random().nextInt(10) == 1) {
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
            for (i in 0..<count) {
                generated.add(random.nextInt())
            }
            return generated
        }
    }

    private class CompositeCollectorManager(private val predicates: List<(Int) -> Boolean>) : CollectorManager<Collector, MutableList<Int>> {
        @Throws(IOException::class)
        override fun newCollector(): Collector {
            return MultiCollector.wrap(*predicates.map(::SimpleListCollector).toTypedArray())
        }

        @Throws(IOException::class)
        override fun reduce(collectors: MutableCollection<Collector>): MutableList<Int> {
            val all = mutableListOf<Int>()
            for (m in collectors) {
                for (c in (m as MultiCollector).getCollectors()) {
                    all.addAll((c as SimpleListCollector).collected)
                }
            }

            return all
        }
    }

    private class SimpleListCollector(private val predicate: (Int) -> Boolean) : Collector {
        val collected: MutableList<Int> = mutableListOf()
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
}
