package org.gnit.lucenekmp.util.hnsw


import org.gnit.lucenekmp.codecs.hnsw.HnswGraphProvider
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.KnnVectorValues
import org.gnit.lucenekmp.search.TaskExecutor
import org.gnit.lucenekmp.util.BitSet
import org.gnit.lucenekmp.util.FixedBitSet
import okio.IOException

/** This merger merges graph in a concurrent manner, by using [HnswConcurrentMergeBuilder]  */
class ConcurrentHnswMerger(
    fieldInfo: FieldInfo,
    scorerSupplier: RandomVectorScorerSupplier,
    M: Int,
    beamWidth: Int,
    private val taskExecutor: TaskExecutor,
    private val numWorker: Int
) : IncrementalHnswGraphMerger(fieldInfo, scorerSupplier, M, beamWidth) {

    @Throws(IOException::class)
    override fun createBuilder(mergedVectorValues: KnnVectorValues, maxOrd: Int): HnswBuilder {
        val graph: OnHeapHnswGraph
        var initializedNodes: BitSet? = null

        if (initReader == null) {
            graph = OnHeapHnswGraph(M, maxOrd)
        } else {
            val initializerGraph: HnswGraph? = (initReader as HnswGraphProvider).getGraph(fieldInfo.name)
            if (initializerGraph?.size() == 0) {
                graph = OnHeapHnswGraph(M, maxOrd)
            } else {
                initializedNodes = FixedBitSet(maxOrd)
                val oldToNewOrdinalMap: IntArray = getNewOrdMapping(mergedVectorValues, initializedNodes)
                graph = InitializedHnswGraphBuilder.initGraph(initializerGraph!!, oldToNewOrdinalMap, maxOrd)
            }
        }
        return HnswConcurrentMergeBuilder(
            taskExecutor, numWorker, scorerSupplier, beamWidth, graph, initializedNodes
        )
    }
}
