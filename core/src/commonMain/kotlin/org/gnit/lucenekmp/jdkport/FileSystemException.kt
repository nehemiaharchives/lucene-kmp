package org.gnit.lucenekmp.jdkport

import okio.IOException

/**
 * Thrown when a file system operation fails on one or two files. This class is
 * the general class for file system exceptions.
 *
 * @since 1.7
 */
open class FileSystemException

    : IOException {
    /**
     * Returns the file used to create this exception.
     *
     * @return  the file (can be `null`)
     */
    /**
     * String identifying the file or `null` if not known.
     */
    val file: String

    /**
     * Returns the other file used to create this exception.
     *
     * @return  the other file (can be `null`)
     */
    /**
     * String identifying the other file or `null` if there isn't
     * another file or if not known.
     */
    val otherFile: String?

    /**
     * Constructs an instance of this class. This constructor should be used
     * when an operation involving one file fails and there isn't any additional
     * information to explain the reason.
     *
     * @param   file
     * a string identifying the file or `null` if not known.
     */
    constructor(file: String) : super(null as String) {
        this.file = file
        this.otherFile = null
    }

    /**
     * Constructs an instance of this class. This constructor should be used
     * when an operation involving two files fails, or there is additional
     * information to explain the reason.
     *
     * @param   file
     * a string identifying the file or `null` if not known.
     * @param   other
     * a string identifying the other file or `null` if there
     * isn't another file or if not known
     * @param   reason
     * a reason message with additional information or `null`
     */
    constructor(file: String, other: String, reason: String) : super(reason) {
        this.file = file
        this.otherFile = other
    }

    val reason: String?
        /**
         * Returns the string explaining why the file system operation failed.
         *
         * @return  the string explaining why the file system operation failed
         */
        get() = super.message

    override val message: String?
        /**
         * Returns the detail message string.
         */
        get() {
            if (file == null && this.otherFile == null) return this.reason
            val sb = StringBuilder()
            if (file != null) sb.append(file)
            if (this.otherFile != null) {
                sb.append(" -> ")
                sb.append(this.otherFile)
            }
            if (this.reason != null) {
                sb.append(": ")
                sb.append(this.reason)
            }
            return sb.toString()
        }

}
