package org.gnit.lucenekmp.jdkport

import platform.posix.sched_yield

internal actual fun platformThreadYield() {
    sched_yield()
}
