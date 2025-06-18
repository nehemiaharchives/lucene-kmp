package org.gnit.lucenekmp.jdkport

import okio.IOException
import kotlin.experimental.or

/**
 * The `StreamTokenizer` class takes an input stream and
 * parses it into "tokens", allowing the tokens to be
 * read one at a time. The parsing process is controlled by a table
 * and a number of flags that can be set to various states. The
 * stream tokenizer can recognize identifiers, numbers, quoted
 * strings, and various comment styles.
 *
 *
 * Each byte read from the input stream is regarded as a character
 * in the range `'\u005Cu0000'` through `'\u005Cu00FF'`.
 * The character value is used to look up five possible attributes of
 * the character: *white space*, *alphabetic*,
 * *numeric*, *string quote*, and *comment character*.
 * Each character can have zero or more of these attributes.
 *
 *
 * In addition, an instance has four flags. These flags indicate:
 *
 *  * Whether line terminators are to be returned as tokens or treated
 * as white space that merely separates tokens.
 *  * Whether C-style comments are to be recognized and skipped.
 *  * Whether C++-style comments are to be recognized and skipped.
 *  * Whether the characters of identifiers are converted to lowercase.
 *
 *
 *
 * A typical application first constructs an instance of this class,
 * sets up the syntax tables, and then repeatedly loops calling the
 * `nextToken` method in each iteration of the loop until
 * it returns the value `TT_EOF`.
 *
 * @author  James Gosling
 * @see StreamTokenizer.nextToken
 * @see StreamTokenizer.TT_EOF
 *
 * @since   1.0
 */
class StreamTokenizer private constructor() {
    /* Only one of these will be non-null */
    private var reader: Reader? = null
    private var input: InputStream? = null

    private var buf = CharArray(20)

    /**
     * The next character to be considered by the nextToken method.  May also
     * be NEED_CHAR to indicate that a new character should be read, or SKIP_LF
     * to indicate that a new character should be read and, if it is a '\n'
     * character, it should be discarded and a second new character should be
     * read.
     */
    private var peekc = NEED_CHAR

    private var pushedBack = false
    private var forceLower = false

    /** The line number of the last token read  */
    private var LINENO = 1

    private var eolIsSignificantP = false
    private var slashSlashCommentsP = false
    private var slashStarCommentsP = false

    private val ctype = ByteArray(256)

    /**
     * After a call to the `nextToken` method, this field
     * contains the type of the token just read. For a single character
     * token, its value is the single character, converted to an integer.
     * For a quoted string token, its value is the quote character.
     * Otherwise, its value is one of the following:
     *
     *  * `TT_WORD` indicates that the token is a word.
     *  * `TT_NUMBER` indicates that the token is a number.
     *  * `TT_EOL` indicates that the end of line has been read.
     * The field can only have this value if the
     * `eolIsSignificant` method has been called with the
     * argument `true`.
     *  * `TT_EOF` indicates that the end of the input stream
     * has been reached.
     *
     *
     *
     * The initial value of this field is {@value TT_NOTHING}.
     *
     * @see StreamTokenizer.eolIsSignificant
     * @see StreamTokenizer.nextToken
     * @see StreamTokenizer.quoteChar
     * @see StreamTokenizer.TT_EOF
     *
     * @see StreamTokenizer.TT_EOL
     *
     * @see StreamTokenizer.TT_NUMBER
     *
     * @see StreamTokenizer.TT_WORD
     */
    var ttype: Int = TT_NOTHING

    /**
     * If the current token is a word token, this field contains a
     * string giving the characters of the word token. When the current
     * token is a quoted string token, this field contains the body of
     * the string.
     *
     *
     * The current token is a word when the value of the
     * `ttype` field is `TT_WORD`. The current token is
     * a quoted string token when the value of the `ttype` field is
     * a quote character.
     *
     *
     * The initial value of this field is null.
     *
     * @see StreamTokenizer.quoteChar
     * @see StreamTokenizer.TT_WORD
     *
     * @see StreamTokenizer.ttype
     */
    var sval: String? = null

    /**
     * If the current token is a number, this field contains the value
     * of that number. The current token is a number when the value of
     * the `ttype` field is `TT_NUMBER`.
     *
     *
     * The initial value of this field is 0.0.
     *
     * @see StreamTokenizer.TT_NUMBER
     *
     * @see StreamTokenizer.ttype
     */
    var nval: Double = 0.0

    /** Private constructor that initializes everything except the streams.  */
    init {
        wordChars('a'.code, 'z'.code)
        wordChars('A'.code, 'Z'.code)
        wordChars(128 + 32, 255)
        whitespaceChars(0, ' '.code)
        commentChar('/'.code)
        quoteChar('"'.code)
        quoteChar('\''.code)
        parseNumbers()
    }

    /**
     * Creates a stream tokenizer that parses the specified input
     * stream. The stream tokenizer is initialized to the following
     * default state:
     *
     *  * All byte values `'A'` through `'Z'`,
     * `'a'` through `'z'`, and
     * `'\u005Cu00A0'` through `'\u005Cu00FF'` are
     * considered to be alphabetic.
     *  * All byte values `'\u005Cu0000'` through
     * `'\u005Cu0020'` are considered to be white space.
     *  * `'/'` is a comment character.
     *  * Single quote `'\u005C''` and double quote `'"'`
     * are string quote characters.
     *  * Numbers are parsed.
     *  * Ends of lines are treated as white space, not as separate tokens.
     *  * C-style and C++-style comments are not recognized.
     *
     *
     * @param      is        an input stream.
     * @see BufferedReader
     *
     * @see InputStreamReader
     *
     * @see StreamTokenizer.StreamTokenizer
     */
    @Deprecated(
        """As of JDK version 1.1, the preferred way to tokenize an
      input stream is to convert it into a character stream, for example:
      {@snippet lang=java :
     *     Reader r = new BufferedReader(new InputStreamReader(is));
     *     StreamTokenizer st = new StreamTokenizer(r);
     * }
     
      """
    )
    constructor(`is`: InputStream) : this() {
        if (`is` == null) {
            throw NullPointerException()
        }
        input = `is`
    }

    /**
     * Create a tokenizer that parses the given character stream.
     *
     * @param r  a Reader object providing the input stream.
     * @since   1.1
     */
    constructor(r: Reader) : this() {
        if (r == null) {
            throw NullPointerException()
        }
        reader = r
    }

    /**
     * Resets this tokenizer's syntax table so that all characters are
     * "ordinary." See the `ordinaryChar` method
     * for more information on a character being ordinary.
     *
     * @see StreamTokenizer.ordinaryChar
     */
    fun resetSyntax() {
        var i = ctype.size
        while (--i >= 0) {
            ctype[i] = 0
        }
    }

    /**
     * Specifies that all characters *c* in the range
     * `low <= c <= high`
     * are word constituents. A word token consists of a word constituent
     * followed by zero or more word constituents or number constituents.
     *
     * @param   low   the low end of the range.
     * @param   hi    the high end of the range.
     */
    fun wordChars(low: Int, hi: Int) {
        var l = low
        var h = hi
        if (l < 0) l = 0
        if (h >= ctype.size) h = ctype.size - 1
        while (l <= h) {
            ctype[l] = ctype[l] or CT_ALPHA
            l++
        }
    }

    /**
     * Specifies that all characters *c* in the range
     * `low <= c <= high`
     * are white space characters. White space characters serve only to
     * separate tokens in the input stream.
     *
     *
     * Any other attribute settings for the characters in the specified
     * range are cleared.
     *
     * @param   low   the low end of the range.
     * @param   hi    the high end of the range.
     */
    fun whitespaceChars(low: Int, hi: Int) {
        var low = low
        var hi = hi
        if (low < 0) low = 0
        if (hi >= ctype.size) hi = ctype.size - 1
        while (low <= hi) ctype[low++] = CT_WHITESPACE
    }

    /**
     * Specifies that all characters *c* in the range
     * `low <= c <= high`
     * are "ordinary" in this tokenizer. See the
     * `ordinaryChar` method for more information on a
     * character being ordinary.
     *
     * @param   low   the low end of the range.
     * @param   hi    the high end of the range.
     * @see StreamTokenizer.ordinaryChar
     */
    fun ordinaryChars(low: Int, hi: Int) {
        var low = low
        var hi = hi
        if (low < 0) low = 0
        if (hi >= ctype.size) hi = ctype.size - 1
        while (low <= hi) ctype[low++] = 0
    }

    /**
     * Specifies that the character argument is "ordinary"
     * in this tokenizer. It removes any special significance the
     * character has as a comment character, word component, string
     * delimiter, white space, or number character. When such a character
     * is encountered by the parser, the parser treats it as a
     * single-character token and sets `ttype` field to the
     * character value.
     *
     *
     * Making a line terminator character "ordinary" may interfere
     * with the ability of a `StreamTokenizer` to count
     * lines. The `lineno` method may no longer reflect
     * the presence of such terminator characters in its line count.
     *
     * @param   ch   the character.
     * @see StreamTokenizer.ttype
     */
    fun ordinaryChar(ch: Int) {
        if (ch >= 0 && ch < ctype.size) ctype[ch] = 0
    }

    /**
     * Specifies that the character argument starts a single-line
     * comment. All characters from the comment character to the end of
     * the line are ignored by this stream tokenizer.
     *
     *
     * Any other attribute settings for the specified character are cleared.
     *
     * @param   ch   the character.
     */
    fun commentChar(ch: Int) {
        if (ch >= 0 && ch < ctype.size) ctype[ch] = CT_COMMENT
    }

    /**
     * Specifies that matching pairs of this character delimit string
     * constants in this tokenizer.
     *
     *
     * When the `nextToken` method encounters a string
     * constant, the `ttype` field is set to the string
     * delimiter and the `sval` field is set to the body of
     * the string.
     *
     *
     * If a string quote character is encountered, then a string is
     * recognized, consisting of all characters after (but not including)
     * the string quote character, up to (but not including) the next
     * occurrence of that same string quote character, or a line
     * terminator, or end of file. The usual escape sequences such as
     * `"\u005Cn"` and `"\u005Ct"` are recognized and
     * converted to single characters as the string is parsed.
     *
     *
     * Any other attribute settings for the specified character are cleared.
     *
     * @param   ch   the character.
     * @see StreamTokenizer.nextToken
     * @see StreamTokenizer.sval
     *
     * @see StreamTokenizer.ttype
     */
    fun quoteChar(ch: Int) {
        if (ch >= 0 && ch < ctype.size) ctype[ch] = CT_QUOTE
    }

    /**
     * Specifies that numbers should be parsed by this tokenizer. The
     * syntax table of this tokenizer is modified so that each of the twelve
     * characters:
     * <blockquote><pre>
     * 0 1 2 3 4 5 6 7 8 9 . -
    </pre></blockquote> *
     *
     *
     * has the "numeric" attribute.
     *
     *
     * When the parser encounters a word token that has the format of a
     * double precision floating-point number, it treats the token as a
     * number rather than a word, by setting the `ttype`
     * field to the value `TT_NUMBER` and putting the numeric
     * value of the token into the `nval` field.
     *
     * @see StreamTokenizer.nval
     *
     * @see StreamTokenizer.TT_NUMBER
     *
     * @see StreamTokenizer.ttype
     */
    fun parseNumbers() {
        var i = '0'.code
        while (i <= '9'.code) {
            ctype[i] = ctype[i] or CT_DIGIT
            i++
        }
        ctype['.'.code] = ctype['.'.code] or CT_DIGIT
        ctype['-'.code] = ctype['-'.code] or CT_DIGIT
    }

    /**
     * Determines whether or not ends of line are treated as tokens.
     * If the flag argument is true, this tokenizer treats end of lines
     * as tokens; the `nextToken` method returns
     * `TT_EOL` and also sets the `ttype` field to
     * this value when an end of line is read.
     *
     *
     * A line is a sequence of characters ending with either a
     * carriage-return character (`'\u005Cr'`) or a newline
     * character (`'\u005Cn'`). In addition, a carriage-return
     * character followed immediately by a newline character is treated
     * as a single end-of-line token.
     *
     *
     * If the `flag` is false, end-of-line characters are
     * treated as white space and serve only to separate tokens.
     *
     * @param   flag   `true` indicates that end-of-line characters
     * are separate tokens; `false` indicates that
     * end-of-line characters are white space.
     * @see StreamTokenizer.nextToken
     * @see StreamTokenizer.ttype
     *
     * @see StreamTokenizer.TT_EOL
     */
    fun eolIsSignificant(flag: Boolean) {
        eolIsSignificantP = flag
    }

    /**
     * Determines whether or not the tokenizer recognizes C-style comments.
     * If the flag argument is `true`, this stream tokenizer
     * recognizes C-style comments. All text between successive
     * occurrences of `/ *` and `*&#47;` are discarded.
     *
     *
     * If the flag argument is `false`, then C-style comments
     * are not treated specially.
     *
     * @param   flag   `true` indicates to recognize and ignore
     * C-style comments.
     */
    fun slashStarComments(flag: Boolean) {
        slashStarCommentsP = flag
    }

    /**
     * Determines whether or not the tokenizer recognizes C++-style comments.
     * If the flag argument is `true`, this stream tokenizer
     * recognizes C++-style comments. Any occurrence of two consecutive
     * slash characters (`'/'`) is treated as the beginning of
     * a comment that extends to the end of the line.
     *
     *
     * If the flag argument is `false`, then C++-style
     * comments are not treated specially.
     *
     * @param   flag   `true` indicates to recognize and ignore
     * C++-style comments.
     */
    fun slashSlashComments(flag: Boolean) {
        slashSlashCommentsP = flag
    }

    /**
     * Determines whether or not word token are automatically lowercased.
     * If the flag argument is `true`, then the value in the
     * `sval` field is lowercased whenever a word token is
     * returned (the `ttype` field has the
     * value `TT_WORD`) by the `nextToken` method
     * of this tokenizer.
     *
     *
     * If the flag argument is `false`, then the
     * `sval` field is not modified.
     *
     * @param   fl   `true` indicates that all word tokens should
     * be lowercased.
     * @see StreamTokenizer.nextToken
     * @see StreamTokenizer.ttype
     *
     * @see StreamTokenizer.TT_WORD
     */
    fun lowerCaseMode(fl: Boolean) {
        forceLower = fl
    }

    /** Read the next character  */
    @Throws(IOException::class)
    private fun read(): Int {
        return if (reader != null) reader!!.read()
        else if (input != null) input!!.read()
        else throw IllegalStateException()
    }

    /**
     * Parses the next token from the input stream of this tokenizer.
     * The type of the next token is returned in the `ttype`
     * field. Additional information about the token may be in the
     * `nval` field or the `sval` field of this
     * tokenizer.
     *
     *
     * Typical clients of this
     * class first set up the syntax tables and then sit in a loop
     * calling nextToken to parse successive tokens until TT_EOF
     * is returned.
     *
     * @return     the value of the `ttype` field.
     * @throws     IOException  if an I/O error occurs.
     * @see StreamTokenizer.nval
     *
     * @see StreamTokenizer.sval
     *
     * @see StreamTokenizer.ttype
     */
    @Throws(IOException::class)
    fun nextToken(): Int {
        if (pushedBack) {
            pushedBack = false
            return ttype
        }
        val ct: ByteArray? = ctype
        sval = null

        var c = peekc
        if (c < 0) c = NEED_CHAR
        if (c == SKIP_LF) {
            c = read()
            if (c < 0) return TT_EOF.also { ttype = it }
            if (c == '\n'.code) c = NEED_CHAR
        }
        if (c == NEED_CHAR) {
            c = read()
            if (c < 0) return TT_EOF.also { ttype = it }
        }
        ttype = c /* Just to be safe */

        /* Set peekc so that the next invocation of nextToken will read
         * another character unless peekc is reset in this invocation
         */
        peekc = NEED_CHAR

        var ctype = (if (c < 256) ct!![c] else CT_ALPHA).toInt()
        while ((ctype and CT_WHITESPACE.toInt()) != 0) {
            if (c == '\r'.code) {
                LINENO++
                if (eolIsSignificantP) {
                    peekc = SKIP_LF
                    return TT_EOL.also { ttype = it }
                }
                c = read()
                if (c == '\n'.code) c = read()
            } else {
                if (c == '\n'.code) {
                    LINENO++
                    if (eolIsSignificantP) {
                        return TT_EOL.also { ttype = it }
                    }
                }
                c = read()
            }
            if (c < 0) return TT_EOF.also { ttype = it }
            ctype = (if (c < 256) ct!![c] else CT_ALPHA).toInt()
        }

        if ((ctype and CT_DIGIT.toInt()) != 0) {
            var neg = false
            if (c == '-'.code) {
                c = read()
                if (c != '.'.code && (c < '0'.code || c > '9'.code)) {
                    peekc = c
                    return '-'.also { ttype = it.code }.code // this was Char in java, but got error so added .code to convert to Int
                }
                neg = true
            }
            var v = 0.0
            var decexp = 0
            var seendot = 0
            while (true) {
                if (c == '.'.code && seendot == 0) seendot = 1
                else if ('0'.code <= c && c <= '9'.code) {
                    v = v * 10 + (c - '0'.code)
                    decexp += seendot
                } else break
                c = read()
            }
            peekc = c
            if (decexp != 0) {
                var denom = 10.0
                decexp--
                while (decexp > 0) {
                    denom *= 10.0
                    decexp--
                }
                /* Do one division of a likely-to-be-more-accurate number */
                v = v / denom
            }
            nval = if (neg) -v else v
            return TT_NUMBER.also { ttype = it }
        }

        if ((ctype and CT_ALPHA.toInt()) != 0) {
            var i = 0
            do {
                if (i >= buf.size) {
                    buf = buf.copyOf(buf.size * 2)
                }
                buf[i++] = c.toChar()
                c = read()
                ctype = (if (c < 0) CT_WHITESPACE else if (c < 256) ct!![c] else CT_ALPHA).toInt()
            } while ((ctype and (CT_ALPHA.toInt() or CT_DIGIT.toInt())) != 0)
            peekc = c
            sval = String.fromCharArray(buf, 0, i)
            if (forceLower) sval = sval!!.lowercase(/*java.util.Locale.getDefault()*/)
            return TT_WORD.also { ttype = it }
        }

        if ((ctype and CT_QUOTE.toInt()) != 0) {
            ttype = c
            var i = 0
            /* Invariants (because \Octal needs a lookahead):
             *   (i)  c contains char value
             *   (ii) d contains the lookahead
             */
            var d = read()
            while (d >= 0 && d != ttype && d != '\n'.code && d != '\r'.code) {
                if (d == '\\'.code) {
                    c = read()
                    val first = c /* To allow \377, but not \477 */
                    if (c >= '0'.code && c <= '7'.code) {
                        c = c - '0'.code
                        var c2 = read()
                        if ('0'.code <= c2 && c2 <= '7'.code) {
                            c = (c shl 3) + (c2 - '0'.code)
                            c2 = read()
                            if ('0'.code <= c2 && c2 <= '7'.code && first <= '3'.code) {
                                c = (c shl 3) + (c2 - '0'.code)
                                d = read()
                            } else d = c2
                        } else d = c2
                    } else {
                        c = when (c.toChar()) {
                            'a' -> 0x7
                            'b' -> '\b'.code
                            'f' -> 0xC
                            'n' -> '\n'.code
                            'r' -> '\r'.code
                            't' -> '\t'.code
                            'v' -> 0xB
                            else -> c
                        }
                        d = read()
                    }
                } else {
                    c = d
                    d = read()
                }
                if (i >= buf.size) {
                    buf = buf.copyOf(buf.size * 2)
                }
                buf[i++] = c.toChar()
            }

            /* If we broke out of the loop because we found a matching quote
             * character then arrange to read a new character next time
             * around; otherwise, save the character.
             */
            peekc = if (d == ttype) NEED_CHAR else d

            sval = String.fromCharArray(buf, 0, i)
            return ttype
        }

        if (c == '/'.code && (slashSlashCommentsP || slashStarCommentsP)) {
            c = read()
            if (c == '*'.code && slashStarCommentsP) {
                var prevc = 0
                while ((read().also { c = it }) != '/'.code || prevc != '*'.code) {
                    if (c == '\r'.code) {
                        LINENO++
                        c = read()
                        if (c == '\n'.code) {
                            c = read()
                        }
                    } else {
                        if (c == '\n'.code) {
                            LINENO++
                            c = read()
                        }
                    }
                    if (c < 0) return TT_EOF.also { ttype = it }
                    prevc = c
                }
                return nextToken()
            } else if (c == '/'.code && slashSlashCommentsP) {
                while ((read().also { c = it }) != '\n'.code && c != '\r'.code && c >= 0);
                peekc = c
                return nextToken()
            } else {
                /* Now see if it is still a single line comment */
                if ((ct!!['/'.code].toInt() and CT_COMMENT.toInt()) != 0) {
                    while ((read().also { c = it }) != '\n'.code && c != '\r'.code && c >= 0);
                    peekc = c
                    return nextToken()
                } else {
                    peekc = c
                    return '/'.also { ttype = it.code }.code
                }
            }
        }

        if ((ctype and CT_COMMENT.toInt()) != 0) {
            while ((read().also { c = it }) != '\n'.code && c != '\r'.code && c >= 0);
            peekc = c
            return nextToken()
        }

        return c.also { ttype = it }
    }

    /**
     * Causes the next call to the `nextToken` method of this
     * tokenizer to return the current value in the `ttype`
     * field, and not to modify the value in the `nval` or
     * `sval` field.
     *
     * @see StreamTokenizer.nextToken
     * @see StreamTokenizer.nval
     *
     * @see StreamTokenizer.sval
     *
     * @see StreamTokenizer.ttype
     */
    fun pushBack() {
        if (ttype != TT_NOTHING)  /* No-op if nextToken() not called */
            pushedBack = true
    }

    /**
     * Returns the current line number.
     *
     * @return  the current line number of this stream tokenizer.
     */
    fun lineno(): Int {
        return LINENO
    }

    /**
     * Returns the string representation of the current stream token and
     * the line number it occurs on.
     *
     *
     * The precise string returned is unspecified, although the following
     * example can be considered typical:
     *
     * <blockquote><pre>
     * Token['a'], line 10
    </pre></blockquote> *
     *
     * @return  a string representation of the token
     * @see StreamTokenizer.nval
     *
     * @see StreamTokenizer.sval
     *
     * @see StreamTokenizer.ttype
     */
    override fun toString(): String {
        val ret: String = when (ttype) {
            TT_EOF -> "EOF"
            TT_EOL -> "EOL"
            TT_WORD -> sval
            TT_NUMBER -> "n=$nval"
            TT_NOTHING -> "NOTHING"
            else -> {

                /*
                 * ttype is the first character of either a quoted string or
                 * is an ordinary character. ttype can definitely not be less
                 * than 0, since those are reserved values used in the previous
                 * case statements
                 */
                if (ttype < 256 && ((ctype[ttype].toInt() and CT_QUOTE.toInt()) != 0)) {
                    sval
                }
                val s = CharArray(3)
                s[2] = '\''
                s[0] = s[2]
                s[1] = ttype.toChar()
                s.concatToString()
            }
        }!!
        return "Token[$ret], line $LINENO"
    }

    companion object {
        private const val NEED_CHAR = Int.Companion.MAX_VALUE
        private const val SKIP_LF = Int.Companion.MAX_VALUE - 1

        private const val CT_WHITESPACE: Byte = 1
        private const val CT_DIGIT: Byte = 2
        private const val CT_ALPHA: Byte = 4
        private const val CT_QUOTE: Byte = 8
        private const val CT_COMMENT: Byte = 16

        /**
         * A constant indicating that the end of the stream has been read.
         */
        const val TT_EOF: Int = -1

        /**
         * A constant indicating that the end of the line has been read.
         */
        val TT_EOL: Int = '\n'.code

        /**
         * A constant indicating that a number token has been read.
         */
        const val TT_NUMBER: Int = -2

        /**
         * A constant indicating that a word token has been read.
         */
        const val TT_WORD: Int = -3

        /* A constant indicating that no token has been read, used for
     * initializing ttype.  FIXME This could be made public and
     * made available as the part of the API in a future release.
     */
        private const val TT_NOTHING = -4
    }
}
