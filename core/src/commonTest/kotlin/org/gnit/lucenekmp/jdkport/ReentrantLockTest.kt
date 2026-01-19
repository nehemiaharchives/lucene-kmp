package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class ReentrantLockTest {
    @Test
    fun testLockUnlock() {
        val lock = ReentrantLock()
        assertFalse(lock.isHeldByCurrentThread())
        lock.lock()
        assertTrue(lock.isHeldByCurrentThread())
        assertTrue(lock.tryLock()) // reentrant
        lock.unlock()
        lock.unlock()
        assertFalse(lock.isHeldByCurrentThread())
        assertTrue(lock.tryLock())
        lock.unlock()
    }

    @Test
    fun testReentrantHoldCount() {
        val lock = ReentrantLock()
        lock.lock()
        assertTrue(lock.isHeldByCurrentThread())
        assertTrue(lock.tryLock())
        lock.unlock()
        assertTrue(lock.isHeldByCurrentThread())
        lock.unlock()
        assertFalse(lock.isHeldByCurrentThread())
    }

    @Test
    fun testUnlockWithoutOwner() {
        val lock = ReentrantLock()
        assertFailsWith<IllegalMonitorStateException> {
            lock.unlock()
        }
    }
}
