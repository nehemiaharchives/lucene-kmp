package org.gnit.lucenekmp.analysis.fi

import org.gnit.lucenekmp.analysis.util.StemmerUtil.delete
import org.gnit.lucenekmp.analysis.util.StemmerUtil.endsWith

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
 * Light Stemmer for Finnish.
 *
 * This stemmer implements the algorithm described in: *Report on CLEF-2003 Monolingual
 * Tracks* Jacques Savoy
 */
internal class FinnishLightStemmer {
    fun stem(s: CharArray, len: Int): Int {
        var length = len
        if (length < 4) return length

        for (i in 0..<length) {
            when (s[i]) {
                'ä', 'å' -> s[i] = 'a'
                'ö' -> s[i] = 'o'
            }
        }

        length = step1(s, length)
        length = step2(s, length)
        length = step3(s, length)
        length = norm1(s, length)
        length = norm2(s, length)
        return length
    }

    private fun step1(s: CharArray, len: Int): Int {
        if (len > 8) {
            if (endsWith(s, len, "kin")) return step1(s, len - 3)
            if (endsWith(s, len, "ko")) return step1(s, len - 2)
        }

        if (len > 11) {
            if (endsWith(s, len, "dellinen")) return len - 8
            if (endsWith(s, len, "dellisuus")) return len - 9
        }
        return len
    }

    private fun step2(s: CharArray, len: Int): Int {
        if (len > 5) {
            if (endsWith(s, len, "lla") || endsWith(s, len, "tse") || endsWith(s, len, "sti")) {
                return len - 3
            }

            if (endsWith(s, len, "ni")) return len - 2

            if (endsWith(s, len, "aa")) return len - 1 // aa -> a
        }

        return len
    }

    private fun step3(s: CharArray, len: Int): Int {
        if (len > 8) {
            if (endsWith(s, len, "nnen")) {
                s[len - 4] = 's'
                return len - 3
            }

            if (endsWith(s, len, "ntena")) {
                s[len - 5] = 's'
                return len - 4
            }

            if (endsWith(s, len, "tten")) return len - 4

            if (endsWith(s, len, "eiden")) return len - 5
        }

        if (len > 6) {
            if (endsWith(s, len, "neen") ||
                endsWith(s, len, "niin") ||
                endsWith(s, len, "seen") ||
                endsWith(s, len, "teen") ||
                endsWith(s, len, "inen")
            ) {
                return len - 4
            }

            if (s[len - 3] == 'h' && isVowel(s[len - 2]) && s[len - 1] == 'n') return len - 3

            if (endsWith(s, len, "den")) {
                s[len - 3] = 's'
                return len - 2
            }

            if (endsWith(s, len, "ksen")) {
                s[len - 4] = 's'
                return len - 3
            }

            if (endsWith(s, len, "ssa") ||
                endsWith(s, len, "sta") ||
                endsWith(s, len, "lla") ||
                endsWith(s, len, "lta") ||
                endsWith(s, len, "tta") ||
                endsWith(s, len, "ksi") ||
                endsWith(s, len, "lle")
            ) {
                return len - 3
            }
        }

        if (len > 5) {
            if (endsWith(s, len, "na") || endsWith(s, len, "ne")) return len - 2

            if (endsWith(s, len, "nei")) return len - 3
        }

        if (len > 4) {
            if (endsWith(s, len, "ja") || endsWith(s, len, "ta")) return len - 2

            if (s[len - 1] == 'a') return len - 1

            if (s[len - 1] == 'n' && isVowel(s[len - 2])) return len - 2

            if (s[len - 1] == 'n') return len - 1
        }

        return len
    }

    private fun norm1(s: CharArray, len: Int): Int {
        if (len > 5 && endsWith(s, len, "hde")) {
            s[len - 3] = 'k'
            s[len - 2] = 's'
            s[len - 1] = 'i'
        }

        if (len > 4) {
            if (endsWith(s, len, "ei") || endsWith(s, len, "at")) return len - 2
        }

        if (len > 3) {
            when (s[len - 1]) {
                't', 's', 'j', 'e', 'a', 'i' -> return len - 1
            }
        }

        return len
    }

    private fun norm2(s: CharArray, len: Int): Int {
        var length = len
        if (length > 8) {
            if (s[length - 1] == 'e' || s[length - 1] == 'o' || s[length - 1] == 'u') length--
        }

        if (length > 4) {
            if (s[length - 1] == 'i') length--

            if (length > 4) {
                var ch = s[0]
                var i = 1
                while (i < length) {
                    if (s[i] == ch && (ch == 'k' || ch == 'p' || ch == 't')) {
                        length = delete(s, i--, length)
                    } else {
                        ch = s[i]
                    }
                    i++
                }
            }
        }

        return length
    }

    private fun isVowel(ch: Char): Boolean {
        when (ch) {
            'a', 'e', 'i', 'o', 'u', 'y' -> return true
            else -> return false
        }
    }
}
