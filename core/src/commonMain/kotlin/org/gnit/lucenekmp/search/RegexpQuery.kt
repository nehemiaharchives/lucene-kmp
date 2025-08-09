package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.util.automaton.Automaton
import org.gnit.lucenekmp.util.automaton.AutomatonProvider
import org.gnit.lucenekmp.util.automaton.Operations
import org.gnit.lucenekmp.util.automaton.RegExp

/**
 * A fast regular expression query based on the [org.apache.lucene.util.automaton] package.
 *
 *
 *  * Comparisons are [fast](http://tusker.org/regex/regex_benchmark.html)
 *  * The term dictionary is enumerated in an intelligent way, to avoid comparisons. See [       ] for more details.
 *
 *
 *
 * The supported syntax is documented in the [RegExp] class. Note this might be different
 * than other regular expression implementations. For some alternatives with different syntax, look
 * under the sandbox.
 *
 *
 * Note this query can be slow, as it needs to iterate over many terms. In order to prevent
 * extremely slow RegexpQueries, a Regexp term should not start with the expression `.*`
 *
 * @see RegExp
 *
 * @lucene.experimental
 */
class RegexpQuery
/**
 * Constructs a query for terms matching `term`.
 *
 * @param term regular expression.
 * @param syntaxFlags optional RegExp features from [RegExp]
 * @param matchFlags boolean 'or' of match behavior options such as case insensitivity
 * @param provider custom AutomatonProvider for named automata
 * @param determinizeWorkLimit maximum effort to spend while compiling the automaton from this
 * regexp. Set higher to allow more complex queries and lower to prevent memory exhaustion.
 * Use [Operations.DEFAULT_DETERMINIZE_WORK_LIMIT] as a decent default if you don't
 * otherwise know what to specify.
 * @param rewriteMethod the rewrite method to use to build the final query
 * @param doDeterminization whether do determinization to force the query to use DFA as
 * runAutomaton, if false, the query will not try to determinize the generated automaton from
 * regexp such that it might or might not be a DFA. In case it is an NFA, the query will
 * eventually use [org.apache.lucene.util.automaton.NFARunAutomaton] to execute. Notice
 * that [org.apache.lucene.util.automaton.NFARunAutomaton] is not thread-safe, so better
 * to avoid rewritten method like [.CONSTANT_SCORE_BLENDED_REWRITE] when searcher is
 * configured with an executor service
 */
/**
 * Constructs a query for terms matching `term`.
 *
 * @param term regular expression.
 * @param syntaxFlags optional RegExp features from [RegExp]
 * @param matchFlags boolean 'or' of match behavior options such as case insensitivity
 * @param provider custom AutomatonProvider for named automata
 * @param determinizeWorkLimit maximum effort to spend while compiling the automaton from this
 * regexp. Set higher to allow more complex queries and lower to prevent memory exhaustion.
 * Use [Operations.DEFAULT_DETERMINIZE_WORK_LIMIT] as a decent default if you don't
 * otherwise know what to specify.
 * @param rewriteMethod the rewrite method to use to build the final query
 */
(
    term: Term,
    syntaxFlags: Int,
    matchFlags: Int,
    provider: AutomatonProvider,
    determinizeWorkLimit: Int,
    rewriteMethod: RewriteMethod,
    doDeterminization: Boolean = true
) : AutomatonQuery(
    term,
    toAutomaton(
        RegExp(term.text(), syntaxFlags, matchFlags),
        determinizeWorkLimit,
        provider,
        doDeterminization
    ),
    false,
    rewriteMethod
) {
    /**
     * Constructs a query for terms matching `term`.
     *
     * @param term regular expression.
     * @param flags optional RegExp features from [RegExp]
     */
    /**
     * Constructs a query for terms matching `term`.
     *
     *
     * By default, all regular expression features are enabled.
     *
     * @param term regular expression.
     */
    constructor(term: Term, flags: Int = RegExp.ALL) : this(
        term,
        flags,
        DEFAULT_PROVIDER,
        Operations.DEFAULT_DETERMINIZE_WORK_LIMIT
    )

    /**
     * Constructs a query for terms matching `term`.
     *
     * @param term regular expression.
     * @param flags optional RegExp syntax features from [RegExp]
     * @param determinizeWorkLimit maximum effort to spend while compiling the automaton from this
     * regexp. Set higher to allow more complex queries and lower to prevent memory exhaustion.
     * Use [Operations.DEFAULT_DETERMINIZE_WORK_LIMIT] as a decent default if you don't
     * otherwise know what to specify.
     */
    constructor(term: Term, flags: Int, determinizeWorkLimit: Int) : this(
        term,
        flags,
        DEFAULT_PROVIDER,
        determinizeWorkLimit
    )

    /**
     * Constructs a query for terms matching `term`.
     *
     * @param term regular expression.
     * @param syntaxFlags optional RegExp syntax features from [RegExp] automaton for the regexp
     * can result in. Set higher to allow more complex queries and lower to prevent memory
     * exhaustion.
     * @param matchFlags boolean 'or' of match behavior options such as case insensitivity
     * @param determinizeWorkLimit maximum effort to spend while compiling the automaton from this
     * regexp. Set higher to allow more complex queries and lower to prevent memory exhaustion.
     * Use [Operations.DEFAULT_DETERMINIZE_WORK_LIMIT] as a decent default if you don't
     * otherwise know what to specify.
     */
    constructor(
        term: Term,
        syntaxFlags: Int,
        matchFlags: Int,
        determinizeWorkLimit: Int
    ) : this(
        term,
        syntaxFlags,
        matchFlags,
        DEFAULT_PROVIDER,
        determinizeWorkLimit,
        CONSTANT_SCORE_BLENDED_REWRITE
    )

    /**
     * Constructs a query for terms matching `term`.
     *
     * @param term regular expression.
     * @param syntaxFlags optional RegExp features from [RegExp]
     * @param provider custom AutomatonProvider for named automata
     * @param determinizeWorkLimit maximum effort to spend while compiling the automaton from this
     * regexp. Set higher to allow more complex queries and lower to prevent memory exhaustion.
     * Use [Operations.DEFAULT_DETERMINIZE_WORK_LIMIT] as a decent default if you don't
     * otherwise know what to specify.
     */
    constructor(
        term: Term,
        syntaxFlags: Int,
        provider: AutomatonProvider,
        determinizeWorkLimit: Int
    ) : this(
        term,
        syntaxFlags,
        0,
        provider,
        determinizeWorkLimit,
        CONSTANT_SCORE_BLENDED_REWRITE
    )

    val regexp: Term
        /** Returns the regexp of this query wrapped in a Term.  */
        get() = term

    /** Prints a user-readable version of this query.  */
    override fun toString(field: String?): String {
        val buffer = StringBuilder()
        if (term.field() != field) {
            buffer.append(term.field())
            buffer.append(":")
        }
        buffer.append('/')
        buffer.append(term.text())
        buffer.append('/')
        return buffer.toString()
    }

    companion object {
        /** A provider that provides no named automata  */
        val DEFAULT_PROVIDER: AutomatonProvider = object : AutomatonProvider {
            override fun getAutomaton(name: String): Automaton? {
                return null
            }
        }

        private fun toAutomaton(
            regexp: RegExp,
            determinizeWorkLimit: Int,
            provider: AutomatonProvider,
            doDeterminization: Boolean
        ): Automaton {
            return if (doDeterminization) {
                Operations.determinize(
                    regexp.toAutomaton(provider)!!,
                    determinizeWorkLimit
                )
            } else {
                regexp.toAutomaton(provider)!!
            }
        }
    }
}
