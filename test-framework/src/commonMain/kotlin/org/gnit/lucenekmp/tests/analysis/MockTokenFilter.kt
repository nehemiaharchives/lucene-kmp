package org.gnit.lucenekmp.tests.analysis

import okio.IOException
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.util.automaton.Automata
import org.gnit.lucenekmp.util.automaton.CharacterRunAutomaton
import org.gnit.lucenekmp.util.automaton.Operations

/**
 * A token filter for testing that removes terms accepted by a DFA.
 */
class MockTokenFilter(
    input: TokenStream,
    private val filter: CharacterRunAutomaton
) : TokenFilter(input) {

    companion object {
        /** Empty set of stopwords */
        val EMPTY_STOPSET = CharacterRunAutomaton(Automata.makeEmpty())

        /** Set of common English stopwords */
        val ENGLISH_STOPSET: CharacterRunAutomaton = CharacterRunAutomaton(
            Operations.determinize(
                Operations.union(
                    mutableListOf(
                        Automata.makeString("a"),
                        Automata.makeString("an"),
                        Automata.makeString("and"),
                        Automata.makeString("are"),
                        Automata.makeString("as"),
                        Automata.makeString("at"),
                        Automata.makeString("be"),
                        Automata.makeString("but"),
                        Automata.makeString("by"),
                        Automata.makeString("for"),
                        Automata.makeString("if"),
                        Automata.makeString("in"),
                        Automata.makeString("into"),
                        Automata.makeString("is"),
                        Automata.makeString("it"),
                        Automata.makeString("no"),
                        Automata.makeString("not"),
                        Automata.makeString("of"),
                        Automata.makeString("on"),
                        Automata.makeString("or"),
                        Automata.makeString("such"),
                        Automata.makeString("that"),
                        Automata.makeString("the"),
                        Automata.makeString("their"),
                        Automata.makeString("then"),
                        Automata.makeString("there"),
                        Automata.makeString("these"),
                        Automata.makeString("they"),
                        Automata.makeString("this"),
                        Automata.makeString("to"),
                        Automata.makeString("was"),
                        Automata.makeString("will"),
                        Automata.makeString("with")
                    )
                ),
                Operations.DEFAULT_DETERMINIZE_WORK_LIMIT
            )
        )
    }

    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
    private val posIncrAtt: PositionIncrementAttribute =
        addAttribute(PositionIncrementAttribute::class)

    private var skippedPositions = 0

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        skippedPositions = 0
        while (input.incrementToken()) {
            if (!filter.run(termAtt.buffer(), 0, termAtt.length)) {
                posIncrAtt.setPositionIncrement(posIncrAtt.getPositionIncrement() + skippedPositions)
                return true
            }
            skippedPositions += posIncrAtt.getPositionIncrement()
        }
        return false
    }

    @Throws(IOException::class)
    override fun end() {
        super.end()
        posIncrAtt.setPositionIncrement(posIncrAtt.getPositionIncrement() + skippedPositions)
    }

    @Throws(IOException::class)
    override fun reset() {
        super.reset()
        skippedPositions = 0
    }
}