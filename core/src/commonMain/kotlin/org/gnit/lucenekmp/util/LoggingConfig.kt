package org.gnit.lucenekmp.util

import dev.scottpierce.envvar.EnvVar
import io.github.oshai.kotlinlogging.DirectLoggerFactory
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.KotlinLoggingConfiguration
import io.github.oshai.kotlinlogging.Level

private const val LOG_LEVEL_ENV_VAR = "LUCENEKMP_LOG_LEVEL"

private var luceneLoggingConfigured = false

private fun configureLogging(level: Level) {
    KotlinLoggingConfiguration.logStartupMessage = false
    KotlinLoggingConfiguration.loggerFactory = DirectLoggerFactory
    KotlinLoggingConfiguration.direct.logLevel = level
    luceneLoggingConfigured = true
}

private fun parseConfiguredLogLevel(): Level {
    return when (EnvVar[LOG_LEVEL_ENV_VAR]?.uppercase()) {
        "TRACE" -> Level.TRACE
        "DEBUG" -> Level.DEBUG
        "INFO" -> Level.INFO
        "WARN" -> Level.WARN
        "ERROR" -> Level.ERROR
        else -> Level.OFF
    }
}

@PublishedApi
internal fun ensureProductionLoggingConfigured() {
    if (!luceneLoggingConfigured) {
        configureLogging(parseConfiguredLogLevel())
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun getLogger(): KLogger {
    ensureProductionLoggingConfigured()
    return KotlinLogging.logger(currentLoggerNameFromCallSite())
}

fun configureTestLogging() {
    configureLogging(Level.DEBUG)
}

@PublishedApi
internal expect fun currentLoggerNameFromCallSite(): String
