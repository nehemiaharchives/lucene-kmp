package org.gnit.lucenekmp.tests.store

private class AndroidOpenHandleTrace(
    private val exception: Exception
) : OpenHandleTrace {
    override fun asException(): Exception = exception

    override fun render(): String = exception.stackTraceToString()
}

internal actual fun createOpenHandleTrace(message: String): OpenHandleTrace =
    AndroidOpenHandleTrace(RuntimeException(message))
