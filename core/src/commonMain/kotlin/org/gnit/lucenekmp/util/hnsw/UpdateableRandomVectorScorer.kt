package org.gnit.lucenekmp.util.hnsw


import org.gnit.lucenekmp.index.KnnVectorValues
import org.gnit.lucenekmp.util.Bits
import kotlinx.io.IOException

/**
 * Just like a [RandomVectorScorer] but allows the scoring ordinal to be changed. Useful
 * during indexing operations
 *
 * @lucene.internal
 */
interface UpdateableRandomVectorScorer : RandomVectorScorer {
    /**
     * Changes the scoring ordinal to the given node. If the same scorer object is being used
     * continually, this can be used to avoid creating a new scorer for each node.
     *
     * @param node the node to score against
     * @throws IOException if an exception occurs initializing the scorer for the given node
     */
    @Throws(IOException::class)
    fun setScoringOrdinal(node: Int)

    /** Creates a default scorer for random access vectors.  */
    class AbstractUpdateableRandomVectorScorer(private val values: KnnVectorValues) : UpdateableRandomVectorScorer {
        override fun score(node: Int): Float {
            TODO("Not yet implemented")
        }

        override fun maxOrd(): Int {
            return values.size()
        }

        override fun ordToDoc(ord: Int): Int {
            return values.ordToDoc(ord)
        }

        override fun getAcceptOrds(acceptDocs: Bits): Bits {
            return values.getAcceptOrds(acceptDocs)
        }

        override fun setScoringOrdinal(node: Int) {
            TODO("Not yet implemented")
        }
    }
}
