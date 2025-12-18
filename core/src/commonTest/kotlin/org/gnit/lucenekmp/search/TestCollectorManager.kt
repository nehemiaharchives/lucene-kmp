package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.search.Collector
import org.gnit.lucenekmp.search.CollectorManager
import org.gnit.lucenekmp.search.MultiCollector
import org.gnit.lucenekmp.search.ScoreMode
import org.gnit.lucenekmp.search.Scorable
import org.gnit.lucenekmp.search.Weight
import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.RandomNumbers
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class TestCollectorManager : LuceneTestCase() {

    @Test
    fun testCollection() {
        val dir = newDirectory()
        val reader = reader(dir)
        val ctx = reader.leaves()[0]

        val evenPredicate: (Int) -> Boolean = { it % 2 == 0 }
        val oddPredicate: (Int) -> Boolean = { it % 2 == 1 }

        val cm = CompositeCollectorManager(listOf(evenPredicate, oddPredicate))

        repeat(100) {
            val docs = RandomNumbers.randomIntBetween(random(), 1000, 10000)
            val expected = generateDocIds(docs, random())
            val expectedEven = expected.filter(evenPredicate)
            val expectedOdd = expected.filter(oddPredicate)

            val result = collectAll(ctx, expected, cm)
            assertTrue(result is List<*>)
            val intResults = (result as List<Int>).sorted()
            assertContentEquals((expectedEven + expectedOdd).sorted(), intResults)
        }

        reader.close()
        dir.close()
    }

    @Test
    fun testEmptyCollectors() {
        assertFailsWith<IllegalArgumentException> {
            CompositeCollectorManager(emptyList()).newCollector()
        }
    }

    private fun newDirectory(): Directory = ByteBuffersDirectory()

    private fun reader(dir: Directory): DirectoryReader {
        val iw = IndexWriter(dir, IndexWriterConfig())
        val doc = Document()
        doc.add(TextField("id", "0", Field.Store.NO))
        iw.addDocument(doc)
        iw.commit()
        val reader = iw.getReader(true, false)
        iw.close()
        return reader
    }

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

    private fun generateDocIds(count: Int, random: Random): Set<Int> {
        val generated = mutableSetOf<Int>()
        repeat(count) {
            generated.add(random.nextInt())
        }
        return generated
    }

    private class CompositeCollectorManager(
        private val predicates: List<(Int) -> Boolean>
    ) : CollectorManager<Collector, List<Int>> {

        override fun newCollector(): Collector {
            val collectors = predicates.map { SimpleListCollector(it) }
            return MultiCollector.wrap(collectors)
        }

        override fun reduce(collectors: MutableCollection<Collector>): List<Int> {
            val all = mutableListOf<Int>()
            for (m in collectors) {
                for (c in (m as MultiCollector).getCollectors()) {
                    all.addAll((c as SimpleListCollector).collected)
                }
            }
            return all
        }
    }

    private class SimpleListCollector(
        private val predicate: (Int) -> Boolean
    ) : Collector {
        val collected: MutableList<Int> = mutableListOf()

        override fun getLeafCollector(context: LeafReaderContext): LeafCollector {
            return object : LeafCollector {
                override var scorer: Scorable? = null
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

        override var weight: Weight? = null
    }
}

