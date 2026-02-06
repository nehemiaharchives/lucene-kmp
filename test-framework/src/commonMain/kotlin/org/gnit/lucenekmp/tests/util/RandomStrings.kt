package org.gnit.lucenekmp.tests.util

import org.gnit.lucenekmp.jdkport.Ported
import kotlin.random.Random

/**
 * A facade to various implementations of [StringGenerator] interface.
 */
@Ported(from = "com.carrotsearch.randomizedtesting.generators.RandomStrings")
object RandomStrings {
    val realisticUnicodeGenerator: RealisticUnicodeGenerator = RealisticUnicodeGenerator()
    val unicodeGenerator: UnicodeGenerator = UnicodeGenerator()
    val asciiLettersGenerator: AsciiLettersGenerator = AsciiLettersGenerator()
    val asciiAlphanumGenerator: AsciiAlphanumGenerator = AsciiAlphanumGenerator()

    /**
     * @deprecated Use [asciiLettersGenerator] instead.
     */
    @Deprecated("Use asciiLettersGenerator instead.")
    val asciiGenerator: ASCIIGenerator = ASCIIGenerator()

    // Ultra wide monitor required to read the source code :)

    /** @deprecated Use [randomAsciiLettersOfLengthBetween] instead. */
    @Deprecated("Use randomAsciiLettersOfLengthBetween instead.")
    fun randomAsciiOfLengthBetween(r: Random, minCodeUnits: Int, maxCodeUnits: Int): String {
        return asciiGenerator.ofCodeUnitsLength(r, minCodeUnits, maxCodeUnits)
    }

    /** @deprecated Use [randomAsciiLettersOfLength] instead. */
    @Deprecated("Use randomAsciiLettersOfLength instead.")
    fun randomAsciiOfLength(r: Random, codeUnits: Int): String {
        return asciiGenerator.ofCodeUnitsLength(r, codeUnits, codeUnits)
    }

    fun randomAsciiLettersOfLengthBetween(r: Random, minCodeUnits: Int, maxCodeUnits: Int): String {
        return asciiLettersGenerator.ofCodeUnitsLength(r, minCodeUnits, maxCodeUnits)
    }

    fun randomAsciiLettersOfLength(r: Random, codeUnits: Int): String {
        return asciiLettersGenerator.ofCodeUnitsLength(r, codeUnits, codeUnits)
    }

    fun randomAsciiAlphanumOfLengthBetween(r: Random, minCodeUnits: Int, maxCodeUnits: Int): String {
        return asciiAlphanumGenerator.ofCodeUnitsLength(r, minCodeUnits, maxCodeUnits)
    }

    fun randomAsciiAlphanumOfLength(r: Random, codeUnits: Int): String {
        return asciiAlphanumGenerator.ofCodeUnitsLength(r, codeUnits, codeUnits)
    }

    fun randomUnicodeOfLengthBetween(r: Random, minCodeUnits: Int, maxCodeUnits: Int): String {
        return unicodeGenerator.ofCodeUnitsLength(r, minCodeUnits, maxCodeUnits)
    }

    fun randomUnicodeOfLength(r: Random, codeUnits: Int): String {
        return unicodeGenerator.ofCodeUnitsLength(r, codeUnits, codeUnits)
    }

    fun randomUnicodeOfCodepointLengthBetween(r: Random, minCodePoints: Int, maxCodePoints: Int): String {
        return unicodeGenerator.ofCodePointsLength(r, minCodePoints, maxCodePoints)
    }

    fun randomUnicodeOfCodepointLength(r: Random, codePoints: Int): String {
        return unicodeGenerator.ofCodePointsLength(r, codePoints, codePoints)
    }

    fun randomRealisticUnicodeOfLengthBetween(r: Random, minCodeUnits: Int, maxCodeUnits: Int): String {
        return realisticUnicodeGenerator.ofCodeUnitsLength(r, minCodeUnits, maxCodeUnits)
    }

    fun randomRealisticUnicodeOfLength(r: Random, codeUnits: Int): String {
        return realisticUnicodeGenerator.ofCodeUnitsLength(r, codeUnits, codeUnits)
    }

    fun randomRealisticUnicodeOfCodepointLengthBetween(
        r: Random,
        minCodePoints: Int,
        maxCodePoints: Int
    ): String {
        return realisticUnicodeGenerator.ofCodePointsLength(r, minCodePoints, maxCodePoints)
    }

    fun randomRealisticUnicodeOfCodepointLength(r: Random, codePoints: Int): String {
        return realisticUnicodeGenerator.ofCodePointsLength(r, codePoints, codePoints)
    }
}
