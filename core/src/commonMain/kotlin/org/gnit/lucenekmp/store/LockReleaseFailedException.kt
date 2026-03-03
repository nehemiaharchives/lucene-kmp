package org.gnit.lucenekmp.store

import okio.IOException

/**
 * This exception is thrown when the `write.lock` could not be released.
 *
 * @see Lock.close
 */
class LockReleaseFailedException : IOException {
    constructor(message: String) : super(message)

    constructor(message: String, cause: Throwable) : super(message, cause)
}
