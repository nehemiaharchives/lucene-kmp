package org.gnit.lucenekmp.util.hnsw

import org.gnit.lucenekmp.util.InfoStream
import kotlinx.io.IOException

/**
 * Interface for builder building the [OnHeapHnswGraph]
 *
 * @lucene.experimental
 */
interface HnswBuilder {
    /**
     * Adds all nodes to the graph up to the provided `maxOrd`.
     *
     * @param maxOrd The maximum ordinal (excluded) of the nodes to be added.
     */
    @Throws(IOException::class)
    fun build(maxOrd: Int): OnHeapHnswGraph

    /** Inserts a doc with vector value to the graph  */
    @Throws(IOException::class)
    fun addGraphNode(node: Int)

    /** Set info-stream to output debugging information  */
    fun setInfoStream(infoStream: InfoStream)

    val graph: OnHeapHnswGraph

    val completedGraph: OnHeapHnswGraph
}
