package org.gnit.lucenekmp.util

import io.github.oshai.kotlinlogging.KotlinLoggingConfiguration
import io.github.oshai.kotlinlogging.Level
import org.gnit.lucenekmp.index.CheckIndex
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.staticCFunction
import org.gnit.lucenekmp.index.ConcurrentMergeScheduler
import platform.linux.backtrace
import platform.linux.backtrace_symbols_fd
import platform.posix.SIG_DFL
import platform.posix.SIGABRT
import platform.posix.SIGBUS
import platform.posix.SIGILL
import platform.posix.SIGSEGV
import platform.posix.SIGUSR1
import platform.posix.STDERR_FILENO
import platform.posix.fflush
import platform.posix.fprintf
import platform.posix.raise
import platform.posix.signal
import platform.posix.stderr

private var nativeFatalSignalHooksInstalled = false

@OptIn(ExperimentalForeignApi::class)
private fun dumpNativeBacktraceToStderr() {
    memScoped {
        val frames = allocArray<COpaquePointerVar>(64)
        val frameCount = backtrace(frames.reinterpret(), 64)
        if (frameCount > 0) {
            backtrace_symbols_fd(frames.reinterpret(), frameCount, STDERR_FILENO)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun luceneKmpFatalSignalHandler(signalNumber: Int) {
    if (signalNumber == SIGUSR1) {
        fprintf(
            stderr,
            "LUCENE-KMP native probe signal=%d cmsActive=%d cmsStarted=%d cmsFinished=%d chkActive=%d chkStarted=%d chkFinished=%d probeRun=%d probeAttempt=%d probePhase=%d probeUpdates=%d\n",
            signalNumber,
            ConcurrentMergeScheduler.debugActiveMergeThreads(),
            ConcurrentMergeScheduler.debugStartedMergeThreads(),
            ConcurrentMergeScheduler.debugFinishedMergeThreads(),
            CheckIndex.debugActiveIntegrityChecks(),
            CheckIndex.debugStartedIntegrityChecks(),
            CheckIndex.debugFinishedIntegrityChecks(),
            NativeCrashProbe.run(),
            NativeCrashProbe.attempt(),
            NativeCrashProbe.phase(),
            NativeCrashProbe.updates()
        )
        fflush(stderr)
        dumpNativeBacktraceToStderr()
        return
    }
    fprintf(
        stderr,
        "LUCENE-KMP native fatal signal=%d cmsActive=%d cmsStarted=%d cmsFinished=%d chkActive=%d chkStarted=%d chkFinished=%d probeRun=%d probeAttempt=%d probePhase=%d probeUpdates=%d\n",
        signalNumber,
        ConcurrentMergeScheduler.debugActiveMergeThreads(),
        ConcurrentMergeScheduler.debugStartedMergeThreads(),
        ConcurrentMergeScheduler.debugFinishedMergeThreads(),
        CheckIndex.debugActiveIntegrityChecks(),
        CheckIndex.debugStartedIntegrityChecks(),
        CheckIndex.debugFinishedIntegrityChecks(),
        NativeCrashProbe.run(),
        NativeCrashProbe.attempt(),
        NativeCrashProbe.phase(),
        NativeCrashProbe.updates()
    )
    fflush(stderr)
    dumpNativeBacktraceToStderr()
    signal(signalNumber, SIG_DFL)
    raise(signalNumber)
}

@OptIn(ExperimentalForeignApi::class)
private fun installNativeFatalSignalHooksIfNeeded() {
    if (nativeFatalSignalHooksInstalled) {
        return
    }
    nativeFatalSignalHooksInstalled = true
    val handler = staticCFunction(::luceneKmpFatalSignalHandler)
    signal(SIGABRT, handler)
    signal(SIGSEGV, handler)
    signal(SIGBUS, handler)
    signal(SIGILL, handler)
    signal(SIGUSR1, handler)
    NativeCrashProbe.installNativeProbeRequester {
        // Use raise() so the probe backtrace belongs to the requesting thread.
        raise(SIGUSR1)
    }
}

actual fun configureTestLogging() {
    KotlinLoggingConfiguration.logLevel = Level.ERROR
    installNativeFatalSignalHooksIfNeeded()
}
