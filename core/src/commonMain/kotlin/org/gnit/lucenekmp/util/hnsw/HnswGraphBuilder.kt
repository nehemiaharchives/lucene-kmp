package org.gnit.lucenekmp.util.hnsw


import org.gnit.lucenekmp.search.DocIdSetIterator.Companion.NO_MORE_DOCS
import org.gnit.lucenekmp.search.KnnCollector
import org.gnit.lucenekmp.search.TopDocs
import org.gnit.lucenekmp.search.knn.KnnSearchStrategy
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.InfoStream
import org.gnit.lucenekmp.util.hnsw.HnswUtil.Component
import kotlinx.io.IOException
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import kotlin.time.TimeSource

/**
 * Builder for HNSW graph. See [HnswGraph] for a gloss on the algorithm and the meaning of the
 * hyper-parameters.
 */
open class HnswGraphBuilder protected constructor(
    scorerSupplier: RandomVectorScorerSupplier,
    beamWidth: Int,
    seed: Long,
    hnsw: OnHeapHnswGraph,
    hnswLock: HnswLock?,
    graphSearcher: HnswGraphSearcher
) : HnswBuilder {
    private val M: Int // max number of connections on upper layers
    private val ml: Double

    private val random: /*Splittable*/Random
    protected val scorerSupplier: RandomVectorScorerSupplier
    private val graphSearcher: HnswGraphSearcher
    private val entryCandidates: GraphBuilderKnnCollector // for upper levels of graph search
    private val beamCandidates: GraphBuilderKnnCollector
    // for levels of graph where we add the node

    protected val hnsw: OnHeapHnswGraph
    protected val hnswLock: HnswLock?

    private var infoStream: InfoStream = InfoStream.default
    private var frozen = false

    /**
     * Reads all the vectors from vector values, builds a graph connecting them by their dense
     * ordinals, using the given hyperparameter settings, and returns the resulting graph.
     *
     * @param scorerSupplier a supplier to create vector scorer from ordinals.
     * @param M – graph fanout parameter used to calculate the maximum number of connections a node
     * can have – M on upper layers, and M * 2 on the lowest level.
     * @param beamWidth the size of the beam search to use when finding nearest neighbors.
     * @param seed the seed for a random number generator used during graph construction. Provide this
     * to ensure repeatable construction.
     * @param graphSize size of graph, if unknown, pass in -1
     */
    protected constructor(
        scorerSupplier: RandomVectorScorerSupplier,
        M: Int,
        beamWidth: Int,
        seed: Long,
        graphSize: Int
    ) : this(scorerSupplier, beamWidth, seed, OnHeapHnswGraph(M, graphSize))

    protected constructor(
        scorerSupplier: RandomVectorScorerSupplier,
        beamWidth: Int,
        seed: Long,
        hnsw: OnHeapHnswGraph
    ) : this(
        scorerSupplier,
        beamWidth,
        seed,
        hnsw,
        null,
        HnswGraphSearcher(NeighborQueue(beamWidth, true), FixedBitSet(hnsw.size()))
    )

    /**
     * Reads all the vectors from vector values, builds a graph connecting them by their dense
     * ordinals, using the given hyperparameter settings, and returns the resulting graph.
     *
     * @param scorerSupplier a supplier to create vector scorer from ordinals.
     * @param beamWidth the size of the beam search to use when finding nearest neighbors.
     * @param seed the seed for a random number generator used during graph construction. Provide this
     * to ensure repeatable construction.
     * @param hnsw the graph to build, can be previously initialized
     */
    init {
        require(hnsw.maxConn() > 0) { "M (max connections) must be positive" }
        require(beamWidth > 0) { "beamWidth must be positive" }
        this.M = hnsw.maxConn()
        this.scorerSupplier = scorerSupplier
        // normalization factor for level generation; currently not configurable
        this.ml = if (M == 1) 1.0 else 1 / ln(1.0 * M)
        this.random = /*Splittable*/Random(seed)
        this.hnsw = hnsw
        this.hnswLock = hnswLock
        this.graphSearcher = graphSearcher
        entryCandidates = GraphBuilderKnnCollector(1)
        beamCandidates = GraphBuilderKnnCollector(beamWidth)
    }

    @Throws(IOException::class)
    override fun build(maxOrd: Int): OnHeapHnswGraph {
        check(!frozen) { "This HnswGraphBuilder is frozen and cannot be updated" }
        if (infoStream.isEnabled(HNSW_COMPONENT)) {
            infoStream.message(HNSW_COMPONENT, "build graph from " + maxOrd + " vectors")
        }
        addVectors(maxOrd)
        return this.completedGraph
    }

    override fun setInfoStream(infoStream: InfoStream) {
        this.infoStream = infoStream
    }

    override val completedGraph: OnHeapHnswGraph
        get() {
            if (!frozen) {
                finish()
            }
            return this.graph
        }

    override val graph: OnHeapHnswGraph
        get() = hnsw

    /** add vectors in range [minOrd, maxOrd)  */
    @Throws(IOException::class)
    protected fun addVectors(minOrd: Int, maxOrd: Int) {
        check(!frozen) { "This HnswGraphBuilder is frozen and cannot be updated" }
        val start = TimeSource.Monotonic.markNow()
        var t = start
        if (infoStream.isEnabled(HNSW_COMPONENT)) {
            infoStream.message(HNSW_COMPONENT, "addVectors [$minOrd $maxOrd)")
        }
        val scorer = scorerSupplier.scorer()
        for (node in minOrd..<maxOrd) {
            scorer.setScoringOrdinal(node)
            addGraphNode(node, scorer)
            if ((node % 10000 == 0) && infoStream.isEnabled(HNSW_COMPONENT)) {
                t = printGraphBuildStatus(node, start, t)
            }
        }
    }

    @Throws(IOException::class)
    private fun addVectors(maxOrd: Int) {
        addVectors(0, maxOrd)
    }

    @Throws(IOException::class)
    open fun addGraphNode(node: Int, scorer: UpdateableRandomVectorScorer) {
        check(!frozen) { "Graph builder is already frozen" }
        val nodeLevel = getRandomGraphLevel(ml, random)
        // first add nodes to all levels
        for (level in nodeLevel downTo 0) {
            hnsw.addNode(level, node)
        }
        // then promote itself as entry node if entry node is not set
        if (hnsw.trySetNewEntryNode(node, nodeLevel)) {
            return
        }

        // if the entry node is already set, then we have to do all connections first before we can
        // promote ourselves as entry node
        var lowestUnsetLevel = 0
        var curMaxLevel: Int
        do {
            curMaxLevel = hnsw.numLevels() - 1
            // NOTE: the entry node and max level may not be paired, but because we get the level first
            // we ensure that the entry node we get later will always exist on the curMaxLevel
            var eps = intArrayOf(hnsw.entryNode())

            // we first do the search from top to bottom
            // for levels > nodeLevel search with topk = 1
            var candidates = entryCandidates
            for (level in curMaxLevel downTo nodeLevel + 1) {
                candidates.clear()
                graphSearcher.searchLevel(candidates, scorer, level, eps, hnsw, null)
                eps[0] = candidates.popNode()
            }

            // for levels <= nodeLevel search with topk = beamWidth, and add connections
            candidates = beamCandidates
            val scratchPerLevel: Array<NeighborArray?> =
                kotlin.arrayOfNulls(min(nodeLevel, curMaxLevel) - lowestUnsetLevel + 1)
            for (i in scratchPerLevel.indices.reversed()) {
                val level: Int = i + lowestUnsetLevel
                candidates.clear()
                graphSearcher.searchLevel(candidates, scorer, level, eps, hnsw, null)
                eps = candidates.popUntilNearestKNodes()
                scratchPerLevel[i] = NeighborArray(max(beamCandidates.k(), M + 1), false)
                popToScratch(candidates, scratchPerLevel[i]!!)
            }

            // then do connections from bottom up
            for (i in scratchPerLevel.indices) {
                addDiverseNeighbors(i + lowestUnsetLevel, node, scratchPerLevel[i]!!, scorer)
            }
            lowestUnsetLevel += scratchPerLevel.size
            require(lowestUnsetLevel == min(nodeLevel, curMaxLevel) + 1)
            if (lowestUnsetLevel > nodeLevel) {
                return
            }
            require(lowestUnsetLevel == curMaxLevel + 1 && nodeLevel > curMaxLevel)
            if (hnsw.tryPromoteNewEntryNode(node, nodeLevel, curMaxLevel)) {
                return
            }
            check(hnsw.numLevels() != curMaxLevel + 1) {
                ("We're not able to promote node "
                        + node
                        + " at level "
                        + nodeLevel
                        + " as entry node. But the max graph level "
                        + curMaxLevel
                        + " has not changed while we are inserting the node.")
            }
        } while (true)
    }

    @Throws(IOException::class)
    override fun addGraphNode(node: Int) {
        /*
    Note: this implementation is thread safe when graph size is fixed (e.g. when merging)
    The process of adding a node is roughly:
    1. Add the node to all level from top to the bottom, but do not connect it to any other node,
       nor try to promote itself to an entry node before the connection is done. (Unless the graph is empty
       and this is the first node, in that case we set the entry node and return)
    2. Do the search from top to bottom, remember all the possible neighbours on each level the node
       is on.
    3. Add the neighbor to the node from bottom to top level, when adding the neighbour,
       we always add all the outgoing links first before adding incoming link such that
       when a search visits this node, it can always find a way out
    4. If the node has level that is less or equal to graph level, then we're done here.
       If the node has level larger than graph level, then we need to promote the node
       as the entry node. If, while we add the node to the graph, the entry node has changed
       (which means the graph level has changed as well), we need to reinsert the node
       to the newly introduced levels (repeating step 2,3 for new levels) and again try to
       promote the node to entry node.
    */
        val scorer = scorerSupplier.scorer()
        scorer.setScoringOrdinal(node)
        addGraphNode(node, scorer)
    }

    private fun printGraphBuildStatus(
        node: Int,
        start: TimeSource.Monotonic.ValueTimeMark,
        t: TimeSource.Monotonic.ValueTimeMark
    ): TimeSource.Monotonic.ValueTimeMark {
        val now = TimeSource.Monotonic.markNow()
        infoStream.message(
            HNSW_COMPONENT,
            "built $node in ${(now - t).inWholeMilliseconds}/${(now - start).inWholeMilliseconds} ms"
        )
        return now
    }

    @Throws(IOException::class)
    private fun addDiverseNeighbors(
        level: Int, node: Int, candidates: NeighborArray, scorer: UpdateableRandomVectorScorer
    ) {
        /* For each of the beamWidth nearest candidates (going from best to worst), select it only if it
     * is closer to target than it is to any of the already-selected neighbors (ie selected in this method,
     * since the node is new and has no prior neighbors).
     */
        val neighbors: NeighborArray = hnsw.getNeighbors(level, node)
        require(
            neighbors.size() == 0 // new node
        )
        val maxConnOnLevel = if (level == 0) M * 2 else M
        val mask = selectAndLinkDiverse(neighbors, candidates, maxConnOnLevel, scorer)

        // Link the selected nodes to the new node, and the new node to the selected nodes (again
        // applying diversity heuristic)
        // NOTE: here we're using candidates and mask but not the neighbour array because once we have
        // added incoming link there will be possibilities of this node being discovered and neighbour
        // array being modified. So using local candidates and mask is a safer option.
        for (i in 0..<candidates.size()) {
            if (mask[i] == false) {
                continue
            }
            val nbr: Int = candidates.nodes()[i]
            if (hnswLock != null) {
                val lock: Lock = hnswLock.write(level, nbr)
                try {
                    val nbrsOfNbr: NeighborArray = this.graph.getNeighbors(level, nbr)
                    nbrsOfNbr.addAndEnsureDiversity(node, candidates.scores()[i], nbr, scorer)
                } finally {
                    lock.unlock()
                }
            } else {
                val nbrsOfNbr: NeighborArray = hnsw.getNeighbors(level, nbr)
                nbrsOfNbr.addAndEnsureDiversity(node, candidates.scores()[i], nbr, scorer)
            }
        }
    }

    /**
     * This method will select neighbors to add and return a mask telling the caller which candidates
     * are selected
     */
    @Throws(IOException::class)
    private fun selectAndLinkDiverse(
        neighbors: NeighborArray,
        candidates: NeighborArray,
        maxConnOnLevel: Int,
        scorer: UpdateableRandomVectorScorer
    ): BooleanArray {
        val mask = BooleanArray(candidates.size())
        // Select the best maxConnOnLevel neighbors of the new node, applying the diversity heuristic
        var i: Int = candidates.size() - 1
        while (neighbors.size() < maxConnOnLevel && i >= 0) {
            // compare each neighbor (in distance order) against the closer neighbors selected so far,
            // only adding it if it is closer to the target than to any of the other selected neighbors
            val cNode: Int = candidates.nodes()[i]
            val cScore: Float = candidates.scores()[i]
            require(cNode <= hnsw.maxNodeId())
            scorer.setScoringOrdinal(cNode)
            if (diversityCheck(cScore, neighbors, scorer)) {
                mask[i] = true
                // here we don't need to lock, because there's no incoming link so no others is able to
                // discover this node such that no others will modify this neighbor array as well
                neighbors.addInOrder(cNode, cScore)
            }
            i--
        }
        return mask
    }

    /**
     * @param score the score of the new candidate and node n, to be compared with scores of the
     * candidate and n's neighbors
     * @param neighbors the neighbors selected so far
     * @return whether the candidate is diverse given the existing neighbors
     */
    @Throws(IOException::class)
    private fun diversityCheck(score: Float, neighbors: NeighborArray, scorer: RandomVectorScorer): Boolean {
        for (i in 0..<neighbors.size()) {
            val neighborSimilarity = scorer.score(neighbors.nodes()[i])
            if (neighborSimilarity >= score) {
                return false
            }
        }
        return true
    }

    @Throws(IOException::class)
    fun finish() {
        // System.out.println("finish " + frozen);
        connectComponents()
        frozen = true
    }

    @Throws(IOException::class)
    private fun connectComponents() {
        val start = TimeSource.Monotonic.markNow()
        for (level in 0..<hnsw.numLevels()) {
            if (!connectComponents(level)) {
                if (infoStream.isEnabled(HNSW_COMPONENT)) {
                    infoStream.message(HNSW_COMPONENT, "connectComponents failed on level $level")
                }
            }
        }
        if (infoStream.isEnabled(HNSW_COMPONENT)) {
            infoStream.message(
                HNSW_COMPONENT, "connectComponents ${start.elapsedNow().inWholeMilliseconds} ms"
            )
        }
    }

    @Throws(IOException::class)
    private fun connectComponents(level: Int): Boolean {
        val notFullyConnected = FixedBitSet(hnsw.size())
        var maxConn = M
        if (level == 0) {
            maxConn *= 2
        }
        val components: MutableList<Component> = HnswUtil.components(hnsw, level, notFullyConnected, maxConn)
        if (infoStream.isEnabled(HNSW_COMPONENT)) {
            infoStream.message(
                HNSW_COMPONENT, "connect " + components.size + " components on level=" + level
            )
        }
        // System.out.println("HnswGraphBuilder. level=" + level + ": " + components);
        var result = true
        if (components.size > 1) {
            // connect other components to the largest one
            val c0: Component = components.maxBy { it.size }
            if (c0.start == NO_MORE_DOCS) {
                // the component is already fully connected - no room for new connections
                return false
            }
            // try for more connections We only do one since otherwise they may become full
            // while linking
            val beam = GraphBuilderKnnCollector(2)
            val eps = IntArray(1)
            val scorer = scorerSupplier.scorer()
            for (c in components) {
                if (c !== c0) {
                    if (c.start == NO_MORE_DOCS) {
                        continue
                    }
                    if (infoStream.isEnabled(HNSW_COMPONENT)) {
                        infoStream.message(HNSW_COMPONENT, "connect component $c to $c0")
                    }

                    beam.clear()
                    eps[0] = c0.start
                    scorer.setScoringOrdinal(c.start)
                    // find the closest node in the largest component to the lowest-numbered node in this
                    // component that has room to make a connection
                    graphSearcher.searchLevel(beam, scorer, level, eps, hnsw, notFullyConnected)
                    var linked = false
                    while (beam.size() > 0) {
                        val c0node = beam.popNode()
                        if (c0node == c.start || !notFullyConnected.get(c0node)) {
                            continue
                        }
                        val score = beam.minimumScore()
                        require(notFullyConnected.get(c0node))
                        // link the nodes
                        // System.out.println("link " + c0 + "." + c0node + " to " + c + "." + c.start);
                        link(level, c0node, c.start, score, notFullyConnected)
                        linked = true
                        if (infoStream.isEnabled(HNSW_COMPONENT)) {
                            infoStream.message(HNSW_COMPONENT, "connected ok " + c0node + " -> " + c.start)
                        }
                    }
                    if (!linked) {
                        if (infoStream.isEnabled(HNSW_COMPONENT)) {
                            infoStream.message(HNSW_COMPONENT, "not connected; no free nodes found")
                        }
                        result = false
                    }
                }
            }
        }
        return result
    }

    // Try to link two nodes bidirectionally; the forward connection will always be made.
    // Update notFullyConnected.
    private fun link(level: Int, n0: Int, n1: Int, score: Float, notFullyConnected: FixedBitSet) {
        val nbr0: NeighborArray = hnsw.getNeighbors(level, n0)
        val nbr1: NeighborArray = hnsw.getNeighbors(level, n1)
        // must subtract 1 here since the nodes array is one larger than the configured
        // max neighbors (M / 2M).
        // We should have taken care of this check by searching for not-full nodes
        val maxConn: Int = nbr0.nodes().size - 1
        require(notFullyConnected.get(n0))
        require(nbr0.size() < maxConn) { "node " + n0 + " is full, has " + nbr0.size() + " friends" }
        nbr0.addOutOfOrder(n1, score)
        if (nbr0.size() == maxConn) {
            notFullyConnected.clear(n0)
        }
        if (nbr1.size() < maxConn) {
            nbr1.addOutOfOrder(n0, score)
            if (nbr1.size() == maxConn) {
                notFullyConnected.clear(n1)
            }
        }
    }

    /**
     * A restricted, specialized knnCollector that can be used when building a graph.
     *
     *
     * Does not support TopDocs
     */
    class GraphBuilderKnnCollector(k: Int) : KnnCollector {
        private val queue: NeighborQueue
        private val k: Int
        private var visitedCount: Long = 0

        /**
         * @param k the number of neighbors to collect
         */
        init {
            this.queue = NeighborQueue(k, false)
            this.k = k
        }

        fun size(): Int {
            return queue.size()
        }

        fun popNode(): Int {
            return queue.pop()
        }

        fun popUntilNearestKNodes(): IntArray {
            while (size() > k()) {
                queue.pop()
            }
            return queue.nodes()
        }

        fun minimumScore(): Float {
            return queue.topScore()
        }

        fun clear() {
            this.queue.clear()
            this.visitedCount = 0
        }

        override fun earlyTerminated(): Boolean {
            return false
        }

        override fun incVisitedCount(count: Int) {
            this.visitedCount += count.toLong()
        }

        override fun visitedCount(): Long {
            return visitedCount
        }

        override fun visitLimit(): Long {
            return Long.Companion.MAX_VALUE
        }

        override fun k(): Int {
            return k
        }

        override fun collect(docId: Int, similarity: Float): Boolean {
            return queue.insertWithOverflow(docId, similarity)
        }

        override fun minCompetitiveSimilarity(): Float {
            return if (queue.size() >= k()) queue.topScore() else Float.Companion.NEGATIVE_INFINITY
        }

        override fun topDocs(): TopDocs {
            throw IllegalArgumentException()
        }

        override val searchStrategy: KnnSearchStrategy
            get() {
                throw IllegalArgumentException("Should not use unique strategy during graph building")
            }
    }

    companion object {
        /** Default number of maximum connections per node  */
        const val DEFAULT_MAX_CONN: Int = 16

        /**
         * Default number of the size of the queue maintained while searching during a graph construction.
         */
        const val DEFAULT_BEAM_WIDTH: Int = 100

        /** Default random seed for level generation *  */
        private const val DEFAULT_RAND_SEED: Long = 42

        /** A name for the HNSW component for the info-stream *  */
        const val HNSW_COMPONENT: String = "HNSW"

        /** Random seed for level generation; public to expose for testing *  */
        var randSeed: Long = DEFAULT_RAND_SEED

        @Throws(IOException::class)
        fun create(
            scorerSupplier: RandomVectorScorerSupplier, M: Int, beamWidth: Int, seed: Long
        ): HnswGraphBuilder {
            return HnswGraphBuilder(scorerSupplier, M, beamWidth, seed, -1)
        }

        @Throws(IOException::class)
        fun create(
            scorerSupplier: RandomVectorScorerSupplier, M: Int, beamWidth: Int, seed: Long, graphSize: Int
        ): HnswGraphBuilder {
            return HnswGraphBuilder(scorerSupplier, M, beamWidth, seed, graphSize)
        }

        private fun popToScratch(candidates: GraphBuilderKnnCollector, scratch: NeighborArray) {
            scratch.clear()
            val candidateCount = candidates.size()
            // extract all the Neighbors from the queue into an array; these will now be
            // sorted from worst to best
            for (i in 0..<candidateCount) {
                val maxSimilarity = candidates.minimumScore()
                scratch.addInOrder(candidates.popNode(), maxSimilarity)
            }
        }

        private fun getRandomGraphLevel(ml: Double, random: /*Splittable*/Random): Int {
            var randDouble: Double
            do {
                randDouble = random.nextDouble() // avoid 0 value, as log(0) is undefined
            } while (randDouble == 0.0)
            return ((-ln(randDouble) * ml).toInt())
        }
    }
}
