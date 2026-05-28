package org.gnit.lucenekmp.analysis.ckb

import org.gnit.lucenekmp.analysis.util.StemmerUtil.endsWith

/** Light stemmer for Sorani */
internal class SoraniStemmer {

    /**
     * Stem an input buffer of Sorani text.
     *
     * @param s input buffer
     * @param len length of input buffer
     * @return length of input buffer after normalization
     */
    fun stem(s: CharArray, len: Int): Int {
        var length = len
        // postposition
        if (length > 5 && endsWith(s, length, "دا")) {
            length -= 2
        } else if (length > 4 && endsWith(s, length, "نا")) {
            length--
        } else if (length > 6 && endsWith(s, length, "ەوە")) {
            length -= 3
        }

        // possessive pronoun
        if (length > 6 && (endsWith(s, length, "مان") || endsWith(s, length, "یان") || endsWith(s, length, "تان"))) {
            length -= 3
        }

        // indefinite singular ezafe
        if (length > 6 && endsWith(s, length, "ێکی")) {
            return length - 3
        } else if (length > 7 && endsWith(s, length, "یەکی")) {
            return length - 4
        }
        // indefinite singular
        if (length > 5 && endsWith(s, length, "ێک")) {
            return length - 2
        } else if (length > 6 && endsWith(s, length, "یەک")) {
            return length - 3
        } // definite singular
        else if (length > 6 && endsWith(s, length, "ەکە")) {
            return length - 3
        } else if (length > 5 && endsWith(s, length, "کە")) {
            return length - 2
        } // definite plural
        else if (length > 7 && endsWith(s, length, "ەکان")) {
            return length - 4
        } else if (length > 6 && endsWith(s, length, "کان")) {
            return length - 3
        } // indefinite plural ezafe
        else if (length > 7 && endsWith(s, length, "یانی")) {
            return length - 4
        } else if (length > 6 && endsWith(s, length, "انی")) {
            return length - 3
        } // indefinite plural
        else if (length > 6 && endsWith(s, length, "یان")) {
            return length - 3
        } else if (length > 5 && endsWith(s, length, "ان")) {
            return length - 2
        } // demonstrative plural
        else if (length > 7 && endsWith(s, length, "یانە")) {
            return length - 4
        } else if (length > 6 && endsWith(s, length, "انە")) {
            return length - 3
        } // demonstrative singular
        else if (length > 5 && (endsWith(s, length, "ایە") || endsWith(s, length, "ەیە"))) {
            return length - 2
        } else if (length > 4 && endsWith(s, length, "ە")) {
            return length - 1
        } // absolute singular ezafe
        else if (length > 4 && endsWith(s, length, "ی")) {
            return length - 1
        }
        return length
    }
}

