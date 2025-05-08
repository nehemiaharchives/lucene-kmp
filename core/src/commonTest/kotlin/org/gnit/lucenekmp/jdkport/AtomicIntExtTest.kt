package org.gnit.lucenekmp.jdkport

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * tests for extension functions to fill the gap with JDK's AtomicInteger with [AtomicInt]
 */
class AtomicIntExtTest {

    /**
     * @see AtomicInt.get()
     */
    @OptIn(ExperimentalAtomicApi::class)
    @Test
    fun testGet() {
        val atomicInt = AtomicInt(5)
        assertEquals(5, atomicInt.get())
    }

    /**
     * @see AtomicInt.set()
     */
    @OptIn(ExperimentalAtomicApi::class)
    @Test
    fun testSet() {
        val atomicInt = AtomicInt(5)
        atomicInt.set(10)
        assertEquals(10, atomicInt.get())
    }

    /**
     * @see AtomicInt.accumulateAndGet()
     */
    @OptIn(ExperimentalAtomicApi::class)
    @Test
    fun testAccumulateAndGet() {
        val atomicInt = AtomicInt(5)
        val result = atomicInt.accumulateAndGet(3) { x, y -> x + y }
        assertEquals(8, result)
        assertEquals(8, atomicInt.get())
    }

    /**
     * @see AtomicInt.weakCompareAndSetVolatile()
     */
    @OptIn(ExperimentalAtomicApi::class)
    @Test
    fun testWeakCompareAndSetVolatile() {
        val atomicInt = AtomicInt(5)
        assertTrue(atomicInt.weakCompareAndSetVolatile(5, 10))
        assertEquals(10, atomicInt.get())
    }
}
