package org.gnit.lucenekmp.store

import okio.IOException


/**
 * This exception is thrown when the `write.lock` could not be acquired. This happens
 * when a writer tries to open an index that another writer already has open.
 *
 * @see LockFactory.obtainLock
 */
class LockObtainFailedException : IOException {
    constructor(message: String) : super(message)

    constructor(message: String, cause: Throwable) : super(message, cause)
}
