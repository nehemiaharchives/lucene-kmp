package org.gnit.lucenekmp.analysis.bn

import org.gnit.lucenekmp.analysis.util.StemmerUtil.delete

/**
 * Normalizer for Bengali.
 *
 * Implements the Bengali-language specific algorithm specified in: A Double Metaphone
 * encoding for Bangla and its application in spelling checker.
 */
internal class BengaliNormalizer {
    /**
     * Normalize an input buffer of Bengali text.
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
                // delete Chandrabindu
                '\u0981' -> {
                    length = delete(s, i, length)
                    i--
                }

                // DirghoI kar -> RosshoI kar
                '\u09C0' -> s[i] = '\u09BF'

                // DirghoU kar -> RosshoU kar
                '\u09C2' -> s[i] = '\u09C1'

                // Khio (Ka + Hoshonto + Murdorno Sh)
                '\u0995' -> {
                    if (i + 2 < length && s[i + 1] == '\u09CD' && s[i + 2] == '\u09BF') {
                        if (i == 0) {
                            s[i] = '\u0996'
                            length = delete(s, i + 2, length)
                            length = delete(s, i + 1, length)
                        } else {
                            s[i + 1] = '\u0996'
                            length = delete(s, i + 2, length)
                        }
                    }
                }

                // Nga to Anusvara
                '\u0999' -> s[i] = '\u0982'

                // Ja Phala
                '\u09AF' -> {
                    if (i - 2 == 0 && s[i - 1] == '\u09CD') {
                        s[i - 1] = '\u09C7'

                        if (i + 1 < length && s[i + 1] == '\u09BE') {
                            length = delete(s, i + 1, length)
                        }
                        length = delete(s, i, length)
                        i--
                    } else if (i - 1 >= 0 && s[i - 1] == '\u09CD') {
                        length = delete(s, i, length)
                        length = delete(s, i - 1, length)
                        i -= 2
                    }
                }

                // Ba Phalaa
                '\u09AC' -> {
                    if ((i >= 1 && s[i - 1] != '\u09CD') || i == 0) {
                        // no-op
                    } else if (i - 2 == 0) {
                        length = delete(s, i, length)
                        length = delete(s, i - 1, length)
                        i -= 2
                    } else if (i - 5 >= 0 && s[i - 3] == '\u09CD') {
                        length = delete(s, i, length)
                        length = delete(s, i - 1, length)
                        i -= 2
                    } else if (i - 2 >= 0) {
                        s[i - 1] = s[i - 2]
                        length = delete(s, i, length)
                        i--
                    }
                }

                // Visarga
                '\u0983' -> {
                    if (i == length - 1) {
                        if (length <= 3) {
                            s[i] = '\u09B9'
                        } else {
                            length = delete(s, i, length)
                        }
                    } else {
                        s[i] = s[i + 1]
                    }
                }

                // All sh
                '\u09B6', '\u09B7' -> s[i] = '\u09B8'

                // check na
                '\u09A3' -> s[i] = '\u09A8'

                // check ra
                '\u09DC', '\u09DD' -> s[i] = '\u09B0'

                '\u09CE' -> s[i] = '\u09A4'
            }
            i++
        }

        return length
    }
}
