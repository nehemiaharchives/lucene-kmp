package org.gnit.lucenekmp.util

import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
@PublishedApi
internal actual fun currentLoggerNameFromCallSite(): String {
    return Throwable().getStackTrace()
        .asSequence()
        .mapNotNull(::extractKotlinNativeFrameName)
        .firstOrNull(::isApplicationLoggerCaller)
        ?: "UnknownLogger"
}

private fun extractKotlinNativeFrameName(frame: String): String? {
    return Regex("""kfun:([^#(<]+)""").find(frame)?.groupValues?.get(1)?.trim()
}

private fun isApplicationLoggerCaller(name: String): Boolean {
    return !name.startsWith("kotlin.") &&
            !name.startsWith("io.github.oshai.kotlinlogging.") &&
            name != "org.gnit.lucenekmp.util" &&
            name != "org.gnit.lucenekmp.util.currentLoggerNameFromCallSite" &&
            name != "org.gnit.lucenekmp.util.getLogger" &&
            name != "org.gnit.lucenekmp.util.LoggingConfig"
}
