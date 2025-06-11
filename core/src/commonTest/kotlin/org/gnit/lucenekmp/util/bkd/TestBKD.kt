package org.gnit.lucenekmp.util.bkd

import okio.fakefilesystem.FakeFileSystem
import okio.Path.Companion.toPath
import org.gnit.lucenekmp.jdkport.BitSet
import org.gnit.lucenekmp.jdkport.Files
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.NIOFSDirectory
import org.gnit.lucenekmp.store.FSLockFactory
import org.gnit.lucenekmp.store.FlushInfo
import kotlin.test.Ignore
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.NumericUtils
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@Ignore
class TestBKD : LuceneTestCase() {
    private lateinit var fakeFileSystem: FakeFileSystem

    @BeforeTest
    fun setUp() {
        fakeFileSystem = FakeFileSystem()
        Files.setFileSystem(fakeFileSystem)
    }

    @AfterTest
    fun tearDown() {
        Files.resetFileSystem()
    }

    private fun getDirectory(): Directory {
        val path = "/tmp".toPath()
        fakeFileSystem.createDirectories(path)
        return NIOFSDirectory(path, FSLockFactory.default, fakeFileSystem)
    }

    private fun getPointValues(input: org.gnit.lucenekmp.store.IndexInput): org.gnit.lucenekmp.index.PointValues {
        return BKDReader(input, input, input)
    }

    @Test
    fun testBasicInts1D() {
        val config = BKDConfig(1, 1, 4, 2)
        getDirectory().use { dir ->
            val writer = BKDWriter(100, dir, "tmp", config, 1.0, 100)
            val scratch = ByteArray(4)
            for (docID in 0 until 100) {
                NumericUtils.intToSortableBytes(docID, scratch, 0)
                writer.add(scratch, docID)
            }

            val indexFP: Long
            dir.createOutput("bkd", IOContext(FlushInfo(100, 0L))).use { out ->
                val finalizer = writer.finish(out, out, out)
                indexFP = out.filePointer
                finalizer?.run()
            }

            dir.openInput("bkd", IOContext(FlushInfo(100, 0L))).use { `in` ->
                `in`.seek(indexFP)
                val r = getPointValues(`in`)
                val queryMin = Array(1) { ByteArray(4) }
                NumericUtils.intToSortableBytes(42, queryMin[0], 0)
                val queryMax = Array(1) { ByteArray(4) }
                NumericUtils.intToSortableBytes(87, queryMax[0], 0)

                val hits = BitSet()
                r.intersect(getIntersectVisitor(hits, queryMin, queryMax, config))

                for (docID in 0 until 100) {
                    val expected = docID in 42..87
                    val actual = hits.get(docID)
                    assertEquals(expected, actual, "docID=$docID")
                }
            }
        }
    }

    private fun getIntersectVisitor(
        hits: BitSet,
        queryMin: Array<ByteArray>,
        queryMax: Array<ByteArray>,
        config: BKDConfig
    ): org.gnit.lucenekmp.index.PointValues.IntersectVisitor {
        return object : org.gnit.lucenekmp.index.PointValues.IntersectVisitor {
            override fun visit(docID: Int) {
                hits.set(docID)
            }

            override fun visit(docID: Int, packedValue: ByteArray) {
                for (dim in 0 until config.numIndexDims) {
                    val start = dim * config.bytesPerDim
                    val end = start + config.bytesPerDim
                    if (org.gnit.lucenekmp.jdkport.Arrays.compareUnsigned(
                            packedValue, start, end,
                            queryMin[dim], 0, config.bytesPerDim
                        ) < 0 ||
                        org.gnit.lucenekmp.jdkport.Arrays.compareUnsigned(
                            packedValue, start, end,
                            queryMax[dim], 0, config.bytesPerDim
                        ) > 0
                    ) {
                        return
                    }
                }
                hits.set(docID)
            }

            override fun compare(minPackedValue: ByteArray, maxPackedValue: ByteArray): org.gnit.lucenekmp.index.PointValues.Relation {
                var crosses = false
                for (dim in 0 until config.numIndexDims) {
                    val start = dim * config.bytesPerDim
                    val end = start + config.bytesPerDim
                    if (org.gnit.lucenekmp.jdkport.Arrays.compareUnsigned(
                            maxPackedValue, start, end,
                            queryMin[dim], 0, config.bytesPerDim
                        ) < 0 ||
                        org.gnit.lucenekmp.jdkport.Arrays.compareUnsigned(
                            minPackedValue, start, end,
                            queryMax[dim], 0, config.bytesPerDim
                        ) > 0
                    ) {
                        return org.gnit.lucenekmp.index.PointValues.Relation.CELL_OUTSIDE_QUERY
                    } else if (org.gnit.lucenekmp.jdkport.Arrays.compareUnsigned(
                            minPackedValue, start, end,
                            queryMin[dim], 0, config.bytesPerDim
                        ) < 0 ||
                        org.gnit.lucenekmp.jdkport.Arrays.compareUnsigned(
                            maxPackedValue, start, end,
                            queryMax[dim], 0, config.bytesPerDim
                        ) > 0
                    ) {
                        crosses = true
                    }
                }
                return if (crosses) {
                    org.gnit.lucenekmp.index.PointValues.Relation.CELL_CROSSES_QUERY
                } else {
                    org.gnit.lucenekmp.index.PointValues.Relation.CELL_INSIDE_QUERY
                }
            }
        }
    }
}

