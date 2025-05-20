package org.gnit.lucenekmp.jdkport

import kotlinx.io.IOException

/**
 * Thrown to indicate that the IP address of a host could not be determined.
 *
 * @author  Jonathan Payne
 * @since   1.0
 */
class UnknownHostException : IOException {
    /**
     * Constructs a new `UnknownHostException` with the
     * specified detail message.
     *
     * @param   message   the detail message.
     */
    constructor(message: String?) : super(message)

    /**
     * Constructs a new `UnknownHostException` with no detail
     * message.
     */
    constructor()
}
