package org.gnit.lucenekmp.jdkport

/**
 * A multiplatform-friendly clone interface.
 * Unlike Java’s Cloneable, this interface declares a [clone] function that returns a copy.
 */
@Ported(from = "java.lang.Cloneable")
interface Cloneable<T> {
    fun clone(): T
}
