package org.gnit.lucenekmp.analysis.cn.smart

import okio.Buffer
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.LowerCaseFilter
import org.gnit.lucenekmp.analysis.StopFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.WordlistLoader
import org.gnit.lucenekmp.analysis.en.PorterStemFilter
import org.gnit.lucenekmp.jdkport.OkioSourceInputStream

/**
 * SmartChineseAnalyzer is an analyzer for Chinese or mixed Chinese-English text.
 *
 * @lucene.experimental
 */
class SmartChineseAnalyzer : Analyzer {
    private val stopWords: CharArraySet

    companion object {
        private const val STOPWORD_FILE_COMMENT = "//"

        private val DEFAULT_STOPWORD_DATA: String = """
////////// Punctuation tokens to remove ////////////////
,
.
`
-
_
=
?
'
|
"
(
)
{
}
[
]
<
>
*
#
&
^
$
@
!
~
:
;
+
/
\\
《
》
—
－
，
。
、
：
；
！
·
？
“
”
）
（
【
】
［
］
●
// the line below contains an IDEOGRAPHIC SPACE character (Used as a space in Chinese)
　
//////////////// English Stop Words ////////////////
//////////////// Chinese Stop Words ////////////////
""".trimIndent()

        /** Returns an unmodifiable instance of the default stop-words set. */
        fun getDefaultStopSet(): CharArraySet {
            return DefaultSetHolder.DEFAULT_STOP_SET
        }

        private object DefaultSetHolder {
            val DEFAULT_STOP_SET: CharArraySet = loadDefaultStopWordSet()

            private fun loadDefaultStopWordSet(): CharArraySet {
                val buffer = Buffer().apply { write(DEFAULT_STOPWORD_DATA.encodeToByteArray()) }
                val stream = OkioSourceInputStream(buffer)
                return try {
                    CharArraySet.unmodifiableSet(WordlistLoader.getWordSet(stream, STOPWORD_FILE_COMMENT))
                } finally {
                    stream.close()
                }
            }
        }
    }

    /** Create a new SmartChineseAnalyzer, using the default stopword list. */
    constructor() : this(true)

    /** Create a new SmartChineseAnalyzer, optionally using the default stopword list. */
    constructor(useDefaultStopWords: Boolean) {
        stopWords = if (useDefaultStopWords) DefaultSetHolder.DEFAULT_STOP_SET else CharArraySet.EMPTY_SET
    }

    /** Create a new SmartChineseAnalyzer, using the provided Set of stopwords. */
    constructor(stopWords: CharArraySet?) {
        this.stopWords = stopWords ?: CharArraySet.EMPTY_SET
    }

    override fun createComponents(fieldName: String): TokenStreamComponents {
        val tokenizer: Tokenizer = HMMChineseTokenizer()
        var result: TokenStream = tokenizer
        result = PorterStemFilter(result)
        if (!stopWords.isEmpty()) {
            result = StopFilter(result, stopWords)
        }
        return TokenStreamComponents(tokenizer, result)
    }

    override fun normalize(fieldName: String, `in`: TokenStream): TokenStream {
        return LowerCaseFilter(`in`)
    }
}
