package org.gnit.lucenekmp.util.bkd

import okio.fakefilesystem.FakeFileSystem
import okio.Path.Companion.toPath
import org.gnit.lucenekmp.jdkport.Files
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.NIOFSDirectory
import org.gnit.lucenekmp.store.FSLockFactory
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.CollectionUtil
import org.gnit.lucenekmp.util.Constants
import org.gnit.lucenekmp.index.PointValues.IntersectVisitor
import org.gnit.lucenekmp.index.PointValues.Relation
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestDocIdsWriter : LuceneTestCase() {
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
        return NIOFSDirectory(path, FSLockFactory.default)
    }

    @Test
    fun testRandom() {
        val numIters = atLeast(10) // TODO originally 100, but reduced to 10 for dev speed
        getDirectory().use { dir ->
            repeat(numIters) {
                val docIDs = IntArray(1 + random().nextInt(50)) // TODO originally 5000, but reduced to 50 for dev speed
                val bpv = TestUtil.nextInt(random(), 1, 32)
                val maxValue: Int =
                    if (bpv == 32) Int.MAX_VALUE else (1 shl bpv) - 1
                for (i in docIDs.indices) {
                    if (maxValue == Int.MAX_VALUE) {
                        // TestUtil.nextInt cannot handle end == Int.MAX_VALUE
                        docIDs[i] = random().nextLong(0L, (Int.MAX_VALUE.toLong() + 1)).toInt()
                    } else {
                        docIDs[i] = TestUtil.nextInt(random(), 0, maxValue)
                    }
                }
                test(dir, docIDs)
            }
        }
    }

    @Test
    fun testSorted() {
        val numIters = atLeast(10) // TODO originally 100 but reduced to 10 for dev speed
        getDirectory().use { dir ->
            repeat(numIters) {
                val docIDs = IntArray(1 + random().nextInt(50)) // TODO originally 5000, but reduced to 50 for dev speed
                val bpv = TestUtil.nextInt(random(), 1, 32)
                val maxValue: Int =
                    if (bpv == 32) Int.MAX_VALUE else (1 shl bpv) - 1
                for (i in docIDs.indices) {
                    if (maxValue == Int.MAX_VALUE) {
                        docIDs[i] = random().nextLong(0L, (Int.MAX_VALUE.toLong() + 1)).toInt()
                    } else {
                        docIDs[i] = TestUtil.nextInt(random(), 0, maxValue)
                    }
                }
                docIDs.sort()
                test(dir, docIDs)
            }
        }
    }

    @Test
    fun testCluster() {
        val numIters = atLeast(10) // TODO originally 100 but reduced to 10 for dev speed
        getDirectory().use { dir ->
            repeat(numIters) {
                val docIDs = IntArray(1 + random().nextInt(50)) // TODO originally 5000, but reduced to 50 for dev speed
                val min = random().nextInt(1000)
                val bpv = TestUtil.nextInt(random(), 1, 16)
                for (i in docIDs.indices) {
                    docIDs[i] = min + TestUtil.nextInt(random(), 0, (1 shl bpv) - 1)
                }
                test(dir, docIDs)
            }
        }
    }

    @Test
    fun testBitSet() {
        val numIters = atLeast(10) // TODO originally 100 but reduced to 10 for dev speed
        getDirectory().use { dir ->
            repeat(numIters) {
                val size = 1 + random().nextInt(50) // TODO originally 5000, but reduced to 50 for dev speed
                val set = CollectionUtil.newHashSet<Int>(size)
                val small = random().nextInt(1000)
                while (set.size < size) {
                    set.add(small + random().nextInt(size * 16))
                }
                val docIDs = set.toIntArray().sortedArray()
                test(dir, docIDs)
            }
        }
    }

    @Test
    fun testContinuousIds() {
        val numIters = atLeast(10) // TODO originally 100 but reduced to 10 for dev speed
        getDirectory().use { dir ->
            repeat(numIters) {
                val size = 1 + random().nextInt(50) // TODO originally 5000, but reduced to 50 for dev speed
                val docIDs = IntArray(size)
                val start = random().nextInt(1_000_000)
                for (i in docIDs.indices) {
                    docIDs[i] = start + i
                }
                test(dir, docIDs)
            }
        }
    }

    private fun test(dir: Directory, ints: IntArray) {
        val docIdsWriter = DocIdsWriter(ints.size)
        val len: Long
        dir.createOutput("tmp", IOContext.DEFAULT).use { out ->
            docIdsWriter.writeDocIds(ints, 0, ints.size, out)
            len = out.filePointer
            if (random().nextBoolean()) {
                out.writeLong(0)
            }
        }
        dir.openInput("tmp", IOContext.READONCE).use { input ->
            val read = IntArray(ints.size)
            docIdsWriter.readInts(input, ints.size, read)
            assertTrue(ints.contentEquals(read))
            assertEquals(len, input.filePointer)
        }
        dir.openInput("tmp", IOContext.READONCE).use { input ->
            val read = IntArray(ints.size)
            docIdsWriter.readInts(
                input,
                ints.size,
                object : IntersectVisitor {
                    var i = 0
                    override fun visit(docID: Int) {
                        read[i++] = docID
                    }
                    override fun visit(docID: Int, packedValue: ByteArray) {
                        throw UnsupportedOperationException()
                    }
                    override fun compare(minPackedValue: ByteArray, maxPackedValue: ByteArray): Relation {
                        throw UnsupportedOperationException()
                    }
                }
            )
            assertTrue(ints.contentEquals(read))
            assertEquals(len, input.filePointer)
        }
        dir.deleteFile("tmp")
    }

    @Ignore
    @Test
    fun testCrash() {
        // TODO: requires full IndexWriter implementation
    }
}
