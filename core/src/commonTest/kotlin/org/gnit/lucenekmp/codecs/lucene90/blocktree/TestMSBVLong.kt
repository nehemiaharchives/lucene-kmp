package org.gnit.lucenekmp.codecs.lucene90.blocktree

import okio.IOException
import org.gnit.lucenekmp.store.ByteArrayDataInput
import org.gnit.lucenekmp.store.ByteArrayDataOutput
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.ArrayUtil
import kotlin.test.Test
import kotlin.test.assertEquals

class TestMSBVLong : LuceneTestCase() {

    @Test
    @Throws(IOException::class)
    fun testMSBVLong() {
        assertMSBVLong(Long.MAX_VALUE)
        val iter: Long = atLeast(10000).toLong()
        for (i in 0..<iter) {
            assertMSBVLong(i)
        }
    }

    companion object {
        @Throws(IOException::class)
        private fun assertMSBVLong(l: Long) {
            val bytes = ByteArray(10)
            val output = ByteArrayDataOutput(bytes)
            Lucene90BlockTreeTermsWriter.writeMSBVLong(l, output)
            val `in` = ByteArrayDataInput(ArrayUtil.copyOfSubArray(bytes, 0, output.position))
            val recovered: Long = FieldReader.readMSBVLong(`in`)
            assertEquals(l, recovered, "$l != $recovered")
        }
    }
}
