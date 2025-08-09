package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.codePointAt
import org.gnit.lucenekmp.search.FuzzyTermsEnum.FuzzyTermsException
import org.gnit.lucenekmp.util.UnicodeUtil
import org.gnit.lucenekmp.util.automaton.CompiledAutomaton
import org.gnit.lucenekmp.util.automaton.LevenshteinAutomata
import org.gnit.lucenekmp.util.automaton.TooComplexToDeterminizeException
import org.gnit.lucenekmp.util.codePointCount
import kotlin.math.min

/**
 * Builds a set of CompiledAutomaton for fuzzy matching on a given term, with specified maximum edit
 * distance, fixed prefix and whether or not to allow transpositions.
 */
internal class FuzzyAutomatonBuilder(term: String, maxEdits: Int, prefixLength: Int, transpositions: Boolean) {
    private val term: String
    private val maxEdits: Int
    private val levBuilder: LevenshteinAutomata
    private val prefix: String
    val termLength: Int

    init {
        var prefixLength = prefixLength
        require(!(maxEdits < 0 || maxEdits > LevenshteinAutomata.MAXIMUM_SUPPORTED_DISTANCE)) {
            ("max edits must be 0.."
                    + LevenshteinAutomata.MAXIMUM_SUPPORTED_DISTANCE
                    + ", inclusive; got: "
                    + maxEdits)
        }
        require(prefixLength >= 0) { "prefixLength cannot be less than 0" }
        this.term = term
        this.maxEdits = maxEdits
        val codePoints = stringToUTF32(term)
        this.termLength = codePoints.size
        prefixLength = min(prefixLength, codePoints.size)
        val suffix = IntArray(codePoints.size - prefixLength)
        System.arraycopy(codePoints, prefixLength, suffix, 0, suffix.size)
        this.levBuilder = LevenshteinAutomata(
            suffix,
            Character.MAX_CODE_POINT,
            transpositions
        )
        this.prefix = UnicodeUtil.newString(codePoints, 0, prefixLength)
    }

    fun buildAutomatonSet(): Array<CompiledAutomaton> {
        val compiled: Array<CompiledAutomaton> =
            kotlin.arrayOfNulls<CompiledAutomaton>(maxEdits + 1) as Array<CompiledAutomaton>
        for (i in 0..maxEdits) {
            try {
                compiled[i] =
                    CompiledAutomaton(levBuilder.toAutomaton(i, prefix)!!, finite = true, simplify = false)
            } catch (e: TooComplexToDeterminizeException) {
                throw FuzzyTermsException(term, e)
            }
        }
        return compiled
    }

    fun buildMaxEditAutomaton(): CompiledAutomaton {
        try {
            return CompiledAutomaton(
                levBuilder.toAutomaton(maxEdits, prefix)!!,
                true,
                simplify = false
            )
        } catch (e: TooComplexToDeterminizeException) {
            throw FuzzyTermsException(term, e)
        }
    }

    companion object {
        private fun stringToUTF32(text: String): IntArray {
            val termText = IntArray(text.codePointCount(0, text.length))
            var cp: Int
            var i = 0
            var j = 0
            while (i < text.length) {
                cp = text.codePointAt(i)
                termText[j++] = cp
                i += Character.charCount(cp)
            }
            return termText
        }
    }
}
