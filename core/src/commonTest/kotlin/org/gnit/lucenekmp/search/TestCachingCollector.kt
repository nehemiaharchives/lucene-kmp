package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestCachingCollector : LuceneTestCase() {
    companion object {
        private const val ONE_BYTE = 1.0 / (1024 * 1024) // 1 byte out of MB
    }

    private class MockScorable : Scorable() {
        override fun score(): Float {
            return 0f
        }
    }

    private class NoOpCollector : SimpleCollector() {
        override var weight: Weight? = null

        @Throws(IOException::class)
        override fun collect(doc: Int) {
        }

        override fun scoreMode(): ScoreMode {
            return ScoreMode.COMPLETE_NO_SCORES
        }
    }

    @Test
    @Throws(Exception::class)
    fun testBasic() {
        for (cacheScores in booleanArrayOf(false, true)) {
            val dir = newDirectory()
            val writer = RandomIndexWriter(random(), dir)
            writer.addDocument(Document())
            val reader = writer.getReader(true, false)
            writer.close()
            try {
                val cc = CachingCollector.create(NoOpCollector(), cacheScores, 1.0)
                val acc = cc.getLeafCollector(reader.leaves()[0])
                acc.scorer = MockScorable()

                // collect 1000 docs
                for (i in 0..<1000) {
                    acc.collect(i)
                }
                acc.finish()

                // now replay them
                cc.replay(
                    object : SimpleCollector() {
                        override var weight: Weight? = null
                        var prevDocID = -1

                        override fun collect(doc: Int) {
                            assertEquals(prevDocID + 1, doc)
                            prevDocID = doc
                        }

                        override fun scoreMode(): ScoreMode {
                            return ScoreMode.COMPLETE_NO_SCORES
                        }
                    }
                )
            } finally {
                reader.close()
                dir.close()
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testIllegalStateOnReplay() {
        val dir = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        writer.addDocument(Document())
        val reader = writer.getReader(true, false)
        writer.close()
        try {
            val cc = CachingCollector.create(NoOpCollector(), true, 50 * ONE_BYTE)
            val acc = cc.getLeafCollector(reader.leaves()[0])
            acc.scorer = MockScorable()

            // collect 130 docs, this should be enough for triggering cache abort.
            for (i in 0..<130) {
                acc.collect(i)
            }

            assertFalse(cc.isCached(), "CachingCollector should not be cached due to low memory limit")

            expectThrows(IllegalStateException::class) {
                cc.replay(NoOpCollector())
            }
        } finally {
            reader.close()
            dir.close()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testCachedArraysAllocation() {
        // tests the cached arrays allocation -- if the 'nextLength' was too high,
        // caching would terminate even if a smaller length would suffice.

        // set RAM limit enough for 150 docs + random(10000)
        val numDocs = random().nextInt(10000) + 150
        for (cacheScores in booleanArrayOf(false, true)) {
            val dir = newDirectory()
            val writer = RandomIndexWriter(random(), dir)
            writer.addDocument(Document())
            val reader = writer.getReader(true, false)
            writer.close()
            try {
                val bytesPerDoc = if (cacheScores) 8 else 4
                val cc = CachingCollector.create(NoOpCollector(), cacheScores, bytesPerDoc * ONE_BYTE * numDocs)
                val acc = cc.getLeafCollector(reader.leaves()[0])
                acc.scorer = MockScorable()
                for (i in 0..<numDocs) {
                    acc.collect(i)
                }
                assertTrue(cc.isCached())

                // The 151's document should terminate caching
                acc.collect(numDocs)
                assertFalse(cc.isCached())
            } finally {
                reader.close()
                dir.close()
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testNoWrappedCollector() {
        for (cacheScores in booleanArrayOf(false, true)) {
            // create w/ null wrapped collector, and test that the methods work
            val dir = newDirectory()
            val writer = RandomIndexWriter(random(), dir)
            writer.addDocument(Document())
            val reader = writer.getReader(true, false)
            writer.close()
            try {
                val cc = CachingCollector.create(cacheScores, 50 * ONE_BYTE)
                val acc = cc.getLeafCollector(reader.leaves()[0])
                acc.scorer = MockScorable()
                acc.collect(0)

                assertTrue(cc.isCached())
                acc.finish()
                cc.replay(NoOpCollector())
            } finally {
                reader.close()
                dir.close()
            }
        }
    }
}
