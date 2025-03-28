package org.gnit.lucenekmp.index


/** [IndexReaderContext] for [CompositeReader] instance.  */
class CompositeReaderContext private constructor(
    parent: CompositeReaderContext?,
    reader: CompositeReader,
    ordInParent: Int,
    docbaseInParent: Int,
    children: MutableList<IndexReaderContext>,
    leaves: MutableList<LeafReaderContext>?
) : IndexReaderContext(parent!!, ordInParent, docbaseInParent) {
    private val children: MutableList<IndexReaderContext>
    private val leaves: MutableList<LeafReaderContext>
    private val reader: CompositeReader

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

    init {
        this.children = children
        this.leaves = leaves!!
        this.reader = reader
    }

    @Throws(UnsupportedOperationException::class)
    public override fun leaves(): MutableList<LeafReaderContext> {
        if (!isTopLevel) throw UnsupportedOperationException("This is not a top-level context.")
        checkNotNull(leaves)
        return leaves
    }

    public override fun children(): MutableList<IndexReaderContext> {
        return children
    }

    public override fun reader(): CompositeReader {
        return reader
    }

    private class Builder(reader: CompositeReader) {
        private val reader: CompositeReader
        private val leaves: MutableList<LeafReaderContext> = mutableListOf<LeafReaderContext>()
        private var leafDocBase = 0

        init {
            this.reader = reader
        }

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
                val sequentialSubReaders: MutableList<out IndexReader> = cr.sequentialSubReaders
                val children: MutableList<IndexReaderContext> =
                    mutableListOf<IndexReaderContext?>(
                        *kotlin.arrayOfNulls<IndexReaderContext>(
                            sequentialSubReaders.size
                        )
                    ) as MutableList<IndexReaderContext>
                val newParent: CompositeReaderContext
                if (parent == null) {
                    newParent = CompositeReaderContext(cr, children, leaves)
                } else {
                    newParent = CompositeReaderContext(parent, cr, ord, docBase, children)
                }
                var newDocBase = 0
                var i = 0
                val c = sequentialSubReaders.size
                while (i < c) {
                    val r: IndexReader = sequentialSubReaders.get(i)
                    children.set(i, build(newParent, r, i, newDocBase))
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
