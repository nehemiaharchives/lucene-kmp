package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.index.Term
import okio.IOException

/** Position of a term in a document that takes into account the term offset within the phrase.  */
class PhrasePositions(
    // stream of docs & positions
    val postings: PostingsEnum,
    o: Int,
    // unique across all PhrasePositions instances
    val ord: Int,
    // for repetitions initialization
    val terms: Array<Term>
) {
    var position: Int = 0 // position in doc
    var count: Int = 0 // remaining pos in this doc
    var offset: Int = o // position in phrase
    var next: PhrasePositions? = null // used to make lists
    var rptGroup: Int = -1 // >=0 indicates that this is a repeating PP
    var rptInd: Int = 0 // index in the rptGroup

    @Throws(IOException::class)
    fun firstPosition() {
        count = postings.freq() // read first pos
        nextPosition()
    }

    /**
     * Go to next location of this term current document, and set `position` as `
     * location - offset`, so that a matching exact phrase is easily identified when all
     * PhrasePositions have exactly the same `position`.
     */
    @Throws(IOException::class)
    fun nextPosition(): Boolean {
        if (count-- > 0) { // read subsequent pos's
            position = postings.nextPosition() - offset
            return true
        } else {
            return false
        }
    }

    /** for debug purposes  */
    override fun toString(): String {
        var s = "o:$offset p:$position c:$count"
        if (rptGroup >= 0) {
            s += " rpt:$rptGroup,i$rptInd"
        }
        return s
    }
}
