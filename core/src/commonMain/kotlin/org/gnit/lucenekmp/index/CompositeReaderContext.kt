package org.gnit.lucenekmp.index


/** [IndexReaderContext] for [CompositeReader] instance.  */
class CompositeReaderContext private constructor(
    parent: CompositeReaderContext?,
    private val reader: CompositeReader,
    ordInParent: Int,
    docbaseInParent: Int,
    private val children: MutableList<IndexReaderContext>,
    leaves: MutableList<LeafReaderContext>?
) : IndexReaderContext(parent, ordInParent, docbaseInParent) {
    private val leaves: MutableList<LeafReaderContext> = leaves!!

    /**
     * Creates a [CompositeReaderContext] for intermediate readers that aren't top-level readers
     * in the current context
     */
    internal constructor(
        parent: CompositeReaderContext,
        reader: CompositeReader,
        ordInParent: Int,
        docbaseInParent: Int,
        children: MutableList<IndexReaderContext>
    ) : this(parent, reader, ordInParent, docbaseInParent, children, null)

    /**
     * Creates a [CompositeReaderContext] for top-level readers with parent set to `null
    ` *
     */
    internal constructor(
        reader: CompositeReader,
        children: MutableList<IndexReaderContext>,
        leaves: MutableList<LeafReaderContext>
    ) : this(null, reader, 0, 0, children, leaves)

    @Throws(UnsupportedOperationException::class)
    override fun leaves(): MutableList<LeafReaderContext> {
        if (!isTopLevel) throw UnsupportedOperationException("This is not a top-level context.")
        checkNotNull(leaves)
        return leaves
    }

    override fun children(): MutableList<IndexReaderContext> {
        return children
    }

    override fun reader(): CompositeReader {
        return reader
    }

    private class Builder(private val reader: CompositeReader) {
        private val leaves: MutableList<LeafReaderContext> = mutableListOf()
        private var leafDocBase = 0

        fun build(): CompositeReaderContext {
            return build(null, reader, 0, 0) as CompositeReaderContext
        }

        fun build(
            parent: CompositeReaderContext?, reader: IndexReader, ord: Int, docBase: Int
        ): IndexReaderContext {
            if (reader is LeafReader) {
                val atomic =
                    LeafReaderContext(parent, reader, ord, docBase, leaves.size, leafDocBase)
                leaves.add(atomic)
                leafDocBase += reader.maxDoc()
                return atomic
            } else {
                val cr: CompositeReader = reader as CompositeReader
                val sequentialSubReaders: List<IndexReader> = cr.sequentialSubReaders
                val children: MutableList<IndexReaderContext> =
                    mutableListOf(
                        *kotlin.arrayOfNulls<IndexReaderContext>(
                            sequentialSubReaders.size
                        )
                    ) as MutableList<IndexReaderContext>

                val newParent: CompositeReaderContext = if (parent == null) {
                    CompositeReaderContext(cr, children, leaves)
                } else {
                    CompositeReaderContext(parent, cr, ord, docBase, children)
                }
                var newDocBase = 0
                var i = 0
                val c = sequentialSubReaders.size
                while (i < c) {
                    val r: IndexReader = sequentialSubReaders[i]
                    children[i] = build(newParent, r, i, newDocBase)
                    newDocBase += r.maxDoc()
                    i++
                }
                require(newDocBase == cr.maxDoc())
                return newParent
            }
        }
    }

    companion object {
        fun create(reader: CompositeReader): CompositeReaderContext {
            return Builder(reader).build()
        }
    }
}
