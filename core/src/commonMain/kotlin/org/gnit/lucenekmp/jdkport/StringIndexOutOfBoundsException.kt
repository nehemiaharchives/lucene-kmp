package org.gnit.lucenekmp.jdkport

/**
 * Thrown by `String` methods to indicate that an index is either negative
 * or greater than the size of the string.  For some methods such as the
 * [charAt][String.charAt] method, this exception also is thrown when the
 * index is equal to the size of the string.
 *
 * @see java.lang.String.charAt
 * @since 1.0
 */
@Ported(from = "java.lang.StringIndexOutOfBoundsException")
class StringIndexOutOfBoundsException : IndexOutOfBoundsException {
    /**
     * Constructs a `StringIndexOutOfBoundsException` with no detail
     * message.
     */
    constructor() : super()

    /**
     * Constructs a `StringIndexOutOfBoundsException` with the specified
     * detail message.
     *
     * @param s the detail message.
     */
    constructor(s: String?) : super(s)

    /**
     * Constructs a new `StringIndexOutOfBoundsException` class with an
     * argument indicating the illegal index.
     *
     *
     * The index is included in this exception's detail message.  The
     * exact presentation format of the detail message is unspecified.
     *
     * @param index the illegal index.
     */
    constructor(index: Int) : super("String index out of range: $index")
}
