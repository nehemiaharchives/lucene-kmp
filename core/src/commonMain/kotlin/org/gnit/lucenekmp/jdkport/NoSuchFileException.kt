package org.gnit.lucenekmp.jdkport


/**
 * port of java.nio.file.NoSuchFileException
 *
 * Checked exception thrown when an attempt is made to access a file that does
 * not exist.
 *
 * @since 1.7
 */
class NoSuchFileException : FileSystemException {
    /**
     * Constructs an instance of this class.
     *
     * @param   file
     * a string identifying the file or `null` if not known.
     */
    constructor(file: String) : super(file)

    /**
     * Constructs an instance of this class.
     *
     * @param   file
     * a string identifying the file or `null` if not known.
     * @param   other
     * a string identifying the other file or `null` if not known.
     * @param   reason
     * a reason message with additional information or `null`
     */
    constructor(file: String, other: String, reason: String) : super(file, other, reason)

}
