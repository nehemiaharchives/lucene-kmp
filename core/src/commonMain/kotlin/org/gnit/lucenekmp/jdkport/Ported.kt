package org.gnit.lucenekmp.jdkport

/**
 * Annotation to indicate that a JDK class used by Java Lucene but no equivalent found in kotlin sdk.
 * Also, classes from com.carrotsearch.randomizedtesting.* library
 *
 * Example usage:
 * ```
 * package org.gnit.lucenekmp.jdkport
 *
 * @Ported(from = "java.io.Reader")
 * class Reader
 * ```
 *
 * This annotation is used in the `progressv2.main.kts` script to normalize the method/function signatures of java lucene classes and lucene-kmp ported counterparts.
 *
 */
annotation class Ported(val from: String)
