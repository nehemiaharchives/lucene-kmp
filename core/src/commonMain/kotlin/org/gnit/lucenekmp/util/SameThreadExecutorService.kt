package org.gnit.lucenekmp.util

import kotlinx.coroutines.Runnable
import org.gnit.lucenekmp.jdkport.AbstractExecutorService
import org.gnit.lucenekmp.jdkport.InterruptedException
import org.gnit.lucenekmp.jdkport.RejectedExecutionException
import org.gnit.lucenekmp.jdkport.TimeUnit
import kotlin.concurrent.Volatile


/**
 * An `ExecutorService` that executes tasks immediately in the calling thread during submit.
 *
 * @lucene.internal
 */
class SameThreadExecutorService : AbstractExecutorService() {
    @Volatile
    private var shutdown = false

    override fun execute(command: Runnable) {
        checkShutdown()
        command.run()
    }

    override suspend fun shutdownNow(): MutableList<Runnable> {
        shutdown()
        return mutableListOf()
    }

    override fun shutdown() {
        this.shutdown = true
    }

    override val isTerminated: Boolean
        get() =// Simplified: we don't check for any threads hanging in execute (we could
            // introduce an atomic counter, but there seems to be no point).
            shutdown == true

    override val isShutdown: Boolean
        get() = shutdown == true


    override suspend fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean {
        // See comment in isTerminated();
        return true
    }

    private fun checkShutdown() {
        if (shutdown) {
            throw RejectedExecutionException("Executor is shut down.")
        }
    }
}
