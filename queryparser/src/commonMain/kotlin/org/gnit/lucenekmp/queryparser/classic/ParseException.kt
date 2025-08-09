package org.gnit.lucenekmp.queryparser.classic

import dev.scottpierce.envvar.EnvVar
import org.gnit.lucenekmp.jdkport.toStringWithRadix

/**
 * This exception is thrown when parse errors are encountered.
 * You can explicitly create objects of this exception type by
 * calling the method generateParseException in the generated
 * parser.
 *
 * You can modify this class to customize your error reporting
 * mechanisms so long as you retain the public fields.
 */
open class ParseException : Exception {
    /**
     * This constructor is used by the method "generateParseException"
     * in the generated parser.  Calling this constructor generates
     * a new object of this type with the fields "currentToken",
     * "expectedTokenSequences", and "tokenImage" set.
     */
    constructor(
        currentTokenVal: Token,
        expectedTokenSequencesVal: Array<IntArray>,
        tokenImageVal: Array<String>
    ) : super(initialise(currentTokenVal, expectedTokenSequencesVal, tokenImageVal)) {
        currentToken = currentTokenVal
        expectedTokenSequences = expectedTokenSequencesVal
        tokenImage = tokenImageVal
    }

    /**
     * The following constructors are for use by you for whatever
     * purpose you can think of.  Constructing the exception in this
     * manner makes the exception behave in the normal way - i.e., as
     * documented in the class "Throwable".  The fields "errorToken",
     * "expectedTokenSequences", and "tokenImage" do not contain
     * relevant information.  The JavaCC generated code does not use
     * these constructors.
     */
    constructor() : super()

    /** Constructor with message.  */
    constructor(message: String) : super(message)

    constructor(message: String, cause: Throwable) : super(message, cause)

    /**
     * This is the last token that has been consumed successfully.  If
     * this object has been created due to a parse error, the token
     * following this token will (therefore) be the first error token.
     */
    var currentToken: Token? = null

    /**
     * Each entry in this array is an array of integers.  Each array
     * of integers represents a sequence of tokens (by their ordinal
     * values) that is expected at this point of the parse.
     */
    lateinit var expectedTokenSequences: Array<IntArray>

    /**
     * This is a reference to the "tokenImage" array of the generated
     * parser within which the parse error occurred.  This array is
     * defined in the generated ...Constants interface.
     */
    lateinit var tokenImage: Array<String>

    companion object {
        /**
         * The version identifier for this Serializable class.
         * Increment only if the *serialized* form of the
         * class changes.
         */
        private const val serialVersionUID = 1L

        /**
         * The end of line string for this machine.
         */
        protected var EOL: String = EnvVar["line.separator"]?: "\n" /*java.lang.System.getProperty("line.separator", "\n")*/

        /**
         * It uses "currentToken" and "expectedTokenSequences" to generate a parse
         * error message and returns it.  If this object has been created
         * due to a parse error, and you do not catch it (it gets thrown
         * from the parser) the correct error message
         * gets displayed.
         */
        private fun initialise(
            currentToken: Token,
            expectedTokenSequences: Array<IntArray>,
            tokenImage: Array<String>
        ): String {
            val expected = StringBuilder()
            var maxSize = 0
            for (i in expectedTokenSequences.indices) {
                if (maxSize < expectedTokenSequences[i].size) {
                    maxSize = expectedTokenSequences[i].size
                }
                for (j in expectedTokenSequences[i].indices) {
                    expected.append(tokenImage[expectedTokenSequences[i][j]]).append(' ')
                }
                if (expectedTokenSequences[i][expectedTokenSequences[i].size - 1] != 0) {
                    expected.append("...")
                }
                expected.append(EOL).append("    ")
            }
            var retval = "Encountered \""
            var tok: Token = currentToken.next!!
            for (i in 0..<maxSize) {
                if (i != 0) retval += " "
                if (tok.kind == 0) {
                    retval += tokenImage[0]
                    break
                }
                retval += " " + tokenImage[tok.kind]
                retval += " \""
                retval += add_escapes(tok.image!!)
                retval += " \""
                tok = tok.next!!
            }
            if (currentToken.next != null) {
                retval += "\" at line " + currentToken.next!!.beginLine + ", column " + currentToken.next!!.beginColumn
            }
            retval += ".$EOL"


            if (expectedTokenSequences.isEmpty()) {
                // Nothing to add here
            } else {
                retval += if (expectedTokenSequences.size == 1) {
                    "Was expecting:$EOL    "
                } else {
                    "Was expecting one of:$EOL    "
                }
                retval += expected.toString()
            }

            return retval
        }


        /**
         * Used to convert raw characters to their escaped version
         * when these raw version cannot be used as part of an ASCII
         * string literal.
         */
        fun add_escapes(str: String): String {
            val retval = StringBuilder()
            var ch: Char
            for (i in 0..<str.length) {
                when (str[i]) {
                    '\b' -> {
                        retval.append("\\b")
                        continue
                    }

                    '\t' -> {
                        retval.append("\\t")
                        continue
                    }

                    '\n' -> {
                        retval.append("\\n")
                        continue
                    }

                    '\u000C' -> { // '\f' is not a valid Kotlin character literal, so we use '\u000C'
                        retval.append("\\f")
                        continue
                    }

                    '\r' -> {
                        retval.append("\\r")
                        continue
                    }

                    '\"' -> {
                        retval.append("\\\"")
                        continue
                    }

                    '\'' -> {
                        retval.append("\\\'")
                        continue
                    }

                    '\\' -> {
                        retval.append("\\\\")
                        continue
                    }

                    else -> {
                        if ((str[i].also { ch = it }).code < 0x20 || ch.code > 0x7e) {
                            val s = "0000" + Int.toStringWithRadix(i = ch.digitToInt(16), radix = 16) /*ch.toString(16)*/
                            retval.append("\\u" + s.substring(s.length - 4, s.length))
                        } else {
                            retval.append(ch)
                        }
                        continue
                    }
                }
            }
            return retval.toString()
        }
    }
} /* (filtered)*/