package org.gnit.lucenekmp.search

abstract class Query {
    /**
     * Prints a query to a string, with `field` assumed to be the default field and
     * omitted.
     */
    abstract fun toString(field: String?): String

    /** Prints a query to a string.  */
    override fun toString(): String {
        return toString("")
    }

    /**
     * Expert: Constructs an appropriate Weight implementation for this query.
     *
     *
     * Only implemented by primitive queries, which re-write to themselves.
     *
     * @param scoreMode How the produced scorers will be consumed.
     * @param boost The boost that is propagated by the parent queries.
     */
    @Throws(Exception::class /* IOException::class */)
    open fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
        throw UnsupportedOperationException("Query $this does not implement createWeight")
    }

    /**
     * Expert: called to re-write queries into primitive queries. For example, a PrefixQuery will be
     * rewritten into a BooleanQuery that consists of TermQuerys.
     *
     *
     * Callers are expected to call `rewrite` multiple times if necessary, until the
     * rewritten query is the same as the original query.
     *
     *
     * The rewrite process may be able to make use of IndexSearcher's executor and be executed in
     * parallel if the executor is provided.
     *
     * @see IndexSearcher.rewrite
     */
    @Throws(Exception::class /* IOException::class */)
    open fun rewrite(indexSearcher: IndexSearcher): Query {
        return this
    }

    /**
     * Recurse through the query tree, visiting any child queries.
     *
     * @param visitor a QueryVisitor to be called by each query in the tree
     */
    abstract fun visit(visitor: QueryVisitor)

    /**
     * Override and implement query instance equivalence properly in a subclass. This is required so
     * that [QueryCache] works properly.
     *
     *
     * Typically a query will be equal to another only if it's an instance of the same class and
     * its document-filtering properties are identical to those of the other instance. Utility methods
     * are provided for certain repetitive code.
     *
     * @see .sameClassAs
     * @see .classHash
     */
    abstract override fun equals(obj: Any?): Boolean

    /**
     * Override and implement query hash code properly in a subclass. This is required so that [ ] works properly.
     *
     * @see .equals
     */
    abstract override fun hashCode(): Int

    /**
     * Utility method to check whether `other` is not null and is exactly of the same class
     * as this object's class.
     *
     *
     * When this method is used in an implementation of [.equals], consider using
     * [.classHash] in the implementation of [.hashCode] to differentiate different
     * class.
     */
    protected fun sameClassAs(other: Any?): Boolean {
        return other != null && this::class == other::class
    }

    private val CLASS_NAME_HASH: Int = this::class.qualifiedName.hashCode()

    /**
     * Provides a constant integer for a given class, derived from the name of the class. The
     * rationale for not using just [Class.hashCode] is that classes may be assigned different
     * hash codes for each execution and we want hashes to be possibly consistent to facilitate
     * debugging.
     */
    protected fun classHash(): Int {
        return CLASS_NAME_HASH
    }
}

