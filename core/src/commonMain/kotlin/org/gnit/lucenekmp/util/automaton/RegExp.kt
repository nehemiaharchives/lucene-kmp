package org.gnit.lucenekmp.util.automaton

import okio.IOException
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.jdkport.appendCodePoint
import org.gnit.lucenekmp.jdkport.codePointAt
import org.gnit.lucenekmp.jdkport.codePointSequence
import kotlin.jvm.JvmOverloads


/**
 * Regular Expression extension to `Automaton`.
 *
 *
 * Regular expressions are built from the following abstract syntax:
 *
 * <table style="border: 0">
 * <caption>description of regular expression grammar</caption>
 * <tr>
 * <td>*regexp*</td>
 * <td>::=</td>
 * <td>*unionexp*</td>
 * <td></td>
 * <td></td>
</tr> *
 * <tr>
 * <td></td>
 * <td>|</td>
 * <td></td>
 * <td></td>
 * <td></td>
</tr> *
 *
 * <tr>
 * <td>*unionexp*</td>
 * <td>::=</td>
 * <td>*interexp*&nbsp;`**|**`&nbsp;*unionexp*</td>
 * <td>(union)</td>
 * <td></td>
</tr> *
 * <tr>
 * <td></td>
 * <td>|</td>
 * <td>*interexp*</td>
 * <td></td>
 * <td></td>
</tr> *
 *
 * <tr>
 * <td>*interexp*</td>
 * <td>::=</td>
 * <td>*concatexp*&nbsp;`**&amp;**`&nbsp;*interexp*</td>
 * <td>(intersection)</td>
 * <td><small>[OPTIONAL]</small></td>
</tr> *
 * <tr>
 * <td></td>
 * <td>|</td>
 * <td>*concatexp*</td>
 * <td></td>
 * <td></td>
</tr> *
 *
 * <tr>
 * <td>*concatexp*</td>
 * <td>::=</td>
 * <td>*repeatexp*&nbsp;*concatexp*</td>
 * <td>(concatenation)</td>
 * <td></td>
</tr> *
 * <tr>
 * <td></td>
 * <td>|</td>
 * <td>*repeatexp*</td>
 * <td></td>
 * <td></td>
</tr> *
 *
 * <tr>
 * <td>*repeatexp*</td>
 * <td>::=</td>
 * <td>*repeatexp*&nbsp;`**?**`</td>
 * <td>(zero or one occurrence)</td>
 * <td></td>
</tr> *
 * <tr>
 * <td></td>
 * <td>|</td>
 * <td>*repeatexp*&nbsp;`*****`</td>
 * <td>(zero or more occurrences)</td>
 * <td></td>
</tr> *
 * <tr>
 * <td></td>
 * <td>|</td>
 * <td>*repeatexp*&nbsp;`**+**`</td>
 * <td>(one or more occurrences)</td>
 * <td></td>
</tr> *
 * <tr>
 * <td></td>
 * <td>|</td>
 * <td>*repeatexp*&nbsp;`**{***n***}**`</td>
 * <td>(`*n*` occurrences)</td>
 * <td></td>
</tr> *
 * <tr>
 * <td></td>
 * <td>|</td>
 * <td>*repeatexp*&nbsp;`**{***n***,}**`</td>
 * <td>(`*n*` or more occurrences)</td>
 * <td></td>
</tr> *
 * <tr>
 * <td></td>
 * <td>|</td>
 * <td>*repeatexp*&nbsp;`**{***n***,***m***}**`</td>
 * <td>(`*n*` to `*m*` occurrences, including both)</td>
 * <td></td>
</tr> *
 * <tr>
 * <td></td>
 * <td>|</td>
 * <td>*complexp*</td>
 * <td></td>
 * <td></td>
</tr> *
 *
 * <tr>
 * <td>*charclassexp*</td>
 * <td>::=</td>
 * <td>`**[**`&nbsp;*charclasses*&nbsp;`**]**`</td>
 * <td>(character class)</td>
 * <td></td>
</tr> *
 * <tr>
 * <td></td>
 * <td>|</td>
 * <td>`**[^**`&nbsp;*charclasses*&nbsp;`**]**`</td>
 * <td>(negated character class)</td>
 * <td></td>
</tr> *
 * <tr>
 * <td></td>
 * <td>|</td>
 * <td>*simpleexp*</td>
 * <td></td>
 * <td></td>
</tr> *
 *
 * <tr>
 * <td>*charclasses*</td>
 * <td>::=</td>
 * <td>*charclass*&nbsp;*charclasses*</td>
 * <td></td>
 * <td></td>
</tr> *
 * <tr>
 * <td></td>
 * <td>|</td>
 * <td>*charclass*</td>
 * <td></td>
 * <td></td>
</tr> *
 *
 * <tr>
 * <td>*charclass*</td>
 * <td>::=</td>
 * <td>*charexp*&nbsp;`**-**`&nbsp;*charexp*</td>
 * <td>(character range, including end-points)</td>
 * <td></td>
</tr> *
 * <tr>
 * <td></td>
 * <td>|</td>
 * <td>*charexp*</td>
 * <td></td>
 * <td></td>
</tr> *
 *
 * <tr>
 * <td>*simpleexp*</td>
 * <td>::=</td>
 * <td>*charexp*</td>
 * <td></td>
 * <td></td>
</tr> *
 * <tr>
 * <td></td>
 * <td>|</td>
 * <td>`**.**`</td>
 * <td>(any single character)</td>
 * <td></td>
</tr> *
 * <tr>
 * <td></td>
 * <td>|</td>
 * <td>`**#**`</td>
 * <td>(the empty language)</td>
 * <td><small>[OPTIONAL]</small></td>
</tr> *
 * <tr>
 * <td></td>
 * <td>|</td>
 * <td>`**@**`</td>
 * <td>(any string)</td>
 * <td><small>[OPTIONAL]</small></td>
</tr> *
 * <tr>
 * <td></td>
 * <td>|</td>
 * <td>`**"**`&nbsp;&lt;Unicode string without double-quotes&gt;&nbsp; `**"**`</td>
 * <td>(a string)</td>
 * <td></td>
</tr> *
 * <tr>
 * <td></td>
 * <td>|</td>
 * <td>`**(**`&nbsp;`**)**`</td>
 * <td>(the empty string)</td>
 * <td></td>
</tr> *
 * <tr>
 * <td></td>
 * <td>|</td>
 * <td>`**(**`&nbsp;*unionexp*&nbsp;`**)**`</td>
 * <td>(precedence override)</td>
 * <td></td>
</tr> *
 * <tr>
 * <td></td>
 * <td>|</td>
 * <td>`**&lt;**`&nbsp;&lt;identifier&gt;&nbsp;`**&gt;**`</td>
 * <td>(named automaton)</td>
 * <td><small>[OPTIONAL]</small></td>
</tr> *
 * <tr>
 * <td></td>
 * <td>|</td>
 * <td>`**&lt;***n*-*m***&gt;**`</td>
 * <td>(numerical interval)</td>
 * <td><small>[OPTIONAL]</small></td>
</tr> *
 *
 * <tr>
 * <td>*charexp*</td>
 * <td>::=</td>
 * <td>&lt;Unicode character&gt;</td>
 * <td>(a single non-reserved character)</td>
 * <td></td>
</tr> *
 *
 * <tr>
 * <td></td>
 * <td>|</td>
 * <td>`**\d**`</td>
 * <td>(a digit [0-9])</td>
 * <td></td>
</tr> *
 *
 * <tr>
 * <td></td>
 * <td>|</td>
 * <td>`**\D**`</td>
 * <td>(a non-digit [^0-9])</td>
 * <td></td>
</tr> *
 *
 * <tr>
 * <td></td>
 * <td>|</td>
 * <td>`**\s**`</td>
 * <td>(whitespace [ \t\n\r])</td>
 * <td></td>
</tr> *
 *
 * <tr>
 * <td></td>
 * <td>|</td>
 * <td>`**\S**`</td>
 * <td>(non whitespace [^\s])</td>
 * <td></td>
</tr> *
 *
 * <tr>
 * <td></td>
 * <td>|</td>
 * <td>`**\w**`</td>
 * <td>(a word character [a-zA-Z_0-9])</td>
 * <td></td>
</tr> *
 *
 * <tr>
 * <td></td>
 * <td>|</td>
 * <td>`**\W**`</td>
 * <td>(a non word character [^\w])</td>
 * <td></td>
</tr> *
 *
 * <tr>
 * <td></td>
 * <td>|</td>
 * <td>`**\**`&nbsp;&lt;Unicode character&gt;&nbsp;</td>
 * <td>(a single character)</td>
 * <td></td>
</tr> *
</table> *
 *
 *
 * The productions marked <small>[OPTIONAL]</small> are only allowed if specified by the syntax
 * flags passed to the `RegExp` constructor. The reserved characters used in the
 * (enabled) syntax must be escaped with backslash (`**\**`) or double-quotes (`
 * **"..."**`). (In contrast to other regexp syntaxes, this is required also in character
 * classes.) Be aware that dash (`**-**`) has a special meaning in *charclass*
 * expressions. An identifier is a string not containing right angle bracket (`**&gt;**
` * ) or dash (`**-**`). Numerical intervals are specified by non-negative
 * decimal integers and include both end points, and if `*n*` and `*m*
` *  have the same number of digits, then the conforming strings must have that length (i.e.
 * prefixed by 0's).
 *
 * @lucene.experimental
 */
class RegExp {
    /** The type of expression represented by a RegExp node.  */
    enum class Kind {
        /** The union of two expressions  */
        REGEXP_UNION,

        /** A sequence of two expressions  */
        REGEXP_CONCATENATION,

        /** The intersection of two expressions  */
        REGEXP_INTERSECTION,

        /** An optional expression  */
        REGEXP_OPTIONAL,

        /** An expression that repeats  */
        REGEXP_REPEAT,

        /** An expression that repeats a minimum number of times  */
        REGEXP_REPEAT_MIN,

        /** An expression that repeats a minimum and maximum number of times  */
        REGEXP_REPEAT_MINMAX,

        /** The complement of a character class  */
        REGEXP_COMPLEMENT,

        /** A Character  */
        REGEXP_CHAR,

        /** A Character range  */
        REGEXP_CHAR_RANGE,

        /** A Character class (list of ranges)  */
        REGEXP_CHAR_CLASS,

        /** Any Character allowed  */
        REGEXP_ANYCHAR,

        /** An empty expression  */
        REGEXP_EMPTY,

        /** A string expression  */
        REGEXP_STRING,

        /** Any string allowed  */
        REGEXP_ANYSTRING,

        /** An Automaton expression  */
        REGEXP_AUTOMATON,

        /** An Interval expression  */
        REGEXP_INTERVAL,

        /**
         * The complement of an expression.
         *
         */
        @Deprecated("Will be removed in Lucene 11")
        REGEXP_DEPRECATED_COMPLEMENT
    }

    // Immutable parsed state
    /** The type of expression  */
    val kind: Kind

    /** Child expressions held by a container type expression  */
    val exp1: RegExp?
    val exp2: RegExp?

    /** String expression  */
    val s: String?

    /** Character expression  */
    val c: Int

    /** Limits for repeatable type expressions  */
    val min: Int
    val max: Int
    val digits: Int

    /** Extents for range type expressions  */
    val from: IntArray?
    val to: IntArray?

    /** The string that was used to construct the regex. Compare to toString.  */
    // Parser variables
    val originalString: String?
    val flags: Int
    var pos: Int = 0

    /**
     * Constructs new `RegExp` from a string.
     *
     * @param s regexp string
     * @param syntax_flags boolean 'or' of optional syntax constructs to be enabled
     * @param match_flags boolean 'or' of match behavior options such as case insensitivity
     * @exception IllegalArgumentException if an error occurred while parsing the regular expression
     */
    /**
     * Constructs new `RegExp` from a string.
     *
     * @param s regexp string
     * @param syntax_flags boolean 'or' of optional syntax constructs to be enabled
     * @exception IllegalArgumentException if an error occurred while parsing the regular expression
     */
    /**
     * Constructs new `RegExp` from a string. Same as `RegExp(s, ALL)`.
     *
     * @param s regexp string
     * @exception IllegalArgumentException if an error occurred while parsing the regular expression
     */
    @JvmOverloads
    constructor(s: String, syntax_flags: Int = ALL, match_flags: Int = 0) {
        require((syntax_flags and DEPRECATED_COMPLEMENT.inv()) <= ALL) { "Illegal syntax flag" }

        require(!(match_flags > 0 && match_flags <= ALL)) { "Illegal match flag" }
        flags = syntax_flags or match_flags
        originalString = s
        val e: RegExp
        if (s.isEmpty()) e = makeString(flags, "")
        else {
            e = parseUnionExp()
            require(pos >= originalString.length) { "end-of-string expected at position $pos" }
        }
        kind = e.kind
        exp1 = e.exp1
        exp2 = e.exp2
        this.s = e.s
        c = e.c
        min = e.min
        max = e.max
        digits = e.digits
        from = e.from
        to = e.to
    }

    internal constructor(
        flags: Int,
        kind: Kind,
        exp1: RegExp?,
        exp2: RegExp?,
        s: String?,
        c: Int,
        min: Int,
        max: Int,
        digits: Int,
        from: IntArray?,
        to: IntArray?
    ) {
        this.originalString = null
        this.kind = kind
        this.flags = flags
        this.exp1 = exp1
        this.exp2 = exp2
        this.s = s
        this.c = c
        this.min = min
        this.max = max
        this.digits = digits
        this.from = from
        this.to = to
    }

    /**
     * Constructs new `Automaton` from this `RegExp`. Same as `
     * toAutomaton(null)` (empty automaton map).
     */
    fun toAutomaton(): Automaton? {
        return toAutomaton(null, null)
    }

    /**
     * Constructs new `Automaton` from this `RegExp`.
     *
     * @param automaton_provider provider of automata for named identifiers
     * @exception IllegalArgumentException if this regular expression uses a named identifier that is
     * not available from the automaton provider
     */
    @Throws(IllegalArgumentException::class, TooComplexToDeterminizeException::class)
    fun toAutomaton(automaton_provider: AutomatonProvider?): Automaton? {
        return toAutomaton(null, automaton_provider)
    }

    /**
     * Constructs new `Automaton` from this `RegExp`.
     *
     * @param automata a map from automaton identifiers to automata (of type `Automaton`).
     * @exception IllegalArgumentException if this regular expression uses a named identifier that
     * does not occur in the automaton map
     */
    @Throws(IllegalArgumentException::class, TooComplexToDeterminizeException::class)
    fun toAutomaton(automata: MutableMap<String?, Automaton?>?): Automaton? {
        return toAutomaton(automata, null)
    }

    @Throws(IllegalArgumentException::class)
    private fun toAutomaton(
        automata: MutableMap<String?, Automaton?>?, automaton_provider: AutomatonProvider?
    ): Automaton {
        var list: MutableList<Automaton>
        var a: Automaton? = null
        when (kind) {
            Kind.REGEXP_UNION -> {
                list = mutableListOf()
                findLeaves(exp1!!, Kind.REGEXP_UNION, list, automata, automaton_provider)
                findLeaves(exp2!!, Kind.REGEXP_UNION, list, automata, automaton_provider)
                a = Operations.union(list)
            }

            Kind.REGEXP_CONCATENATION -> {
                list = mutableListOf()
                findLeaves(exp1!!, Kind.REGEXP_CONCATENATION, list, automata, automaton_provider)
                findLeaves(exp2!!, Kind.REGEXP_CONCATENATION, list, automata, automaton_provider)
                a = Operations.concatenate(list)
            }

            Kind.REGEXP_INTERSECTION -> a =
                Operations.intersection(
                    exp1!!.toAutomaton(automata, automaton_provider),
                    exp2!!.toAutomaton(automata, automaton_provider)
                )

            Kind.REGEXP_OPTIONAL -> a = Operations.optional(exp1!!.toAutomaton(automata, automaton_provider))
            Kind.REGEXP_REPEAT -> a = Operations.repeat(exp1!!.toAutomaton(automata, automaton_provider))
            Kind.REGEXP_REPEAT_MIN -> {
                a = exp1!!.toAutomaton(automata, automaton_provider)
                a = Operations.repeat(a, min)
            }

            Kind.REGEXP_REPEAT_MINMAX -> {
                a = exp1!!.toAutomaton(automata, automaton_provider)
                a = Operations.repeat(a, min, max)
            }

            Kind.REGEXP_COMPLEMENT -> {
                // we don't support arbitrary complement, just "negated character class"
                // this is just a list of characters (e.g. "a") or ranges (e.g. "b-d")
                a = exp1!!.toAutomaton(automata, automaton_provider)
                a = Operations.complement(a, Int.Companion.MAX_VALUE)
            }

            Kind.REGEXP_DEPRECATED_COMPLEMENT -> {
                // to ease transitions for users only, support arbitrary complement
                // but bounded by DEFAULT_DETERMINIZE_WORK_LIMIT: must not be configurable.
                a = exp1!!.toAutomaton(automata, automaton_provider)
                a = Operations.complement(a, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
            }

            Kind.REGEXP_CHAR -> a = if (check(ASCII_CASE_INSENSITIVE or CASE_INSENSITIVE)) {
                Automata.makeCharSet(toCaseInsensitiveChar(c))
            } else {
                Automata.makeChar(c)
            }

            Kind.REGEXP_CHAR_RANGE -> a = Automata.makeCharRange(from!![0], to!![0])
            Kind.REGEXP_CHAR_CLASS -> a = Automata.makeCharClass(from!!, to!!)
            Kind.REGEXP_ANYCHAR -> a = Automata.makeAnyChar()
            Kind.REGEXP_EMPTY -> a = Automata.makeEmpty()
            Kind.REGEXP_STRING -> a = if (check(ASCII_CASE_INSENSITIVE or CASE_INSENSITIVE)) {
                toCaseInsensitiveString()
            } else {
                Automata.makeString(s!!)
            }

            Kind.REGEXP_ANYSTRING -> a = Automata.makeAnyString()
            Kind.REGEXP_AUTOMATON -> {
                var aa: Automaton? = null
                if (automata != null) {
                    aa = automata[s]
                }
                if (aa == null && automaton_provider != null) {
                    try {
                        aa = automaton_provider.getAutomaton(s!!)
                    } catch (e: IOException) {
                        throw IllegalArgumentException(e)
                    }
                }
                requireNotNull(aa) { "'" + s!! + "' not found" }
                a = aa
            }

            Kind.REGEXP_INTERVAL -> a = Automata.makeDecimalInterval(min, max, digits)
        }
        return a
    }

    /**
     * This function handles uses the Unicode spec for generating case-insensitive alternates.
     *
     *
     * See the [.CASE_INSENSITIVE] flag for details on case folding within the Unicode spec.
     *
     * @param codepoint the Character code point to encode as an Automaton
     * @return the original codepoint and the set of alternates
     */
    private fun toCaseInsensitiveChar(codepoint: Int): IntArray {
        val altCodepoints: IntArray? = CaseFolding.lookupAlternates(codepoint)
        if (altCodepoints != null) {
            val concat = IntArray(altCodepoints.size + 1)
            /*java.lang.System.arraycopy(altCodepoints, 0, concat, 0, altCodepoints.size)*/
            altCodepoints.copyInto(
                concat,
                0,
                0,
                altCodepoints.size
            )
            concat[altCodepoints.size] = codepoint
            return concat
        } else {
            val altCase =
                if (Character.isLowerCase(codepoint)) Character.toUpperCase(codepoint) else Character.toLowerCase(codepoint)
            return if (altCase != codepoint) {
                intArrayOf(altCase, codepoint)
            } else {
                intArrayOf(codepoint)
            }
        }
    }

    private fun toCaseInsensitiveString(): Automaton {
        val list: MutableList<Automaton> = mutableListOf()

        val iter: CharIterator = s!!.codePointSequence().iterator() as CharIterator
        while (iter.hasNext()) {
            val points = toCaseInsensitiveChar(iter.next().code)
            list.add(Automata.makeCharSet(points))
        }
        return Operations.concatenate(list)
    }

    private fun findLeaves(
        exp: RegExp,
        kind: Kind?,
        list: MutableList<Automaton>,
        automata: MutableMap<String?, Automaton?>?,
        automaton_provider: AutomatonProvider?
    ) {
        if (exp.kind == kind) {
            findLeaves(exp.exp1!!, kind, list, automata, automaton_provider)
            findLeaves(exp.exp2!!, kind, list, automata, automaton_provider)
        } else {
            list.add(exp.toAutomaton(automata, automaton_provider))
        }
    }

    /** Constructs string from parsed regular expression.  */
    override fun toString(): String {
        val b = StringBuilder()
        toStringBuilder(b)
        return b.toString()
    }

    fun toStringBuilder(b: StringBuilder) {
        when (kind) {
            Kind.REGEXP_UNION -> {
                b.append("(")
                exp1!!.toStringBuilder(b)
                b.append("|")
                exp2!!.toStringBuilder(b)
                b.append(")")
            }

            Kind.REGEXP_CONCATENATION -> {
                exp1!!.toStringBuilder(b)
                exp2!!.toStringBuilder(b)
            }

            Kind.REGEXP_INTERSECTION -> {
                b.append("(")
                exp1!!.toStringBuilder(b)
                b.append("&")
                exp2!!.toStringBuilder(b)
                b.append(")")
            }

            Kind.REGEXP_OPTIONAL -> {
                b.append("(")
                exp1!!.toStringBuilder(b)
                b.append(")?")
            }

            Kind.REGEXP_REPEAT -> {
                b.append("(")
                exp1!!.toStringBuilder(b)
                b.append(")*")
            }

            Kind.REGEXP_REPEAT_MIN -> {
                b.append("(")
                exp1!!.toStringBuilder(b)
                b.append("){").append(min).append(",}")
            }

            Kind.REGEXP_REPEAT_MINMAX -> {
                b.append("(")
                exp1!!.toStringBuilder(b)
                b.append("){").append(min).append(",").append(max).append("}")
            }

            Kind.REGEXP_COMPLEMENT, Kind.REGEXP_DEPRECATED_COMPLEMENT -> {
                b.append("~(")
                exp1!!.toStringBuilder(b)
                b.append(")")
            }

            Kind.REGEXP_CHAR -> b.append("\\").appendCodePoint(c)
            Kind.REGEXP_CHAR_RANGE -> b.append("[\\").appendCodePoint(from!![0]).append("-\\").appendCodePoint(to!![0])
                .append("]")

            Kind.REGEXP_CHAR_CLASS -> {
                b.append("[")
                var i = 0
                while (i < from!!.size) {
                    if (from[i] == to!![i]) {
                        b.append("\\").appendCodePoint(from[i])
                    } else {
                        b.append("\\").appendCodePoint(from[i])
                        b.append("-\\").appendCodePoint(to[i])
                    }
                    i++
                }
                b.append("]")
            }

            Kind.REGEXP_ANYCHAR -> b.append(".")
            Kind.REGEXP_EMPTY -> b.append("#")
            Kind.REGEXP_STRING -> b.append("\"").append(s!!).append("\"")
            Kind.REGEXP_ANYSTRING -> b.append("@")
            Kind.REGEXP_AUTOMATON -> b.append("<").append(s!!).append(">")
            Kind.REGEXP_INTERVAL -> {
                val s1 = min.toString()
                val s2 = max.toString()
                b.append("<")
                if (digits > 0) {
                    var i = s1.length
                    while (i < digits) {
                        b.append('0')
                        i++
                    }
                }
                b.append(s1).append("-")
                if (digits > 0) {
                    var i = s2.length
                    while (i < digits) {
                        b.append('0')
                        i++
                    }
                }
                b.append(s2).append(">")
            }
        }
    }

    /** Like to string, but more verbose (shows the hierarchy more clearly).  */
    fun toStringTree(): String {
        val b = StringBuilder()
        toStringTree(b, "")
        return b.toString()
    }

    fun toStringTree(b: StringBuilder, indent: String?) {
        when (kind) {
            Kind.REGEXP_UNION, Kind.REGEXP_CONCATENATION, Kind.REGEXP_INTERSECTION -> {
                b.append(indent)
                b.append(kind)
                b.append('\n')
                exp1!!.toStringTree(b, "$indent  ")
                exp2!!.toStringTree(b, "$indent  ")
            }

            Kind.REGEXP_OPTIONAL, Kind.REGEXP_REPEAT, Kind.REGEXP_COMPLEMENT, Kind.REGEXP_DEPRECATED_COMPLEMENT -> {
                b.append(indent)
                b.append(kind)
                b.append('\n')
                exp1!!.toStringTree(b, "$indent  ")
            }

            Kind.REGEXP_REPEAT_MIN -> {
                b.append(indent)
                b.append(kind)
                b.append(" min=")
                b.append(min)
                b.append('\n')
                exp1!!.toStringTree(b, "$indent  ")
            }

            Kind.REGEXP_REPEAT_MINMAX -> {
                b.append(indent)
                b.append(kind)
                b.append(" min=")
                b.append(min)
                b.append(" max=")
                b.append(max)
                b.append('\n')
                exp1!!.toStringTree(b, "$indent  ")
            }

            Kind.REGEXP_CHAR -> {
                b.append(indent)
                b.append(kind)
                b.append(" char=")
                b.appendCodePoint(c)
                b.append('\n')
            }

            Kind.REGEXP_CHAR_RANGE -> {
                b.append(indent)
                b.append(kind)
                b.append(" from=")
                b.appendCodePoint(from!![0])
                b.append(" to=")
                b.appendCodePoint(to!![0])
                b.append('\n')
            }

            Kind.REGEXP_CHAR_CLASS -> {
                b.append(indent)
                b.append(kind)
                b.append(" starts=")
                b.append(toHexString(from!!))
                b.append(" ends=")
                b.append(toHexString(to!!))
                b.append('\n')
            }

            Kind.REGEXP_ANYCHAR, Kind.REGEXP_EMPTY -> {
                b.append(indent)
                b.append(kind)
                b.append('\n')
            }

            Kind.REGEXP_STRING -> {
                b.append(indent)
                b.append(kind)
                b.append(" string=")
                b.append(s!!)
                b.append('\n')
            }

            Kind.REGEXP_ANYSTRING -> {
                b.append(indent)
                b.append(kind)
                b.append('\n')
            }

            Kind.REGEXP_AUTOMATON -> {
                b.append(indent)
                b.append(kind)
                b.append('\n')
            }

            Kind.REGEXP_INTERVAL -> {
                b.append(indent)
                b.append(kind)
                val s1 = min.toString()
                val s2 = max.toString()
                b.append("<")
                if (digits > 0) {
                    var i = s1.length
                    while (i < digits) {
                        b.append('0')
                        i++
                    }
                }
                b.append(s1).append("-")
                if (digits > 0) {
                    var i = s2.length
                    while (i < digits) {
                        b.append('0')
                        i++
                    }
                }
                b.append(s2).append(">")
                b.append('\n')
            }
        }
    }

    /** prints like `[U+002A U+FD72 U+1FFFF]`  */
    private fun toHexString(range: IntArray): StringBuilder {
        val sb = StringBuilder()
        sb.append('[')
        for ((i, codepoint) in range.withIndex()) {
            if (i > 0) {
                sb.append(' ')
            }
            // Convert the code point to an uppercase hexadecimal string,
            // ensuring at least 4 digits (zero-padded if necessary).
            val hex = codepoint.toString(16).uppercase().padStart(4, '0')
            sb.append("U+").append(hex)
        }
        sb.append(']')
        return sb
    }

    val identifiers: MutableSet<String?>
        /** Returns set of automaton identifiers that occur in this regular expression.  */
        get() {
            val set: MutableSet<String?> = mutableSetOf()
            getIdentifiers(set)
            return set
        }

    fun getIdentifiers(set: MutableSet<String?>) {
        when (kind) {
            Kind.REGEXP_UNION, Kind.REGEXP_CONCATENATION, Kind.REGEXP_INTERSECTION -> {
                exp1!!.getIdentifiers(set)
                exp2!!.getIdentifiers(set)
            }

            Kind.REGEXP_OPTIONAL, Kind.REGEXP_REPEAT, Kind.REGEXP_REPEAT_MIN, Kind.REGEXP_REPEAT_MINMAX, Kind.REGEXP_COMPLEMENT, Kind.REGEXP_DEPRECATED_COMPLEMENT -> exp1!!.getIdentifiers(
                set
            )

            Kind.REGEXP_AUTOMATON -> set.add(s)
            Kind.REGEXP_ANYCHAR, Kind.REGEXP_ANYSTRING, Kind.REGEXP_CHAR, Kind.REGEXP_CHAR_RANGE, Kind.REGEXP_CHAR_CLASS, Kind.REGEXP_EMPTY, Kind.REGEXP_INTERVAL, Kind.REGEXP_STRING -> {}
            else -> {}
        }
    }

    private fun peek(s: String): Boolean {
        return more() && s.indexOf(originalString!!.codePointAt(pos).toChar()) != -1
    }

    private fun match(c: Int): Boolean {
        if (pos >= originalString!!.length) return false
        if (originalString.codePointAt(pos) == c) {
            pos += Character.charCount(c)
            return true
        }
        return false
    }

    private fun more(): Boolean {
        return pos < originalString!!.length
    }

    @Throws(IllegalArgumentException::class)
    private fun next(): Int {
        require(more()) { "unexpected end-of-string" }
        val ch: Int = originalString!!.codePointAt(pos)
        pos += Character.charCount(ch)
        return ch
    }

    private fun check(flag: Int): Boolean {
        return (flags and flag) != 0
    }

    @Throws(IllegalArgumentException::class)
            /*fun parseUnionExp(): RegExp {
                return iterativeParseExp(
                    java.util.function.Supplier { this.parseInterExp() },
                    java.util.function.BooleanSupplier { match('|'.code) },
                    MakeRegexGroup { flags: Int, exp1: RegExp?, exp2: RegExp? -> Companion.makeUnion(flags, exp1!!, exp2!!) })
            }*/
    fun parseUnionExp(): RegExp =
        iterativeParseExp(this::parseInterExp, { match('|'.code) }, RegExp::makeUnion)

    @Throws(IllegalArgumentException::class)
    fun parseInterExp(): RegExp =
        iterativeParseExp(::parseConcatExp, { check(INTERSECTION) && match('&'.code) }, RegExp::makeIntersection)

    @Throws(IllegalArgumentException::class)
    fun parseConcatExp(): RegExp =
        iterativeParseExp(::parseRepeatExp, { more() && !peek(")|") && (!check(INTERSECTION) || !peek("&")) },
            RegExp::makeConcatenation)

    /**
     * Custom Functional Interface for a Supplying methods with signature of RegExp(int int1, RegExp
     * exp1, RegExp exp2)
     */
    private fun interface MakeRegexGroup {
        fun get(int1: Int, exp1: RegExp?, exp2: RegExp?): RegExp
    }

    @Throws(IllegalArgumentException::class)
    fun iterativeParseExp(
        gather: () -> RegExp,
        stop: () -> Boolean,
        associativeReduce: (Int, RegExp, RegExp) -> RegExp
    ): RegExp {
        var result = gather()
        while (stop()) {
            val e = gather()
            result = associativeReduce(flags, result, e)
        }
        return result
    }

    @Throws(IllegalArgumentException::class)
    fun parseRepeatExp(): RegExp {
        var e = parseComplExp()
        while (peek("?*+{")) {
            if (match('?'.code)) e = makeOptional(flags, e)
            else if (match('*'.code)) e = makeRepeat(flags, e)
            else if (match('+'.code)) e = makeRepeat(flags, e, 1)
            else if (match('{'.code)) {
                var start = pos
                while (peek("0123456789")) next()
                require(start != pos) { "integer expected at position $pos" }
                val n = originalString!!.substring(start, pos).toInt()
                var m = -1
                if (match(','.code)) {
                    start = pos
                    while (peek("0123456789")) next()
                    if (start != pos) m = originalString.substring(start, pos).toInt()
                } else m = n
                require(match('}'.code)) { "expected '}' at position $pos" }
                require(!(m != -1 && n > m)) { "invalid repetition range(out of order): $n..$m" }
                e = if (m == -1) makeRepeat(flags, e, n)
                else makeRepeat(flags, e, n, m)
            }
        }
        return e
    }

    @Throws(IllegalArgumentException::class)
    fun parseComplExp(): RegExp {
        return if (check(DEPRECATED_COMPLEMENT) && match('~'.code)) makeDeprecatedComplement(flags, parseComplExp())
        else parseCharClassExp()
    }

    @Throws(IllegalArgumentException::class)
    fun parseCharClassExp(): RegExp {
        if (match('['.code)) {
            var negate = false
            if (match('^'.code)) negate = true
            var e = parseCharClasses()
            if (negate) e = makeIntersection(flags, makeAnyChar(flags), makeComplement(flags, e))
            require(match(']'.code)) { "expected ']' at position $pos" }
            return e
        } else return parseSimpleExp()
    }

    @Throws(IllegalArgumentException::class)
    fun parseCharClasses(): RegExp {
        val starts: MutableList<Int?> = mutableListOf()
        val ends: MutableList<Int?> = mutableListOf()

        do {
            // look for escape
            if (match('\\'.code)) {
                if (peek("\\ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz")) {
                    if (peek("u")) {
                        // rewind one char to let parseCharExp handle unicode escape
                        pos--
                        val c = parseCharExp()
                        starts.add(c)
                        ends.add(c)
                    } else {
                        // special "escape" or invalid escape
                        expandPreDefined(starts, ends)
                    }
                } else {
                    // escaped character
                    pos--
                    val c = parseCharExp()
                    starts.add(c)
                    ends.add(c)
                }
            } else {
                // parse a character
                val c = parseCharExp()

                if (match('-'.code)) {
                    // range from c-d
                    starts.add(c)
                    ends.add(parseCharExp())
                } else if (check(ASCII_CASE_INSENSITIVE or CASE_INSENSITIVE)) {
                    // single case-insensitive character
                    for (form in toCaseInsensitiveChar(c)) {
                        starts.add(form)
                        ends.add(form)
                    }
                } else {
                    // single character
                    starts.add(c)
                    ends.add(c)
                }
            }
        } while (more() && !peek("]"))

        // not sure why we bother optimizing nodes, same automaton...
        // definitely saves time vs fixing toString()-based tests.
        return if (starts.size == 1) {
            if (starts[0] == ends[0]) {
                makeChar(flags, starts[0]!!)
            } else {
                makeCharRange(flags, starts[0]!!, ends[0]!!)
            }
        } else {
            makeCharClass(
                flags,
                starts.map { obj: Int? -> obj!! }.toTypedArray() as IntArray,
                ends.map { obj: Int? -> obj!! }.toTypedArray() as IntArray
            )
        }
    }

    fun expandPreDefined(starts: MutableList<Int?>, ends: MutableList<Int?>) {
        if (peek("\\")) {
            // escape
            starts.add('\\'.code)
            ends.add('\\'.code)
            next()
        } else if (peek("d")) {
            // digit: [0-9]
            starts.add('0'.code)
            ends.add('9'.code)
            next()
        } else if (peek("D")) {
            // non-digit: [^0-9]
            starts.add(Character.MIN_CODE_POINT)
            ends.add('0'.code - 1)
            starts.add('9'.code + 1)
            ends.add(Character.MAX_CODE_POINT)
            next()
        } else if (peek("s")) {
            // whitespace: [\t-\n\r ]
            starts.add('\t'.code)
            ends.add('\n'.code)
            starts.add('\r'.code)
            ends.add('\r'.code)
            starts.add(' '.code)
            ends.add(' '.code)
            next()
        } else if (peek("S")) {
            // non-whitespace: [^\t-\n\r ]
            starts.add(Character.MIN_CODE_POINT)
            ends.add('\t'.code - 1)
            starts.add('\n'.code + 1)
            ends.add('\r'.code - 1)
            starts.add('\r'.code + 1)
            ends.add(' '.code - 1)
            starts.add(' '.code + 1)
            ends.add(Character.MAX_CODE_POINT)
            next()
        } else if (peek("w")) {
            // word: [0-9A-Z_a-z]
            starts.add('0'.code)
            ends.add('9'.code)
            starts.add('A'.code)
            ends.add('Z'.code)
            starts.add('_'.code)
            ends.add('_'.code)
            starts.add('a'.code)
            ends.add('z'.code)
            next()
        } else if (peek("W")) {
            // non-word: [^0-9A-Z_a-z]
            starts.add(Character.MIN_CODE_POINT)
            ends.add('0'.code - 1)
            starts.add('9'.code + 1)
            ends.add('A'.code - 1)
            starts.add('Z'.code + 1)
            ends.add('_'.code - 1)
            starts.add('_'.code + 1)
            ends.add('a'.code - 1)
            starts.add('z'.code + 1)
            ends.add(Character.MAX_CODE_POINT)
            next()
        } else require(!(peek("abcefghijklmnopqrtuvxyz") || peek("ABCEFGHIJKLMNOPQRTUVXYZ"))) { "invalid character class \\" + next() }
    }

    fun matchPredefinedCharacterClass(): RegExp? {
        // See https://docs.oracle.com/javase/tutorial/essential/regex/pre_char_classes.html
        if (pos < originalString!!.length - 1 &&
            originalString[pos] == '\\' && originalString[pos + 1] == 'u'
        ) {
            // Unicode escape; let caller handle via parseCharExp
            return null
        }
        if (match('\\'.code) && peek("\\ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz")) {
            val starts: MutableList<Int?> = mutableListOf()
            val ends: MutableList<Int?> = mutableListOf()
            expandPreDefined(starts, ends)
            return makeCharClass(
                flags,
                starts.map { obj: Int? -> obj!! }.toTypedArray() as IntArray,
                ends.map { obj: Int? -> obj!! }.toTypedArray() as IntArray
            )
        }

        return null
    }

    @Throws(IllegalArgumentException::class)
    fun parseSimpleExp(): RegExp {
        if (match('.'.code)) return makeAnyChar(flags)
        else if (check(EMPTY) && match('#'.code)) return makeEmpty(flags)
        else if (check(ANYSTRING) && match('@'.code)) return makeAnyString(flags)
        else if (match('"'.code)) {
            val start = pos
            while (more() && !peek("\"")) next()
            require(match('"'.code)) { "expected '\"' at position $pos" }
            return makeString(flags, originalString!!.substring(start, pos - 1))
        } else if (match('('.code)) {
            if (match(')'.code)) return makeString(flags, "")
            val e = parseUnionExp()
            require(match(')'.code)) { "expected ')' at position $pos" }
            return e
        } else if ((check(AUTOMATON) || check(INTERVAL)) && match('<'.code)) {
            val start = pos
            while (more() && !peek(">")) next()
            require(match('>'.code)) { "expected '>' at position $pos" }
            val s = originalString!!.substring(start, pos - 1)
            val i = s.indexOf('-')
            if (i == -1) {
                require(check(AUTOMATON)) { "interval syntax error at position " + (pos - 1) }
                return makeAutomaton(flags, s)
            } else {
                require(check(INTERVAL)) { "illegal identifier at position " + (pos - 1) }
                try {
                    if (i == 0 || i == s.length - 1 || i != s.lastIndexOf('-')) throw NumberFormatException()
                    val smin = s.substring(0, i)
                    val smax = s.substring(i + 1)
                    var imin = smin.toInt()
                    var imax = smax.toInt()
                    val digits: Int = if (smin.length == smax.length) smin.length
                    else 0
                    if (imin > imax) {
                        val t = imin
                        imin = imax
                        imax = t
                    }
                    return makeInterval(flags, imin, imax, digits)
                } catch (e: NumberFormatException) {
                    throw IllegalArgumentException("interval syntax error at position " + (pos - 1), e)
                }
            }
        } else {
            val predefined = matchPredefinedCharacterClass()
            if (predefined != null) {
                return predefined
            }
            return makeChar(flags, parseCharExp())
        }
    }

    @Throws(IllegalArgumentException::class)
    fun parseCharExp(): Int {
        match('\\'.code)
        if (peek("u")) {
            next() // consume 'u'
            var code = 0
            repeat(4) {
                require(more()) { "unexpected end-of-string" }
                val ch = next().toChar()
                val digit = ch.digitToIntOrNull(16) ?: -1
                require(digit != -1) { "invalid unicode escape" }
                code = (code shl 4) or digit
            }
            return code
        }
        return next()
    }

    companion object {
        // -----  Syntax flags ( <= 0xff )  ------
        /** Syntax flag, enables intersection (`&`).  */
        const val INTERSECTION: Int = 0x0001

        /** Syntax flag, enables empty language (`#`).  */
        const val EMPTY: Int = 0x0004

        /** Syntax flag, enables anystring (`@`).  */
        const val ANYSTRING: Int = 0x0008

        /** Syntax flag, enables named automata (`<`identifier`>`).  */
        const val AUTOMATON: Int = 0x0010

        /** Syntax flag, enables numerical intervals ( `<*n*-*m*>`).  */
        const val INTERVAL: Int = 0x0020

        /** Syntax flag, enables all optional regexp syntax.  */
        const val ALL: Int = 0xff

        /** Syntax flag, enables no optional regexp syntax.  */
        const val NONE: Int = 0x0000

        // -----  Matching flags ( > 0xff <= 0xffff )  ------
        /**
         * Allows case-insensitive matching of ASCII characters.
         *
         *
         * This flag has been deprecated in favor of [.CASE_INSENSITIVE] that supports the full
         * range of Unicode characters. Usage of this flag now has the same behavior as [ ][.CASE_INSENSITIVE]
         */
        @Deprecated("")
        const val ASCII_CASE_INSENSITIVE: Int = 0x0100

        /**
         * Allows case-insensitive matching of most Unicode characters.
         *
         *
         * In general the attempt is to reach parity with [java.util.regex.Pattern]
         * Pattern.CASE_INSENSITIVE and Pattern.UNICODE_CASE flags when doing a case-insensitive match. We
         * support common case folding in addition to simple case folding as defined by the common (C),
         * simple (S) and special (T) mappings in
         * https://www.unicode.org/Public/16.0.0/ucd/CaseFolding.txt. This is in line with [ ] and means characters like those representing the Greek symbol sigma
         * (Σ, σ, ς) will all match one another despite σ and ς both being lowercase characters as
         * detailed here: https://www.unicode.org/Public/UCD/latest/ucd/SpecialCasing.txt.
         *
         *
         * Some Unicode characters are difficult to correctly decode casing. In some cases Java's
         * String class correctly handles decoding these but Java's [java.util.regex.Pattern] class
         * does not. We make only a best effort to maintaining consistency with [ ] and there may be differences.
         *
         *
         * There are three known special classes of these characters:
         *
         *
         *  * 1. the set of characters whose casing matches across multiple characters such as the
         * Greek sigma character mentioned above (Σ, σ, ς); we support these; notably some of these
         * characters fall into the ASCII range and so will behave differently when this flag is
         * enabled
         *  * 2. the set of characters that are neither in an upper nor lower case stable state and can
         * be both uppercased and lowercased from their current code point such as ǅ which when
         * uppercased produces Ǆ and when lowercased produces ǆ; we support these
         *  * 3. the set of characters that when uppercased produce more than 1 character. For
         * performance reasons we ignore characters for now, which is consistent with [       ]
         *
         *
         *
         * Sometimes these classes of character will overlap; if a character is in both class 3 and any
         * other case listed above it is ignored; this is consistent with [java.util.regex.Pattern]
         * and C,S,T mappings in https://www.unicode.org/Public/16.0.0/ucd/CaseFolding.txt. Support for
         * class 3 is only available with full (F) mappings, which is not supported. For instance: this
         * character ῼ will match it's lowercase form ῳ but not it's uppercase form: ΩΙ
         *
         *
         * Class 3 characters that when uppercased generate multiple characters such as ﬗ (0xFB17)
         * which when uppercased produces ՄԽ (code points: 0x0544 0x053D) and are therefore ignored;
         * however, lowercase matching on these values is supported: 0x00DF, 0x0130, 0x0149, 0x01F0,
         * 0x0390, 0x03B0, 0x0587, 0x1E96-0x1E9A, 0x1F50, 0x1F52, 0x1F54, 0x1F56, 0x1F80-0x1FAF,
         * 0x1FB2-0x1FB4, 0x1FB6, 0x1FB7, 0x1FBC, 0x1FC2-0x1FC4, 0x1FC6, 0x1FC7, 0x1FCC, 0x1FD2, 0x1FD3,
         * 0x1FD6, 0x1FD7, 0x1FE2-0x1FE4, 0x1FE6, 0x1FE7, 0x1FF2-0x1FF4, 0x1FF6, 0x1FF7, 0x1FFC,
         * 0xFB00-0xFB06, 0xFB13-0xFB17
         */
        const val CASE_INSENSITIVE: Int = 0x0200

        // -----  Deprecated flags ( > 0xffff )  ------
        /**
         * Allows regexp parsing of the complement (`~`).
         *
         *
         * Note that processing the complement can require exponential time, but will be bounded by an
         * internal limit. Regexes exceeding the limit will fail with TooComplexToDeterminizeException.
         *
         */
        @Deprecated("This method will be removed in Lucene 11")
        const val DEPRECATED_COMPLEMENT: Int = 0x10000

        // Simplified construction of container nodes
        fun newContainerNode(flags: Int, kind: Kind, exp1: RegExp?, exp2: RegExp?): RegExp {
            return RegExp(flags, kind, exp1, exp2, null, 0, 0, 0, 0, null, null)
        }

        // Simplified construction of repeating nodes
        fun newRepeatingNode(flags: Int, kind: Kind, exp: RegExp, min: Int, max: Int): RegExp {
            return RegExp(flags, kind, exp, null, null, 0, min, max, 0, null, null)
        }

        // Simplified construction of leaf nodes
        fun newLeafNode(
            flags: Int, kind: Kind, s: String?, c: Int, min: Int, max: Int, digits: Int, from: IntArray?, to: IntArray?
        ): RegExp {
            return RegExp(flags, kind, null, null, s, c, min, max, digits, from, to)
        }

        fun makeUnion(flags: Int, exp1: RegExp, exp2: RegExp): RegExp {
            return newContainerNode(flags, Kind.REGEXP_UNION, exp1, exp2)
        }

        fun makeConcatenation(flags: Int, exp1: RegExp, exp2: RegExp): RegExp {
            if ((exp1.kind == Kind.REGEXP_CHAR || exp1.kind == Kind.REGEXP_STRING)
                && (exp2.kind == Kind.REGEXP_CHAR || exp2.kind == Kind.REGEXP_STRING)
            ) return makeString(flags, exp1, exp2)
            val rexp1: RegExp
            val rexp2: RegExp
            if (exp1.kind == Kind.REGEXP_CONCATENATION && (exp1.exp2!!.kind == Kind.REGEXP_CHAR || exp1.exp2.kind == Kind.REGEXP_STRING)
                && (exp2.kind == Kind.REGEXP_CHAR || exp2.kind == Kind.REGEXP_STRING)
            ) {
                rexp1 = exp1.exp1!!
                rexp2 = makeString(flags, exp1.exp2, exp2)
            } else if ((exp1.kind == Kind.REGEXP_CHAR || exp1.kind == Kind.REGEXP_STRING)
                && exp2.kind == Kind.REGEXP_CONCATENATION && (exp2.exp1!!.kind == Kind.REGEXP_CHAR || exp2.exp1.kind == Kind.REGEXP_STRING)
            ) {
                rexp1 = makeString(flags, exp1, exp2.exp1)
                rexp2 = exp2.exp2!!
            } else {
                rexp1 = exp1
                rexp2 = exp2
            }
            return newContainerNode(flags, Kind.REGEXP_CONCATENATION, rexp1, rexp2)
        }

        private fun makeString(flags: Int, exp1: RegExp, exp2: RegExp): RegExp {
            val b = StringBuilder()
            if (exp1.kind == Kind.REGEXP_STRING) b.append(exp1.s!!)
            else b.appendCodePoint(exp1.c)
            if (exp2.kind == Kind.REGEXP_STRING) b.append(exp2.s!!)
            else b.appendCodePoint(exp2.c)
            return makeString(flags, b.toString())
        }

        fun makeIntersection(flags: Int, exp1: RegExp, exp2: RegExp): RegExp {
            return newContainerNode(flags, Kind.REGEXP_INTERSECTION, exp1, exp2)
        }

        fun makeOptional(flags: Int, exp: RegExp): RegExp {
            return Companion.newContainerNode(flags, Kind.REGEXP_OPTIONAL, exp, null)
        }

        fun makeRepeat(flags: Int, exp: RegExp): RegExp {
            return Companion.newContainerNode(flags, Kind.REGEXP_REPEAT, exp, null)
        }

        fun makeRepeat(flags: Int, exp: RegExp, min: Int): RegExp {
            return newRepeatingNode(flags, Kind.REGEXP_REPEAT_MIN, exp, min, 0)
        }

        fun makeRepeat(flags: Int, exp: RegExp, min: Int, max: Int): RegExp {
            return newRepeatingNode(flags, Kind.REGEXP_REPEAT_MINMAX, exp, min, max)
        }

        fun makeComplement(flags: Int, exp: RegExp): RegExp {
            return Companion.newContainerNode(flags, Kind.REGEXP_COMPLEMENT, exp, null)
        }

        /**
         * Creates node that will compute complement of arbitrary expression.
         *
         */
        @Deprecated("Will be removed in Lucene 11")
        fun makeDeprecatedComplement(flags: Int, exp: RegExp): RegExp {
            return Companion.newContainerNode(flags, Kind.REGEXP_DEPRECATED_COMPLEMENT, exp, null)
        }

        fun makeChar(flags: Int, c: Int): RegExp {
            return Companion.newLeafNode(flags, Kind.REGEXP_CHAR, null, c, 0, 0, 0, null, null)
        }

        fun makeCharRange(flags: Int, from: Int, to: Int): RegExp {
            require(from <= to) { "invalid range: from ($from) cannot be > to ($to)" }
            return Companion.newLeafNode(
                flags, Kind.REGEXP_CHAR_RANGE, null, 0, 0, 0, 0, intArrayOf(from), intArrayOf(to)
            )
        }

        fun makeCharClass(flags: Int, from: IntArray, to: IntArray): RegExp {
            if (from.size != to.size) {
                throw IllegalStateException("invalid class: from.length (${from.size}) != to.length (${to.size})")
            }
            for (i in from.indices) {
                if (from[i] > to[i]) {
                    throw IllegalArgumentException("invalid range: from (${from[i]}) cannot be > to (${to[i]})")
                }
            }
            return newLeafNode(flags, Kind.REGEXP_CHAR_CLASS, null, 0, 0, 0, 0, from, to)
        }

        fun makeAnyChar(flags: Int): RegExp {
            return Companion.newContainerNode(flags, Kind.REGEXP_ANYCHAR, null, null)
        }

        fun makeEmpty(flags: Int): RegExp {
            return Companion.newContainerNode(flags, Kind.REGEXP_EMPTY, null, null)
        }

        fun makeString(flags: Int, s: String): RegExp {
            return newLeafNode(flags, Kind.REGEXP_STRING, s, 0, 0, 0, 0, null, null)
        }

        fun makeAnyString(flags: Int): RegExp {
            return Companion.newContainerNode(flags, Kind.REGEXP_ANYSTRING, null, null)
        }

        fun makeAutomaton(flags: Int, s: String): RegExp {
            return newLeafNode(flags, Kind.REGEXP_AUTOMATON, s, 0, 0, 0, 0, null, null)
        }

        fun makeInterval(flags: Int, min: Int, max: Int, digits: Int): RegExp {
            return Companion.newLeafNode(flags, Kind.REGEXP_INTERVAL, null, 0, min, max, digits, null, null)
        }
    }
}