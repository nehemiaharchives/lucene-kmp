package org.gnit.lucenekmp.jdkport

/**
 * Checked exception thrown when an input character (or byte) sequence
 * is valid but cannot be mapped to an output byte (or character)
 * sequence.
 *
 * @since 1.4
 */
class UnmappableCharacterException

/**
 * Constructs an `UnmappableCharacterException` with the
 * given length.
 * @param inputLength the length of the input
 */(
    /**
     * The length of the input character (or byte) sequence.
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
