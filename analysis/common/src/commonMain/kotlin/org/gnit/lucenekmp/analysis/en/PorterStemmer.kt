package org.gnit.lucenekmp.analysis.en

import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.util.ArrayUtil

/**
 * Stemmer, implementing the Porter Stemming Algorithm.
 *
 * The Stemmer class transforms a word into its root form. The input word can be provided a
 * character at time (by calling add()), or at once by calling one of the various stem(something)
 * methods.
 */
internal class PorterStemmer {
    private var b: CharArray = CharArray(INITIAL_SIZE)
    private var i = 0
    private var j = 0
    private var k = 0
    private var k0 = 0
    private var dirty = false

    /**
     * reset() resets the stemmer so it can stem another word. If you invoke the stemmer by calling
     * add(char) and then stem(), you must call reset() before starting another word.
     */
    fun reset() {
        i = 0
        dirty = false
    }

    /**
     * Add a character to the word being stemmed. When you are finished adding characters, you can
     * call stem(void) to process the word.
     */
    fun add(ch: Char) {
        if (b.size <= i) {
            b = ArrayUtil.grow(b, i + 1)
        }
        b[i++] = ch
    }

    /**
     * After a word has been stemmed, it can be retrieved by toString(), or a reference to the
     * internal buffer can be retrieved by getResultBuffer and getResultLength (which is generally
     * more efficient.)
     */
    override fun toString(): String {
        return b.concatToString(0, i)
    }

    /** Returns the length of the word resulting from the stemming process. */
    fun getResultLength(): Int {
        return i
    }

    /**
     * Returns a reference to a character buffer containing the results of the stemming process. You
     * also need to consult getResultLength() to determine the length of the result.
     */
    fun getResultBuffer(): CharArray {
        return b
    }

    /* cons(i) is true <=> b[i] is a consonant. */
    private fun cons(i: Int): Boolean {
        return when (b[i]) {
            'a', 'e', 'i', 'o', 'u' -> false
            'y' -> if (i == k0) true else !cons(i - 1)
            else -> true
        }
    }

    /* m() measures the number of consonant sequences between k0 and j. if c is
       a consonant sequence and v a vowel sequence, and <..> indicates arbitrary
       presence,

            <c><v>       gives 0
            <c>vc<v>     gives 1
            <c>vcvc<v>   gives 2
            <c>vcvcvc<v> gives 3
            ....
    */
    private fun m(): Int {
        var n = 0
        var i = k0
        while (true) {
            if (i > j) return n
            if (!cons(i)) break
            i++
        }
        i++
        while (true) {
            while (true) {
                if (i > j) return n
                if (cons(i)) break
                i++
            }
            i++
            n++
            while (true) {
                if (i > j) return n
                if (!cons(i)) break
                i++
            }
            i++
        }
    }

    /* vowelinstem() is true <=> k0,...j contains a vowel */
    private fun vowelinstem(): Boolean {
        for (i in k0..j) {
            if (!cons(i)) return true
        }
        return false
    }

    /* doublec(j) is true <=> j,(j-1) contain a double consonant. */
    private fun doublec(j: Int): Boolean {
        if (j < k0 + 1) return false
        if (b[j] != b[j - 1]) return false
        return cons(j)
    }

    /* cvc(i) is true <=> i-2,i-1,i has the form consonant - vowel - consonant
       and also if the second c is not w,x or y. this is used when trying to
       restore an e at the end of a short word. e.g.

            cav(e), lov(e), hop(e), crim(e), but
            snow, box, tray.
    */
    private fun cvc(i: Int): Boolean {
        if (i < k0 + 2 || !cons(i) || cons(i - 1) || !cons(i - 2)) return false
        val ch = b[i]
        if (ch == 'w' || ch == 'x' || ch == 'y') return false
        return true
    }

    private fun ends(s: String): Boolean {
        val l = s.length
        val o = k - l + 1
        if (o < k0) return false
        for (i in 0 until l) {
            if (b[o + i] != s[i]) return false
        }
        j = k - l
        return true
    }

    /* setto(s) sets (j+1),...k to the characters in the string s, readjusting k. */
    private fun setto(s: String) {
        val l = s.length
        val o = j + 1
        for (i in 0 until l) {
            b[o + i] = s[i]
        }
        k = j + l
        dirty = true
    }

    /* r(s) is used further down. */
    private fun r(s: String) {
        if (m() > 0) setto(s)
    }

    /* step1() gets rid of plurals and -ed or -ing. */
    private fun step1() {
        if (b[k] == 's') {
            if (ends("sses")) k -= 2
            else if (ends("ies")) setto("i")
            else if (b[k - 1] != 's') k--
        }
        if (ends("eed")) {
            if (m() > 0) k--
        } else if ((ends("ed") || ends("ing")) && vowelinstem()) {
            k = j
            if (ends("at")) setto("ate")
            else if (ends("bl")) setto("ble")
            else if (ends("iz")) setto("ize")
            else if (doublec(k)) {
                val ch = b[k--]
                if (ch == 'l' || ch == 's' || ch == 'z') k++
            } else if (m() == 1 && cvc(k)) setto("e")
        }
    }

    /* step2() turns terminal y to i when there is another vowel in the stem. */
    private fun step2() {
        if (ends("y") && vowelinstem()) {
            b[k] = 'i'
            dirty = true
        }
    }

    /* step3() maps double suffices to single ones. */
    private fun step3() {
        if (k == k0) return
        when (b[k - 1]) {
            'a' -> {
                if (ends("ational")) {
                    r("ate")
                    return
                }
                if (ends("tional")) {
                    r("tion")
                    return
                }
            }
            'c' -> {
                if (ends("enci")) {
                    r("ence")
                    return
                }
                if (ends("anci")) {
                    r("ance")
                    return
                }
            }
            'e' -> {
                if (ends("izer")) {
                    r("ize")
                    return
                }
            }
            'l' -> {
                if (ends("bli")) {
                    r("ble")
                    return
                }
                if (ends("alli")) {
                    r("al")
                    return
                }
                if (ends("entli")) {
                    r("ent")
                    return
                }
                if (ends("eli")) {
                    r("e")
                    return
                }
                if (ends("ousli")) {
                    r("ous")
                    return
                }
            }
            'o' -> {
                if (ends("ization")) {
                    r("ize")
                    return
                }
                if (ends("ation")) {
                    r("ate")
                    return
                }
                if (ends("ator")) {
                    r("ate")
                    return
                }
            }
            's' -> {
                if (ends("alism")) {
                    r("al")
                    return
                }
                if (ends("iveness")) {
                    r("ive")
                    return
                }
                if (ends("fulness")) {
                    r("ful")
                    return
                }
                if (ends("ousness")) {
                    r("ous")
                    return
                }
            }
            't' -> {
                if (ends("aliti")) {
                    r("al")
                    return
                }
                if (ends("iviti")) {
                    r("ive")
                    return
                }
                if (ends("biliti")) {
                    r("ble")
                    return
                }
            }
            'g' -> {
                if (ends("logi")) {
                    r("log")
                    return
                }
            }
        }
    }

    /* step4() deals with -ic-, -full, -ness etc. */
    private fun step4() {
        when (b[k]) {
            'e' -> {
                if (ends("icate")) {
                    r("ic")
                    return
                }
                if (ends("ative")) {
                    r("")
                    return
                }
                if (ends("alize")) {
                    r("al")
                    return
                }
            }
            'i' -> {
                if (ends("iciti")) {
                    r("ic")
                    return
                }
            }
            'l' -> {
                if (ends("ical")) {
                    r("ic")
                    return
                }
                if (ends("ful")) {
                    r("")
                    return
                }
            }
            's' -> {
                if (ends("ness")) {
                    r("")
                    return
                }
            }
        }
    }

    /* step5() takes off -ant, -ence etc., in context <c>vcvc<v>. */
    private fun step5() {
        if (k == k0) return
        when (b[k - 1]) {
            'a' -> {
                if (ends("al")) {
                    // ok
                } else return
            }
            'c' -> {
                if (ends("ance")) {
                    // ok
                } else if (ends("ence")) {
                    // ok
                } else return
            }
            'e' -> {
                if (ends("er")) {
                    // ok
                } else return
            }
            'i' -> {
                if (ends("ic")) {
                    // ok
                } else return
            }
            'l' -> {
                if (ends("able")) {
                    // ok
                } else if (ends("ible")) {
                    // ok
                } else return
            }
            'n' -> {
                if (ends("ant")) {
                    // ok
                } else if (ends("ement")) {
                    // ok
                } else if (ends("ment")) {
                    // ok
                } else if (ends("ent")) {
                    // ok
                } else return
            }
            'o' -> {
                if (ends("ion") && j >= 0 && (b[j] == 's' || b[j] == 't')) {
                    // ok
                } else if (ends("ou")) {
                    // ok
                } else return
            }
            's' -> {
                if (ends("ism")) {
                    // ok
                } else return
            }
            't' -> {
                if (ends("ate")) {
                    // ok
                } else if (ends("iti")) {
                    // ok
                } else return
            }
            'u' -> {
                if (ends("ous")) {
                    // ok
                } else return
            }
            'v' -> {
                if (ends("ive")) {
                    // ok
                } else return
            }
            'z' -> {
                if (ends("ize")) {
                    // ok
                } else return
            }
            else -> return
        }
        if (m() > 1) k = j
    }

    /* step6() removes a final -e if m() > 1. */
    private fun step6() {
        j = k
        if (b[k] == 'e') {
            val a = m()
            if (a > 1 || a == 1 && !cvc(k - 1)) k--
        }
        if (b[k] == 'l' && doublec(k) && m() > 1) k--
    }

    /** Stem a word provided as a String. Returns the result as a String. */
    fun stem(s: String): String {
        return if (stem(s.toCharArray(), s.length)) toString() else s
    }

    /**
     * Stem a word contained in a char[]. Returns true if the stemming process resulted in a word
     * different from the input.
     */
    fun stem(word: CharArray): Boolean {
        return stem(word, word.size)
    }

    /** Use a portion of a char[] array. */
    fun stem(wordBuffer: CharArray, offset: Int, wordLen: Int): Boolean {
        reset()
        if (b.size < wordLen) {
            b = CharArray(ArrayUtil.oversize(wordLen, Char.SIZE_BYTES))
        }
        System.arraycopy(wordBuffer, offset, b, 0, wordLen)
        i = wordLen
        return stem(0)
    }

    /** Stem a word contained in a leading portion of a char[] array. */
    fun stem(word: CharArray, wordLen: Int): Boolean {
        return stem(word, 0, wordLen)
    }

    /** Stem the word placed into the Stemmer buffer through calls to add(). */
    fun stem(): Boolean {
        return stem(0)
    }

    fun stem(i0: Int): Boolean {
        k = i - 1
        k0 = i0
        if (k > k0 + 1) {
            step1()
            step2()
            step3()
            step4()
            step5()
            step6()
        }
        if (i != k + 1) dirty = true
        i = k + 1
        return dirty
    }

    companion object {
        private const val INITIAL_SIZE = 50
    }
}
