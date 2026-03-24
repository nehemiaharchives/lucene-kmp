package org.gnit.lucenekmp.jdkport

import kotlinx.coroutines.Runnable
import kotlinx.coroutines.runBlocking

/**
 * Port of `java.util.concurrent.CyclicBarrier`.
 */
@Ported(from = "java.util.concurrent.CyclicBarrier")
class CyclicBarrier(
    private val parties: Int,
    private val barrierAction: Runnable? = null
) {
    private val lock = ReentrantLock()
    private val trip = lock.newCondition()
    private var count = parties
    private var generation = 0
    private var broken = false

    init {
        require(parties > 0) { "parties must be > 0" }
    }

    @Throws(InterruptedException::class, BrokenBarrierException::class)
    fun await(): Int {
        lock.lock()
        try {
            if (broken) {
                throw BrokenBarrierException()
            }
            val arrivalGeneration = generation
            val index = --count
            if (index == 0) {
                runBarrierAction()
                nextGeneration()
                return 0
            }

            while (arrivalGeneration == generation && !broken) {
                runBlocking {
                    trip.await()
                }
            }

            if (broken) {
                throw BrokenBarrierException()
            }
            return index
        } catch (e: InterruptedException) {
            breakBarrier()
            throw e
        } catch (e: BrokenBarrierException) {
            throw e
        } catch (e: Exception) {
            breakBarrier()
            throw e
        } finally {
            lock.unlock()
        }
    }

    private fun runBarrierAction() {
        barrierAction?.run()
    }

    private fun nextGeneration() {
        count = parties
        generation++
        runBlocking {
            trip.signalAll()
        }
    }

    private fun breakBarrier() {
        broken = true
        runBlocking {
            trip.signalAll()
        }
    }
}
