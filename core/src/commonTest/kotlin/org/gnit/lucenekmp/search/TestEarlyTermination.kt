package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse

class TestEarlyTermination : LuceneTestCase() {
    private lateinit var dir: Directory
    private lateinit var writer: RandomIndexWriter

    @BeforeTest
    @Throws(Exception::class)
    fun setUpTestEarlyTermination() {
        dir = newDirectory()
        writer = RandomIndexWriter(random(), dir)
        val numDocs = atLeast(100)
        for (i in 0..<numDocs) {
            writer.addDocument(Document())
            if (rarely()) {
                writer.commit()
            }
        }
    }

    @AfterTest
    @Throws(Exception::class)
    fun tearDownTestEarlyTermination() {
        writer.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testEarlyTermination() {
        val iters = atLeast(5)
        val reader: IndexReader = writer.reader

        for (i in 0..<iters) {
            val searcher = newSearcher(reader)
            searcher.search(
                MatchAllDocsQuery(),
                object : CollectorManager<SimpleCollector, Unit> {
                    override fun newCollector(): SimpleCollector {
                        return object : SimpleCollector() {
                            var collectionTerminated = true
                            override var weight: Weight? = null

                            @Throws(IOException::class)
                            override fun collect(doc: Int) {
                                assertFalse(collectionTerminated)
                                if (rarely()) {
                                    collectionTerminated = true
                                    throw CollectionTerminatedException()
                                }
                            }

                            @Throws(IOException::class)
                            override fun doSetNextReader(context: LeafReaderContext) {
                                if (random().nextBoolean()) {
                                    collectionTerminated = true
                                    throw CollectionTerminatedException()
                                } else {
                                    collectionTerminated = false
                                }
                            }

                            override fun scoreMode(): ScoreMode {
                                return ScoreMode.COMPLETE_NO_SCORES
                            }
                        }
                    }

                    override fun reduce(collectors: MutableCollection<SimpleCollector>) {
                    }
                }
            )
        }
        reader.close()
    }
}
