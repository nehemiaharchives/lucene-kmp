package org.gnit.lucenekmp.tests.store

internal interface OpenHandleTrace {
    fun asException(): Exception

    fun render(): String
}

internal expect fun createOpenHandleTrace(message: String): OpenHandleTrace
