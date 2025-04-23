package org.gnit.lucenekmp.codecs


import org.gnit.lucenekmp.index.OrdTermState
import org.gnit.lucenekmp.index.TermState

/**
 * Holds all state required for [PostingsReaderBase] to produce a [ ] without re-seeking the terms dict.
 *
 * @lucene.internal
 */
open class BlockTermState
/** Sole constructor. (For invocation by subclass constructors, typically implicit.)  */
protected constructor() : OrdTermState() {
    /** how many docs have this term  */
    var docFreq: Int = 0

    /** total number of occurrences of this term  */
    var totalTermFreq: Long = 0

    /** the term's ord in the current block  */
    var termBlockOrd: Int = 0

    /** fp into the terms dict primary file (_X.tim) that holds this term  */ // TODO: update BTR to nuke this
    var blockFilePointer: Long = 0

    override fun copyFrom(_other: TermState) {
        require(_other is BlockTermState) { "can not copy from " + _other::class.qualifiedName }
        val other = _other
        super.copyFrom(_other)
        docFreq = other.docFreq
        totalTermFreq = other.totalTermFreq
        termBlockOrd = other.termBlockOrd
        blockFilePointer = other.blockFilePointer
    }

    override fun toString(): String {
        return ("docFreq="
                + docFreq
                + " totalTermFreq="
                + totalTermFreq
                + " termBlockOrd="
                + termBlockOrd
                + " blockFP="
                + blockFilePointer)
    }
}
