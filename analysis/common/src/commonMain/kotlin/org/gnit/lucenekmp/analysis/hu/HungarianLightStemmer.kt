package org.gnit.lucenekmp.analysis.hu

import org.gnit.lucenekmp.analysis.util.StemmerUtil

/*
 * This algorithm is updated based on code located at:
 * http://members.unine.ch/jacques.savoy/clef/
 *
 * Full copyright for that code follows:
 */

/*
 * Copyright (c) 2005, Jacques Savoy
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer. Redistributions in binary
 * form must reproduce the above copyright notice, this list of conditions and
 * the following disclaimer in the documentation and/or other materials
 * provided with the distribution. Neither the name of the author nor the names
 * of its contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

/**
 * Light Stemmer for Hungarian.
 *
 * This stemmer implements the "UniNE" algorithm in: _Light Stemming Approaches for the French,
 * Portuguese, German and Hungarian Languages_ Jacques Savoy
 */
internal class HungarianLightStemmer {
    fun stem(s: CharArray, len: Int): Int {
        var currentLen = len
        for (i in 0 until currentLen) {
            when (s[i]) {
                'á' -> s[i] = 'a'
                'ë', 'é' -> s[i] = 'e'
                'í' -> s[i] = 'i'
                'ó', 'ő', 'õ', 'ö' -> s[i] = 'o'
                'ú', 'ű', 'ũ', 'û', 'ü' -> s[i] = 'u'
            }
        }

        currentLen = removeCase(s, currentLen)
        currentLen = removePossessive(s, currentLen)
        currentLen = removePlural(s, currentLen)
        return normalize(s, currentLen)
    }

    private fun removeCase(s: CharArray, len: Int): Int {
        if (len > 6 && StemmerUtil.endsWith(s, len, "kent")) return len - 4

        if (len > 5) {
            if (StemmerUtil.endsWith(s, len, "nak") ||
                StemmerUtil.endsWith(s, len, "nek") ||
                StemmerUtil.endsWith(s, len, "val") ||
                StemmerUtil.endsWith(s, len, "vel") ||
                StemmerUtil.endsWith(s, len, "ert") ||
                StemmerUtil.endsWith(s, len, "rol") ||
                StemmerUtil.endsWith(s, len, "ban") ||
                StemmerUtil.endsWith(s, len, "ben") ||
                StemmerUtil.endsWith(s, len, "bol") ||
                StemmerUtil.endsWith(s, len, "nal") ||
                StemmerUtil.endsWith(s, len, "nel") ||
                StemmerUtil.endsWith(s, len, "hoz") ||
                StemmerUtil.endsWith(s, len, "hez") ||
                StemmerUtil.endsWith(s, len, "tol")
            ) return len - 3

            if (StemmerUtil.endsWith(s, len, "al") || StemmerUtil.endsWith(s, len, "el")) {
                if (!isVowel(s[len - 3]) && s[len - 3] == s[len - 4]) return len - 3
            }
        }

        if (len > 4) {
            if (StemmerUtil.endsWith(s, len, "at") ||
                StemmerUtil.endsWith(s, len, "et") ||
                StemmerUtil.endsWith(s, len, "ot") ||
                StemmerUtil.endsWith(s, len, "va") ||
                StemmerUtil.endsWith(s, len, "ve") ||
                StemmerUtil.endsWith(s, len, "ra") ||
                StemmerUtil.endsWith(s, len, "re") ||
                StemmerUtil.endsWith(s, len, "ba") ||
                StemmerUtil.endsWith(s, len, "be") ||
                StemmerUtil.endsWith(s, len, "ul") ||
                StemmerUtil.endsWith(s, len, "ig")
            ) return len - 2

            if ((StemmerUtil.endsWith(s, len, "on") || StemmerUtil.endsWith(s, len, "en")) && !isVowel(s[len - 3])) {
                return len - 2
            }

            when (s[len - 1]) {
                't', 'n' -> return len - 1
                'a', 'e' -> if (s[len - 2] == s[len - 3] && !isVowel(s[len - 2])) return len - 2
            }
        }

        return len
    }

    private fun removePossessive(s: CharArray, len: Int): Int {
        if (len > 6) {
            if (!isVowel(s[len - 5]) &&
                (StemmerUtil.endsWith(s, len, "atok") ||
                    StemmerUtil.endsWith(s, len, "otok") ||
                    StemmerUtil.endsWith(s, len, "etek"))
            ) {
                return len - 4
            }
            if (StemmerUtil.endsWith(s, len, "itek") || StemmerUtil.endsWith(s, len, "itok")) return len - 4
        }

        if (len > 5) {
            if (!isVowel(s[len - 4]) &&
                (StemmerUtil.endsWith(s, len, "unk") ||
                    StemmerUtil.endsWith(s, len, "tok") ||
                    StemmerUtil.endsWith(s, len, "tek"))
            ) {
                return len - 3
            }
            if (isVowel(s[len - 4]) && StemmerUtil.endsWith(s, len, "juk")) return len - 3
            if (StemmerUtil.endsWith(s, len, "ink")) return len - 3
        }

        if (len > 4) {
            if (!isVowel(s[len - 3]) &&
                (StemmerUtil.endsWith(s, len, "am") ||
                    StemmerUtil.endsWith(s, len, "em") ||
                    StemmerUtil.endsWith(s, len, "om") ||
                    StemmerUtil.endsWith(s, len, "ad") ||
                    StemmerUtil.endsWith(s, len, "ed") ||
                    StemmerUtil.endsWith(s, len, "od") ||
                    StemmerUtil.endsWith(s, len, "uk"))
            ) {
                return len - 2
            }
            if (isVowel(s[len - 3]) &&
                (StemmerUtil.endsWith(s, len, "nk") ||
                    StemmerUtil.endsWith(s, len, "ja") ||
                    StemmerUtil.endsWith(s, len, "je"))
            ) {
                return len - 2
            }
            if (StemmerUtil.endsWith(s, len, "im") || StemmerUtil.endsWith(s, len, "id") || StemmerUtil.endsWith(s, len, "ik")) {
                return len - 2
            }
        }

        if (len > 3) {
            when (s[len - 1]) {
                'a', 'e' -> if (!isVowel(s[len - 2])) return len - 1
                'm', 'd' -> if (isVowel(s[len - 2])) return len - 1
                'i' -> return len - 1
            }
        }

        return len
    }

    private fun removePlural(s: CharArray, len: Int): Int {
        if (len > 3 && s[len - 1] == 'k') {
            when (s[len - 2]) {
                'a', 'o', 'e' -> {
                    if (len > 4) return len - 2
                    return len - 1 // intentional fallthru
                }
                else -> return len - 1
            }
        }
        return len
    }

    private fun normalize(s: CharArray, len: Int): Int {
        if (len > 3) {
            when (s[len - 1]) {
                'a', 'e', 'i', 'o' -> return len - 1
            }
        }
        return len
    }

    private fun isVowel(ch: Char): Boolean {
        return when (ch) {
            'a', 'e', 'i', 'o', 'u', 'y' -> true
            else -> false
        }
    }
}
