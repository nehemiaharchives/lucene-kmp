package org.gnit.lucenekmp.tests.util

import org.gnit.lucenekmp.jdkport.Ported

/**
 * A generator emitting simple ASCII characters from the set
 * (newlines not counted):
 * <pre>
 * abcdefghijklmnopqrstuvwxyz
 * ABCDEFGHIJKLMNOPQRSTUVWXYZ
 * </pre>
 */
@Ported(from = "com.carrotsearch.randomizedtesting.generators.AsciiLettersGenerator")
open class AsciiLettersGenerator : CodepointSetGenerator(CHARS) {
    companion object {
        private val CHARS: CharArray =
            ("abcdefghijklmnopqrstuvwxyz" +
                "ABCDEFGHIJKLMNOPQRSTUVWXYZ").toCharArray()
    }
}
