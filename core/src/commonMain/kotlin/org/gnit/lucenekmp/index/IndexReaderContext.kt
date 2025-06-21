package org.gnit.lucenekmp.index


/**
 * A struct-like class that represents a hierarchical relationship between [IndexReader]
 * instances.
 */
abstract class IndexReaderContext internal constructor(
    /** The reader context for this reader's immediate parent, or null if none  */
    val parent: CompositeReaderContext,
    /** the ord for this reader in the parent, `0` if parent is null  */
    val ordInParent: Int,
    /** the doc base for this reader in the parent, `0` if parent is null  */
    val docBaseInParent: Int
) {
    /**
     * `true` if this context struct represents the top level reader within the hierarchical
     * context
     */
    val isTopLevel: Boolean

    // An object that uniquely identifies this context without referencing
    // segments. The goal is to make it fine to have references to this
    // identity object, even after the index reader has been closed
    val identity: Any = Any()

    init {
        this.isTopLevel = parent == null
    }

    /**
     * Expert: Return an [Object] that uniquely identifies this context. The returned object
     * does neither reference this [IndexReaderContext] nor the wrapped [IndexReader].
     *
     * @lucene.experimental
     */
    fun id(): Any {
        return identity
    }

    /** Returns the [IndexReader], this context represents.  */
    abstract fun reader(): IndexReader

    /**
     * Returns the context's leaves if this context is a top-level context. For convenience, if this
     * is an [LeafReaderContext] this returns itself as the only leaf, and it will never return
     * a null value.
     *
     *
     * Note: this is convenience method since leaves can always be obtained by walking the context
     * tree using [.children].
     *
     * @throws UnsupportedOperationException if this is not a top-level context.
     * @see .children
     */
    @Throws(UnsupportedOperationException::class)
    abstract fun leaves(): MutableList<LeafReaderContext>

    /**
     * Returns the context's children iff this context is a composite context otherwise `null
    ` * .
     */
    abstract fun children(): MutableList<IndexReaderContext>?
}
