package org.gnit.lucenekmp.jdkport

/**
 * This function had to be created because as of Kotlin 2.1.21 or around, as of May 2025 assert() function does not exist in
 * kotlin common standard library. It exists in JVM and Native, so we will delegate to them for now.
 * When std lib get assert in common, we will remove this function and use the std lib assert() instead.
 *
 * @param condition The boolean condition to check. If false, an assertion error is thrown.
 * @param lazyMessage A lambda that produces the error message if the assertion fails. Defaults to "assertion failed".
 * @return Returns Unit if the assertion passes; otherwise, throws an AssertionError.
 *
 * Delegates to platform-specific implementations on JVM and Native.
 * Remove this function when the standard library provides a common `assert`.
 */
//@PublishedApi
expect inline fun assert(
    condition: Boolean,
    lazyMessage: () -> Any = { "assertion failed" }
)

// TODO need to change like below:
//ref: https://blog.nnn.dev/entry/2025/07/15/110000
/*
inline fun debugAssert(condition: () -> Boolean, message: () -> String) {
    if (AssertionFlag.enabled) {
        if (!condition()) {
            throw AssertionError(message())
        }
    }
}

object AssertionFlag {
    val enabled: Boolean = javaClass.desiredAssertionStatus() // TODO need to figure out what to do in KMP, Kotlin/Common
}
*/
