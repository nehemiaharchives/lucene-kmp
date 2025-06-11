package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals

class TestBytesRefHashConcurrency : LuceneTestCase() {
    @Test
    fun testConcurrentAccessToBytesRefHash() {
        val pool = ByteBlockPool(ByteBlockPool.DirectAllocator())
        val hash = BytesRefHash(pool)
        val numStrings = 797
        val strings = ArrayList<String>(numStrings)
        for (i in 0 until numStrings) {
            val str = TestUtil.randomUnicodeString(random(), 1000)
            hash.add(newBytesRef(str))
            strings.add(str)
        }
        val hashSize = hash.size()
        val notFound = AtomicInteger()
        val notEquals = AtomicInteger()
        val wrongSize = AtomicInteger()
        val numThreads = atLeast(3)
        val latch = CountDownLatch(numThreads)
        val threads = Array(numThreads) { i ->
            val loops = atLeast(100)
            Thread({
                val scratch = BytesRef()
                latch.countDown()
                latch.await()
                for (k in 0 until loops) {
                    val find = newBytesRef(strings[k % strings.size])
                    val id = hash.find(find)
                    if (id < 0) {
                        notFound.incrementAndGet()
                    } else {
                        val get = hash.get(id, scratch)
                        if (!get.bytesEquals(find)) {
                            notEquals.incrementAndGet()
                        }
                    }
                    if (hash.size() != hashSize) {
                        wrongSize.incrementAndGet()
                    }
                }
            }, "t$i")
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertEquals(0, notFound.get())
        assertEquals(0, notEquals.get())
        assertEquals(0, wrongSize.get())
    }
}
