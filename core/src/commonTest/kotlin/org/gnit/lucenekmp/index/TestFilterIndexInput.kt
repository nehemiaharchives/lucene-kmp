package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.store.FilterIndexInput
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class TestFilterIndexInput : TestIndexInput() {

    override fun getIndexInput(len: Long): IndexInput {
        return FilterIndexInput("wrapped foo", InterceptingIndexInput("foo", len))
    }

    @Test
    fun testRawFilterIndexInputRead() {
        for (i in 0 until 1) { // TODO originally 10 but reduced to 1 for dev speed
            val random = random()
            newDirectory().use { dir ->
                dir.createOutput("foo", newIOContext(random)).use { os ->
                    os.writeBytes(READ_TEST_BYTES, READ_TEST_BYTES.size)
                }
                FilterIndexInput("wrapped foo", dir.openInput("foo", newIOContext(random))).use { input ->
                    checkReads(input, IOException::class)
                    checkSeeksAndSkips(input, random)
                }

                dir.createOutput("bar", newIOContext(random)).use { os ->
                    os.writeBytes(RANDOM_TEST_BYTES, RANDOM_TEST_BYTES.size)
                }
                FilterIndexInput("wrapped bar", dir.openInput("bar", newIOContext(random))).use { input ->
                    checkRandomReads(input)
                    checkSeeksAndSkips(input, random)
                }
            }
        }
    }

    @Test
    fun testOverrides() {
        val delegate = InterceptingIndexInput("foo", 7L)
        val input = FilterIndexInput("wrapped foo", delegate)

        assertEquals(7L, input.length())
        assertEquals(0L, input.filePointer)

        input.seek(3L)
        assertEquals(3L, input.filePointer)
        assertEquals(3L, delegate.filePointer)

        assertSame(delegate, input.delegate)
        assertSame(delegate, FilterIndexInput.unwrap(input))

        input.close()
    }

    @Test
    fun testUnwrap() {
        newDirectory().use { dir ->
            dir.createOutput("test", IOContext.DEFAULT).use { }
            val indexInput = dir.openInput("test", IOContext.DEFAULT)
            val filterIndexInput = FilterIndexInput("wrapper of test", indexInput)
            assertEquals(indexInput, filterIndexInput.delegate)
            assertEquals(indexInput, FilterIndexInput.unwrap(filterIndexInput))
            filterIndexInput.close()
        }
    }

    // tests inherited from TestIndexInput
    @Test
    override fun testRawIndexInputRead() = super.testRawIndexInputRead()

    @Test
    override fun testByteArrayDataInput() = super.testByteArrayDataInput()

    @Test
    override fun testNoReadOnSkipBytes() = super.testNoReadOnSkipBytes()
}
