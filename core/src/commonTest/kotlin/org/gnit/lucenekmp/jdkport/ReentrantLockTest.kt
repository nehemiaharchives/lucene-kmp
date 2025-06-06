package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReentrantLockTest {
    @Test
    fun testLockUnlock() {
        val lock = ReentrantLock()
        assertFalse(lock.isHeldByCurrentThread())
        lock.lock()
        assertTrue(lock.isHeldByCurrentThread())
        assertFalse(lock.tryLock()) // already locked
        lock.unlock()
        assertFalse(lock.isHeldByCurrentThread())
        assertTrue(lock.tryLock())
        lock.unlock()
    }
}
