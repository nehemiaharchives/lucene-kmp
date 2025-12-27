package org.gnit.lucenekmp.analysis.id

import org.gnit.lucenekmp.analysis.util.StemmerUtil.deleteN
import org.gnit.lucenekmp.analysis.util.StemmerUtil.endsWith
import org.gnit.lucenekmp.analysis.util.StemmerUtil.startsWith

/**
 * Stemmer for Indonesian.
 *
 * Stems Indonesian words with the algorithm presented in: A Study of Stemming Effects on
 * Information Retrieval in Bahasa Indonesia, Fadillah Z Tala.
 */
internal class IndonesianStemmer {
    private var numSyllables = 0
    private var flags = 0

    private companion object {
        private const val REMOVED_KE = 1
        private const val REMOVED_PENG = 2
        private const val REMOVED_DI = 4
        private const val REMOVED_MENG = 8
        private const val REMOVED_TER = 16
        private const val REMOVED_BER = 32
        private const val REMOVED_PE = 64
    }

    /**
     * Stem a term (returning its new length).
     *
     * Use stemDerivational to control whether full stemming or only light inflectional stemming is done.
     */
    fun stem(text: CharArray, length: Int, stemDerivational: Boolean): Int {
        flags = 0
        numSyllables = 0
        for (i in 0 until length) {
            if (isVowel(text[i])) {
                numSyllables++
            }
        }

        var len = length
        if (numSyllables > 2) len = removeParticle(text, len)
        if (numSyllables > 2) len = removePossessivePronoun(text, len)
        if (stemDerivational) {
            len = stemDerivational(text, len)
        }
        return len
    }

    private fun stemDerivational(text: CharArray, length: Int): Int {
        var len = length
        var oldLength = len
        if (numSyllables > 2) len = removeFirstOrderPrefix(text, len)
        if (oldLength != len) {
            oldLength = len
            if (numSyllables > 2) len = removeSuffix(text, len)
            if (oldLength != len) {
                if (numSyllables > 2) len = removeSecondOrderPrefix(text, len)
            }
        } else {
            if (numSyllables > 2) len = removeSecondOrderPrefix(text, len)
            if (numSyllables > 2) len = removeSuffix(text, len)
        }
        return len
    }

    private fun isVowel(ch: Char): Boolean {
        return when (ch) {
            'a', 'e', 'i', 'o', 'u' -> true
            else -> false
        }
    }

    private fun removeParticle(text: CharArray, length: Int): Int {
        if (endsWith(text, length, "kah") || endsWith(text, length, "lah") || endsWith(text, length, "pun")) {
            numSyllables--
            return length - 3
        }
        return length
    }

    private fun removePossessivePronoun(text: CharArray, length: Int): Int {
        if (endsWith(text, length, "ku") || endsWith(text, length, "mu")) {
            numSyllables--
            return length - 2
        }
        if (endsWith(text, length, "nya")) {
            numSyllables--
            return length - 3
        }
        return length
    }

    private fun removeFirstOrderPrefix(text: CharArray, length: Int): Int {
        if (startsWith(text, length, "meng")) {
            flags = flags or REMOVED_MENG
            numSyllables--
            return deleteN(text, 0, length, 4)
        }

        if (startsWith(text, length, "meny") && length > 4 && isVowel(text[4])) {
            flags = flags or REMOVED_MENG
            text[3] = 's'
            numSyllables--
            return deleteN(text, 0, length, 3)
        }

        if (startsWith(text, length, "men")) {
            flags = flags or REMOVED_MENG
            numSyllables--
            return deleteN(text, 0, length, 3)
        }

        if (startsWith(text, length, "mem")) {
            flags = flags or REMOVED_MENG
            numSyllables--
            return deleteN(text, 0, length, 3)
        }

        if (startsWith(text, length, "me")) {
            flags = flags or REMOVED_MENG
            numSyllables--
            return deleteN(text, 0, length, 2)
        }

        if (startsWith(text, length, "peng")) {
            flags = flags or REMOVED_PENG
            numSyllables--
            return deleteN(text, 0, length, 4)
        }

        if (startsWith(text, length, "peny") && length > 4 && isVowel(text[4])) {
            flags = flags or REMOVED_PENG
            text[3] = 's'
            numSyllables--
            return deleteN(text, 0, length, 3)
        }

        if (startsWith(text, length, "peny")) {
            flags = flags or REMOVED_PENG
            numSyllables--
            return deleteN(text, 0, length, 4)
        }

        if (startsWith(text, length, "pen") && length > 3 && isVowel(text[3])) {
            flags = flags or REMOVED_PENG
            text[2] = 't'
            numSyllables--
            return deleteN(text, 0, length, 2)
        }

        if (startsWith(text, length, "pen")) {
            flags = flags or REMOVED_PENG
            numSyllables--
            return deleteN(text, 0, length, 3)
        }

        if (startsWith(text, length, "pem")) {
            flags = flags or REMOVED_PENG
            numSyllables--
            return deleteN(text, 0, length, 3)
        }

        if (startsWith(text, length, "di")) {
            flags = flags or REMOVED_DI
            numSyllables--
            return deleteN(text, 0, length, 2)
        }

        if (startsWith(text, length, "ter")) {
            flags = flags or REMOVED_TER
            numSyllables--
            return deleteN(text, 0, length, 3)
        }

        if (startsWith(text, length, "ke")) {
            flags = flags or REMOVED_KE
            numSyllables--
            return deleteN(text, 0, length, 2)
        }

        return length
    }

    private fun removeSecondOrderPrefix(text: CharArray, length: Int): Int {
        if (startsWith(text, length, "ber")) {
            flags = flags or REMOVED_BER
            numSyllables--
            return deleteN(text, 0, length, 3)
        }

        if (length == 7 && startsWith(text, length, "belajar")) {
            flags = flags or REMOVED_BER
            numSyllables--
            return deleteN(text, 0, length, 3)
        }

        if (startsWith(text, length, "be") && length > 4 && !isVowel(text[2]) && text[3] == 'e' && text[4] == 'r') {
            flags = flags or REMOVED_BER
            numSyllables--
            return deleteN(text, 0, length, 2)
        }

        if (startsWith(text, length, "per")) {
            numSyllables--
            return deleteN(text, 0, length, 3)
        }

        if (length == 7 && startsWith(text, length, "pelajar")) {
            numSyllables--
            return deleteN(text, 0, length, 3)
        }

        if (startsWith(text, length, "pe")) {
            flags = flags or REMOVED_PE
            numSyllables--
            return deleteN(text, 0, length, 2)
        }

        return length
    }

    private fun removeSuffix(text: CharArray, length: Int): Int {
        if (endsWith(text, length, "kan") &&
            (flags and REMOVED_KE) == 0 &&
            (flags and REMOVED_PENG) == 0 &&
            (flags and REMOVED_PE) == 0
        ) {
            numSyllables--
            return length - 3
        }

        if (endsWith(text, length, "an") &&
            (flags and REMOVED_DI) == 0 &&
            (flags and REMOVED_MENG) == 0 &&
            (flags and REMOVED_TER) == 0
        ) {
            numSyllables--
            return length - 2
        }

        if (endsWith(text, length, "i") &&
            !endsWith(text, length, "si") &&
            (flags and REMOVED_BER) == 0 &&
            (flags and REMOVED_KE) == 0 &&
            (flags and REMOVED_PENG) == 0
        ) {
            numSyllables--
            return length - 1
        }

        return length
    }
}
