package org.gnit.lucenekmp.util.packed

import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.gnit.lucenekmp.jdkport.Files
import org.gnit.lucenekmp.store.*
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.LongValues
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TestDirectMonotonic : LuceneTestCase() {
    private lateinit var fs: FakeFileSystem

    @BeforeTest
    fun setUp() {
        fs = FakeFileSystem()
        Files.setFileSystem(fs)
    }

    @AfterTest
    fun tearDown() {
        Files.resetFileSystem()
    }

    private fun newDirectory(): Directory {
        val path = "/dir".toPath()
        fs.createDirectories(path)
        return NIOFSDirectory(path, FSLockFactory.default, fs)
    }

    @Test
    fun testValidation() {
        val meta = ByteBuffersIndexOutput(ByteBuffersDataOutput(), "meta", "meta")
        val data = ByteBuffersIndexOutput(ByteBuffersDataOutput(), "data", "data")
        var e = expectThrows(IllegalArgumentException::class) {
            DirectMonotonicWriter.getInstance(meta, data, -1, 10)
        }
        assertEquals("numValues can't be negative, got -1", e!!.message)
        e = expectThrows(IllegalArgumentException::class) {
            DirectMonotonicWriter.getInstance(meta, data, 10, 1)
        }
        assertEquals("blockShift must be in [2-22], got 1", e!!.message)
        e = expectThrows(IllegalArgumentException::class) {
            DirectMonotonicWriter.getInstance(meta, data, 1L shl 40, 5)
        }
        assertEquals(
            "blockShift is too low for the provided number of values: blockShift=5, numValues=1099511627776, MAX_ARRAY_LENGTH=" +
                ArrayUtil.MAX_ARRAY_LENGTH,
            e!!.message
        )
    }

    @Test
    fun testEmpty() {
        val dir = newDirectory()
        val blockShift = TestUtil.nextInt(random(), DirectMonotonicWriter.MIN_BLOCK_SHIFT, DirectMonotonicWriter.MAX_BLOCK_SHIFT)
        val dataLength: Long
        dir.createOutput("meta", IOContext.DEFAULT).use { metaOut ->
            dir.createOutput("data", IOContext.DEFAULT).use { dataOut ->
                DirectMonotonicWriter.getInstance(metaOut, dataOut, 0, blockShift).finish()
                dataLength = dataOut.filePointer
            }
        }

        dir.openInput("meta", IOContext.READONCE).use { metaIn ->
            dir.openInput("data", IOContext.DEFAULT).use { dataIn ->
                val meta = DirectMonotonicReader.loadMeta(metaIn, 0, blockShift)
                DirectMonotonicReader.getInstance(meta, dataIn.randomAccessSlice(0, dataLength))
            }
        }

        dir.close()
    }

    @Test
    fun testSimple() {
        val dir = newDirectory()
        val blockShift = 2
        val actualValues = listOf(1L, 2L, 5L, 7L, 8L, 100L)
        val numValues = actualValues.size
        val dataLength: Long
        dir.createOutput("meta", IOContext.DEFAULT).use { metaOut ->
            dir.createOutput("data", IOContext.DEFAULT).use { dataOut ->
                val w = DirectMonotonicWriter.getInstance(metaOut, dataOut, numValues.toLong(), blockShift)
                for (v in actualValues) w.add(v)
                w.finish()
                dataLength = dataOut.filePointer
            }
        }

        dir.openInput("meta", IOContext.READONCE).use { metaIn ->
            dir.openInput("data", IOContext.DEFAULT).use { dataIn ->
                val meta = DirectMonotonicReader.loadMeta(metaIn, numValues.toLong(), blockShift)
                val values = DirectMonotonicReader.getInstance(meta, dataIn.randomAccessSlice(0, dataLength))
                for (i in 0 until numValues) {
                    val v = values.get(i.toLong())
                    assertEquals(actualValues[i], v)
                }
            }
        }

        dir.close()
    }

    @Test
    fun testConstantSlope() {
        val dir = newDirectory()
        val blockShift = TestUtil.nextInt(random(), DirectMonotonicWriter.MIN_BLOCK_SHIFT, DirectMonotonicWriter.MAX_BLOCK_SHIFT)
        val numValues = TestUtil.nextInt(random(), 1, 1 shl 20)
        var min = random().nextLong()
        val inc = random().nextInt(1 shl random().nextInt(20))
        val maxInc = inc.toLong() * (numValues - 1).toLong()
        if (maxInc > 0 && min > Long.MAX_VALUE - maxInc) {
            min = Long.MAX_VALUE - maxInc
        }
        val actualValues = ArrayList<Long>(numValues)
        for (i in 0 until numValues) actualValues.add(min + inc.toLong() * i)
        val dataLength: Long
        dir.createOutput("meta", IOContext.DEFAULT).use { metaOut ->
            dir.createOutput("data", IOContext.DEFAULT).use { dataOut ->
                val w = DirectMonotonicWriter.getInstance(metaOut, dataOut, numValues.toLong(), blockShift)
                for (v in actualValues) w.add(v)
                w.finish()
                dataLength = dataOut.filePointer
            }
        }

        dir.openInput("meta", IOContext.READONCE).use { metaIn ->
            dir.openInput("data", IOContext.DEFAULT).use { dataIn ->
                val meta = DirectMonotonicReader.loadMeta(metaIn, numValues.toLong(), blockShift)
                val values = DirectMonotonicReader.getInstance(meta, dataIn.randomAccessSlice(0, dataLength))
                for (i in 0 until numValues) {
                    assertEquals(actualValues[i], values.get(i.toLong()))
                }
                assertEquals(0L, dataIn.filePointer)
            }
        }

        dir.close()
    }

    @Test
    fun testZeroValuesSmallBlobShift() {
        val dir = newDirectory()
        val numValues = TestUtil.nextInt(random(), 8, 1 shl 20)
        val blockShift = TestUtil.nextInt(
            random(),
            DirectMonotonicWriter.MIN_BLOCK_SHIFT,
            Math.toIntExact(Math.round(Math.log(numValues.toDouble()) / Math.log(2.0))) - 1
        )
        val dataLength: Long
        dir.createOutput("meta", IOContext.DEFAULT).use { metaOut ->
            dir.createOutput("data", IOContext.DEFAULT).use { dataOut ->
                val w = DirectMonotonicWriter.getInstance(metaOut, dataOut, numValues.toLong(), blockShift)
                for (i in 0 until numValues) w.add(0)
                w.finish()
                dataLength = dataOut.filePointer
            }
        }

        dir.openInput("meta", IOContext.READONCE).use { metaIn ->
            dir.openInput("data", IOContext.DEFAULT).use { dataIn ->
                val meta = DirectMonotonicReader.loadMeta(metaIn, numValues.toLong(), blockShift)
                assertEquals(metaIn.length(), metaIn.filePointer)
                metaIn.seek(0L)
                assertSame(meta, DirectMonotonicReader.loadMeta(metaIn, numValues.toLong(), blockShift))
                val values = DirectMonotonicReader.getInstance(meta, dataIn.randomAccessSlice(0, dataLength))
                for (i in 0 until numValues) assertEquals(0L, values.get(i.toLong()))
                assertEquals(0L, dataIn.filePointer)
            }
        }

        dir.close()
    }

    @Test
    fun testRandom() { doTestRandom(false) }

    @Test
    fun testRandomMerging() { doTestRandom(true) }

    private fun doTestRandom(merging: Boolean) {
        val rnd = /*random()*/ Random(12345)
        val iters = atLeast(rnd, 3)
        for (iter in 0 until iters) {
            val dir = newDirectory()
            val blockShift = TestUtil.nextInt(rnd, DirectMonotonicWriter.MIN_BLOCK_SHIFT, DirectMonotonicWriter.MAX_BLOCK_SHIFT)
            val maxNumValues = 1 shl 20
            val numValues: Int = if (rnd.nextBoolean()) {
                TestUtil.nextInt(rnd, 1, maxNumValues)
            } else {
                val numBlocks = TestUtil.nextInt(rnd, 0, maxNumValues ushr blockShift)
                TestUtil.nextInt(rnd, 0, numBlocks) shl blockShift
            }
            val actualValues = ArrayList<Long>()
            var previous = rnd.nextLong()
            if (numValues > 0) actualValues.add(previous)
            for (i in 1 until numValues) {
                val inc = rnd.nextInt(1 shl rnd.nextInt(20))
                if (inc > 0 && previous > Long.MAX_VALUE - inc) {
                    previous = Long.MAX_VALUE
                } else {
                    previous += inc
                }
                actualValues.add(previous)
            }
            val dataLength: Long
            dir.createOutput("meta", IOContext.DEFAULT).use { metaOut ->
                dir.createOutput("data", IOContext.DEFAULT).use { dataOut ->
                    val w = DirectMonotonicWriter.getInstance(metaOut, dataOut, numValues.toLong(), blockShift)
                    for (v in actualValues) w.add(v)
                    w.finish()
                    dataLength = dataOut.filePointer
                }
            }

            dir.openInput("meta", IOContext.READONCE).use { metaIn ->
                dir.openInput("data", IOContext.DEFAULT).use { dataIn ->
                    val meta = DirectMonotonicReader.loadMeta(metaIn, numValues.toLong(), blockShift)
                    val values = DirectMonotonicReader.getInstance(meta, dataIn.randomAccessSlice(0, dataLength), merging)
                    for (i in 0 until numValues) assertEquals(actualValues[i], values.get(i.toLong()))
                }
            }

            dir.close()
        }
    }

    @Test
    fun testMonotonicBinarySearch() {
        newDirectory().use { dir ->
            doTestMonotonicBinarySearchAgainstLongArray(dir, longArrayOf(4,7,8,10,19,30,55,78,100), 2)
        }
    }

    @Ignore("unstable")
    @Test
    fun testMonotonicBinarySearchRandom() {
        newDirectory().use { dir ->
            val iters = atLeast(100)
            for (iter in 0 until iters) {
                val arrayLength = random().nextInt(1 shl random().nextInt(14))
                val array = LongArray(arrayLength)
                val base = random().nextLong()
                val bpv = TestUtil.nextInt(random(), 4, 61)
                for (i in array.indices) {
                    array[i] = base + random().nextLong(0, 1L shl bpv)
                }
                array.sort()
                doTestMonotonicBinarySearchAgainstLongArray(dir, array, TestUtil.nextInt(random(), 2, 10))
            }
        }
    }

    private fun doTestMonotonicBinarySearchAgainstLongArray(dir: Directory, array: LongArray, blockShift: Int) {
        dir.createOutput("meta", IOContext.DEFAULT).use { metaOut ->
            dir.createOutput("data", IOContext.DEFAULT).use { dataOut ->
                val writer = DirectMonotonicWriter.getInstance(metaOut, dataOut, array.size.toLong(), blockShift)
                for (l in array) writer.add(l)
                writer.finish()
            }
        }
        dir.openInput("meta", IOContext.READONCE).use { metaIn ->
            dir.openInput("data", IOContext.DEFAULT).use { dataIn ->
                val meta = DirectMonotonicReader.loadMeta(metaIn, array.size.toLong(), blockShift)
                val reader = DirectMonotonicReader.getInstance(meta, dataIn.randomAccessSlice(0L, dir.fileLength("data")))
                if (array.isEmpty()) {
                    assertEquals(-1, reader.binarySearch(0L, array.size.toLong(), 42L))
                } else {
                    for (i in array.indices) {
                        val index = reader.binarySearch(0L, array.size.toLong(), array[i])
                        assertTrue(index >= 0)
                        assertTrue(index < array.size)
                        assertEquals(array[i], reader.get(index))
                    }
                    if (array[0] != Long.MIN_VALUE) {
                        assertEquals(-1, reader.binarySearch(0L, array.size.toLong(), array[0] - 1))
                    }
                    if (array[array.size - 1] != Long.MAX_VALUE) {
                        assertEquals(-1L - array.size.toLong(), reader.binarySearch(0L, array.size.toLong(), array[array.size - 1] + 1))
                    }
                    for (i in 0 until array.size - 2) {
                        if (array[i] + 1 < array[i + 1]) {
                            val intermediate = if (random().nextBoolean()) array[i] + 1 else array[i + 1] - 1
                            val index = reader.binarySearch(0L, array.size.toLong(), intermediate)
                            assertTrue(index < 0)
                            val insertionPoint = (-1 - index).toInt()
                            assertTrue(insertionPoint > 0)
                            assertTrue(insertionPoint < array.size)
                            assertTrue(array[insertionPoint] > intermediate)
                            assertTrue(array[insertionPoint - 1] < intermediate)
                        }
                    }
                }
            }
        }
        dir.deleteFile("meta")
        dir.deleteFile("data")
    }
}

