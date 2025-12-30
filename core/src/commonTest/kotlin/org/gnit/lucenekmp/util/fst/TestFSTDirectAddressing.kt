package org.gnit.lucenekmp.util.fst

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.CharsRef
import org.gnit.lucenekmp.util.IntsRefBuilder
import org.gnit.lucenekmp.store.ByteArrayDataInput
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.jdkport.Files
import org.gnit.lucenekmp.jdkport.StandardCharsets
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestFSTDirectAddressing : LuceneTestCase() {

    @Test
    fun testDenseWithGap() {
        val words = listOf("ah", "bi", "cj", "dk", "fl", "gm")
        val entries = words.map { BytesRef(it.encodeToByteArray()) }
        val fstEnum = BytesRefFSTEnum<Any>(buildFST(entries))
        for (entry in entries) {
            assertNotNull(fstEnum.seekExact(entry), "${entry.utf8ToString()} not found")
        }
    }

    @Test
    fun testDeDupTails() {
        val entries = mutableListOf<BytesRef>()
        var i = 0
        while (i < 1_000_000) {
            val b = ByteArray(3)
            var valInt = i
            for (j in b.indices.reversed()) {
                b[j] = (valInt and 0xff).toByte()
                valInt = valInt ushr 8
            }
            entries.add(BytesRef(b))
            i += 4
        }
        val size = buildFST(entries).numBytes()
        assertTrue(size <= 1648 * 1.01, "FST size = $size B")
    }

    @Test
    @Ignore
    @LuceneTestCase.Companion.Nightly
    fun testWorstCaseForDirectAddressing() {
        val MEMORY_INCREASE_LIMIT_PERCENT = 1.0
        val NUM_WORDS = 1_000_000

        val wordSet = mutableSetOf<BytesRef>()
        for (i in 0 until NUM_WORDS) {
            val b = ByteArray(5)
            random().nextBytes(b)
            for (j in b.indices) {
                b[j] = (b[j].toInt() and 0xfc).toByte()
            }
            wordSet.add(BytesRef(b))
        }
        val wordList = wordSet.toMutableList()
        wordList.sort()

        var fstCompiler = createFSTCompiler(-1f)
        var fst = buildFST(wordList, fstCompiler)
        val ramBytesUsedNoDirectAddressing = fst.ramBytesUsed()

        fstCompiler = createFSTCompiler(FSTCompiler.DIRECT_ADDRESSING_MAX_OVERSIZING_FACTOR)
        fst = buildFST(wordList, fstCompiler)
        val ramBytesUsed = fst.ramBytesUsed()

        val directAddressingMemoryIncreasePercent =
            ((ramBytesUsed.toDouble() / ramBytesUsedNoDirectAddressing) - 1) * 100

        assertTrue(
            directAddressingMemoryIncreasePercent < MEMORY_INCREASE_LIMIT_PERCENT,
            "FST size exceeds limit, size = $ramBytesUsed, increase = $directAddressingMemoryIncreasePercent %, limit = $MEMORY_INCREASE_LIMIT_PERCENT %"
        )
    }

    companion object {
        private fun createFSTCompiler(directAddressingMaxOversizingFactor: Float): FSTCompiler<Any> {
            return FSTCompiler.Builder(FST.INPUT_TYPE.BYTE1, NoOutputs.singleton)
                .directAddressingMaxOversizingFactor(directAddressingMaxOversizingFactor)
                .build()
        }

        private fun buildFST(entries: List<BytesRef>): FST<Any> {
            return buildFST(entries, createFSTCompiler(FSTCompiler.DIRECT_ADDRESSING_MAX_OVERSIZING_FACTOR))
        }

        private fun buildFST(entries: List<BytesRef>, fstCompiler: FSTCompiler<Any>): FST<Any> {
            var last: BytesRef? = null
            for (entry in entries) {
                if (entry != last) {
                    fstCompiler.add(Util.toIntsRef(entry, IntsRefBuilder()), NoOutputs.singleton.noOutput)
                }
                last = entry
            }
            return FST.fromFSTReader(fstCompiler.compile(), fstCompiler.getFSTReader())!!
        }
        fun main(args: Array<String>) {
            require(args.size >= 2) { "Missing argument" }
            when (args[0]) {
                "-countFSTArcs" -> countFSTArcs(args[1])
                "-measureFSTOversizing" -> measureFSTOversizing(args[1])
                "-recompileAndWalk" -> recompileAndWalk(args[1])
                else -> throw IllegalArgumentException("Invalid argument " + args[0])
            }
        }

        private fun countFSTArcs(fstFilePath: String) {
            val buf = Files.newInputStream(fstFilePath.toPath()).use { it.readAllBytes() } ?: return
            val dataIn: DataInput = ByteArrayDataInput(buf)
            val fst = FST(FST.readMetadata(dataIn, ByteSequenceOutputs.singleton), dataIn)
            val reader = fst.getBytesReader()
            val firstArc = fst.getFirstArc(FST.Arc<BytesRef>())
            val counts = IntArray(4)
            traverseArcs(fst, firstArc, reader, counts)
            println(
                "continuous arcs = ${counts[3]}, direct addressing arcs = ${counts[1]}, " +
                    "binary search arcs = ${counts[0]} list arcs = ${counts[2]}"
            )
        }

        private fun traverseArcs(
            fst: FST<BytesRef>,
            arc: FST.Arc<BytesRef>,
            reader: FST.BytesReader,
            counts: IntArray
        ) {
            var current = arc
            while (true) {
                when {
                    current.bytesPerArc == 0 -> counts[2]++
                    current.nodeFlags == FST.ARCS_FOR_DIRECT_ADDRESSING -> counts[1]++
                    current.nodeFlags == FST.ARCS_FOR_CONTINUOUS -> counts[3]++
                    else -> counts[0]++
                }
                if (FST.targetHasArcs(current)) {
                    val child = FST.Arc<BytesRef>()
                    fst.readFirstTargetArc(current, child, reader)
                    traverseArcs(fst, child, reader, counts)
                }
                if (current.isLast) break
                current = fst.readNextArc(current, reader)
            }
        }

        private fun measureFSTOversizing(wordsFilePath: String) {
            val MAX_NUM_WORDS = 1_000_000
            val wordList = mutableListOf<BytesRef>()
            Files.newBufferedReader(wordsFilePath.toPath(), StandardCharsets.UTF_8).use { r ->
                val reader = r
                while (wordList.size < MAX_NUM_WORDS) {
                    val word = reader.readLine() ?: break
                    wordList.add(BytesRef(word))
                }
            }
            wordList.sort()

            var fstCompiler = createFSTCompiler(-1f)
            var fst = buildFST(wordList, fstCompiler)
            val ramBytesUsedNoDirectAddressing = fst.ramBytesUsed()

            fstCompiler = createFSTCompiler(FSTCompiler.DIRECT_ADDRESSING_MAX_OVERSIZING_FACTOR)
            fst = buildFST(wordList, fstCompiler)
            val ramBytesUsed = fst.ramBytesUsed()

            val directAddressingMemoryIncreasePercent =
                ((ramBytesUsed.toDouble() / ramBytesUsedNoDirectAddressing) - 1) * 100.0

            printStats(fstCompiler, ramBytesUsed, directAddressingMemoryIncreasePercent)
        }

        private fun recompileAndWalk(fstFilePath: String) {
            println("recompileAndWalk is not supported in this port")
        }

        private fun newInputStream(path: okio.Path) = Files.newInputStream(path)

        private fun recompile(fst: FST<CharsRef>, oversizingFactor: Float): FST<CharsRef> {
            error("recompile is not supported in this port")
        }

        private fun walk(read: FST<CharsRef>): Int {
            println("walk is not supported in this port")
            return 0
        }

        private fun printStats(
            fstCompiler: FSTCompiler<Any>,
            ramBytesUsed: Long,
            directAddressingMemoryIncreasePercent: Double
        ) {
            println("directAddressingMaxOversizingFactor = " + fstCompiler.directAddressingMaxOversizingFactor)
            println(
                "ramBytesUsed = " + (ramBytesUsed / 1024.0 / 1024.0) +
                    " MB (" + directAddressingMemoryIncreasePercent + " % increase with direct addressing)"
            )
            println("num nodes = " + fstCompiler.nodeCount)
            val fixedLengthArcNodeCount =
                fstCompiler.directAddressingNodeCount + fstCompiler.binarySearchNodeCount + fstCompiler.continuousNodeCount
            println(
                "num fixed-length-arc nodes = " + fixedLengthArcNodeCount +
                    " (" + (fixedLengthArcNodeCount.toDouble() / fstCompiler.nodeCount * 100) + " % of all nodes)"
            )
            println(
                "num binary-search nodes = " + fstCompiler.binarySearchNodeCount +
                    " (" + (fstCompiler.binarySearchNodeCount.toDouble() / fixedLengthArcNodeCount * 100) +
                    " % of fixed-length-arc nodes)"
            )
            println(
                "num direct-addressing nodes = " + fstCompiler.directAddressingNodeCount +
                    " (" + (fstCompiler.directAddressingNodeCount.toDouble() / fixedLengthArcNodeCount * 100) +
                    " % of fixed-length-arc nodes)"
            )
            println(
                "num continuous-arcs nodes = " + fstCompiler.continuousNodeCount +
                    " (" + (fstCompiler.continuousNodeCount.toDouble() / fixedLengthArcNodeCount * 100) +
                    " % of fixed-length-arc nodes)"
            )
        }
    }
}

