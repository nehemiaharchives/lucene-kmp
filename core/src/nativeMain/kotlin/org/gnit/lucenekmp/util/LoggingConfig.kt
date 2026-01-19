package org.gnit.lucenekmp.util

import io.github.oshai.kotlinlogging.KotlinLoggingConfiguration
import io.github.oshai.kotlinlogging.Level

actual fun configureTestLogging() {
    KotlinLoggingConfiguration.logLevel = Level.DEBUG
}
