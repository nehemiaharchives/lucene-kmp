package org.gnit.lucenekmp.internal.vectorization

import org.gnit.lucenekmp.util.getLogger
import org.gnit.lucenekmp.codecs.lucene101.ForUtil
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.store.MMapDirectory
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.configureTestLogging
import kotlin.time.TimeSource
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class TestPostingDecodingUtil : LuceneTestCase() {
    init {
        configureTestLogging()
    }

    private val logger = getLogger()

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

    @Test
    fun testPerfSplitIntsProbe() {
        val count = 32
        val iterations = 200 // TODO reduced from 20_000 to 200 for dev speed
        val bShift = 16
        val dec = 4
        val bMask = 0x0F
        val cIndex = 0
        val cMask = 0x00FF00FF

        MMapDirectory(createTempDir()).use { dir ->
            dir.createOutput("perf-split.bin", IOContext.DEFAULT).use { out ->
                for (i in 0 until count) {
                    out.writeInt(random().nextInt())
                }
            }

            dir.openInput("perf-split.bin", IOContext.DEFAULT).use { input ->
                val baselineB = IntArray(ForUtil.BLOCK_SIZE)
                val baselineC = IntArray(ForUtil.BLOCK_SIZE)
                val optimizedB = IntArray(ForUtil.BLOCK_SIZE)
                val optimizedC = IntArray(ForUtil.BLOCK_SIZE)

                val baselineMark = TimeSource.Monotonic.markNow()
                var baselineChecksum = 0L
                repeat(iterations) {
                    input.seek(0)
                    baselineSplitInts(input, count, baselineB, bShift, dec, bMask, baselineC, cIndex, cMask)
                    baselineChecksum += baselineB[0].toLong() + baselineC[0].toLong()
                }
                val baselineMs = baselineMark.elapsedNow().inWholeMilliseconds

                val util = PostingDecodingUtil(input)
                val optimizedMark = TimeSource.Monotonic.markNow()
                var optimizedChecksum = 0L
                repeat(iterations) {
                    input.seek(0)
                    util.splitInts(count, optimizedB, bShift, dec, bMask, optimizedC, cIndex, cMask)
                    optimizedChecksum += optimizedB[0].toLong() + optimizedC[0].toLong()
                }
                val optimizedMs = optimizedMark.elapsedNow().inWholeMilliseconds

                assertEquals(baselineChecksum, optimizedChecksum)
                logger.debug {
                    "perf:PostingDecodingUtil splitInts iterations=$iterations count=$count baselineMs=$baselineMs optimizedMs=$optimizedMs checksum=$optimizedChecksum"
                }
            }
        }
    }

    private fun baselineSplitInts(
        input: IndexInput,
        count: Int,
        b: IntArray,
        bShift: Int,
        dec: Int,
        bMask: Int,
        c: IntArray,
        cIndex: Int,
        cMask: Int
    ) {
        input.readInts(c, cIndex, count)
        val maxIter = (bShift - 1) / dec
        for (i in 0..<count) {
            for (j in 0..maxIter) {
                b[count * j + i] = (c[cIndex + i] ushr (bShift - j * dec)) and bMask
            }
            c[cIndex + i] = c[cIndex + i] and cMask
        }
    }
}
