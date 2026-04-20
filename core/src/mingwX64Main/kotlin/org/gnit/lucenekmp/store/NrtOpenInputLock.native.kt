package org.gnit.lucenekmp.store

import org.gnit.lucenekmp.jdkport.ReentrantLock
import org.gnit.lucenekmp.jdkport.withLock

internal actual class NrtOpenInputLock actual constructor() {
    @PublishedApi
    internal val lock = ReentrantLock()

    actual inline fun <T> withLock(crossinline action: () -> T): T = lock.withLock { action() }

    actual fun close() {
    }
}
