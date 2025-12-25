package org.gnit.lucenekmp.jdkport

/**
 * port of java.util.concurrent.CountDownLatch
 * currently only have placeholder implementation to make compile pass
 *
 * TODO later we will implement or refactor with kotlin coroutines
 */
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

class CountDownLatch(count: Int) {

    @OptIn(ExperimentalAtomicApi::class)
    private val count = AtomicInt(count)

    private val completed = Job()

    init {
        require(count >= 0) { "count < 0" }
        if (count == 0) {
            completed.complete()
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun getCount(): Long {
        return count.load().toLong()
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun countDown() {
        while (true) {
            val c = count.load()
            if (c == 0) return
            if (count.compareAndSet(c, c - 1)) {
                if (c - 1 == 0) {
                    completed.complete()
                }
                return
            }
        }
    }

    fun await(){
        runBlocking {
            completed.join()
        }
    }
}
