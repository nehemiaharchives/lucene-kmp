package org.gnit.lucenekmp.tests.analysis

import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute

/**
 * Simple example of a filter that exercises LookaheadTokenFilter.
 */
class TrivialLookaheadFilter(input: TokenStream) : LookaheadTokenFilter<TrivialLookaheadFilter.TestPosition>(input) {
    private val termAtt = addAttribute(CharTermAttribute::class)

    private var insertUpto = -1

    class TestPosition : Position() {
        var fact: String? = null
    }

    override fun newPosition(): TestPosition = TestPosition()

    override fun incrementToken(): Boolean {
        if (positions.getMaxPos() < outputPos) {
            peekSentence()
        }
        return nextToken()
    }

    override fun reset() {
        super.reset()
        insertUpto = -1
    }

    override fun afterPosition() {
        if (insertUpto < outputPos) {
            insertToken()
            clearAttributes()
            termAtt.setEmpty()
            posIncAtt.setPositionIncrement(0)
            termAtt.append(positions.get(outputPos).fact!!)
            offsetAtt.setOffset(positions.get(outputPos).startOffset, positions.get(outputPos + 1).endOffset)
            insertUpto = outputPos
        }
    }

    private fun peekSentence() {
        val facts = mutableListOf<String>()
        var haveSentence = false
        do {
            if (peekToken()) {
                val term = termAtt.toString()
                facts.add("${term}-huh?")
                if (term == ".") haveSentence = true
            } else {
                haveSentence = true
            }
        } while (!haveSentence)

        for (x in 0 until facts.size) {
            positions.get(outputPos + x).fact = facts[x]
        }
    }
}
