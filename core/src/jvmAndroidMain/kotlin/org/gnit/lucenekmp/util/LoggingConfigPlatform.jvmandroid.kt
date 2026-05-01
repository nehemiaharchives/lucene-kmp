package org.gnit.lucenekmp.util

@PublishedApi
internal actual fun currentLoggerNameFromCallSite(): String {
    return Throwable().stackTrace
        .asSequence()
        .map { it.className }
        .firstOrNull(::isApplicationLoggerCaller)
        ?: "UnknownLogger"
}

private fun isApplicationLoggerCaller(className: String): Boolean {
    return !className.startsWith("java.") &&
            !className.startsWith("jdk.") &&
            !className.startsWith("kotlin.") &&
            !className.startsWith("io.github.oshai.kotlinlogging.") &&
            className != "org.gnit.lucenekmp.util.LoggingConfigKt" &&
            className != "org.gnit.lucenekmp.util.LoggingConfigPlatform_jvmandroidKt"
}
