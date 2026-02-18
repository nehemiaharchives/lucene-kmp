package org.gnit.lucenekmp.util

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import org.gnit.lucenekmp.jdkport.CoroutineRunnable
import org.gnit.lucenekmp.jdkport.AtomicInteger
import org.gnit.lucenekmp.jdkport.ThreadFactory
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement

/**
 * A default [ThreadFactory] implementation that accepts the name prefix of the created
 * threads as a constructor argument. Otherwise, this factory yields the same semantics as the
 * thread factory returned by [Executors.defaultThreadFactory].
 */
@OptIn(ExperimentalAtomicApi::class)
class NamedThreadFactory(threadNamePrefix: String?) : ThreadFactory {
    @OptIn(ExperimentalAtomicApi::class)
    private val threadNumber: AtomicInteger = AtomicInteger(1)
    private val threadNamePrefix: String = "${checkPrefix(threadNamePrefix)}-${threadPoolNumber.fetchAndIncrement()}-thread"

    /**
     * Creates a new [Thread]
     *
     * @see ThreadFactory.newThread
     */
    @OptIn(DelicateCoroutinesApi::class)
    override fun newThread(r: Runnable): Job {
        // build a name without String.format or java.util.Locale
        val threadName = "$threadNamePrefix-${threadNumber.fetchAndIncrement()}"
        return GlobalScope.launch(Dispatchers.Default + CoroutineName(threadName)) {
            when (r) {
                is CoroutineRunnable -> r.runSuspending()
                else -> r.run()
            }
        }
    }

    companion object {
        private val threadPoolNumber: AtomicInteger = AtomicInteger(1)
        //private const val NAME_PATTERN = "%s-%d-thread"

        private fun checkPrefix(prefix: String?): String {
            return if (prefix == null || prefix.isEmpty()) "Lucene" else prefix
        }
    }
}
