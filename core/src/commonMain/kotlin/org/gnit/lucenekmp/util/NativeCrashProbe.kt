package org.gnit.lucenekmp.util

import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

/**
 * Low-overhead crash probe for kotlin/native fatal-signal diagnostics.
 *
 * Values are intentionally numeric so linux signal handlers can print them safely.
 */
@OptIn(ExperimentalAtomicApi::class)
object NativeCrashProbe {
    const val PHASE_IDLE = 0
    const val PHASE_TEST_RUN_START = 10
    const val PHASE_TEST_RUN_END = 11
    const val PHASE_TEST_RUN_THROWABLE = 12
    const val PHASE_TEST_SUPER_CALL_ENTER = 13
    const val PHASE_TEST_SUPER_CALL_EXIT = 14
    const val PHASE_ATTEMPT_START = 20
    const val PHASE_ATTEMPT_IN_USE = 21
    const val PHASE_VERIFY_INDEXING = 30
    const val PHASE_VERIFY_POST_BEFORE_READER = 31
    const val PHASE_VERIFY_POST_AFTER_READER = 32
    const val PHASE_VERIFY_POST_BEFORE_CLOSE_WRITER = 33
    const val PHASE_VERIFY_POST_AFTER_CLOSE_WRITER = 34
    const val PHASE_VERIFY_QUERY_ITERS = 35
    const val PHASE_VERIFY_FINALLY_CLOSE = 36
    const val PHASE_VERIFY_CLOSE_READER = 42
    const val PHASE_VERIFY_CLOSE_WRITER = 43
    const val PHASE_VERIFY_CLOSE_SAVE_WRITER = 44
    const val PHASE_VERIFY_CLOSE_DIR = 45
    const val PHASE_IW_CLOSE_ENTER = 50
    const val PHASE_IW_SHUTDOWN_START = 51
    const val PHASE_IW_SHUTDOWN_AFTER_FLUSH = 52
    const val PHASE_IW_SHUTDOWN_WAIT_FOR_MERGES = 53
    const val PHASE_IW_SHUTDOWN_BEFORE_COMMIT = 54
    const val PHASE_IW_SHUTDOWN_AFTER_COMMIT = 55
    const val PHASE_IW_SHUTDOWN_BEFORE_ROLLBACK = 56
    const val PHASE_IW_WAIT_FOR_MERGES_LOOP = 57
    const val PHASE_IW_WAIT_FOR_MERGES_DO_WAIT = 58
    const val PHASE_IW_WAIT_FOR_MERGES_DONE = 59
    const val PHASE_IW_ROLLBACK_START = 60
    const val PHASE_IW_ABORT_MERGES_WAIT = 61
    const val PHASE_IW_ROLLBACK_BEFORE_MERGE_SCHEDULER_CLOSE = 62
    const val PHASE_IW_ROLLBACK_AFTER_MERGE_SCHEDULER_CLOSE = 63
    const val PHASE_IW_ROLLBACK_BEFORE_DOC_WRITER_ABORT = 64
    const val PHASE_IW_ROLLBACK_BEFORE_WAIT_FOR_FLUSH = 65
    const val PHASE_IW_ROLLBACK_AFTER_WAIT_FOR_FLUSH = 66
    const val PHASE_IW_SHUTDOWN_BEFORE_SHOULD_CLOSE = 67
    const val PHASE_IW_SHUTDOWN_SHOULD_CLOSE_WAIT = 68
    const val PHASE_IW_SHUTDOWN_AFTER_SHOULD_CLOSE = 69
    const val PHASE_IW_SHUTDOWN_BEFORE_FLUSH = 70
    const val PHASE_IW_SHUTDOWN_SKIP_ALREADY_CLOSED = 71
    const val PHASE_ATTEMPT_ESCAPE_THROWABLE = 40
    const val PHASE_ATTEMPT_END = 41

    private val runCounter = AtomicInt(0)
    private val attemptCounter = AtomicInt(0)
    private val phaseCode = AtomicInt(PHASE_IDLE)
    private val updateCounter = AtomicInt(0)
    @Volatile
    private var nativeProbeRequester: (() -> Unit)? = null

    fun mark(run: Int, attempt: Int, phase: Int) {
        runCounter.store(run)
        attemptCounter.store(attempt)
        phaseCode.store(phase)
        updateCounter.incrementAndFetch()
    }

    fun markPhase(phase: Int) {
        phaseCode.store(phase)
        updateCounter.incrementAndFetch()
    }

    fun clear() {
        runCounter.store(0)
        attemptCounter.store(0)
        phaseCode.store(PHASE_IDLE)
        updateCounter.incrementAndFetch()
    }

    fun installNativeProbeRequester(requester: (() -> Unit)?) {
        nativeProbeRequester = requester
    }

    fun requestNativeProbeDump(times: Int = 1) {
        repeat(times.coerceAtLeast(1)) {
            try {
                nativeProbeRequester?.invoke()
            } catch (_: Throwable) {
                // Best-effort diagnostics only.
            }
        }
    }

    fun run(): Int = runCounter.load()
    fun attempt(): Int = attemptCounter.load()
    fun phase(): Int = phaseCode.load()
    fun updates(): Int = updateCounter.load()
}
