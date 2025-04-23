package org.gnit.lucenekmp.index

/**
 * An ordinal based [TermState]
 *
 * @lucene.experimental
 */
open class OrdTermState
/** Sole constructor.  */
    : TermState() {
    /** Term ordinal, i.e. its position in the full list of sorted terms.  */
    var ord: Long = 0

    override fun copyFrom(other: TermState) {
        require(other is OrdTermState) { "can not copy from " + other::class.qualifiedName }
        this.ord = (other as OrdTermState).ord
    }

    override fun toString(): String {
        return "OrdTermState ord=$ord"
    }
}
