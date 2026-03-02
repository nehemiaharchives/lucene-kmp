package org.gnit.lucenekmp.jdkport

/**
 * port of java.nio.file.AtomicMoveNotSupportedException
 *
 * Checked exception thrown when a file cannot be moved as an atomic file system operation.
 *
 * @since 1.7
 */
@Ported(from = "java.nio.file.AtomicMoveNotSupportedException")
class AtomicMoveNotSupportedException(
    source: String,
    target: String,
    reason: String
) : FileSystemException(source, target, reason)

