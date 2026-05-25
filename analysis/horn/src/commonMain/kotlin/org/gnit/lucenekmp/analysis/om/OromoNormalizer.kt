package org.gnit.lucenekmp.analysis.om

/** Conservative Oromo normalization. */
internal class OromoNormalizer {
    fun normalize(s: CharArray, len: Int): Int {
        var i = 0
        while (i < len) {
            if (s[i] == '\u2019' || s[i] == '\u2018' || s[i] == '\u02BC' || s[i] == '`' || s[i] == '´') {
                s[i] = '\''
            }
            i++
        }
        return len
    }
}
