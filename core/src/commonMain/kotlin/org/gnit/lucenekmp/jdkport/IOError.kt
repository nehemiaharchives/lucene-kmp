package org.gnit.lucenekmp.jdkport

/**
 * Port of `java.io.IOError`.
 */
@Ported(from = "java.io.IOError")
open class IOError(cause: Throwable) : Error(cause)
