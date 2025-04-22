package org.gnit.lucenekmp.codecs.lucene99


import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.codecs.KnnFieldVectorsWriter
import org.gnit.lucenekmp.codecs.KnnVectorsWriter
import org.gnit.lucenekmp.codecs.KnnVectorsWriter.MergedVectorValues.hasVectorValues
import org.gnit.lucenekmp.codecs.hnsw.FlatFieldVectorsWriter
import org.gnit.lucenekmp.codecs.hnsw.FlatVectorsScorer
import org.gnit.lucenekmp.codecs.hnsw.FlatVectorsWriter
import org.gnit.lucenekmp.codecs.lucene99.Lucene99HnswVectorsFormat.Companion.DIRECT_MONOTONIC_BLOCK_SHIFT
import org.gnit.lucenekmp.codecs.lucene99.Lucene99HnswVectorsReader.Companion.SIMILARITY_FUNCTIONS
import org.gnit.lucenekmp.index.ByteVectorValues
import org.gnit.lucenekmp.index.DocsWithFieldSet
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.FloatVectorValues
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.KnnVectorValues
import org.gnit.lucenekmp.index.MergeState
import org.gnit.lucenekmp.index.SegmentWriteState
import org.gnit.lucenekmp.index.Sorter
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.search.TaskExecutor
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.InfoStream
import org.gnit.lucenekmp.util.RamUsageEstimator
import org.gnit.lucenekmp.util.hnsw.CloseableRandomVectorScorerSupplier
import org.gnit.lucenekmp.util.hnsw.ConcurrentHnswMerger
import org.gnit.lucenekmp.util.hnsw.HnswGraph
import org.gnit.lucenekmp.util.hnsw.HnswGraph.NodesIterator
import org.gnit.lucenekmp.util.hnsw.HnswGraphBuilder
import org.gnit.lucenekmp.util.hnsw.HnswGraphMerger
import org.gnit.lucenekmp.util.hnsw.IncrementalHnswGraphMerger
import org.gnit.lucenekmp.util.hnsw.NeighborArray
import org.gnit.lucenekmp.util.hnsw.OnHeapHnswGraph
import org.gnit.lucenekmp.util.hnsw.RandomVectorScorerSupplier
import org.gnit.lucenekmp.util.hnsw.UpdateableRandomVectorScorer
import org.gnit.lucenekmp.util.packed.DirectMonotonicWriter
import kotlinx.io.IOException
import org.gnit.lucenekmp.index.VectorEncoding
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.Math

/**
 * Writes vector values and knn graphs to index segments.
 *
 * @lucene.experimental
 */
class Lucene99HnswVectorsWriter(
    state: SegmentWriteState,
    private val M: Int,
    private val beamWidth: Int,
    private val flatVectorWriter: FlatVectorsWriter,
    private val numMergeWorkers: Int,
    private val mergeExec: TaskExecutor?
) : KnnVectorsWriter() {
    private val segmentWriteState: SegmentWriteState = state
    private val meta: IndexOutput?
    private val vectorIndex: IndexOutput?

    private val fields: MutableList<FieldWriter<*>> = ArrayList<FieldWriter<*>>()
    private var finished = false

    init {
        val metaFileName: String =
            IndexFileNames.segmentFileName(
                state.segmentInfo.name, state.segmentSuffix, Lucene99HnswVectorsFormat.META_EXTENSION
            )

        val indexDataFileName: String =
            IndexFileNames.segmentFileName(
                state.segmentInfo.name,
                state.segmentSuffix,
                Lucene99HnswVectorsFormat.VECTOR_INDEX_EXTENSION
            )

        var success = false
        try {
            meta = state.directory.createOutput(metaFileName, state.context)
            vectorIndex = state.directory.createOutput(indexDataFileName, state.context)

            CodecUtil.writeIndexHeader(
                meta,
                Lucene99HnswVectorsFormat.META_CODEC_NAME,
                Lucene99HnswVectorsFormat.VERSION_CURRENT,
                state.segmentInfo.getId(),
                state.segmentSuffix
            )
            CodecUtil.writeIndexHeader(
                vectorIndex,
                Lucene99HnswVectorsFormat.VECTOR_INDEX_CODEC_NAME,
                Lucene99HnswVectorsFormat.VERSION_CURRENT,
                state.segmentInfo.getId(),
                state.segmentSuffix
            )
            success = true
        } finally {
            if (!success) {
                IOUtils.closeWhileHandlingException(this)
            }
        }
    }

    @Throws(IOException::class)
    override fun addField(fieldInfo: FieldInfo): KnnFieldVectorsWriter<*> {
        val newField =
            FieldWriter.Companion.create(
                flatVectorWriter.getFlatVectorScorer(),
                flatVectorWriter.addField(fieldInfo),
                fieldInfo,
                M,
                beamWidth,
                segmentWriteState.infoStream
            )
        fields.add(newField)
        return newField
    }

    @Throws(IOException::class)
    override fun flush(maxDoc: Int, sortMap: Sorter.DocMap?) {
        flatVectorWriter.flush(maxDoc, sortMap)
        for (field in fields) {
            if (sortMap == null) {
                writeField(field)
            } else {
                writeSortingField(field, sortMap)
            }
        }
    }

    @Throws(IOException::class)
    override fun finish() {
        check(!finished) { "already finished" }
        finished = true
        flatVectorWriter.finish()

        if (meta != null) {
            // write end of fields marker
            meta.writeInt(-1)
            CodecUtil.writeFooter(meta)
        }
        if (vectorIndex != null) {
            CodecUtil.writeFooter(vectorIndex)
        }
    }

    override fun ramBytesUsed(): Long {
        var total = SHALLOW_RAM_BYTES_USED
        for (field in fields) {
            // the field tracks the delegate field usage
            total += field.ramBytesUsed()
        }
        return total
    }

    @Throws(IOException::class)
    private fun writeField(fieldData: FieldWriter<*>) {
        // write graph
        val vectorIndexOffset: Long = vectorIndex!!.getFilePointer()
        val graph: OnHeapHnswGraph = fieldData.graph!!
        val graphLevelNodeOffsets = writeGraph(graph)
        val vectorIndexLength: Long = vectorIndex.getFilePointer() - vectorIndexOffset

        writeMeta(
            fieldData.fieldInfo,
            vectorIndexOffset,
            vectorIndexLength,
            fieldData.docsWithFieldSet.cardinality(),
            graph,
            graphLevelNodeOffsets
        )
    }

    @Throws(IOException::class)
    private fun writeSortingField(fieldData: FieldWriter<*>, sortMap: Sorter.DocMap) {
        val ordMap =
            IntArray(fieldData.docsWithFieldSet.cardinality()) // new ord to old ord
        val oldOrdMap =
            IntArray(fieldData.docsWithFieldSet.cardinality()) // old ord to new ord

        mapOldOrdToNewOrd(fieldData.docsWithFieldSet, sortMap, oldOrdMap, ordMap, null)
        // write graph
        val vectorIndexOffset: Long = vectorIndex!!.getFilePointer()
        val graph: OnHeapHnswGraph? = fieldData.graph
        val graphLevelNodeOffsets: Array<IntArray?> =
            if (graph == null) kotlin.arrayOfNulls(0) else kotlin.arrayOfNulls(graph.numLevels())
        val mockGraph: HnswGraph? = reconstructAndWriteGraph(graph!!, ordMap, oldOrdMap, graphLevelNodeOffsets)
        val vectorIndexLength: Long = vectorIndex.getFilePointer() - vectorIndexOffset

        writeMeta(
            fieldData.fieldInfo,
            vectorIndexOffset,
            vectorIndexLength,
            fieldData.docsWithFieldSet.cardinality(),
            mockGraph,
            graphLevelNodeOffsets
        )
    }

    /**
     * Reconstructs the graph given the old and new node ids.
     *
     *
     * Additionally, the graph node connections are written to the vectorIndex.
     *
     * @param graph The current on heap graph
     * @param newToOldMap the new node ids indexed to the old node ids
     * @param oldToNewMap the old node ids indexed to the new node ids
     * @param levelNodeOffsets where to place the new offsets for the nodes in the vector index.
     * @return The graph
     * @throws IOException if writing to vectorIndex fails
     */
    @Throws(IOException::class)
    private fun reconstructAndWriteGraph(
        graph: OnHeapHnswGraph?, newToOldMap: IntArray, oldToNewMap: IntArray, levelNodeOffsets: Array<IntArray?>
    ): HnswGraph? {
        if (graph == null) return null

        val nodesByLevel: MutableList<IntArray?> = ArrayList(graph.numLevels())
        nodesByLevel.add(null)

        val maxOrd: Int = graph.size()
        val scratch = IntArray(graph.maxConn() * 2)
        val nodesOnLevel0: NodesIterator = graph.getNodesOnLevel(0)
        levelNodeOffsets[0] = IntArray(nodesOnLevel0.size())
        while (nodesOnLevel0.hasNext()) {
            val node: Int = nodesOnLevel0.nextInt()
            val neighbors: NeighborArray = graph.getNeighbors(0, newToOldMap[node])
            val offset: Long = vectorIndex!!.getFilePointer()
            reconstructAndWriteNeighbours(neighbors, oldToNewMap, scratch, maxOrd)
            levelNodeOffsets[0]!![node] = Math.toIntExact(vectorIndex.getFilePointer() - offset)
        }

        for (level in 1..<graph.numLevels()) {
            val nodesOnLevel: NodesIterator = graph.getNodesOnLevel(level)
            val newNodes = IntArray(nodesOnLevel.size())
            var n = 0
            while (nodesOnLevel.hasNext()) {
                newNodes[n] = oldToNewMap[nodesOnLevel.nextInt()]
                n++
            }
            Arrays.sort(newNodes)
            nodesByLevel.add(newNodes)
            levelNodeOffsets[level] = IntArray(newNodes.size)
            var nodeOffsetIndex = 0
            for (node in newNodes) {
                val neighbors: NeighborArray = graph.getNeighbors(level, newToOldMap[node])
                val offset: Long = vectorIndex!!.getFilePointer()
                reconstructAndWriteNeighbours(neighbors, oldToNewMap, scratch, maxOrd)
                levelNodeOffsets[level]!![nodeOffsetIndex++] =
                    Math.toIntExact(vectorIndex.getFilePointer() - offset)
            }
        }
        return object : HnswGraph() {
            override fun nextNeighbor(): Int {
                throw UnsupportedOperationException("Not supported on a mock graph")
            }

            override fun seek(level: Int, target: Int) {
                throw UnsupportedOperationException("Not supported on a mock graph")
            }

            override fun size(): Int {
                return graph.size()
            }

            override fun numLevels(): Int {
                return graph.numLevels()
            }

            override fun maxConn(): Int {
                return graph.maxConn()
            }

            override fun entryNode(): Int {
                throw UnsupportedOperationException("Not supported on a mock graph")
            }

            override fun neighborCount(): Int {
                throw UnsupportedOperationException("Not supported on a mock graph")
            }

            override fun getNodesOnLevel(level: Int): NodesIterator {
                return if (level == 0) {
                    graph.getNodesOnLevel(0)
                } else {
                    ArrayNodesIterator(nodesByLevel[level]!!, nodesByLevel[level]!!.size)
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun reconstructAndWriteNeighbours(
        neighbors: NeighborArray, oldToNewMap: IntArray, scratch: IntArray, maxOrd: Int
    ) {
        val size: Int = neighbors.size()
        // Destructively modify; it's ok we are discarding it after this
        val nnodes: IntArray = neighbors.nodes()
        for (i in 0..<size) {
            nnodes[i] = oldToNewMap[nnodes[i]]
        }
        Arrays.sort(nnodes, 0, size)
        var actualSize = 0
        if (size > 0) {
            scratch[0] = nnodes[0]
            actualSize = 1
        }
        // Now that we have sorted, do delta encoding to minimize the required bits to store the
        // information
        for (i in 1..<size) {
            require(nnodes[i] < maxOrd) { "node too large: " + nnodes[i] + ">=" + maxOrd }
            if (nnodes[i - 1] == nnodes[i]) {
                continue
            }
            scratch[actualSize++] = nnodes[i] - nnodes[i - 1]
        }
        // Write the size after duplicates are removed
        vectorIndex!!.writeVInt(actualSize)
        for (i in 0..<actualSize) {
            vectorIndex.writeVInt(scratch[i])
        }
    }

    @Throws(IOException::class)
    override fun mergeOneField(fieldInfo: FieldInfo, mergeState: MergeState) {
        val scorerSupplier: CloseableRandomVectorScorerSupplier =
            flatVectorWriter.mergeOneFieldToIndex(fieldInfo, mergeState)
        var success = false
        try {
            val vectorIndexOffset: Long = vectorIndex!!.getFilePointer()
            // build the graph using the temporary vector data
            // we use Lucene99HnswVectorsReader.DenseOffHeapVectorValues for the graph construction
            // doesn't need to know docIds
            // TODO: separate random access vector values from DocIdSetIterator
            var graph: OnHeapHnswGraph? = null
            var vectorIndexNodeOffsets: Array<IntArray?>? = null
            if (scorerSupplier.totalVectorCount() > 0) {
                // build graph
                val merger: HnswGraphMerger =
                    createGraphMerger(
                        fieldInfo,
                        scorerSupplier,
                        if (mergeState.intraMergeTaskExecutor == null)
                            null
                        else
                            TaskExecutor(mergeState.intraMergeTaskExecutor),
                        numMergeWorkers
                    )
                for (i in 0..<mergeState.liveDocs.size) {
                    if (hasVectorValues(mergeState.fieldInfos[i]!!, fieldInfo.name)) {
                        merger.addReader(
                            mergeState.knnVectorsReaders[i]!!, mergeState.docMaps!![i], mergeState.liveDocs[i]!!
                        )
                    }
                }
                val mergedVectorValues: KnnVectorValues = when (fieldInfo.getVectorEncoding()) {
                    VectorEncoding.BYTE -> MergedVectorValues.mergeByteVectorValues(fieldInfo, mergeState)

                    VectorEncoding.FLOAT32 -> MergedVectorValues.mergeFloatVectorValues(fieldInfo, mergeState)
                }
                graph =
                    merger.merge(
                        mergedVectorValues,
                        segmentWriteState.infoStream,
                        scorerSupplier.totalVectorCount()
                    )
                vectorIndexNodeOffsets = writeGraph(graph)
            }
            val vectorIndexLength: Long = vectorIndex.getFilePointer() - vectorIndexOffset
            writeMeta(
                fieldInfo,
                vectorIndexOffset,
                vectorIndexLength,
                scorerSupplier.totalVectorCount(),
                graph,
                vectorIndexNodeOffsets!!
            )
            success = true
        } finally {
            if (success) {
                IOUtils.close(scorerSupplier)
            } else {
                IOUtils.closeWhileHandlingException(scorerSupplier)
            }
        }
    }

    /**
     * @param graph Write the graph in a compressed format
     * @return The non-cumulative offsets for the nodes. Should be used to create cumulative offsets.
     * @throws IOException if writing to vectorIndex fails
     */
    @Throws(IOException::class)
    private fun writeGraph(graph: OnHeapHnswGraph?): Array<IntArray?> {
        if (graph == null) return Array(0) { IntArray(0) }
        // write vectors' neighbours on each level into the vectorIndex file
        val countOnLevel0: Int = graph.size()
        val offsets: Array<IntArray?> = kotlin.arrayOfNulls(graph.numLevels())
        val scratch = IntArray(graph.maxConn() * 2)
        for (level in 0..<graph.numLevels()) {
            val sortedNodes: IntArray = NodesIterator.getSortedNodes(graph.getNodesOnLevel(level))
            offsets[level] = IntArray(sortedNodes.size)
            var nodeOffsetId = 0
            for (node in sortedNodes) {
                val neighbors: NeighborArray = graph.getNeighbors(level, node)
                val size: Int = neighbors.size()
                // Write size in VInt as the neighbors list is typically small
                val offsetStart: Long = vectorIndex!!.getFilePointer()
                val nnodes: IntArray = neighbors.nodes()
                Arrays.sort(nnodes, 0, size)
                // Now that we have sorted, do delta encoding to minimize the required bits to store the
                // information
                var actualSize = 0
                if (size > 0) {
                    scratch[0] = nnodes[0]
                    actualSize = 1
                }
                for (i in 1..<size) {
                    require(nnodes[i] < countOnLevel0) { "node too large: " + nnodes[i] + ">=" + countOnLevel0 }
                    if (nnodes[i - 1] == nnodes[i]) {
                        continue
                    }
                    scratch[actualSize++] = nnodes[i] - nnodes[i - 1]
                }
                // Write the size after duplicates are removed
                vectorIndex.writeVInt(actualSize)
                for (i in 0..<actualSize) {
                    vectorIndex.writeVInt(scratch[i])
                }
                offsets[level]!![nodeOffsetId++] =
                    Math.toIntExact(vectorIndex.getFilePointer() - offsetStart)
            }
        }
        return offsets
    }

    @Throws(IOException::class)
    private fun writeMeta(
        field: FieldInfo,
        vectorIndexOffset: Long,
        vectorIndexLength: Long,
        count: Int,
        graph: HnswGraph?,
        graphLevelNodeOffsets: Array<IntArray?>
    ) {
        meta!!.writeInt(field.number)
        meta.writeInt(field.getVectorEncoding().ordinal)
        meta.writeInt(distFuncToOrd(field.getVectorSimilarityFunction()))
        meta.writeVLong(vectorIndexOffset)
        meta.writeVLong(vectorIndexLength)
        meta.writeVInt(field.getVectorDimension())
        meta.writeInt(count)
        // write graph nodes on each level
        if (graph == null) {
            meta.writeVInt(M)
            meta.writeVInt(0)
        } else {
            meta.writeVInt(graph.maxConn())
            meta.writeVInt(graph.numLevels())
            var valueCount: Long = 0
            for (level in 0..<graph.numLevels()) {
                val nodesOnLevel: NodesIterator = graph.getNodesOnLevel(level)
                valueCount += nodesOnLevel.size()
                if (level > 0) {
                    val nol = IntArray(nodesOnLevel.size())
                    val numberConsumed: Int = nodesOnLevel.consume(nol)
                    Arrays.sort(nol)
                    require(numberConsumed == nodesOnLevel.size())
                    meta.writeVInt(nol.size) // number of nodes on a level
                    for (i in nodesOnLevel.size() - 1 downTo 1) {
                        nol[i] -= nol[i - 1]
                    }
                    for (n in nol) {
                        require(n >= 0) { "delta encoding for nodes failed; expected nodes to be sorted" }
                        meta.writeVInt(n)
                    }
                } else {
                    require(nodesOnLevel.size() == count) { "Level 0 expects to have all nodes" }
                }
            }
            val start: Long = vectorIndex!!.getFilePointer()
            meta.writeLong(start)
            meta.writeVInt(DIRECT_MONOTONIC_BLOCK_SHIFT)
            val memoryOffsetsWriter: DirectMonotonicWriter =
                DirectMonotonicWriter.getInstance(
                    meta, vectorIndex, valueCount, DIRECT_MONOTONIC_BLOCK_SHIFT
                )
            var cumulativeOffsetSum: Long = 0
            for (levelOffsets in graphLevelNodeOffsets) {
                for (v in levelOffsets!!) {
                    memoryOffsetsWriter.add(cumulativeOffsetSum)
                    cumulativeOffsetSum += v.toLong()
                }
            }
            memoryOffsetsWriter.finish()
            meta.writeLong(vectorIndex.getFilePointer() - start)
        }
    }

    private fun createGraphMerger(
        fieldInfo: FieldInfo,
        scorerSupplier: RandomVectorScorerSupplier,
        parallelMergeTaskExecutor: TaskExecutor?,
        numParallelMergeWorkers: Int
    ): HnswGraphMerger {
        if (mergeExec != null) {
            return ConcurrentHnswMerger(
                fieldInfo, scorerSupplier, M, beamWidth, mergeExec, numMergeWorkers
            )
        }
        if (parallelMergeTaskExecutor != null) {
            return ConcurrentHnswMerger(
                fieldInfo,
                scorerSupplier,
                M,
                beamWidth,
                parallelMergeTaskExecutor,
                numParallelMergeWorkers
            )
        }
        return IncrementalHnswGraphMerger(fieldInfo, scorerSupplier, M, beamWidth)
    }

    @Throws(IOException::class)
    override fun close() {
        IOUtils.close(meta!!, vectorIndex!!, flatVectorWriter)
    }

    private class FieldWriter<T>(
        scorer: FlatVectorsScorer,
        flatFieldVectorsWriter: FlatFieldVectorsWriter<T>,
        val fieldInfo: FieldInfo,
        M: Int,
        beamWidth: Int,
        infoStream: InfoStream
    ) : KnnFieldVectorsWriter<T>() {
        private val hnswGraphBuilder: HnswGraphBuilder
        private var lastDocID = -1
        private var node = 0
        private val flatFieldVectorsWriter: FlatFieldVectorsWriter<T>
        private val scorer: UpdateableRandomVectorScorer

        init {
            val scorerSupplier: RandomVectorScorerSupplier =
                when (fieldInfo.getVectorEncoding()) {
                    VectorEncoding.BYTE -> scorer.getRandomVectorScorerSupplier(
                        fieldInfo.getVectorSimilarityFunction(),
                        ByteVectorValues.fromBytes(
                            flatFieldVectorsWriter.getVectors() as MutableList<ByteArray>,
                            fieldInfo.getVectorDimension()
                        )
                    )

                    VectorEncoding.FLOAT32 -> scorer.getRandomVectorScorerSupplier(
                        fieldInfo.getVectorSimilarityFunction(),
                        FloatVectorValues.fromFloats(
                            flatFieldVectorsWriter.getVectors() as MutableList<FloatArray>,
                            fieldInfo.getVectorDimension()
                        )
                    )
                }
            this.scorer = scorerSupplier.scorer()
            hnswGraphBuilder =
                HnswGraphBuilder.create(scorerSupplier, M, beamWidth, HnswGraphBuilder.randSeed)
            hnswGraphBuilder.setInfoStream(infoStream)
            this.flatFieldVectorsWriter = flatFieldVectorsWriter
        }

        @Throws(IOException::class)
        override fun addValue(docID: Int, vectorValue: T) {
            require(docID != lastDocID) {
                ("VectorValuesField \""
                        + fieldInfo.name
                        + "\" appears more than once in this document (only one value is allowed per field)")
            }
            flatFieldVectorsWriter.addValue(docID, vectorValue)
            scorer.setScoringOrdinal(node)
            hnswGraphBuilder.addGraphNode(node, scorer)
            node++
            lastDocID = docID
        }

        val docsWithFieldSet: DocsWithFieldSet
            get() = flatFieldVectorsWriter.getDocsWithFieldSet()

        override fun copyValue(vectorValue: T): T {
            throw UnsupportedOperationException()
        }

        @get:Throws(IOException::class)
        val graph: OnHeapHnswGraph?
            get() {
                require(flatFieldVectorsWriter.isFinished)
                return if (node > 0) {
                    hnswGraphBuilder.getCompletedGraph()
                } else {
                    null
                }
            }

        override fun ramBytesUsed(): Long {
            return (SHALLOW_SIZE
                    + flatFieldVectorsWriter.ramBytesUsed()
                    + hnswGraphBuilder.getGraph().ramBytesUsed())
        }

        companion object {
            private val SHALLOW_SIZE: Long = RamUsageEstimator.shallowSizeOfInstance(FieldWriter::class)

            @Throws(IOException::class)
            fun create(
                scorer: FlatVectorsScorer,
                flatFieldVectorsWriter: FlatFieldVectorsWriter<*>,
                fieldInfo: FieldInfo,
                M: Int,
                beamWidth: Int,
                infoStream: InfoStream
            ): FieldWriter<*> {
                return when (fieldInfo.getVectorEncoding()) {
                    VectorEncoding.BYTE -> FieldWriter(
                        scorer,
                        flatFieldVectorsWriter as FlatFieldVectorsWriter<ByteArray>,
                        fieldInfo,
                        M,
                        beamWidth,
                        infoStream
                    )

                    VectorEncoding.FLOAT32 -> FieldWriter(
                        scorer,
                        flatFieldVectorsWriter as FlatFieldVectorsWriter<FloatArray>,
                        fieldInfo,
                        M,
                        beamWidth,
                        infoStream
                    )
                }
            }
        }
    }

    companion object {
        private val SHALLOW_RAM_BYTES_USED: Long =
            RamUsageEstimator.shallowSizeOfInstance(Lucene99HnswVectorsWriter::class)

        fun distFuncToOrd(func: VectorSimilarityFunction): Int {
            for (i in 0..<SIMILARITY_FUNCTIONS.size) {
                if (SIMILARITY_FUNCTIONS[i]!! == func) {
                    return i.toByte().toInt()
                }
            }
            throw IllegalArgumentException("invalid distance function: $func")
        }
    }
}
