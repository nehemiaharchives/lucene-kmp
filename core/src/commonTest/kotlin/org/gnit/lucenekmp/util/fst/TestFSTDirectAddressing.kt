package org.gnit.lucenekmp.util.fst

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IntsRefBuilder
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
}

