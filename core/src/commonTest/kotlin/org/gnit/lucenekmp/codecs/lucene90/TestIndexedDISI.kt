package org.gnit.lucenekmp.codecs.lucene90

import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BitSet
import org.gnit.lucenekmp.util.BitSetIterator
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.SparseFixedBitSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TestIndexedDISI : LuceneTestCase() {

    @Test
    fun testEmpty() {
        val maxDoc = TestUtil.nextInt(random(), 1, 100000)
        val set: BitSet = SparseFixedBitSet(maxDoc)
        newDirectory().use { dir ->
            doTest(set, dir)
        }
    }

    @Test
    @LuceneTestCase.Companion.Nightly
    fun testEmptyBlocks() {
        val B = 65536
        val maxDoc = B * 11
        val set: BitSet = SparseFixedBitSet(maxDoc)
        // block 0: EMPTY
        set.set(B + 5) // block 1: SPARSE
        // block 2: EMPTY
        // block 3: EMPTY
        set.set(B * 4 + 5) // block 4: SPARSE

        for (i in 0 until B) {
            set.set(B * 6 + i) // block 6: ALL
        }
        for (i in 0 until B step 3) {
            set.set(B * 7 + i) // block 7: DENSE
        }
        for (i in 0 until B) {
            if (i != 32768) {
                set.set(B * 8 + i) // block 8: DENSE (all-1)
            }
        }
        // block 9-11: EMPTY

        newDirectory().use { dir ->
            doTestAllSingleJump(set, dir)
        }

        // Change the first block to DENSE to see if jump-tables sets to position 0
        set.set(0)
        newDirectory().use { dir ->
            doTestAllSingleJump(set, dir)
        }
    }

    @Test
    fun testLastEmptyBlocks() {
        val B = 65536
        val maxDoc = B * 3
        val set: BitSet = SparseFixedBitSet(maxDoc)
        for (docID in 0 until B * 2) {
            set.set(docID)
        }

        newDirectory().use { dir ->
            doTestAllSingleJump(set, dir)
            assertAdvanceBeyondEnd(set, dir)
        }
    }

    @Test
    @LuceneTestCase.Companion.Nightly
    fun testRandomBlocks() {
        val BLOCKS = 5
        val set = createSetWithRandomBlocks(BLOCKS)
        newDirectory().use { dir ->
            doTestAllSingleJump(set, dir)
        }
    }

    @Test
    fun testPositionNotZero() {
        val BLOCKS = 10
        val denseRankPower: Byte =
            if (rarely()) (-1).toByte() else (random().nextInt(7) + 7).toByte()
        val set = createSetWithRandomBlocks(BLOCKS)
        newDirectory().use { dir ->
            val cardinality = set.cardinality()
            val jumpTableEntryCount: Int
            dir.createOutput("foo", IOContext.DEFAULT).use { out ->
                jumpTableEntryCount =
                    IndexedDISI.writeBitSet(
                        BitSetIterator(set, cardinality.toLong()),
                        out,
                        denseRankPower
                    ).toInt()
            }
            dir.openInput("foo", IOContext.DEFAULT).use { fullInput ->
                val blockData =
                    IndexedDISI.createBlockSlice(
                        fullInput,
                        "blocks",
                        0L,
                        fullInput.length(),
                        jumpTableEntryCount
                    )
                blockData.seek(random().nextInt(blockData.length().toInt()).toLong())
                val jumpTable =
                    IndexedDISI.createJumpTable(
                        fullInput,
                        0L,
                        fullInput.length(),
                        jumpTableEntryCount
                    )
                val disi =
                    IndexedDISI(
                        blockData,
                        jumpTable,
                        jumpTableEntryCount,
                        denseRankPower,
                        cardinality.toLong()
                    )
                disi.advanceExact(BLOCKS * 65536 - 1)
            }
        }
    }

    @Test
    fun testOneDoc() {
        val maxDoc = TestUtil.nextInt(random(), 1, 100000)
        val set: BitSet = SparseFixedBitSet(maxDoc)
        set.set(random().nextInt(maxDoc))
        newDirectory().use { dir ->
            doTest(set, dir)
        }
    }

    @Test
    fun testTwoDocs() {
        val maxDoc = TestUtil.nextInt(random(), 1, 100000)
        val set: BitSet = SparseFixedBitSet(maxDoc)
        set.set(random().nextInt(maxDoc))
        set.set(random().nextInt(maxDoc))
        newDirectory().use { dir ->
            doTest(set, dir)
        }
    }

    @Test
    fun testAllDocs() {
        val maxDoc = TestUtil.nextInt(random(), 1, 100000)
        val set = FixedBitSet(maxDoc)
        set.set(1, maxDoc)
        newDirectory().use { dir ->
            doTest(set, dir)
        }
    }

    @Test
    fun testHalfFull() {
        val maxDoc = TestUtil.nextInt(random(), 1, 100000)
        val set: BitSet = SparseFixedBitSet(maxDoc)
        var i = random().nextInt(2)
        while (i < maxDoc) {
            set.set(i)
            i += TestUtil.nextInt(random(), 1, 3)
        }
        newDirectory().use { dir ->
            doTest(set, dir)
        }
    }

    @Test
    fun testDocRange() {
        newDirectory().use { dir ->
            for (iter in 0 until 10) {
                val maxDoc = TestUtil.nextInt(random(), 1, 1000000)
                val set = FixedBitSet(maxDoc)
                val start = random().nextInt(maxDoc)
                val end = TestUtil.nextInt(random(), start + 1, maxDoc)
                set.set(start, end)
                doTest(set, dir)
            }
        }
    }

    private fun createSetWithRandomBlocks(blockCount: Int): BitSet {
        val B = 65536
        val set: BitSet = SparseFixedBitSet(blockCount * B)
        for (block in 0 until blockCount) {
            when (random().nextInt(4)) {
                0 -> {
                    // EMPTY
                }
                1 -> {
                    for (docID in block * B until (block + 1) * B) {
                        set.set(docID)
                    }
                }
                2 -> {
                    for (docID in block * B until (block + 1) * B step 101) {
                        set.set(docID)
                    }
                }
                3 -> {
                    for (docID in block * B until (block + 1) * B step 3) {
                        set.set(docID)
                    }
                }
                else -> {
                    throw IllegalStateException("Modulo logic error: there should only be 4 possibilities")
                }
            }
        }
        return set
    }

    @Test
    fun testSparseDenseBoundary() {
        // TODO
    }

    @Test
    fun testOneDocMissing() {
        // TODO
    }

    @Test
    fun testFewMissingDocs() {
        // TODO
    }

    @Test
    fun testDenseMultiBlock() {
        // TODO
    }

    @Test
    fun testIllegalDenseRankPower() {
        // TODO
    }

    @Test
    fun testOneDocMissingFixed() {
        // TODO
    }

    @Test
    fun testRandom() {
        // TODO
    }

    private fun rarely(): Boolean = TestUtil.rarely(random())

    private fun newDirectory(): Directory = ByteBuffersDirectory()

    private fun doTest(set: BitSet, dir: Directory) {
        val cardinality = set.cardinality()
        val denseRankPower: Byte = if (rarely()) (-1).toByte() else (random().nextInt(7) + 7).toByte()
        val length: Long
        val jumpTableEntryCount: Int
        dir.createOutput("foo", IOContext.DEFAULT).use { out ->
            jumpTableEntryCount = IndexedDISI.writeBitSet(BitSetIterator(set, cardinality.toLong()), out, denseRankPower).toInt()
            length = out.filePointer
        }

        dir.openInput("foo", IOContext.DEFAULT).use { input ->
            val disi = IndexedDISI(input, 0L, length, jumpTableEntryCount, denseRankPower, cardinality.toLong())
            val disi2 = BitSetIterator(set, cardinality.toLong())
            assertSingleStepEquality(disi, disi2)
        }

        for (step in intArrayOf(1, 10, 100, 1000, 10000, 100000)) {
            dir.openInput("foo", IOContext.DEFAULT).use { input ->
                val disi = IndexedDISI(input, 0L, length, jumpTableEntryCount, denseRankPower, cardinality.toLong())
                val disi2 = BitSetIterator(set, cardinality.toLong())
                assertAdvanceEquality(disi, disi2, step)
            }
        }

        for (step in intArrayOf(10, 100, 1000, 10000, 100000)) {
            dir.openInput("foo", IOContext.DEFAULT).use { input ->
                val disi = IndexedDISI(input, 0L, length, jumpTableEntryCount, denseRankPower, cardinality.toLong())
                val disi2 = BitSetIterator(set, cardinality.toLong())
                val disi2length = set.length()
                assertAdvanceExactRandomized(disi, disi2, disi2length, step)
            }
        }

        dir.deleteFile("foo")
    }

    private fun doTestAllSingleJump(set: BitSet, dir: Directory) {
        val cardinality = set.cardinality()
        val denseRankPower: Byte = if (rarely()) (-1).toByte() else (random().nextInt(7) + 7).toByte()
        val length: Long
        val jumpTableEntryCount: Int
        dir.createOutput("foo", IOContext.DEFAULT).use { out ->
            jumpTableEntryCount =
                IndexedDISI.writeBitSet(BitSetIterator(set, cardinality.toLong()), out, denseRankPower).toInt()
            length = out.filePointer
        }

        dir.openInput("foo", IOContext.DEFAULT).use { input ->
            for (i in 0 until set.length()) {
                val disi = IndexedDISI(input, 0L, length, jumpTableEntryCount, denseRankPower, cardinality.toLong())
                assertEquals(
                    set.get(i),
                    disi.advanceExact(i),
                    "The bit at $i should be correct with advanceExact"
                )

                val disi2 =
                    IndexedDISI(input, 0L, length, jumpTableEntryCount, denseRankPower, cardinality.toLong())
                disi2.advance(i)
                assertTrue(
                    i <= disi2.docID(),
                    "The docID should at least be $i after advance($i) but was ${disi2.docID()}"
                )
                if (set.get(i)) {
                    assertEquals(i, disi2.docID(), "The docID should be present with advance")
                } else {
                    assertNotEquals(i, disi2.docID(), "The docID should not be present with advance")
                }
            }
        }
    }

    private fun assertAdvanceBeyondEnd(set: BitSet, dir: Directory) {
        val cardinality = set.cardinality()
        val denseRankPower: Byte = 9
        val jumpTableEntryCount: Int
        dir.createOutput("bar", IOContext.DEFAULT).use { out ->
            jumpTableEntryCount =
                IndexedDISI.writeBitSet(BitSetIterator(set, cardinality.toLong()), out, denseRankPower).toInt()
        }

        dir.openInput("bar", IOContext.DEFAULT).use { input ->
            val disi2 = BitSetIterator(set, cardinality.toLong())
            var doc = disi2.docID()
            var index = 0
            while (doc < cardinality) {
                doc = disi2.nextDoc()
                index++
            }

            val disi =
                IndexedDISI(
                    input,
                    0L,
                    input.length(),
                    jumpTableEntryCount,
                    denseRankPower,
                    cardinality.toLong()
                )
            assertFalse(
                disi.advanceExact(set.length()),
                "There should be no set bit beyond the valid docID range"
            )
            disi.advance(doc)
            assertEquals(
                index,
                disi.index() + 1,
                "The index when advancing beyond the last defined docID should be correct"
            )
        }

        dir.deleteFile("bar")
    }

    private fun assertAdvanceExactRandomized(
        disi: IndexedDISI,
        disi2: BitSetIterator,
        disi2length: Int,
        step: Int
    ) {
        var index = -1
        val random = random()
        var target = 0
        while (target < disi2length) {
            target += TestUtil.nextInt(random, 0, step)
            var doc = disi2.docID()
            while (doc < target) {
                doc = disi2.nextDoc()
                index++
            }
            val exists = disi.advanceExact(target)
            assertEquals(doc == target, exists)
            if (exists) {
                assertEquals(index, disi.index())
            } else if (random.nextBoolean()) {
                assertEquals(doc, disi.nextDoc())
                // This is a bit strange when doc == NO_MORE_DOCS as the index overcounts in the disi2 while-loop
                assertEquals(index, disi.index())
                target = doc
            }
        }
    }

    private fun assertSingleStepEquality(disi: IndexedDISI, disi2: BitSetIterator) {
        var i = 0
        var doc = disi2.nextDoc()
        while (doc != DocIdSetIterator.NO_MORE_DOCS) {
            assertEquals(doc, disi.nextDoc())
            assertEquals(i++, disi.index())
            doc = disi2.nextDoc()
        }
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, disi.nextDoc())
    }

    private fun assertAdvanceEquality(disi: IndexedDISI, disi2: BitSetIterator, step: Int) {
        var index = -1
        while (true) {
            val target = disi2.docID() + step
            var doc: Int
            do {
                doc = disi2.nextDoc()
                index++
            } while (doc < target)
            assertEquals(doc, disi.advance(target))
            if (doc == DocIdSetIterator.NO_MORE_DOCS) {
                break
            }
            assertEquals(index, disi.index(), "Expected equality using step $step at docID $doc")
        }
    }
}

