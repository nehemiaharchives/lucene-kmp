package org.gnit.lucenekmp.util.hnsw


import kotlinx.coroutines.runBlocking
import org.gnit.lucenekmp.search.DocIdSetIterator.Companion.NO_MORE_DOCS
import org.gnit.lucenekmp.search.TaskExecutor
import org.gnit.lucenekmp.util.BitSet
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.InfoStream
import org.gnit.lucenekmp.util.hnsw.HnswGraphBuilder.Companion.HNSW_COMPONENT
import org.gnit.lucenekmp.jdkport.AtomicInteger
import kotlinx.io.IOException
import org.gnit.lucenekmp.jdkport.Callable
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.getAndAdd
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.min

/**
 * A graph builder that manages multiple workers, it only supports adding the whole graph all at
 * once. It will spawn a thread for each worker and the workers will pick the work in batches.
 */
@OptIn(ExperimentalAtomicApi::class)
class HnswConcurrentMergeBuilder(
    private val taskExecutor: TaskExecutor,
    numWorker: Int,
    scorerSupplier: RandomVectorScorerSupplier,
    beamWidth: Int,
    hnsw: OnHeapHnswGraph,
    initializedNodes: BitSet?
) : HnswBuilder {
    private val workers: Array<ConcurrentMergeWorker?>
    private val hnswLock: HnswLock
    private var infoStream: InfoStream = InfoStream.default
    private var frozen = false

    init {
        val workProgress: AtomicInteger = AtomicInteger(0)
        workers = kotlin.arrayOfNulls<ConcurrentMergeWorker>(numWorker)
        hnswLock = HnswLock()
        for (i in 0..<numWorker) {
            workers[i] =
                ConcurrentMergeWorker(
                    scorerSupplier.copy(),
                    beamWidth,
                    HnswGraphBuilder.randSeed,
                    hnsw,
                    hnswLock,
                    initializedNodes,
                    workProgress
                )
        }
    }

    @Throws(IOException::class)
    override fun build(maxOrd: Int): OnHeapHnswGraph {
        check(!frozen) { "graph has already been built" }
        if (infoStream.isEnabled(HNSW_COMPONENT)) {
            infoStream.message(
                HNSW_COMPONENT,
                "build graph from " + maxOrd + " vectors, with " + workers.size + " workers"
            )
        }
        val futures: MutableList<Callable<Nothing?>> =
            mutableListOf()
        for (i in workers.indices) {
            val finalI = i
            futures.add(
                Callable {
                    workers[finalI]!!.run(maxOrd)
                    null
                })
        }
        runBlocking { taskExecutor.invokeAll(futures) }
        return this.completedGraph
    }

    @Throws(IOException::class)
    override fun addGraphNode(node: Int) {
        throw UnsupportedOperationException("This builder is for merge only")
    }

    override fun setInfoStream(infoStream: InfoStream) {
        this.infoStream = infoStream
        for (worker in workers) {
            worker!!.setInfoStream(infoStream)
        }
    }

    @get:Throws(IOException::class)
    override val completedGraph: OnHeapHnswGraph
        get() {
            if (!frozen) {
                // should already have been called in build(), but just in case
                finish()
                frozen = true
            }
            return this.graph
        }

    @Throws(IOException::class)
    private fun finish() {
        workers[0]!!.finish()
    }

    override val graph: OnHeapHnswGraph
        get() = workers[0]!!.graph

    /* test only for now */
    fun setBatchSize(newSize: Int) {
        for (worker in workers) {
            worker!!.batchSize = newSize
        }
    }

    private class ConcurrentMergeWorker(
        scorerSupplier: RandomVectorScorerSupplier,
        beamWidth: Int,
        seed: Long,
        hnsw: OnHeapHnswGraph,
        hnswLock: HnswLock,
        private val initializedNodes: BitSet?,
        /**
         * A common AtomicInteger shared among all workers, used for tracking what's the next vector to
         * be added to the graph.
         */
        private val workProgress: AtomicInteger
    ) : HnswGraphBuilder(
        scorerSupplier,
        beamWidth,
        seed,
        hnsw,
        hnswLock,
        MergeSearcher(
            NeighborQueue(beamWidth, true), hnswLock, FixedBitSet(hnsw.maxNodeId() + 1)
        )
    ) {

        var batchSize = DEFAULT_BATCH_SIZE
        private val scorer: UpdateableRandomVectorScorer = scorerSupplier.scorer()

        /**
         * This method first try to "reserve" part of work by calling [.getStartPos] and then
         * calling [.addVectors] to actually add the nodes to the graph. By doing this
         * we are able to dynamically allocate the work to multiple workers and try to make all of them
         * finishing around the same time.
         */
        @Throws(IOException::class)
        fun run(maxOrd: Int) {
            var start = getStartPos(maxOrd)
            var end: Int
            while (start != -1) {
                end = min(maxOrd, start + batchSize)
                addVectors(start, end)
                start = getStartPos(maxOrd)
            }
        }

        /** Reserve the work by atomically increment the [.workProgress]  */
        fun getStartPos(maxOrd: Int): Int {
            val start: Int = workProgress.getAndAdd(batchSize)
            return if (start < maxOrd) {
                start
            } else {
                -1
            }
        }

        @Throws(IOException::class)
        override fun addGraphNode(node: Int, scorer: UpdateableRandomVectorScorer) {
            if (initializedNodes != null && initializedNodes.get(node)) {
                return
            }
            super.addGraphNode(node, scorer)
        }

        @Throws(IOException::class)
        override fun addGraphNode(node: Int) {
            if (initializedNodes != null && initializedNodes.get(node)) {
                return
            }
            scorer.setScoringOrdinal(node)
            addGraphNode(node, scorer)
        }
    }

    /**
     * This searcher will obtain the lock and make a copy of neighborArray when seeking the graph such
     * that concurrent modification of the graph will not impact the search
     */
    private class MergeSearcher(candidates: NeighborQueue, private val hnswLock: HnswLock, visited: BitSet) :
        HnswGraphSearcher(
            candidates, visited
        ) {
        private var nodeBuffer: IntArray? = null
        private var upto = 0
        private var size = 0

        override fun graphSeek(graph: HnswGraph, level: Int, targetNode: Int) {
            val lock: Lock = hnswLock.read(level, targetNode)
            try {
                val neighborArray = (graph as OnHeapHnswGraph).getNeighbors(level, targetNode)
                if (nodeBuffer == null || nodeBuffer!!.size < neighborArray.size()) {
                    nodeBuffer = IntArray(neighborArray.size())
                }
                size = neighborArray.size()
                System.arraycopy(neighborArray.nodes(), 0, nodeBuffer!!, 0, size)
            } finally {
                lock.unlock()
            }
            upto = -1
        }

        override fun graphNextNeighbor(graph: HnswGraph): Int {
            if (++upto < size) {
                return nodeBuffer!![upto]
            }
            return NO_MORE_DOCS
        }
    }

    companion object {
        private const val DEFAULT_BATCH_SIZE = 2048 // number of vectors the worker handles sequentially at one batch
    }
}
