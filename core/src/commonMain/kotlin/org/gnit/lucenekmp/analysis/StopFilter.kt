package org.gnit.lucenekmp.analysis

import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute

/** Removes stop words from a token stream.  */
class StopFilter(`in`: TokenStream, private val stopWords: CharArraySet) : FilteringTokenFilter(`in`) {
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)

    /** Returns the next input Token whose term() is not a stop word.  */
    override fun accept(): Boolean {

        val buffer: CharArray = termAtt.buffer()
        val offset = 0
        val length = termAtt.length

        return !stopWords.contains(buffer, offset, length)
    }

    companion object {
        /**
         * Builds a Set from an array of stop words, appropriate for passing into the StopFilter
         * constructor. This permits this stopWords construction to be cached once when an Analyzer is
         * constructed.
         *
         * @param stopWords An array of stopwords
         * @see .makeStopSet
         */
        fun makeStopSet(vararg stopWords: String): CharArraySet {
            return makeStopSet(stopWords.toMutableList(), false)
        }

        /**
         * Builds a Set from an array of stop words, appropriate for passing into the StopFilter
         * constructor. This permits this stopWords construction to be cached once when an Analyzer is
         * constructed.
         *
         * @param stopWords A List of Strings or char[] or any other toString()-able list representing the
         * stopwords
         * @return A Set ([CharArraySet]) containing the words
         * @see .makeStopSet
         */
        fun makeStopSet(stopWords: MutableList<*>): CharArraySet {
            return makeStopSet(stopWords, false)
        }

        /**
         * Creates a stopword set from the given stopword array.
         *
         * @param stopWords An array of stopwords
         * @param ignoreCase If true, all words are lower cased first.
         * @return a Set containing the words
         */
        fun makeStopSet(stopWords: Array<String>, ignoreCase: Boolean): CharArraySet {
            return makeStopSet(
                stopWords.toMutableList(), ignoreCase
            )
        }

        /**
         * Creates a stopword set from the given stopword list.
         *
         * @param stopWords A List of Strings or char[] or any other toString()-able list representing the
         * stopwords
         * @param ignoreCase if true, all words are lower cased first
         * @return A Set ([CharArraySet]) containing the words
         */
        fun makeStopSet(stopWords: MutableList<*>, ignoreCase: Boolean): CharArraySet {
            //java.util.Objects.requireNonNull(stopWords, "stopWords")
            val stopSet = CharArraySet(stopWords.size, ignoreCase)
            stopSet.addAll(stopWords as Collection<Any>)
            return stopSet
        }
    }
}
