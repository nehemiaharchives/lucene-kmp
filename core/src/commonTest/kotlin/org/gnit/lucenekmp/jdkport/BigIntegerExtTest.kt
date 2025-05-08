package org.gnit.lucenekmp.jdkport

import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class BigIntegerExtTest {

    @Test
    fun testValueOf() {
        // Test zero
        assertSame(BigInteger.ZERO, BigInteger.valueOf(0L), "Should return BigInteger.ZERO for 0L")

        // Test positive values within MAX_CONSTANT
        for (i in 1L..16L) {
            val result = BigInteger.valueOf(i)
            val expected = BigInteger.fromLong(i)
            assertEquals(expected, result, "Incorrect value for $i")
        }

        // Test negative values within -MAX_CONSTANT
        for (i in 1L..16L) {
            val result = BigInteger.valueOf(-i)
            val expected = BigInteger.fromLong(-i)
            assertEquals(expected, result, "Incorrect value for -$i")
        }

        // Test positive value outside MAX_CONSTANT
        val bigPos = 17L
        assertEquals(BigInteger.fromLong(bigPos), BigInteger.valueOf(bigPos), "Incorrect value for value above MAX_CONSTANT")

        // Test negative value outside -MAX_CONSTANT
        val bigNeg = -17L
        assertEquals(BigInteger.fromLong(bigNeg), BigInteger.valueOf(bigNeg), "Incorrect value for value below -MAX_CONSTANT")
    }
}