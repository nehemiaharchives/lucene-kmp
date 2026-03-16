package org.gnit.lucenekmp.tests.store

private class NativeOpenHandleTrace(
    private val message: String
) : OpenHandleTrace {
    override fun asException(): Exception = RuntimeException(message)

    override fun render(): String = message
}

internal actual fun createOpenHandleTrace(message: String): OpenHandleTrace =
    NativeOpenHandleTrace(message)
