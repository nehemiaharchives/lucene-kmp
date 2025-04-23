package org.gnit.lucenekmp.util.hnsw


import org.gnit.lucenekmp.index.KnnVectorValues
import org.gnit.lucenekmp.util.Bits
import kotlinx.io.IOException

/**
 * A [RandomVectorScorer] for scoring random nodes in batches against an abstract query. This
 * class isn't thread-safe and should be used by a single thread.
 */
interface RandomVectorScorer {
    /**
     * Returns the score between the query and the provided node.
     *
     * @param node a random node in the graph
     * @return the computed score
     */
    @Throws(IOException::class)
    fun score(node: Int): Float

    /**
     * @return the maximum possible ordinal for this scorer
     */
    fun maxOrd(): Int

    /**
     * Translates vector ordinal to the correct document ID. By default, this is an identity function.
     *
     * @param ord the vector ordinal
     * @return the document Id for that vector ordinal
     */
    fun ordToDoc(ord: Int): Int {
        return ord
    }

    /**
     * Returns the [Bits] representing live documents. By default, this is an identity function.
     *
     * @param acceptDocs the accept docs
     * @return the accept docs
     */
    fun getAcceptOrds(acceptDocs: Bits): Bits? {
        return acceptDocs
    }

    /** Creates a default scorer for random access vectors.  */
    open class AbstractRandomVectorScorer(private val values: KnnVectorValues) : RandomVectorScorer {

        override fun score(node: Int): Float {
            TODO("Not yet implemented")
        }

        override fun maxOrd(): Int {
            return values.size()
        }

        override fun ordToDoc(ord: Int): Int {
            return values.ordToDoc(ord)
        }

        override fun getAcceptOrds(acceptDocs: Bits): Bits? {
            return values.getAcceptOrds(acceptDocs)
        }
    }
}
