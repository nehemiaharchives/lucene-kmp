package org.gnit.lucenekmp.util.hnsw

import org.gnit.lucenekmp.codecs.KnnVectorsReader
import org.gnit.lucenekmp.index.KnnVectorValues
import org.gnit.lucenekmp.index.MergeState
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.InfoStream
import kotlinx.io.IOException

/**
 * Abstraction of merging multiple graphs into one on-heap graph
 *
 * @lucene.experimental
 */
interface HnswGraphMerger {
    /**
     * Adds a reader to the graph merger to record the state
     *
     * @param reader KnnVectorsReader to add to the merger
     * @param docMap MergeState.DocMap for the reader
     * @param liveDocs Bits representing live docs, can be null
     * @return this
     * @throws IOException If an error occurs while reading from the merge state
     */
    @Throws(IOException::class)
    fun addReader(reader: KnnVectorsReader, docMap: MergeState.DocMap, liveDocs: Bits): HnswGraphMerger

    /**
     * Merge and produce the on heap graph
     *
     * @param mergedVectorValues view of the vectors in the merged segment
     * @param infoStream optional info stream to set to builder
     * @param maxOrd max number of vectors that will be added to the graph
     * @return merged graph
     * @throws IOException during merge
     */
    @Throws(IOException::class)
    fun merge(mergedVectorValues: KnnVectorValues, infoStream: InfoStream, maxOrd: Int): OnHeapHnswGraph
}
