package org.gnit.lucenekmp.jdkport

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.gnit.lucenekmp.util.CloseableThreadLocal
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * port of java.lang.Thread
 */
@OptIn(ExperimentalAtomicApi::class, ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
@Ported(from = "java.lang.Thread")
open class Thread : Runnable {
    private val target: Runnable?
    private var job: Job? = null
    private var dispatcher: CloseableCoroutineDispatcher? = null
    private var interrupted = false
    private var started = false
    internal val threadId: Long = threadIdNumber.fetchAndIncrement().toLong()
    private var name: String = "Thread-${threadInitNumber.fetchAndIncrement()}"
    private var priority: Int = NORM_PRIORITY
    private var daemon: Boolean = false

    constructor() {
        target = null
    }

    constructor(target: Runnable?) {
        this.target = target
    }

    constructor(target: () -> Unit) : this(Runnable { target() })

    open fun start() {
        check(!started) { "Thread already started" }
        started = true
        val threadDispatcher = newSingleThreadContext(name)
        dispatcher = threadDispatcher
        val launched =
            CoroutineScope(threadDispatcher + CoroutineName(name)).launch {
                val previous = currentThreadLocal.get()
                currentThreadLocal.set(this@Thread)
                try {
                    this@Thread.run()
                } finally {
                    currentThreadLocal.set(previous)
                }
            }
        job = launched
    }

    override fun run() {
        target?.run()
    }

    fun join() {
        runBlocking {
            job?.join()
        }
        closeDispatcherIfFinished()
    }

    fun join(millis: Long) {
        if (millis <= 0L) {
            return
        }
        runBlocking {
            val localJob = job
            if (localJob != null) {
                withTimeoutOrNull(millis.milliseconds) {
                    localJob.join()
                }
            }
        }
        closeDispatcherIfFinished()
    }

    fun isAlive(): Boolean {
        return job?.isActive == true
    }

    fun interrupt() {
        interrupted = true
        job?.cancel(CancellationException("Thread interrupted"))
    }

    fun isInterrupted(): Boolean {
        return interrupted
    }

    fun setName(name: String) {
        this.name = name
    }

    fun getName(): String {
        return name
    }

    fun setPriority(newPriority: Int) {
        priority = newPriority.coerceIn(MIN_PRIORITY, MAX_PRIORITY)
    }

    fun getPriority(): Int {
        return priority
    }

    fun setDaemon(on: Boolean) {
        daemon = on
    }

    fun isDaemon(): Boolean {
        return daemon
    }

    private fun closeDispatcherIfFinished() {
        val localJob = job
        val localDispatcher = dispatcher
        if (localDispatcher != null && (localJob == null || localJob.isCompleted)) {
            localDispatcher.close()
            if (dispatcher === localDispatcher) {
                dispatcher = null
            }
        }
    }

    companion object {
        const val MIN_PRIORITY: Int = 1
        const val NORM_PRIORITY: Int = 5
        const val MAX_PRIORITY: Int = 10

        private val currentThreadLocal = CloseableThreadLocal<Thread>()
        private val threadInitNumber = AtomicInt(0)
        private val threadIdNumber = AtomicInt(1)
        private val mainThread = Thread().apply { setName("main") }

        fun currentThread(): Thread {
            return currentThreadOrNull() ?: mainThread
        }

        internal fun currentThreadOrNull(): Thread? {
            return currentThreadLocal.get()
        }

        fun interrupted(): Boolean {
            val current = currentThread()
            val wasInterrupted = current.isInterrupted()
            current.interrupted = false
            return wasInterrupted
        }

        fun sleep(millis: Long) {
            val current = currentThread()
            val start = TimeSource.Monotonic.markNow()
            while (start.elapsedNow().inWholeMilliseconds < millis) {
                if (current.isInterrupted()) {
                    throw InterruptedException("sleep interrupted")
                }
                val remaining = millis - start.elapsedNow().inWholeMilliseconds
                runBlocking {
                    delay(minOf(remaining, 10L))
                }
            }
        }

        fun yield() {
            runBlocking {
                kotlinx.coroutines.yield()
            }
        }
    }
}
