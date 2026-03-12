package org.gnit.lucenekmp.store

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal actual class NrtOpenInputLock actual constructor() {
    private val mutex = Mutex()

    actual inline fun <T> withLock(crossinline action: () -> T): T = runBlocking {
        mutex.withLock { action() }
    }

    actual fun close() {
    }
}
