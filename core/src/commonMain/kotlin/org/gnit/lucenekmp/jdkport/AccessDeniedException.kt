package org.gnit.lucenekmp.jdkport

/**
 * Port of java.nio.file.AccessDeniedException.
 *
 * Thrown when an attempt is made to access a file or directory and the
 * operation is denied.
 */
class AccessDeniedException : FileSystemException {
    constructor(file: String) : super(file)
    constructor(file: String, other: String, reason: String) : super(file, other, reason)
}
