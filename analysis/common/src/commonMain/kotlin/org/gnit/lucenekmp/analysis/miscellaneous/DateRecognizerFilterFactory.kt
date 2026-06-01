package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.jdkport.Locale
import kotlin.properties.Delegates

/**
 * Factory for [DateRecognizerFilter].
 *
 * <pre class="prettyprint">
 * &lt;fieldType name="text_filter_none_date" class="solr.TextField" positionIncrementGap="100"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.WhitespaceTokenizerFactory"/&gt;
 *     &lt;filter class="solr.DateRecognizerFilterFactory" datePattern="yyyy/mm/dd" locale="en-US" /&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;
 * </pre>
 *
 * The `datePattern` is optional. If omitted, [DateRecognizerFilter] will be created
 * with the default English date recognizer. The `locale` is optional and if omitted the
 * filter will be created with English defaults.
 *
 * @since 5.5.0
 * @lucene.spi {@value #NAME}
 */
class DateRecognizerFilterFactory : TokenFilterFactory {
    private var dateRecognizer: DateRecognizer = EnglishDefaultDateRecognizer
    private var locale: Locale by Delegates.notNull()

    /** Creates a new FingerprintFilterFactory */
    constructor(args: MutableMap<String, String>) : super(args) {
        locale = getLocale(get(args, LOCALE))
        dateRecognizer = getDateRecognizer(get(args, DATE_PATTERN))
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    /** Default ctor for compatibility with SPI */
    constructor() {
        throw defaultCtorException()
    }

    override fun create(input: TokenStream): TokenStream {
        return DateRecognizerFilter(input, dateRecognizer)
    }

    private fun getLocale(localeStr: String?): Locale {
        if (localeStr == null) {
            return Locale.US
        }
        require(Regex("^[A-Za-z]{2,8}(-[A-Za-z0-9]{2,8})*$").matches(localeStr)) {
            "Invalid locale tag: $localeStr"
        }
        val parts = localeStr.split('-')
        val language = parts.getOrNull(0)
        val country = parts.getOrNull(1)
        val variant = parts.getOrNull(2)
        return Locale(language, country, variant)
    }

    private fun getDateRecognizer(datePattern: String?): DateRecognizer {
        if (datePattern != null) {
            return PatternDateRecognizer(datePattern)
        }
        return when (locale.language?.lowercase()) {
            "en" -> EnglishDefaultDateRecognizer
            else -> EnglishDefaultDateRecognizer
        }
    }

    companion object {
        /** SPI name */
        const val NAME = "dateRecognizer"
        const val DATE_PATTERN = "datePattern"
        const val LOCALE = "locale"
    }
}
