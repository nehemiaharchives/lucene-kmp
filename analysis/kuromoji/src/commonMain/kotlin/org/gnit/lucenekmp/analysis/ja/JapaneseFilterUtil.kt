package org.gnit.lucenekmp.analysis.ja

import org.gnit.lucenekmp.internal.hppc.CharObjectHashMap

/** Utility methods for Japanese filters. */
internal object JapaneseFilterUtil {
    /** Creates a primitive char-to-char map from a set of char mappings. */
    fun createCharMap(vararg charMappings: Pair<Char, Char>): CharObjectHashMap<Char> {
        val map = CharObjectHashMap<Char>(/*charMappings.size*/)
        for ((key, value) in charMappings) {
            map.put(key, value)
        }
        return map
    }
}
