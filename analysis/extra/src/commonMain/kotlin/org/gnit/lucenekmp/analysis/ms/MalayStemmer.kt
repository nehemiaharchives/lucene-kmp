package org.gnit.lucenekmp.analysis.ms

/**
 * Light Stemmer for Malay.
 *
 * Applies conservative dictionary-free stripping for common Malay particles, pronouns, prefixes,
 * and suffixes. The rule shape follows the same syllable-count guard used by Lucene's Indonesian
 * stemmer because Malay shares the relevant meN-, ber-, di-, ter-, ke-, peN-, -kan, -an, and -i
 * affix family.
 */
internal class MalayStemmer {
    private var numSyllables = 0
    private var flags = 0

    /**
     * Stem an input buffer of Malay text.
     *
     * @param s input buffer
     * @param len length of input buffer
     * @return length of input buffer after stemming
     */
    fun stem(s: CharArray, len: Int): Int {
        if (len <= 3) return len
        var word = s.concatToString(0, len)
        val lower = word.lowercase()
        if (word != lower) {
            // Avoid lowercasing or modifying mixed-case tokens in this filter.
            return len
        }

        flags = 0
        numSyllables = countSyllables(word)

        if (numSyllables > 2) word = removeParticle(word)
        if (numSyllables > 2) word = removePossessivePronoun(word)
        if (numSyllables > 2) word = stemDerivational(word)

        val outLen = word.length
        val out = word.toCharArray()
        var i = 0
        while (i < outLen) {
            s[i] = out[i]
            i += 1
        }
        return outLen
    }

    private fun stemDerivational(word: String): String {
        var result = word
        var old = result
        if (numSyllables > 2) result = removeFirstOrderPrefix(result)
        if (old != result) {
            old = result
            if (numSyllables > 2) result = removeSuffix(result)
            if (old != result && numSyllables > 2) result = removeSecondOrderPrefix(result)
        } else {
            if (numSyllables > 2) result = removeSecondOrderPrefix(result)
            if (numSyllables > 2) result = removeSuffix(result)
        }
        return result
    }

    private fun removeParticle(word: String): String {
        return when {
            word.endsWith("kah") || word.endsWith("lah") || word.endsWith("pun") -> {
                numSyllables -= 1
                word.substring(0, word.length - 3)
            }
            else -> word
        }
    }

    private fun removePossessivePronoun(word: String): String {
        return when {
            word.endsWith("ku") || word.endsWith("mu") -> {
                numSyllables -= 1
                word.substring(0, word.length - 2)
            }
            word.endsWith("nya") -> {
                numSyllables -= 1
                word.substring(0, word.length - 3)
            }
            else -> word
        }
    }

    private fun removeFirstOrderPrefix(word: String): String {
        return when {
            word.startsWith("meng") -> {
                flags = flags or REMOVED_MENG
                numSyllables -= 1
                word.substring(4)
            }
            word.startsWith("meny") && word.length > 4 && isVowel(word[4]) -> {
                flags = flags or REMOVED_MENG
                numSyllables -= 1
                "s" + word.substring(4)
            }
            word.startsWith("men") && word.length > 3 && isVowel(word[3]) -> {
                flags = flags or REMOVED_MENG
                numSyllables -= 1
                "t" + word.substring(3)
            }
            word.startsWith("men") -> {
                flags = flags or REMOVED_MENG
                numSyllables -= 1
                word.substring(3)
            }
            word.startsWith("mem") && word.length > 3 && isVowel(word[3]) -> {
                flags = flags or REMOVED_MENG
                numSyllables -= 1
                "p" + word.substring(3)
            }
            word.startsWith("mem") -> {
                flags = flags or REMOVED_MENG
                numSyllables -= 1
                word.substring(3)
            }
            word.startsWith("me") -> {
                flags = flags or REMOVED_MENG
                numSyllables -= 1
                word.substring(2)
            }
            word.startsWith("peng") -> {
                flags = flags or REMOVED_PENG
                numSyllables -= 1
                word.substring(4)
            }
            word.startsWith("peny") && word.length > 4 && isVowel(word[4]) -> {
                flags = flags or REMOVED_PENG
                numSyllables -= 1
                "s" + word.substring(4)
            }
            word.startsWith("peny") -> {
                flags = flags or REMOVED_PENG
                numSyllables -= 1
                word.substring(4)
            }
            word.startsWith("pen") && word.length > 3 && isVowel(word[3]) -> {
                flags = flags or REMOVED_PENG
                numSyllables -= 1
                "t" + word.substring(3)
            }
            word.startsWith("pen") -> {
                flags = flags or REMOVED_PENG
                numSyllables -= 1
                word.substring(3)
            }
            word.startsWith("pem") && word.length > 3 && isVowel(word[3]) -> {
                flags = flags or REMOVED_PENG
                numSyllables -= 1
                "p" + word.substring(3)
            }
            word.startsWith("pem") -> {
                flags = flags or REMOVED_PENG
                numSyllables -= 1
                word.substring(3)
            }
            word.startsWith("di") -> {
                flags = flags or REMOVED_DI
                numSyllables -= 1
                word.substring(2)
            }
            word.startsWith("ter") -> {
                flags = flags or REMOVED_TER
                numSyllables -= 1
                word.substring(3)
            }
            word.startsWith("ke") -> {
                flags = flags or REMOVED_KE
                numSyllables -= 1
                word.substring(2)
            }
            else -> word
        }
    }

    private fun removeSecondOrderPrefix(word: String): String {
        return when {
            word.startsWith("ber") -> {
                flags = flags or REMOVED_BER
                numSyllables -= 1
                word.substring(3)
            }
            word == "belajar" -> {
                flags = flags or REMOVED_BER
                numSyllables -= 1
                word.substring(3)
            }
            word.length > 4 && word.startsWith("be") && !isVowel(word[2]) && word[3] == 'e' && word[4] == 'r' -> {
                flags = flags or REMOVED_BER
                numSyllables -= 1
                word.substring(2)
            }
            word.startsWith("per") -> {
                numSyllables -= 1
                word.substring(3)
            }
            word == "pelajar" -> {
                numSyllables -= 1
                word.substring(3)
            }
            word.startsWith("pe") -> {
                flags = flags or REMOVED_PE
                numSyllables -= 1
                word.substring(2)
            }
            else -> word
        }
    }

    private fun removeSuffix(word: String): String {
        return when {
            word.endsWith("kan") && (flags and REMOVED_KE) == 0 && (flags and REMOVED_PENG) == 0 && (flags and REMOVED_PE) == 0 -> {
                numSyllables -= 1
                word.substring(0, word.length - 3)
            }
            word.endsWith("an") && (flags and REMOVED_DI) == 0 && (flags and REMOVED_MENG) == 0 && (flags and REMOVED_TER) == 0 -> {
                numSyllables -= 1
                word.substring(0, word.length - 2)
            }
            word.endsWith("i") &&
                !word.endsWith("si") &&
                (flags and REMOVED_BER) == 0 &&
                (flags and REMOVED_KE) == 0 &&
                (flags and REMOVED_PENG) == 0 -> {
                numSyllables -= 1
                word.substring(0, word.length - 1)
            }
            else -> word
        }
    }

    private fun countSyllables(word: String): Int {
        var count = 0
        var i = 0
        while (i < word.length) {
            if (isVowel(word[i])) count += 1
            i += 1
        }
        return count
    }

    private fun isVowel(ch: Char): Boolean {
        return ch == 'a' || ch == 'e' || ch == 'i' || ch == 'o' || ch == 'u'
    }

    companion object {
        private const val REMOVED_KE = 1
        private const val REMOVED_PENG = 2
        private const val REMOVED_DI = 4
        private const val REMOVED_MENG = 8
        private const val REMOVED_TER = 16
        private const val REMOVED_BER = 32
        private const val REMOVED_PE = 64
    }
}
