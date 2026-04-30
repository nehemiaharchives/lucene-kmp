package org.gnit.lucenekmp.jdkport

/**
 * Port of java.nio.file.ProviderMismatchException.
 *
 * Unchecked exception thrown when an attempt is made to access an object associated with a
 * different file system provider.
 */
@Ported(from = "java.nio.file.ProviderMismatchException")
class ProviderMismatchException : IllegalArgumentException {
    constructor() : super()
    constructor(message: String) : super(message)
}
