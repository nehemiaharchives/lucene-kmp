package org.gnit.lucenekmp.analysis.ja

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenizerFactory
import org.gnit.lucenekmp.analysis.ja.dict.UserDictionary
import org.gnit.lucenekmp.jdkport.Charset
import org.gnit.lucenekmp.jdkport.CodingErrorAction
import org.gnit.lucenekmp.jdkport.InputStreamReader
import org.gnit.lucenekmp.util.AttributeFactory
import org.gnit.lucenekmp.util.ResourceLoader
import org.gnit.lucenekmp.util.ResourceLoaderAware
import kotlin.properties.Delegates

/**
 * Factory for {@link org.apache.lucene.analysis.ja.JapaneseTokenizer}.
 *
 * <pre class="prettyprint">
 * &lt;fieldType name="text_ja" class="solr.TextField"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.JapaneseTokenizerFactory"
 *       mode="NORMAL"
 *       userDictionary="user.txt"
 *       userDictionaryEncoding="UTF-8"
 *       discardPunctuation="true"
 *       discardCompoundToken="false"
 *     /&gt;
 *     &lt;filter class="solr.JapaneseBaseFormFilterFactory"/&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;
 * </pre>
 *
 * <p>Additional expert user parameters nBestCost and nBestExamples can be used to include
 * additional searchable tokens that those most likely according to the statistical model. A typical
 * use-case for this is to improve recall and make segmentation more resilient to mistakes. The
 * feature can also be used to get a decompounding effect.
 *
 * <p>The nBestCost parameter specifies an additional Viterbi cost, and when used, JapaneseTokenizer
 * will include all tokens in Viterbi paths that are within the nBestCost value of the best path.
 *
 * <p>Finding a good value for nBestCost can be difficult to do by hand. The nBestExamples parameter
 * can be used to find an nBestCost value based on examples with desired segmentation outcomes.
 *
 * <p>For example, a value of /箱根山-箱根/成田空港-成田/ indicates that in the texts, 箱根山 (Mt. Hakone) and
 * 成田空港 (Narita Airport) we'd like a cost that gives is us 箱根 (Hakone) and 成田 (Narita). Notice that
 * costs are estimated for each example individually, and the maximum nBestCost found across all
 * examples is used.
 *
 * <p>If both nBestCost and nBestExamples is used in a configuration, the largest value of the two
 * is used.
 *
 * <p>Parameters nBestCost and nBestExamples work with all tokenizer modes, but it makes the most
 * sense to use them with NORMAL mode.
 *
 * @since 3.6.0
 * @lucene.spi {@value #NAME}
 */
class JapaneseTokenizerFactory : TokenizerFactory, ResourceLoaderAware {

    private var userDictionary: UserDictionary? = null

    private lateinit var mode: JapaneseTokenizer.Mode
    private var discardPunctuation by Delegates.notNull<Boolean>()
    private var discardCompoundToken by Delegates.notNull<Boolean>()
    private var userDictionaryPath: String? = null
    private var userDictionaryEncoding: String? = null

    /* Example string for NBEST output.
     * its form as:
     *   nbestExamples := [ / ] example [ / example ]... [ / ]
     *   example := TEXT - TOKEN
     *   TEXT := input text
     *   TOKEN := token should be in nbest result
     * Ex. /箱根山-箱根/成田空港-成田/
     * When the result tokens are "箱根山", "成田空港" in NORMAL mode,
     * /箱根山-箱根/成田空港-成田/ requests "箱根" and "成田" to be in the result in NBEST output.
     */
    private var nbestExamples: String? = null
    private var nbestCost by Delegates.notNull<Int>()

    /** Creates a new JapaneseTokenizerFactory */
    constructor(args: MutableMap<String, String>) : super(args) {
        mode = JapaneseTokenizer.Mode.valueOf(get(args, MODE_PARAM, JapaneseTokenizer.DEFAULT_MODE.toString()).uppercase())
        userDictionaryPath = args.remove(USER_DICT_PATH)
        userDictionaryEncoding = args.remove(USER_DICT_ENCODING)
        discardPunctuation = getBoolean(args, DISCARD_PUNCTUATION, true)
        discardCompoundToken = getBoolean(args, DISCARD_COMPOUND_TOKEN, true)
        nbestCost = getInt(args, NBEST_COST, 0)
        nbestExamples = args.remove(NBEST_EXAMPLES)
        if (args.isNotEmpty()) {
            throw IllegalArgumentException("Unknown parameters: $args")
        }
    }

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    @Throws(IOException::class)
    override fun inform(loader: ResourceLoader) {
        userDictionary = if (userDictionaryPath != null) {
            loader.openResource(userDictionaryPath!!).use { stream ->
                val encoding = userDictionaryEncoding ?: "UTF-8"
                val decoder = Charset.forName(encoding)
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                val reader = InputStreamReader(stream, decoder)
                UserDictionary.open(reader)
            }
        } else {
            null
        }
    }

    override fun create(factory: AttributeFactory): JapaneseTokenizer {
        val t = JapaneseTokenizer(factory, userDictionary, discardPunctuation, discardCompoundToken, mode)
        if (nbestExamples != null) {
            nbestCost = maxOf(nbestCost, t.calcNBestCost(nbestExamples!!))
        }
        t.setNBestCost(nbestCost)
        return t
    }

    companion object {
        const val NAME: String = "japanese"

        private const val MODE_PARAM: String = "mode"
        private const val USER_DICT_PATH: String = "userDictionary"
        private const val USER_DICT_ENCODING: String = "userDictionaryEncoding"
        private const val DISCARD_PUNCTUATION: String = "discardPunctuation"
        private const val DISCARD_COMPOUND_TOKEN: String = "discardCompoundToken"
        private const val NBEST_COST: String = "nBestCost"
        private const val NBEST_EXAMPLES: String = "nBestExamples"
    }
}
