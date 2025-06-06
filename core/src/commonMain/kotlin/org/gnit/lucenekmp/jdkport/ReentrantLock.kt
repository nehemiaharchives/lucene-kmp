package org.gnit.lucenekmp.jdkport


/**
 * port of java.util.concurrent.locks.ReentrantLock
 * currently only have placeholder implementation to make compile pass
 *
 * TODO later we will implement or refactor with kotlin coroutines
 */
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex

class ReentrantLock {

    private val mutex = Mutex()
    private var holdCount = 0

    fun tryLock(): Boolean {
        val locked = mutex.tryLock()
        if (locked) {
            holdCount++
        }
        return locked
    }

    fun lock() {
        runBlocking {
            mutex.lock()
        }
        holdCount++
    }

    fun unlock() {
        if (holdCount > 0) {
            holdCount--
            if (holdCount == 0) {
                mutex.unlock()
            }
        }
    }

    fun isHeldByCurrentThread(): Boolean {
        return holdCount > 0
    }
}
