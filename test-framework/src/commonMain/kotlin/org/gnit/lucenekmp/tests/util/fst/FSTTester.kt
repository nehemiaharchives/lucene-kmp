package org.gnit.lucenekmp.tests.util.fst

import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IntsRef
import org.gnit.lucenekmp.util.IntsRefBuilder
import org.gnit.lucenekmp.util.fst.*
import kotlin.random.Random

/** Simplified FST tester used by the ported tests. */
class FSTTester<T>(
    val random: Random,
    val dir: Directory = ByteBuffersDirectory(),
    val inputMode: Int,
    val pairs: List<InputOutput<T>>,
    val outputs: Outputs<T>
) {
    var nodeCount: Long = 0
    var arcCount: Long = 0

    data class InputOutput<T>(val input: IntsRef, val output: T) : Comparable<InputOutput<T>> {
        override fun compareTo(other: InputOutput<T>): Int = input.compareTo(other.input)
    }

    fun doTest(): FST<T>? {
        val builder = FSTCompiler.Builder(
            if (inputMode == 0) FST.INPUT_TYPE.BYTE1 else FST.INPUT_TYPE.BYTE4,
            outputs
        ).suffixRAMLimitMB(0.0)
        val compiler = builder.build()
        val sorted = pairs.sorted()
        for (io in sorted) {
            compiler.add(io.input, io.output)
        }
        val meta = compiler.compile() ?: return null
        nodeCount = compiler.nodeCount
        arcCount = compiler.arcCount
        val fst = FST.fromFSTReader(meta, compiler.getFSTReader())
        fst?.let {
            for (io in sorted) {
                val out = Util.get(it, io.input)
                if (out != null) {
                    kotlin.test.assertEquals(io.output, out)
                }
            }
        }
        return fst
    }
}

object FSTTesterUtil {
    fun getRandomString(r: Random): String {
        return if (r.nextBoolean()) {
            TestUtil.randomRealisticUnicodeString(r)
        } else {
            simpleRandomString(r)
        }
    }

    fun simpleRandomString(r: Random): String {
        val end = r.nextInt(10)
        if (end == 0) return ""
        val buffer = CharArray(end) { (r.nextInt(6) + 97).toChar() }
        return buffer.concatToString()
    }

    fun toIntsRef(s: String, inputMode: Int): IntsRef {
        val ir = IntsRefBuilder()
        return toIntsRef(s, inputMode, ir)
    }

    fun toIntsRef(s: String, inputMode: Int, ir: IntsRefBuilder): IntsRef {
        return if (inputMode == 0) {
            Util.toIntsRef(BytesRef(s), ir)
        } else {
            ir.clear()
            for (ch in s) {
                ir.append(ch.code)
            }
            ir.get()
        }
    }
}
