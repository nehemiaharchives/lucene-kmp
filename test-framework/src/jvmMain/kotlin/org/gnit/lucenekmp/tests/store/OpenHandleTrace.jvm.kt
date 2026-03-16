package org.gnit.lucenekmp.tests.store

private class JvmOpenHandleTrace(
    private val exception: Exception
) : OpenHandleTrace {
    override fun asException(): Exception = exception

    override fun render(): String = exception.stackTraceToString()
}

internal actual fun createOpenHandleTrace(message: String): OpenHandleTrace =
    JvmOpenHandleTrace(RuntimeException(message))
