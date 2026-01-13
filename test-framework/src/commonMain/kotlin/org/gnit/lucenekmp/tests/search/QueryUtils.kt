package org.gnit.lucenekmp.tests.search

import okio.IOException
import org.gnit.lucenekmp.index.BinaryDocValues
import org.gnit.lucenekmp.index.ByteVectorValues
import org.gnit.lucenekmp.index.DocValuesSkipper
import org.gnit.lucenekmp.index.FieldInfos
import org.gnit.lucenekmp.index.FloatVectorValues
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.LeafMetaData
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.MultiReader
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.index.SortedDocValues
import org.gnit.lucenekmp.index.SortedNumericDocValues
import org.gnit.lucenekmp.index.SortedSetDocValues
import org.gnit.lucenekmp.index.StoredFieldVisitor
import org.gnit.lucenekmp.index.StoredFields
import org.gnit.lucenekmp.index.TermVectors
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.BulkScorer
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.DocIdStream
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.KnnCollector
import org.gnit.lucenekmp.search.LeafCollector
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.QueryVisitor
import org.gnit.lucenekmp.search.Scorable
import org.gnit.lucenekmp.search.ScoreMode
import org.gnit.lucenekmp.search.Scorer
import org.gnit.lucenekmp.search.ScorerSupplier
import org.gnit.lucenekmp.search.SimpleCollector
import org.gnit.lucenekmp.search.Weight
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.Version
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private object IdentityHashCodes {
    private var nextId: Int = 1
    fun next(): Int {
        return nextId++
    }
}

/** Utility class for sanity-checking queries.  */
object QueryUtils {
    /** Check the types of things query objects should be able to do.  */
    fun check(q: Query) {
        checkHashEquals(q)
    }

    /** check very basic hashCode and equals  */
    fun checkHashEquals(q: Query) {
        checkEqual(q, q)

        // test that a class check is done so that no exception is thrown
        // in the implementation of equals()
        val whacky: Query =
            object : Query() {
                override fun toString(field: String?): String {
                    return "My Whacky Query"
                }

                override fun visit(visitor: QueryVisitor) {}

                override fun equals(o: Any?): Boolean {
                    return o === this
                }

                // In your anonymous Query subclass:
                private var _identityHash: Int = 0

                override fun hashCode(): Int {
                    if (_identityHash == 0) {
                        _identityHash = IdentityHashCodes.next()
                        if (_identityHash == 0) _identityHash = IdentityHashCodes.next() // avoid 0 sentinel
                    }
                    return _identityHash
                }
            }
        checkUnequal(q, whacky)

        // null test
        assertFalse(q == null)
    }

    fun checkEqual(q1: Query, q2: Query) {
        assertEquals(q1, q2)
        assertEquals(q1.hashCode().toLong(), q2.hashCode().toLong())
    }

    fun checkUnequal(q1: Query, q2: Query) {
        assertNotEquals(q1, q2, "$q1 equal to $q2")
        assertNotEquals(q2, q1, "$q2 equal to $q1")
    }

    /** deep check that explanations of a query 'score' correctly  */
    @Throws(IOException::class)
    fun checkExplanations(
        q: Query,
        s: IndexSearcher
    ) {
        CheckHits.checkExplanations(q, null, s, true)
    }

    /**
     * Various query sanity checks on a searcher, some checks are only done for instanceof
     * IndexSearcher.
     *
     * @see .check
     * @see .checkFirstSkipTo
     *
     * @see .checkSkipTo
     *
     * @see .checkExplanations
     *
     * @see .checkEqual
     *
     * @see CheckHits.checkMatches
     */
    fun check(
        random: Random,
        q1: Query,
        s: IndexSearcher,
        wrap: Boolean = true
    ) {
        try {
            check(q1)
            if (s != null) {
                checkFirstSkipTo(q1, s)
                checkSkipTo(q1, s)
                checkBulkScorerSkipTo(random, q1, s)
                checkCount(q1, s)
                if (wrap) {
                    check(random, q1, wrapUnderlyingReader(random, s, -1), false)
                    check(random, q1, wrapUnderlyingReader(random, s, 0), false)
                    check(random, q1, wrapUnderlyingReader(random, s, +1), false)
                }
                checkExplanations(q1, s)
                CheckHits.checkMatches(q1, s)
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    /**
     * Given an IndexSearcher, returns a new IndexSearcher whose IndexReader is a MultiReader
     * containing the Reader of the original IndexSearcher, as well as several "empty" IndexReaders --
     * some of which will have deleted documents in them. This new IndexSearcher should behave exactly
     * the same as the original IndexSearcher.
     *
     * @param s the searcher to wrap
     * @param edge if negative, s will be the first sub; if 0, s will be in the middle, if positive s
     * will be the last sub
     */
    @Throws(IOException::class)
    fun wrapUnderlyingReader(
        random: Random, s: IndexSearcher, edge: Int
    ): IndexSearcher {
        val r: IndexReader = s.indexReader

        // we can't put deleted docs before the nested reader, because
        // it will throw off the docIds
        val readers: Array<IndexReader> =
            arrayOf<IndexReader>(
                if (edge < 0) r else MultiReader(),
                MultiReader(),
                MultiReader(
                    if (edge < 0) emptyReader(4) else MultiReader(),
                    MultiReader(),
                    if (0 == edge) r else MultiReader()
                ),
                if (0 < edge) MultiReader() else emptyReader(7),
                MultiReader(),
                MultiReader(
                    if (0 < edge) MultiReader() else emptyReader(5),
                    MultiReader(),
                    if (0 < edge) r else MultiReader()
                )
            )

        val out: IndexSearcher =
            LuceneTestCase.newSearcher(
                MultiReader(*readers)
            )
        out.similarity = s.similarity
        return out
    }

    private fun emptyReader(maxDoc: Int): IndexReader {
        return object : LeafReader() {
            @Throws(IOException::class)
            override fun terms(field: String?): Terms? {
                return null
            }

            @Throws(IOException::class)
            override fun getNumericDocValues(field: String): NumericDocValues? {
                return null
            }

            @Throws(IOException::class)
            override fun getBinaryDocValues(field: String): BinaryDocValues? {
                return null
            }

            @Throws(IOException::class)
            override fun getSortedDocValues(field: String): SortedDocValues? {
                return null
            }

            @Throws(IOException::class)
            override fun getSortedNumericDocValues(field: String): SortedNumericDocValues? {
                return null
            }

            @Throws(IOException::class)
            override fun getSortedSetDocValues(field: String): SortedSetDocValues? {
                return null
            }

            @Throws(IOException::class)
            override fun getNormValues(field: String): NumericDocValues? {
                return null
            }

            @Throws(IOException::class)
            override fun getDocValuesSkipper(field: String): DocValuesSkipper? {
                return null
            }

            @Throws(IOException::class)
            override fun getFloatVectorValues(field: String): FloatVectorValues? {
                return null
            }

            @Throws(IOException::class)
            override fun getByteVectorValues(field: String): ByteVectorValues? {
                return null
            }

            override fun searchNearestVectors(
                field: String,
                target: FloatArray,
                knnCollector: KnnCollector,
                acceptDocs: Bits?
            ) {
            }

            override fun searchNearestVectors(
                field: String,
                target: ByteArray,
                knnCollector: KnnCollector,
                acceptDocs: Bits?
            ) {
            }

            override val fieldInfos: FieldInfos
                get() = FieldInfos.EMPTY

            override val liveDocs: Bits =
                Bits.MatchNoBits(maxDoc)

            override fun getPointValues(fieldName: String): PointValues? {
                return null
            }

            @Throws(IOException::class)
            override fun checkIntegrity() {
            }

            override fun termVectors(): TermVectors {
                return TermVectors.EMPTY
            }

            override fun numDocs(): Int {
                return 0
            }

            override fun maxDoc(): Int {
                return maxDoc
            }

            override fun storedFields(): StoredFields {
                return object : StoredFields() {
                    @Throws(IOException::class)
                    override fun document(
                        docID: Int,
                        visitor: StoredFieldVisitor
                    ) {
                    }
                }
            }

            @Throws(IOException::class)
            override fun doClose() {
            }

            override val metaData: LeafMetaData
                get() = LeafMetaData(
                    Version.LATEST.major,
                    Version.LATEST,
                    null,
                    false
                )

            override val coreCacheHelper: CacheHelper?
                get() = null

            override val readerCacheHelper: CacheHelper?
                get() = null
        }
    }

    /**
     * alternate scorer advance(),advance(),next(),next(),advance(),advance(), etc and ensure a
     * hitcollector receives same docs and scores
     */
    @Throws(IOException::class)
    fun checkSkipTo(q: Query, s: IndexSearcher) {
        // System.out.println("Checking "+q);
        val readerContextArray: MutableList<LeafReaderContext> =
            s.topReaderContext.leaves()

        val skip_op = 0
        val next_op = 1
        val orders = arrayOf<IntArray>(
            intArrayOf(next_op),
            intArrayOf(skip_op),
            intArrayOf(skip_op, next_op),
            intArrayOf(next_op, skip_op),
            intArrayOf(skip_op, skip_op, next_op, next_op),
            intArrayOf(next_op, next_op, skip_op, skip_op),
            intArrayOf(skip_op, skip_op, skip_op, next_op, next_op),
        )
        for (k in orders.indices) {
            val order = orders[k]
            // System.out.print("Order:");for (int i = 0; i < order.length; i++)
            // System.out.print(order[i]==skip_op  " skip()":" next()");
            // System.out.println();
            val opidx = intArrayOf(0)
            val lastDoc = intArrayOf(-1)

            // FUTURE: ensure scorer.doc()==-1
            val maxDiff = 1e-5f
            val lastReader: Array<LeafReader> = arrayOf()

            s.search(
                q,
                object : SimpleCollector() {
                    override var scorer: Scorable? = null
                    override var weight: Weight? = null
                    private var iterator: DocIdSetIterator? = null
                    private var leafPtr = 0

                    @Throws(IOException::class)
                    override fun collect(doc: Int) {
                        val score: Float = scorer!!.score()
                        lastDoc[0] = doc
                        try {
                            if (scorer == null) {
                                val rewritten: Query = s.rewrite(q)
                                val w: Weight = s.createWeight(
                                    rewritten,
                                    ScoreMode.COMPLETE,
                                    1f
                                )
                                val context: LeafReaderContext =
                                    readerContextArray.get(leafPtr)
                                scorer = w.scorer(context)
                                iterator = (scorer!! as Scorer).iterator()
                            }

                            val op = order[(opidx[0]++) % order.size]
                            // System.out.println(op==skip_op
                            // "skip("+(sdoc[0]+1)+")":"next()");
                            val more =
                                if (op == skip_op)
                                    iterator!!.advance((scorer!! as Scorer).docID() + 1) != DocIdSetIterator.NO_MORE_DOCS
                                else
                                    iterator!!.nextDoc() != DocIdSetIterator.NO_MORE_DOCS
                            val scorerDoc: Int = (scorer!! as Scorer).docID()
                            val scorerScore: Float = scorer!!.score()
                            val scorerScore2: Float = scorer!!.score()
                            val scoreDiff = abs(score - scorerScore)
                            val scorerDiff = abs(scorerScore2 - scorerScore)

                            var success = false
                            try {
                                assertTrue(more)
                                assertEquals(
                                    scorerDoc.toLong(),
                                    doc.toLong(),
                                    message = "scorerDoc=$scorerDoc,doc=$doc"
                                )
                                assertTrue(
                                    scoreDiff <= maxDiff,
                                    message = "scorerDoc=$scorerDoc,doc=$doc"
                                )
                                assertTrue(
                                    scorerDiff <= maxDiff,
                                    message = "scorerScorer=$scorerScore, scorerScore2=$scorerScore2"
                                )
                                success = true
                            } finally {
                                if (!success) {
                                    if (LuceneTestCase.VERBOSE) {
                                        val sbord = StringBuilder()
                                        for (i in order.indices) {
                                            sbord.append(if (order[i] == skip_op) " skip()" else " next()")
                                        }
                                        println(
                                            ("ERROR matching docs:"
                                                    + "\n\t"
                                                    + (if (doc != scorerDoc) "--> " else "")
                                                    + "doc="
                                                    + doc
                                                    + ", scorerDoc="
                                                    + scorerDoc
                                                    + "\n\t"
                                                    + (if (!more) "--> " else "")
                                                    + "tscorer.more="
                                                    + more
                                                    + "\n\t"
                                                    + (if (scoreDiff > maxDiff) "--> " else "")
                                                    + "scorerScore="
                                                    + scorerScore
                                                    + " scoreDiff="
                                                    + scoreDiff
                                                    + " maxDiff="
                                                    + maxDiff
                                                    + "\n\t"
                                                    + (if (scorerDiff > maxDiff) "--> " else "")
                                                    + "scorerScore2="
                                                    + scorerScore2
                                                    + " scorerDiff="
                                                    + scorerDiff
                                                    + "\n\thitCollector.doc="
                                                    + doc
                                                    + " score="
                                                    + score
                                                    + "\n\t Scorer="
                                                    + scorer
                                                    + "\n\t Query="
                                                    + q
                                                    + "  "
                                                    + q::class.simpleName
                                                    + "\n\t Searcher="
                                                    + s
                                                    + "\n\t Order="
                                                    + sbord
                                                    + "\n\t Op="
                                                    + (if (op == skip_op) " skip()" else " next()"))
                                        )
                                    }
                                }
                            }
                        } catch (e: IOException) {
                            throw RuntimeException(e)
                        }
                    }

                    override fun scoreMode(): ScoreMode {
                        return ScoreMode.COMPLETE
                    }

                    @Throws(IOException::class)
                    override fun doSetNextReader(context: LeafReaderContext) {
                        // confirm that skipping beyond the last doc, on the
                        // previous reader, hits NO_MORE_DOCS
                        if (lastReader[0] != null) {
                            val previousReader: LeafReader = lastReader[0]
                            val indexSearcher: IndexSearcher =
                                LuceneTestCase.newSearcher(
                                    previousReader,
                                    false
                                )
                            indexSearcher.similarity = s.similarity
                            val rewritten: Query = indexSearcher.rewrite(q)
                            val w: Weight = indexSearcher.createWeight(
                                rewritten,
                                ScoreMode.COMPLETE,
                                1f
                            )
                            val ctx: LeafReaderContext =
                                indexSearcher.topReaderContext as LeafReaderContext
                            val scorer: Scorer? = w.scorer(ctx)
                            if (scorer != null) {
                                val iterator: DocIdSetIterator = scorer.iterator()
                                var more = false
                                val liveDocs: Bits? = context.reader().liveDocs
                                var d: Int = iterator.advance(lastDoc[0] + 1)
                                while (d != DocIdSetIterator.NO_MORE_DOCS
                                ) {
                                    if (liveDocs == null || liveDocs.get(d)) {
                                        more = true
                                        break
                                    }
                                    d = iterator.nextDoc()
                                }
                                assertFalse(more,
                                    message = ("query's last doc was "
                                            + lastDoc[0]
                                            + " but advance("
                                            + (lastDoc[0] + 1)
                                            + ") got to "
                                            + scorer.docID())
                                )
                            }
                            leafPtr++
                        }
                        lastReader[0] = context.reader()
                        assert(readerContextArray.get(leafPtr).reader() === context.reader())
                        this.scorer = null
                        lastDoc[0] = -1
                    }
                })

            if (lastReader[0] != null) {
                // confirm that skipping beyond the last doc, on the
                // previous reader, hits NO_MORE_DOCS
                val previousReader: LeafReader = lastReader[0]
                val indexSearcher: IndexSearcher =
                    LuceneTestCase.newSearcher(previousReader, false)
                indexSearcher.similarity = s.similarity
                val rewritten: Query = indexSearcher.rewrite(q)
                val w: Weight = indexSearcher.createWeight(
                    rewritten,
                    ScoreMode.COMPLETE,
                    1f
                )
                val ctx: LeafReaderContext = previousReader.context
                val scorer: Scorer? = w.scorer(ctx)
                if (scorer != null) {
                    val iterator: DocIdSetIterator = scorer.iterator()
                    var more = false
                    val liveDocs: Bits? = lastReader[0].liveDocs
                    var d: Int = iterator.advance(lastDoc[0] + 1)
                    while (d != DocIdSetIterator.NO_MORE_DOCS
                    ) {
                        if (liveDocs == null || liveDocs.get(d)) {
                            more = true
                            break
                        }
                        d = iterator.nextDoc()
                    }
                    assertFalse(more,
                        message = ("query's last doc was "
                                + lastDoc[0]
                                + " but advance("
                                + (lastDoc[0] + 1)
                                + ") got to "
                                + scorer.docID())
                    )
                }
            }
        }
    }

    /** check that first skip on just created scorers always goes to the right doc  */
    @Throws(IOException::class)
    fun checkFirstSkipTo(
        q: Query,
        s: IndexSearcher
    ) {
        // System.out.println("checkFirstSkipTo: "+q);
        val maxDiff = 1e-3f
        val lastDoc = intArrayOf(-1)
        val lastReader: Array<LeafReader> = arrayOf()
        val context: MutableList<LeafReaderContext> =
            s.topReaderContext.leaves()
        val rewritten: Query = s.rewrite(q)
        s.search(
            q,
            object : SimpleCollector() {
                private val w: Weight =
                    s.createWeight(rewritten, ScoreMode.COMPLETE, 1f)
                override var scorer: Scorable? = null
                override var weight: Weight?
                    get() = w
                    set(value) { /* noop */ }
                private var leafPtr = 0
                private var intervalTimes32 = (1 * 32).toLong()

                @Throws(IOException::class)
                override fun collect(doc: Int) {
                    val score: Float = scorer!!.score()
                    try {
                        // The intervalTimes32 trick helps contain the runtime of this check: first we check
                        // every single doc in the interval, then after 32 docs we check every 2 docs, etc.
                        var i = lastDoc[0] + 1
                        while (i <= doc) {
                            val supplier: ScorerSupplier? = w.scorerSupplier(context.get(leafPtr))
                            val scorer: Scorer = supplier!!.get(1L)!! // only checking one doc, so leadCost = 1
                            assertTrue(scorer.iterator().advance(i) != DocIdSetIterator.NO_MORE_DOCS,
                                message = "query collected $doc but advance($i) says no more docs!"
                            )
                            assertEquals(
                                doc.toLong(),
                                scorer.docID().toLong(),
                                message = "query collected " + doc + " but advance(" + i + ") got to " + scorer.docID()
                            )
                            val advanceScore: Float = scorer.score()
                            assertEquals(
                                advanceScore,
                                scorer.score(),
                                maxDiff,
                                message = "unstable advance($i) score!"
                            )
                            assertEquals(
                                score,
                                advanceScore,
                                maxDiff,
                                message = ("query assigned doc "
                                        + doc
                                        + " a score of <"
                                        + score
                                        + "> but advance("
                                        + i
                                        + ") has <"
                                        + advanceScore
                                        + ">!")
                            )
                            i += (intervalTimes32++ / 1024).toInt()
                        }
                        lastDoc[0] = doc
                    } catch (e: IOException) {
                        throw RuntimeException(e)
                    }
                }

                override fun scoreMode(): ScoreMode {
                    return ScoreMode.COMPLETE
                }

                @Throws(IOException::class)
                override fun doSetNextReader(context: LeafReaderContext) {
                    // confirm that skipping beyond the last doc, on the
                    // previous reader, hits NO_MORE_DOCS
                    if (lastReader[0] != null) {
                        val previousReader: LeafReader = lastReader[0]
                        val indexSearcher: IndexSearcher =
                            LuceneTestCase.newSearcher(
                                previousReader,
                                false
                            )
                        indexSearcher.similarity = s.similarity
                        val w: Weight = indexSearcher.createWeight(
                            rewritten,
                            ScoreMode.COMPLETE,
                            1f
                        )
                        val scorer: Scorer? = w.scorer(indexSearcher.topReaderContext as LeafReaderContext)
                        if (scorer != null) {
                            val iterator: DocIdSetIterator = scorer.iterator()
                            var more = false
                            val liveDocs: Bits? = context.reader().liveDocs
                            var d: Int = iterator.advance(lastDoc[0] + 1)
                            while (d != DocIdSetIterator.NO_MORE_DOCS
                            ) {
                                if (liveDocs == null || liveDocs.get(d)) {
                                    more = true
                                    break
                                }
                                d = iterator.nextDoc()
                            }
                            assertFalse(more,
                                message = ("query's last doc was "
                                        + lastDoc[0]
                                        + " but advance("
                                        + (lastDoc[0] + 1)
                                        + ") got to "
                                        + scorer.docID())
                            )
                        }
                        leafPtr++
                    }

                    lastReader[0] = context.reader()
                    lastDoc[0] = -1
                }
            })

        if (lastReader[0] != null) {
            // confirm that skipping beyond the last doc, on the
            // previous reader, hits NO_MORE_DOCS
            val previousReader: LeafReader = lastReader[0]
            val indexSearcher: IndexSearcher =
                LuceneTestCase.newSearcher(previousReader, false)
            indexSearcher.similarity = s.similarity
            val w: Weight = indexSearcher.createWeight(
                rewritten,
                ScoreMode.COMPLETE,
                1f
            )
            val scorer: Scorer? = w.scorer(indexSearcher.topReaderContext as LeafReaderContext)
            if (scorer != null) {
                val iterator: DocIdSetIterator = scorer.iterator()
                var more = false
                val liveDocs: Bits? = lastReader[0].liveDocs
                var d: Int = iterator.advance(lastDoc[0] + 1)
                while (d != DocIdSetIterator.NO_MORE_DOCS
                ) {
                    if (liveDocs == null || liveDocs.get(d)) {
                        more = true
                        break
                    }
                    d = iterator.nextDoc()
                }
                assertFalse(more,
                    ("query's last doc was "
                            + lastDoc[0]
                            + " but advance("
                            + (lastDoc[0] + 1)
                            + ") got to "
                            + scorer.docID())
                )
            }
        }
    }

    /** Check that the scorer and bulk scorer advance consistently.  */
    @Throws(IOException::class)
    fun checkBulkScorerSkipTo(
        r: Random,
        query: Query,
        searcher: IndexSearcher
    ) {
        var query: Query = query
        query = searcher.rewrite(query)
        val weight: Weight =
            searcher.createWeight(query, ScoreMode.COMPLETE, 1f)
        for (context in searcher.indexReader.leaves()) {
            val scorer: Scorer?
            val scorerSupplier: ScorerSupplier? = weight.scorerSupplier(context)
            if (scorerSupplier == null) {
                scorer = null
            } else {
                // For IndexOrDocValuesQuey, the bulk scorer will use the indexed structure query
                // and the scorer with a lead cost of 0 will use the doc values query.
                scorer = scorerSupplier.get(0)
            }
            val bulkScorer: BulkScorer? = weight.bulkScorer(context)
            if (scorer == null && bulkScorer == null) {
                continue
            } else if (bulkScorer == null) {
                // ensure scorer is exhausted (it just didnt return null)
                assert(
                    scorer!!.iterator().nextDoc() == DocIdSetIterator.NO_MORE_DOCS
                )
                continue
            }
            val iterator: DocIdSetIterator = scorer!!.iterator()
            var upTo = 0
            while (true) {
                val min: Int = upTo + r.nextInt(5)
                val max: Int = min + 1 + r.nextInt(if (r.nextBoolean()) 10 else 5000)
                if (scorer.docID() < min) {
                    iterator.advance(min)
                }
                val next: Int =
                    bulkScorer.score(
                        object : LeafCollector {

                            var scorer2: Scorable? = null

                            override var scorer: Scorable?
                                get() = scorer2
                                set(value) { scorer2 = value }

                            @Throws(IOException::class)
                            override fun collect(doc: Int) {
                                assert(doc >= min)
                                assert(doc < max)
                                assertEquals(scorer.docID().toLong(), doc.toLong())
                                assertEquals(
                                    scorer.score(),
                                    scorer2!!.score(),
                                    0.01f
                                )
                                iterator.nextDoc()
                            }
                        },
                        null,
                        min,
                        max
                    )
                assert(max <= next)
                assert(next <= scorer.docID())
                upTo = max

                if (scorer.docID() == DocIdSetIterator.NO_MORE_DOCS) {
                    bulkScorer.score(
                        object : LeafCollector {

                            override var scorer: Scorable? = null

                            @Throws(IOException::class)
                            override fun collect(doc: Int) {
                                // no more matches
                                assert(false)
                            }
                        },
                        null,
                        upTo,
                        DocIdSetIterator.NO_MORE_DOCS
                    )
                    break
                }
            }
        }
    }

    /**
     * Check that counting hits through [DocIdStream.count] yield the same result as counting
     * naively.
     */
    @Throws(IOException::class)
    fun checkCount(
        query: Query,
        searcher: IndexSearcher
    ) {
        var query: Query = query
        query = searcher.rewrite(query)
        val weight: Weight =
            searcher.createWeight(query, ScoreMode.COMPLETE_NO_SCORES, 1f)
        for (context in searcher.indexReader.leaves()) {
            var scorer: BulkScorer? = weight.bulkScorer(context)
            if (scorer == null) {
                continue
            }
            val expectedCount = intArrayOf(0)
            val docIdStream = booleanArrayOf(false)
            scorer.score(
                object : LeafCollector {
                    @Throws(IOException::class)
                    override fun collect(stream: DocIdStream) {
                        // Don't use DocIdStream#count, we want to count the slow way here.
                        docIdStream[0] = true
                        super.collect(stream)
                    }

                    @Throws(IOException::class)
                    override fun collect(doc: Int) {
                        expectedCount[0]++
                    }

                    override var scorer: Scorable? = null
                },
                context.reader().liveDocs,
                0,
                DocIdSetIterator.NO_MORE_DOCS
            )
            if (docIdStream[0] == false) {
                // Don't spend cycles running the query one more time, it doesn't use the DocIdStream
                // optimization.
                continue
            }
            scorer = weight.bulkScorer(context)
            if (scorer == null) {
                assertEquals(0, expectedCount[0].toLong())
                continue
            }
            val actualCount = intArrayOf(0)
            scorer.score(
                object : LeafCollector {
                    @Throws(IOException::class)
                    override fun collect(stream: DocIdStream) {
                        actualCount[0] += stream.count()
                    }

                    @Throws(IOException::class)
                    override fun collect(doc: Int) {
                        actualCount[0]++
                    }

                    override var scorer: Scorable? = null
                },
                context.reader().liveDocs,
                0,
                DocIdSetIterator.NO_MORE_DOCS
            )
            assertEquals(expectedCount[0].toLong(), actualCount[0].toLong())
        }
    }
}
