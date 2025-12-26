package org.gnit.lucenekmp.analysis.en

import okio.IOException
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.util.ResourceLoader
import org.gnit.lucenekmp.util.ResourceLoaderAware

/**
 * Abstract parent class for analysis factories that accept a stopwords file as input.
 */
abstract class AbstractWordsFileFilterFactory protected constructor(args: MutableMap<String, String>) : TokenFilterFactory(args), ResourceLoaderAware {
    companion object {
        const val FORMAT_WORDSET: String = "wordset"
        const val FORMAT_SNOWBALL: String = "snowball"
    }

    private var words: CharArraySet? = null
    private var wordFiles: String? = null
    private var format: String? = null
    private var ignoreCase: Boolean = false

    init {
        wordFiles = get(args, "words")
        format = get(args, "format") ?: if (wordFiles == null) null else FORMAT_WORDSET
        ignoreCase = getBoolean(args, "ignoreCase", false)
        if (args.isNotEmpty()) {
            throw IllegalArgumentException("Unknown parameters: $args")
        }
    }

    /** Default ctor for compatibility with SPI */
    protected constructor() : this(mutableMapOf()) {
        throw defaultCtorException()
    }

    /** Initialize the set of stopwords provided via ResourceLoader, or using defaults. */
    @Throws(IOException::class)
    override fun inform(loader: ResourceLoader) {
        val files = wordFiles
        val formatValue = format
        if (files != null) {
            words = if (FORMAT_WORDSET.equals(formatValue, ignoreCase = true)) {
                getWordSet(loader, files, ignoreCase)
            } else if (FORMAT_SNOWBALL.equals(formatValue, ignoreCase = true)) {
                getSnowballWordSet(loader, files, ignoreCase)
            } else {
                throw IllegalArgumentException("Unknown 'format' specified for 'words' file: $formatValue")
            }
        } else {
            if (formatValue != null) {
                throw IllegalArgumentException("'format' can not be specified w/o an explicit 'words' file: $formatValue")
            }
            words = createDefaultWords()
        }
    }

    /** Default word set implementation. */
    protected abstract fun createDefaultWords(): CharArraySet

    fun getWords(): CharArraySet? {
        return words
    }

    fun getWordFiles(): String? {
        return wordFiles
    }

    fun getFormat(): String? {
        return format
    }

    fun isIgnoreCase(): Boolean {
        return ignoreCase
    }
}
