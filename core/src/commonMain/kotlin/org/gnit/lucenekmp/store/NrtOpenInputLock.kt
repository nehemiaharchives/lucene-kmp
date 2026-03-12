package org.gnit.lucenekmp.store

internal expect class NrtOpenInputLock() {
    inline fun <T> withLock(crossinline action: () -> T): T

    fun close()
}
