package org.gnit.lucenekmp.index

import kotlinx.coroutines.Runnable
import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.jdkport.Executor
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.KnnCollector
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.RandomizedTest
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.Version
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestSegmentToThreadMapping : LuceneTestCase() {

    companion object{
        private fun dummyIndexReader(maxDoc: Int): LeafReader {
            return object : LeafReader() {
                override fun maxDoc(): Int {
                    return maxDoc
                }

                override fun numDocs(): Int {
                    return maxDoc
                }

                override val fieldInfos: FieldInfos
                    get() = FieldInfos.EMPTY

                override val liveDocs: Bits?
                    get() = null

                @Throws(IOException::class)
                override fun terms(field: String?): Terms? {
                    return null
                }

                override fun termVectors(): TermVectors {
                    return TermVectors.EMPTY
                }

                override fun getNumericDocValues(field: String): NumericDocValues? {
                    return null
                }

                override fun getBinaryDocValues(field: String): BinaryDocValues? {
                    return null
                }

                override fun getSortedDocValues(field: String): SortedDocValues? {
                    return null
                }

                override fun getSortedNumericDocValues(field: String): SortedNumericDocValues? {
                    return null
                }

                override fun getSortedSetDocValues(field: String): SortedSetDocValues? {
                    return null
                }

                override fun getNormValues(field: String): NumericDocValues? {
                    return null
                }

                override fun getDocValuesSkipper(field: String): DocValuesSkipper? {
                    return null
                }

                override fun getPointValues(field: String): PointValues? {
                    return null
                }

                override fun getFloatVectorValues(field: String): FloatVectorValues? {
                    return null
                }

                override fun getByteVectorValues(field: String): ByteVectorValues? {
                    return null
                }

                override fun searchNearestVectors(
                    field: String, target: FloatArray, knnCollector: KnnCollector, acceptDocs: Bits?
                ) {
                }

                override fun searchNearestVectors(
                    field: String, target: ByteArray, knnCollector: KnnCollector, acceptDocs: Bits?
                ) {
                }

                override fun doClose() {}

                override fun storedFields(): StoredFields {
                    return object : StoredFields() {
                        override fun document(doc: Int, visitor: StoredFieldVisitor) {}
                    }
                }

                @Throws(IOException::class)
                override fun checkIntegrity() {
                }

                override val metaData: LeafMetaData
                    get() = LeafMetaData(Version.LATEST.major, Version.LATEST, null, false)

                override val coreCacheHelper: CacheHelper?
                    get() = null

                override val readerCacheHelper: CacheHelper?
                    get() = null
            }
        }

        private fun createLeafReaderContexts(vararg maxDocs: Int): MutableList<LeafReaderContext> {
            val leafReaderContexts: MutableList<LeafReaderContext> = mutableListOf()
            for (maxDoc in maxDocs) {
                leafReaderContexts.add(LeafReaderContext(dummyIndexReader(maxDoc)))
            }
            leafReaderContexts.shuffle(random())
            return leafReaderContexts
        }
    }

    @Test
    fun testSingleSlice() {
        val leafReaderContexts: MutableList<LeafReaderContext> =
            createLeafReaderContexts(50000, 30000, 30000, 30000)
        val resultSlices: Array<IndexSearcher.LeafSlice> =
            IndexSearcher.slices(
                leafReaderContexts, 250000, RandomizedTest.randomIntBetween(4, 10), false
            )
        assertEquals(1, resultSlices.size.toLong())
        assertEquals(4, resultSlices[0].partitions.size.toLong())
    }

    @Test
    fun testSingleSliceWithPartitions() {
        val leafReaderContexts: MutableList<LeafReaderContext> =
            createLeafReaderContexts(50000, 30000, 30000, 30000)
        val resultSlices: Array<IndexSearcher.LeafSlice> =
            IndexSearcher.slices(
                leafReaderContexts, 250000, RandomizedTest.randomIntBetween(4, 10), true
            )
        assertEquals(1, resultSlices.size.toLong())
        assertEquals(4, resultSlices[0].partitions.size.toLong())
    }

    @Test
    fun testMaxSegmentsPerSlice() {
        val leafReaderContexts: MutableList<LeafReaderContext> =
            createLeafReaderContexts(50000, 30000, 30000, 30000)
        run {
            val resultSlices: Array<IndexSearcher.LeafSlice> =
                IndexSearcher.slices(leafReaderContexts, 250000, 3, false)
            assertEquals(2, resultSlices.size.toLong())
            assertEquals(3, resultSlices[0].partitions.size.toLong())
            assertEquals(110000, resultSlices[0].maxDocs.toLong())
            assertEquals(1, resultSlices[1].partitions.size.toLong())
            assertEquals(30000, resultSlices[1].maxDocs.toLong())
        }
        run {
            val resultSlices: Array<IndexSearcher.LeafSlice> =
                IndexSearcher.slices(leafReaderContexts, 250000, 2, false)
            assertEquals(2, resultSlices.size.toLong())
            assertEquals(2, resultSlices[0].partitions.size.toLong())
            assertEquals(80000, resultSlices[0].maxDocs.toLong())
            assertEquals(2, resultSlices[1].partitions.size.toLong())
            assertEquals(60000, resultSlices[1].maxDocs.toLong())
        }
        run {
            val resultSlices: Array<IndexSearcher.LeafSlice> =
                IndexSearcher.slices(leafReaderContexts, 250000, 1, false)
            assertEquals(4, resultSlices.size.toLong())
            assertEquals(1, resultSlices[0].partitions.size.toLong())
            assertEquals(50000, resultSlices[0].maxDocs.toLong())
            assertEquals(1, resultSlices[1].partitions.size.toLong())
            assertEquals(30000, resultSlices[1].maxDocs.toLong())
            assertEquals(1, resultSlices[2].partitions.size.toLong())
            assertEquals(30000, resultSlices[2].maxDocs.toLong())
            assertEquals(1, resultSlices[3].partitions.size.toLong())
            assertEquals(30000, resultSlices[3].maxDocs.toLong())
        }
    }

    @Test
    fun testMaxSegmentsPerSliceWithPartitions() {
        val leafReaderContexts: MutableList<LeafReaderContext> =
            createLeafReaderContexts(50000, 30000, 30000, 30000)
        run {
            val resultSlices: Array<IndexSearcher.LeafSlice> =
                IndexSearcher.slices(leafReaderContexts, 250000, 3, true)
            assertEquals(2, resultSlices.size.toLong())
            assertEquals(3, resultSlices[0].partitions.size.toLong())
            assertEquals(110000, resultSlices[0].maxDocs.toLong())
            assertEquals(1, resultSlices[1].partitions.size.toLong())
            assertEquals(30000, resultSlices[1].maxDocs.toLong())
        }
        run {
            val resultSlices: Array<IndexSearcher.LeafSlice> =
                IndexSearcher.slices(leafReaderContexts, 250000, 2, true)
            assertEquals(2, resultSlices.size.toLong())
            assertEquals(2, resultSlices[0].partitions.size.toLong())
            assertEquals(80000, resultSlices[0].maxDocs.toLong())
            assertEquals(2, resultSlices[1].partitions.size.toLong())
            assertEquals(60000, resultSlices[1].maxDocs.toLong())
        }
        run {
            val resultSlices: Array<IndexSearcher.LeafSlice> =
                IndexSearcher.slices(leafReaderContexts, 250000, 1, true)
            assertEquals(4, resultSlices.size.toLong())
            assertEquals(1, resultSlices[0].partitions.size.toLong())
            assertEquals(50000, resultSlices[0].maxDocs.toLong())
            assertEquals(1, resultSlices[1].partitions.size.toLong())
            assertEquals(30000, resultSlices[1].maxDocs.toLong())
            assertEquals(1, resultSlices[2].partitions.size.toLong())
            assertEquals(30000, resultSlices[2].maxDocs.toLong())
            assertEquals(1, resultSlices[3].partitions.size.toLong())
            assertEquals(30000, resultSlices[3].maxDocs.toLong())
        }
    }

    @Test
    fun testSmallSegments() {
        val leafReaderContexts: MutableList<LeafReaderContext> =
            createLeafReaderContexts(10000, 10000, 10000, 10000, 10000, 10000, 130000, 130000)

        run {
            val resultSlices: Array<IndexSearcher.LeafSlice> =
                IndexSearcher.slices(leafReaderContexts, 250000, 5, false)
            assertEquals(3, resultSlices.size.toLong())

            assertEquals(2, resultSlices[0].partitions.size.toLong())
            assertEquals(260000, resultSlices[0].maxDocs.toLong())
            assertEquals(5, resultSlices[1].partitions.size.toLong())
            assertEquals(50000, resultSlices[1].maxDocs.toLong())
            assertEquals(1, resultSlices[2].partitions.size.toLong())
            assertEquals(10000, resultSlices[2].maxDocs.toLong())
        }
        run {
            val resultSlices: Array<IndexSearcher.LeafSlice> =
                IndexSearcher.slices(leafReaderContexts, 130000, 5, false)
            assertEquals(3, resultSlices.size.toLong())
            // this is odd, because we allow two segments in the same slice with both size ==
            // maxDocsPerSlice
            assertEquals(2, resultSlices[0].partitions.size.toLong())
            assertEquals(260000, resultSlices[0].maxDocs.toLong())
            assertEquals(5, resultSlices[1].partitions.size.toLong())
            assertEquals(50000, resultSlices[1].maxDocs.toLong())
            assertEquals(1, resultSlices[2].partitions.size.toLong())
            assertEquals(10000, resultSlices[2].maxDocs.toLong())
        }
    }

    @Test
    fun testSmallSegmentsWithPartitions() {
        val leafReaderContexts: MutableList<LeafReaderContext> =
            createLeafReaderContexts(10000, 10000, 10000, 10000, 10000, 10000, 130000, 130000)

        run {
            val resultSlices: Array<IndexSearcher.LeafSlice> =
                IndexSearcher.slices(leafReaderContexts, 250000, 5, true)
            assertEquals(3, resultSlices.size.toLong())

            assertEquals(2, resultSlices[0].partitions.size.toLong())
            assertEquals(260000, resultSlices[0].maxDocs.toLong())
            assertEquals(5, resultSlices[1].partitions.size.toLong())
            assertEquals(50000, resultSlices[1].maxDocs.toLong())
            assertEquals(1, resultSlices[2].partitions.size.toLong())
            assertEquals(10000, resultSlices[2].maxDocs.toLong())
        }
        run {
            val resultSlices: Array<IndexSearcher.LeafSlice> =
                IndexSearcher.slices(leafReaderContexts, 130000, 5, true)
            assertEquals(3, resultSlices.size.toLong())
            // this is odd, because we allow two segments in the same slice with both size ==
            // maxDocsPerSlice
            assertEquals(2, resultSlices[0].partitions.size.toLong())
            assertEquals(260000, resultSlices[0].maxDocs.toLong())
            assertEquals(5, resultSlices[1].partitions.size.toLong())
            assertEquals(50000, resultSlices[1].maxDocs.toLong())
            assertEquals(1, resultSlices[2].partitions.size.toLong())
            assertEquals(10000, resultSlices[2].maxDocs.toLong())
        }
    }

    @Test
    fun testLargeSlices() {
        val leafReaderContexts: MutableList<LeafReaderContext> =
            createLeafReaderContexts(290900, 170000, 170000, 170000)
        val resultSlices: Array<IndexSearcher.LeafSlice> =
            IndexSearcher.slices(leafReaderContexts, 250000, 5, false)

        assertEquals(3, resultSlices.size.toLong())
        assertEquals(1, resultSlices[0].partitions.size.toLong())
        assertEquals(2, resultSlices[1].partitions.size.toLong())
        assertEquals(1, resultSlices[2].partitions.size.toLong())
    }

    @Test
    fun testLargeSlicesWithPartitions() {
        val leafReaderContexts: MutableList<LeafReaderContext> =
            createLeafReaderContexts(290900, 170000, 170000, 170000)
        val resultSlices: Array<IndexSearcher.LeafSlice> =
            IndexSearcher.slices(
                leafReaderContexts, 250000, RandomizedTest.randomIntBetween(5, 10), true
            )

        assertEquals(4, resultSlices.size.toLong())
        assertEquals(1, resultSlices[0].partitions.size.toLong())
        assertEquals(145450, resultSlices[0].maxDocs.toLong())
        assertEquals(1, resultSlices[1].partitions.size.toLong())
        assertEquals(145450, resultSlices[1].maxDocs.toLong())
        assertEquals(2, resultSlices[2].partitions.size.toLong())
        assertEquals(340000, resultSlices[2].maxDocs.toLong())
        assertEquals(1, resultSlices[3].partitions.size.toLong())
        assertEquals(170000, resultSlices[3].maxDocs.toLong())
    }

    @Test
    fun testSingleSegmentPartitions() {
        val leafReaderContexts: MutableList<LeafReaderContext> = createLeafReaderContexts(750001)
        val resultSlices: Array<IndexSearcher.LeafSlice> =
            IndexSearcher.slices(
                leafReaderContexts, 250000, RandomizedTest.randomIntBetween(1, 10), true
            )

        assertEquals(4, resultSlices.size.toLong())
        assertEquals(1, resultSlices[0].partitions.size.toLong())
        assertEquals(187500, resultSlices[0].maxDocs.toLong())
        assertEquals(1, resultSlices[1].partitions.size.toLong())
        assertEquals(187500, resultSlices[1].maxDocs.toLong())
        assertEquals(1, resultSlices[2].partitions.size.toLong())
        assertEquals(187500, resultSlices[2].maxDocs.toLong())
        assertEquals(1, resultSlices[3].partitions.size.toLong())
        assertEquals(187501, resultSlices[3].maxDocs.toLong())
    }

    @Test
    fun testExtremeSegmentsPartitioning() {
        val leafReaderContexts: MutableList<LeafReaderContext> = createLeafReaderContexts(2, 5, 10)
        val resultSlices: Array<IndexSearcher.LeafSlice> = IndexSearcher.slices(leafReaderContexts, 1, 1, true)

        assertEquals(12, resultSlices.size.toLong())
        var i = 0
        for (leafSlice in resultSlices) {
            if (i++ > 4) {
                assertEquals(1, leafSlice.maxDocs.toLong())
            } else {
                assertEquals(2, leafSlice.maxDocs.toLong())
            }
            assertEquals(1, leafSlice.partitions.size.toLong())
        }
    }

    @Test
    @Throws(Exception::class)
    fun testIntraSliceDocIDOrder() {
        val dir: Directory = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        w.addDocument(Document())
        w.addDocument(Document())
        w.commit()
        w.addDocument(Document())
        w.addDocument(Document())
        w.commit()
        val r: IndexReader = w.reader
        w.close()

        val s = IndexSearcher(r) { `_`: Runnable -> }
        val slices: Array<IndexSearcher.LeafSlice> = s.slices
        assertNotNull(slices)

        for (leafSlice in slices!!) {
            var previousDocBase: Int = leafSlice.partitions[0].ctx.docBase

            for (leafReaderContextPartition in leafSlice.partitions) {
                assertTrue(previousDocBase <= leafReaderContextPartition.ctx.docBase)
                previousDocBase = leafReaderContextPartition.ctx.docBase
            }
        }
        IOUtils.close(r, dir)
    }

    @Test
    @Throws(Exception::class)
    fun testIntraSliceDocIDOrderWithPartitions() {
        val dir: Directory = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        w.addDocument(Document())
        w.addDocument(Document())
        w.commit()
        w.addDocument(Document())
        w.addDocument(Document())
        w.commit()
        val r: IndexReader = w.reader
        w.close()

        val s: IndexSearcher =
            object : IndexSearcher(r, Executor { `_`: Runnable -> }) {
                override fun slices(leaves: MutableList<LeafReaderContext>): Array<LeafSlice> {
                    // force partitioning of segment with max docs per slice set to 1: 1 doc per partition.
                    return slices(leaves, 1, 1, true)
                }
            }
        val slices: Array<IndexSearcher.LeafSlice> = s.slices
        assertNotNull(slices)

        for (leafSlice in slices) {
            var previousDocBase: Int = leafSlice.partitions[0].ctx.docBase

            for (leafReaderContextPartition in leafSlice.partitions) {
                assertTrue(previousDocBase <= leafReaderContextPartition.ctx.docBase)
                previousDocBase = leafReaderContextPartition.ctx.docBase
            }
        }
        IOUtils.close(r, dir)
    }

    @Test
    fun testRandom() {
        val leafReaderContexts: MutableList<LeafReaderContext> = mutableListOf()
        val max = 500000
        val min = 10000
        val numSegments: Int = 1 + random().nextInt(50)

        for (i in 0..<numSegments) {
            leafReaderContexts.add(
                LeafReaderContext(dummyIndexReader(random().nextInt((max - min) + 1) + min))
            )
        }
        val resultSlices: Array<IndexSearcher.LeafSlice> =
            IndexSearcher.slices(leafReaderContexts, 250000, 5, random().nextBoolean())
        assertTrue(resultSlices.size > 0)
    }
}
