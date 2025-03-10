package org.gnit.lucenekmp.tests.util

import kotlin.random.Random

class TestUtil {
    companion object {

        // line 552 of TestUtil.java
        /** start and end are BOTH inclusive  */
        fun nextInt(r: Random, start: Int, end: Int): Int {
            return r.nextInt(start, end)
        }

        // line 617 of TestUtil.java
        /** Returns random string, including full unicode range.  */
        fun randomUnicodeString(r: Random): String {
            return randomUnicodeString(r, 20)
        }

        /** Returns a random string up to a certain length.  */
        fun randomUnicodeString(r: Random, maxLength: Int): String {
            val end = nextInt(r, 0, maxLength)
            if (end == 0) {
                // allow 0 length
                return ""
            }
            val buffer = CharArray(end)
            TestUtil.randomFixedLengthUnicodeString(r, buffer, 0, buffer.size)
            return buffer.concatToString(0, 0 + end)
        }

        /** Fills provided char[] with valid random unicode code unit sequence.  */
        fun randomFixedLengthUnicodeString(
            random: Random, chars: CharArray, offset: Int, length: Int
        ) {
            var i = offset
            val end = offset + length
            while (i < end) {
                val t: Int = random.nextInt(5)
                if (0 == t && i < length - 1) {
                    // Make a surrogate pair
                    // High surrogate
                    chars[i++] = nextInt(random, 0xd800, 0xdbff).toChar()
                    // Low surrogate
                    chars[i++] = nextInt(random, 0xdc00, 0xdfff).toChar()
                } else if (t <= 1) {
                    chars[i++] = random.nextInt(0x80).toChar()
                } else if (2 == t) {
                    chars[i++] = nextInt(random, 0x80, 0x7ff).toChar()
                } else if (3 == t) {
                    chars[i++] = nextInt(random, 0x800, 0xd7ff).toChar()
                } else if (4 == t) {
                    chars[i++] = nextInt(random, 0xe000, 0xffff).toChar()
                }
            }
        }


    }// end of companion object
}