package org.gnit.lucenekmp.jdkport

/**
 * Exception thrown when a blocking operation times out.  Blocking
 * operations for which a timeout is specified need a means to
 * indicate that the timeout has occurred. For many such operations it
 * is possible to return a value that indicates timeout; when that is
 * not possible or desirable then `TimeoutException` should be
 * declared and thrown.
 *
 * @since 1.5
 * @author Doug Lea
 */
open class TimeoutException : Exception {
    /**
     * Constructs a `TimeoutException` with no specified detail
     * message.
     */
    constructor()

    /**
     * Constructs a `TimeoutException` with the specified detail
     * message.
     *
     * @param message the detail message
     */
    constructor(message: String?) : super(message)

}
