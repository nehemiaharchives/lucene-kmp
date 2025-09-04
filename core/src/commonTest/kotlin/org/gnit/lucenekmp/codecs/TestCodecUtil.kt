package org.gnit.lucenekmp.codecs

import org.gnit.lucenekmp.index.CorruptIndexException
import org.gnit.lucenekmp.store.BufferedChecksumIndexInput
import org.gnit.lucenekmp.store.ByteBuffersDataOutput
import org.gnit.lucenekmp.store.ByteBuffersIndexInput
import org.gnit.lucenekmp.store.ByteBuffersIndexOutput
import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.StringHelper
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** tests for codecutil methods */
class TestCodecUtil : LuceneTestCase() {
    @Test
    fun testHeaderLength() {
        val out = ByteBuffersDataOutput()
        val output = ByteBuffersIndexOutput(out, "temp", "temp")
        CodecUtil.writeHeader(output, "FooBar", 5)
        output.writeString("this is the data")
        output.close()

        val input = ByteBuffersIndexInput(out.toDataInput(), "temp")
        input.seek(CodecUtil.headerLength("FooBar").toLong())
        assertEquals("this is the data", input.readString())
        input.close()
    }

    @Test
    fun testWriteTooLongHeader() {
        val tooLong = StringBuilder()
        for (i in 0 until 128) {
            tooLong.append('a')
        }
        val out = ByteBuffersDataOutput()
        val output = ByteBuffersIndexOutput(out, "temp", "temp")
        expectThrows(IllegalArgumentException::class) {
            CodecUtil.writeHeader(output, tooLong.toString(), 5)
        }
    }

    @Test
    fun testWriteNonAsciiHeader() {
        val out = ByteBuffersDataOutput()
        val output = ByteBuffersIndexOutput(out, "temp", "temp")
        expectThrows(IllegalArgumentException::class) {
            CodecUtil.writeHeader(output, "\u1234", 5)
        }
    }

    @Test
    fun testReadHeaderWrongMagic() {
        val out = ByteBuffersDataOutput()
        val output = ByteBuffersIndexOutput(out, "temp", "temp")
        output.writeInt(1234)
        output.close()

        val input = ByteBuffersIndexInput(out.toDataInput(), "temp")
        expectThrows(CorruptIndexException::class) {
            CodecUtil.checkHeader(input, "bogus", 1, 1)
        }
    }

    @Test
    fun testChecksumEntireFile() {
        val out = ByteBuffersDataOutput()
        val output = ByteBuffersIndexOutput(out, "temp", "temp")
        CodecUtil.writeHeader(output, "FooBar", 5)
        output.writeString("this is the data")
        CodecUtil.writeFooter(output)
        output.close()

        val input = ByteBuffersIndexInput(out.toDataInput(), "temp")
        CodecUtil.checksumEntireFile(input)
        input.close()
    }

    @Test
    fun testCheckFooterValid() {
        val out = ByteBuffersDataOutput()
        val output = ByteBuffersIndexOutput(out, "temp", "temp")
        CodecUtil.writeHeader(output, "FooBar", 5)
        output.writeString("this is the data")
        CodecUtil.writeFooter(output)
        output.close()

        val input = BufferedChecksumIndexInput(ByteBuffersIndexInput(out.toDataInput(), "temp"))
        val mine = RuntimeException("fake exception")
        val expected = expectThrows(RuntimeException::class) {
            CodecUtil.checkFooter(input, mine)
        }
        assertEquals("fake exception", expected!!.message)
        val suppressed = expected.suppressedExceptions
        assertEquals(1, suppressed.size)
        assertTrue(suppressed[0].message!!.contains("checksum passed"))
        input.close()
    }

    @Test
    fun testCheckFooterValidAtFooter() {
        val out = ByteBuffersDataOutput()
        val output = ByteBuffersIndexOutput(out, "temp", "temp")
        CodecUtil.writeHeader(output, "FooBar", 5)
        output.writeString("this is the data")
        CodecUtil.writeFooter(output)
        output.close()

        val input = BufferedChecksumIndexInput(ByteBuffersIndexInput(out.toDataInput(), "temp"))
        CodecUtil.checkHeader(input, "FooBar", 5, 5)
        assertEquals("this is the data", input.readString())
        val mine = RuntimeException("fake exception")
        val expected = expectThrows(RuntimeException::class) {
            CodecUtil.checkFooter(input, mine)
        }
        assertEquals("fake exception", expected!!.message)
        val suppressed = expected.suppressedExceptions
        assertEquals(1, suppressed.size)
        assertTrue(suppressed[0].message!!.contains("checksum passed"))
        input.close()
    }

    @Test
    fun testCheckFooterValidPastFooter() {
        val out = ByteBuffersDataOutput()
        val output = ByteBuffersIndexOutput(out, "temp", "temp")
        CodecUtil.writeHeader(output, "FooBar", 5)
        output.writeString("this is the data")
        CodecUtil.writeFooter(output)
        output.close()

        val input = BufferedChecksumIndexInput(ByteBuffersIndexInput(out.toDataInput(), "temp"))
        CodecUtil.checkHeader(input, "FooBar", 5, 5)
        assertEquals("this is the data", input.readString())
        input.readByte()
        val mine = RuntimeException("fake exception")
        val expected = expectThrows(CorruptIndexException::class) {
            CodecUtil.checkFooter(input, mine)
        }
        assertTrue(expected!!.message!!.contains("checksum status indeterminate"))
        val suppressed = expected.suppressedExceptions
        assertEquals(1, suppressed.size)
        assertEquals("fake exception", suppressed[0].message)
        input.close()
    }

    @Test
    fun testCheckFooterInvalid() {
        val out = ByteBuffersDataOutput()
        val output = ByteBuffersIndexOutput(out, "temp", "temp")
        CodecUtil.writeHeader(output, "FooBar", 5)
        output.writeString("this is the data")
        CodecUtil.writeBEInt(output, CodecUtil.FOOTER_MAGIC)
        CodecUtil.writeBEInt(output, 0)
        CodecUtil.writeBELong(output, 1234567)
        output.close()

        val input = BufferedChecksumIndexInput(ByteBuffersIndexInput(out.toDataInput(), "temp"))
        CodecUtil.checkHeader(input, "FooBar", 5, 5)
        assertEquals("this is the data", input.readString())
        val mine = RuntimeException("fake exception")
        val expected = expectThrows(CorruptIndexException::class) {
            CodecUtil.checkFooter(input, mine)
        }
        assertTrue(expected!!.message!!.contains("checksum failed"))
        val suppressed = expected.suppressedExceptions
        assertEquals(1, suppressed.size)
        assertEquals("fake exception", suppressed[0].message)
        input.close()
    }

    @Test
    fun testSegmentHeaderLength() {
        val out = ByteBuffersDataOutput()
        val output = ByteBuffersIndexOutput(out, "temp", "temp")
        CodecUtil.writeIndexHeader(output, "FooBar", 5, StringHelper.randomId(), "xyz")
        output.writeString("this is the data")
        output.close()

        val input = ByteBuffersIndexInput(out.toDataInput(), "temp")
        input.seek(CodecUtil.indexHeaderLength("FooBar", "xyz").toLong())
        assertEquals("this is the data", input.readString())
        input.close()
    }

    @Test
    fun testWriteTooLongSuffix() {
        val tooLong = StringBuilder()
        for (i in 0 until 256) {
            tooLong.append('a')
        }
        val out = ByteBuffersDataOutput()
        val output = ByteBuffersIndexOutput(out, "temp", "temp")
        expectThrows(IllegalArgumentException::class) {
            CodecUtil.writeIndexHeader(output, "foobar", 5, StringHelper.randomId(), tooLong.toString())
        }
    }

    @Test
    fun testWriteVeryLongSuffix() {
        val justLongEnough = StringBuilder()
        for (i in 0 until 255) {
            justLongEnough.append('a')
        }
        val out = ByteBuffersDataOutput()
        val output = ByteBuffersIndexOutput(out, "temp", "temp")
        val id = StringHelper.randomId()
        CodecUtil.writeIndexHeader(output, "foobar", 5, id, justLongEnough.toString())
        output.close()

        val input = ByteBuffersIndexInput(out.toDataInput(), "temp")
        CodecUtil.checkIndexHeader(input, "foobar", 5, 5, id, justLongEnough.toString())
        assertEquals(input.filePointer, input.length())
        assertEquals(input.filePointer, CodecUtil.indexHeaderLength("foobar", justLongEnough.toString()).toLong())
        input.close()
    }

    @Test
    fun testWriteNonAsciiSuffix() {
        val out = ByteBuffersDataOutput()
        val output = ByteBuffersIndexOutput(out, "temp", "temp")
        expectThrows(IllegalArgumentException::class) {
            CodecUtil.writeIndexHeader(output, "foobar", 5, StringHelper.randomId(), "\u1234")
        }
    }

    @Test
    fun testReadBogusCRC() {
        val out = ByteBuffersDataOutput()
        val output = ByteBuffersIndexOutput(out, "temp", "temp")
        CodecUtil.writeBELong(output, -1L)
        CodecUtil.writeBELong(output, 1L shl 32)
        CodecUtil.writeBELong(output, -(1L shl 32))
        CodecUtil.writeBELong(output, (1L shl 32) - 1)
        output.close()
        val input: IndexInput = BufferedChecksumIndexInput(ByteBuffersIndexInput(out.toDataInput(), "temp"))
        for (i in 0 until 3) {
            expectThrows(CorruptIndexException::class) {
                CodecUtil.readCRC(input)
            }
        }
        CodecUtil.readCRC(input)
    }

    @OptIn(ExperimentalAtomicApi::class)
    @Test
    fun testWriteBogusCRC() {
        val out = ByteBuffersDataOutput()
        val output = ByteBuffersIndexOutput(out, "temp", "temp")
        val fakeChecksum = AtomicLong(0)
        val fakeOutput = object : IndexOutput("fake", "fake") {
            override fun close() {
                output.close()
            }

            override val filePointer: Long
                get() = output.filePointer

            override fun getChecksum(): Long {
                return fakeChecksum.load()
            }

            override fun writeByte(b: Byte) {
                output.writeByte(b)
            }

            override fun writeBytes(b: ByteArray, offset: Int, length: Int) {
                output.writeBytes(b, offset, length)
            }
        }

        fakeChecksum.store(-1L)
        expectThrows(IllegalStateException::class) {
            CodecUtil.writeCRC(fakeOutput)
        }

        fakeChecksum.store(1L shl 32)
        expectThrows(IllegalStateException::class) {
            CodecUtil.writeCRC(fakeOutput)
        }

        fakeChecksum.store(-(1L shl 32))
        expectThrows(IllegalStateException::class) {
            CodecUtil.writeCRC(fakeOutput)
        }

        fakeChecksum.store((1L shl 32) - 1)
        CodecUtil.writeCRC(fakeOutput)
    }

    @Test
    fun testTruncatedFileThrowsCorruptIndexException() {
        val out = ByteBuffersDataOutput()
        val output = ByteBuffersIndexOutput(out, "temp", "temp")
        output.close()

        val input = ByteBuffersIndexInput(out.toDataInput(), "temp")

        var e = expectThrows(CorruptIndexException::class) {
            CodecUtil.checksumEntireFile(input)
        }
        assertTrue(
            e!!.message!!.contains(
                "misplaced codec footer (file truncated?): length=0 but footerLength==16"
            )
        )

        e = expectThrows(CorruptIndexException::class) {
            CodecUtil.retrieveChecksum(input)
        }
        assertTrue(
            e!!.message!!.contains(
                "misplaced codec footer (file truncated?): length=0 but footerLength==16"
            )
        )
    }

    @Test
    fun testRetrieveChecksum() {
        val dir: Directory = ByteBuffersDirectory()
        dir.createOutput("foo", IOContext.DEFAULT).use { out ->
            out.writeByte(42)
            CodecUtil.writeFooter(out)
        }
        dir.openInput("foo", IOContext.DEFAULT).use { `in` ->
            CodecUtil.retrieveChecksum(`in`, `in`.length())

            var exception = expectThrows(CorruptIndexException::class) {
                CodecUtil.retrieveChecksum(`in`, `in`.length() - 1)
            }
            assertTrue(exception!!.message!!.contains("too long"))
            assertEquals(0, exception.suppressedExceptions.size)

            exception = expectThrows(CorruptIndexException::class) {
                CodecUtil.retrieveChecksum(`in`, `in`.length() + 1)
            }
            assertTrue(exception!!.message!!.contains("truncated"))
            assertEquals(0, exception.suppressedExceptions.size)
        }

        dir.createOutput("bar", IOContext.DEFAULT).use { out ->
            for (i in 0..CodecUtil.footerLength()) {
                out.writeByte(i.toByte())
            }
        }
        dir.openInput("bar", IOContext.DEFAULT).use { `in` ->
            var exception = expectThrows(CorruptIndexException::class) {
                CodecUtil.retrieveChecksum(`in`, `in`.length())
            }
            assertTrue(exception!!.message!!.contains("codec footer mismatch"))
            assertEquals(0, exception.suppressedExceptions.size)

            exception = expectThrows(CorruptIndexException::class) {
                CodecUtil.retrieveChecksum(`in`, `in`.length() - 1)
            }
            assertTrue(exception!!.message!!.contains("too long"))

            exception = expectThrows(CorruptIndexException::class) {
                CodecUtil.retrieveChecksum(`in`, `in`.length() + 1)
            }
            assertTrue(exception!!.message!!.contains("truncated"))
        }

        dir.close()
    }
}

