package org.gnit.lucenekmp.analysis.miscellaneous

import okio.IOException
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.jdkport.fromCharArray
import org.gnit.lucenekmp.util.ResourceLoader
import org.gnit.lucenekmp.util.ResourceLoaderAware
import kotlin.properties.Delegates

/**
 * Factory for [WordDelimiterGraphFilter].
 *
 * @since 6.5.0
 * @lucene.spi [NAME]
 */
class WordDelimiterGraphFilterFactory : TokenFilterFactory, ResourceLoaderAware {
    companion object {
        /** SPI name */
        const val NAME = "wordDelimiterGraph"

        const val PROTECTED_TOKENS = "protected"
        const val TYPES = "types"
        const val OFFSETS = "adjustOffsets"

        // source => type
        private val TYPE_PATTERN = Regex("(.*)\\s*=>\\s*(.*)\\s*$")
    }

    private var wordFiles: String? = null
    private var types: String? = null
    private var flags: Int by Delegates.notNull()
    private var typeTable: ByteArray? = null
    private var protectedWords: CharArraySet? = null
    private var adjustOffsets: Boolean by Delegates.notNull()

    private val out = CharArray(256)

    /** Creates a new WordDelimiterGraphFilterFactory */
    constructor(args: MutableMap<String, String>) : super(args) {
        var flags = 0
        if (getInt(args, "generateWordParts", 1) != 0) {
            flags = flags or WordDelimiterGraphFilter.GENERATE_WORD_PARTS
        }
        if (getInt(args, "generateNumberParts", 1) != 0) {
            flags = flags or WordDelimiterGraphFilter.GENERATE_NUMBER_PARTS
        }
        if (getInt(args, "catenateWords", 0) != 0) {
            flags = flags or WordDelimiterGraphFilter.CATENATE_WORDS
        }
        if (getInt(args, "catenateNumbers", 0) != 0) {
            flags = flags or WordDelimiterGraphFilter.CATENATE_NUMBERS
        }
        if (getInt(args, "catenateAll", 0) != 0) {
            flags = flags or WordDelimiterGraphFilter.CATENATE_ALL
        }
        if (getInt(args, "splitOnCaseChange", 1) != 0) {
            flags = flags or WordDelimiterGraphFilter.SPLIT_ON_CASE_CHANGE
        }
        if (getInt(args, "splitOnNumerics", 1) != 0) {
            flags = flags or WordDelimiterGraphFilter.SPLIT_ON_NUMERICS
        }
        if (getInt(args, "preserveOriginal", 0) != 0) {
            flags = flags or WordDelimiterGraphFilter.PRESERVE_ORIGINAL
        }
        if (getInt(args, "stemEnglishPossessive", 1) != 0) {
            flags = flags or WordDelimiterGraphFilter.STEM_ENGLISH_POSSESSIVE
        }
        if (getInt(args, "ignoreKeywords", 0) != 0) {
            flags = flags or WordDelimiterGraphFilter.IGNORE_KEYWORDS
        }
        wordFiles = get(args, PROTECTED_TOKENS)
        types = get(args, TYPES)
        this.flags = flags
        this.adjustOffsets = getBoolean(args, OFFSETS, true)
        require(args.isEmpty()) { "Unknown parameters: $args" }
    }

    /** Default ctor for compatibility with SPI */
    constructor() : super() {
        throw defaultCtorException()
    }

    @Throws(IOException::class)
    override fun inform(loader: ResourceLoader) {
        val localWordFiles = wordFiles
        if (localWordFiles != null) {
            protectedWords = getWordSet(loader, localWordFiles, false)
        }
        val localTypes = types
        if (localTypes != null) {
            val files = splitFileNames(localTypes)
            val wlist = mutableListOf<String>()
            for (file in files) {
                val lines = getLines(loader, file.trim())
                wlist.addAll(lines)
            }
            typeTable = parseTypes(wlist)
        }
    }

    override fun create(input: TokenStream): TokenFilter {
        return WordDelimiterGraphFilter(
            input,
            adjustOffsets,
            typeTable ?: WordDelimiterIterator.DEFAULT_WORD_DELIM_TABLE,
            flags,
            protectedWords
        )
    }

    // parses a list of MappingCharFilter style rules into a custom byte[] type table
    private fun parseTypes(rules: List<String>): ByteArray {
        val typeMap = mutableMapOf<Char, Byte>()
        for (rule in rules) {
            val m = TYPE_PATTERN.matchEntire(rule)
            require(m != null) { "Invalid Mapping Rule : [$rule]" }
            val lhs = parseString(m.groupValues[1].trim())
            val rhs = parseType(m.groupValues[2].trim())
            require(lhs.length == 1) { "Invalid Mapping Rule : [$rule]. Only a single character is allowed." }
            require(rhs != null) { "Invalid Mapping Rule : [$rule]. Illegal type." }
            typeMap[lhs[0]] = rhs
        }

        val maxKey = typeMap.keys.maxOrNull()?.code ?: 0
        val types =
            ByteArray(
                kotlin.math.max(
                    maxKey + 1,
                    WordDelimiterIterator.DEFAULT_WORD_DELIM_TABLE.size
                )
            )
        for (i in types.indices) {
            types[i] = WordDelimiterIterator.getType(i)
        }
        for (entry in typeMap.entries) {
            types[entry.key.code] = entry.value
        }
        return types
    }

    private fun parseType(s: String): Byte? {
        return when (s) {
            "LOWER" -> WordDelimiterIterator.LOWER.toByte()
            "UPPER" -> WordDelimiterIterator.UPPER.toByte()
            "ALPHA" -> WordDelimiterIterator.ALPHA.toByte()
            "DIGIT" -> WordDelimiterIterator.DIGIT.toByte()
            "ALPHANUM" -> WordDelimiterIterator.ALPHANUM.toByte()
            "SUBWORD_DELIM" -> WordDelimiterIterator.SUBWORD_DELIM.toByte()
            else -> null
        }
    }

    private fun parseString(s: String): String {
        var readPos = 0
        val len = s.length
        var writePos = 0
        while (readPos < len) {
            var c = s[readPos++]
            if (c == '\\') {
                require(readPos < len) { "Invalid escaped char in [$s]" }
                c = s[readPos++]
                c =
                    when (c) {
                        '\\' -> '\\'
                        'n' -> '\n'
                        't' -> '\t'
                        'r' -> '\r'
                        'b' -> '\b'
                        'f' -> '\u000C'
                        'u' -> {
                            require(readPos + 3 < len) { "Invalid escaped char in [$s]" }
                            val value = s.substring(readPos, readPos + 4).toInt(16).toChar()
                            readPos += 4
                            value
                        }
                        else -> c
                    }
            }
            out[writePos++] = c
        }
        return String.fromCharArray(out, 0, writePos)
    }
}
