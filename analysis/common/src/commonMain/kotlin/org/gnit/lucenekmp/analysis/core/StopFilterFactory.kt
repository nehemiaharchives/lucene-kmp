package org.gnit.lucenekmp.analysis.core

import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.StopFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.en.AbstractWordsFileFilterFactory
import org.gnit.lucenekmp.analysis.en.EnglishAnalyzer
import org.gnit.lucenekmp.analysis.WordlistLoader

/**
 * Factory for [StopFilter].
 *
 * <pre class="prettyprint">
 * &lt;fieldType name="text_stop" class="solr.TextField" positionIncrementGap="100" autoGeneratePhraseQueries="true"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.WhitespaceTokenizerFactory"/&gt;
 *     &lt;filter class="solr.StopFilterFactory" ignoreCase="true"
 *             words="stopwords.txt" format="wordset"
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 *
 * <p>All attributes are optional:
 *
 * <ul>
 *   <li><code>ignoreCase</code> defaults to <code>false</code>
 *   <li><code>words</code> should be the name of a stopwords file to parse, if not specified the
 *       factory will use [EnglishAnalyzer.ENGLISH_STOP_WORDS_SET]
 *   <li><code>format</code> defines how the <code>words</code> file will be parsed, and defaults to
 *       <code>wordset</code>. If <code>words</code> is not specified, then <code>format</code> must
 *       not be specified.
 * </ul>
 *
 * <p>The valid values for the <code>format</code> option are:
 *
 * <ul>
 *   <li><code>wordset</code> - This is the default format, which supports one word per line
 *       (including any intra-word whitespace) and allows whole line comments beginning with the "#"
 *       character. Blank lines are ignored. See [WordlistLoader.getLines]
 *       for details.
 *   <li><code>snowball</code> - This format allows for multiple words specified on each line, and
 *       trailing comments may be specified using the vertical line ("&#124;"). Blank lines are
 *       ignored. See [WordlistLoader.getSnowballWordSet]
 *       for details.
 * </ul>
 *
 * @since 3.1
 * @lucene.spi {@value #NAME}
 */
class StopFilterFactory : AbstractWordsFileFilterFactory {
    /** SPI name */
    companion object {
        const val NAME: String = "stop"
    }

    /** Creates a new StopFilterFactory */
    constructor(args: MutableMap<String, String>) : super(args)

    /** Default ctor for compatibility with SPI */
    constructor() : super()

    fun getStopWords(): CharArraySet? {
        return getWords()
    }

    override fun createDefaultWords(): CharArraySet {
        return CharArraySet(EnglishAnalyzer.ENGLISH_STOP_WORDS_SET, isIgnoreCase())
    }

    override fun create(input: TokenStream): TokenStream {
        val stopFilter = StopFilter(input, getWords()!!)
        return stopFilter
    }
}

