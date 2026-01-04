package org.gnit.lucenekmp.analysis.mr

import org.gnit.lucenekmp.analysis.util.StemmerUtil.delete

/**
 * Normalizer for Marathi.
 *
 * Normalizes text to remove some differences in spelling variations.
 */
internal class MarathiNormalizer {
    /**
     * Normalize an input buffer of Marathi text.
     *
     * @param s input buffer
     * @param len length of input buffer
     * @return length of input buffer after normalization
     */
    fun normalize(s: CharArray, len: Int): Int {
        var length = len
        var i = 0
        while (i < length) {
            when (s[i]) {
                // dead n -> bindu
                '\u0928' -> {
                    if (i + 1 < length && s[i + 1] == '\u094D') {
                        s[i] = '\u0902'
                        length = delete(s, i + 1, length)
                    }
                }
                // candrabindu -> bindu
                '\u0901' -> s[i] = '\u0902'
                // nukta deletions
                '\u093C' -> {
                    length = delete(s, i, length)
                    i--
                }
                '\u0929' -> s[i] = '\u0928'
                '\u0931' -> s[i] = '\u0930'
                '\u0934' -> s[i] = '\u0933'
                '\u0958' -> s[i] = '\u0915'
                '\u0959' -> s[i] = '\u0916'
                '\u095A' -> s[i] = '\u0917'
                '\u095B' -> s[i] = '\u091C'
                '\u095C' -> s[i] = '\u0921'
                '\u095D' -> s[i] = '\u0922'
                '\u095E' -> s[i] = '\u092B'
                '\u095F' -> s[i] = '\u092F'
                // zwj/zwnj -> delete
                '\u200D', '\u200C' -> {
                    length = delete(s, i, length)
                    i--
                }
                // virama -> delete
                '\u094D' -> {
                    length = delete(s, i, length)
                    i--
                }
                // chandra/short -> replace
                '\u0945', '\u0946' -> s[i] = '\u0947'
                '\u0949', '\u094A' -> s[i] = '\u094B'
                '\u090D', '\u090E' -> s[i] = '\u090F'
                '\u0911', '\u0912' -> s[i] = '\u0913'
                '\u0972' -> s[i] = '\u0905'
                // long -> short ind. vowels
                '\u0906' -> s[i] = '\u0905'
                '\u0908' -> s[i] = '\u0907'
                '\u090A' -> s[i] = '\u0909'
                '\u0960' -> s[i] = '\u090B'
                '\u0961' -> s[i] = '\u090C'
                '\u0910' -> s[i] = '\u090F'
                '\u0914' -> s[i] = '\u0913'
                // long -> short dep. vowels
                '\u0940' -> s[i] = '\u093F'
                '\u0942' -> s[i] = '\u0941'
                '\u0944' -> s[i] = '\u0943'
                '\u0963' -> s[i] = '\u0962'
                '\u0948' -> s[i] = '\u0947'
                '\u094C' -> s[i] = '\u094B'
            }
            i++
        }
        return length
    }
}
