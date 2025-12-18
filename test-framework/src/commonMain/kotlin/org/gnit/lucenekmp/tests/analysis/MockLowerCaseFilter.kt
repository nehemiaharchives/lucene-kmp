package org.gnit.lucenekmp.tests.analysis

import okio.IOException
import org.gnit.lucenekmp.analysis.CharacterUtils
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute

/** A lowercasing [org.gnit.lucenekmp.analysis.TokenFilter]. */
class MockLowerCaseFilter(`in`: TokenStream) : TokenFilter(`in`) {
    private val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        if (input.incrementToken()) {
            CharacterUtils.toLowerCase(termAtt.buffer(), 0, termAtt.length)
            return true
        }
        return false
    }
}