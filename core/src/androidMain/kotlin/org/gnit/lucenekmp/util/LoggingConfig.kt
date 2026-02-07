package org.gnit.lucenekmp.util

actual fun configureTestLogging() {
    // Android host unit tests run on the JVM without mocked android.util.Log.
    // Ensure kotlin-logging does NOT use native android Log backend in host tests.
    System.clearProperty("kotlin-logging-to-android-native")
}
