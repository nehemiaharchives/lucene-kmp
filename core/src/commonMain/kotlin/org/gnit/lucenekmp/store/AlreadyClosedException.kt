package org.gnit.lucenekmp.store

/**
 * This exception is thrown when there is an attempt to access something that has already been
 * closed.
 */
class AlreadyClosedException : IllegalStateException {
    constructor(message: String?) : super(message)

    constructor(message: String?, cause: Throwable?) : super(message, cause)
}
