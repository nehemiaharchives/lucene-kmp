package org.gnit.lucenekmp.index

import kotlinx.io.IOException
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.util.Bits


/**
 * This class abstracts addressing of document vector values indexed as [KnnFloatVectorField]
 * or [KnnByteVectorField].
 *
 * @lucene.experimental
 */
abstract class KnnVectorValues {
    /** Return the dimension of the vectors  */
    abstract fun dimension(): Int

    /**
     * Return the number of vectors for this field.
     *
     * @return the number of vectors returned by this iterator
     */
    abstract fun size(): Int

    /**
     * Return the docid of the document indexed with the given vector ordinal. This default
     * implementation returns the argument and is appropriate for dense values implementations where
     * every doc has a single value.
     */
    fun ordToDoc(ord: Int): Int {
        return ord
    }

    /**
     * Creates a new copy of this [KnnVectorValues]. This is helpful when you need to access
     * different values at once, to avoid overwriting the underlying vector returned.
     */
    @Throws(IOException::class)
    abstract fun copy(): KnnVectorValues

    val vectorByteLength: Int
        /** Returns the vector byte length, defaults to dimension multiplied by float byte size  */
        get() = dimension() * this.encoding!!.byteSize

    /** The vector encoding of these values.  */
    abstract val encoding: VectorEncoding

    /** Returns a Bits accepting docs accepted by the argument and having a vector value  */
    fun getAcceptOrds(acceptDocs: Bits): Bits {
        // FIXME: change default to return acceptDocs and provide this impl
        // somewhere more specialized (in every non-dense impl).
        /*if (acceptDocs == null) {
            return null
        }*/
        return object : Bits {
            override fun get(index: Int): Boolean {
                return acceptDocs.get(ordToDoc(index))
            }

            override fun length(): Int {
                return size()
            }
        }
    }

    /** Create an iterator for this instance.  */
    open fun iterator(): DocIndexIterator {
        throw UnsupportedOperationException()
    }

    /**
     * A DocIdSetIterator that also provides an index() method tracking a distinct ordinal for a
     * vector associated with each doc.
     */
    abstract class DocIndexIterator : DocIdSetIterator() {
        /** return the value index (aka "ordinal" or "ord") corresponding to the current doc  */
        abstract fun index(): Int
    }

    /**
     * Creates an iterator for instances where every doc has a value, and the value ordinals are equal
     * to the docids.
     */
    protected fun createDenseIterator(): DocIndexIterator {
        return object : DocIndexIterator() {
            var doc: Int = -1

            override fun docID(): Int {
                return doc
            }

            override fun index(): Int {
                return doc
            }

            @Throws(IOException::class)
            override fun nextDoc(): Int {
                if (doc >= size() - 1) {
                    return NO_MORE_DOCS.also { doc = it }
                } else {
                    return ++doc
                }
            }

            override fun advance(target: Int): Int {
                if (target >= size()) {
                    return NO_MORE_DOCS.also { doc = it }
                }
                return target.also { doc = it }
            }

            override fun cost(): Long {
                return size().toLong()
            }
        }
    }

    /**
     * Creates an iterator from this instance's ordinal-to-docid mapping which must be monotonic
     * (docid increases when ordinal does).
     */
    protected fun createSparseIterator(): DocIndexIterator {
        return object : DocIndexIterator() {
            private var ord = -1

            override fun docID(): Int {
                if (ord == -1) {
                    return -1
                }
                if (ord == NO_MORE_DOCS) {
                    return NO_MORE_DOCS
                }
                return ordToDoc(ord)
            }

            override fun index(): Int {
                return ord
            }

            @Throws(IOException::class)
            override fun nextDoc(): Int {
                if (ord >= size() - 1) {
                    ord = NO_MORE_DOCS
                } else {
                    ++ord
                }
                return docID()
            }

            @Throws(IOException::class)
            override fun advance(target: Int): Int {
                return slowAdvance(target)
            }

            override fun cost(): Long {
                return size().toLong()
            }
        }
    }

    companion object {
        /**
         * Creates an iterator from a DocIdSetIterator indicating which docs have values, and for which
         * ordinals increase monotonically with docid.
         */
        protected fun fromDISI(docsWithField: DocIdSetIterator): DocIndexIterator {
            return object : DocIndexIterator() {
                var ord: Int = -1

                override fun docID(): Int {
                    return docsWithField.docID()
                }

                override fun index(): Int {
                    return ord
                }

                @Throws(IOException::class)
                override fun nextDoc(): Int {
                    if (docID() == NO_MORE_DOCS) {
                        return NO_MORE_DOCS
                    }
                    ord++
                    return docsWithField.nextDoc()
                }

                @Throws(IOException::class)
                override fun advance(target: Int): Int {
                    return docsWithField.advance(target)
                }

                override fun cost(): Long {
                    return docsWithField.cost()
                }
            }
        }
    }
}
