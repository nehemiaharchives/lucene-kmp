package org.gnit.lucenekmp.jdkport

/**
 * Error thrown when the [decodeLoop][CharsetDecoder.decodeLoop] method of
 * a [CharsetDecoder], or the [ encodeLoop][CharsetEncoder.encodeLoop] method of a [CharsetEncoder], throws an unexpected
 * exception.
 *
 * @since 1.4
 */
class CoderMalfunctionError

/**
 * Initializes an instance of this class.
 *
 * @param  cause
 * The unexpected exception that was thrown
 */
    (cause: Exception?) : Error(cause) {

}
