package org.gnit.lucenekmp.queryparser.classic

import org.gnit.lucenekmp.jdkport.toStringWithRadix


/** Token Manager Error.  */
class TokenMgrError : Error {
    /**
     * Indicates the reason why the exception is thrown. It will have
     * one of the above 4 values.
     */
    var errorCode: Int = 0

    override val message: String?
        /**
         * You can also modify the body of this method to customize your error messages.
         * For example, cases like LOOP_DETECTED and INVALID_LEXICAL_STATE are not
         * of end-users concern, so you can return something like :
         *
         * "Internal Error : Please file a bug report .... "
         *
         * from this method for such cases in the release version of your parser.
         */
        get() = super.message

    /*
  * Constructors of various flavors follow.
  */
    /** No arg constructor.  */
    constructor()

    /** Constructor with message and reason.  */
    constructor(message: String?, reason: Int) : super(message) {
        errorCode = reason
    }

    /** Full Constructor.  */
    constructor(
        EOFSeen: Boolean,
        lexState: Int,
        errorLine: Int,
        errorColumn: Int,
        errorAfter: String?,
        curChar: Int,
        reason: Int
    ) : this(
        LexicalErr(EOFSeen, lexState, errorLine, errorColumn, errorAfter, curChar), reason
    )

    companion object {
        /**
         * The version identifier for this Serializable class.
         * Increment only if the *serialized* form of the
         * class changes.
         */
        private const val serialVersionUID = 1L

        /*
   * Ordinals for various reasons why an Error of this type can be thrown.
   */
        /**
         * Lexical error occurred.
         */
        const val LEXICAL_ERROR: Int = 0

        /**
         * An attempt was made to create a second instance of a static token manager.
         */
        const val STATIC_LEXER_ERROR: Int = 1

        /**
         * Tried to change to an invalid lexical state.
         */
        const val INVALID_LEXICAL_STATE: Int = 2

        /**
         * Detected (and bailed out of) an infinite loop in the token manager.
         */
        const val LOOP_DETECTED: Int = 3

        /**
         * Replaces unprintable characters by their escaped (or unicode escaped)
         * equivalents in the given string
         */
        protected fun addEscapes(str: String): String {
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
                            val s = "0000" + Int.toStringWithRadix(i = ch.digitToInt(radix = 16), radix = 16)
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

        /**
         * Returns a detailed message for the Error when it is thrown by the
         * token manager to indicate a lexical error.
         * Parameters :
         * EOFSeen     : indicates if EOF caused the lexical error
         * lexState    : lexical state in which this error occurred
         * errorLine   : line number when the error occurred
         * errorColumn : column number when the error occurred
         * errorAfter  : prefix that was seen before this error occurred
         * curchar     : the offending character
         * Note: You can customize the lexical error message by modifying this method.
         */
        protected fun LexicalErr(
            EOFSeen: Boolean,
            lexState: Int,
            errorLine: Int,
            errorColumn: Int,
            errorAfter: String?,
            curChar: Int
        ): String {
            return ("Lexical error at line " +  //
                    errorLine + ", column " +  //
                    errorColumn + ".  Encountered: " +  //
                    (if (EOFSeen) "<EOF>" else ("'" + addEscapes(curChar.toString()) + "' (" + curChar + "),")) +  //
                    (if (errorAfter == null || errorAfter.isEmpty()) "" else " after prefix \"" + addEscapes(
                        errorAfter
                    ) + "\"")) +  //
                    (if (lexState == 0) "" else " (in lexical state $lexState)")
        }
    }
} /* (filtered)*/

