package org.gnit.lucenekmp.jdkport


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

