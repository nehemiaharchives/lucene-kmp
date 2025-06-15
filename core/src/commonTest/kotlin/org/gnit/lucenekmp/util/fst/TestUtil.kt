package org.gnit.lucenekmp.util.fst

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IntsRefBuilder
import org.gnit.lucenekmp.util.fst.NoOutputs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TestUtil : LuceneTestCase() {

    @Test
    fun testBinarySearch() {
        val letters = listOf("A", "E", "J", "K", "L", "O", "T", "z")
        val fst = buildFST(letters, allowArrayArcs = true, allowDirectAddressing = false)
        var arc = fst.getFirstArc(FST.Arc<Any>())
        arc = fst.readFirstTargetArc(arc, arc, fst.getBytesReader())
        for (i in letters.indices) {
            assertEquals(i, Util.binarySearch(fst, arc, letters[i][0].code))
        }
        assertEquals(-1, Util.binarySearch(fst, arc, ' '.code))
        assertEquals(-1 - letters.size, Util.binarySearch(fst, arc, '~'.code))
        assertEquals(-2, Util.binarySearch(fst, arc, 'B'.code))
        assertEquals(-2, Util.binarySearch(fst, arc, 'C'.code))
        assertEquals(-7, Util.binarySearch(fst, arc, 'P'.code))
    }

    @Test
    fun testContinuous() {
        val letters = listOf("A", "B", "C", "D", "E", "F", "G", "H")
        val fst = buildFST(letters, allowArrayArcs = true, allowDirectAddressing = false)
        val first = fst.getFirstArc(FST.Arc<Any>())
        var arc = FST.Arc<Any>()
        val input = fst.getBytesReader()
        for (letter in letters) {
            val c = letter[0]
            arc = Util.readCeilArc(c.code, fst, first, arc, input)!!
            assertEquals(c.code, arc.label())
        }
        assertEquals('F'.code, Util.readCeilArc('F'.code, fst, first, arc, input)!!.label())
        assertNull(Util.readCeilArc('A'.code, fst, arc, arc, input))
    }

    @Test
    fun testReadCeilArcPackedArray() {
        val letters = listOf("A", "E", "J", "K", "L", "O", "T", "z")
        verifyReadCeilArc(letters, allowArrayArcs = true, allowDirectAddressing = false)
    }

    @Test
    fun testReadCeilArcArrayWithGaps() {
        val letters = listOf("A", "E", "J", "K", "L", "O", "T")
        verifyReadCeilArc(letters, allowArrayArcs = true, allowDirectAddressing = true)
    }

    @Test
    fun testReadCeilArcList() {
        val letters = listOf("A", "E", "J", "K", "L", "O", "T", "z")
        verifyReadCeilArc(letters, allowArrayArcs = false, allowDirectAddressing = false)
    }

    private fun verifyReadCeilArc(
        letters: List<String>,
        allowArrayArcs: Boolean,
        allowDirectAddressing: Boolean
    ) {
        val fst = buildFST(letters, allowArrayArcs, allowDirectAddressing)
        val first = fst.getFirstArc(FST.Arc<Any>())
        var arc = FST.Arc<Any>()
        val input = fst.getBytesReader()
        for (letter in letters) {
            val c = letter[0]
            arc = Util.readCeilArc(c.code, fst, first, arc, input)!!
            assertEquals(c.code, arc.label())
        }
        assertEquals('A'.code, Util.readCeilArc(' '.code, fst, first, arc, input)!!.label())
        assertNull(Util.readCeilArc('~'.code, fst, first, arc, input))
        assertEquals('J'.code, Util.readCeilArc('F'.code, fst, first, arc, input)!!.label())
        assertNull(Util.readCeilArc('Z'.code, fst, arc, arc, input))
    }

    private fun buildFST(
        words: List<String>,
        allowArrayArcs: Boolean,
        allowDirectAddressing: Boolean
    ): FST<Any> {
        val outputs = NoOutputs.singleton
        val builder = FSTCompiler.Builder(FST.INPUT_TYPE.BYTE1, outputs)
            .allowFixedLengthArcs(allowArrayArcs)
        if (!allowDirectAddressing) {
            builder.directAddressingMaxOversizingFactor(-1f)
        }
        val fstCompiler = builder.build()
        for (word in words) {
            fstCompiler.add(
                Util.toIntsRef(BytesRef(word), IntsRefBuilder()),
                outputs.noOutput
            )
        }
        return FST.fromFSTReader(fstCompiler.compile(), fstCompiler.getFSTReader())!!
    }
}
