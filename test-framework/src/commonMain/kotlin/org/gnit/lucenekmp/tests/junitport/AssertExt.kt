package org.gnit.lucenekmp.tests.junitport

import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
