package org.gnit.lucenekmp.tests.search

import okio.IOException
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.TermStates
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.Thread
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.withLock
import org.gnit.lucenekmp.search.CollectionStatistics
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.QueryVisitor
import org.gnit.lucenekmp.search.ScoreDoc
import org.gnit.lucenekmp.search.SearcherLifetimeManager
import org.gnit.lucenekmp.search.SearcherManager
import org.gnit.lucenekmp.search.Sort
import org.gnit.lucenekmp.search.TermStatistics
import org.gnit.lucenekmp.search.TopDocs
import org.gnit.lucenekmp.search.TopFieldDocs
import org.gnit.lucenekmp.search.TotalHits
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.LineFileDocs
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.jdkport.ReentrantLock

// TODO
//   - doc blocks?  so we can test joins/grouping...
//   - controlled consistency (NRTMgr)

/** Base test class for simulating distributed search across multiple shards. */
abstract class ShardSearchingTestBase : LuceneTestCase() {

    // TODO: maybe SLM should throw this instead of returning null...
    /** Thrown when the lease for a searcher has expired. */
    class SearcherExpiredException(message: String) : RuntimeException(message)

    class FieldAndShardVersion(
        val nodeID: Int,
        val version: Long,
        val field: String
    ) {
        override fun equals(other: Any?): Boolean {
            if (other !is FieldAndShardVersion) {
                return false
            }

            return field == other.field && version == other.version && nodeID == other.nodeID
        }

        override fun hashCode(): Int {
            var result = field.hashCode()
            result = 31 * result + version.hashCode()
            result = 31 * result + nodeID
            return result
        }

        override fun toString(): String {
            return "FieldAndShardVersion(field=$field nodeID=$nodeID version=$version)"
        }
    }

    class TermAndShardVersion(
        val nodeID: Int,
        val version: Long,
        val term: Term
    ) {
        override fun equals(other: Any?): Boolean {
            if (other !is TermAndShardVersion) {
                return false
            }

            return term == other.term && version == other.version && nodeID == other.nodeID
        }

        override fun hashCode(): Int {
            var result = term.hashCode()
            result = 31 * result + version.hashCode()
            result = 31 * result + nodeID
            return result
        }
    }

    // We share collection stats for these fields on each node
    private val fieldsToShare = arrayOf("body", "title")

    // Called by one node once it has reopened, to notify all
    // other nodes.  This is just a mock (since it goes and
    // directly updates all other nodes, in RAM)... in a real
    // env this would hit the wire, sending version &
    // collection stats to all other nodes:
    @Throws(IOException::class)
    fun broadcastNodeReopen(nodeID: Int, version: Long, newSearcher: IndexSearcher) {
        if (VERBOSE) {
            println("REOPEN: nodeID=$nodeID version=$version maxDoc=${newSearcher.indexReader.maxDoc()}")
        }

        // Broadcast new collection stats for this node to all
        // other nodes:
        for (field in fieldsToShare) {
            val stats = newSearcher.collectionStatistics(field)
            if (stats != null) {
                for (node in nodes) {
                    // Don't put my own collection stats into the cache;
                    // we pull locally:
                    if (node.myNodeID != nodeID) {
                        node.putCollectionStats(FieldAndShardVersion(nodeID, version, field), stats)
                    }
                }
            }
        }
        for (node in nodes) {
            node.updateNodeVersion(nodeID, version)
        }
    }

    // TODO: broadcastNodeExpire?  then we can purge the
    // known-stale cache entries...

    // MOCK: in a real env you have to hit the wire
    // (send this query to all remote nodes
    // concurrently):
    @Throws(IOException::class)
    fun searchNode(
        nodeID: Int,
        nodeVersions: LongArray,
        q: Query,
        sort: Sort?,
        numHits: Int,
        searchAfter: ScoreDoc?
    ): TopDocs {
        val s = nodes[nodeID].acquire(nodeVersions)
        try {
            return if (sort == null) {
                if (searchAfter != null) {
                    s.localSearchAfter(searchAfter, q, numHits)
                } else {
                    s.localSearch(q, numHits)
                }
            } else {
                assert(searchAfter == null)
                s.localSearch(q, numHits, sort)
            }
        } finally {
            nodes[nodeID].release(s)
        }
    }

    // Mock: in a real env, this would hit the wire and get
    // term stats from remote node
    @Throws(IOException::class)
    fun getNodeTermStats(terms: Set<Term>, nodeID: Int, version: Long): MutableMap<Term, TermStatistics> {
        val node = nodes[nodeID]
        val stats = HashMap<Term, TermStatistics>()
        val s = node.searchers.acquire(version)
            ?: throw SearcherExpiredException("node=$nodeID version=$version")
        try {
            for (term in terms) {
                val ts = TermStates.build(s, term, true)
                if (ts.docFreq() > 0) {
                    stats[term] = s.termStatistics(term, ts.docFreq(), ts.totalTermFreq())
                }
            }
        } finally {
            node.searchers.release(s)
        }
        return stats
    }

    /** Simulated shard node under test */
    protected inner class NodeState(val myNodeID: Int, numNodes: Int) : AutoCloseable {
        val dir: Directory
        val writer: IndexWriter
        val searchers: SearcherLifetimeManager
        val mgr: SearcherManager
        val currentNodeVersions: LongArray

        // TODO: nothing evicts from here!!!  Somehow, on searcher
        // expiration on remote nodes we must evict from our
        // local cache...?  And still LRU otherwise (for the
        // still-live searchers).

        private val collectionStatsCache = HashMap<FieldAndShardVersion, CollectionStatistics>()
        private val termStatsCache = HashMap<TermAndShardVersion, TermStatistics>()
        private val collectionStatsLock = ReentrantLock()
        private val termStatsLock = ReentrantLock()

        /**
         * Matches docs in the local shard but scores based on aggregated stats ("mock distributed
         * scoring") from all nodes.
         */
        inner class ShardIndexSearcher(
            val nodeVersions: LongArray,
            localReader: IndexReader,
            val myNodeID: Int
        ) : IndexSearcher(localReader) {
            init {
                assert(this.myNodeID == this@NodeState.myNodeID) {
                    "myNodeID=$myNodeID NodeState.this.myNodeID=${this@NodeState.myNodeID}"
                }
            }

            @Throws(IOException::class)
            override fun rewrite(original: Query): Query {
                val localSearcher = IndexSearcher(indexReader)
                val rewritten = localSearcher.rewrite(original)
                val terms = HashSet<Term>()
                rewritten.visit(QueryVisitor.termCollector(terms))

                // Make a single request to remote nodes for term
                // stats:
                for (nodeID in nodeVersions.indices) {
                    if (nodeID == myNodeID) {
                        continue
                    }

                    val missing = HashSet<Term>()
                    for (term in terms) {
                        val key = TermAndShardVersion(nodeID, nodeVersions[nodeID], term)
                        if (getTermStats(key) == null) {
                            missing.add(term)
                        }
                    }
                    if (missing.isNotEmpty()) {
                        for ((term, stats) in getNodeTermStats(missing, nodeID, nodeVersions[nodeID])) {
                            val key = TermAndShardVersion(nodeID, nodeVersions[nodeID], term)
                            putTermStats(key, stats)
                        }
                    }
                }

                return rewritten
            }

            @Throws(IOException::class)
            override fun termStatistics(term: Term, docFreq: Int, totalTermFreq: Long): TermStatistics {
                var distributedDocFreq = 0L
                var distributedTotalTermFreq = 0L
                for (nodeID in nodeVersions.indices) {
                    val subStats =
                        if (nodeID == myNodeID) {
                            super.termStatistics(term, docFreq, totalTermFreq)
                        } else {
                            val key = TermAndShardVersion(nodeID, nodeVersions[nodeID], term)
                            getTermStats(key) ?: continue
                        }

                    distributedDocFreq += subStats.docFreq
                    distributedTotalTermFreq += subStats.totalTermFreq
                }
                assert(distributedDocFreq > 0)
                return TermStatistics(term.bytes(), distributedDocFreq, distributedTotalTermFreq)
            }

            @Throws(IOException::class)
            override fun collectionStatistics(field: String): CollectionStatistics? {
                // TODO: we could compute this on init and cache,
                // since we are re-inited whenever any nodes have a
                // new reader
                var docCount = 0L
                var sumTotalTermFreq = 0L
                var sumDocFreq = 0L
                var maxDoc = 0L

                for (nodeID in nodeVersions.indices) {
                    val key = FieldAndShardVersion(nodeID, nodeVersions[nodeID], field)
                    val nodeStats =
                        if (nodeID == myNodeID) {
                            super.collectionStatistics(field)
                        } else {
                            getCollectionStats(key)
                        }
                    if (nodeStats == null) {
                        continue // field not in sub at all
                    }

                    docCount += nodeStats.docCount
                    sumTotalTermFreq += nodeStats.sumTotalTermFreq
                    sumDocFreq += nodeStats.sumDocFreq
                    assert(nodeStats.maxDoc >= 0)
                    maxDoc += nodeStats.maxDoc
                }

                return if (maxDoc == 0L) {
                    null // field not found across any node whatsoever
                } else {
                    CollectionStatistics(field, maxDoc, docCount, sumTotalTermFreq, sumDocFreq)
                }
            }

            @Throws(IOException::class)
            override fun search(query: Query, n: Int): TopDocs {
                val shardHits = Array(nodeVersions.size) {
                    TopDocs(TotalHits(0L, TotalHits.Relation.EQUAL_TO), emptyArray())
                }
                for (nodeID in nodeVersions.indices) {
                    shardHits[nodeID] =
                        if (nodeID == myNodeID) {
                            // My node; run using local shard searcher we
                            // already aquired:
                            localSearch(query, n)
                        } else {
                            searchNode(nodeID, nodeVersions, query, null, n, null)
                        }

                    for (scoreDoc in shardHits[nodeID].scoreDocs) {
                        scoreDoc.shardIndex = nodeID
                    }
                }

                // Merge:
                return TopDocs.merge(n, shardHits)
            }

            @Throws(IOException::class)
            fun localSearch(query: Query, numHits: Int): TopDocs {
                return super.search(query, numHits)
            }

            @Throws(IOException::class)
            override fun searchAfter(after: ScoreDoc?, query: Query, numHits: Int): TopDocs {
                if (after == null) {
                    return super.searchAfter(after, query, numHits)
                }
                val shardHits = Array(nodeVersions.size) {
                    TopDocs(TotalHits(0L, TotalHits.Relation.EQUAL_TO), emptyArray())
                }
                // results are merged in that order: score, shardIndex, doc. therefore we set
                // after to after.score and depending on the nodeID we set doc to either:
                // - not collect any more documents with that score (only with worse score)
                // - collect more documents with that score (and worse) following the last collected
                // document
                // - collect all documents with that score (and worse)
                val shardAfter = ScoreDoc(after.doc, after.score)
                for (nodeID in nodeVersions.indices) {
                    if (nodeID < after.shardIndex) {
                        // all documents with after.score were already collected, so collect
                        // only documents with worse scores.
                        val s = nodes[nodeID].acquire(nodeVersions)
                        try {
                            // Setting after.doc to reader.maxDoc-1 is a way to tell
                            // TopScoreDocCollector that no more docs with that score should
                            // be collected. note that in practice the shard which sends the
                            // request to a remote shard won't have reader.maxDoc at hand, so
                            // it will send some arbitrary value which will be fixed on the
                            // other end.
                            shardAfter.doc = s.indexReader.maxDoc() - 1
                        } finally {
                            nodes[nodeID].release(s)
                        }
                    } else if (nodeID == after.shardIndex) {
                        // collect all documents following the last collected doc with
                        // after.score + documents with worse scores.
                        shardAfter.doc = after.doc
                    } else {
                        // all documents with after.score (and worse) should be collected
                        // because they didn't make it to top-N in the previous round.
                        shardAfter.doc = -1
                    }
                    shardHits[nodeID] =
                        if (nodeID == myNodeID) {
                            // My node; run using local shard searcher we
                            // already aquired:
                            localSearchAfter(shardAfter, query, numHits)
                        } else {
                            searchNode(nodeID, nodeVersions, query, null, numHits, shardAfter)
                        }

                    for (scoreDoc in shardHits[nodeID].scoreDocs) {
                        scoreDoc.shardIndex = nodeID
                    }
                }

                // Merge:
                return TopDocs.merge(numHits, shardHits)
            }

            @Throws(IOException::class)
            fun localSearchAfter(after: ScoreDoc, query: Query, numHits: Int): TopDocs {
                return super.searchAfter(after, query, numHits)
            }

            @Throws(IOException::class)
            override fun search(query: Query, n: Int, sort: Sort): TopFieldDocs {
                val shardHits = Array(nodeVersions.size) {
                    TopFieldDocs(TotalHits(0L, TotalHits.Relation.EQUAL_TO), emptyArray(), sort.sort)
                }
                for (nodeID in nodeVersions.indices) {
                    shardHits[nodeID] =
                        if (nodeID == myNodeID) {
                            // My node; run using local shard searcher we
                            // already aquired:
                            localSearch(query, n, sort)
                        } else {
                            searchNode(nodeID, nodeVersions, query, sort, n, null) as TopFieldDocs
                        }

                    for (scoreDoc in shardHits[nodeID].scoreDocs) {
                        scoreDoc.shardIndex = nodeID
                    }
                }

                // Merge:
                return TopDocs.merge(sort, n, shardHits)
            }

            @Throws(IOException::class)
            fun localSearch(query: Query, numHits: Int, sort: Sort): TopFieldDocs {
                return super.search(query, numHits, sort)
            }
        }

        private var currentShardSearcher: ShardIndexSearcher? = null

        init {
            dir = newFSDirectory(createTempDir("ShardSearchingTestBase"))
            // TODO: set warmer
            val analyzer = MockAnalyzer(random())
            analyzer.setMaxTokenLength(TestUtil.nextInt(random(), 1, IndexWriter.MAX_TERM_LENGTH))
            val iwc = IndexWriterConfig(analyzer)
            iwc.openMode = IndexWriterConfig.OpenMode.CREATE
            // VERBOSE: omit PrintStreamInfoStream(System.out) since System.out is not available in KMP
            writer = IndexWriter(dir, iwc)
            mgr = SearcherManager(writer, null)
            searchers = SearcherLifetimeManager()

            // Init w/ 0s... caller above will do initial
            // "broadcast" by calling initSearcher:
            currentNodeVersions = LongArray(numNodes)
        }

        @Throws(IOException::class)
        fun initSearcher(nodeVersions: LongArray) {
            assert(currentShardSearcher == null)
            nodeVersions.copyInto(currentNodeVersions)
            currentShardSearcher =
                ShardIndexSearcher(currentNodeVersions.copyOf(), mgr.acquire().indexReader, myNodeID)
        }

        @Throws(IOException::class)
        fun updateNodeVersion(nodeID: Int, version: Long) {
            currentNodeVersions[nodeID] = version
            currentShardSearcher?.indexReader?.decRef()
            currentShardSearcher =
                ShardIndexSearcher(currentNodeVersions.copyOf(), mgr.acquire().indexReader, myNodeID)
        }

        // Get the current (fresh) searcher for this node
        fun acquire(): ShardIndexSearcher {
            while (true) {
                val s = currentShardSearcher!!
                // In theory the reader could get decRef'd to 0
                // before we have a chance to incRef, ie if a reopen
                // happens right after the above line, this thread
                // gets stalled, and the old IR is closed.  So we
                // must try/retry until incRef succeeds:
                if (s.indexReader.tryIncRef()) {
                    return s
                }
            }
        }

        @Throws(IOException::class)
        fun release(s: ShardIndexSearcher) {
            s.indexReader.decRef()
        }

        // Get and old searcher matching the specified versions:
        fun acquire(nodeVersions: LongArray): ShardIndexSearcher {
            val s = searchers.acquire(nodeVersions[myNodeID])
                ?: throw SearcherExpiredException("nodeID=$myNodeID version=${nodeVersions[myNodeID]}")
            return ShardIndexSearcher(nodeVersions, s.indexReader, myNodeID)
        }

        // Reopen local reader
        @Throws(IOException::class)
        fun reopen() {
            val before = mgr.acquire()
            mgr.release(before)

            mgr.maybeRefresh()
            val after = mgr.acquire()
            try {
                if (after !== before) {
                    // New searcher was opened
                    val version = searchers.record(after)
                    searchers.prune(SearcherLifetimeManager.PruneByAge(maxSearcherAgeSeconds.toDouble()))
                    broadcastNodeReopen(myNodeID, version, after)
                }
            } finally {
                mgr.release(after)
            }
        }

        override fun close() {
            currentShardSearcher?.indexReader?.decRef()
            searchers.close()
            mgr.close()
            writer.close()
            dir.close()
        }

        fun putCollectionStats(key: FieldAndShardVersion, value: CollectionStatistics) {
            collectionStatsLock.withLock {
                collectionStatsCache[key] = value
            }
        }

        fun getCollectionStats(key: FieldAndShardVersion): CollectionStatistics? {
            return collectionStatsLock.withLock {
                collectionStatsCache[key]
            }
        }

        fun putTermStats(key: TermAndShardVersion, value: TermStatistics) {
            termStatsLock.withLock {
                termStatsCache[key] = value
            }
        }

        fun getTermStats(key: TermAndShardVersion): TermStatistics? {
            return termStatsLock.withLock {
                termStatsCache[key]
            }
        }
    }

    // TODO: make this more realistic, ie, each node should
    // have its own thread, so we have true node to node
    // concurrency
    private inner class ChangeIndices : Thread() {
        override fun run() {
            try {
                LineFileDocs(random()).use { docs ->
                    var numDocs = 0
                    while (System.nanoTime() < endTimeNanos) {
                        val what = random().nextInt(3)
                        val node = nodes[random().nextInt(nodes.size)]
                        if (numDocs == 0 || what == 0) {
                            node.writer.addDocument(docs.nextDoc())
                            numDocs++
                        } else if (what == 1) {
                            node.writer.updateDocument(Term("docid", random().nextInt(numDocs).toString()), docs.nextDoc())
                            numDocs++
                        } else {
                            node.writer.deleteDocuments(Term("docid", random().nextInt(numDocs).toString()))
                        }
                        // TODO: doc blocks too

                        if (random().nextInt(17) == 12) {
                            node.writer.commit()
                        }

                        if (random().nextInt(17) == 12) {
                            nodes[random().nextInt(nodes.size)].reopen()
                        }
                    }
                }
            } catch (t: Throwable) {
                println("FAILED:")
                t.printStackTrace()
                throw RuntimeException(t)
            }
        }
    }

    protected lateinit var nodes: Array<NodeState>
    var maxSearcherAgeSeconds = 0
    var endTimeNanos: Long = 0
    private var changeIndicesThread: Thread? = null

    @Throws(IOException::class)
    protected fun start(numNodes: Int, runTimeSec: Double, maxSearcherAgeSeconds: Int) {
        endTimeNanos = System.nanoTime() + (runTimeSec * 1000000000L).toLong()
        this.maxSearcherAgeSeconds = maxSearcherAgeSeconds

        nodes = Array(numNodes) { nodeID -> NodeState(nodeID, numNodes) }

        val nodeVersions = LongArray(nodes.size)
        for (nodeID in nodes.indices) {
            val s = nodes[nodeID].mgr.acquire()
            try {
                nodeVersions[nodeID] = nodes[nodeID].searchers.record(s)
            } finally {
                nodes[nodeID].mgr.release(s)
            }
        }

        for (nodeID in nodes.indices) {
            val s = nodes[nodeID].mgr.acquire()
            assert(nodeVersions[nodeID] == nodes[nodeID].searchers.record(s))
            try {
                broadcastNodeReopen(nodeID, nodeVersions[nodeID], s)
            } finally {
                nodes[nodeID].mgr.release(s)
            }
        }

        changeIndicesThread = ChangeIndices().also { it.start() }
    }

    @Throws(IOException::class)
    protected fun finish() {
        changeIndicesThread?.join()
        for (node in nodes) {
            node.close()
        }
    }

    /** An IndexSearcher and associated version (lease) */
    protected data class SearcherAndVersion(val searcher: IndexSearcher, val version: Long)
}
