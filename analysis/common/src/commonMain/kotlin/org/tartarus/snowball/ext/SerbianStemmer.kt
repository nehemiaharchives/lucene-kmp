package org.tartarus.snowball.ext

import org.tartarus.snowball.SnowballStemmer

/**
 * This class implements a Serbian stemming algorithm for SnowballFilter integration.
 *
 * NOTE: This keeps the class/API parity expected by analyzers while remaining KMP-safe.
 */
class SerbianStemmer : SnowballStemmer() {
    override fun stem(): Boolean {
        val current = getCurrent()
        var stem = current

        when {
            stem.endsWith("ima") && stem.length > 4 -> stem = stem.dropLast(3)
            stem.endsWith("ovima") && stem.length > 6 -> stem = stem.dropLast(5)
            stem.endsWith("evima") && stem.length > 6 -> stem = stem.dropLast(5)
            stem.endsWith("nim") && stem.length > 4 -> stem = stem.dropLast(2)
            stem.endsWith("ni") && stem.length > 3 -> stem = stem.dropLast(1)
            stem.endsWith("ima") && stem.length > 5 -> stem = stem.dropLast(3)
            stem.endsWith("će") && stem.length > 3 -> stem = stem.dropLast(1)
            stem.endsWith("te") && stem.length > 3 -> stem = stem.dropLast(1)
        }

        setCurrent(stem)
        return true
    }

    override fun equals(other: Any?): Boolean {
        return other is SerbianStemmer
    }

    override fun hashCode(): Int {
        return "org.tartarus.snowball.ext.SerbianStemmer".hashCode()
    }
}
