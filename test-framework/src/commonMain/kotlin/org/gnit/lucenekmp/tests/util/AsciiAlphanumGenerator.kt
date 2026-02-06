package org.gnit.lucenekmp.tests.util

import org.gnit.lucenekmp.jdkport.Ported

/**
 * A generator emitting simple ASCII alphanumeric letters and numbers
 * from the set (newlines not counted):
 * <pre>
 * abcdefghijklmnopqrstuvwxyz
 * ABCDEFGHIJKLMNOPQRSTUVWXYZ
 * 0123456789
 * </pre>
 */
@Ported(from = "com.carrotsearch.randomizedtesting.generators.AsciiAlphanumGenerator")
class AsciiAlphanumGenerator : CodepointSetGenerator(CHARS) {
    companion object {
        private val CHARS: CharArray =
            ("abcdefghijklmnopqrstuvwxyz" +
                "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
                "0123456789").toCharArray()
    }
}
