package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
