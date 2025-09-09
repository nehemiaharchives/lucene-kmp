package org.gnit.lucenekmp.search

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.gnit.lucenekmp.tests.util.LuceneTestCase

/** Tests for [DisiPriorityQueue]. */
class TestDisiPriorityQueue : LuceneTestCase() {

    @Test
    fun testDisiPriorityQueue2() {
        val r = random()
        val w1 = wrapper(randomDisi(r))
        val w2 = wrapper(randomDisi(r))
        val w3 = wrapper(randomDisi(r))

        val pq = DisiPriorityQueue.ofMaxSize(2)
        w1.doc = 1
        w2.doc = 0
        assertNull(pq.top())
        assertEquals(0, pq.size())
        assertSame(w1, pq.add(w1))
        assertSame(w1, pq.top())
        assertEquals(1, pq.size())
        assertSame(w2, pq.add(w2))
        assertSame(w2, pq.top())
        assertEquals(2, pq.size())
        assertFailsWith<IllegalStateException> { pq.add(w3) }

        w2.doc = 1
        assertSame(w2, pq.updateTop())
        var topList = pq.topList()
        assertSame(w1, topList)
        assertSame(w2, topList!!.next)
        assertNull(topList.next!!.next)

        w2.doc = 2
        assertSame(w1, pq.updateTop())
        topList = pq.topList()
        assertSame(w1, topList)
        assertNull(topList!!.next)

        assertSame(w1, pq.pop())
        assertSame(w2, pq.top())
    }

    @Test
    fun testRandom() {
        val r = random()

        val size = r.nextInt(1, if (TEST_NIGHTLY) 1000 else 10)
        val all = Array(size) {
            val it = randomDisi(r)
            wrapper(it)
        }

        val pq = DisiPriorityQueue.ofMaxSize(size)
        if (r.nextBoolean()) {
            for (w in all) {
                pq.add(w)
            }
        } else {
            if (r.nextInt(10) < 2 && size > 1) {
                val len = random().nextInt(1, size)
                for (i in 0 until len) {
                    pq.add(all[i])
                }
                pq.addAll(all, len, size - len)
            } else {
                pq.addAll(all, 0, size)
            }
        }

        while (pq.size() > 0) {
            all.sortBy { it.doc }
            val top = pq.top()!!
            assertEquals(all[0].doc, top.doc)
            top.doc = top.iterator!!.nextDoc()
            if (top.doc == DocIdSetIterator.NO_MORE_DOCS) {
                pq.pop()
            } else {
                pq.updateTop()
            }
        }
    }

    private fun wrapper(iterator: DocIdSetIterator): DisiWrapper {
        val scorer = ConstantScoreScorer(0f, ScoreMode.COMPLETE_NO_SCORES, iterator)
        return DisiWrapper(scorer, random().nextBoolean())
    }

    private fun randomDisi(r: Random): DocIdSetIterator {
        val maxSize = r.nextInt(50)
        val values = (0 until maxSize)
            .map { r.nextInt(0, DocIdSetIterator.NO_MORE_DOCS - 1) }
            .sorted()
            .distinct()
        return object : DocIdSetIterator() {
            private var index = 0
            private var doc = -1

            override fun docID(): Int {
                return doc
            }

            override fun nextDoc(): Int {
                return if (index < values.size) {
                    doc = values[index++]
                    doc
                } else {
                    doc = DocIdSetIterator.NO_MORE_DOCS
                    doc
                }
            }

            override fun advance(target: Int): Int {
                while (doc < target) {
                    nextDoc()
                }
                return doc
            }

            override fun cost(): Long {
                return maxSize.toLong()
            }
        }
    }
}

