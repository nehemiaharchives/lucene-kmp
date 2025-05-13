package org.gnit.lucenekmp.jdkport


/**
 * Compares two `short` values numerically.
 * The value returned is identical to what would be returned by:
 * <pre>
 * Short.valueOf(x).compareTo(Short.valueOf(y))
 * </pre>
 *
 * @param  x the first `short` to compare
 * @param  y the second `short` to compare
 * @return the value `0` if `x == y`;
 * a value less than `0` if `x < y`; and
 * a value greater than `0` if `x > y`
 * @since 1.7
 */
fun Short.Companion.compare(x: Short, y: Short): Int {
    return if (x < y) -1 else (if (x == y) 0 else 1)
}

/**
 * Converts the argument to an `int` by an unsigned
 * conversion.  In an unsigned conversion to an `int`, the
 * high-order 16 bits of the `int` are zero and the
 * low-order 16 bits are equal to the bits of the `short` argument.
 *
 * Consequently, zero and positive `short` values are mapped
 * to a numerically equal `int` value and negative `short` values are mapped to an `int` value equal to the
 * input plus 2<sup>16</sup>.
 *
 * @param  x the value to convert to an unsigned `int`
 * @return the argument converted to `int` by an unsigned
 * conversion
 * @since 1.8
 */
fun Short.Companion.toUnsignedInt(x: Short): Int {
    return (x.toInt()) and 0xffff
}

/**
 * Converts the argument to a `long` by an unsigned
 * conversion.  In an unsigned conversion to a `long`, the
 * high-order 48 bits of the `long` are zero and the
 * low-order 16 bits are equal to the bits of the `short` argument.
 *
 * Consequently, zero and positive `short` values are mapped
 * to a numerically equal `long` value and negative `short` values are mapped to a `long` value equal to the
 * input plus 2<sup>16</sup>.
 *
 * @param  x the value to convert to an unsigned `long`
 * @return the argument converted to `long` by an unsigned
 * conversion
 * @since 1.8
 */
fun Short.Companion.toUnsignedLong(x: Short): Long {
    return (x.toLong()) and 0xffffL
}

/**
 * Returns the value obtained by reversing the order of the bytes in the
 * two's complement representation of the specified `short` value.
 *
 * @param i the value whose bytes are to be reversed
 * @return the value obtained by reversing (or, equivalently, swapping)
 * the bytes in the specified `short` value.
 * @since 1.5
 */
fun Short.Companion.reverseBytes(i: Short): Short {
    return (((i.toInt() and 0xFF00) shr 8) or (i.toInt() shl 8)).toShort()
}
