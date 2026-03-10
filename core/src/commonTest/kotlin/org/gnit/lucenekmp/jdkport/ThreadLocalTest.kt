package org.gnit.lucenekmp.jdkport

import kotlinx.coroutines.Runnable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ThreadLocalTest {
    @Test
    fun testInitialValueIsUsedOnceUntilRemove() {
        var initialValueCallCount = 0
        val threadLocal =
            object : ThreadLocal<String>() {
                override fun initialValue(): String {
                    initialValueCallCount++
                    return "value-$initialValueCallCount"
                }
            }

        assertEquals("value-1", threadLocal.get())
        assertEquals("value-1", threadLocal.get())
        assertEquals(1, initialValueCallCount)

        threadLocal.remove()

        assertEquals("value-2", threadLocal.get())
        assertEquals(2, initialValueCallCount)
    }

    @Test
    fun testSetOverridesInitialValueUntilRemove() {
        var initialValueCallCount = 0
        val threadLocal =
            object : ThreadLocal<String>() {
                override fun initialValue(): String {
                    initialValueCallCount++
                    return "initial-$initialValueCallCount"
                }
            }

        threadLocal.set("explicit")
        assertEquals("explicit", threadLocal.get())
        assertEquals(0, initialValueCallCount)

        threadLocal.remove()

        assertEquals("initial-1", threadLocal.get())
        assertEquals(1, initialValueCallCount)
    }

    @Test
    fun testWithInitialUsesSupplier() {
        var supplierCallCount = 0
        val threadLocal =
            ThreadLocal.withInitial {
                supplierCallCount++
                supplierCallCount
            }

        assertEquals(1, threadLocal.get())
        assertEquals(1, threadLocal.get())
        assertEquals(1, supplierCallCount)

        threadLocal.remove()

        assertEquals(2, threadLocal.get())
        assertEquals(2, supplierCallCount)
    }

    @Test
    fun testRemoveClearsValueToNull() {
        val threadLocal = ThreadLocal<String>()

        assertNull(threadLocal.get())
        threadLocal.set("value")
        assertEquals("value", threadLocal.get())

        threadLocal.remove()

        assertNull(threadLocal.get())
    }

    @Test
    fun testValuesAreIsolatedAcrossThreads() {
        val threadLocal = ThreadLocal<String>()
        val worker1Done = CountDownLatch(1)
        val worker2Done = CountDownLatch(1)
        val worker1Observed = arrayOfNulls<String>(1)
        val worker2Initial = arrayOfNulls<String>(1)
        val worker2AfterSet = arrayOfNulls<String>(1)

        threadLocal.set("main")

        val worker1 =
            Thread(
                Runnable {
                    worker1Observed[0] = threadLocal.get()
                    threadLocal.set("worker-1")
                    worker1Done.countDown()
                }
            )
        worker1.start()
        worker1Done.await()
        worker1.join()

        val worker2 =
            Thread(
                Runnable {
                    worker2Initial[0] = threadLocal.get()
                    threadLocal.set("worker-2")
                    worker2AfterSet[0] = threadLocal.get()
                    worker2Done.countDown()
                }
            )
        worker2.start()
        worker2Done.await()
        worker2.join()

        assertEquals("main", threadLocal.get())
        assertNull(worker1Observed[0])
        assertNull(worker2Initial[0])
        assertEquals("worker-2", worker2AfterSet[0])
    }
}
