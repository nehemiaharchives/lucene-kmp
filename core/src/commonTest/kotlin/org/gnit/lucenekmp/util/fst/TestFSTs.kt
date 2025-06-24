package org.gnit.lucenekmp.util.fst

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IntsRefBuilder
import org.gnit.lucenekmp.util.IntsRef
import org.gnit.lucenekmp.util.fst.*
import org.gnit.lucenekmp.tests.util.fst.FSTTester
import org.gnit.lucenekmp.tests.util.fst.FSTTesterUtil
import kotlin.random.Random
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TestFSTs : LuceneTestCase() {
    @Test
    fun testSimpleDepth() {
        val outputs = PositiveIntOutputs.singleton
        val fstCompiler = FSTCompiler.Builder(FST.INPUT_TYPE.BYTE1, outputs).build()

        val ab: BytesRef = newBytesRef("ab")
        val ac: BytesRef = newBytesRef("ac")
        val bd: BytesRef = newBytesRef("bd")

        fstCompiler.add(Util.toIntsRef(ab, IntsRefBuilder()), 3L)
        fstCompiler.add(Util.toIntsRef(ac, IntsRefBuilder()), 5L)
        fstCompiler.add(Util.toIntsRef(bd, IntsRefBuilder()), 7L)

        val fst = FST.fromFSTReader(fstCompiler.compile(), fstCompiler.getFSTReader())!!

        assertEquals(3, Util.get(fst, ab)!!.toLong())
        assertEquals(5, Util.get(fst, ac)!!.toLong())
        assertEquals(7, Util.get(fst, bd)!!.toLong())
    }

    @Test
    fun testBasicFSA() {
        val strings = arrayOf(
            "station", "commotion", "elation", "elastic", "plastic",
            "stop", "ftop", "ftation", "stat"
        )
        val strings2 = arrayOf(
            "station", "commotion", "elation", "elastic", "plastic",
            "stop", "ftop", "ftation"
        )
        for (inputMode in 0..1) {
            val terms = strings.map { FSTTesterUtil.toIntsRef(it, inputMode) }
            val terms2 = strings2.map { FSTTesterUtil.toIntsRef(it, inputMode) }

            val tester1 = FSTTester<Any>(
                Random(0),
                inputMode = inputMode,
                pairs = terms2.map { FSTTester.InputOutput(it, NoOutputs.singleton.noOutput) },
                outputs = NoOutputs.singleton
            )
            val fst1 = tester1.doTest()
            assertNotNull(fst1)

            val tester2 = FSTTester<Long>(
                Random(1),
                inputMode = inputMode,
                pairs = terms2.mapIndexed { idx, t -> FSTTester.InputOutput(t, idx.toLong()) },
                outputs = PositiveIntOutputs.singleton
            )
            val fst2 = tester2.doTest()
            assertNotNull(fst2)
        }
    }

    @Test
    fun testRandomWords() {
        val random = Random(0)
        for (iter in 0 until 2) {
            for (inputMode in 0..1) {
                val numWords = random.nextInt(20)
                val terms = mutableSetOf<IntsRef>()
                while (terms.size < numWords) {
                    terms.add(FSTTesterUtil.toIntsRef(FSTTesterUtil.getRandomString(random), inputMode))
                }
                val tester = FSTTester<Long>(
                    random,
                    inputMode = inputMode,
                    pairs = terms.sorted().mapIndexed { idx, t -> FSTTester.InputOutput(t, idx.toLong()) },
                    outputs = PositiveIntOutputs.singleton
                )
                tester.doTest()
            }
        }
    }

    @Test
    fun testSingleString() {
        val outputs = NoOutputs.singleton
        val compiler = FSTCompiler.Builder(FST.INPUT_TYPE.BYTE1, outputs).build()
        compiler.add(Util.toIntsRef(newBytesRef("foobar"), IntsRefBuilder()), outputs.noOutput)
        val fst = FST.fromFSTReader(compiler.compile(), compiler.getFSTReader())!!
        val fstEnum = BytesRefFSTEnum(fst)
        assertNull(fstEnum.seekFloor(newBytesRef("foo")))
        assertNull(fstEnum.seekCeil(newBytesRef("foobaz")))
    }

    @Test
    fun testDuplicateFSAString() {
        val outputs = NoOutputs.singleton
        val compiler = FSTCompiler.Builder(FST.INPUT_TYPE.BYTE1, outputs).build()
        val ints = IntsRefBuilder()
        compiler.add(Util.toIntsRef(newBytesRef("foobar"), ints), outputs.noOutput)
        val fst = FST.fromFSTReader(compiler.compile(), compiler.getFSTReader())!!
        val fstEnum = BytesRefFSTEnum(fst)
        var count = 0
        while (fstEnum.next() != null) count++
        assertEquals(1, count)
        assertNotNull(Util.get(fst, newBytesRef("foobar")))
        assertNull(Util.get(fst, newBytesRef("foobaz")))
    }

    @Test
    fun testSimple() {
        val outputs = PositiveIntOutputs.singleton
        val compiler = FSTCompiler.Builder(FST.INPUT_TYPE.BYTE1, outputs).build()
        val a = newBytesRef("a")
        val b = newBytesRef("b")
        val c = newBytesRef("c")
        compiler.add(Util.toIntsRef(a, IntsRefBuilder()), 17L)
        compiler.add(Util.toIntsRef(b, IntsRefBuilder()), 42L)
        compiler.add(Util.toIntsRef(c, IntsRefBuilder()), 13824324872317238L)
        val fst = FST.fromFSTReader(compiler.compile(), compiler.getFSTReader())!!
        assertEquals(13824324872317238L, Util.get(fst, c))
        assertEquals(42L, Util.get(fst, b))
        assertEquals(17L, Util.get(fst, a))
        val fstEnum = BytesRefFSTEnum(fst)
        var seekResult = fstEnum.seekFloor(a)
        assertNotNull(seekResult)
        assertEquals(17L, seekResult.output)
        seekResult = fstEnum.seekFloor(newBytesRef("aa"))
        assertNotNull(seekResult)
        assertEquals(17L, seekResult.output)
        seekResult = fstEnum.seekCeil(newBytesRef("aa"))
        assertNotNull(seekResult)
        assertEquals(b, seekResult.input)
        assertEquals(42L, seekResult.output)
    }

    @Ignore
    @Test
    fun testPrimaryKeys() {
        // TODO: requires indexing classes not yet ported
    }

    @Ignore
    @Test
    fun testRandomTermLookup() {
        // TODO: requires indexing classes not yet ported
    }

    @Test
    fun testExpandedCloseToRoot() {
        class SyntheticData {
            fun compile(lines: Array<String?>): FST<Any>? {
                val outputs = NoOutputs.singleton
                val nothing = outputs.noOutput
                val fstCompiler =
                    FSTCompiler.Builder(FST.INPUT_TYPE.BYTE1, outputs).build()
                var line = 0
                val scratchIntsRef = IntsRefBuilder()
                while (line < lines.size) {
                    val w = lines[line++] ?: break
                    fstCompiler.add(
                        Util.toIntsRef(newBytesRef(w), scratchIntsRef),
                        nothing
                    )
                }
                return FST.fromFSTReader(fstCompiler.compile(), fstCompiler.getFSTReader())
            }

            fun generate(out: MutableList<String>, b: StringBuilder, from: Char, to: Char, depth: Int) {
                if (depth == 0 || from == to) {
                    val seq = b.toString() + "_" + out.size + "_end"
                    out.add(seq)
                } else {
                    var c = from
                    while (c <= to) {
                        b.append(c)
                        generate(out, b, from, if (c == to) to else from, depth - 1)
                        b.deleteCharAt(b.length - 1)
                        c++
                    }
                }
            }

            fun verifyStateAndBelow(fst: FST<Any>, arc: FST.Arc<Any>, depth: Int): Int {
                if (FST.targetHasArcs(arc)) {
                    var childCount = 0
                    val reader = fst.getBytesReader()
                    var a = fst.readFirstTargetArc(arc, FST.Arc(), reader)
                    while (true) {
                        val expanded = fst.isExpandedTarget(a, reader)
                        val children = verifyStateAndBelow(fst, FST.Arc<Any>().copyFrom(a), depth + 1)
                        assertEquals(
                            (depth <= FSTCompiler.FIXED_LENGTH_ARC_SHALLOW_DEPTH &&
                                children >= FSTCompiler.FIXED_LENGTH_ARC_SHALLOW_NUM_ARCS) ||
                                children >= FSTCompiler.FIXED_LENGTH_ARC_DEEP_NUM_ARCS,
                            expanded
                        )
                        childCount++
                        if (a.isLast) break
                        a = fst.readNextArc(a, reader)
                    }
                    return childCount
                }
                return 0
            }
        }

        assertTrue(
            FSTCompiler.FIXED_LENGTH_ARC_SHALLOW_NUM_ARCS <
                FSTCompiler.FIXED_LENGTH_ARC_DEEP_NUM_ARCS
        )
        assertTrue(FSTCompiler.FIXED_LENGTH_ARC_SHALLOW_DEPTH >= 0)

        val s = SyntheticData()
        val out = ArrayList<String>()
        val b = StringBuilder()
        s.generate(out, b, 'a', 'i', 10)
        val input = out.toTypedArray()
        input.sort()
        val fst = s.compile(input)!!
        val arc = fst.getFirstArc(FST.Arc())
        s.verifyStateAndBelow(fst, arc, 1)
    }
}
