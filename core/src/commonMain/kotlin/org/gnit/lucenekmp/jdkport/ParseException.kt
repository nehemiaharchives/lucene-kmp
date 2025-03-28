package org.gnit.lucenekmp.jdkport


/**
 * ported from java.text.ParseException
 *
 * Signals that an error has been reached unexpectedly
 * while parsing.
 * @see java.lang.Exception
 *
 * @see java.text.Format
 *
 * @see java.text.FieldPosition
 *
 * @author      Mark Davis
 * @since 1.1
 * Constructs a ParseException with the specified detail message and
 * offset.
 * A detail message is a String that describes this particular exception.
 *
 * @param s the detail message
 * @param errorOffset the position where the error is found while parsing.
 */
class ParseException(
    message: String,

    /**
     * The zero-based character offset into the string being parsed at which
     * the error was found during parsing.
     * @serial
     */
    val errorOffset: Int) : Exception(message) {

    private var _cause: Throwable? = null

    override val cause: Throwable? get() = _cause

    fun initCause(cause: Throwable): ParseException {
        if (_cause != null) {
            throw IllegalStateException("Can't overwrite cause", this)
        }
        if (cause === this) {
            throw IllegalArgumentException("Self-causation not permitted", this)
        }
        _cause = cause
        return this
    }
}
