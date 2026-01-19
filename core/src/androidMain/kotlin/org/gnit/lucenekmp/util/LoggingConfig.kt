package org.gnit.lucenekmp.util

actual fun configureTestLogging() {
    System.setProperty("kotlin-logging-to-android-native", "true")
}
