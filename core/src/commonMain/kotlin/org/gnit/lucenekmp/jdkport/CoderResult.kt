package org.gnit.lucenekmp.jdkport

import org.gnit.lucenekmp.jdkport.CoderResult.Cache.Companion.INSTANCE

/**
 * A description of the result state of a coder.
 *
 *
 *  A charset coder, that is, either a decoder or an encoder, consumes bytes
 * (or characters) from an input buffer, translates them, and writes the
 * resulting characters (or bytes) to an output buffer.  A coding process
 * terminates for one of four categories of reasons, which are described by
 * instances of this class:
 *
 *
 *
 *  *
 *
 * *Underflow* is reported when there is no more input to be
 * processed, or there is insufficient input and additional input is
 * required.  This condition is represented by the unique result object
 * [.UNDERFLOW], whose [isUnderflow][.isUnderflow] method
 * returns `true`.
 *
 *  *
 *
 * *Overflow* is reported when there is insufficient room
 * remaining in the output buffer.  This condition is represented by the
 * unique result object [.OVERFLOW], whose [   isOverflow][.isOverflow] method returns `true`.
 *
 *  *
 *
 * A *malformed-input error* is reported when a sequence of
 * input units is not well-formed.  Such errors are described by instances of
 * this class whose [isMalformed][.isMalformed] method returns
 * `true` and whose [length][.length] method returns the length
 * of the malformed sequence.  There is one unique instance of this class for
 * all malformed-input errors of a given length.
 *
 *  *
 *
 * An *unmappable-character error* is reported when a sequence
 * of input units denotes a character that cannot be represented in the
 * output charset.  Such errors are described by instances of this class
 * whose [isUnmappable][.isUnmappable] method returns `true` and
 * whose [length][.length] method returns the length of the input
 * sequence denoting the unmappable character.  There is one unique instance
 * of this class for all unmappable-character errors of a given length.
 *
 *
 *
 *
 *
 *  For convenience, the [isError][.isError] method returns `true`
 * for result objects that describe malformed-input and unmappable-character
 * errors but `false` for those that describe underflow or overflow
 * conditions.
 *
 *
 * @author Mark Reinhold
 * @author JSR-51 Expert Group
 * @since 1.4
 */
class CoderResult private constructor(private val type: Int, private val length: Int) {
    /**
     * Returns a string describing this coder result.
     *
     * @return  A descriptive string
     */
    override fun toString(): String {
        val nm = names[type]
        return if (this.isError) "$nm[$length]" else nm
    }

    val isUnderflow: Boolean
        /**
         * Tells whether or not this object describes an underflow condition.
         *
         * @return  `true` if, and only if, this object denotes underflow
         */
        get() = (type == CR_UNDERFLOW)

    val isOverflow: Boolean
        /**
         * Tells whether or not this object describes an overflow condition.
         *
         * @return  `true` if, and only if, this object denotes overflow
         */
        get() = (type == CR_OVERFLOW)

    val isError: Boolean
        /**
         * Tells whether or not this object describes an error condition.
         *
         * @return  `true` if, and only if, this object denotes either a
         * malformed-input error or an unmappable-character error
         */
        get() = (type >= CR_ERROR_MIN)

    val isMalformed: Boolean
        /**
         * Tells whether or not this object describes a malformed-input error.
         *
         * @return  `true` if, and only if, this object denotes a
         * malformed-input error
         */
        get() = (type == CR_MALFORMED)

    val isUnmappable: Boolean
        /**
         * Tells whether or not this object describes an unmappable-character
         * error.
         *
         * @return  `true` if, and only if, this object denotes an
         * unmappable-character error
         */
        get() = (type == CR_UNMAPPABLE)

    /**
     * Returns the length of the erroneous input described by this
     * object&nbsp;&nbsp;*(optional operation)*.
     *
     * @return  The length of the erroneous input, a positive integer
     *
     * @throws  UnsupportedOperationException
     * If this object does not describe an error condition, that is,
     * if the [isError][.isError] does not return `true`
     */
    fun length(): Int {
        if (!this.isError) throw UnsupportedOperationException()
        return length
    }

    private class Cache {
        val unmappable: MutableMap<Int, CoderResult> = HashMap()
        val malformed: MutableMap<Int, CoderResult> = HashMap()

        companion object {
            val INSTANCE: Cache = Cache()
        }
    }

    /**
     * Throws an exception appropriate to the result described by this object.
     *
     * @throws  BufferUnderflowException
     * If this object is [.UNDERFLOW]
     *
     * @throws  BufferOverflowException
     * If this object is [.OVERFLOW]
     *
     * @throws  MalformedInputException
     * If this object represents a malformed-input error; the
     * exception's length value will be that of this object
     *
     * @throws  UnmappableCharacterException
     * If this object represents an unmappable-character error; the
     * exception's length value will be that of this object
     *
     * @throws  CharacterCodingException
     * `MalformedInputException` if this object represents a
     * malformed-input error; `UnmappableCharacterException`
     * if this object represents an unmappable-character error
     */
    @Throws(CharacterCodingException::class)
    fun throwException() {
        when (type) {
            CR_UNDERFLOW -> throw BufferUnderflowException()
            CR_OVERFLOW -> throw BufferOverflowException()
            CR_MALFORMED -> throw MalformedInputException(length)
            CR_UNMAPPABLE -> throw UnmappableCharacterException(length)
            else -> throw Exception()
        }
    }

    companion object {
        private const val CR_UNDERFLOW = 0
        private const val CR_OVERFLOW = 1
        private const val CR_ERROR_MIN = 2
        private const val CR_MALFORMED = 2
        private const val CR_UNMAPPABLE = 3

        private val names = arrayOf<String>("UNDERFLOW", "OVERFLOW", "MALFORMED", "UNMAPPABLE")

        /**
         * Result object indicating underflow, meaning that either the input buffer
         * has been completely consumed or, if the input buffer is not yet empty,
         * that additional input is required.
         */
        val UNDERFLOW
                : CoderResult = CoderResult(CR_UNDERFLOW, 0)

        /**
         * Result object indicating overflow, meaning that there is insufficient
         * room in the output buffer.
         */
        val OVERFLOW
                : CoderResult = CoderResult(CR_OVERFLOW, 0)

        private val malformed4 = arrayOf<CoderResult>(
            CoderResult(CR_MALFORMED, 1),
            CoderResult(CR_MALFORMED, 2),
            CoderResult(CR_MALFORMED, 3),
            CoderResult(CR_MALFORMED, 4),
        )

        /**
         * Static factory method that returns the unique object describing a
         * malformed-input error of the given length.
         *
         * @param   length
         * The given length
         *
         * @return  The requested coder-result object
         */
        fun malformedForLength(length: Int): CoderResult? {
            require(length > 0) { "Non-positive length" }
            if (length <= 4) return malformed4[length - 1]
            return INSTANCE.malformed.computeIfAbsent(
                length
            ) { n: Int -> CoderResult(CR_MALFORMED, n) }
        }

        private val unmappable4 = arrayOf<CoderResult>(
            CoderResult(CR_UNMAPPABLE, 1),
            CoderResult(CR_UNMAPPABLE, 2),
            CoderResult(CR_UNMAPPABLE, 3),
            CoderResult(CR_UNMAPPABLE, 4),
        )

        /**
         * Static factory method that returns the unique result object describing
         * an unmappable-character error of the given length.
         *
         * @param   length
         * The given length
         *
         * @return  The requested coder-result object
         */
        fun unmappableForLength(length: Int): CoderResult? {
            require(length > 0) { "Non-positive length" }
            if (length <= 4) return unmappable4[length - 1]
            return INSTANCE.unmappable.computeIfAbsent(
                length
            ) { n: Int -> CoderResult(CR_UNMAPPABLE, n) }
        }
    }
}
