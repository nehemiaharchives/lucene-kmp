package org.gnit.lucenekmp.codecs.lucene101

import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.GroupVIntUtil
import org.gnit.lucenekmp.util.getLogger
import kotlin.time.TimeSource
import kotlin.test.Test
import kotlin.test.assertEquals

class TestPostingsUtil : LuceneTestCase() {

    private val logger = getLogger()

    // checks for bug described in https://github.com/apache/lucene/issues/13373
    @Test
    fun testIntegerOverflow() {
        // Size that writes the first value as a regular vint
        val randomSize1 = random().nextInt(1, 3)
        // Size that writes the first value as a group vint
        val randomSize2 = random().nextInt(4, ForUtil.BLOCK_SIZE)
        doTestIntegerOverflow(randomSize1)
        doTestIntegerOverflow(randomSize2)
    }

    private fun doTestIntegerOverflow(size: Int) {
        val docDeltaBuffer = IntArray(size)
        val freqBuffer = IntArray(size)

        val delta = 1 shl 30
        docDeltaBuffer[0] = delta
        newDirectory().use { dir ->
            dir.createOutput("test", IOContext.DEFAULT).use { out ->
                // In old implementation, this would cause integer overflow exception.
                PostingsUtil.writeVIntBlock(out, docDeltaBuffer, freqBuffer, size, true)
            }
            val restoredDocs = IntArray(size)
            val restoredFreqs = IntArray(size)
            dir.openInput("test", IOContext.DEFAULT).use { input ->
                PostingsUtil.readVIntBlock(input, restoredDocs, restoredFreqs, size, true, true)
            }
            assertEquals(delta, restoredDocs[0])
        }
    }

    private fun newDirectory(): Directory {
        return ByteBuffersDirectory()
    }

    @Test
    fun testPerfReadVIntBlockProbe() {
        val blockCount = 256
        val blockSize = 64
        val offsets = LongArray(blockCount)
        val docs = IntArray(blockSize)
        val freqs = IntArray(blockSize)
        val restoredDocs = IntArray(blockSize)
        val restoredFreqs = IntArray(blockSize)
        val baselineDocs = IntArray(blockSize)
        val baselineFreqs = IntArray(blockSize)

        newDirectory().use { dir ->
            dir.createOutput("perf.bin", IOContext.DEFAULT).use { out ->
                for (block in 0 until blockCount) {
                    offsets[block] = out.filePointer
                    for (i in 0 until blockSize) {
                        docs[i] = random().nextInt(1, 1 shl 20)
                        freqs[i] = random().nextInt(1, 8)
                    }
                    PostingsUtil.writeVIntBlock(out, docs, freqs, blockSize, true)
                }
            }
            dir.openInput("perf.bin", IOContext.DEFAULT).use { input ->
                val iterations = 100

                var baselineChecksum = 0L
                val baselineMark = TimeSource.Monotonic.markNow()
                repeat(iterations) {
                    for (block in 0 until blockCount) {
                        input.seek(offsets[block])
                        GroupVIntUtil.readGroupVInts(input, baselineDocs, blockSize)
                        readVIntBlockBaseline(input, baselineDocs, baselineFreqs, blockSize, true, true)
                        baselineChecksum += baselineDocs[0].toLong() + baselineFreqs[0].toLong()
                    }
                }
                val baselineMs = baselineMark.elapsedNow().inWholeMilliseconds

                var optimizedChecksum = 0L
                val optimizedMark = TimeSource.Monotonic.markNow()
                repeat(iterations) {
                    for (block in 0 until blockCount) {
                        input.seek(offsets[block])
                        PostingsUtil.readVIntBlock(input, restoredDocs, restoredFreqs, blockSize, true, true)
                        optimizedChecksum += restoredDocs[0].toLong() + restoredFreqs[0].toLong()
                    }
                }
                val optimizedMs = optimizedMark.elapsedNow().inWholeMilliseconds

                assertEquals(baselineChecksum, optimizedChecksum)
                logger.debug {
                    "perf:PostingsUtil readVIntBlock blocks=$blockCount blockSize=$blockSize iterations=$iterations baselineMs=$baselineMs optimizedMs=$optimizedMs checksum=$optimizedChecksum"
                }
            }
        }
    }

    private fun readVIntBlockBaseline(
        docIn: org.gnit.lucenekmp.store.IndexInput,
        docBuffer: IntArray,
        freqBuffer: IntArray,
        num: Int,
        indexHasFreq: Boolean,
        decodeFreq: Boolean
    ) {
        if (indexHasFreq && decodeFreq) {
            for (i in 0..<num) {
                freqBuffer[i] = docBuffer[i] and 0x01
                docBuffer[i] = docBuffer[i] ushr 1
                if (freqBuffer[i] == 0) {
                    freqBuffer[i] = docIn.readVInt()
                }
            }
        } else if (indexHasFreq) {
            for (i in 0..<num) {
                docBuffer[i] = docBuffer[i] ushr 1
            }
        }
    }
}
