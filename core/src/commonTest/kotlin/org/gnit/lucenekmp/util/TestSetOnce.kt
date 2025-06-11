package org.gnit.lucenekmp.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TestSetOnce : org.gnit.lucenekmp.tests.util.LuceneTestCase() {
    @Test
    fun testEmptyCtor() {
        val set = SetOnce<Int>()
        assertNull(set.get())
    }

    @Test
    fun testSettingCtor() {
        val set = SetOnce(5)
        assertEquals(5, set.get())
        expectThrows(SetOnce.AlreadySetException::class) {
            set.set(7)
        }
    }

    @Test
    fun testSetOnce() {
        val set = SetOnce<Int>()
        set.set(5)
        assertEquals(5, set.get())
        expectThrows(SetOnce.AlreadySetException::class) {
            set.set(7)
        }
    }

    @Test
    fun testTrySet() {
        val set = SetOnce<Int>()
        assertTrue(set.trySet(5))
        assertEquals(5, set.get())
        assertTrue(!set.trySet(7))
        assertEquals(5, set.get())
    }

    @Test
    fun testSetMultiThreaded() = runBlocking {
        val set = SetOnce<Int>()
        val rnd = random()
        val successes = BooleanArray(10)
        val jobs = (0 until 10).map { idx ->
            launch {
                delay(rnd.nextInt(10).toLong())
                if (set.trySet(idx + 1)) {
                    successes[idx] = true
                }
            }
        }
        jobs.forEach { it.join() }
        successes.forEachIndexed { idx, success ->
            if (success) {
                assertEquals(idx + 1, set.get())
            }
        }
    }
}
