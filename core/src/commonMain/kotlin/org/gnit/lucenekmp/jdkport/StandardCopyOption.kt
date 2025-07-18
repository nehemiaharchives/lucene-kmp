package org.gnit.lucenekmp.jdkport

/**
 * port of java.nio.file.StandardCopyOption
 *
 * Defines the standard copy options.
 *
 * @since 1.7
 */
enum class StandardCopyOption : CopyOption {
    /**
     * Replace an existing file if it exists.
     */
    REPLACE_EXISTING,

    /**
     * Copy attributes to the new file.
     */
    COPY_ATTRIBUTES,

    /**
     * Move the file as an atomic file system operation.
     */
    ATOMIC_MOVE
}
