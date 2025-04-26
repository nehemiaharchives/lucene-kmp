package org.gnit.lucenekmp.jdkport


/**
 * A typesafe enumeration for coding-error actions.
 *
 *
 *  Instances of this class are used to specify how malformed-input and
 * unmappable-character errors are to be handled by charset [decoders](CharsetDecoder.html#cae) and [encoders](CharsetEncoder.html#cae).
 *
 *
 * @author Mark Reinhold
 * @author JSR-51 Expert Group
 * @since 1.4
 */
class CodingErrorAction private constructor(private val name: String) {
    /**
     * Returns a string describing this action.
     *
     * @return  A descriptive string
     */
    override fun toString(): String {
        return name
    }

    companion object {
        /**
         * Action indicating that a coding error is to be handled by dropping the
         * erroneous input and resuming the coding operation.
         */
        val IGNORE
                : CodingErrorAction = CodingErrorAction("IGNORE")

        /**
         * Action indicating that a coding error is to be handled by dropping the
         * erroneous input, appending the coder's replacement value to the output
         * buffer, and resuming the coding operation.
         */
        val REPLACE
                : CodingErrorAction = CodingErrorAction("REPLACE")

        /**
         * Action indicating that a coding error is to be reported, either by
         * returning a [CoderResult] object or by throwing a [ ], whichever is appropriate for the method
         * implementing the coding process.
         */
        val REPORT
                : CodingErrorAction = CodingErrorAction("REPORT")
    }
}
