package org.gnit.lucenekmp.analysis.te

import org.gnit.lucenekmp.analysis.util.StemmerUtil.endsWith

/** Stemmer for Telugu. */
internal class TeluguStemmer {
    fun stem(buffer: CharArray, len: Int): Int {
        // 4
        if ((len > 5) && (endsWith(buffer, len, "ళ్ళు") || endsWith(buffer, len, "డ్లు"))) {
            return len - 4
        }

        // 2
        if ((len > 3) &&
            (endsWith(buffer, len, "డు") ||
                endsWith(buffer, len, "ము") ||
                endsWith(buffer, len, "వు") ||
                endsWith(buffer, len, "లు") ||
                endsWith(buffer, len, "ని") ||
                endsWith(buffer, len, "ను") ||
                endsWith(buffer, len, "చే") ||
                endsWith(buffer, len, "కై") ||
                endsWith(buffer, len, "లో") ||
                endsWith(buffer, len, "డు") ||
                endsWith(buffer, len, "ది") ||
                endsWith(buffer, len, "కి") ||
                endsWith(buffer, len, "సు") ||
                endsWith(buffer, len, "వై") ||
                endsWith(buffer, len, "పై"))) {
            return len - 2
        }

        // 1
        if ((len > 2) &&
            (endsWith(buffer, len, "ి") ||
                endsWith(buffer, len, "ీ") ||
                endsWith(buffer, len, "ు") ||
                endsWith(buffer, len, "ూ") ||
                endsWith(buffer, len, "ె") ||
                endsWith(buffer, len, "ే") ||
                endsWith(buffer, len, "ొ") ||
                endsWith(buffer, len, "ో") ||
                endsWith(buffer, len, "ా"))) {
            return len - 1
        }

        return len
    }
}
