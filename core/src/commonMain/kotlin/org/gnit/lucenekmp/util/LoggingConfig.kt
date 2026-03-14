package org.gnit.lucenekmp.util

import io.github.oshai.kotlinlogging.KotlinLoggingConfiguration
import io.github.oshai.kotlinlogging.Level

fun configureTestLogging() {
    KotlinLoggingConfiguration.direct.logLevel = Level.DEBUG
}
