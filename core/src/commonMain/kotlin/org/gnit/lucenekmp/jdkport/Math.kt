package org.gnit.lucenekmp.jdkport

object Math {
    /**
     * ported from java.lang.Math.toIntExact
     *
     * Returns the value of the `long` argument,
     * throwing an exception if the value overflows an `int`.
     *
     * @param value the long value
     * @return the argument as an int
     * @throws ArithmeticException if the `argument` overflows an int
     * @since 1.8
     */
    fun toIntExact(value: Long): Int {
        if (value.toInt().toLong() != value) {
            throw ArithmeticException("integer overflow")
        }
        return value.toInt()
    }

    /**
     * ported from java.lang.Math.addExact
     *
     * Returns true if the argument is a finite floating-point value;
     * returns false otherwise (for NaN and infinity arguments).
     *
     * This method corresponds to the isFinite operation defined in IEEE 754.
     *
     * @param d the double value to be tested
     * @return true if the argument is a finite floating-point value, false otherwise.
     */
    fun addExact(x: Long, y: Long): Long {
        val r = x + y
        if (((x xor r) and (y xor r)) < 0) {
            throw ArithmeticException("long overflow")
        }
        return r
    }

    /**
     * Returns the sum of its arguments,
     * throwing an exception if the result overflows an `int`.
     *
     * @param x the first value
     * @param y the second value
     * @return the result
     * @throws ArithmeticException if the result overflows an int
     * @since 1.8
     */
    fun addExact(x: Int, y: Int): Int {
        val r = x + y
        // HD 2-12 Overflow iff both arguments have the opposite sign of the result
        if (((x xor r) and (y xor r)) < 0) {
            throw ArithmeticException("integer overflow")
        }
        return r
    }
}