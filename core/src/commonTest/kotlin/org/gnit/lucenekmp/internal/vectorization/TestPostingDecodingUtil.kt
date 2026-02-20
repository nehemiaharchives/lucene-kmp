package org.gnit.lucenekmp.internal.vectorization

import org.gnit.lucenekmp.codecs.lucene101.ForUtil
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.store.MMapDirectory
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class TestPostingDecodingUtil : LuceneTestCase() {
    @Test
    fun testDuelSplitInts() {
        val iterations = atLeast(10) // TODO reduced from 100 to for dev speed

        MMapDirectory(createTempDir()).use { dir: Directory ->
            dir.createOutput("tests.bin", IOContext.DEFAULT).use { out: IndexOutput ->
                out.writeInt(random().nextInt())
                for (i in 0..<ForUtil.BLOCK_SIZE) {
                    out.writeLong(random().nextInt().toLong())
                }
            }
            val vectorizationProvider = VectorizationProvider.getInstance()
            dir.openInput("tests.bin", IOContext.DEFAULT).use { `in`: IndexInput ->
                val expectedB = IntArray(ForUtil.BLOCK_SIZE)
                val expectedC = IntArray(ForUtil.BLOCK_SIZE)
                val actualB = IntArray(ForUtil.BLOCK_SIZE)
                val actualC = IntArray(ForUtil.BLOCK_SIZE)
                for (iter in 0..<iterations) {
                    // Initialize arrays with random content.
                    for (i in expectedB.indices) {
                        expectedB[i] = random().nextInt()
                        actualB[i] = expectedB[i]
                        expectedC[i] = random().nextInt()
                        actualC[i] = expectedC[i]
                    }
                    val bShift = TestUtil.nextInt(random(), 1, 31)
                    val dec = TestUtil.nextInt(random(), 1, bShift)
                    val numIters = (bShift + dec - 1) / dec
                    val count = TestUtil.nextInt(random(), 1, 64 / numIters)
                    val bMask = random().nextInt()
                    val cIndex = random().nextInt(64)
                    val cMask = random().nextInt()
                    val startFP = random().nextInt(4).toLong()

                    // Work on a slice that has just the right number of bytes to make the test fail with an
                    // index-out-of-bounds in case the implementation reads more than the allowed number of
                    // padding bytes.
                    val slice = `in`.slice("test", 0, startFP + count.toLong() * Long.SIZE_BYTES)

                    val defaultUtil = PostingDecodingUtil(slice)
                    val optimizedUtil = vectorizationProvider.newPostingDecodingUtil(slice)

                    slice.seek(startFP)
                    defaultUtil.splitInts(count, expectedB, bShift, dec, bMask, expectedC, cIndex, cMask)
                    val expectedEndFP = slice.filePointer
                    slice.seek(startFP)
                    optimizedUtil.splitInts(count, actualB, bShift, dec, bMask, actualC, cIndex, cMask)
                    assertEquals(expectedEndFP, slice.filePointer)
                    assertContentEquals(expectedB, actualB)
                    assertContentEquals(expectedC, actualC)
                }
            }
        }
    }
}
