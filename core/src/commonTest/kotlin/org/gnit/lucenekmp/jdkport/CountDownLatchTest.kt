package org.gnit.lucenekmp.jdkport

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CountDownLatchTest {
    @Test
    fun testCountDown() {
        val latch = CountDownLatch(2)
        assertEquals(2L, latch.getCount())
        latch.countDown()
        assertEquals(1L, latch.getCount())
        latch.countDown()
        assertEquals(0L, latch.getCount())
        // extra countdown should keep it at zero
        latch.countDown()
        assertEquals(0L, latch.getCount())
    }

    @Test
    fun testAwait(){
        val latch = CountDownLatch(1)
        var signaled = false

        runBlocking {
            val job = launch(Dispatchers.Default) {
                delay(10)
                signaled = true
                latch.countDown()
            }
            latch.await()
            job.join()
        }

        assertTrue(signaled)
        assertEquals(0L, latch.getCount())
    }
}
