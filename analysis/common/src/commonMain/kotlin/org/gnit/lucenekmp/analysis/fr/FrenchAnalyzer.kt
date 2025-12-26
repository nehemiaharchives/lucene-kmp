package org.gnit.lucenekmp.analysis.fr

import org.gnit.lucenekmp.analysis.CharArraySet

/**
 * Minimal FrenchAnalyzer stub providing DEFAULT_ARTICLES for ElisionFilter tests.
 */
class FrenchAnalyzer private constructor() {
    companion object {
        const val DEFAULT_STOPWORD_FILE: String = "french_stop.txt"

        /** Default set of articles for ElisionFilter. */
        val DEFAULT_ARTICLES: CharArraySet = CharArraySet.unmodifiableSet(
            CharArraySet(
                mutableListOf<Any>(
                    "l",
                    "m",
                    "t",
                    "qu",
                    "n",
                    "s",
                    "j",
                    "d",
                    "c",
                    "jusqu",
                    "quoiqu",
                    "lorsqu",
                    "puisqu"
                ),
                true
            )
        )
    }
}
