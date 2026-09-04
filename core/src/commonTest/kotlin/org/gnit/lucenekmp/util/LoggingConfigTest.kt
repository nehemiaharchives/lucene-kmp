package org.gnit.lucenekmp.util

import io.github.oshai.kotlinlogging.KotlinLoggingConfiguration
import io.github.oshai.kotlinlogging.Level
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LoggingConfigTest {
    @Test
    fun missingProductionLogLevelPreservesHostConfiguration() {
        val previousLogStartupMessage = KotlinLoggingConfiguration.logStartupMessage
        val previousLoggerFactory = KotlinLoggingConfiguration.loggerFactory
        val previousLogLevel = KotlinLoggingConfiguration.direct.logLevel

        try {
            KotlinLoggingConfiguration.logStartupMessage = true
            KotlinLoggingConfiguration.direct.logLevel = Level.WARN

            configureProductionLogging(null)

            assertTrue(KotlinLoggingConfiguration.logStartupMessage)
            assertSame(previousLoggerFactory, KotlinLoggingConfiguration.loggerFactory)
            assertEquals(Level.WARN, KotlinLoggingConfiguration.direct.logLevel)
        } finally {
            KotlinLoggingConfiguration.logStartupMessage = previousLogStartupMessage
            KotlinLoggingConfiguration.loggerFactory = previousLoggerFactory
            KotlinLoggingConfiguration.direct.logLevel = previousLogLevel
        }
    }
}
