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
    val kfunPrefix = "kfun:"
    val kfunStart = frame.indexOf(kfunPrefix)
    if (kfunStart == -1) {
        return null
    }
    val nameStart = kfunStart + kfunPrefix.length
    val nameEnd = firstNameDelimiter(frame, nameStart)
    if (nameEnd <= nameStart) {
        return null
    }
    return frame.substring(nameStart, nameEnd)
}

private fun firstNameDelimiter(frame: String, startIndex: Int): Int {
    var result = frame.length
    result = minDelimiterIndex(result, frame.indexOf('#', startIndex))
    result = minDelimiterIndex(result, frame.indexOf('(', startIndex))
    result = minDelimiterIndex(result, frame.indexOf('<', startIndex))
    result = minDelimiterIndex(result, frame.indexOf(' ', startIndex))
    return result
}

private fun minDelimiterIndex(current: Int, candidate: Int): Int {
    return if (candidate != -1 && candidate < current) candidate else current
}

private fun isApplicationLoggerCaller(name: String): Boolean {
    return !name.startsWith("kotlin.") &&
        !name.startsWith("io.github.oshai.kotlinlogging.") &&
        name != "org.gnit.lucenekmp.util" &&
        name != "org.gnit.lucenekmp.util.currentLoggerNameFromCallSite" &&
        name != "org.gnit.lucenekmp.util.extractKotlinNativeFrameName" &&
        name != "org.gnit.lucenekmp.util.getLogger" &&
        name != "org.gnit.lucenekmp.util.LoggingConfig"
}
