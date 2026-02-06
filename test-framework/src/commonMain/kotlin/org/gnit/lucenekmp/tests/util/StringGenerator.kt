package org.gnit.lucenekmp.tests.util

import org.gnit.lucenekmp.jdkport.Ported
import kotlin.random.Random

/**
 * A [StringGenerator] generates random strings composed of characters. What these characters
 * are and their distribution depends on a subclass.
 *
 * @see String
 */
@Ported(from = "com.carrotsearch.randomizedtesting.generators.StringGenerator")
abstract class StringGenerator {
    /**
     * An alias for [ofCodeUnitsLength].
     */
    fun ofStringLength(r: Random, minCodeUnits: Int, maxCodeUnits: Int): String {
        return ofCodeUnitsLength(r, minCodeUnits, maxCodeUnits)
    }

    /**
     * @return Returns a string of variable length between `minCodeUnits` (inclusive)
     * and `maxCodeUnits` (inclusive) length. Code units are essentially
     * an equivalent of `char` type, see [String] class for
     * explanation.
     *
     * @param minCodeUnits Minimum number of code units (inclusive).
     * @param maxCodeUnits Maximum number of code units (inclusive).
     * @throws IllegalArgumentException Thrown if the generator cannot emit random string
     * of the given unit length. For example a generator emitting only extended unicode
     * plane characters (encoded as surrogate pairs) will not be able to emit an odd number
     * of code units.
     */
    abstract fun ofCodeUnitsLength(r: Random, minCodeUnits: Int, maxCodeUnits: Int): String

    /**
     * @return Returns a string of variable length between `minCodePoints` (inclusive)
     * and `maxCodePoints` (inclusive) length. Code points are full unicode
     * codepoints or an equivalent of `int` type, see [String] class for
     * explanation. The returned [String.length] may exceed `maxCodePoints`
     * because certain code points may be encoded as surrogate pairs.
     *
     * @param minCodePoints Minimum number of code points (inclusive).
     * @param maxCodePoints Maximum number of code points (inclusive).
     */
    abstract fun ofCodePointsLength(r: Random, minCodePoints: Int, maxCodePoints: Int): String
}
