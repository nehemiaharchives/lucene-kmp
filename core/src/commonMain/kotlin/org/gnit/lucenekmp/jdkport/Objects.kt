package org.gnit.lucenekmp.jdkport

/**
 * partially ported from java.util.Objects
 *
 */
object Objects {
    fun hashCode(o: Any?): Int {
        return if (o != null) o.hashCode() else 0
    }

    /**
     * {@return a hash code for a sequence of input values} The hash
     * code is generated as if all the input values were placed into an
     * array, and that array were hashed by calling [ ][Arrays.hashCode].
     *
     *
     * This method is useful for implementing [ ][Object.hashCode] on objects containing multiple fields. For
     * example, if an object that has three fields, `x`, `y`, and `z`, one could write:
     *
     * <blockquote><pre>
     * &#064;Override public int hashCode() {
     * return Objects.hash(x, y, z);
     * }
    </pre></blockquote> *
     *
     * **Warning: When a single object reference is supplied, the returned
     * value does not equal the hash code of that object reference.** This
     * value can be computed by calling [.hashCode].
     *
     * @param values the values to be hashed
     * @see Arrays.hashCode
     * @see List.hashCode
     */
    fun hash(vararg values: Any?): Int {
        return when {
            values.isEmpty() -> 0
            values.size == 1 -> values[0]?.hashCode() ?: 0
            else -> values.contentHashCode()
        }
    }

    fun checkFromIndexSize(fromIndex: Int, size: Int, length: Int): Int {
        if (fromIndex < 0 || size < 0 || fromIndex > length - size) {
            throw IndexOutOfBoundsException("fromIndex: $fromIndex, size: $size, length: $length")
        }
        return fromIndex
    }

    fun checkFromToIndex(fromIndex: Int, toIndex: Int, length: Int): Int {
        if (fromIndex < 0 || fromIndex > toIndex || toIndex > length) {
            throw IndexOutOfBoundsException(
                "fromIndex $fromIndex, toIndex $toIndex out of bounds for length $length"
            )
        }
        return fromIndex
    }

    fun checkIndex(index: Int, length: Int): Int =
        checkIndex(index, length) { message, _ -> IndexOutOfBoundsException(message) }

    fun checkIndex(
        index: Int,
        length: Int,
        oobef: (String, List<Number>) -> RuntimeException
    ): Int {
        if (index < 0 || index >= length) {
            throw outOfBoundsCheckIndex(oobef, index, length)
        }
        return index
    }

    private fun outOfBoundsCheckIndex(
        oobef: (String, List<Number>) -> RuntimeException,
        index: Int,
        length: Int
    ): RuntimeException {
        return oobef("Index $index out of bounds for length $length", listOf(index, length))
    }

    /**
     * {@return the result of calling {@code toString} for a
     * * non-{@code null} argument and {@code "null"} for a
     * * {@code null} argument}
     *
     * @param o an object
     * @see Object.toString
     *
     * @see String.valueOf
     */
    fun toString(o: Any?): String {
        return o?.toString() ?: "null"
    }

    /**
     * Returns {@code true} if the arguments are equal to each other
     * and {@code false} otherwise.
     * Consequently, if both arguments are {@code null}, {@code true}
     * is returned.  Otherwise, if the first argument is not {@code null},
     * equality is determined by calling the {@code equals} method of
     * the first argument with the second argument as a parameter.
     *
     * @param a an object
     * @param b an object to be compared with {@code a} for equality
     * @return {@code true} if the arguments are equal to each other
     * and {@code false} otherwise
     * @see Object.equals
     */
    fun equals(a: Any?, b: Any?): Boolean {
        return a === b || (a != null && a.equals(b))
    }

    fun isNull(obj: Any?): Boolean {
        return obj == null
    }
}


/**
 * Copies characters from this StringBuilder into [dst].
 *
 * @param srcBegin the starting index in this builder (inclusive)
 * @param srcEnd the ending index in this builder (exclusive)
 * @param dst the destination char array
 * @param dstBegin the starting index in the destination array
 * @throws IndexOutOfBoundsException if the indices are out of range
 */
fun StringBuilder.getChars(srcBegin: Int, srcEnd: Int, dst: CharArray, dstBegin: Int) {
    // Check the source range against the builder's length.
    Objects.checkFromToIndex(srcBegin, srcEnd, this.length)
    val n = srcEnd - srcBegin
    // Check the destination range against the destination array length.
    Objects.checkFromToIndex(dstBegin, dstBegin + n, dst.size)

    // Copy characters from this builder to dst.
    for (i in srcBegin until srcEnd) {
        dst[dstBegin + i - srcBegin] = this[i]
    }
}


/**
 * Appends the Unicode code point represented by [codePoint] to this StringBuilder.
 *
 * If [codePoint] is in the Basic Multilingual Plane (i.e. less than 0x10000),
 * a single character is appended. Otherwise, it is converted to a surrogate pair.
 *
 * @param codePoint the Unicode code point to append.
 * @return this StringBuilder.
 * @throws IllegalArgumentException if [codePoint] is not a valid Unicode code point.
 */
fun StringBuilder.appendCodePoint(codePoint: Int): StringBuilder {
    require(codePoint in 0..0x10FFFF) { "Invalid Unicode code point: $codePoint" }
    return if (codePoint < 0x10000) {
        append(codePoint.toChar())
    } else {
        val cpPrime = codePoint - 0x10000
        val high = ((cpPrime shr 10) + 0xD800).toChar()
        val low = ((cpPrime and 0x3FF) + 0xDC00).toChar()
        append(high)
        append(low)
        this
    }
}


/**
 * Returns the Unicode code point at the specified index of this CharSequence.
 *
 * If the character at [index] is a high surrogate and the following character is a low surrogate,
 * then the supplementary code point corresponding to the surrogate pair is returned.
 * Otherwise, the code of the single character at [index] is returned.
 *
 * @throws IndexOutOfBoundsException if the index is negative or not less than [length].
 */
fun CharSequence.codePointAt(index: Int): Int {
    checkIndex(index, length)
    val first = this[index]
    // Check for surrogate pair
    if (first.isHighSurrogate() && index + 1 < length) {
        val second = this[index + 1]
        if (second.isLowSurrogate()) {
            return toCodePoint(first, second)
        }
    }
    return first.code
}


/**
 * Checks if the given index is within bounds (0 until length).
 *
 * @throws IndexOutOfBoundsException if the index is out of bounds.
 */
fun checkIndex(index: Int, length: Int) {
    require(index in 0 until length) { "Index $index out of bounds for length $length" }
}

/**
 * Converts a surrogate pair into the corresponding Unicode code point.
 */
fun toCodePoint(high: Char, low: Char): Int =
    ((high.code - 0xD800) shl 10) + (low.code - 0xDC00) + 0x10000

/**
 * Extension functions for surrogate detection.
 */
fun Char.isHighSurrogate(): Boolean = this in '\uD800'..'\uDBFF'
fun Char.isLowSurrogate(): Boolean = this in '\uDC00'..'\uDFFF'
