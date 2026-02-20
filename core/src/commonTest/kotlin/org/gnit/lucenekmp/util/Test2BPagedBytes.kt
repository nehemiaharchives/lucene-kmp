package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.tests.store.BaseDirectoryWrapper
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class Test2BPagedBytes : LuceneTestCase() {

    @Test
    fun test() {
        val dir: BaseDirectoryWrapper = newFSDirectory(createTempDir("test2BPagedBytes"))
        if (dir is MockDirectoryWrapper) {
            dir.setThrottling(MockDirectoryWrapper.Throttling.NEVER)
        }
        val pb = PagedBytes(15)
        val dataOutput: IndexOutput = dir.createOutput("foo", IOContext.DEFAULT)
        var netBytes = 0L
        val seed = random().nextLong()
        var lastFP = 0L
        var r2 = Random(seed)
        while (netBytes < 1_100_000L) {
            // TODO reduced valueA = 1.1 * Integer.MAX_VALUE to 1100000 for dev speed
            val numBytes = TestUtil.nextInt(r2, 1, 32768)
            val bytes = ByteArray(numBytes)
            r2.nextBytes(bytes)
            dataOutput.writeBytes(bytes, bytes.size)
            val fp = dataOutput.filePointer
            assert(fp == lastFP + numBytes)
            lastFP = fp
            netBytes += numBytes
        }
        dataOutput.close()
        val input: IndexInput = dir.openInput("foo", IOContext.DEFAULT)
        pb.copy(input, input.length())
        input.close()
        val reader: PagedBytes.Reader = pb.freeze(true)

        r2 = Random(seed)
        netBytes = 0
        while (netBytes < 1_100_000L) {
            // TODO reduced valueA = 1.1 * Integer.MAX_VALUE to 1100000 for dev speed
            val numBytes = TestUtil.nextInt(r2, 1, 32768)
            val bytes = ByteArray(numBytes)
            r2.nextBytes(bytes)
            val expected = BytesRef(bytes)

            val actual = BytesRef()
            reader.fillSlice(actual, netBytes, numBytes)
            assertEquals(expected, actual)

            netBytes += numBytes
        }
        dir.close()
    }
}
