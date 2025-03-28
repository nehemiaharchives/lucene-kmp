package org.gnit.lucenekmp.index

/** [IndexReaderContext] for [LeafReader] instances.  */
class LeafReaderContext internal constructor(
    parent: CompositeReaderContext?,
    private val reader: LeafReader,

    /** The reader's ord in the top-level's leaves array  */
    var ord: Int,
    /** The reader's absolute doc base  */
    var docBase: Int,

    leafOrd: Int = 0,
    leafDocBase: Int = 0,

) : IndexReaderContext(parent!!, ord, docBase) {
    private val leaves: MutableList<LeafReaderContext>?

    /** Creates a new [LeafReaderContext]  */
    init {
        this.ord = leafOrd
        this.docBase = leafDocBase
        this.leaves = if (isTopLevel) mutableListOf<LeafReaderContext>(this) else null
    }

    internal constructor(leafReader: LeafReader) : this(null, leafReader, 0, 0)

    override fun leaves(): MutableList<LeafReaderContext> {
        if (!isTopLevel) {
            throw UnsupportedOperationException("This is not a top-level context.")
        }
        checkNotNull(leaves)
        return leaves
    }

    override fun children(): MutableList<IndexReaderContext>? {
        return null
    }

    override fun reader(): LeafReader {
        return reader
    }

    override fun toString(): String {
        return "LeafReaderContext($reader docBase=$docBase ord=$ord)"
    }
}
