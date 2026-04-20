@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.gnit.lucenekmp.store

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import platform.posix.pthread_mutex_destroy
import platform.posix.pthread_mutex_init
import platform.posix.pthread_mutex_lock
import platform.posix.pthread_mutex_t
import platform.posix.pthread_mutex_unlock

internal actual class NrtOpenInputLock actual constructor() {
    private val mutex = nativeHeap.alloc<pthread_mutex_t>()

    init {
        checkPosix(pthread_mutex_init(mutex.ptr, null), "pthread_mutex_init")
    }

    actual inline fun <T> withLock(crossinline action: () -> T): T {
        checkPosix(pthread_mutex_lock(mutex.ptr), "pthread_mutex_lock")
        try {
            return action()
        } finally {
            checkPosix(pthread_mutex_unlock(mutex.ptr), "pthread_mutex_unlock")
        }
    }

    actual fun close() {
        checkPosix(pthread_mutex_destroy(mutex.ptr), "pthread_mutex_destroy")
        nativeHeap.free(mutex.rawPtr)
    }

    private fun checkPosix(result: Int, operation: String) {
        check(result == 0) { "$operation failed: $result" }
    }
}
