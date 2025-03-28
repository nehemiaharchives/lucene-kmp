package org.gnit.lucenekmp.util.automaton

import kotlin.jvm.Transient


/** This exception is thrown when determinizing an automaton would require too much work.  */
class TooComplexToDeterminizeException : RuntimeException {
    /** Returns the automaton that caused this exception, if any.  */
    @Transient
    val automaton: Automaton?

    @Transient
    private val regExp: RegExp?

    /** Get the maximum allowed determinize effort.  */
    @Transient
    val determinizeWorkLimit: Int

    /** Use this constructor when the RegExp failed to convert to an automaton.  */
    constructor(regExp: RegExp, cause: TooComplexToDeterminizeException) : super(
        ("Determinizing "
                + regExp.originalString
                + " would require more than "
                + cause.determinizeWorkLimit
                + " effort."),
        cause
    ) {
        this.regExp = regExp
        this.automaton = cause.automaton
        this.determinizeWorkLimit = cause.determinizeWorkLimit
    }

    /** Use this constructor when the automaton failed to determinize.  */
    constructor(automaton: Automaton, determinizeWorkLimit: Int) : super(
        ("Determinizing automaton with "
                + automaton.numStates
                + " states and "
                + automaton.numTransitions
                + " transitions would require more than "
                + determinizeWorkLimit
                + " effort.")
    ) {
        this.automaton = automaton
        this.regExp = null
        this.determinizeWorkLimit = determinizeWorkLimit
    }

    /** Return the RegExp that caused this exception if any.  */
    fun getRegExp(): RegExp? {
        return regExp
    }
}
