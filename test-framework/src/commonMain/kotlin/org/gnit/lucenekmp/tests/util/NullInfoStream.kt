package org.gnit.lucenekmp.tests.util

import org.gnit.lucenekmp.util.InfoStream

/**
 * An [InfoStream] implementation that disables all logging.
 * It also asserts if `message` is called while disabled.
 */
class NullInfoStream : InfoStream() {
    override fun message(component: String, message: String) {
        require(false) { "message() should not be called when isEnabled returns false" }
    }

    override fun isEnabled(component: String): Boolean = false

    override fun close() { /* no-op */ }
}

