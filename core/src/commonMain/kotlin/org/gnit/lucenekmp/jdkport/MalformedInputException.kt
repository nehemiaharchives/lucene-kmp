package org.gnit.lucenekmp.jdkport

/**
 * ported from java.nio.charset.MalformedInputException
 *
 * Checked exception thrown when an input byte sequence is not legal for given
 * charset, or an input character sequence is not a legal sixteen-bit Unicode
 * sequence.
 *
 * @since 1.4
 */
class MalformedInputException

/**
 * Constructs an `MalformedInputException` with the given
 * length.
 * @param inputLength the length of the input
 */(
    /**
     * The length of the input.
     */
    val inputLength: Int
) : CharacterCodingException() {
    /**
     * Returns the length of the input.
     * @return the length of the input
     */

    override val message: String
        /**
         * Returns the message.
         * @return the message
         */
        get() = "Input length = $inputLength"

}
