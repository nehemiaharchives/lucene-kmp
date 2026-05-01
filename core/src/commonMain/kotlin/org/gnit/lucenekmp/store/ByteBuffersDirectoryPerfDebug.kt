package org.gnit.lucenekmp.store

import org.gnit.lucenekmp.jdkport.ReentrantLock
import org.gnit.lucenekmp.jdkport.withLock

internal object ByteBuffersDirectoryPerfDebug {
    private val lock = ReentrantLock()
    private val counts = linkedMapOf<String, Long>()
    private val elapsedNs = linkedMapOf<String, Long>()

    private var enabled = false

    fun enable() {
        reset()
        enabled = true
    }

    fun disable() {
        enabled = false
    }

    fun reset() {
        lock.withLock {
            counts.clear()
            elapsedNs.clear()
        }
    }

    fun record(operation: String, operationElapsedNs: Long) {
        if (!enabled) return
        lock.withLock {
            counts[operation] = (counts[operation] ?: 0L) + 1L
            elapsedNs[operation] = (elapsedNs[operation] ?: 0L) + operationElapsedNs
        }
    }

    fun snapshot(): String =
        if (!enabled) {
            "substep=byte_buffers_directory_lock disabled=true"
        } else {
            lock.withLock {
                if (counts.isEmpty()) {
                    "substep=byte_buffers_directory_lock calls=0 elapsedNs=0"
                } else {
                    val totalCalls = counts.values.sum()
                    val totalElapsedNs = elapsedNs.values.sum()
                    buildString {
                        append("substep=byte_buffers_directory_lock calls=")
                        append(totalCalls)
                        append(" elapsedNs=")
                        append(totalElapsedNs)
                        for ((operation, count) in counts) {
                            append(' ')
                            append(operation)
                            append("Calls=")
                            append(count)
                            append(' ')
                            append(operation)
                            append("ElapsedNs=")
                            append(elapsedNs[operation] ?: 0L)
                        }
                    }
                }
            }
        }
}
