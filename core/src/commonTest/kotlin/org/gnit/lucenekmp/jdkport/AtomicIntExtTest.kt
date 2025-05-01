package org.gnit.lucenekmp.jdkport

import kotlin.concurrent.atomics.AtomicInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AtomicIntExtTest {

    @Test
    fun testGet() {
        val atomicInt = AtomicInt(5)
        assertEquals(5, atomicInt.get())
    }

    @Test
    fun testSet() {
        val atomicInt = AtomicInt(5)
        atomicInt.set(10)
        assertEquals(10, atomicInt.get())
    }

    @Test
    fun testIncrementAndGet() {
        val atomicInt = AtomicInt(5)
        assertEquals(6, atomicInt.incrementAndGet())
        assertEquals(6, atomicInt.get())
    }

    @Test
    fun testDecrementAndGet() {
        val atomicInt = AtomicInt(5)
        assertEquals(4, atomicInt.decrementAndGet())
        assertEquals(4, atomicInt.get())
    }

    @Test
    fun testAccumulateAndGet() {
        val atomicInt = AtomicInt(5)
        val result = atomicInt.accumulateAndGet(3) { x, y -> x + y }
        assertEquals(8, result)
        assertEquals(8, atomicInt.get())
    }

    @Test
    fun testWeakCompareAndSetVolatile() {
        val atomicInt = AtomicInt(5)
        assertTrue(atomicInt.weakCompareAndSetVolatile(5, 10))
        assertEquals(10, atomicInt.get())
    }

    @Test
    fun testGetAndAdd() {
        val atomicInt = AtomicInt(5)
        assertEquals(5, atomicInt.getAndAdd(3))
        assertEquals(8, atomicInt.get())
    }
}
