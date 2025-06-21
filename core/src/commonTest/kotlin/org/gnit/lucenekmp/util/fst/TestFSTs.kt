package org.gnit.lucenekmp.util.fst

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IntsRefBuilder
import org.gnit.lucenekmp.util.fst.FST
import org.gnit.lucenekmp.util.fst.FSTCompiler
import org.gnit.lucenekmp.util.fst.PositiveIntOutputs
import org.gnit.lucenekmp.util.fst.Util
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
