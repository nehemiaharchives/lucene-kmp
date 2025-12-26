package org.gnit.lucenekmp.analysis

import okio.IOException
import org.gnit.lucenekmp.jdkport.CharsetDecoder
import org.gnit.lucenekmp.jdkport.CodingErrorAction
import org.gnit.lucenekmp.jdkport.InputStreamReader
import org.gnit.lucenekmp.jdkport.ParseException
import org.gnit.lucenekmp.jdkport.StandardCharsets
import org.gnit.lucenekmp.util.ResourceLoader
import org.gnit.lucenekmp.util.Version

/**
 * Abstract parent class for analysis factories [TokenizerFactory], [TokenFilterFactory]
 * and [CharFilterFactory].
 *
 *
 * The typical lifecycle for a factory consumer is:
 *
 *
 *  1. Create factory via its constructor (or via XXXFactory.forName)
 *  1. (Optional) If the factory uses resources such as files, [       ][ResourceLoaderAware.inform] is called to initialize those resources.
 *  1. Consumer calls create() to obtain instances.
 *
 */
abstract class AbstractAnalysisFactory {
    /** The original args, before any processing  */
    lateinit var originalArgs: Map<String, String>

    /** the luceneVersion arg  */
    protected var luceneMatchVersion: Version? = null

    /** whether the luceneMatchVersion arg is explicitly specified in the serialized schema  */
    var isExplicitLuceneMatchVersion: Boolean = false

    /**
     * This default ctor is required to be implemented by all subclasses because of service loader
     * (SPI) specification, but it is never called by Lucene.
     *
     *
     * Subclass ctors should call: `throw defaultCtorException();`
     *
     * @throws UnsupportedOperationException if invoked
     * @see .defaultCtorException
     * @see .AbstractAnalysisFactory
     */
    protected constructor() {
        throw defaultCtorException()
    }

    /** Initialize this factory via a set of key-value pairs.  */
    protected constructor(args: MutableMap<String, String>) {
        originalArgs = args.toMap()
        val version = get(args, LUCENE_MATCH_VERSION_PARAM)
        if (version == null) {
            luceneMatchVersion = Version.LATEST
        } else {
            try {
                luceneMatchVersion = Version.parseLeniently(version)
            } catch (pe: ParseException) {
                throw IllegalArgumentException(pe)
            }
        }
        args.remove(CLASS_NAME) // consume the class arg
        args.remove(SPI_NAME) // consume the spi arg
    }

    /*fun getLuceneMatchVersion(): Version {
        return this.luceneMatchVersion!!
    }*/

    fun require(args: MutableMap<String, String>, name: String): String {
        val s = args.remove(name)
        requireNotNull(s) { "Configuration Error: missing parameter '$name'" }
        return s
    }

    fun require(
        args: MutableMap<String, String>,
        name: String,
        allowedValues: MutableCollection<String>,
        caseSensitive: Boolean = true
    ): String {
        val s = args.remove(name)
        requireNotNull(s != null) { "Configuration Error: missing parameter '$name'" }
        for (allowedValue in allowedValues) {
            if (caseSensitive) {
                if (s == allowedValue) {
                    return s
                }
            } else {
                if (s.equals(allowedValue, ignoreCase = true)) {
                    return s!!
                }
            }
        }
        throw IllegalArgumentException(
            "Configuration Error: '$name' value must be one of $allowedValues"
        )
    }

    fun get(args: MutableMap<String, String>, name: String): String? {
        return args.remove(name) // defaultVal = null
    }

    fun get(args: MutableMap<String, String>, name: String, defaultVal: String): String {
        val s = args.remove(name)
        return s ?: defaultVal
    }

    fun get(
        args: MutableMap<String, String>,
        name: String,
        allowedValues: MutableCollection<String>,
        defaultVal: String? = null,
        caseSensitive: Boolean = true
    ): String? {
        val s = args.remove(name)
        if (s == null) {
            return defaultVal
        } else {
            for (allowedValue in allowedValues) {
                if (caseSensitive) {
                    if (s == allowedValue) {
                        return s
                    }
                } else {
                    if (s.equals(allowedValue, ignoreCase = true)) {
                        return s
                    }
                }
            }
            throw IllegalArgumentException(
                "Configuration Error: '$name' value must be one of $allowedValues"
            )
        }
    }

    protected fun requireInt(args: MutableMap<String, String>, name: String): Int {
        return require(args, name).toInt()
    }

    protected fun getInt(args: MutableMap<String, String>, name: String, defaultVal: Int): Int {
        val s = args.remove(name)
        return s?.toInt() ?: defaultVal
    }

    protected fun requireBoolean(args: MutableMap<String, String>, name: String): Boolean {
        return require(args, name).toBoolean()
    }

    protected fun getBoolean(
        args: MutableMap<String, String>,
        name: String,
        defaultVal: Boolean
    ): Boolean {
        val s = args.remove(name)
        return s?.toBoolean() ?: defaultVal
    }

    protected fun requireFloat(args: MutableMap<String, String>, name: String): Float {
        return require(args, name).toFloat()
    }

    protected fun getFloat(
        args: MutableMap<String, String>,
        name: String,
        defaultVal: Float
    ): Float {
        val s = args.remove(name)
        return s?.toFloat() ?: defaultVal
    }

    fun requireChar(args: MutableMap<String, String>, name: String): Char {
        return require(args, name)[0]
    }

    fun getChar(args: MutableMap<String, String>, name: String, defaultValue: Char): Char {
        val s = args.remove(name)
        if (s == null) {
            return defaultValue
        } else {
            require(s.length == 1) { "$name should be a char. \"$s\" is invalid" }
            return s[0]
        }
    }

    /** Returns whitespace- and/or comma-separated set of values, or null if none are found  */
    fun getSet(args: MutableMap<String, String>, name: String): MutableSet<String>? {
        val s = args.remove(name)
        if (s == null) {
            return null
        } else {
            var set: MutableSet<String>? = null
            // following was the java lucene implementation:
            /*val matcher: java.util.regex.Matcher = ITEM_PATTERN.matcher(s)
            if (matcher.find()) {
                set = java.util.HashSet<String>()
                set!!.add(matcher.group(0))
                while (matcher.find()) {
                    set.add(matcher.group(0))
                }
            }*/
            val match = ITEM_PATTERN.find(s)
            if (match != null) {
                set = mutableSetOf()
                set.add(match.value)
                ITEM_PATTERN.findAll(s, startIndex = match.range.last + 1).forEach { m ->
                    set.add(m.value)
                }
            }

            return set
        }
    }

    /** Compiles a pattern for the value of the specified argument key `name`  */
    // following was java lucene implementation:
    /*protected fun getPattern(
        args: MutableMap<String, String>,
        name: String
    ): java.util.regex.Pattern {
        try {
            return java.util.regex.Pattern.compile(require(args, name))
        } catch (e: java.util.regex.PatternSyntaxException) {
            throw IllegalArgumentException(
                ("Configuration Error: '"
                        + name
                        + "' can not be parsed in "
                        + this.javaClass.getSimpleName()),
                e
            )
        }
    }*/
    protected fun getPattern(
        args: MutableMap<String, String>,
        name: String
    ): Regex {
        val pattern = require(args, name)
        try {
            return Regex(pattern)
        } catch (e: IllegalArgumentException) {
            val simpleName = this::class.simpleName ?: "AbstractAnalysisFactory"
            throw IllegalArgumentException(
                "Configuration Error: '$name' can not be parsed in $simpleName",
                e
            )
        }
    }

    /**
     * Returns as [CharArraySet] from wordFiles, which can be a comma-separated list of
     * filenames
     */
    @Throws(IOException::class)
    protected fun getWordSet(
        loader: ResourceLoader, wordFiles: String, ignoreCase: Boolean
    ): CharArraySet? {
        val files = splitFileNames(wordFiles)
        var words: CharArraySet? = null
        if (files.isNotEmpty()) {
            // default stopwords list has 35 or so words, but maybe don't make it that
            // big to start
            words = CharArraySet(files.size * 10, ignoreCase)
            for (file in files) {
                val wlist = getLines(loader, file.trim { it <= ' ' })
                words.addAll(StopFilter.makeStopSet(wlist, ignoreCase))
            }
        }
        return words
    }

    /** Returns the resource's lines (with content treated as UTF-8)  */
    @Throws(IOException::class)
    protected fun getLines(
        loader: ResourceLoader,
        resource: String
    ): MutableList<String> {
        return WordlistLoader.getLines(
            loader.openResource(resource),
            StandardCharsets.UTF_8
        )
    }

    /**
     * same as [.getWordSet], except the input is in snowball
     * format.
     */
    @Throws(IOException::class)
    protected fun getSnowballWordSet(
        loader: ResourceLoader, wordFiles: String, ignoreCase: Boolean
    ): CharArraySet? {
        val files = splitFileNames(wordFiles)
        var words: CharArraySet? = null
        if (files.isNotEmpty()) {
            // default stopwords list has 35 or so words, but maybe don't make it that
            // big to start
            words = CharArraySet(files.size * 10, ignoreCase)
            for (file in files) {
                val decoder: CharsetDecoder =
                    StandardCharsets.UTF_8
                        .newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                InputStreamReader(loader.openResource(file.trim { it <= ' ' }), decoder)
                    .use { reader ->
                        WordlistLoader.getSnowballWordSet(reader, words)
                    }
            }
        }
        return words
    }

    /**
     * Splits file names separated by comma character. File names can contain comma characters escaped
     * by backslash '\'
     *
     * @param fileNames the string containing file names
     * @return a list of file names with the escaping backslashed removed
     */
    protected fun splitFileNames(fileNames: String): MutableList<String> {
        return splitAt(',', fileNames)
    }

    /**
     * Splits a list separated by zero or more given separator characters. List items can contain
     * comma characters escaped by backslash '\'. Whitespace is NOT trimmed from the returned list
     * items.
     *
     * @param list the string containing the split list items
     * @return a list of items with the escaping backslashes removed
     */
    protected fun splitAt(separator: Char, list: String): MutableList<String> {
        if (list == null) return mutableListOf()

        val result: MutableList<String> = mutableListOf()
        for (item in list.split(("(<!\\\\)[$separator]").toRegex())
            .dropLastWhile { it.isEmpty() }.toTypedArray()) {
            result.add(item.replace(("\\\\(=[$separator])").toRegex(), ""))
        }

        return result
    }

    val classArg: String
        /**
         * @return the string used to specify the concrete class name in a serialized representation: the
         * class arg. If the concrete class name was not specified via a class arg, returns `getClass().getName()`.
         */
        get() {
            val className = originalArgs[CLASS_NAME]
            if (className != null) {
                return className
            }
            // Kotlin common replacement for javaClass.getName()
            return this::class.qualifiedName
                ?: this::class.simpleName
                ?: "org.gnit.lucenekmp.analysis.AbstractAnalysisFactory"
        }

    companion object {
        const val LUCENE_MATCH_VERSION_PARAM: String = "luceneMatchVersion"

        /**
         * Helper method to be called from mandatory default constructor of all subclasses to make [ ] happy.
         *
         *
         * Should be used in subclass ctors like: `throw defaultCtorException();`
         *
         * @see .AbstractAnalysisFactory
         */
        fun defaultCtorException(): RuntimeException {
            return UnsupportedOperationException(
                "Analysis factories cannot be instantiated without arguments. "
                        + "Use applicable factory methods of TokenizerFactory, CharFilterFactory, or TokenFilterFactory."
            )
        }

        /*private val ITEM_PATTERN: java.util.regex.Pattern =
            java.util.regex.Pattern.compile("[^,\\s]+")*/
        private val ITEM_PATTERN = Regex("""[^,\s]+""")

        private const val CLASS_NAME = "class"

        private const val SPI_NAME = "name"
    }
}
