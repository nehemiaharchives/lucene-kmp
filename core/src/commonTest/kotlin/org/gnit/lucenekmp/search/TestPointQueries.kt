/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.codecs.FilterCodec
import org.gnit.lucenekmp.codecs.PointsFormat
import org.gnit.lucenekmp.codecs.PointsReader
import org.gnit.lucenekmp.codecs.PointsWriter
import org.gnit.lucenekmp.codecs.lucene90.Lucene90PointsReader
import org.gnit.lucenekmp.codecs.lucene90.Lucene90PointsWriter
import org.gnit.lucenekmp.document.BinaryPoint
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.DoublePoint
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FloatPoint
import org.gnit.lucenekmp.document.IntPoint
import org.gnit.lucenekmp.document.LongPoint
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.SortedNumericDocValuesField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.MultiDocValues
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.SegmentWriteState
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.BitSet
import org.gnit.lucenekmp.jdkport.CountDownLatch
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.Thread
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.junitport.assertArrayEquals
import org.gnit.lucenekmp.tests.search.FixedBitSetCollector
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.LuceneTestCase.Companion.Nightly
import org.gnit.lucenekmp.tests.util.LuceneTestCase.Companion.SuppressCodecs
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.NumericUtils
import org.gnit.lucenekmp.util.bkd.BKDConfig
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.compare
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

@SuppressCodecs("SimpleText")
@OptIn(ExperimentalAtomicApi::class)
class TestPointQueries : LuceneTestCase() {

    @Test
    @Throws(Exception::class)
    fun testBasicInts() {
        val dir = newDirectory()
        val w = IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random())))

        var doc = Document()
        doc.add(IntPoint("point", -7))
        w.addDocument(doc)

        doc = Document()
        doc.add(IntPoint("point", 0))
        w.addDocument(doc)

        doc = Document()
        doc.add(IntPoint("point", 3))
        w.addDocument(doc)

        val r = DirectoryReader.open(w)
        val s = IndexSearcher(r)
        assertEquals(2, s.count(IntPoint.newRangeQuery("point", -8, 1)))
        assertEquals(3, s.count(IntPoint.newRangeQuery("point", -7, 3)))
        assertEquals(1, s.count(IntPoint.newExactQuery("point", -7)))
        assertEquals(0, s.count(IntPoint.newExactQuery("point", -6)))
        w.close()
        r.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testBasicFloats() {
        val dir = newDirectory()
        val w = IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random())))

        var doc = Document()
        doc.add(FloatPoint("point", -7.0f))
        w.addDocument(doc)

        doc = Document()
        doc.add(FloatPoint("point", 0.0f))
        w.addDocument(doc)

        doc = Document()
        doc.add(FloatPoint("point", 3.0f))
        w.addDocument(doc)

        val r = DirectoryReader.open(w)
        val s = IndexSearcher(r)
        assertEquals(2, s.count(FloatPoint.newRangeQuery("point", -8.0f, 1.0f)))
        assertEquals(3, s.count(FloatPoint.newRangeQuery("point", -7.0f, 3.0f)))
        assertEquals(1, s.count(FloatPoint.newExactQuery("point", -7.0f)))
        assertEquals(0, s.count(FloatPoint.newExactQuery("point", -6.0f)))
        w.close()
        r.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testBasicLongs() {
        val dir = newDirectory()
        val w = IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random())))

        var doc = Document()
        doc.add(LongPoint("point", -7))
        w.addDocument(doc)

        doc = Document()
        doc.add(LongPoint("point", 0))
        w.addDocument(doc)

        doc = Document()
        doc.add(LongPoint("point", 3))
        w.addDocument(doc)

        val r = DirectoryReader.open(w)
        val s = IndexSearcher(r)
        assertEquals(2, s.count(LongPoint.newRangeQuery("point", -8L, 1L)))
        assertEquals(3, s.count(LongPoint.newRangeQuery("point", -7L, 3L)))
        assertEquals(1, s.count(LongPoint.newExactQuery("point", -7L)))
        assertEquals(0, s.count(LongPoint.newExactQuery("point", -6L)))
        w.close()
        r.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testBasicDoubles() {
        val dir = newDirectory()
        val w = IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random())))

        var doc = Document()
        doc.add(DoublePoint("point", -7.0))
        w.addDocument(doc)

        doc = Document()
        doc.add(DoublePoint("point", 0.0))
        w.addDocument(doc)

        doc = Document()
        doc.add(DoublePoint("point", 3.0))
        w.addDocument(doc)

        val r = DirectoryReader.open(w)
        val s = IndexSearcher(r)
        assertEquals(2, s.count(DoublePoint.newRangeQuery("point", -8.0, 1.0)))
        assertEquals(3, s.count(DoublePoint.newRangeQuery("point", -7.0, 3.0)))
        assertEquals(1, s.count(DoublePoint.newExactQuery("point", -7.0)))
        assertEquals(0, s.count(DoublePoint.newExactQuery("point", -6.0)))
        w.close()
        r.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testCrazyDoubles() {
        val dir = newDirectory()
        val w = IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random())))

        var doc = Document()
        doc.add(DoublePoint("point", Double.NEGATIVE_INFINITY))
        w.addDocument(doc)

        doc = Document()
        doc.add(DoublePoint("point", -0.0))
        w.addDocument(doc)

        doc = Document()
        doc.add(DoublePoint("point", +0.0))
        w.addDocument(doc)

        doc = Document()
        doc.add(DoublePoint("point", Double.MIN_VALUE))
        w.addDocument(doc)

        doc = Document()
        doc.add(DoublePoint("point", Double.MAX_VALUE))
        w.addDocument(doc)

        doc = Document()
        doc.add(DoublePoint("point", Double.POSITIVE_INFINITY))
        w.addDocument(doc)

        doc = Document()
        doc.add(DoublePoint("point", Double.NaN))
        w.addDocument(doc)

        val r = DirectoryReader.open(w)
        val s = IndexSearcher(r)

        // exact queries
        assertEquals(1, s.count(DoublePoint.newExactQuery("point", Double.NEGATIVE_INFINITY)))
        assertEquals(1, s.count(DoublePoint.newExactQuery("point", -0.0)))
        assertEquals(1, s.count(DoublePoint.newExactQuery("point", +0.0)))
        assertEquals(1, s.count(DoublePoint.newExactQuery("point", Double.MIN_VALUE)))
        assertEquals(1, s.count(DoublePoint.newExactQuery("point", Double.MAX_VALUE)))
        assertEquals(1, s.count(DoublePoint.newExactQuery("point", Double.POSITIVE_INFINITY)))
        assertEquals(1, s.count(DoublePoint.newExactQuery("point", Double.NaN)))

        // set query
        val set = doubleArrayOf(
            Double.MAX_VALUE,
            Double.NaN,
            +0.0,
            Double.NEGATIVE_INFINITY,
            Double.MIN_VALUE,
            -0.0,
            Double.POSITIVE_INFINITY
        )
        assertEquals(7, s.count(DoublePoint.newSetQuery("point", *set)))

        // ranges
        assertEquals(2, s.count(DoublePoint.newRangeQuery("point", Double.NEGATIVE_INFINITY, -0.0)))
        assertEquals(2, s.count(DoublePoint.newRangeQuery("point", -0.0, 0.0)))
        assertEquals(2, s.count(DoublePoint.newRangeQuery("point", 0.0, Double.MIN_VALUE)))
        assertEquals(2, s.count(DoublePoint.newRangeQuery("point", Double.MIN_VALUE, Double.MAX_VALUE)))
        assertEquals(2, s.count(DoublePoint.newRangeQuery("point", Double.MAX_VALUE, Double.POSITIVE_INFINITY)))
        assertEquals(2, s.count(DoublePoint.newRangeQuery("point", Double.POSITIVE_INFINITY, Double.NaN)))

        w.close()
        r.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testCrazyFloats() {
        val dir = newDirectory()
        val w = IndexWriter(dir, IndexWriterConfig(MockAnalyzer(random())))

        var doc = Document()
        doc.add(FloatPoint("point", Float.NEGATIVE_INFINITY))
        w.addDocument(doc)

        doc = Document()
        doc.add(FloatPoint("point", -0.0f))
        w.addDocument(doc)

        doc = Document()
        doc.add(FloatPoint("point", +0.0f))
        w.addDocument(doc)

        doc = Document()
        doc.add(FloatPoint("point", Float.MIN_VALUE))
        w.addDocument(doc)

        doc = Document()
        doc.add(FloatPoint("point", Float.MAX_VALUE))
        w.addDocument(doc)

        doc = Document()
        doc.add(FloatPoint("point", Float.POSITIVE_INFINITY))
        w.addDocument(doc)

        doc = Document()
        doc.add(FloatPoint("point", Float.NaN))
        w.addDocument(doc)

        val r = DirectoryReader.open(w)
        val s = IndexSearcher(r)

        // exact queries
        assertEquals(1, s.count(FloatPoint.newExactQuery("point", Float.NEGATIVE_INFINITY)))
        assertEquals(1, s.count(FloatPoint.newExactQuery("point", -0.0f)))
        assertEquals(1, s.count(FloatPoint.newExactQuery("point", +0.0f)))
        assertEquals(1, s.count(FloatPoint.newExactQuery("point", Float.MIN_VALUE)))
        assertEquals(1, s.count(FloatPoint.newExactQuery("point", Float.MAX_VALUE)))
        assertEquals(1, s.count(FloatPoint.newExactQuery("point", Float.POSITIVE_INFINITY)))
        assertEquals(1, s.count(FloatPoint.newExactQuery("point", Float.NaN)))

        // set query
        val set = floatArrayOf(
            Float.MAX_VALUE,
            Float.NaN,
            +0.0f,
            Float.NEGATIVE_INFINITY,
            Float.MIN_VALUE,
            -0.0f,
            Float.POSITIVE_INFINITY
        )
        assertEquals(7, s.count(FloatPoint.newSetQuery("point", *set)))

        // ranges
        assertEquals(2, s.count(FloatPoint.newRangeQuery("point", Float.NEGATIVE_INFINITY, -0.0f)))
        assertEquals(2, s.count(FloatPoint.newRangeQuery("point", -0.0f, 0.0f)))
        assertEquals(2, s.count(FloatPoint.newRangeQuery("point", 0.0f, Float.MIN_VALUE)))
        assertEquals(2, s.count(FloatPoint.newRangeQuery("point", Float.MIN_VALUE, Float.MAX_VALUE)))
        assertEquals(2, s.count(FloatPoint.newRangeQuery("point", Float.MAX_VALUE, Float.POSITIVE_INFINITY)))
        assertEquals(2, s.count(FloatPoint.newRangeQuery("point", Float.POSITIVE_INFINITY, Float.NaN)))

        w.close()
        r.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testAllEqual() {
        val numValues = atLeast(1000)
        val value = randomValue()
        val values = LongArray(numValues)

        if (VERBOSE) {
            println("TEST: use same value=$value")
        }
        values.fill(value)

        verifyLongs(values, null)
    }

    @Test
    @Throws(Exception::class)
    fun testRandomLongsTiny() {
        // Make sure single-leaf-node case is OK:
        doTestRandomLongs(10)
    }

    @Test
    @Throws(Exception::class)
    fun testRandomLongsMedium() {
        doTestRandomLongs(1000)
    }

    @Test
    @Throws(Exception::class)
    fun testRandomLongsBig() {
        doTestRandomLongs(2000) // TODO reduced from 20_000 to 2000 for dev speed
    }

    @Throws(Exception::class)
    private fun doTestRandomLongs(count: Int) {

        val numValues = TestUtil.nextInt(random(), count, count * 2)

        if (VERBOSE) {
            println("TEST: numValues=$numValues")
        }

        val values = LongArray(numValues)
        val ids = IntArray(numValues)

        val singleValued = random().nextBoolean()

        val sameValuePct = random().nextInt(100)

        var id = 0
        for (ord in 0 until numValues) {
            if (ord > 0 && random().nextInt(100) < sameValuePct) {
                // Identical to old value
                values[ord] = values[random().nextInt(ord)]
            } else {
                values[ord] = randomValue()
            }

            ids[ord] = id
            if (singleValued || random().nextInt(2) == 1) {
                id++
            }
        }

        verifyLongs(values, ids)
    }

    @Test
    fun testLongEncode() {
        for (i in 0 until 10000) {
            val v = random().nextLong()
            val tmp = ByteArray(8)
            NumericUtils.longToSortableBytes(v, tmp, 0)
            val v2 = NumericUtils.sortableBytesToLong(tmp, 0)
            assertEquals(v, v2, "got bytes=${tmp.contentToString()}")
        }
    }

    // verify for long values
    @Throws(Exception::class)
    private fun verifyLongs(values: LongArray, ids: IntArray?) {
        val iwc = newIndexWriterConfig()

        // Else we can get O(N^2) merging:
        val mbd = iwc.maxBufferedDocs
        if (mbd != -1 && mbd < values.size / 100) {
            iwc.setMaxBufferedDocs(values.size / 100)
        }
        iwc.setCodec(getCodec())
        val dir: Directory
        if (values.size > 100000) {
            dir = newFSDirectory(createTempDir("TestRangeTree"))
        } else {
            dir = newDirectory()
        }

        /*
        The point range query chooses only considers using an inverse BKD visitor if
        there is exactly one value per document. If any document misses a value that
        code is not exercised. Using a nextBoolean() here increases the likelihood
        that there is no missing values, making the test more likely to test that code.
        */
        val missingPct = if (random().nextBoolean()) 0 else random().nextInt(100)
        val deletedPct = random().nextInt(100)
        if (VERBOSE) {
            println("  missingPct=$missingPct")
            println("  deletedPct=$deletedPct")
        }

        val missing = BitSet()
        val deleted = BitSet()

        var doc: Document? = null
        var lastID = -1

        val w = IndexWriter(dir, iwc)
        for (ord in values.indices) {
            val id: Int = if (ids == null) {
                ord
            } else {
                ids[ord]
            }
            if (id != lastID) {
                if (random().nextInt(100) < missingPct) {
                    missing.set(id)
                    if (VERBOSE) {
                        println("  missing id=$id")
                    }
                }

                if (doc != null) {
                    w.addDocument(doc)
                    if (random().nextInt(100) < deletedPct) {
                        val idToDelete = random().nextInt(id)
                        w.deleteDocuments(Term("id", "" + idToDelete))
                        deleted.set(idToDelete)
                        if (VERBOSE) {
                            println("  delete id=$idToDelete")
                        }
                    }
                }

                doc = Document()
                doc.add(newStringField("id", "" + id, Field.Store.NO))
                doc.add(NumericDocValuesField("id", id.toLong()))
                lastID = id
            }

            if (!missing.get(id)) {
                doc!!.add(LongPoint("sn_value", values[id]))
                val bytes = ByteArray(8)
                NumericUtils.longToSortableBytes(values[id], bytes, 0)
                doc.add(BinaryPoint("ss_value", arrayOf(bytes)))
            }
        }

        w.addDocument(doc!!)

        if (random().nextBoolean()) {
            if (VERBOSE) {
                println("  forceMerge(1)")
            }
            w.forceMerge(1)
        }
        val r: IndexReader = DirectoryReader.open(w)
        w.close()

        val s = newSearcher(r, false)

        val numThreads = TestUtil.nextInt(random(), 2, 5)

        if (VERBOSE) {
            println("TEST: use $numThreads query threads; searcher=$s")
        }

        val threads = mutableListOf<Thread>()
        val iters = atLeast(100)

        val startingGun = CountDownLatch(1)
        val failed = AtomicBoolean(false)

        for (i in 0 until numThreads) {
            val thread = object : Thread() {
                override fun run() {
                    try {
                        _run()
                    } catch (e: Exception) {
                        failed.store(true)
                        throw RuntimeException(e)
                    }
                }

                private fun _run() {
                    startingGun.await()

                    var iter = 0
                    while (iter < iters && !failed.load()) {
                        var lower = randomValue()
                        var upper = randomValue()

                        if (upper < lower) {
                            val x = lower
                            lower = upper
                            upper = x
                        }

                        val query: Query

                        if (VERBOSE) {
                            println(
                                "\n${Thread.currentThread().getName()}: TEST: iter=$iter value=$lower TO $upper"
                            )
                            val tmp = ByteArray(8)
                            NumericUtils.longToSortableBytes(lower, tmp, 0)
                            println("  lower bytes=${tmp.contentToString()}")
                            NumericUtils.longToSortableBytes(upper, tmp, 0)
                            println("  upper bytes=${tmp.contentToString()}")
                        }

                        if (random().nextBoolean()) {
                            query = LongPoint.newRangeQuery("sn_value", lower, upper)
                        } else {
                            val lowerBytes = ByteArray(8)
                            NumericUtils.longToSortableBytes(lower, lowerBytes, 0)
                            val upperBytes = ByteArray(8)
                            NumericUtils.longToSortableBytes(upper, upperBytes, 0)
                            query = BinaryPoint.newRangeQuery("ss_value", lowerBytes, upperBytes)
                        }

                        if (VERBOSE) {
                            println("${Thread.currentThread().getName()}:  using query: $query")
                        }

                        val hits: FixedBitSet =
                            s.search(query, FixedBitSetCollector.createManager(r.maxDoc()))

                        if (VERBOSE) {
                            println("${Thread.currentThread().getName()}:  hitCount: ${hits.cardinality()}")
                        }

                        val docIDToID: NumericDocValues = MultiDocValues.getNumericValues(r, "id")!!

                        for (docID in 0 until r.maxDoc()) {
                            assertEquals(docID, docIDToID.nextDoc())
                            val id = docIDToID.longValue().toInt()
                            val expected =
                                !missing.get(id) &&
                                        !deleted.get(id) &&
                                        values[id] >= lower &&
                                        values[id] <= upper
                            if (hits.get(docID) != expected) {
                                // We do exact quantized comparison so the bbox query should never disagree:
                                fail(
                                    "${Thread.currentThread().getName()}: iter=$iter id=$id docID=$docID value=${values[id]}" +
                                            " (range: $lower TO $upper) expected $expected but got: ${hits.get(docID)}" +
                                            " deleted?=${deleted.get(id)} query=$query"
                                )
                            }
                        }
                        iter++
                    }
                }
            }
            thread.setName("T$i")
            thread.start()
            threads.add(thread)
        }
        startingGun.countDown()
        for (thread in threads) {
            thread.join()
        }
        IOUtils.close(r, dir)
    }

    @Test
    @Throws(Exception::class)
    fun testRandomBinaryTiny() {
        doTestRandomBinary(10)
    }

    @Test
    @Throws(Exception::class)
    fun testRandomBinaryMedium() {
        doTestRandomBinary(1000)
    }

    @Throws(Exception::class)
    private fun doTestRandomBinary(count: Int) {
        val numValues = TestUtil.nextInt(random(), count, count * 2)
        val numBytesPerDim = TestUtil.nextInt(random(), 2, PointValues.MAX_NUM_BYTES)
        val numDims = TestUtil.nextInt(random(), 1, PointValues.MAX_INDEX_DIMENSIONS)

        val sameValuePct = random().nextInt(100)
        if (VERBOSE) {
            println("TEST: sameValuePct=$sameValuePct")
        }

        val docValues = Array(numValues) { Array(0) { ByteArray(0) } }

        val singleValued = random().nextBoolean()
        val ids = IntArray(numValues)

        var id = 0
        if (VERBOSE) {
            println("Picking values: $numValues")
        }
        for (ord in 0 until numValues) {
            if (ord > 0 && random().nextInt(100) < sameValuePct) {
                // Identical to old value
                docValues[ord] = docValues[random().nextInt(ord)]
            } else {
                // Make a new random value
                val values = Array(numDims) { ByteArray(numBytesPerDim) }
                for (dim in 0 until numDims) {
                    random().nextBytes(values[dim])
                }
                docValues[ord] = values
            }
            ids[ord] = id
            if (singleValued || random().nextInt(2) == 1) {
                id++
            }
        }

        verifyBinary(docValues, ids, numBytesPerDim)
    }

    // verify for byte[][] values
    @Throws(Exception::class)
    private fun verifyBinary(docValues: Array<Array<ByteArray>>, ids: IntArray, numBytesPerDim: Int) {
        val iwc = newIndexWriterConfig()

        val numDims = docValues[0].size
        val bytesPerDim = docValues[0][0].size

        // Else we can get O(N^2) merging:
        val mbd = iwc.maxBufferedDocs
        if (mbd != -1 && mbd < docValues.size / 100) {
            iwc.setMaxBufferedDocs(docValues.size / 100)
        }
        iwc.setCodec(getCodec())

        val dir: Directory
        if (docValues.size > 100000) {
            dir = newFSDirectory(createTempDir("TestPointQueries"))
        } else {
            dir = newDirectory()
        }

        val w = IndexWriter(dir, iwc)

        val numValues = docValues.size
        if (VERBOSE) {
            println("TEST: numValues=$numValues numDims=$numDims numBytesPerDim=$numBytesPerDim")
        }

        val missingPct = random().nextInt(100)
        val deletedPct = random().nextInt(100)
        if (VERBOSE) {
            println("  missingPct=$missingPct")
            println("  deletedPct=$deletedPct")
        }

        val missing = BitSet()
        val deleted = BitSet()

        var doc: Document? = null
        var lastID = -1

        for (ord in 0 until numValues) {
            if (ord % 1000 == 0) {
                if (VERBOSE) {
                    println("Adding docs: $ord")
                }
            }
            val id = ids[ord]
            if (id != lastID) {
                if (random().nextInt(100) < missingPct) {
                    missing.set(id)
                    if (VERBOSE) {
                        println("  missing id=$id")
                    }
                }

                if (doc != null) {
                    w.addDocument(doc)
                    if (random().nextInt(100) < deletedPct) {
                        val idToDelete = random().nextInt(id)
                        w.deleteDocuments(Term("id", "" + idToDelete))
                        deleted.set(idToDelete)
                        if (VERBOSE) {
                            println("  delete id=$idToDelete")
                        }
                    }
                }

                doc = Document()
                doc.add(newStringField("id", "" + id, Field.Store.NO))
                doc.add(NumericDocValuesField("id", id.toLong()))
                lastID = id
            }

            if (!missing.get(id)) {
                doc!!.add(BinaryPoint("value", docValues[ord]))
                if (VERBOSE) {
                    println("id=$id")
                    for (dim in 0 until numDims) {
                        println("  dim=$dim value=${bytesToString(docValues[ord][dim])}")
                    }
                }
            }
        }

        w.addDocument(doc!!)

        if (random().nextBoolean()) {
            if (VERBOSE) {
                println("  forceMerge(1)")
            }
            w.forceMerge(1)
        }
        val r: IndexReader = DirectoryReader.open(w)
        w.close()

        val s = newSearcher(r, false)

        val numThreads = TestUtil.nextInt(random(), 2, 5)

        if (VERBOSE) {
            println("TEST: use $numThreads query threads; searcher=$s")
        }

        val threads = mutableListOf<Thread>()
        val iters = atLeast(100)

        val startingGun = CountDownLatch(1)
        val failed = AtomicBoolean(false)

        for (i in 0 until numThreads) {
            val thread = object : Thread() {
                override fun run() {
                    try {
                        _run()
                    } catch (e: Exception) {
                        failed.store(true)
                        throw RuntimeException(e)
                    }
                }

                private fun _run() {
                    startingGun.await()

                    var iter = 0
                    while (iter < iters && !failed.load()) {

                        val lower = Array(numDims) { ByteArray(bytesPerDim) }
                        val upper = Array(numDims) { ByteArray(bytesPerDim) }
                        for (dim in 0 until numDims) {
                            random().nextBytes(lower[dim])
                            random().nextBytes(upper[dim])

                            if (Arrays.compareUnsigned(lower[dim], 0, bytesPerDim, upper[dim], 0, bytesPerDim) > 0) {
                                val x = lower[dim]
                                lower[dim] = upper[dim]
                                upper[dim] = x
                            }
                        }

                        if (VERBOSE) {
                            println("\n${Thread.currentThread().getName()}: TEST: iter=$iter")
                            for (dim in 0 until numDims) {
                                println(
                                    "  dim=$dim ${bytesToString(lower[dim])} TO ${bytesToString(upper[dim])}"
                                )
                            }
                        }

                        val query: Query = BinaryPoint.newRangeQuery("value", lower, upper)

                        if (VERBOSE) {
                            println("${Thread.currentThread().getName()}:  using query: $query")
                        }

                        val hits: FixedBitSet =
                            s.search(query, FixedBitSetCollector.createManager(r.maxDoc()))

                        if (VERBOSE) {
                            println("${Thread.currentThread().getName()}:  hitCount: ${hits.cardinality()}")
                        }

                        val expected = BitSet()
                        for (ord in 0 until numValues) {
                            val id = ids[ord]
                            if (!missing.get(id) &&
                                !deleted.get(id) &&
                                matches(bytesPerDim, lower, upper, docValues[ord])
                            ) {
                                expected.set(id)
                            }
                        }

                        val docIDToID: NumericDocValues = MultiDocValues.getNumericValues(r, "id")!!

                        var failCount = 0
                        for (docID in 0 until r.maxDoc()) {
                            assertEquals(docID, docIDToID.nextDoc())
                            val id = docIDToID.longValue().toInt()
                            if (hits.get(docID) != expected.get(id)) {
                                println(
                                    "FAIL: iter=$iter id=$id docID=$docID expected=${expected.get(id)}" +
                                            " but got ${hits.get(docID)} deleted?=${deleted.get(id)}" +
                                            " missing?=${missing.get(id)}"
                                )
                                for (dim in 0 until numDims) {
                                    println(
                                        "  dim=$dim range: ${bytesToString(lower[dim])} TO ${bytesToString(upper[dim])}"
                                    )
                                    failCount++
                                }
                            }
                        }
                        if (failCount != 0) {
                            fail("$failCount hits were wrong")
                        }
                        iter++
                    }
                }
            }
            thread.setName("T$i")
            thread.start()
            threads.add(thread)
        }

        startingGun.countDown()
        for (thread in threads) {
            thread.join()
        }

        IOUtils.close(r, dir)
    }

    @Test
    @Throws(Exception::class)
    fun testMinMaxLong() {
        val dir = newDirectory()
        val iwc = newIndexWriterConfig()
        iwc.setCodec(getCodec())
        val w = RandomIndexWriter(random(), dir, iwc)
        var doc = Document()
        doc.add(LongPoint("value", Long.MIN_VALUE))
        w.addDocument(doc)

        doc = Document()
        doc.add(LongPoint("value", Long.MAX_VALUE))
        w.addDocument(doc)

        val r = w.getReader(true, false)

        val s = newSearcher(r, false)

        assertEquals(1, s.count(LongPoint.newRangeQuery("value", Long.MIN_VALUE, 0L)))
        assertEquals(1, s.count(LongPoint.newRangeQuery("value", 0L, Long.MAX_VALUE)))
        assertEquals(2, s.count(LongPoint.newRangeQuery("value", Long.MIN_VALUE, Long.MAX_VALUE)))

        IOUtils.close(r, w, dir)
    }

    @Test
    @Throws(Exception::class)
    fun testBasicSortedSet() {
        val dir = newDirectory()
        val iwc = newIndexWriterConfig()
        iwc.setCodec(getCodec())
        val w = RandomIndexWriter(random(), dir, iwc)
        var doc = Document()
        doc.add(BinaryPoint("value", arrayOf(toUTF8("abc"))))
        w.addDocument(doc)
        doc = Document()
        doc.add(BinaryPoint("value", arrayOf(toUTF8("def"))))
        w.addDocument(doc)

        val r = w.getReader(true, false)

        val s = newSearcher(r, false)

        assertEquals(1, s.count(BinaryPoint.newRangeQuery("value", toUTF8("aaa"), toUTF8("bbb"))))
        assertEquals(1, s.count(BinaryPoint.newRangeQuery("value", toUTF8("c", 3), toUTF8("e", 3))))
        assertEquals(2, s.count(BinaryPoint.newRangeQuery("value", toUTF8("a", 3), toUTF8("z", 3))))
        assertEquals(1, s.count(BinaryPoint.newRangeQuery("value", toUTF8("", 3), toUTF8("abc"))))
        assertEquals(1, s.count(BinaryPoint.newRangeQuery("value", toUTF8("a", 3), toUTF8("abc"))))
        assertEquals(0, s.count(BinaryPoint.newRangeQuery("value", toUTF8("a", 3), toUTF8("abb"))))
        assertEquals(1, s.count(BinaryPoint.newRangeQuery("value", toUTF8("def"), toUTF8("zzz"))))
        assertEquals(1, s.count(BinaryPoint.newRangeQuery("value", toUTF8("def"), toUTF8("z", 3))))
        assertEquals(0, s.count(BinaryPoint.newRangeQuery("value", toUTF8("deg"), toUTF8("z", 3))))

        IOUtils.close(r, w, dir)
    }

    @Test
    @Throws(Exception::class)
    fun testLongMinMaxNumeric() {
        val dir = newDirectory()
        val iwc = newIndexWriterConfig()
        iwc.setCodec(getCodec())
        val w = RandomIndexWriter(random(), dir, iwc)
        var doc = Document()
        doc.add(LongPoint("value", Long.MIN_VALUE))
        w.addDocument(doc)
        doc = Document()
        doc.add(LongPoint("value", Long.MAX_VALUE))
        w.addDocument(doc)

        val r = w.getReader(true, false)

        val s = newSearcher(r, false)

        assertEquals(2, s.count(LongPoint.newRangeQuery("value", Long.MIN_VALUE, Long.MAX_VALUE)))
        assertEquals(1, s.count(LongPoint.newRangeQuery("value", Long.MIN_VALUE, Long.MAX_VALUE - 1)))
        assertEquals(1, s.count(LongPoint.newRangeQuery("value", Long.MIN_VALUE + 1, Long.MAX_VALUE)))
        assertEquals(0, s.count(LongPoint.newRangeQuery("value", Long.MIN_VALUE + 1, Long.MAX_VALUE - 1)))

        IOUtils.close(r, w, dir)
    }

    @Test
    @Throws(Exception::class)
    fun testLongMinMaxSortedSet() {
        val dir = newDirectory()
        val iwc = newIndexWriterConfig()
        iwc.setCodec(getCodec())
        val w = RandomIndexWriter(random(), dir, iwc)
        var doc = Document()
        doc.add(LongPoint("value", Long.MIN_VALUE))
        w.addDocument(doc)
        doc = Document()
        doc.add(LongPoint("value", Long.MAX_VALUE))
        w.addDocument(doc)

        val r = w.getReader(true, false)

        val s = newSearcher(r, false)

        assertEquals(2, s.count(LongPoint.newRangeQuery("value", Long.MIN_VALUE, Long.MAX_VALUE)))
        assertEquals(1, s.count(LongPoint.newRangeQuery("value", Long.MIN_VALUE, Long.MAX_VALUE - 1)))
        assertEquals(1, s.count(LongPoint.newRangeQuery("value", Long.MIN_VALUE + 1, Long.MAX_VALUE)))
        assertEquals(0, s.count(LongPoint.newRangeQuery("value", Long.MIN_VALUE + 1, Long.MAX_VALUE - 1)))

        IOUtils.close(r, w, dir)
    }

    @Test
    @Throws(Exception::class)
    fun testSortedSetNoOrdsMatch() {
        val dir = newDirectory()
        val iwc = newIndexWriterConfig()
        iwc.setCodec(getCodec())
        val w = RandomIndexWriter(random(), dir, iwc)
        var doc = Document()
        doc.add(BinaryPoint("value", arrayOf(toUTF8("a"))))
        w.addDocument(doc)
        doc = Document()
        doc.add(BinaryPoint("value", arrayOf(toUTF8("z"))))
        w.addDocument(doc)

        val r = w.getReader(true, false)

        val s = newSearcher(r, false)
        assertEquals(0, s.count(BinaryPoint.newRangeQuery("value", toUTF8("m"), toUTF8("m"))))

        IOUtils.close(r, w, dir)
    }

    @Test
    @Throws(Exception::class)
    fun testNumericNoValuesMatch() {
        val dir = newDirectory()
        val iwc = newIndexWriterConfig()
        iwc.setCodec(getCodec())
        val w = RandomIndexWriter(random(), dir, iwc)
        var doc = Document()
        doc.add(SortedNumericDocValuesField("value", 17))
        w.addDocument(doc)
        doc = Document()
        doc.add(SortedNumericDocValuesField("value", 22))
        w.addDocument(doc)

        val r = w.getReader(true, false)

        val s = IndexSearcher(r)
        assertEquals(0, s.count(LongPoint.newRangeQuery("value", 17L, 13L)))

        IOUtils.close(r, w, dir)
    }

    @Test
    @Throws(Exception::class)
    fun testNoDocs() {
        val dir = newDirectory()
        val iwc = newIndexWriterConfig()
        iwc.setCodec(getCodec())
        val w = RandomIndexWriter(random(), dir, iwc)
        w.addDocument(Document())

        val r = w.getReader(true, false)

        val s = newSearcher(r, false)
        assertEquals(0, s.count(LongPoint.newRangeQuery("value", 17L, 13L)))

        IOUtils.close(r, w, dir)
    }

    @Test
    @Throws(Exception::class)
    fun testWrongNumDims() {
        val dir = newDirectory()
        val iwc = newIndexWriterConfig()
        iwc.setCodec(getCodec())
        val w = RandomIndexWriter(random(), dir, iwc)
        val doc = Document()
        doc.add(LongPoint("value", Long.MIN_VALUE))
        w.addDocument(doc)

        val r = w.getReader(true, false)

        // no wrapping, else the exc might happen in executor thread:
        val s = IndexSearcher(r)
        val point = Array(2) { ByteArray(8) }
        val expected = expectThrows(
            IllegalArgumentException::class
        ) {
            s.count(BinaryPoint.newRangeQuery("value", point, point))
        }
        assertEquals(
            "field=\"value\" was indexed with numIndexDimensions=1 but this query has numDims=2",
            expected.message
        )

        IOUtils.close(r, w, dir)
    }

    @Test
    @Throws(Exception::class)
    fun testWrongNumBytes() {
        val dir = newDirectory()
        val iwc = newIndexWriterConfig()
        iwc.setCodec(getCodec())
        val w = RandomIndexWriter(random(), dir, iwc)
        val doc = Document()
        doc.add(LongPoint("value", Long.MIN_VALUE))
        w.addDocument(doc)

        val r = w.getReader(true, false)

        // no wrapping, else the exc might happen in executor thread:
        val s = IndexSearcher(r)
        val point = arrayOf(ByteArray(10))
        val expected = expectThrows(
            IllegalArgumentException::class
        ) {
            s.count(BinaryPoint.newRangeQuery("value", point, point))
        }
        assertEquals(
            "field=\"value\" was indexed with bytesPerDim=8 but this query has bytesPerDim=10",
            expected.message
        )

        IOUtils.close(r, w, dir)
    }

    @Test
    @Throws(Exception::class)
    fun testAllPointDocsWereDeletedAndThenMergedAgain() {
        val dir = newDirectory()
        val iwc = newIndexWriterConfig()
        iwc.setCodec(getCodec())
        val w = IndexWriter(dir, iwc)
        var doc = Document()
        doc.add(StringField("id", "0", Field.Store.NO))
        doc.add(LongPoint("value", 0L))
        w.addDocument(doc)

        // Add document that won't be deleted to avoid IW dropping
        // segment below since it's 100% deleted:
        w.addDocument(Document())
        w.commit()

        // Need another segment so we invoke BKDWriter.merge
        doc = Document()
        doc.add(StringField("id", "0", Field.Store.NO))
        doc.add(LongPoint("value", 0L))
        w.addDocument(doc)
        w.addDocument(Document())

        w.deleteDocuments(Term("id", "0"))
        w.forceMerge(1)

        doc = Document()
        doc.add(StringField("id", "0", Field.Store.NO))
        doc.add(LongPoint("value", 0L))
        w.addDocument(doc)
        w.addDocument(Document())

        w.deleteDocuments(Term("id", "0"))
        w.forceMerge(1)

        IOUtils.close(w, dir)
    }

    @Test
    @Throws(Exception::class)
    fun testExactPoints() {
        val dir = newDirectory()
        val iwc = newIndexWriterConfig()
        iwc.setCodec(getCodec())
        val w = IndexWriter(dir, iwc)

        var doc = Document()
        doc.add(LongPoint("long", 5L))
        w.addDocument(doc)

        doc = Document()
        doc.add(IntPoint("int", 42))
        w.addDocument(doc)

        doc = Document()
        doc.add(FloatPoint("float", 2.0f))
        w.addDocument(doc)

        doc = Document()
        doc.add(DoublePoint("double", 1.0))
        w.addDocument(doc)

        val r = DirectoryReader.open(w)
        val s = newSearcher(r, false)
        assertEquals(1, s.count(IntPoint.newExactQuery("int", 42)))
        assertEquals(0, s.count(IntPoint.newExactQuery("int", 41)))

        assertEquals(1, s.count(LongPoint.newExactQuery("long", 5L)))
        assertEquals(0, s.count(LongPoint.newExactQuery("long", -1L)))

        assertEquals(1, s.count(FloatPoint.newExactQuery("float", 2.0f)))
        assertEquals(0, s.count(FloatPoint.newExactQuery("float", 1.0f)))

        assertEquals(1, s.count(DoublePoint.newExactQuery("double", 1.0)))
        assertEquals(0, s.count(DoublePoint.newExactQuery("double", 2.0)))
        w.close()
        r.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testToString() {

        // ints
        assertEquals("field:[1 TO 2]", IntPoint.newRangeQuery("field", 1, 2).toString())
        assertEquals("field:[-2 TO 1]", IntPoint.newRangeQuery("field", -2, 1).toString())

        // longs
        assertEquals(
            "field:[1099511627776 TO 2199023255552]",
            LongPoint.newRangeQuery("field", 1L shl 40, 1L shl 41).toString()
        )
        assertEquals("field:[-5 TO 6]", LongPoint.newRangeQuery("field", -5L, 6L).toString())

        // floats
        assertEquals("field:[1.3 TO 2.5]", FloatPoint.newRangeQuery("field", 1.3f, 2.5f).toString())
        assertEquals("field:[-2.9 TO 1.0]", FloatPoint.newRangeQuery("field", -2.9f, 1.0f).toString())

        // doubles
        assertEquals("field:[1.3 TO 2.5]", DoublePoint.newRangeQuery("field", 1.3, 2.5).toString())
        assertEquals("field:[-2.9 TO 1.0]", DoublePoint.newRangeQuery("field", -2.9, 1.0).toString())

        // n-dimensional double
        assertEquals(
            "field:[1.3 TO 2.5],[-2.9 TO 1.0]",
            DoublePoint.newRangeQuery("field", doubleArrayOf(1.3, -2.9), doubleArrayOf(2.5, 1.0)).toString()
        )
    }

    private fun toArray(valuesSet: Set<Int>): IntArray {
        val values = IntArray(valuesSet.size)
        var upto = 0
        for (value in valuesSet) {
            values[upto++] = value
        }
        return values
    }

    private fun randomIntValue(min: Int?, max: Int?): Int {
        return if (min == null) {
            random().nextInt()
        } else {
            TestUtil.nextInt(random(), min, max!!)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testRandomPointInSetQuery() {

        val useNarrowRange = random().nextBoolean()
        val valueMin: Int?
        val valueMax: Int?
        val numValues: Int
        if (useNarrowRange) {
            val gap = random().nextInt(100)
            valueMin = random().nextInt(Int.MAX_VALUE - gap)
            valueMax = valueMin + gap
            numValues = TestUtil.nextInt(random(), 1, gap + 1)
        } else {
            valueMin = null
            valueMax = null
            numValues = TestUtil.nextInt(random(), 1, 100)
        }
        val valuesSet = mutableSetOf<Int>()
        while (valuesSet.size < numValues) {
            valuesSet.add(randomIntValue(valueMin, valueMax))
        }
        val values = toArray(valuesSet)
        val numDocs = TestUtil.nextInt(random(), 1, 10000)

        if (VERBOSE) {
            println("TEST: numValues=$numValues numDocs=$numDocs")
        }

        val dir: Directory
        if (numDocs > 100000) {
            dir = newFSDirectory(createTempDir("TestPointQueries"))
        } else {
            dir = newDirectory()
        }

        val iwc = newIndexWriterConfig()
        iwc.setCodec(getCodec())
        val w = RandomIndexWriter(random(), dir, iwc)

        val docValues = IntArray(numDocs)
        for (i in 0 until numDocs) {
            val x = values[random().nextInt(values.size)]
            val doc = Document()
            doc.add(IntPoint("int", x))
            docValues[i] = x
            w.addDocument(doc)
        }

        if (random().nextBoolean()) {
            if (VERBOSE) {
                println("  forceMerge(1)")
            }
            w.forceMerge(1)
        }
        val r: IndexReader = w.getReader(true, false)
        w.close()

        val s = newSearcher(r, false)

        val numThreads = TestUtil.nextInt(random(), 2, 5)

        if (VERBOSE) {
            println("TEST: use $numThreads query threads; searcher=$s")
        }

        val threads = mutableListOf<Thread>()
        val iters = atLeast(100)

        val startingGun = CountDownLatch(1)
        val failed = AtomicBoolean(false)

        for (i in 0 until numThreads) {
            val thread = object : Thread() {
                override fun run() {
                    try {
                        _run()
                    } catch (e: Exception) {
                        failed.store(true)
                        throw RuntimeException(e)
                    }
                }

                private fun _run() {
                    startingGun.await()

                    var iter = 0
                    while (iter < iters && !failed.load()) {

                        val numValidValuesToQuery = random().nextInt(values.size)

                        val valuesToQuery = mutableSetOf<Int>()
                        while (valuesToQuery.size < numValidValuesToQuery) {
                            valuesToQuery.add(values[random().nextInt(values.size)])
                        }

                        val numExtraValuesToQuery = random().nextInt(20)
                        while (valuesToQuery.size < numValidValuesToQuery + numExtraValuesToQuery) {
                            valuesToQuery.add(random().nextInt())
                        }

                        var expectedCount = 0
                        for (value in docValues) {
                            if (valuesToQuery.contains(value)) {
                                expectedCount++
                            }
                        }

                        if (VERBOSE) {
                            println(
                                "TEST: thread=${Thread.currentThread()} values=$valuesToQuery expectedCount=$expectedCount"
                            )
                        }

                        assertEquals(
                            expectedCount,
                            s.count(IntPoint.newSetQuery("int", *toArray(valuesToQuery)))
                        )
                        iter++
                    }
                }
            }
            thread.setName("T$i")
            thread.start()
            threads.add(thread)
        }
        startingGun.countDown()
        for (thread in threads) {
            thread.join()
        }
        IOUtils.close(r, dir)
    }

    // TODO: in the future, if there is demand for real usage, we can "graduate" this test-only query
    // factory as IntPoint.newMultiSetQuery or
    // something (and same for other XXXPoint classes):
    @Throws(IOException::class)
    private fun newMultiDimIntSetQuery(field: String, numDims: Int, vararg valuesIn: Int): Query {
        if (valuesIn.size % numDims != 0) {
            throw IllegalArgumentException(
                "incongruent number of values: valuesIn.length=${valuesIn.size} but numDims=$numDims"
            )
        }

        // Pack all values:
        val packedValues = Array(valuesIn.size / numDims) { i ->
            val packedValue = ByteArray(numDims * Int.SIZE_BYTES)
            for (dim in 0 until numDims) {
                IntPoint.encodeDimension(valuesIn[i * numDims + dim], packedValue, dim * Int.SIZE_BYTES)
            }
            packedValue
        }

        // Sort:
        Arrays.sort(
            packedValues,
            Comparator { a, b ->
                Arrays.compareUnsigned(a, 0, a.size, b, 0, a.size)
            }
        )

        val value = BytesRef()
        value.length = numDims * Int.SIZE_BYTES

        return object : PointInSetQuery(
            field,
            numDims,
            Int.SIZE_BYTES,
            object : PointInSetQuery.Stream() {
                var upto = 0

                override fun next(): BytesRef? {
                    if (upto >= packedValues.size) {
                        return null
                    }
                    value.bytes = packedValues[upto]
                    upto++
                    return value
                }
            }
        ) {
            override fun toString(value: ByteArray): String {
                assert(value.size == numDims * Int.SIZE_BYTES)
                val sb = StringBuilder()
                for (dim in 0 until numDims) {
                    if (dim > 0) {
                        sb.append(',')
                    }
                    sb.append(IntPoint.decodeDimension(value, dim * Int.SIZE_BYTES).toString())
                }

                return sb.toString()
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testBasicMultiDimPointInSetQuery() {
        val dir = newDirectory()
        val iwc = newIndexWriterConfig()
        iwc.setCodec(getCodec())
        val w = IndexWriter(dir, iwc)

        val doc = Document()
        doc.add(IntPoint("int", 17, 42))
        w.addDocument(doc)
        val r = DirectoryReader.open(w)
        val s = newSearcher(r, false)

        assertEquals(0, s.count(newMultiDimIntSetQuery("int", 2, 17, 41)))
        assertEquals(1, s.count(newMultiDimIntSetQuery("int", 2, 17, 42)))
        assertEquals(1, s.count(newMultiDimIntSetQuery("int", 2, -7, -7, 17, 42)))
        assertEquals(1, s.count(newMultiDimIntSetQuery("int", 2, 17, 42, -14, -14)))

        w.close()
        r.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testBasicMultiValueMultiDimPointInSetQuery() {
        val dir = newDirectory()
        val iwc = newIndexWriterConfig()
        iwc.setCodec(getCodec())
        val w = IndexWriter(dir, iwc)

        val doc = Document()
        doc.add(IntPoint("int", 17, 42))
        doc.add(IntPoint("int", 34, 79))
        w.addDocument(doc)
        val r = DirectoryReader.open(w)
        val s = newSearcher(r, false)

        assertEquals(0, s.count(newMultiDimIntSetQuery("int", 2, 17, 41)))
        assertEquals(1, s.count(newMultiDimIntSetQuery("int", 2, 17, 42)))
        assertEquals(1, s.count(newMultiDimIntSetQuery("int", 2, 17, 42, 34, 79)))
        assertEquals(1, s.count(newMultiDimIntSetQuery("int", 2, -7, -7, 17, 42)))
        assertEquals(1, s.count(newMultiDimIntSetQuery("int", 2, -7, -7, 34, 79)))
        assertEquals(1, s.count(newMultiDimIntSetQuery("int", 2, 17, 42, -14, -14)))

        assertEquals(
            "int:{-14,-14 17,42}", newMultiDimIntSetQuery("int", 2, 17, 42, -14, -14).toString()
        )

        w.close()
        r.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testManyEqualValuesMultiDimPointInSetQuery() {
        val dir = newDirectory()
        val iwc = newIndexWriterConfig()
        iwc.setCodec(getCodec())
        val w = IndexWriter(dir, iwc)

        var zeroCount = 0
        for (i in 0 until 10000) {
            val x = random().nextInt(2)
            if (x == 0) {
                zeroCount++
            }
            val doc = Document()
            doc.add(IntPoint("int", x, x))
            w.addDocument(doc)
        }
        val r = DirectoryReader.open(w)
        val s = newSearcher(r, false)

        assertEquals(zeroCount, s.count(newMultiDimIntSetQuery("int", 2, 0, 0)))
        assertEquals(10000 - zeroCount, s.count(newMultiDimIntSetQuery("int", 2, 1, 1)))
        assertEquals(0, s.count(newMultiDimIntSetQuery("int", 2, 2, 2)))

        w.close()
        r.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testInvalidMultiDimPointInSetQuery() {
        val expected = expectThrows(
            IllegalArgumentException::class
        ) {
            newMultiDimIntSetQuery("int", 2, 3, 4, 5)
        }
        assertEquals(
            "incongruent number of values: valuesIn.length=3 but numDims=2", expected.message
        )
    }

    @Test
    @Throws(Exception::class)
    fun testBasicPointInSetQuery() {
        val dir = newDirectory()
        val iwc = newIndexWriterConfig()
        iwc.setCodec(getCodec())
        val w = IndexWriter(dir, iwc)

        var doc = Document()
        doc.add(IntPoint("int", 17))
        doc.add(LongPoint("long", 17L))
        doc.add(FloatPoint("float", 17.0f))
        doc.add(DoublePoint("double", 17.0))
        doc.add(BinaryPoint("bytes", arrayOf(byteArrayOf(0, 17))))
        w.addDocument(doc)

        doc = Document()
        doc.add(IntPoint("int", 42))
        doc.add(LongPoint("long", 42L))
        doc.add(FloatPoint("float", 42.0f))
        doc.add(DoublePoint("double", 42.0))
        doc.add(BinaryPoint("bytes", arrayOf(byteArrayOf(0, 42))))
        w.addDocument(doc)

        doc = Document()
        doc.add(IntPoint("int", 97))
        doc.add(LongPoint("long", 97L))
        doc.add(FloatPoint("float", 97.0f))
        doc.add(DoublePoint("double", 97.0))
        doc.add(BinaryPoint("bytes", arrayOf(byteArrayOf(0, 97))))
        w.addDocument(doc)

        val r = DirectoryReader.open(w)
        val s = newSearcher(r, false)
        assertEquals(0, s.count(IntPoint.newSetQuery("int", 16)))
        assertEquals(1, s.count(IntPoint.newSetQuery("int", 17)))
        assertEquals(3, s.count(IntPoint.newSetQuery("int", 17, 97, 42)))
        assertEquals(3, s.count(IntPoint.newSetQuery("int", -7, 17, 42, 97)))
        assertEquals(3, s.count(IntPoint.newSetQuery("int", 17, 20, 42, 97)))
        assertEquals(3, s.count(IntPoint.newSetQuery("int", 17, 105, 42, 97)))

        assertEquals(0, s.count(LongPoint.newSetQuery("long", 16)))
        assertEquals(1, s.count(LongPoint.newSetQuery("long", 17)))
        assertEquals(3, s.count(LongPoint.newSetQuery("long", 17, 97, 42)))
        assertEquals(3, s.count(LongPoint.newSetQuery("long", -7, 17, 42, 97)))
        assertEquals(3, s.count(LongPoint.newSetQuery("long", 17, 20, 42, 97)))
        assertEquals(3, s.count(LongPoint.newSetQuery("long", 17, 105, 42, 97)))

        assertEquals(0, s.count(FloatPoint.newSetQuery("float", 16f)))
        assertEquals(1, s.count(FloatPoint.newSetQuery("float", 17f)))
        assertEquals(3, s.count(FloatPoint.newSetQuery("float", 17f, 97f, 42f)))
        assertEquals(3, s.count(FloatPoint.newSetQuery("float", -7f, 17f, 42f, 97f)))
        assertEquals(3, s.count(FloatPoint.newSetQuery("float", 17f, 20f, 42f, 97f)))
        assertEquals(3, s.count(FloatPoint.newSetQuery("float", 17f, 105f, 42f, 97f)))

        assertEquals(0, s.count(DoublePoint.newSetQuery("double", 16.0)))
        assertEquals(1, s.count(DoublePoint.newSetQuery("double", 17.0)))
        assertEquals(3, s.count(DoublePoint.newSetQuery("double", 17.0, 97.0, 42.0)))
        assertEquals(3, s.count(DoublePoint.newSetQuery("double", -7.0, 17.0, 42.0, 97.0)))
        assertEquals(3, s.count(DoublePoint.newSetQuery("double", 17.0, 20.0, 42.0, 97.0)))
        assertEquals(3, s.count(DoublePoint.newSetQuery("double", 17.0, 105.0, 42.0, 97.0)))

        assertEquals(0, s.count(BinaryPoint.newSetQuery("bytes", arrayOf(byteArrayOf(0, 16)))))
        assertEquals(1, s.count(BinaryPoint.newSetQuery("bytes", arrayOf(byteArrayOf(0, 17)))))
        assertEquals(
            3,
            s.count(
                BinaryPoint.newSetQuery(
                    "bytes", arrayOf(byteArrayOf(0, 17), byteArrayOf(0, 97), byteArrayOf(0, 42))
                )
            )
        )
        assertEquals(
            3,
            s.count(
                BinaryPoint.newSetQuery(
                    "bytes", arrayOf(
                        byteArrayOf(0, -7),
                        byteArrayOf(0, 17),
                        byteArrayOf(0, 42),
                        byteArrayOf(0, 97)
                    ))
            )
        )
        assertEquals(
            3,
            s.count(
                BinaryPoint.newSetQuery(
                    "bytes", arrayOf(
                        byteArrayOf(0, 17),
                        byteArrayOf(0, 20),
                        byteArrayOf(0, 42),
                        byteArrayOf(0, 97)
                    ))
            )
        )
        assertEquals(
            3,
            s.count(
                BinaryPoint.newSetQuery(
                    "bytes", arrayOf(
                        byteArrayOf(0, 17),
                        byteArrayOf(0, 105),
                        byteArrayOf(0, 42),
                        byteArrayOf(0, 97)
                    ))
            )
        )

        w.close()
        r.close()
        dir.close()
    }

    /** Boxed methods for primitive types should behave the same as unboxed: just sugar */
    @Test
    @Throws(Exception::class)
    fun testPointIntSetBoxed() {
        assertEquals(
            IntPoint.newSetQuery("foo", 1, 2, 3), IntPoint.newSetQuery("foo", mutableListOf(1, 2, 3))
        )
        assertEquals(
            FloatPoint.newSetQuery("foo", 1f, 2f, 3f),
            FloatPoint.newSetQuery("foo", mutableListOf(1f, 2f, 3f))
        )
        assertEquals(
            LongPoint.newSetQuery("foo", 1L, 2L, 3L),
            LongPoint.newSetQuery("foo", mutableListOf(1L, 2L, 3L))
        )
        assertEquals(
            DoublePoint.newSetQuery("foo", 1.0, 2.0, 3.0),
            DoublePoint.newSetQuery("foo", mutableListOf(1.0, 2.0, 3.0))
        )
    }

    @Test
    @Throws(Exception::class)
    fun testBasicMultiValuedPointInSetQuery() {
        val dir = newDirectory()
        val iwc = newIndexWriterConfig()
        iwc.setCodec(getCodec())
        val w = IndexWriter(dir, iwc)

        val doc = Document()
        doc.add(IntPoint("int", 17))
        doc.add(IntPoint("int", 42))
        doc.add(LongPoint("long", 17L))
        doc.add(LongPoint("long", 42L))
        doc.add(FloatPoint("float", 17.0f))
        doc.add(FloatPoint("float", 42.0f))
        doc.add(DoublePoint("double", 17.0))
        doc.add(DoublePoint("double", 42.0))
        doc.add(BinaryPoint("bytes", arrayOf(byteArrayOf(0, 17))))
        doc.add(BinaryPoint("bytes", arrayOf(byteArrayOf(0, 42))))
        w.addDocument(doc)

        val r = DirectoryReader.open(w)
        val s = newSearcher(r, false)
        assertEquals(0, s.count(IntPoint.newSetQuery("int", 16)))
        assertEquals(1, s.count(IntPoint.newSetQuery("int", 17)))
        assertEquals(1, s.count(IntPoint.newSetQuery("int", 17, 97, 42)))
        assertEquals(1, s.count(IntPoint.newSetQuery("int", -7, 17, 42, 97)))
        assertEquals(0, s.count(IntPoint.newSetQuery("int", 16, 20, 41, 97)))

        assertEquals(0, s.count(LongPoint.newSetQuery("long", 16)))
        assertEquals(1, s.count(LongPoint.newSetQuery("long", 17)))
        assertEquals(1, s.count(LongPoint.newSetQuery("long", 17, 97, 42)))
        assertEquals(1, s.count(LongPoint.newSetQuery("long", -7, 17, 42, 97)))
        assertEquals(0, s.count(LongPoint.newSetQuery("long", 16, 20, 41, 97)))

        assertEquals(0, s.count(FloatPoint.newSetQuery("float", 16f)))
        assertEquals(1, s.count(FloatPoint.newSetQuery("float", 17f)))
        assertEquals(1, s.count(FloatPoint.newSetQuery("float", 17f, 97f, 42f)))
        assertEquals(1, s.count(FloatPoint.newSetQuery("float", -7f, 17f, 42f, 97f)))
        assertEquals(0, s.count(FloatPoint.newSetQuery("float", 16f, 20f, 41f, 97f)))

        assertEquals(0, s.count(DoublePoint.newSetQuery("double", 16.0)))
        assertEquals(1, s.count(DoublePoint.newSetQuery("double", 17.0)))
        assertEquals(1, s.count(DoublePoint.newSetQuery("double", 17.0, 97.0, 42.0)))
        assertEquals(1, s.count(DoublePoint.newSetQuery("double", -7.0, 17.0, 42.0, 97.0)))
        assertEquals(0, s.count(DoublePoint.newSetQuery("double", 16.0, 20.0, 41.0, 97.0)))

        assertEquals(0, s.count(BinaryPoint.newSetQuery("bytes", arrayOf(byteArrayOf(0, 16)))))
        assertEquals(1, s.count(BinaryPoint.newSetQuery("bytes", arrayOf(byteArrayOf(0, 17)))))
        assertEquals(
            1,
            s.count(
                BinaryPoint.newSetQuery(
                    "bytes", arrayOf(byteArrayOf(0, 17), byteArrayOf(0, 97), byteArrayOf(0, 42))
                )
            )
        )
        assertEquals(
            1,
            s.count(
                BinaryPoint.newSetQuery(
                    "bytes", arrayOf(
                        byteArrayOf(0, -7),
                        byteArrayOf(0, 17),
                        byteArrayOf(0, 42),
                        byteArrayOf(0, 97)
                    ))
            )
        )
        assertEquals(
            0,
            s.count(
                BinaryPoint.newSetQuery(
                    "bytes", arrayOf(
                        byteArrayOf(0, 16),
                        byteArrayOf(0, 20),
                        byteArrayOf(0, 41),
                        byteArrayOf(0, 97)
                    ))
            )
        )

        w.close()
        r.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testEmptyPointInSetQuery() {
        val dir = newDirectory()
        val iwc = newIndexWriterConfig()
        iwc.setCodec(getCodec())
        val w = IndexWriter(dir, iwc)

        val doc = Document()
        doc.add(IntPoint("int", 17))
        doc.add(LongPoint("long", 17L))
        doc.add(FloatPoint("float", 17.0f))
        doc.add(DoublePoint("double", 17.0))
        doc.add(BinaryPoint("bytes", arrayOf(byteArrayOf(0, 17))))
        w.addDocument(doc)

        val r = DirectoryReader.open(w)
        val s = newSearcher(r, false)
        assertEquals(0, s.count(IntPoint.newSetQuery("int")))
        assertEquals(0, s.count(LongPoint.newSetQuery("long")))
        assertEquals(0, s.count(FloatPoint.newSetQuery("float")))
        assertEquals(0, s.count(DoublePoint.newSetQuery("double")))
        assertEquals(0, s.count(BinaryPoint.newSetQuery("bytes", arrayOf<ByteArray>())))

        w.close()
        r.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testPointInSetQueryManyEqualValues() {
        val dir = newDirectory()
        val iwc = newIndexWriterConfig()
        iwc.setCodec(getCodec())
        val w = IndexWriter(dir, iwc)

        var zeroCount = 0
        for (i in 0 until 10000) {
            val x = random().nextInt(2)
            if (x == 0) {
                zeroCount++
            }
            val doc = Document()
            doc.add(IntPoint("int", x))
            doc.add(LongPoint("long", x.toLong()))
            doc.add(FloatPoint("float", x.toFloat()))
            doc.add(DoublePoint("double", x.toDouble()))
            doc.add(BinaryPoint("bytes", arrayOf(byteArrayOf(x.toByte()))))
            w.addDocument(doc)
        }

        val r = DirectoryReader.open(w)
        val s = newSearcher(r, false)
        assertEquals(zeroCount, s.count(IntPoint.newSetQuery("int", 0)))
        assertEquals(zeroCount, s.count(IntPoint.newSetQuery("int", 0, -7)))
        assertEquals(zeroCount, s.count(IntPoint.newSetQuery("int", 7, 0)))
        assertEquals(10000 - zeroCount, s.count(IntPoint.newSetQuery("int", 1)))
        assertEquals(0, s.count(IntPoint.newSetQuery("int", 2)))

        assertEquals(zeroCount, s.count(LongPoint.newSetQuery("long", 0)))
        assertEquals(zeroCount, s.count(LongPoint.newSetQuery("long", 0, -7)))
        assertEquals(zeroCount, s.count(LongPoint.newSetQuery("long", 7, 0)))
        assertEquals(10000 - zeroCount, s.count(LongPoint.newSetQuery("long", 1)))
        assertEquals(0, s.count(LongPoint.newSetQuery("long", 2)))

        assertEquals(zeroCount, s.count(FloatPoint.newSetQuery("float", 0f)))
        assertEquals(zeroCount, s.count(FloatPoint.newSetQuery("float", 0f, -7f)))
        assertEquals(zeroCount, s.count(FloatPoint.newSetQuery("float", 7f, 0f)))
        assertEquals(10000 - zeroCount, s.count(FloatPoint.newSetQuery("float", 1f)))
        assertEquals(0, s.count(FloatPoint.newSetQuery("float", 2f)))

        assertEquals(zeroCount, s.count(DoublePoint.newSetQuery("double", 0.0)))
        assertEquals(zeroCount, s.count(DoublePoint.newSetQuery("double", 0.0, -7.0)))
        assertEquals(zeroCount, s.count(DoublePoint.newSetQuery("double", 7.0, 0.0)))
        assertEquals(10000 - zeroCount, s.count(DoublePoint.newSetQuery("double", 1.0)))
        assertEquals(0, s.count(DoublePoint.newSetQuery("double", 2.0)))

        assertEquals(zeroCount, s.count(BinaryPoint.newSetQuery("bytes", arrayOf(byteArrayOf(0)))))
        assertEquals(zeroCount, s.count(BinaryPoint.newSetQuery("bytes", arrayOf(byteArrayOf(0), byteArrayOf(-7)))))
        assertEquals(zeroCount, s.count(BinaryPoint.newSetQuery("bytes", arrayOf(byteArrayOf(7), byteArrayOf(0)))))
        assertEquals(10000 - zeroCount, s.count(BinaryPoint.newSetQuery("bytes", arrayOf(byteArrayOf(1)))))
        assertEquals(0, s.count(BinaryPoint.newSetQuery("bytes", arrayOf(byteArrayOf(2)))))

        w.close()
        r.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testPointRangeQueryManyEqualValues() {
        val dir = newDirectory()
        val iwc = newIndexWriterConfig()
        iwc.setCodec(getCodec())
        val w = IndexWriter(dir, iwc)

        val cardinality = TestUtil.nextInt(random(), 2, 20)

        var zeroCount = 0
        var oneCount = 0
        for (i in 0 until 10000) {
            val x = random().nextInt(cardinality)
            if (x == 0) {
                zeroCount++
            } else if (x == 1) {
                oneCount++
            }
            val doc = Document()
            doc.add(IntPoint("int", x))
            doc.add(LongPoint("long", x.toLong()))
            doc.add(FloatPoint("float", x.toFloat()))
            doc.add(DoublePoint("double", x.toDouble()))
            doc.add(BinaryPoint("bytes", arrayOf(byteArrayOf(x.toByte()))))
            w.addDocument(doc)
        }

        val r = DirectoryReader.open(w)
        val s = newSearcher(r, false)

        assertEquals(zeroCount, s.count(IntPoint.newRangeQuery("int", 0, 0)))
        assertEquals(oneCount, s.count(IntPoint.newRangeQuery("int", 1, 1)))
        assertEquals(zeroCount + oneCount, s.count(IntPoint.newRangeQuery("int", 0, 1)))
        assertEquals(10000 - zeroCount - oneCount, s.count(IntPoint.newRangeQuery("int", 2, cardinality)))

        assertEquals(zeroCount, s.count(LongPoint.newRangeQuery("long", 0, 0)))
        assertEquals(oneCount, s.count(LongPoint.newRangeQuery("long", 1, 1)))
        assertEquals(zeroCount + oneCount, s.count(LongPoint.newRangeQuery("long", 0, 1)))
        assertEquals(
            10000 - zeroCount - oneCount,
            s.count(LongPoint.newRangeQuery("long", 2, cardinality.toLong()))
        )

        assertEquals(zeroCount, s.count(FloatPoint.newRangeQuery("float", 0f, 0f)))
        assertEquals(oneCount, s.count(FloatPoint.newRangeQuery("float", 1f, 1f)))
        assertEquals(zeroCount + oneCount, s.count(FloatPoint.newRangeQuery("float", 0f, 1f)))
        assertEquals(
            10000 - zeroCount - oneCount,
            s.count(FloatPoint.newRangeQuery("float", 2f, cardinality.toFloat()))
        )

        assertEquals(zeroCount, s.count(DoublePoint.newRangeQuery("double", 0.0, 0.0)))
        assertEquals(oneCount, s.count(DoublePoint.newRangeQuery("double", 1.0, 1.0)))
        assertEquals(zeroCount + oneCount, s.count(DoublePoint.newRangeQuery("double", 0.0, 1.0)))
        assertEquals(
            10000 - zeroCount - oneCount,
            s.count(DoublePoint.newRangeQuery("double", 2.0, cardinality.toDouble()))
        )

        assertEquals(
            zeroCount, s.count(BinaryPoint.newRangeQuery("bytes", byteArrayOf(0), byteArrayOf(0)))
        )
        assertEquals(
            oneCount, s.count(BinaryPoint.newRangeQuery("bytes", byteArrayOf(1), byteArrayOf(1)))
        )
        assertEquals(
            zeroCount + oneCount,
            s.count(BinaryPoint.newRangeQuery("bytes", byteArrayOf(0), byteArrayOf(1)))
        )
        assertEquals(
            10000 - zeroCount - oneCount,
            s.count(
                BinaryPoint.newRangeQuery("bytes", byteArrayOf(2), byteArrayOf(cardinality.toByte()))
            )
        )

        w.close()
        r.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testPointInSetQueryManyEqualValuesWithBigGap() {
        val dir = newDirectory()
        val iwc = newIndexWriterConfig()
        iwc.setCodec(getCodec())
        val w = IndexWriter(dir, iwc)

        var zeroCount = 0
        for (i in 0 until 10000) {
            val x = 200 * random().nextInt(2)
            if (x == 0) {
                zeroCount++
            }
            val doc = Document()
            doc.add(IntPoint("int", x))
            doc.add(LongPoint("long", x.toLong()))
            doc.add(FloatPoint("float", x.toFloat()))
            doc.add(DoublePoint("double", x.toDouble()))
            doc.add(BinaryPoint("bytes", arrayOf(byteArrayOf(x.toByte()))))
            w.addDocument(doc)
        }

        val r = DirectoryReader.open(w)
        val s = newSearcher(r, false)
        assertEquals(zeroCount, s.count(IntPoint.newSetQuery("int", 0)))
        assertEquals(zeroCount, s.count(IntPoint.newSetQuery("int", 0, -7)))
        assertEquals(zeroCount, s.count(IntPoint.newSetQuery("int", 7, 0)))
        assertEquals(10000 - zeroCount, s.count(IntPoint.newSetQuery("int", 200)))
        assertEquals(0, s.count(IntPoint.newSetQuery("int", 2)))

        assertEquals(zeroCount, s.count(LongPoint.newSetQuery("long", 0)))
        assertEquals(zeroCount, s.count(LongPoint.newSetQuery("long", 0, -7)))
        assertEquals(zeroCount, s.count(LongPoint.newSetQuery("long", 7, 0)))
        assertEquals(10000 - zeroCount, s.count(LongPoint.newSetQuery("long", 200)))
        assertEquals(0, s.count(LongPoint.newSetQuery("long", 2)))

        assertEquals(zeroCount, s.count(FloatPoint.newSetQuery("float", 0f)))
        assertEquals(zeroCount, s.count(FloatPoint.newSetQuery("float", 0f, -7f)))
        assertEquals(zeroCount, s.count(FloatPoint.newSetQuery("float", 7f, 0f)))
        assertEquals(10000 - zeroCount, s.count(FloatPoint.newSetQuery("float", 200f)))
        assertEquals(0, s.count(FloatPoint.newSetQuery("float", 2f)))

        assertEquals(zeroCount, s.count(DoublePoint.newSetQuery("double", 0.0)))
        assertEquals(zeroCount, s.count(DoublePoint.newSetQuery("double", 0.0, -7.0)))
        assertEquals(zeroCount, s.count(DoublePoint.newSetQuery("double", 7.0, 0.0)))
        assertEquals(10000 - zeroCount, s.count(DoublePoint.newSetQuery("double", 200.0)))
        assertEquals(0, s.count(DoublePoint.newSetQuery("double", 2.0)))

        assertEquals(zeroCount, s.count(BinaryPoint.newSetQuery("bytes", arrayOf(byteArrayOf(0)))))
        assertEquals(
            zeroCount, s.count(BinaryPoint.newSetQuery("bytes", arrayOf(byteArrayOf(0), byteArrayOf(-7))))
        )
        assertEquals(
            zeroCount, s.count(BinaryPoint.newSetQuery("bytes", arrayOf(byteArrayOf(7), byteArrayOf(0))))
        )
        assertEquals(
            10000 - zeroCount, s.count(BinaryPoint.newSetQuery("bytes", arrayOf(byteArrayOf(200.toByte()))))
        )
        assertEquals(0, s.count(BinaryPoint.newSetQuery("bytes", arrayOf(byteArrayOf(2)))))

        w.close()
        r.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testInvalidPointInSetQuery() {
        val expected = expectThrows(
            IllegalArgumentException::class
        ) {
            object : PointInSetQuery(
                "foo",
                3,
                4,
                object : PointInSetQuery.Stream() {
                    override fun next(): BytesRef? {
                        return newBytesRef(ByteArray(3))
                    }
                }
            ) {
                override fun toString(point: ByteArray): String {
                    return point.contentToString()
                }
            }
        }
        assertEquals(
            "packed point length should be 12 but got 3; field=\"foo\" numDims=3 bytesPerDim=4",
            expected.message
        )
    }

    @Test
    @Throws(Exception::class)
    fun testInvalidPointInSetBinaryQuery() {
        val expected = expectThrows(
            IllegalArgumentException::class
        ) {
            BinaryPoint.newSetQuery("bytes", arrayOf(byteArrayOf(2), byteArrayOf()))
        }
        assertEquals("all byte[] must be the same length, but saw 1 and 0", expected.message)
    }

    @Test
    @Throws(Exception::class)
    fun testPointInSetQueryToString() {
        // int
        assertEquals("int:{-42 18}", IntPoint.newSetQuery("int", -42, 18).toString())

        // long
        assertEquals("long:{-42 18}", LongPoint.newSetQuery("long", -42L, 18L).toString())

        // float
        assertEquals("float:{-42.0 18.0}", FloatPoint.newSetQuery("float", -42.0f, 18.0f).toString())

        // double
        assertEquals("double:{-42.0 18.0}", DoublePoint.newSetQuery("double", -42.0, 18.0).toString())

        // binary
        assertEquals(
            "bytes:{[12] [2a]}",
            BinaryPoint.newSetQuery("bytes", arrayOf(byteArrayOf(42), byteArrayOf(18))).toString()
        )
    }

    @Test
    @Throws(Exception::class)
    fun testPointInSetQueryGetPackedPoints() {
        val numValues = randomIntValue(1, 32)
        val values = mutableListOf<ByteArray>()
        for (i in 0 until numValues) {
            values.add(byteArrayOf(i.toByte()))
        }

        val query =
            BinaryPoint.newSetQuery("field", values.toTypedArray()) as PointInSetQuery
        val packedPoints: Collection<ByteArray> = query.packedPoints
        assertEquals(numValues, packedPoints.size)
        val iterator: Iterator<ByteArray> = packedPoints.iterator()
        for (expectedValue in values) {
            assertArrayEquals(expectedValue, iterator.next())
        }
        expectThrows(NoSuchElementException::class) { iterator.next() }
        assertFalse(iterator.hasNext())
    }

    @Test
    @Throws(IOException::class)
    fun testRangeOptimizesIfAllPointsMatch() {
        val numDims = TestUtil.nextInt(random(), 1, 3)
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        val doc = Document()
        val value = IntArray(numDims)
        for (i in 0 until numDims) {
            value[i] = TestUtil.nextInt(random(), 1, 10)
        }
        doc.add(IntPoint("point", *value))
        w.addDocument(doc)
        val reader = w.getReader(true, false)
        val searcher = IndexSearcher(reader)
        searcher.queryCache = null
        val lowerBound = IntArray(numDims)
        val upperBound = IntArray(numDims)
        for (i in 0 until numDims) {
            lowerBound[i] = value[i] - random().nextInt(1)
            upperBound[i] = value[i] + random().nextInt(1)
        }
        val query: Query = IntPoint.newRangeQuery("point", lowerBound, upperBound)
        val weight = searcher.createWeight(query, ScoreMode.COMPLETE_NO_SCORES, 1f)
        val scorer = weight.scorer(searcher.indexReader.leaves().get(0))!!
        assertEquals(DocIdSetIterator.all(1)::class, scorer.iterator()::class)

        // When not all documents in the query have a value, the optimization is not applicable
        reader.close()
        w.addDocument(Document())
        w.forceMerge(1)
        val reader2 = w.getReader(true, false)
        val searcher2 = IndexSearcher(reader2)
        searcher2.queryCache = null
        val weight2 = searcher2.createWeight(query, ScoreMode.COMPLETE_NO_SCORES, 1f)
        val scorer2 = weight2.scorer(searcher2.indexReader.leaves().get(0))!!
        assertFalse(DocIdSetIterator.all(1)::class == scorer2.iterator()::class)

        reader2.close()
        w.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testPointRangeWeightCount() {
        // the optimization for Weight#count kicks in only when the number of dimensions is 1
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)

        val numPoints = random().nextInt(1, 10)
        val points = IntArray(numPoints)

        val numQueries = random().nextInt(1, 10)
        val lowerBound = IntArray(numQueries)
        val upperBound = IntArray(numQueries)
        val expectedCount = IntArray(numQueries)

        for (i in 0 until numQueries) {
            // generate random queries
            lowerBound[i] = random().nextInt(1, 10)
            // allow malformed ranges where upperBound could be less than lowerBound
            upperBound[i] = random().nextInt(1, 10)
        }

        for (i in 0 until numPoints) {
            // generate random 1D points
            points[i] = random().nextInt(1, 10)
            if (random().nextBoolean()) {
                // the doc may have at-most 1 point
                val doc = Document()
                doc.add(IntPoint("point", points[i]))
                w.addDocument(doc)
                for (j in 0 until numQueries) {
                    // calculate the number of points that lie within the query range
                    if (lowerBound[j] <= points[i] && points[i] <= upperBound[j]) {
                        expectedCount[j]++
                    }
                }
            }
        }
        w.commit()
        w.forceMerge(1)

        val reader = w.getReader(true, false)
        val searcher = IndexSearcher(reader)
        if (searcher.leafContexts.isEmpty().not()) { // we need at least 1 leaf in the segment
            for (i in 0 until numQueries) {
                val query: Query = IntPoint.newRangeQuery("point", lowerBound[i], upperBound[i])
                val weight = searcher.createWeight(query, ScoreMode.COMPLETE_NO_SCORES, 1f)
                assertEquals(expectedCount[i], weight.count(searcher.leafContexts.get(0)))
            }
        }

        reader.close()
        w.close()
        dir.close()
    }

    @Test
    fun testPointRangeEquals() {
        var q1: Query
        var q2: Query

        q1 = IntPoint.newRangeQuery("a", 0, 1000)
        q2 = IntPoint.newRangeQuery("a", 0, 1000)
        assertEquals(q1, q2)
        assertEquals(q1.hashCode(), q2.hashCode())
        assertFalse(q1 == IntPoint.newRangeQuery("a", 1, 1000))
        assertFalse(q1 == IntPoint.newRangeQuery("b", 0, 1000))

        q1 = LongPoint.newRangeQuery("a", 0, 1000)
        q2 = LongPoint.newRangeQuery("a", 0, 1000)
        assertEquals(q1, q2)
        assertEquals(q1.hashCode(), q2.hashCode())
        assertFalse(q1 == LongPoint.newRangeQuery("a", 1, 1000))

        q1 = FloatPoint.newRangeQuery("a", 0f, 1000f)
        q2 = FloatPoint.newRangeQuery("a", 0f, 1000f)
        assertEquals(q1, q2)
        assertEquals(q1.hashCode(), q2.hashCode())
        assertFalse(q1 == FloatPoint.newRangeQuery("a", 1f, 1000f))

        q1 = DoublePoint.newRangeQuery("a", 0.0, 1000.0)
        q2 = DoublePoint.newRangeQuery("a", 0.0, 1000.0)
        assertEquals(q1, q2)
        assertEquals(q1.hashCode(), q2.hashCode())
        assertFalse(q1 == DoublePoint.newRangeQuery("a", 1.0, 1000.0))

        val zeros = ByteArray(5)
        val ones = ByteArray(5)
        ones.fill(0xff.toByte())
        q1 = BinaryPoint.newRangeQuery("a", arrayOf(zeros), arrayOf(ones))
        q2 = BinaryPoint.newRangeQuery("a", arrayOf(zeros), arrayOf(ones))
        assertEquals(q1, q2)
        assertEquals(q1.hashCode(), q2.hashCode())
        val other = ones.copyOf()
        other[2] = 5.toByte()
        assertFalse(q1 == BinaryPoint.newRangeQuery("a", arrayOf(zeros), arrayOf(other)))
    }

    @Test
    fun testPointExactEquals() {
        var q1: Query
        var q2: Query

        q1 = IntPoint.newExactQuery("a", 1000)
        q2 = IntPoint.newExactQuery("a", 1000)
        assertEquals(q1, q2)
        assertEquals(q1.hashCode(), q2.hashCode())
        assertFalse(q1 == IntPoint.newExactQuery("a", 1))
        assertFalse(q1 == IntPoint.newExactQuery("b", 1000))

        assertTrue(q1 is PointRangeQuery && q2 is PointRangeQuery)
        var pq1 = q1 as PointRangeQuery
        var pq2 = q2 as PointRangeQuery

        assertTrue(pq1.lowerPoint.contentEquals(pq2.lowerPoint))
        assertTrue(pq1.upperPoint.contentEquals(pq2.upperPoint))

        q1 = LongPoint.newExactQuery("a", 1000)
        q2 = LongPoint.newExactQuery("a", 1000)
        assertEquals(q1, q2)
        assertEquals(q1.hashCode(), q2.hashCode())
        assertFalse(q1 == LongPoint.newExactQuery("a", 1))

        assertTrue(q1 is PointRangeQuery && q2 is PointRangeQuery)
        pq1 = q1 as PointRangeQuery
        pq2 = q2 as PointRangeQuery

        assertTrue(pq1.lowerPoint.contentEquals(pq2.lowerPoint))
        assertTrue(pq1.upperPoint.contentEquals(pq2.upperPoint))

        q1 = FloatPoint.newExactQuery("a", 1000f)
        q2 = FloatPoint.newExactQuery("a", 1000f)
        assertEquals(q1, q2)
        assertEquals(q1.hashCode(), q2.hashCode())
        assertFalse(q1 == FloatPoint.newExactQuery("a", 1f))

        assertTrue(q1 is PointRangeQuery && q2 is PointRangeQuery)
        pq1 = q1 as PointRangeQuery
        pq2 = q2 as PointRangeQuery

        assertTrue(pq1.lowerPoint.contentEquals(pq2.lowerPoint))
        assertTrue(pq1.upperPoint.contentEquals(pq2.upperPoint))

        q1 = DoublePoint.newExactQuery("a", 1000.0)
        q2 = DoublePoint.newExactQuery("a", 1000.0)
        assertEquals(q1, q2)
        assertEquals(q1.hashCode(), q2.hashCode())
        assertFalse(q1 == DoublePoint.newExactQuery("a", 1.0))

        assertTrue(q1 is PointRangeQuery && q2 is PointRangeQuery)
        pq1 = q1 as PointRangeQuery
        pq2 = q2 as PointRangeQuery

        assertTrue(pq1.lowerPoint.contentEquals(pq2.lowerPoint))
        assertTrue(pq1.upperPoint.contentEquals(pq2.upperPoint))

        val ones2 = ByteArray(5)
        ones2.fill(0xff.toByte())
        q1 = BinaryPoint.newExactQuery("a", ones2)
        q2 = BinaryPoint.newExactQuery("a", ones2)
        assertEquals(q1, q2)
        assertEquals(q1.hashCode(), q2.hashCode())
        val other2 = ones2.copyOf()
        other2[2] = 5.toByte()
        assertFalse(q1 == BinaryPoint.newExactQuery("a", other2))

        assertTrue(q1 is PointRangeQuery && q2 is PointRangeQuery)
        pq1 = q1 as PointRangeQuery
        pq2 = q2 as PointRangeQuery

        assertTrue(pq1.lowerPoint.contentEquals(pq2.lowerPoint))
        assertTrue(pq1.upperPoint.contentEquals(pq2.upperPoint))
    }

    @Test
    fun testPointInSetEquals() {
        var q1: Query
        var q2: Query
        q1 = IntPoint.newSetQuery("a", 0, 1000, 17)
        q2 = IntPoint.newSetQuery("a", 17, 0, 1000)
        assertEquals(q1, q2)
        assertEquals(q1.hashCode(), q2.hashCode())
        assertFalse(q1 == IntPoint.newSetQuery("a", 1, 17, 1000))
        assertFalse(q1 == IntPoint.newSetQuery("b", 0, 1000, 17))

        q1 = LongPoint.newSetQuery("a", 0, 1000, 17)
        q2 = LongPoint.newSetQuery("a", 17, 0, 1000)
        assertEquals(q1, q2)
        assertEquals(q1.hashCode(), q2.hashCode())
        assertFalse(q1 == LongPoint.newSetQuery("a", 1, 17, 1000))

        q1 = FloatPoint.newSetQuery("a", 0f, 1000f, 17f)
        q2 = FloatPoint.newSetQuery("a", 17f, 0f, 1000f)
        assertEquals(q1, q2)
        assertEquals(q1.hashCode(), q2.hashCode())
        assertFalse(q1 == FloatPoint.newSetQuery("a", 1f, 17f, 1000f))

        q1 = DoublePoint.newSetQuery("a", 0.0, 1000.0, 17.0)
        q2 = DoublePoint.newSetQuery("a", 17.0, 0.0, 1000.0)
        assertEquals(q1, q2)
        assertEquals(q1.hashCode(), q2.hashCode())
        assertFalse(q1 == DoublePoint.newSetQuery("a", 1.0, 17.0, 1000.0))

        val zeros = ByteArray(5)
        val ones = ByteArray(5)
        ones.fill(0xff.toByte())
        q1 = BinaryPoint.newSetQuery("a", arrayOf(zeros, ones))
        q2 = BinaryPoint.newSetQuery("a", arrayOf(zeros, ones))
        assertEquals(q1, q2)
        assertEquals(q1.hashCode(), q2.hashCode())
        val other = ones.copyOf()
        other[2] = 5.toByte()
        assertFalse(q1 == BinaryPoint.newSetQuery("a", arrayOf(zeros, other)))
    }

    @Test
    fun testInvalidPointLength() {
        val e = expectThrows(
            IllegalArgumentException::class
        ) {
            object : PointRangeQuery("field", ByteArray(4), ByteArray(8), 1) {
                override fun toString(dimension: Int, value: ByteArray): String {
                    return "foo"
                }
            }
        }
        assertEquals("lowerPoint has length=4 but upperPoint has different length=8", e.message)
    }

    @Test
    fun testNextUp() {
        assertTrue(Double.compare(0.0, DoublePoint.nextUp(-0.0)) == 0)
        assertTrue(Double.compare(Double.MIN_VALUE, DoublePoint.nextUp(0.0)) == 0)
        assertTrue(Double.compare(Double.POSITIVE_INFINITY, DoublePoint.nextUp(Double.MAX_VALUE)) == 0)
        assertTrue(
            Double.compare(Double.POSITIVE_INFINITY, DoublePoint.nextUp(Double.POSITIVE_INFINITY)) == 0
        )
        assertTrue(
            Double.compare(-Double.MAX_VALUE, DoublePoint.nextUp(Double.NEGATIVE_INFINITY)) == 0
        )

        assertTrue(Float.compare(0f, FloatPoint.nextUp(-0f)) == 0)
        assertTrue(Float.compare(Float.MIN_VALUE, FloatPoint.nextUp(0f)) == 0)
        assertTrue(Float.compare(Float.POSITIVE_INFINITY, FloatPoint.nextUp(Float.MAX_VALUE)) == 0)
        assertTrue(
            Float.compare(Float.POSITIVE_INFINITY, FloatPoint.nextUp(Float.POSITIVE_INFINITY)) == 0
        )
        assertTrue(Float.compare(-Float.MAX_VALUE, FloatPoint.nextUp(Float.NEGATIVE_INFINITY)) == 0)
    }

    @Test
    fun testNextDown() {
        assertTrue(Double.compare(-0.0, DoublePoint.nextDown(0.0)) == 0)
        assertTrue(Double.compare(-Double.MIN_VALUE, DoublePoint.nextDown(-0.0)) == 0)
        assertTrue(
            Double.compare(Double.NEGATIVE_INFINITY, DoublePoint.nextDown(-Double.MAX_VALUE)) == 0
        )
        assertTrue(
            Double.compare(Double.NEGATIVE_INFINITY, DoublePoint.nextDown(Double.NEGATIVE_INFINITY)) == 0
        )
        assertTrue(
            Double.compare(Double.MAX_VALUE, DoublePoint.nextDown(Double.POSITIVE_INFINITY)) == 0
        )

        assertTrue(Float.compare(-0f, FloatPoint.nextDown(0f)) == 0)
        assertTrue(Float.compare(-Float.MIN_VALUE, FloatPoint.nextDown(-0f)) == 0)
        assertTrue(Float.compare(Float.NEGATIVE_INFINITY, FloatPoint.nextDown(-Float.MAX_VALUE)) == 0)
        assertTrue(
            Float.compare(Float.NEGATIVE_INFINITY, FloatPoint.nextDown(Float.NEGATIVE_INFINITY)) == 0
        )
        assertTrue(Float.compare(Float.MAX_VALUE, FloatPoint.nextDown(Float.POSITIVE_INFINITY)) == 0)
    }

    @Nightly
    @Test
    @Throws(IOException::class)
    fun testInversePointRange() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig())
        val numDims = TestUtil.nextInt(random(), 1, 3)
        val numDocs =
            atLeast(
                10 * BKDConfig.DEFAULT_MAX_POINTS_IN_LEAF_NODE
            ) // we need multiple leaves to enable this optimization
        for (i in 0 until numDocs) {
            val doc = Document()
            val values = IntArray(numDims)
            values.fill(i)
            doc.add(IntPoint("f", *values))
            w.addDocument(doc)
        }
        w.forceMerge(1)
        val r = DirectoryReader.open(w)
        w.close()

        val searcher = newSearcher(r)
        val low = IntArray(numDims)
        val high = IntArray(numDims)
        high.fill(numDocs - 2)
        assertEquals(high[0] - low[0] + 1, searcher.count(IntPoint.newRangeQuery("f", low, high)))
        low.fill(1)
        assertEquals(high[0] - low[0] + 1, searcher.count(IntPoint.newRangeQuery("f", low, high)))
        high.fill(numDocs - 1)
        assertEquals(high[0] - low[0] + 1, searcher.count(IntPoint.newRangeQuery("f", low, high)))
        low.fill(BKDConfig.DEFAULT_MAX_POINTS_IN_LEAF_NODE + 1)
        assertEquals(high[0] - low[0] + 1, searcher.count(IntPoint.newRangeQuery("f", low, high)))
        high.fill(numDocs - BKDConfig.DEFAULT_MAX_POINTS_IN_LEAF_NODE)
        assertEquals(high[0] - low[0] + 1, searcher.count(IntPoint.newRangeQuery("f", low, high)))

        r.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testRangeQuerySkipsNonMatchingSegments() {
        val dir = newDirectory()
        val w = IndexWriter(dir, newIndexWriterConfig())
        val doc = Document()
        doc.add(IntPoint("field", 2))
        doc.add(IntPoint("field2d", 1, 3))
        w.addDocument(doc)

        val reader = DirectoryReader.open(w)
        val searcher = newSearcher(reader)

        var query: Query = IntPoint.newRangeQuery("field", 0, 1)
        var weight =
            searcher.createWeight(searcher.rewrite(query), ScoreMode.COMPLETE_NO_SCORES, 1f)
        assertNull(weight.scorerSupplier(reader.leaves().get(0)))

        query = IntPoint.newRangeQuery("field", 3, 4)
        weight = searcher.createWeight(searcher.rewrite(query), ScoreMode.COMPLETE_NO_SCORES, 1f)
        assertNull(weight.scorerSupplier(reader.leaves().get(0)))

        query = IntPoint.newRangeQuery("field2d", intArrayOf(0, 0), intArrayOf(2, 2))
        weight = searcher.createWeight(searcher.rewrite(query), ScoreMode.COMPLETE_NO_SCORES, 1f)
        assertNull(weight.scorerSupplier(reader.leaves().get(0)))

        query = IntPoint.newRangeQuery("field2d", intArrayOf(2, 2), intArrayOf(4, 4))
        weight = searcher.createWeight(searcher.rewrite(query), ScoreMode.COMPLETE_NO_SCORES, 1f)
        assertNull(weight.scorerSupplier(reader.leaves().get(0)))

        reader.close()
        w.close()
        dir.close()
    }

    // ---- static helpers ----

    companion object {
        // Controls what range of values we randomly generate, so we sometimes test narrow ranges:
        var valueMid: Long
        var valueRange: Int

        init {
            val r = LuceneTestCase.random()
            if (r.nextBoolean()) {
                valueMid = r.nextLong()
                if (r.nextBoolean()) {
                    // Wide range
                    valueRange = TestUtil.nextInt(r, 1, Int.MAX_VALUE)
                } else {
                    // Narrow range
                    valueRange = TestUtil.nextInt(r, 1, 100000)
                }
                if (VERBOSE) {
                    println("TEST: will generate long values $valueMid +/- $valueRange")
                }
            } else {
                valueMid = 0L
                // All longs
                valueRange = 0
                if (VERBOSE) {
                    println("TEST: will generate all long values")
                }
            }
        }

        fun bytesToString(bytes: ByteArray?): String {
            if (bytes == null) {
                return "null"
            }
            return newBytesRef(bytes).toString()
        }

        private fun matches(bytesPerDim: Int, lower: Array<ByteArray>, upper: Array<ByteArray>, value: Array<ByteArray>): Boolean {
            val numDims = lower.size
            for (dim in 0 until numDims) {

                if (Arrays.compareUnsigned(value[dim], 0, bytesPerDim, lower[dim], 0, bytesPerDim) < 0) {
                    // Value is below the lower bound, on this dim
                    return false
                }

                if (Arrays.compareUnsigned(value[dim], 0, bytesPerDim, upper[dim], 0, bytesPerDim) > 0) {
                    // Value is above the upper bound, on this dim
                    return false
                }
            }

            return true
        }

        private fun randomValue(): Long {
            return if (valueRange == 0) {
                LuceneTestCase.random().nextLong()
            } else {
                valueMid + TestUtil.nextInt(LuceneTestCase.random(), -valueRange, valueRange)
            }
        }

        // Right zero pads:
        private fun toUTF8(s: String): ByteArray {
            return s.encodeToByteArray()
        }

        private fun toUTF8(s: String, length: Int): ByteArray {
            val bytes = s.encodeToByteArray()
            if (length < bytes.size) {
                throw IllegalArgumentException(
                    "length=$length but string's UTF8 bytes has length=${bytes.size}"
                )
            }
            val result = ByteArray(length)
            System.arraycopy(bytes, 0, result, 0, bytes.size)
            return result
        }

        private fun getCodec(): Codec {
            if (Codec.default.name == "Lucene84") {
                val maxPointsInLeafNode = TestUtil.nextInt(LuceneTestCase.random(), 16, 2048)
                val maxMBSortInHeap = 5.0 + (3 * LuceneTestCase.random().nextDouble())
                if (VERBOSE) {
                    println(
                        "TEST: using Lucene60PointsFormat with maxPointsInLeafNode=$maxPointsInLeafNode" +
                                " and maxMBSortInHeap=$maxMBSortInHeap"
                    )
                }

                return object : FilterCodec("Lucene84", Codec.default) {
                    override fun pointsFormat(): PointsFormat {
                        return object : PointsFormat() {
                            @Throws(IOException::class)
                            override fun fieldsWriter(writeState: SegmentWriteState): PointsWriter {
                                return Lucene90PointsWriter(writeState, maxPointsInLeafNode, maxMBSortInHeap)
                            }

                            @Throws(IOException::class)
                            override fun fieldsReader(readState: SegmentReadState): PointsReader {
                                return Lucene90PointsReader(readState)
                            }
                        }
                    }
                }
            } else {
                return Codec.default
            }
        }
    }
}
