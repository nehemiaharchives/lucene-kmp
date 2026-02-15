package org.gnit.lucenekmp.tests.index

import com.ionspin.kotlin.bignum.integer.BigInteger
import io.github.oshai.kotlinlogging.KotlinLogging
import okio.IOException
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.document.BinaryPoint
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.IntPoint
import org.gnit.lucenekmp.document.LongPoint
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.index.CodecReader
import org.gnit.lucenekmp.index.ConcurrentMergeScheduler
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.MergeScheduler
import org.gnit.lucenekmp.index.MultiBits
import org.gnit.lucenekmp.index.MultiDocValues
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.index.SerialMergeScheduler
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.internal.tests.ConcurrentMergeSchedulerAccess
import org.gnit.lucenekmp.internal.tests.TestSecrets
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.BitSet
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.randomBigInteger
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.junitport.assertArrayEquals
import org.gnit.lucenekmp.tests.util.Rethrow
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.NumericUtils
import kotlin.math.max
import kotlin.math.min
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Abstract class to do basic tests for a points format. NOTE: This test focuses on the points impl,
 * nothing else. The [stretch] goal is for this test to be so thorough in testing a new PointsFormat
 * that if this test passes, then all Lucene tests should also pass. Ie, if there is some bug in a
 * given PointsFormat that this test fails to catch then this test needs to be improved!
 */
abstract class BasePointsFormatTestCase : BaseIndexFileFormatTestCase() {
    private val logger = KotlinLogging.logger {}

    override fun addRandomFields(doc: Document) {
        val numValues: Int = random().nextInt(3)
        for (i in 0..<numValues) {
            doc.add(IntPoint("f", random().nextInt()))
        }
    }

    @Throws(Exception::class)
    open fun testBasic() {
        val dir: Directory = getDirectory(20)
        val iwc: IndexWriterConfig = newIndexWriterConfig()
        iwc.setMergePolicy(newLogMergePolicy())
        val w = IndexWriter(dir, iwc)
        val point = ByteArray(4)
        for (i in 0..19) {
            val doc = Document()
            NumericUtils.intToSortableBytes(i, point, 0)
            doc.add(BinaryPoint("dim", arrayOf(point)))
            w.addDocument(doc)
        }
        w.forceMerge(1)
        w.close()

        val r: DirectoryReader = DirectoryReader.open(dir)
        val sub: LeafReader = getOnlyLeafReader(r)
        val values: PointValues = sub.getPointValues("dim")!!

        // Simple test: make sure intersect can visit every doc:
        val seen = BitSet()
        values.intersect(
            object : PointValues.IntersectVisitor {
                override fun compare(minPacked: ByteArray, maxPacked: ByteArray): PointValues.Relation {
                    return PointValues.Relation.CELL_CROSSES_QUERY
                }

                override fun visit(docID: Int) {
                    throw IllegalStateException()
                }

                override fun visit(docID: Int, packedValue: ByteArray) {
                    seen.set(docID)
                    assertEquals(docID.toLong(), NumericUtils.sortableBytesToInt(packedValue, 0).toLong())
                }
            })
        assertEquals(20, seen.cardinality().toLong())
        IOUtils.close(r, dir)
    }

    @Throws(Exception::class)
    open fun testMerge() {
        val dir: Directory = getDirectory(20)
        val iwc: IndexWriterConfig = newIndexWriterConfig()
        iwc.setMergePolicy(newLogMergePolicy())
        val w = IndexWriter(dir, iwc)
        val point = ByteArray(4)
        for (i in 0..19) {
            val doc = Document()
            NumericUtils.intToSortableBytes(i, point, 0)
            doc.add(BinaryPoint("dim", arrayOf(point)))
            w.addDocument(doc)
            if (i == 10) {
                w.commit()
            }
        }
        w.forceMerge(1)
        w.close()

        val r: DirectoryReader = DirectoryReader.open(dir)
        val sub: LeafReader = getOnlyLeafReader(r)
        val values: PointValues = sub.getPointValues("dim")!!

        // Simple test: make sure intersect can visit every doc:
        val seen = BitSet()
        values.intersect(
            object : PointValues.IntersectVisitor {
                override fun compare(minPacked: ByteArray, maxPacked: ByteArray): PointValues.Relation {
                    return PointValues.Relation.CELL_CROSSES_QUERY
                }

                override fun visit(docID: Int) {
                    throw IllegalStateException()
                }

                override fun visit(docID: Int, packedValue: ByteArray) {
                    seen.set(docID)
                    assertEquals(docID.toLong(), NumericUtils.sortableBytesToInt(packedValue, 0).toLong())
                }
            })
        assertEquals(20, seen.cardinality().toLong())
        IOUtils.close(r, dir)
    }

    @Throws(Exception::class)
    open fun testAllPointDocsDeletedInSegment() {
        val dir: Directory = getDirectory(20)
        val iwc: IndexWriterConfig = newIndexWriterConfig()
        val w = IndexWriter(dir, iwc)
        val point = ByteArray(4)
        for (i in 0..9) {
            val doc = Document()
            NumericUtils.intToSortableBytes(i, point, 0)
            doc.add(BinaryPoint("dim", arrayOf(point)))
            doc.add(NumericDocValuesField("id", i.toLong()))
            doc.add(newStringField("x", "x", Field.Store.NO))
            w.addDocument(doc)
        }
        w.addDocument(Document())
        w.deleteDocuments(Term("x", "x"))
        if (random().nextBoolean()) {
            w.forceMerge(1)
        }
        w.close()
        val r: DirectoryReader = DirectoryReader.open(dir)
        assertEquals(1, r.numDocs().toLong())
        val liveDocs: Bits? = MultiBits.getLiveDocs(r)

        for (ctx in r.leaves()) {
            val values: PointValues? = ctx.reader().getPointValues("dim")

            val idValues: NumericDocValues? = ctx.reader().getNumericDocValues("id")
            if (idValues == null) {
                // this is (surprisingly) OK, because if the random IWC flushes all 10 docs before the 11th
                // doc is added, and force merge runs, it
                // will drop the 100% deleted segments, and the "id" field never exists in the final single
                // doc segment
                continue
            }
            val docIDToID = IntArray(ctx.reader().maxDoc())
            var docID: Int
            while ((idValues.nextDoc().also { docID = it }) != DocIdSetIterator.NO_MORE_DOCS) {
                docIDToID[docID] = idValues.longValue().toInt()
            }

            if (values != null) {
                val seen = BitSet()
                values.intersect(
                    object : PointValues.IntersectVisitor {
                        override fun compare(minPacked: ByteArray, maxPacked: ByteArray): PointValues.Relation {
                            return PointValues.Relation.CELL_CROSSES_QUERY
                        }

                        override fun visit(docID: Int) {
                            throw IllegalStateException()
                        }

                        override fun visit(docID: Int, packedValue: ByteArray) {
                            if (liveDocs == null || liveDocs.get(docID)) {
                                seen.set(docID)
                            }
                            assertEquals(docIDToID[docID].toLong(), NumericUtils.sortableBytesToInt(packedValue, 0).toLong())
                        }
                    })
                assertEquals(0, seen.cardinality().toLong())
            }
        }
        IOUtils.close(r, dir)
    }

    /** Make sure we close open files, delete temp files, etc., on exception  */
    @Throws(Exception::class)
    open fun testWithExceptions() {
        val numDocs: Int = atLeast(1000)
        val numBytesPerDim: Int = TestUtil.nextInt(random(), 2, PointValues.MAX_NUM_BYTES)
        val numDims: Int = TestUtil.nextInt(random(), 1, PointValues.MAX_DIMENSIONS)
        val numIndexDims: Int =
            TestUtil.nextInt(random(), 1, min(numDims, PointValues.MAX_INDEX_DIMENSIONS))

        val docValues: Array<Array<ByteArray>> = Array(numDocs) { emptyArray() }
        for (docID in 0..<numDocs) {
            val values = Array(numDims) { ByteArray(numBytesPerDim) }
            for (dim in 0..<numDims) {
                random().nextBytes(values[dim])
            }
            docValues[docID] = values
        }

        // Keep retrying until we 1) we allow a big enough heap, and 2) we hit a random IOExc from MDW:
        var done = false
        var attempt = 0
        while (done == false) {
            attempt++
            logger.debug {
                "testWithExceptions attempt=$attempt numDocs=$numDocs numDims=$numDims numIndexDims=$numIndexDims numBytesPerDim=$numBytesPerDim"
            }
            newMockFSDirectory(createTempDir()).use { dir ->
                try {
                    dir.randomIOExceptionRate = 0.05
                    dir.randomIOExceptionRateOnOpen = 0.05
                    logger.debug {
                        "testWithExceptions attempt=$attempt verify start ioRate=${dir.randomIOExceptionRate} ioRateOnOpen=${dir.randomIOExceptionRateOnOpen}"
                    }
                    verify(dir, docValues, null, numDims, numIndexDims, numBytesPerDim, true)
                    logger.debug { "testWithExceptions attempt=$attempt verify completed without exception" }
                } catch (ise: IllegalStateException) {
                    done = handlePossiblyFakeException(ise)
                    logger.debug {
                        "testWithExceptions attempt=$attempt caught IllegalStateException done=$done message=${ise.message}"
                    }
                } catch (ae: AssertionError) {
                    if (ae.message != null && ae.message!!.contains("does not exist; files=")) {
                        // OK: likely we threw the random IOExc when IW was asserting the commit files exist
                        done = true
                        logger.debug {
                            "testWithExceptions attempt=$attempt caught AssertionError(known-random-io) done=$done message=${ae.message}"
                        }
                    } else {
                        logger.debug {
                            "testWithExceptions attempt=$attempt rethrow AssertionError message=${ae.message}"
                        }
                        throw ae
                    }
                } catch (iae: IllegalArgumentException) {
                    // This just means we got a too-small maxMB for the maxPointsInLeafNode; just retry w/
                    // more heap
                    logger.debug {
                        "testWithExceptions attempt=$attempt caught IllegalArgumentException(retry) message=${iae.message}"
                    }
                    assertTrue(
                        iae.message!!.contains("either increase maxMBSortInHeap or decrease maxPointsInLeafNode")
                    )
                } catch (ioe: IOException) {
                    done = handlePossiblyFakeException(ioe)
                    logger.debug {
                        "testWithExceptions attempt=$attempt caught IOException done=$done message=${ioe.message}"
                    }
                }
            }
        }
        logger.debug { "testWithExceptions finished after attempt=$attempt done=$done" }
    }

    // TODO: merge w/ BaseIndexFileFormatTestCase.handleFakeIOException
    private fun handlePossiblyFakeException(e: Exception?): Boolean {
        var ex: Throwable? = e
        while (ex != null) {
            val message = ex.message
            if (message != null
                && (message.contains("a random IOException")
                        || message.contains("background merge hit exception"))
            ) {
                logger.debug {
                    "handlePossiblyFakeException accepted as fake exception throwable=$ex message=$message"
                }
                return true
            }
            ex = ex.cause
        }
        logger.debug {
            "handlePossiblyFakeException rethrowing throwable=$e message=${e?.message}"
        }
        Rethrow.rethrow(e!!)

        // dead code yet javac disagrees:
        return false
    }

    @Throws(Exception::class)
    open fun testMultiValued() {
        val numBytesPerDim: Int = TestUtil.nextInt(random(), 2, PointValues.MAX_NUM_BYTES)
        val numDims: Int = TestUtil.nextInt(random(), 1, PointValues.MAX_DIMENSIONS)
        val numIndexDims: Int =
            TestUtil.nextInt(random(), 1, min(PointValues.MAX_INDEX_DIMENSIONS, numDims))

        val numDocs: Int = if (TEST_NIGHTLY) atLeast(1000) else atLeast(100)
        val docValues: MutableList<Array<ByteArray>> = mutableListOf()
        val docIDs: MutableList<Int> = mutableListOf()

        for (docID in 0..<numDocs) {
            val numValuesInDoc: Int = TestUtil.nextInt(random(), 1, 5)
            for (ord in 0..<numValuesInDoc) {
                docIDs.add(docID)
                val values = arrayOfNulls<ByteArray>(numDims) as Array<ByteArray>
                for (dim in 0..<numDims) {
                    values[dim] = ByteArray(numBytesPerDim)
                    random().nextBytes(values[dim])
                }
                docValues.add(values)
            }
        }

        val docValuesArray: Array<Array<ByteArray>> = docValues.toTypedArray<Array<ByteArray>>()
        val docIDsArray = IntArray(docIDs.size)
        for (i in docIDsArray.indices) {
            docIDsArray[i] = docIDs[i]
        }

        verify(docValuesArray, docIDsArray, numDims, numIndexDims, numBytesPerDim)
    }

    @Throws(Exception::class)
    open fun testAllEqual() {
        val numBytesPerDim: Int = TestUtil.nextInt(random(), 2, PointValues.MAX_NUM_BYTES)
        val numDims: Int = TestUtil.nextInt(random(), 1, PointValues.MAX_INDEX_DIMENSIONS)

        val numDocs: Int = atLeast(1000)
        val docValues: Array<Array<ByteArray>> = arrayOfNulls<Array<ByteArray>>(numDocs) as Array<Array<ByteArray>>

        for (docID in 0..<numDocs) {
            if (docID == 0) {
                val values = arrayOfNulls<ByteArray>(numDims) as Array<ByteArray>
                for (dim in 0..<numDims) {
                    values[dim] = ByteArray(numBytesPerDim)
                    random().nextBytes(values[dim])
                }
                docValues[docID] = values
            } else {
                docValues[docID] = docValues[0]
            }
        }

        verify(docValues, null, numDims, numBytesPerDim)
    }

    @Throws(Exception::class)
    open fun testOneDimEqual() {
        val numBytesPerDim: Int = TestUtil.nextInt(random(), 2, PointValues.MAX_NUM_BYTES)
        val numDims: Int = TestUtil.nextInt(random(), 1, PointValues.MAX_INDEX_DIMENSIONS)

        val numDocs: Int = atLeast(1000)
        val theEqualDim: Int = random().nextInt(numDims)
        val docValues: Array<Array<ByteArray>> = arrayOfNulls<Array<ByteArray>>(numDocs) as Array<Array<ByteArray>>

        for (docID in 0..<numDocs) {
            val values = arrayOfNulls<ByteArray>(numDims) as Array<ByteArray>
            for (dim in 0..<numDims) {
                values[dim] = ByteArray(numBytesPerDim)
                random().nextBytes(values[dim])
            }
            docValues[docID] = values
            if (docID > 0) {
                docValues[docID][theEqualDim] = docValues[0][theEqualDim]
            }
        }

        verify(docValues, null, numDims, numBytesPerDim)
    }

    // this should trigger run-length compression with lengths that are greater than 255
    @Throws(Exception::class)
    open fun testOneDimTwoValues() {
        val numBytesPerDim: Int = TestUtil.nextInt(random(), 2, PointValues.MAX_NUM_BYTES)
        val numDims: Int = TestUtil.nextInt(random(), 1, PointValues.MAX_INDEX_DIMENSIONS)

        val numDocs: Int = atLeast(1000)
        val theDim: Int = random().nextInt(numDims)
        val value1 = ByteArray(numBytesPerDim)
        random().nextBytes(value1)
        val value2 = ByteArray(numBytesPerDim)
        random().nextBytes(value2)
        val docValues: Array<Array<ByteArray>> = arrayOfNulls<Array<ByteArray>>(numDocs) as Array<Array<ByteArray>>

        for (docID in 0..<numDocs) {
            val values = arrayOfNulls<ByteArray>(numDims) as Array<ByteArray>
            for (dim in 0..<numDims) {
                if (dim == theDim) {
                    values[dim] = if (random().nextBoolean()) value1 else value2
                } else {
                    values[dim] = ByteArray(numBytesPerDim)
                    random().nextBytes(values[dim])
                }
            }
            docValues[docID] = values
        }

        verify(docValues, null, numDims, numBytesPerDim)
    }

    // Tests on N-dimensional points where each dimension is a BigInteger
    @Throws(Exception::class)
    open fun testBigIntNDims() {
        val numDocs: Int = atLeast(200)
        getDirectory(numDocs).use { dir ->
            val numBytesPerDim: Int = TestUtil.nextInt(random(), 2, PointValues.MAX_NUM_BYTES)
            val numDims: Int = TestUtil.nextInt(random(), 1, PointValues.MAX_INDEX_DIMENSIONS)
            val iwc: IndexWriterConfig = newIndexWriterConfig(MockAnalyzer(random()))
            // We rely on docIDs not changing:
            iwc.setMergePolicy(newLogMergePolicy())
            val w = RandomIndexWriter(random(), dir, iwc)
            val docs: Array<Array<BigInteger>> = arrayOfNulls<Array<BigInteger>>(numDocs) as Array<Array<BigInteger>>

            for (docID in 0..<numDocs) {
                val values: Array<BigInteger> = arrayOfNulls<BigInteger>(numDims) as Array<BigInteger>
                if (VERBOSE) {
                    println("  docID=$docID")
                }
                val bytes: Array<ByteArray> = arrayOfNulls<ByteArray>(numDims) as Array<ByteArray>
                for (dim in 0..<numDims) {
                    values[dim] = randomBigInt(numBytesPerDim)
                    bytes[dim] = ByteArray(numBytesPerDim)
                    NumericUtils.bigIntToSortableBytes(values[dim], numBytesPerDim, bytes[dim], 0)
                    if (VERBOSE) {
                        println("    " + dim + " -> " + values[dim])
                    }
                }
                docs[docID] = values
                val doc = Document()
                doc.add(BinaryPoint("field", bytes))
                w.addDocument(doc)
            }

            val r: DirectoryReader = w.reader
            w.close()

            val iters: Int = atLeast(100)
            for (iter in 0..<iters) {
                if (VERBOSE) {
                    println("\nTEST: iter=$iter")
                }

                // Random N dims rect query:
                val queryMin: Array<BigInteger> = arrayOfNulls<BigInteger>(numDims) as Array<BigInteger>
                val queryMax: Array<BigInteger> = arrayOfNulls<BigInteger>(numDims) as Array<BigInteger>
                for (dim in 0..<numDims) {
                    queryMin[dim] = randomBigInt(numBytesPerDim)
                    queryMax[dim] = randomBigInt(numBytesPerDim)
                    if (queryMin[dim] > queryMax[dim]) {
                        val x: BigInteger = queryMin[dim]
                        queryMin[dim] = queryMax[dim]
                        queryMax[dim] = x
                    }
                    if (VERBOSE) {
                        println(
                            "  " + dim + "\n    min=" + queryMin[dim] + "\n    max=" + queryMax[dim]
                        )
                    }
                }

                val hits = BitSet()
                for (ctx in r.leaves()) {
                    val dimValues: PointValues? = ctx.reader().getPointValues("field")
                    if (dimValues == null) {
                        continue
                    }

                    val docBase: Int = ctx.docBase

                    dimValues.intersect(
                        object : PointValues.IntersectVisitor {
                            override fun visit(docID: Int) {
                                hits.set(docBase + docID)
                                // System.out.println("visit docID=" + docID);
                            }

                            override fun visit(docID: Int, packedValue: ByteArray) {
                                // System.out.println("visit check docID=" + docID);
                                for (dim in 0..<numDims) {
                                    val x: BigInteger =
                                        NumericUtils.sortableBytesToBigInt(
                                            packedValue, dim * numBytesPerDim, numBytesPerDim
                                        )
                                    if (x < queryMin[dim] || x > queryMax[dim]) {
                                        // System.out.println("  no");
                                        return
                                    }
                                }

                                // System.out.println("  yes");
                                hits.set(docBase + docID)
                            }

                            override fun compare(minPacked: ByteArray, maxPacked: ByteArray): PointValues.Relation {
                                var crosses = false
                                for (dim in 0..<numDims) {
                                    val min: BigInteger =
                                        NumericUtils.sortableBytesToBigInt(
                                            minPacked, dim * numBytesPerDim, numBytesPerDim
                                        )
                                    val max: BigInteger =
                                        NumericUtils.sortableBytesToBigInt(
                                            maxPacked, dim * numBytesPerDim, numBytesPerDim
                                        )
                                    assert(max >= min)

                                    if (max < queryMin[dim] || min > queryMax[dim]) {
                                        return PointValues.Relation.CELL_OUTSIDE_QUERY
                                    } else if (min < queryMin[dim]
                                        || max > queryMax[dim]
                                    ) {
                                        crosses = true
                                    }
                                }

                                if (crosses) {
                                    return PointValues.Relation.CELL_CROSSES_QUERY
                                } else {
                                    return PointValues.Relation.CELL_INSIDE_QUERY
                                }
                            }
                        })
                }

                for (docID in 0..<numDocs) {
                    val docValues: Array<BigInteger> = docs[docID]
                    var expected = true
                    for (dim in 0..<numDims) {
                        val x: BigInteger = docValues[dim]
                        if (x < queryMin[dim] || x > queryMax[dim]) {
                            expected = false
                            break
                        }
                    }
                    val actual: Boolean = hits[docID]
                    assertEquals(expected, actual, "docID=$docID")
                }
            }
            r.close()
        }
    }

    @Throws(Exception::class)
    open fun testRandomBinaryTiny() {
        doTestRandomBinary(3) // TODO reduced from 10 to 3 for dev speed
    }

    @Throws(Exception::class)
    open fun testRandomBinaryMedium() {
        doTestRandomBinary(20) // TODO reduced from 200 to 20 for dev speed
    }

    /*@org.apache.lucene.tests.util.LuceneTestCase.Nightly*/
    @Throws(Exception::class)
    open fun testRandomBinaryBig() {
        assumeFalse("too slow with SimpleText", Codec.default.name == "SimpleText")
        doTestRandomBinary(200) // TODO reduced from 200000 to 200 for dev speed
    }

    @Throws(Exception::class)
    private fun doTestRandomBinary(count: Int) {
        val numDocs: Int = TestUtil.nextInt(random(), count, count * 2)
        val numBytesPerDim: Int = TestUtil.nextInt(random(), 2, PointValues.MAX_NUM_BYTES)
        val numDataDims: Int = TestUtil.nextInt(random(), 1, PointValues.MAX_INDEX_DIMENSIONS)
        val numIndexDims: Int = TestUtil.nextInt(random(), 1, numDataDims)

        val docValues: Array<Array<ByteArray>> = arrayOfNulls<Array<ByteArray>>(numDocs) as Array<Array<ByteArray>>

        for (docID in 0..<numDocs) {
            val values = arrayOfNulls<ByteArray>(numDataDims) as Array<ByteArray>
            for (dim in 0..<numDataDims) {
                values[dim] = ByteArray(numBytesPerDim)
                // TODO: sometimes test on a "small" volume too, so we test the high density cases, higher
                // chance of boundary, etc. cases:
                random().nextBytes(values[dim])
            }
            docValues[docID] = values
        }

        verify(docValues, null, numDataDims, numIndexDims, numBytesPerDim)
    }

    @Throws(Exception::class)
    private fun verify(docValues: Array<Array<ByteArray>>, docIDs: IntArray?, numDims: Int, numBytesPerDim: Int) {
        verify(docValues, docIDs, numDims, numDims, numBytesPerDim)
    }

    /**
     * docIDs can be null, for the single valued case, else it maps value to docID, but all values for
     * one doc must be adjacent
     */
    @Throws(Exception::class)
    private fun verify(
        docValues: Array<Array<ByteArray>>, docIDs: IntArray?, numDataDims: Int, numIndexDims: Int, numBytesPerDim: Int
    ) {
        getDirectory(docValues.size).use { dir ->
            while (true) {
                try {
                    verify(dir, docValues, docIDs, numDataDims, numIndexDims, numBytesPerDim, false)
                    return
                } catch (iae: IllegalArgumentException) {
                    iae.printStackTrace()
                    // This just means we got a too-small maxMB for the maxPointsInLeafNode; just retry
                    assertTrue(
                        iae.message!!.contains("either increase maxMBSortInHeap or decrease maxPointsInLeafNode")
                    )
                }
            }
        }
    }

    private fun flattenBinaryPoint(value: Array<ByteArray>, numDataDims: Int, numBytesPerDim: Int): ByteArray {
        val result = ByteArray(value.size * numBytesPerDim)
        for (d in 0..<numDataDims) {
            System.arraycopy(value[d], 0, result, d * numBytesPerDim, numBytesPerDim)
        }
        return result
    }

    /** test selective indexing  */
    @Throws(Exception::class)
    private fun verify(
        dir: Directory,
        docValues: Array<Array<ByteArray>>,
        ids: IntArray?,
        numDims: Int,
        numIndexDims: Int,
        numBytesPerDim: Int,
        expectExceptions: Boolean
    ) {
        var dir: Directory? = dir
        val numValues = docValues.size
        if (VERBOSE) {
            println(
                ("TEST: numValues="
                        + numValues
                        + " numDims="
                        + numDims
                        + " numIndexDims="
                        + numIndexDims
                        + " numBytesPerDim="
                        + numBytesPerDim)
            )
        }

        // RandomIndexWriter is too slow:
        val useRealWriter = docValues.size > 10000

        var iwc: IndexWriterConfig
        if (useRealWriter) {
            iwc = IndexWriterConfig(MockAnalyzer(random()))
        } else {
            iwc = newIndexWriterConfig()
        }

        if (expectExceptions) {
            val ms: MergeScheduler = iwc.mergeScheduler
            if (ms is ConcurrentMergeScheduler) {
                CONCURRENT_MERGE_SCHEDULER_ACCESS.setSuppressExceptions(ms)
            }
        }
        var w = RandomIndexWriter(random(), dir!!, iwc)
        var r: DirectoryReader? = null

        // Compute actual min/max values:
        val expectedMinValues = arrayOfNulls<ByteArray>(numDims)
        val expectedMaxValues = arrayOfNulls<ByteArray>(numDims)
        for (ord in docValues.indices) {
            for (dim in 0..<numDims) {
                if (ord == 0) {
                    expectedMinValues[dim] = ByteArray(numBytesPerDim)
                    System.arraycopy(docValues[ord][dim], 0, expectedMinValues[dim] as ByteArray, 0, numBytesPerDim)
                    expectedMaxValues[dim] = ByteArray(numBytesPerDim)
                    System.arraycopy(docValues[ord][dim], 0, expectedMaxValues[dim] as ByteArray, 0, numBytesPerDim)
                } else {
                    // TODO: it's cheating that we use StringHelper.compare for "truth": what if it's buggy
                    if (Arrays.compareUnsigned(
                            docValues[ord][dim], 0, numBytesPerDim, expectedMinValues[dim] as ByteArray, 0, numBytesPerDim
                        )
                        < 0
                    ) {
                        System.arraycopy(docValues[ord][dim], 0, expectedMinValues[dim] as ByteArray, 0, numBytesPerDim)
                    }
                    if (Arrays.compareUnsigned(
                            docValues[ord][dim], 0, numBytesPerDim, expectedMaxValues[dim] as ByteArray, 0, numBytesPerDim
                        )
                        > 0
                    ) {
                        System.arraycopy(docValues[ord][dim], 0, expectedMaxValues[dim] as ByteArray, 0, numBytesPerDim)
                    }
                }
            }
        }

        // 20% of the time we add into a separate directory, then at some point use
        // addIndexes to bring the indexed point values to the main directory:
        var saveDir: Directory?
        var saveW: RandomIndexWriter?
        val addIndexesAt: Int
        if (random().nextInt(5) == 1) {
            saveDir = dir
            saveW = w
            dir = getDirectory(numValues)
            if (useRealWriter) {
                iwc = IndexWriterConfig(MockAnalyzer(random()))
            } else {
                iwc = newIndexWriterConfig()
            }
            if (expectExceptions) {
                val ms: MergeScheduler = iwc.mergeScheduler
                if (ms is ConcurrentMergeScheduler) {
                    CONCURRENT_MERGE_SCHEDULER_ACCESS.setSuppressExceptions(ms)
                }
            }
            w = RandomIndexWriter(random(), dir, iwc)
            addIndexesAt = TestUtil.nextInt(random(), 1, numValues - 1)
        } else {
            saveW = null
            saveDir = null
            addIndexesAt = 0
        }

        try {
            val fieldType = FieldType()
            fieldType.setDimensions(numDims, numIndexDims, numBytesPerDim)
            fieldType.freeze()

            var doc: Document? = null
            var lastID = -1
            for (ord in 0..<numValues) {
                val id: Int
                if (ids == null) {
                    id = ord
                } else {
                    id = ids[ord]
                }
                if (id != lastID) {
                    if (doc != null) {
                        if (useRealWriter) {
                            w.w.addDocument(doc)
                        } else {
                            w.addDocument(doc)
                        }
                    }
                    doc = Document()
                    doc.add(NumericDocValuesField("id", id.toLong()))
                }
                // pack the binary point
                var `val` = flattenBinaryPoint(docValues[ord], numDims, numBytesPerDim)

                doc!!.add(BinaryPoint("field", `val`, fieldType))
                lastID = id

                if (random().nextInt(30) == 17) {
                    // randomly index some documents without this field
                    if (useRealWriter) {
                        w.w.addDocument(Document())
                    } else {
                        w.addDocument(Document())
                    }
                    if (VERBOSE) {
                        println("add empty doc")
                    }
                }

                if (random().nextInt(30) == 17) {
                    // randomly index some documents with this field, but we will delete them:
                    val xdoc = Document()
                    `val` = flattenBinaryPoint(docValues[ord], numDims, numBytesPerDim)
                    xdoc.add(BinaryPoint("field", `val`, fieldType))
                    xdoc.add(StringField("nukeme", "yes", Field.Store.NO))
                    if (useRealWriter) {
                        w.w.addDocument(xdoc)
                    } else {
                        w.addDocument(xdoc)
                    }
                    if (VERBOSE) {
                        println("add doc doc-to-delete")
                    }

                    if (random().nextInt(5) == 1) {
                        if (useRealWriter) {
                            w.w.deleteDocuments(Term("nukeme", "yes"))
                        } else {
                            w.deleteDocuments(Term("nukeme", "yes"))
                        }
                    }
                }

                if (VERBOSE) {
                    println("  ord=$ord id=$id")
                    for (dim in 0..<numDims) {
                        println("    dim=" + dim + " value=" + BytesRef(docValues[ord][dim]))
                    }
                }

                if (saveW != null && ord >= addIndexesAt) {
                    switchIndex(w, dir!!, saveW)
                    w = saveW
                    dir = saveDir
                    saveW = null
                    saveDir = null
                }
            }
            w.addDocument(doc!!)
            w.deleteDocuments(Term("nukeme", "yes"))

            if (random().nextBoolean()) {
                if (VERBOSE) {
                    println("\nTEST: now force merge")
                }
                w.forceMerge(1)
            }

            r = w.reader
            w.close()

            if (VERBOSE) {
                println("TEST: reader=$r")
            }

            val idValues: NumericDocValues = MultiDocValues.getNumericValues(r, "id")!!
            val docIDToID = IntArray(r.maxDoc())
            run {
                var docID: Int
                while ((idValues.nextDoc().also { docID = it }) != DocIdSetIterator.NO_MORE_DOCS) {
                    docIDToID[docID] = idValues.longValue().toInt()
                }
            }

            val liveDocs: Bits? = MultiBits.getLiveDocs(r)

            // Verify min/max values are correct:
            val minValues = ByteArray(numIndexDims * numBytesPerDim)
            Arrays.fill(minValues, 0xff.toByte())

            val maxValues = ByteArray(numIndexDims * numBytesPerDim)

            for (ctx in r.leaves()) {
                val dimValues: PointValues? = ctx.reader().getPointValues("field")
                if (dimValues == null) {
                    continue
                }
                assertSize(dimValues.pointTree)
                val leafMinValues: ByteArray = dimValues.minPackedValue
                val leafMaxValues: ByteArray = dimValues.maxPackedValue
                for (dim in 0..<numIndexDims) {
                    if (Arrays.compareUnsigned(
                            leafMinValues,
                            dim * numBytesPerDim,
                            dim * numBytesPerDim + numBytesPerDim,
                            minValues,
                            dim * numBytesPerDim,
                            dim * numBytesPerDim + numBytesPerDim
                        )
                        < 0
                    ) {
                        System.arraycopy(
                            leafMinValues,
                            dim * numBytesPerDim,
                            minValues,
                            dim * numBytesPerDim,
                            numBytesPerDim
                        )
                    }
                    if (Arrays.compareUnsigned(
                            leafMaxValues,
                            dim * numBytesPerDim,
                            dim * numBytesPerDim + numBytesPerDim,
                            maxValues,
                            dim * numBytesPerDim,
                            dim * numBytesPerDim + numBytesPerDim
                        )
                        > 0
                    ) {
                        System.arraycopy(
                            leafMaxValues,
                            dim * numBytesPerDim,
                            maxValues,
                            dim * numBytesPerDim,
                            numBytesPerDim
                        )
                    }
                }
            }

            val scratch = ByteArray(numBytesPerDim)
            for (dim in 0..<numIndexDims) {
                System.arraycopy(minValues, dim * numBytesPerDim, scratch, 0, numBytesPerDim)
                // System.out.println("dim=" + dim + " expectedMin=" + new BytesRef(expectedMinValues[dim])
                // + " min=" + new BytesRef(scratch));
                assertTrue(expectedMinValues[dim].contentEquals(scratch))
                System.arraycopy(maxValues, dim * numBytesPerDim, scratch, 0, numBytesPerDim)
                // System.out.println("dim=" + dim + " expectedMax=" + new BytesRef(expectedMaxValues[dim])
                // + " max=" + new BytesRef(scratch));
                assertTrue(expectedMaxValues[dim].contentEquals(scratch))
            }

            val iters: Int = atLeast(100)
            for (iter in 0..<iters) {
                if (VERBOSE) {
                    println("\nTEST: iter=$iter")
                }

                // Random N dims rect query:
                val queryMin = arrayOfNulls<ByteArray>(numIndexDims)
                val queryMax = arrayOfNulls<ByteArray>(numIndexDims)
                for (dim in 0..<numIndexDims) {
                    queryMin[dim] = ByteArray(numBytesPerDim)
                    random().nextBytes(queryMin[dim]!!)
                    queryMax[dim] = ByteArray(numBytesPerDim)
                    random().nextBytes(queryMax[dim]!!)
                    if (Arrays.compareUnsigned(
                            queryMin[dim] as ByteArray, 0, numBytesPerDim, queryMax[dim] as ByteArray, 0, numBytesPerDim
                        )
                        > 0
                    ) {
                        val x = queryMin[dim]
                        queryMin[dim] = queryMax[dim]
                        queryMax[dim] = x
                    }
                }

                if (VERBOSE) {
                    for (dim in 0..<numIndexDims) {
                        println(
                            ("  dim="
                                    + dim
                                    + "\n    queryMin="
                                    + BytesRef(queryMin[dim]!!)
                                    + "\n    queryMax="
                                    + BytesRef(queryMax[dim]!!))
                        )
                    }
                }

                val hits = BitSet()

                for (ctx in r.leaves()) {
                    val dimValues: PointValues? = ctx.reader().getPointValues("field")
                    if (dimValues == null) {
                        continue
                    }

                    val docBase: Int = ctx.docBase

                    dimValues.intersect(
                        object : PointValues.IntersectVisitor {
                            override fun visit(docID: Int) {
                                if (liveDocs == null || liveDocs.get(docBase + docID)) {
                                    hits.set(docIDToID[docBase + docID])
                                }
                                // System.out.println("visit docID=" + docID);
                            }

                            override fun visit(docID: Int, packedValue: ByteArray) {
                                if (liveDocs != null && liveDocs.get(docBase + docID) == false) {
                                    return
                                }

                                for (dim in 0..<numIndexDims) {
                                    // System.out.println("  dim=" + dim + " value=" + new BytesRef(packedValue,
                                    // dim*numBytesPerDim, numBytesPerDim));
                                    if ((Arrays.compareUnsigned(
                                            packedValue,
                                            dim * numBytesPerDim,
                                            dim * numBytesPerDim + numBytesPerDim,
                                            queryMin[dim] as ByteArray,
                                            0,
                                            numBytesPerDim
                                        )
                                                < 0)
                                        || (Arrays.compareUnsigned(
                                            packedValue,
                                            dim * numBytesPerDim,
                                            dim * numBytesPerDim + numBytesPerDim,
                                            queryMax[dim] as ByteArray,
                                            0,
                                            numBytesPerDim
                                        )
                                                > 0)
                                    ) {
                                        // System.out.println("  no");
                                        return
                                    }
                                }

                                // System.out.println("  yes");
                                hits.set(docIDToID[docBase + docID])
                            }

                            override fun compare(minPacked: ByteArray, maxPacked: ByteArray): PointValues.Relation {
                                var crosses = false
                                // System.out.println("compare");
                                for (dim in 0..<numIndexDims) {
                                    if ((Arrays.compareUnsigned(
                                            maxPacked,
                                            dim * numBytesPerDim,
                                            dim * numBytesPerDim + numBytesPerDim,
                                            queryMin[dim] as ByteArray,
                                            0,
                                            numBytesPerDim
                                        )
                                                < 0)
                                        || (Arrays.compareUnsigned(
                                            minPacked,
                                            dim * numBytesPerDim,
                                            dim * numBytesPerDim + numBytesPerDim,
                                            queryMax[dim] as ByteArray,
                                            0,
                                            numBytesPerDim
                                        )
                                                > 0)
                                    ) {
                                        // System.out.println("  query_outside_cell");
                                        return PointValues.Relation.CELL_OUTSIDE_QUERY
                                    } else if ((Arrays.compareUnsigned(
                                            minPacked,
                                            dim * numBytesPerDim,
                                            dim * numBytesPerDim + numBytesPerDim,
                                            queryMin[dim] as ByteArray,
                                            0,
                                            numBytesPerDim
                                        )
                                                < 0)
                                        || (Arrays.compareUnsigned(
                                            maxPacked,
                                            dim * numBytesPerDim,
                                            dim * numBytesPerDim + numBytesPerDim,
                                            queryMax[dim] as ByteArray,
                                            0,
                                            numBytesPerDim
                                        )
                                                > 0)
                                    ) {
                                        crosses = true
                                    }
                                }

                                if (crosses) {
                                    // System.out.println("  query_crosses_cell");
                                    return PointValues.Relation.CELL_CROSSES_QUERY
                                } else {
                                    // System.out.println("  cell_inside_query");
                                    return PointValues.Relation.CELL_INSIDE_QUERY
                                }
                            }
                        })
                }

                val expected = BitSet()
                for (ord in 0..<numValues) {
                    var matches = true
                    for (dim in 0..<numIndexDims) {
                        val x = docValues[ord][dim]
                        if (Arrays.compareUnsigned(x, 0, numBytesPerDim, queryMin[dim] as ByteArray, 0, numBytesPerDim) < 0
                            || (Arrays.compareUnsigned(x, 0, numBytesPerDim, queryMax[dim] as ByteArray, 0, numBytesPerDim)
                                    > 0)
                        ) {
                            matches = false
                            break
                        }
                    }

                    if (matches) {
                        val id: Int
                        if (ids == null) {
                            id = ord
                        } else {
                            id = ids[ord]
                        }
                        expected.set(id)
                    }
                }

                val limit: Int = max(expected.length(), hits.length())
                var failCount = 0
                var successCount = 0
                for (id in 0..<limit) {
                    if (expected[id] != hits[id]) {
                        println("FAIL: id=$id")
                        failCount++
                    } else {
                        successCount++
                    }
                }

                if (failCount != 0) {
                    for (docID in 0..<r.maxDoc()) {
                        println("  docID=" + docID + " id=" + docIDToID[docID])
                    }

                    fail("$failCount docs failed; $successCount docs succeeded")
                }
            }
        } finally {
            IOUtils.closeWhileHandlingException(r, w, saveW, if (saveDir == null) null else dir)
        }
    }

    @Throws(IOException::class)
    private fun assertSize(tree: PointValues.PointTree) {
        var tree: PointValues.PointTree = tree
        val clone: PointValues.PointTree = tree.clone()
        assertEquals(clone.size(), tree.size())
        // rarely continue with the clone tree
        tree = if (rarely()) clone else tree
        val visitDocIDSize = longArrayOf(0)
        val visitDocValuesSize = longArrayOf(0)
        val visitor: PointValues.IntersectVisitor =
            object : PointValues.IntersectVisitor {
                override fun visit(docID: Int) {
                    visitDocIDSize[0]++
                }

                override fun visit(docID: Int, packedValue: ByteArray) {
                    visitDocValuesSize[0]++
                }

                override fun compare(minPackedValue: ByteArray, maxPackedValue: ByteArray): PointValues.Relation {
                    return PointValues.Relation.CELL_CROSSES_QUERY
                }
            }
        if (random().nextBoolean()) {
            tree.visitDocIDs(visitor)
            tree.visitDocValues(visitor)
        } else {
            tree.visitDocValues(visitor)
            tree.visitDocIDs(visitor)
        }
        assertEquals(visitDocIDSize[0], visitDocValuesSize[0])
        assertEquals(visitDocIDSize[0], tree.size())
        if (tree.moveToChild()) {
            do {
                randomPointTreeNavigation(tree)
                assertSize(tree)
            } while (tree.moveToSibling())
            tree.moveToParent()
        }
    }

    @Throws(IOException::class)
    private fun randomPointTreeNavigation(tree: PointValues.PointTree) {
        val minPackedValue: ByteArray = tree.minPackedValue.copyOf()
        val maxPackedValue: ByteArray = tree.maxPackedValue.copyOf()
        val size: Long = tree.size()
        if (random().nextBoolean() && tree.moveToChild()) {
            randomPointTreeNavigation(tree)
            if (random().nextBoolean() && tree.moveToSibling()) {
                randomPointTreeNavigation(tree)
            }
            tree.moveToParent()
        }
        // we always finish on the same node we started
        assertArrayEquals(minPackedValue, tree.minPackedValue)
        assertArrayEquals(maxPackedValue, tree.maxPackedValue)
        assertEquals(size, tree.size())
    }

    @Throws(IOException::class)
    open fun testAddIndexes() {
        val dir1: Directory = newDirectory()
        var w = RandomIndexWriter(random(), dir1)
        var doc = Document()
        doc.add(IntPoint("int1", 17))
        w.addDocument(doc)
        doc = Document()
        doc.add(IntPoint("int2", 42))
        w.addDocument(doc)
        w.close()

        // Different field number assigments:
        val dir2: Directory = newDirectory()
        w = RandomIndexWriter(random(), dir2)
        doc = Document()
        doc.add(IntPoint("int2", 42))
        w.addDocument(doc)
        doc = Document()
        doc.add(IntPoint("int1", 17))
        w.addDocument(doc)
        w.close()

        val dir: Directory = newDirectory()
        w = RandomIndexWriter(random(), dir)
        w.addIndexes(*arrayOf(dir1, dir2))
        w.forceMerge(1)

        val r: DirectoryReader = w.reader
        val s: IndexSearcher = newSearcher(r, false)
        assertEquals(2, s.count(IntPoint.newExactQuery("int1", 17)).toLong())
        assertEquals(2, s.count(IntPoint.newExactQuery("int2", 42)).toLong())
        r.close()
        w.close()
        dir.close()
        dir1.close()
        dir2.close()
    }

    @Throws(IOException::class)
    private fun switchIndex(w: RandomIndexWriter, dir: Directory, saveW: RandomIndexWriter) {
        if (random().nextBoolean()) {
            // Add via readers:
            w.reader.use { r ->
                if (random().nextBoolean()) {
                    // Add via CodecReaders:
                    val subs: MutableList<CodecReader> = mutableListOf()
                    for (context in r.leaves()) {
                        subs.add(context.reader() as CodecReader)
                    }
                    if (VERBOSE) {
                        println("TEST: now use addIndexes(CodecReader[]) to switch writers")
                    }
                    saveW.addIndexes(*subs.toTypedArray<CodecReader>())
                } else {
                    if (VERBOSE) {
                        println(
                            "TEST: now use TestUtil.addIndexesSlowly(DirectoryReader[]) to switch writers"
                        )
                    }
                    TestUtil.addIndexesSlowly(saveW.w, r)
                }
            }
        } else {
            // Add via directory:
            if (VERBOSE) {
                println("TEST: now use addIndexes(Directory[]) to switch writers")
            }
            w.close()
            saveW.addIndexes(*arrayOf(dir))
        }
        w.close()
        dir.close()
    }

    private fun randomBigInt(numBytes: Int): BigInteger {
        var x: BigInteger = randomBigInteger(numBytes * 8 - 1, random())
        if (random().nextBoolean()) {
            x = x.negate()
        }
        return x
    }

    @Throws(IOException::class)
    private fun getDirectory(numPoints: Int): Directory {
        val dir: Directory
        if (numPoints > 100000) {
            dir = newFSDirectory(createTempDir("TestBKDTree"))
        } else {
            dir = newDirectory()
        }
        // dir = FSDirectory.open(createTempDir());
        return dir
    }

    override fun mergeIsStable(): Boolean {
        // suppress this test from base class: merges for BKD trees are not stable because the tree
        // created by merge will have a different
        // structure than the tree created by adding points separately
        return false
    }

    // LUCENE-7491
    @Throws(Exception::class)
    open fun testMergeMissing() {
        val dir: Directory = newDirectory()
        val iwc: IndexWriterConfig = newIndexWriterConfig()
        val w = RandomIndexWriter(random(), dir, iwc)
        iwc.setMaxBufferedDocs(2)
        for (i in 0..1) {
            val doc = Document()
            doc.add(IntPoint("int", i))
            w.addDocument(doc)
        }

        // index has 1 segment now (with 2 docs) and that segment does have points
        val doc = Document()
        doc.add(IntPoint("id", 0))
        w.addDocument(doc)
        // now we write another segment where the id field does have points:
        w.forceMerge(1)
        IOUtils.close(w, dir)
    }

    open fun testDocCountEdgeCases() {
        val visitor: PointValues.IntersectVisitor =
            object : PointValues.IntersectVisitor {
                override fun visit(docID: Int) {}

                override fun visit(docID: Int, packedValue: ByteArray) {}

                override fun compare(minPackedValue: ByteArray, maxPackedValue: ByteArray): PointValues.Relation {
                    return PointValues.Relation.CELL_INSIDE_QUERY
                }
            }
        var values: PointValues = getPointValues(Long.MAX_VALUE, 1, Long.MAX_VALUE)
        var docs: Long = values.estimateDocCount(visitor)
        assertEquals(1, docs)
        values = getPointValues(Long.MAX_VALUE, 1, 1)
        docs = values.estimateDocCount(visitor)
        assertEquals(1, docs)
        values = getPointValues(Long.MAX_VALUE, Int.MAX_VALUE, Long.MAX_VALUE)
        docs = values.estimateDocCount(visitor)
        assertEquals(Int.MAX_VALUE.toLong(), docs)
        values = getPointValues(Long.MAX_VALUE, Int.MAX_VALUE, Long.MAX_VALUE / 2)
        docs = values.estimateDocCount(visitor)
        assertEquals(Int.MAX_VALUE.toLong(), docs)
        values = getPointValues(Long.MAX_VALUE, Int.MAX_VALUE, 1)
        docs = values.estimateDocCount(visitor)
        assertEquals(1, docs)
    }

    open fun testRandomDocCount() {
        for (i in 0..99) {
            val size: Long = TestUtil.nextLong(random(), 1, Long.MAX_VALUE)
            val maxDoc = if (size > Int.MAX_VALUE) Int.MAX_VALUE else Math.toIntExact(size)
            val docCount: Int = TestUtil.nextInt(random(), 1, maxDoc)
            val estimatedPointCount: Long = TestUtil.nextLong(random(), 0, size)
            val values: PointValues = getPointValues(size, docCount, estimatedPointCount)
            val docs: Long =
                values.estimateDocCount(
                    object : PointValues.IntersectVisitor {
                        override fun visit(docID: Int) {}

                        override fun visit(docID: Int, packedValue: ByteArray) {}

                        override fun compare(minPackedValue: ByteArray, maxPackedValue: ByteArray): PointValues.Relation {
                            return PointValues.Relation.CELL_INSIDE_QUERY
                        }
                    })
            assertTrue(docs <= estimatedPointCount)
            assertTrue(docs <= maxDoc)
            assertTrue(docs >= estimatedPointCount / (size / docCount))
        }
    }

    private fun getPointValues(size: Long, docCount: Int, estimatedPointCount: Long): PointValues {
        return object : PointValues() {
            override val pointTree: PointTree
                get() = object : PointTree {
                    override fun clone(): PointTree {
                        throw UnsupportedOperationException()
                    }

                    override fun moveToChild(): Boolean {
                        return false
                    }

                    override fun moveToSibling(): Boolean {
                        return false
                    }

                    override fun moveToParent(): Boolean {
                        return false
                    }

                    override val minPackedValue: ByteArray
                        get() = ByteArray(0)

                    override val maxPackedValue: ByteArray
                        get() = ByteArray(0)

                    override fun size(): Long {
                        return estimatedPointCount
                    }

                    override fun visitDocIDs(visitor: IntersectVisitor) {
                        throw UnsupportedOperationException()
                    }

                    override fun visitDocValues(visitor: IntersectVisitor) {
                        throw UnsupportedOperationException()
                    }
                }

            override val minPackedValue: ByteArray
                get() {
                    throw UnsupportedOperationException()
                }

            override val maxPackedValue: ByteArray
                get() {
                    throw UnsupportedOperationException()
                }

            override val numDimensions: Int
                get() {
                    throw UnsupportedOperationException()
                }

            override val numIndexDimensions: Int
                get() {
                    throw UnsupportedOperationException()
                }

            override val bytesPerDimension: Int
                get() {
                    throw UnsupportedOperationException()
                }

            override fun size(): Long {
                return size
            }

            override val docCount: Int
                get() = docCount
        }
    }

    @Throws(Exception::class)
    open fun testMismatchedFields() {
        val dir1: Directory = newDirectory()
        val w1 = IndexWriter(dir1, newIndexWriterConfig())
        val doc = Document()
        doc.add(LongPoint("f", 1L))
        doc.add(LongPoint("g", 42L, 43L))
        w1.addDocument(doc)

        val dir2: Directory = newDirectory()
        val w2 =
            IndexWriter(dir2, newIndexWriterConfig().setMergeScheduler(SerialMergeScheduler()))
        w2.addDocument(doc)
        w2.commit()

        var reader: DirectoryReader = DirectoryReader.open(w1)
        w1.close()
        w2.addIndexes(MismatchedCodecReader(getOnlyLeafReader(reader) as CodecReader, random()))
        reader.close()
        w2.forceMerge(1)
        reader = DirectoryReader.open(w2)
        w2.close()

        val leafReader: LeafReader = getOnlyLeafReader(reader)
        assertEquals(2, leafReader.maxDoc().toLong())

        val fPoints: PointValues = leafReader.getPointValues("f")!!
        assertEquals(2, fPoints.size())
        fPoints.intersect(
            object : PointValues.IntersectVisitor {
                var expectedDoc: Int = 0

                @Throws(IOException::class)
                override fun visit(docID: Int, packedValue: ByteArray) {
                    assertEquals(LongPoint.pack(1L), BytesRef(packedValue))
                    assertEquals((expectedDoc++).toLong(), docID.toLong())
                }

                @Throws(IOException::class)
                override fun visit(docID: Int) {
                    throw UnsupportedOperationException()
                }

                override fun compare(minPackedValue: ByteArray, maxPackedValue: ByteArray): PointValues.Relation {
                    return PointValues.Relation.CELL_CROSSES_QUERY
                }
            })

        val gPoints: PointValues = leafReader.getPointValues("g")!!
        assertEquals(2, fPoints.size())
        gPoints.intersect(
            object : PointValues.IntersectVisitor {
                var expectedDoc: Int = 0

                @Throws(IOException::class)
                override fun visit(docID: Int, packedValue: ByteArray) {
                    assertEquals(LongPoint.pack(42L, 43L), BytesRef(packedValue))
                    assertEquals((expectedDoc++).toLong(), docID.toLong())
                }

                @Throws(IOException::class)
                override fun visit(docID: Int) {
                    throw UnsupportedOperationException()
                }

                override fun compare(minPackedValue: ByteArray, maxPackedValue: ByteArray): PointValues.Relation {
                    return PointValues.Relation.CELL_CROSSES_QUERY
                }
            })

        IOUtils.close(reader, w2, dir1, dir2)
    }

    companion object {
        private val CONCURRENT_MERGE_SCHEDULER_ACCESS: ConcurrentMergeSchedulerAccess = TestSecrets.concurrentMergeSchedulerAccess
    }
}
