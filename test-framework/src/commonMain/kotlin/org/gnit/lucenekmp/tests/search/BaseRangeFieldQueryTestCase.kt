package org.gnit.lucenekmp.tests.search

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.MultiBits
import org.gnit.lucenekmp.index.MultiDocValues
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.SerialMergeScheduler
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.LuceneTestCase.Companion.Nightly
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.IOUtils
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Abstract class to do basic tests for a RangeField query. Testing rigor inspired by `BaseGeoPointTestCase`
 */
abstract class BaseRangeFieldQueryTestCase : LuceneTestCase() {
    protected abstract fun newRangeField(box: Range): Field

    protected abstract fun newIntersectsQuery(box: Range): Query

    protected abstract fun newContainsQuery(box: Range): Query

    protected abstract fun newWithinQuery(box: Range): Query

    protected abstract fun newCrossesQuery(box: Range): Query

    @Throws(Exception::class)
    protected abstract fun nextRange(dimensions: Int): Range

    protected open fun dimension(): Int {
        return random().nextInt(4) + 1
    }

    @Throws(Exception::class)
    open fun testRandomTiny() {
        // Make sure single-leaf-node case is OK:
        repeat(10) {
            doTestRandom(10, false)
        }
    }

    @Throws(Exception::class)
    open fun testRandomMedium() {
        doTestRandom(1000, false)
    }

    @Nightly
    @Throws(Exception::class)
    open fun testRandomBig() {
        doTestRandom(2000, false) // TODO reduced from 200000 to 2000 for dev speed
    }

    @Throws(Exception::class)
    open fun testMultiValued() {
        doTestRandom(1000, true)
    }

    @Throws(Exception::class)
    open fun testAllEqual() {
        val numDocs = atLeast(1000)
        val dimensions = dimension()
        val ranges = arrayOfNulls<Array<Range>>(numDocs)
        val theRange = arrayOf(nextRange(dimensions))
        for (i in ranges.indices) {
            ranges[i] = theRange
        }
        verify(ranges)
    }

    // Force low cardinality leaves
    @Throws(Exception::class)
    open fun testLowCardinality() {
        val numDocs = atLeast(1000)
        val dimensions = dimension()

        val cardinality = TestUtil.nextInt(random(), 2, 20)
        val diffRanges = arrayOfNulls<Array<Range>>(cardinality)
        for (i in 0..<cardinality) {
            diffRanges[i] = arrayOf(nextRange(dimensions))
        }

        val ranges = arrayOfNulls<Array<Range>>(numDocs)
        for (i in 0..<numDocs) {
            ranges[i] = diffRanges[random().nextInt(cardinality)]
        }
        verify(ranges)
    }

    @Throws(Exception::class)
    private fun doTestRandom(count: Int, multiValued: Boolean) {
        val numDocs = atLeast(count)
        val dimensions = dimension()

        if (VERBOSE) {
            println("TEST: numDocs=$numDocs")
        }

        val ranges = arrayOfNulls<Array<Range>>(numDocs)

        val haveRealDoc = true

        nextdoc@ for (id in 0..<numDocs) {
            val x = random().nextInt(20)
            if (ranges[id] == null) {
                ranges[id] = arrayOf(nextRange(dimensions))
            }
            if (x == 17) {
                // some docs don't have a box:
                ranges[id]!![0].isMissing = true
                if (VERBOSE) {
                    println("  id=$id is missing")
                }
                continue
            }

            if (multiValued && random().nextBoolean()) {
                // randomly add multi valued documents (up to 2 fields)
                val n = random().nextInt(2) + 1
                ranges[id] = Array(n) { nextRange(dimensions) }
            }

            if (id > 0 && x < 9 && haveRealDoc) {
                var oldID: Int
                var i = 0
                // don't step on missing ranges:
                while (true) {
                    oldID = random().nextInt(id)
                    if (!ranges[oldID]!![0].isMissing) {
                        break
                    } else if (++i > id) {
                        continue@nextdoc
                    }
                }

                if (x == dimensions * 2) {
                    // Fully identical box (use first box in case current is multivalued but old is not)
                    for (d in 0..<dimensions) {
                        ranges[id]!![0].setMin(d, ranges[oldID]!![0].getMin(d))
                        ranges[id]!![0].setMax(d, ranges[oldID]!![0].getMax(d))
                    }
                    if (VERBOSE) {
                        println("  id=$id box=${ranges[id]!!.contentToString()} (same box as doc=$oldID)")
                    }
                } else {
                    for (m in 0 until dimensions * 2) {
                        if (x == m) {
                            val d = kotlin.math.floor(m / 2f).toInt()
                            // current could be multivalue but old may not be, so use first box
                            if (dimensions % 2 == 0) { // even is min
                                ranges[id]!![0].setMin(d, ranges[oldID]!![0].getMin(d))
                                if (VERBOSE) {
                                    println("  id=$id box=${ranges[id]!!.contentToString()} (same min[$d] as doc=$oldID)")
                                }
                            } else { // odd is max
                                ranges[id]!![0].setMax(d, ranges[oldID]!![0].getMax(d))
                                if (VERBOSE) {
                                    println("  id=$id box=${ranges[id]!!.contentToString()} (same max[$d] as doc=$oldID)")
                                }
                            }
                        }
                    }
                }
            }
        }
        verify(ranges)
    }

    @Throws(Exception::class)
    private fun verify(ranges: Array<Array<Range>?>) {
        val iwc: IndexWriterConfig = newIndexWriterConfig()
        // Else seeds may not reproduce:
        iwc.mergeScheduler = SerialMergeScheduler()
        // Else we can get O(N^2) merging
        val mbd = iwc.maxBufferedDocs
        if (mbd != -1 && mbd < ranges.size / 100) {
            iwc.maxBufferedDocs = ranges.size / 100
        }
        val dir: Directory = if (ranges.size > 50000) {
            // Avoid slow codecs like SimpleText
            iwc.codec = TestUtil.getDefaultCodec()
            newFSDirectory(createTempDir(this::class.simpleName ?: "BaseRangeFieldQueryTestCase"))
        } else {
            newDirectory()
        }

        val deleted = HashSet<Int>()
        val w = IndexWriter(dir, iwc)
        for (id in ranges.indices) {
            val doc = Document()
            doc.add(StringField("id", "$id", Field.Store.NO))
            doc.add(NumericDocValuesField("id", id.toLong()))
            if (!ranges[id]!![0].isMissing) {
                for (n in ranges[id]!!.indices) {
                    addRange(doc, ranges[id]!![n])
                }
            }
            w.addDocument(doc)
            if (id > 0 && random().nextInt(100) == 1) {
                val idToDelete = random().nextInt(id)
                w.deleteDocuments(Term("id", "$idToDelete"))
                deleted.add(idToDelete)
                if (VERBOSE) {
                    println("  delete id=$idToDelete")
                }
            }
        }

        if (random().nextBoolean()) {
            w.forceMerge(1)
        }
        val r: IndexReader = DirectoryReader.open(w)
        w.close()
        val s = newSearcher(r)

        val dimensions = ranges[0]!![0].numDimensions()
        val iters = atLeast(25)
        val liveDocs: Bits? = MultiBits.getLiveDocs(s.indexReader)
        val maxDoc = s.indexReader.maxDoc()

        for (iter in 0..<iters) {
            if (VERBOSE) {
                println("\nTEST: iter=$iter s=$s")
            }

            // occasionally test open ended bounding ranges
            val queryRange = nextRange(dimensions)
            val rv = random().nextInt(4)
            val queryType: Range.QueryType
            val query: Query = if (rv == 0) {
                queryType = Range.QueryType.INTERSECTS
                newIntersectsQuery(queryRange)
            } else if (rv == 1) {
                queryType = Range.QueryType.CONTAINS
                newContainsQuery(queryRange)
            } else if (rv == 2) {
                queryType = Range.QueryType.WITHIN
                newWithinQuery(queryRange)
            } else {
                queryType = Range.QueryType.CROSSES
                newCrossesQuery(queryRange)
            }

            if (VERBOSE) {
                println("  query=$query")
            }

            val hits: FixedBitSet = s.search(query, FixedBitSetCollector.createManager(maxDoc))

            val docIDToID: NumericDocValues = MultiDocValues.getNumericValues(r, "id")!!
            for (docID in 0..<maxDoc) {
                assertEquals(docID.toLong(), docIDToID.nextDoc().toLong())
                val id = docIDToID.longValue().toInt()
                val expected = if (liveDocs != null && !liveDocs.get(docID)) {
                    false
                } else if (ranges[id]!![0].isMissing) {
                    false
                } else {
                    expectedResult(queryRange, ranges[id]!!, queryType)
                }

                if (hits.get(docID) != expected) {
                    val b = StringBuilder()
                    b.append("FAIL (iter ").append(iter).append("): ")
                    if (expected) {
                        b.append("id=")
                            .append(id)
                            .append(if (ranges[id]!!.size > 1) " (MultiValue) " else " ")
                            .append("should match but did not\n")
                    } else {
                        b.append("id=").append(id).append(" should not match but did\n")
                    }
                    b.append(" queryRange=").append(queryRange).append("\n")
                    b.append(" box").append(if (ranges[id]!!.size > 1) "es=" else "=").append(ranges[id]!![0])
                    for (n in 1..<ranges[id]!!.size) {
                        b.append(", ")
                        b.append(ranges[id]!![n])
                    }
                    b.append("\n queryType=").append(queryType).append("\n")
                    b.append(" deleted?=").append(liveDocs != null && !liveDocs.get(docID))
                    fail("wrong hit (first of possibly more):\n\n$b")
                }
            }
        }
        IOUtils.close(r, dir)
    }

    protected open fun addRange(doc: Document, box: Range) {
        doc.add(newRangeField(box))
    }

    protected open fun expectedResult(queryRange: Range, range: Array<Range>, queryType: Range.QueryType): Boolean {
        for (i in range.indices) {
            if (expectedBBoxQueryResult(queryRange, range[i], queryType)) {
                return true
            }
        }
        return false
    }

    protected open fun expectedBBoxQueryResult(queryRange: Range, range: Range, queryType: Range.QueryType): Boolean {
        if (queryRange.isEqual(range) && queryType != Range.QueryType.CROSSES) {
            return true
        }
        val relation = range.relate(queryRange)
        return if (queryType == Range.QueryType.INTERSECTS) {
            relation != null
        } else if (queryType == Range.QueryType.CROSSES) {
            // by definition, RangeFields that CONTAIN the query are also considered to cross
            relation == queryType || relation == Range.QueryType.CONTAINS
        } else {
            relation == queryType
        }
    }

    /** base class for range verification */
    abstract class Range {
        var isMissing: Boolean = false

        /** supported query relations */
        enum class QueryType {
            INTERSECTS,
            WITHIN,
            CONTAINS,
            CROSSES
        }

        abstract fun numDimensions(): Int

        abstract fun getMin(dim: Int): Any

        abstract fun setMin(dim: Int, value: Any)

        abstract fun getMax(dim: Int): Any

        abstract fun setMax(dim: Int, value: Any)

        abstract fun isEqual(other: Range): Boolean

        protected abstract fun isDisjoint(other: Range): Boolean

        protected abstract fun isWithin(other: Range): Boolean

        protected abstract fun contains(other: Range): Boolean

        fun relate(other: Range): QueryType? {
            return if (isDisjoint(other)) {
                // if disjoint; return null:
                null
            } else if (isWithin(other)) {
                QueryType.WITHIN
            } else if (contains(other)) {
                QueryType.CONTAINS
            } else {
                QueryType.CROSSES
            }
        }
    }
}
