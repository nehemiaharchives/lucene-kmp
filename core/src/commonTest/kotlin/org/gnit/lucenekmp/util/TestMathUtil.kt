package org.gnit.lucenekmp.util

import com.ionspin.kotlin.bignum.integer.BigInteger
import org.gnit.lucenekmp.jdkport.valueOf
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestMathUtil : LuceneTestCase() {
    private val PRIMES = longArrayOf(2, 3, 5, 7, 11, 13, 17, 19, 23, 29)

    private fun randomLong(): Long {
        val rnd = random()
        return if (rnd.nextBoolean()) {
            var l = 1L
            if (rnd.nextBoolean()) {
                l *= -1
            }
            for (p in PRIMES) {
                val m = rnd.nextInt(3)
                repeat(m) { l *= p }
            }
            l
        } else if (rnd.nextBoolean()) {
            rnd.nextLong()
        } else {
            listOf(Long.MIN_VALUE, Long.MAX_VALUE, 0L, -1L, 1L).random(rnd)
        }
    }

    // slow version used for testing
    private fun gcdSlow(l1: Long, l2: Long): Long {
        val gcd = BigInteger.valueOf(l1).gcd(BigInteger.valueOf(l2)).abs()
        return if (gcd.bitLength() > 63) {
            Long.MIN_VALUE
        } else {
            gcd.longValue(true)
        }
    }

    @Test
    fun testGCD() {
        val iters = atLeast(100)
        repeat(iters) {
            val l1 = randomLong()
            val l2 = randomLong()
            val gcd = MathUtil.gcd(l1, l2)
            val actualGcd = gcdSlow(l1, l2)
            assertEquals(actualGcd, gcd)
            if (gcd != 0L) {
                assertEquals(l1, (l1 / gcd) * gcd)
                assertEquals(l2, (l2 / gcd) * gcd)
            }
        }
    }

    @Test
    fun testGCD2() {
        val a = 30L
        val b = 50L
        val c = 77L

        assertEquals(0L, MathUtil.gcd(0, 0))
        assertEquals(b, MathUtil.gcd(0, b))
        assertEquals(a, MathUtil.gcd(a, 0))
        assertEquals(b, MathUtil.gcd(0, -b))
        assertEquals(a, MathUtil.gcd(-a, 0))

        assertEquals(10L, MathUtil.gcd(a, b))
        assertEquals(10L, MathUtil.gcd(-a, b))
        assertEquals(10L, MathUtil.gcd(a, -b))
        assertEquals(10L, MathUtil.gcd(-a, -b))

        assertEquals(1L, MathUtil.gcd(a, c))
        assertEquals(1L, MathUtil.gcd(-a, c))
        assertEquals(1L, MathUtil.gcd(a, -c))
        assertEquals(1L, MathUtil.gcd(-a, -c))

        assertEquals(3L * (1L shl 45), MathUtil.gcd(3L * (1L shl 50), 9L * (1L shl 45)))
        assertEquals(1L shl 45, MathUtil.gcd(1L shl 45, Long.MIN_VALUE))

        assertEquals(Long.MAX_VALUE, MathUtil.gcd(Long.MAX_VALUE, 0L))
        assertEquals(Long.MAX_VALUE, MathUtil.gcd(-Long.MAX_VALUE, 0L))
        assertEquals(1L, MathUtil.gcd(60247241209L, 153092023L))

        assertEquals(Long.MIN_VALUE, MathUtil.gcd(Long.MIN_VALUE, 0))
        assertEquals(Long.MIN_VALUE, MathUtil.gcd(0, Long.MIN_VALUE))
        assertEquals(Long.MIN_VALUE, MathUtil.gcd(Long.MIN_VALUE, Long.MIN_VALUE))
    }

    @Test
    fun testAcoshMethod() {
        assertTrue(MathUtil.acosh(Double.NaN).isNaN())
        assertEquals(0L, MathUtil.acosh(1.0).toRawBits())
        assertEquals(Double.POSITIVE_INFINITY.toRawBits(), MathUtil.acosh(Double.POSITIVE_INFINITY).toRawBits())
        assertTrue(MathUtil.acosh(0.9).isNaN())
        assertTrue(MathUtil.acosh(0.0).isNaN())
        assertTrue(MathUtil.acosh(-0.0).isNaN())
        assertTrue(MathUtil.acosh(-0.9).isNaN())
        assertTrue(MathUtil.acosh(-1.0).isNaN())
        assertTrue(MathUtil.acosh(-10.0).isNaN())
        assertTrue(MathUtil.acosh(Double.NEGATIVE_INFINITY).isNaN())

        val epsilon = 0.000001
        assertEquals(0.0, MathUtil.acosh(1.0), epsilon)
        assertEquals(1.5667992369724109, MathUtil.acosh(2.5), epsilon)
        assertEquals(14.719378760739708, MathUtil.acosh(1234567.89), epsilon)
    }

    @Test
    fun testAsinhMethod() {
        assertTrue(MathUtil.asinh(Double.NaN).isNaN())
        assertEquals(0L, MathUtil.asinh(0.0).toRawBits())
        assertEquals((-0.0).toRawBits(), MathUtil.asinh(-0.0).toRawBits())
        assertEquals(Double.POSITIVE_INFINITY.toRawBits(), MathUtil.asinh(Double.POSITIVE_INFINITY).toRawBits())
        assertEquals(Double.NEGATIVE_INFINITY.toRawBits(), MathUtil.asinh(Double.NEGATIVE_INFINITY).toRawBits())

        val epsilon = 0.000001
        assertEquals(-14.719378760740035, MathUtil.asinh(-1234567.89), epsilon)
        assertEquals(-1.6472311463710958, MathUtil.asinh(-2.5), epsilon)
        assertEquals(-0.8813735870195429, MathUtil.asinh(-1.0), epsilon)
        assertEquals(0.0, MathUtil.asinh(0.0), 0.0)
        assertEquals(0.8813735870195429, MathUtil.asinh(1.0), epsilon)
        assertEquals(1.6472311463710958, MathUtil.asinh(2.5), epsilon)
        assertEquals(14.719378760740035, MathUtil.asinh(1234567.89), epsilon)
    }

    @Test
    fun testAtanhMethod() {
        assertTrue(MathUtil.atanh(Double.NaN).isNaN())
        assertEquals(0L, MathUtil.atanh(0.0).toRawBits())
        assertEquals((-0.0).toRawBits(), MathUtil.atanh(-0.0).toRawBits())
        assertEquals(Double.POSITIVE_INFINITY.toRawBits(), MathUtil.atanh(1.0).toRawBits())
        assertEquals(Double.NEGATIVE_INFINITY.toRawBits(), MathUtil.atanh(-1.0).toRawBits())
        assertTrue(MathUtil.atanh(1.1).isNaN())
        assertTrue(MathUtil.atanh(Double.POSITIVE_INFINITY).isNaN())
        assertTrue(MathUtil.atanh(-1.1).isNaN())
        assertTrue(MathUtil.atanh(Double.NEGATIVE_INFINITY).isNaN())

        val epsilon = 0.000001
        assertEquals(Double.NEGATIVE_INFINITY, MathUtil.atanh(-1.0), 0.0)
        assertEquals(-0.5493061443340549, MathUtil.atanh(-0.5), epsilon)
        assertEquals(0.0, MathUtil.atanh(0.0), 0.0)
        assertEquals(0.5493061443340549, MathUtil.atanh(0.5), epsilon)
        assertEquals(Double.POSITIVE_INFINITY, MathUtil.atanh(1.0), 0.0)
    }
}

