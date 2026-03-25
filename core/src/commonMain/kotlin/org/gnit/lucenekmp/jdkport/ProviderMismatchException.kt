package org.gnit.lucenekmp.jdkport

/**
 * Port of java.nio.file.ProviderMismatchException.
 *
 * Unchecked exception thrown when an attempt is made to access an object associated with a
 * different file system provider.
 */
class ProviderMismatchException : IllegalArgumentException {
    constructor() : super()
    constructor(message: String) : super(message)
}
