package org.gnit.lucenekmp.analysis.miscellaneous

import org.gnit.lucenekmp.analysis.TokenStream

/** An always exhausted token stream. */
class EmptyTokenStream : TokenStream() {
    override fun incrementToken(): Boolean {
        return false
    }
}
