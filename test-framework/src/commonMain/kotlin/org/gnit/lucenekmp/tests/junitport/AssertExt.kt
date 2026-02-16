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


fun assertArrayEquals(
    expected: LongArray,
    actual: LongArray,
    message: String? = null
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

/**
 * Asserts that two object arrays are equal. If they are not, an
 * [AssertionError] is thrown. If `expected` and
 * `actual` are `null`, they are considered
 * equal.
 *
 * @param expecteds Object array or array of arrays (multi-dimensional array) with
 * expected values
 * @param actuals Object array or array of arrays (multi-dimensional array) with
 * actual values
 */
fun assertArrayEquals(expecteds: Array<String?>, actuals: Array<String?>, message: String? = null) {
    assertEquals(expecteds.size, actuals.size, "${message ?: ""} size mismatch: expected ${expecteds.size} elements, got ${actuals.size}")

    for (i in expecteds.indices) {
        assertEquals(
            expecteds[i],
            actuals[i],
            "${message ?: ""} arrays differ at index $i: expected=${expecteds[i]}, actual=${actuals[i]}"
        )
    }
}

/**
 * Asserts that `actual` satisfies the condition specified by
 * `matcher`. If not, an [AssertionError] is thrown with
 * information about the matcher and failing value. Example:
 *
 * <pre>
 * assertThat(0, is(1)); // fails:
 * // failure message:
 * // expected: is &lt;1&gt;
 * // got value: &lt;0&gt;
 * assertThat(0, is(not(1))) // passes
</pre> *
 *
 * `Matcher` does not currently document the meaning
 * of its type parameter `T`.  This method assumes that a matcher
 * typed as `Matcher<T>` can be meaningfully applied only
 * to values that could be assigned to a variable of type `T`.
 *
 * @param <T> the static type accepted by the matcher (this can flag obvious
 * compile-time problems such as `assertThat(1, is("a"))`
 * @param actual the computed value being compared
 * @param matcher an expression, built of [Matcher]s, specifying allowed
 * values
 * @see org.hamcrest.CoreMatchers
 *
</T> */
@Deprecated("use {@code MatcherAssert.assertThat()}")
fun <T> assertThat(actual: T, matcher: Matcher<in T>) {
    assertThat<T>("", actual, matcher)
}

/**
 * Asserts that `actual` satisfies the condition specified by
 * `matcher`. If not, an [AssertionError] is thrown with
 * the reason and information about the matcher and failing value. Example:
 *
 * <pre>
 * assertThat(&quot;Help! Integers don't work&quot;, 0, is(1)); // fails:
 * // failure message:
 * // Help! Integers don't work
 * // expected: is &lt;1&gt;
 * // got value: &lt;0&gt;
 * assertThat(&quot;Zero is one&quot;, 0, is(not(1))) // passes
</pre> *
 *
 * `Matcher` does not currently document the meaning
 * of its type parameter `T`.  This method assumes that a matcher
 * typed as `Matcher<T>` can be meaningfully applied only
 * to values that could be assigned to a variable of type `T`.
 *
 * @param reason additional information about the error
 * @param <T> the static type accepted by the matcher (this can flag obvious
 * compile-time problems such as `assertThat(1, is("a"))`
 * @param actual the computed value being compared
 * @param matcher an expression, built of [Matcher]s, specifying allowed
 * values
 * @see org.hamcrest.CoreMatchers
 *
</T> */
@Deprecated("use {@code MatcherAssert.assertThat()}")
fun <T> assertThat(
    reason: String, actual: T,
    matcher: Matcher<in T>
) {
    MatcherAssert.assertThat<T>(reason, actual, matcher)
}

