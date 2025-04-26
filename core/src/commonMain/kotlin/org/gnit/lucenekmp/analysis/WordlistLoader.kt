package org.gnit.lucenekmp.analysis

import org.gnit.lucenekmp.util.IOUtils
import kotlinx.io.IOException
import org.gnit.lucenekmp.jdkport.BufferedReader
import org.gnit.lucenekmp.jdkport.Charset
import org.gnit.lucenekmp.jdkport.InputStream
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.jdkport.StandardCharsets

/**
 * Loader for text files that represent a list of stopwords.
 *
 * @see IOUtils to obtain Reader instances
 *
 * @lucene.internal
 */
object WordlistLoader {
    private const val INITIAL_CAPACITY = 16

    /**
     * Reads lines from a Reader and adds every non-blank line as an entry to a CharArraySet (omitting
     * leading and trailing whitespace). Every line of the Reader should contain only one word. The
     * words need to be in lowercase if you make use of an Analyzer which uses LowerCaseFilter (like
     * StandardAnalyzer).
     *
     * @param reader Reader containing the wordlist
     * @param result the [CharArraySet] to fill with the readers words
     * @return the given [CharArraySet] with the reader's words
     */
    @Throws(IOException::class)
    fun getWordSet(reader: Reader, result: CharArraySet): CharArraySet {
        getBufferedReader(reader).use { br ->
            var word: String? = null
            while ((br.readLine().also { word = it }) != null) {
                word = word!!.trim { it <= ' ' }
                // skip blank lines
                if (word.isEmpty()) continue
                result.add(word)
            }
        }
        return result
    }

    /**
     * Reads lines from a Reader and adds every line as an entry to a CharArraySet (omitting leading
     * and trailing whitespace). Every line of the Reader should contain only one word. The words need
     * to be in lowercase if you make use of an Analyzer which uses LowerCaseFilter (like
     * StandardAnalyzer).
     *
     * @param reader Reader containing the wordlist
     * @return An unmodifiable [CharArraySet] with the reader's words
     */
    @Throws(IOException::class)
    fun getWordSet(reader: Reader): CharArraySet {
        return CharArraySet.unmodifiableSet(
            getWordSet(reader, CharArraySet(INITIAL_CAPACITY, false))
        )
    }

    /**
     * Reads lines from an InputStream with UTF-8 charset and adds every line as an entry to a
     * CharArraySet (omitting leading and trailing whitespace). Every line of the Reader should
     * contain only one word. The words need to be in lowercase if you make use of an Analyzer which
     * uses LowerCaseFilter (like StandardAnalyzer).
     *
     * @param stream InputStream containing the wordlist
     * @return An unmodifiable [CharArraySet] with the reader's words
     */
    @Throws(IOException::class)
    fun getWordSet(stream: InputStream): CharArraySet {
        return getWordSet(stream, StandardCharsets.UTF_8)
    }

    /**
     * Reads lines from an InputStream with the given charset and adds every line as an entry to a
     * CharArraySet (omitting leading and trailing whitespace). Every line of the Reader should
     * contain only one word. The words need to be in lowercase if you make use of an Analyzer which
     * uses LowerCaseFilter (like StandardAnalyzer).
     *
     * @param stream InputStream containing the wordlist
     * @param charset Charset of the wordlist
     * @return An unmodifiable [CharArraySet] with the reader's words
     */
    @Throws(IOException::class)
    fun getWordSet(stream: InputStream, charset:
    Charset): CharArraySet {
        return getWordSet(IOUtils.getDecodingReader(stream, charset))
    }

    /**
     * Reads lines from a Reader and adds every non-blank non-comment line as an entry to a
     * CharArraySet (omitting leading and trailing whitespace). Every line of the Reader should
     * contain only one word. The words need to be in lowercase if you make use of an Analyzer which
     * uses LowerCaseFilter (like StandardAnalyzer).
     *
     * @param reader Reader containing the wordlist
     * @param comment The string representing a comment.
     * @param result the [CharArraySet] to fill with the readers words
     * @return the given [CharArraySet] with the reader's words
     */
    @Throws(IOException::class)
    fun getWordSet(reader: Reader, comment: String, result: CharArraySet): CharArraySet {
        getBufferedReader(reader).use { br ->
            var word: String? = null
            while ((br.readLine().also { word = it }) != null) {
                if (!word!!.startsWith(comment)) {
                    word = word.trim { it <= ' ' }
                    // skip blank lines
                    if (word.isEmpty()) continue
                    result.add(word)
                }
            }
        }
        return result
    }

    /**
     * Reads lines from a Reader and adds every non-comment line as an entry to a CharArraySet
     * (omitting leading and trailing whitespace). Every line of the Reader should contain only one
     * word. The words need to be in lowercase if you make use of an Analyzer which uses
     * LowerCaseFilter (like StandardAnalyzer).
     *
     * @param reader Reader containing the wordlist
     * @param comment The string representing a comment.
     * @return An unmodifiable CharArraySet with the reader's words
     */
    @Throws(IOException::class)
    fun getWordSet(reader: Reader, comment: String): CharArraySet {
        return CharArraySet.unmodifiableSet(
            getWordSet(reader, comment, CharArraySet(INITIAL_CAPACITY, false))
        )
    }

    /**
     * Reads lines from an InputStream with UTF-8 charset and adds every non-comment line as an entry
     * to a CharArraySet (omitting leading and trailing whitespace). Every line of the Reader should
     * contain only one word. The words need to be in lowercase if you make use of an Analyzer which
     * uses LowerCaseFilter (like StandardAnalyzer).
     *
     * @param stream InputStream in UTF-8 encoding containing the wordlist
     * @param comment The string representing a comment.
     * @return An unmodifiable CharArraySet with the reader's words
     */
    @Throws(IOException::class)
    fun getWordSet(stream: InputStream, comment: String): CharArraySet {
        return getWordSet(stream, StandardCharsets.UTF_8, comment)
    }

    /**
     * Reads lines from an InputStream with the given charset and adds every non-comment line as an
     * entry to a CharArraySet (omitting leading and trailing whitespace). Every line of the Reader
     * should contain only one word. The words need to be in lowercase if you make use of an Analyzer
     * which uses LowerCaseFilter (like StandardAnalyzer).
     *
     * @param stream InputStream containing the wordlist
     * @param charset Charset of the wordlist
     * @param comment The string representing a comment.
     * @return An unmodifiable CharArraySet with the reader's words
     */
    @Throws(IOException::class)
    fun getWordSet(stream: InputStream, charset:
    Charset, comment: String): CharArraySet {
        return getWordSet(IOUtils.getDecodingReader(stream, charset), comment)
    }

    /**
     * Reads stopwords from a stopword list in Snowball format.
     *
     *
     * The snowball format is the following:
     *
     *
     *  * Lines may contain multiple words separated by whitespace.
     *  * The comment character is the vertical line (&#124;).
     *  * Lines may contain trailing comments.
     *
     *
     * @param reader Reader containing a Snowball stopword list
     * @param result the [CharArraySet] to fill with the readers words
     * @return the given [CharArraySet] with the reader's words
     */
    @Throws(IOException::class)
    fun getSnowballWordSet(reader: Reader, result: CharArraySet): CharArraySet {
        getBufferedReader(reader).use { br ->
            var line: String? = null
            while ((br.readLine().also { line = it }) != null) {
                val comment = line!!.indexOf('|')
                if (comment >= 0) line = line.substring(0, comment)
                val words: Array<String> = line.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                for (i in words.indices) {
                    if (words[i].isNotEmpty()) result.add(words[i])
                }
            }
        }
        return result
    }

    /**
     * Reads stopwords from a stopword list in Snowball format.
     *
     *
     * The snowball format is the following:
     *
     *
     *  * Lines may contain multiple words separated by whitespace.
     *  * The comment character is the vertical line (&#124;).
     *  * Lines may contain trailing comments.
     *
     *
     * @param reader Reader containing a Snowball stopword list
     * @return An unmodifiable [CharArraySet] with the reader's words
     */
    @Throws(IOException::class)
    fun getSnowballWordSet(reader: Reader): CharArraySet {
        return CharArraySet.unmodifiableSet(
            getSnowballWordSet(reader, CharArraySet(INITIAL_CAPACITY, false))
        )
    }

    /**
     * Reads stopwords from a stopword list in Snowball format.
     *
     *
     * The snowball format is the following:
     *
     *
     *  * Lines may contain multiple words separated by whitespace.
     *  * The comment character is the vertical line (&#124;).
     *  * Lines may contain trailing comments.
     *
     *
     * @param stream InputStream in UTF-8 encoding containing a Snowball stopword list
     * @return An unmodifiable [CharArraySet] with the reader's words
     */
    @Throws(IOException::class)
    fun getSnowballWordSet(stream: InputStream): CharArraySet {
        return getSnowballWordSet(stream, StandardCharsets.UTF_8)
    }

    /**
     * Reads stopwords from a stopword list in Snowball format.
     *
     *
     * The snowball format is the following:
     *
     *
     *  * Lines may contain multiple words separated by whitespace.
     *  * The comment character is the vertical line (&#124;).
     *  * Lines may contain trailing comments.
     *
     *
     * @param stream InputStream containing a Snowball stopword list
     * @param charset Charset of the stopword list
     * @return An unmodifiable [CharArraySet] with the reader's words
     */
    @Throws(IOException::class)
    fun getSnowballWordSet(stream: InputStream, charset:
    Charset): CharArraySet {
        return getSnowballWordSet(IOUtils.getDecodingReader(stream, charset))
    }

    /**
     * Reads a stem dictionary. Each line contains:
     *
     * <pre>word**\t**stem</pre>
     *
     * (i.e. two tab separated words)
     *
     * @return stem dictionary that overrules the stemming algorithm
     * @throws IOException If there is a low-level I/O error.
     */
    @Throws(IOException::class)
    fun getStemDict(reader: Reader, result: CharArrayMap<String>): CharArrayMap<String> {
        getBufferedReader(reader).use { br ->
            var line: String?
            while ((br.readLine().also { line = it }) != null) {
                val wordstem: Array<String> = line!!.split("\t".toRegex(), limit = 2).toTypedArray()
                result.put(wordstem[0], wordstem[1])
            }
        }
        return result
    }

    /**
     * Accesses a resource by name and returns the (non comment) lines containing data using the given
     * character encoding.
     *
     *
     * A comment line is any line that starts with the character "#"
     *
     * @return a list of non-blank non-comment lines with whitespace trimmed
     * @throws IOException If there is a low-level I/O error.
     */
    @Throws(IOException::class)
    fun getLines(stream: InputStream, charset:
    Charset): MutableList<String> {
        val lines: ArrayList<String>
        getBufferedReader(IOUtils.getDecodingReader(stream, charset)).use { input ->
            lines = ArrayList()
            var word: String? = null
            while ((input.readLine().also { word = it }) != null) {
                // skip initial bom marker
                if (lines.isEmpty() && word!!.isNotEmpty() && word[0] == '\uFEFF') word = word.substring(1)
                // skip comments
                if (word!!.startsWith("#")) {
                    continue
                }
                word = word.trim { it <= ' ' }
                // skip blank lines
                if (word.isEmpty()) {
                    continue
                }
                lines.add(word)
            }
            return lines
        }
    }

    private fun getBufferedReader(reader: Reader): BufferedReader {
        return reader as? BufferedReader ?: BufferedReader(reader)
    }
}
