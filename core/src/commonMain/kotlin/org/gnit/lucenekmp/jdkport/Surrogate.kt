package org.gnit.lucenekmp.jdkport


/**
 * Utility class for dealing with surrogates.
 *
 * @author Mark Reinhold
 * @author Martin Buchholz
 * @author Ulf Zibis
 */
object Surrogate {
    // TODO: Deprecate/remove the following redundant definitions
    val MIN_HIGH: Char = Character.MIN_HIGH_SURROGATE
    val MAX_HIGH: Char = Character.MAX_HIGH_SURROGATE
    val MIN_LOW: Char = Character.MIN_LOW_SURROGATE
    val MAX_LOW: Char = Character.MAX_LOW_SURROGATE
    val MIN: Char = Character.MIN_SURROGATE
    val MAX: Char = Character.MAX_SURROGATE
    val UCS4_MIN: Int = Character.MIN_SUPPLEMENTARY_CODE_POINT
    val UCS4_MAX: Int = Character.MAX_CODE_POINT

    /**
     * Tells whether or not the given value is in the high surrogate range.
     * Use of [Character.isHighSurrogate] is generally preferred.
     */
    fun isHigh(c: Int): Boolean {
        return (MIN_HIGH.code <= c) && (c <= MAX_HIGH.code)
    }

    /**
     * Tells whether or not the given value is in the low surrogate range.
     * Use of [Character.isLowSurrogate] is generally preferred.
     */
    fun isLow(c: Int): Boolean {
        return (MIN_LOW.code <= c) && (c <= MAX_LOW.code)
    }

    /**
     * Tells whether or not the given value is in the surrogate range.
     * Use of [Character.isSurrogate] is generally preferred.
     */
    fun `is`(c: Int): Boolean {
        return (MIN.code <= c) && (c <= MAX.code)
    }

    /**
     * Tells whether or not the given UCS-4 character must be represented as a
     * surrogate pair in UTF-16.
     * Use of [Character.isSupplementaryCodePoint] is generally preferred.
     */
    fun neededFor(uc: Int): Boolean {
        return Character.isSupplementaryCodePoint(uc)
    }

    /**
     * Returns the high UTF-16 surrogate for the given supplementary UCS-4 character.
     * Use of [Character.highSurrogate] is generally preferred.
     */
    fun high(uc: Int): Char {
        require(Character.isSupplementaryCodePoint(uc))
        return Character.highSurrogate(uc)
    }

    /**
     * Returns the low UTF-16 surrogate for the given supplementary UCS-4 character.
     * Use of [Character.lowSurrogate] is generally preferred.
     */
    fun low(uc: Int): Char {
        require(Character.isSupplementaryCodePoint(uc))
        return Character.lowSurrogate(uc)
    }

    /**
     * Converts the given surrogate pair into a 32-bit UCS-4 character.
     * Use of [Character.toCodePoint] is generally preferred.
     */
    fun toUCS4(c: Char, d: Char): Int {
        require(Character.isHighSurrogate(c) && d.isLowSurrogate())
        return Character.toCodePoint(c, d)
    }

    /**
     * Surrogate parsing support.  Charset implementations may use instances of
     * this class to handle the details of parsing UTF-16 surrogate pairs.
     */
    class Parser {
        private var character = 0 // UCS-4
        private var error: CoderResult? = CoderResult.UNDERFLOW
        private var isPair = false

        /**
         * Returns the UCS-4 character previously parsed.
         */
        fun character(): Int {
            require(error == null)
            return character
        }

        /**
         * Tells whether or not the previously-parsed UCS-4 character was
         * originally represented by a surrogate pair.
         */
        fun isPair(): Boolean {
            require(error == null)
            return isPair
        }

        /**
         * Returns the number of UTF-16 characters consumed by the previous
         * parse.
         */
        fun increment(): Int {
            require(error == null)
            return if (isPair) 2 else 1
        }

        /**
         * If the previous parse operation detected an error, return the object
         * describing that error.
         */
        fun error(): CoderResult {
            checkNotNull(error)
            return error!!
        }

        /**
         * Returns an unmappable-input result object, with the appropriate
         * input length, for the previously-parsed character.
         */
        fun unmappableResult(): CoderResult {
            require(error == null)
            return CoderResult.unmappableForLength(if (isPair) 2 else 1)
        }

        /**
         * Parses a UCS-4 character from the given source buffer, handling
         * surrogates.
         *
         * @param  c    The first character
         * @param  in   The source buffer, from which one more character
         * will be consumed if c is a high surrogate
         *
         * @return  Either a parsed UCS-4 character, in which case the isPair()
         * and increment() methods will return meaningful values, or
         * -1, in which case error() will return a descriptive result
         * object
         */
        fun parse(c: Char, `in`: CharBuffer): Int {
            if (Character.isHighSurrogate(c)) {
                if (!`in`.hasRemaining()) {
                    error = CoderResult.UNDERFLOW
                    return -1
                }
                val d: Char = `in`.get()
                if (d.isLowSurrogate()) {
                    character = Character.toCodePoint(c, d)
                    isPair = true
                    error = null
                    return character
                }
                error = CoderResult.malformedForLength(1)
                return -1
            }
            if (c.isLowSurrogate()) {
                error = CoderResult.malformedForLength(1)
                return -1
            }
            character = c.code
            isPair = false
            error = null
            return character
        }

        /**
         * Parses a UCS-4 character from the given source buffer, handling
         * surrogates.
         *
         * @param  c    The first character
         * @param  ia   The input array, from which one more character
         * will be consumed if c is a high surrogate
         * @param  ip   The input index
         * @param  il   The input limit
         *
         * @return  Either a parsed UCS-4 character, in which case the isPair()
         * and increment() methods will return meaningful values, or
         * -1, in which case error() will return a descriptive result
         * object
         */
        fun parse(c: Char, ia: CharArray, ip: Int, il: Int): Int {
            require(ia[ip] == c)
            if (Character.isHighSurrogate(c)) {
                if (il - ip < 2) {
                    error = CoderResult.UNDERFLOW
                    return -1
                }
                val d = ia[ip + 1]
                if (d.isLowSurrogate()) {
                    character = Character.toCodePoint(c, d)
                    isPair = true
                    error = null
                    return character
                }
                error = CoderResult.malformedForLength(1)
                return -1
            }
            if (c.isLowSurrogate()) {
                error = CoderResult.malformedForLength(1)
                return -1
            }
            character = c.code
            isPair = false
            error = null
            return character
        }
    }

    /**
     * Surrogate generation support.  Charset implementations may use instances
     * of this class to handle the details of generating UTF-16 surrogate
     * pairs.
     */
    class Generator {
        private var error: CoderResult? = CoderResult.OVERFLOW

        /**
         * If the previous generation operation detected an error, return the
         * object describing that error.
         */
        fun error(): CoderResult {
            checkNotNull(error)
            return error!!
        }

        /**
         * Generates one or two UTF-16 characters to represent the given UCS-4
         * character.
         *
         * @param  uc   The UCS-4 character
         * @param  len  The number of input bytes from which the UCS-4 value
         * was constructed (used when creating result objects)
         * @param  dst  The destination buffer, to which one or two UTF-16
         * characters will be written
         *
         * @return  Either a positive count of the number of UTF-16 characters
         * written to the destination buffer, or -1, in which case
         * error() will return a descriptive result object
         */
        fun generate(uc: Int, len: Int, dst: CharBuffer): Int {
            if (Character.isBmpCodePoint(uc)) {
                val c = uc.toChar()
                if (c.isSurrogate()) {
                    error = CoderResult.malformedForLength(len)
                    return -1
                }
                if (dst.remaining() < 1) {
                    error = CoderResult.OVERFLOW
                    return -1
                }
                dst.put(c)
                error = null
                return 1
            } else if (Character.isValidCodePoint(uc)) {
                if (dst.remaining() < 2) {
                    error = CoderResult.OVERFLOW
                    return -1
                }
                dst.put(Character.highSurrogate(uc))
                dst.put(Character.lowSurrogate(uc))
                error = null
                return 2
            } else {
                error = CoderResult.unmappableForLength(len)
                return -1
            }
        }

        /**
         * Generates one or two UTF-16 characters to represent the given UCS-4
         * character.
         *
         * @param  uc   The UCS-4 character
         * @param  len  The number of input bytes from which the UCS-4 value
         * was constructed (used when creating result objects)
         * @param  da   The destination array, to which one or two UTF-16
         * characters will be written
         * @param  dp   The destination position
         * @param  dl   The destination limit
         *
         * @return  Either a positive count of the number of UTF-16 characters
         * written to the destination buffer, or -1, in which case
         * error() will return a descriptive result object
         */
        fun generate(uc: Int, len: Int, da: CharArray, dp: Int, dl: Int): Int {
            if (Character.isBmpCodePoint(uc)) {
                val c = uc.toChar()
                if (c.isSurrogate()) {
                    error = CoderResult.malformedForLength(len)
                    return -1
                }
                if (dl - dp < 1) {
                    error = CoderResult.OVERFLOW
                    return -1
                }
                da[dp] = c
                error = null
                return 1
            } else if (Character.isValidCodePoint(uc)) {
                if (dl - dp < 2) {
                    error = CoderResult.OVERFLOW
                    return -1
                }
                da[dp] = Character.highSurrogate(uc)
                da[dp + 1] = Character.lowSurrogate(uc)
                error = null
                return 2
            } else {
                error = CoderResult.unmappableForLength(len)
                return -1
            }
        }
    }
}
