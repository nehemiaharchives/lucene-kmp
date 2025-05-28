package org.gnit.lucenekmp.jdkport

import okio.IOException


/**
 * Wraps an [IOException] with an unchecked exception.
 *
 * @since   1.8
 */
class UncheckedIOException : RuntimeException {
    /**
     * Constructs an instance of this class.
     *
     * @param   message
     * the detail message, can be null
     * @param   cause
     * the `IOException`
     *
     * @throws  NullPointerException
     * if the cause is `null`
     */
    constructor(message: String, cause: IOException) : super(
        message,
        cause
    )

    /**
     * Constructs an instance of this class.
     *
     * @param   cause
     * the `IOException`
     *
     * @throws  NullPointerException
     * if the cause is `null`
     */
    constructor(cause: IOException) : super(cause)

    override val cause: IOException
        /**
         * Returns the cause of this exception.
         *
         * @return  the `IOException` which is the cause of this exception.
         */
        get() = super.cause as IOException

    /**
     * Called to read the object from a stream.
     *
     * @param  s the `ObjectInputStream` from which data is read
     * @throws IOException if an I/O error occurs
     * @throws ClassNotFoundException if a serialized class cannot be loaded
     * @throws  InvalidObjectException
     * if the object is invalid or has a cause that is not
     * an `IOException`
     */
    /*
    // NOT_TODO will not be implemented until needed

    @Throws(IOException::class, ClassNotFoundException::class)
    private fun readObject(s: ObjectInputStream) {
        s.defaultReadObject()
        val cause: Throwable = super.cause
        if (cause !is IOException) throw InvalidObjectException("Cause must be an IOException")
    }*/

}
