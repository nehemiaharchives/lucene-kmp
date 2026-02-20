package org.gnit.lucenekmp.codecs.lucene90.compressing

import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class TestStoredFieldsInt : LuceneTestCase() {
    @Test
    @Throws(Exception::class)
    fun testRandom() {
        val numIters = atLeast(100)
        newDirectory().use { dir ->
            for (iter in 0..<numIters) {
                val values = IntArray(random().nextInt(5000) + 1)
                val bpv = TestUtil.nextInt(random(), 1, 31)
                for (i in values.indices) {
                    values[i] = TestUtil.nextInt(random(), 0, (1 shl bpv) - 1)
                }
                test(dir, values)
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testAllEquals() {
        newDirectory().use { dir ->
            val docIDs = IntArray(random().nextInt(5000) + 1)
            val bpv = TestUtil.nextInt(random(), 1, 31)
            docIDs.fill(TestUtil.nextInt(random(), 0, (1 shl bpv) - 1))
            test(dir, docIDs)
        }
    }

    @Throws(Exception::class)
    private fun test(dir: Directory, ints: IntArray) {
        val len: Long
        dir.createOutput("tmp", IOContext.DEFAULT).use { out: IndexOutput ->
            StoredFieldsInts.writeInts(ints, 0, ints.size, out)
            len = out.filePointer
            if (random().nextBoolean()) {
                out.writeLong(0) // garbage
            }
        }

        dir.openInput("tmp", IOContext.READONCE).use { `in`: IndexInput ->
            val offset = random().nextInt(5)
            val read = LongArray(ints.size + offset)
            StoredFieldsInts.readInts(`in`, ints.size, read, offset)
            val readInts = IntArray(ints.size)
            for (i in ints.indices) {
                readInts[i] = read[offset + i].toInt()
            }
            assertContentEquals(ints, readInts)
            assertEquals(len, `in`.filePointer)
        }
        dir.deleteFile("tmp")
    }
}
