package org.gnit.lucenekmp.jdkport

/**
 * ported from java.util.zip.DataFormatException
 *
 * Signals that a data format error has occurred.
 *
 * @author      David Connelly
 * @since 1.1
 */
class DataFormatException : Exception {
    /**
     * Constructs a DataFormatException with no detail message.
     */
    constructor() : super()

    /**
     * Constructs a DataFormatException with the specified detail message.
     * A detail message is a String that describes this particular exception.
     * @param s the String containing a detail message
     */
    constructor(s: String?) : super(s)

}
