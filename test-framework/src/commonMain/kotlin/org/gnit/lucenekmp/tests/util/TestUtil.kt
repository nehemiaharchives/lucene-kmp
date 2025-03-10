package org.gnit.lucenekmp.tests.util

import kotlin.random.Random

class TestUtil {
    companion object {

        // line 552 of TestUtil.java
        /** start and end are BOTH inclusive  */
        fun nextInt(r: Random, start: Int, end: Int): Int {
            return r.nextInt(start, end)
        }
    }
}