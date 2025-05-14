package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals

class KotlinMathRoundTest {
    @Test
    fun testKotlinMathRound() {
        // Test the behavior of kotlin.math.round with double values
        val round1_5 = kotlin.math.round(1.5)
        val roundNeg1_5 = kotlin.math.round(-1.5)
        println("[DEBUG_LOG] kotlin.math.round(1.5) = $round1_5")
        println("[DEBUG_LOG] kotlin.math.round(-1.5) = $roundNeg1_5")

        // Test the behavior of kotlin.math.round with float values and conversion to Int
        val round1_5f = kotlin.math.round(1.5f).toInt()
        val roundNeg1_5f = kotlin.math.round(-1.5f).toInt()
        println("[DEBUG_LOG] kotlin.math.round(1.5f).toInt() = $round1_5f")
        println("[DEBUG_LOG] kotlin.math.round(-1.5f).toInt() = $roundNeg1_5f")

        // Assert the expected values
        assertEquals(2.0, round1_5)
        assertEquals(-2.0, roundNeg1_5) // Kotlin rounds away from zero for negative values
        assertEquals(2, round1_5f)
        assertEquals(-2, roundNeg1_5f) // Kotlin rounds away from zero for negative values
    }
}
