package org.gnit.lucenekmp.tests.junitport

import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue


/**
 * ported from org.junit.Assert.assertArrayEquals
 *
 * Asserts that two byte arrays are equal. If they are not, an
 * [AssertionError] is thrown with the given message.
 *
 * @param message the identifying message for the [AssertionError] (`null`
 * okay)
 * @param expecteds byte array with expected values.
 * @param actuals byte array with actual values
 */
fun assertArrayEquals(
    expecteds: ByteArray,
    actuals: ByteArray,
    message: String? = null
) {
    internalArrayEquals(message, expecteds, actuals)
}

/**
 * mimics following:
 *     private static void internalArrayEquals(String message, Object expecteds,
 *             Object actuals) throws ArrayComparisonFailure {
 *         new ExactComparisonCriteria().arrayEquals(message, expecteds, actuals);
 *     }
 */
private fun internalArrayEquals(
    message: String?,
    expected: ByteArray,
    actual: ByteArray
) {
    assertEquals(
        expected.size,
        actual.size,
        "${message ?: ""} size mismatch: expected ${expected.size} elements, got ${actual.size}"
    )
    for (i in expected.indices) {
        assertEquals(
            expected[i],
            actual[i],
            "${message ?: ""} arrays differ at index $i: expected=${expected[i]}, actual=${actual[i]}"
        )
    }
}

/**
 * ported from org.junit.Assert.assertArrayEquals
 */
fun assertArrayEquals(
    expected: FloatArray,
    actual: FloatArray,
    delta: Float,
    message: String? = null
) {
    assertEquals(
        expected.size,
        actual.size,
        "${message ?: ""} size mismatch: expected ${expected.size} elements, got ${actual.size}"
    )
    for (i in expected.indices) {
        val ok = abs(expected[i] - actual[i]) <= delta
        assertTrue(
            ok,
            "${message ?: ""} arrays differ at index $i: expected=${expected[i]}, actual=${actual[i]}, delta=$delta"
        )
    }
}

/**
 * ported from org.junit.Assert.assertArrayEquals
 */
fun assertArrayEquals(
    expected: DoubleArray,
    actual: DoubleArray,
    delta: Double,
    message: String? = null
) {
    assertEquals(
        expected.size,
        actual.size,
        "${message ?: ""} size mismatch: expected ${expected.size} elements, got ${actual.size}"
    )
    for (i in expected.indices) {
        val ok = abs(expected[i] - actual[i]) <= delta
        assertTrue(
            ok,
            "${message ?: ""} arrays differ at index $i: expected=${expected[i]}, actual=${actual[i]}, delta=$delta"
        )
    }
}
