package org.gnit.lucenekmp.jdkport

/**
 * Annotation to indicate that a JDK class used by Java Lucene but no equivalent found in kotlin sdk.
 *
 * Example usage:
 * ```
 * package org.gnit.lucenekmp.jdkport
 *
 * @Ported(from = "java.io.Reader")
 * class Reader
 * ```
 */
annotation class Ported(val from: String)
