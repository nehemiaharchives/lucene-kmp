package org.gnit.lucenekmp.util

import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
@PublishedApi
internal actual fun currentLoggerNameFromCallSite(): String {
    // TODO: Native logger name resolution was a severe bottleneck (4700x slower than JVM).
    // Stack frame parsing with regex on every logger creation caused testRollbackAndCommitWithThreads
    // to take 72s on iOS vs 1.3s on JVM. Since logger names are primarily used for debugging and
    // the majority of logs are disabled in tests, returning a constant avoids the expensive stack walk.
    // This reduced that test from 99s to 4.5s (22x improvement).
    return "org.gnit.lucenekmp"
}

