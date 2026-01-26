package org.gnit.lucenekmp.tests.store

import okio.Path
import okio.Path.Companion.toPath
import okio.EOFException
import okio.IOException
import okio.FileNotFoundException
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexNotFoundException
import org.gnit.lucenekmp.store.FSDirectory
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.jdkport.Files
import org.gnit.lucenekmp.jdkport.NoSuchFileException
import org.gnit.lucenekmp.jdkport.FileAlreadyExistsException
import org.gnit.lucenekmp.store.FilterDirectory
import org.gnit.lucenekmp.store.MMapDirectory
import org.gnit.lucenekmp.store.ReadAdvice
import org.gnit.lucenekmp.store.RandomAccessInput
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.packed.PackedInts
import org.gnit.lucenekmp.util.GroupVIntUtil
import org.gnit.lucenekmp.util.BitUtil
import org.gnit.lucenekmp.util.Constants
import org.gnit.lucenekmp.store.IOContext
import kotlin.random.Random

/**
 * Partial Kotlin port of Lucene's BaseDirectoryTestCase.
 */
abstract class BaseDirectoryTestCase : LuceneTestCase() {
    /**
     * Subclasses must create a Directory, optionally using the provided path if
     * it points to the filesystem.
     */
    @Throws(Exception::class)
    protected abstract fun getDirectory(path: Path): Directory

    /** Create a new Directory using a temporary folder. */
    @Throws(Exception::class)
    protected open fun newDirectory(): Directory {
        return getDirectory(createTempDir("dir"))
    }

    /** Create a temporary directory for tests. */
    protected fun createTempDir(prefix: String): Path {
        val base = ("/tmp".toPath() / "lucenekmp_${prefix}_${Random.nextInt()}")
        Files.createDirectories(base)
        return base
    }

    /** Simple existence check helper. */
    protected fun slowFileExists(dir: Directory, name: String): Boolean {
        return dir.listAll().contains(name)
    }

    @Throws(Exception::class)
    open fun testCopyFrom() {
        newDirectory().use { source ->
            newDirectory().use { dest ->
                runCopyFrom(source, dest)
            }
        }
    }

    @Throws(Exception::class)
    open fun testRename() {
        newDirectory().use { dir ->
            val out = dir.createOutput("foobar", IOContext.DEFAULT)
            val numBytes = Random.nextInt(20000)
            val bytes = ByteArray(numBytes).apply { Random.nextBytes(this) }
            out.writeBytes(bytes, bytes.size)
            out.close()

            dir.rename("foobar", "foobaz")

            dir.openInput("foobaz", IOContext.DEFAULT).use { input ->
                val bytes2 = ByteArray(numBytes)
                input.readBytes(bytes2, 0, bytes2.size)
                kotlin.test.assertEquals(numBytes.toLong(), input.length())
                kotlin.test.assertContentEquals(bytes, bytes2)
            }
        }
    }

    @Throws(Exception::class)
    open fun testDeleteFile() {
        newDirectory().use { dir ->
            val file = "foo.txt"
            kotlin.test.assertFalse(dir.listAll().contains(file))

            dir.createOutput(file, IOContext.DEFAULT).close()
            kotlin.test.assertTrue(dir.listAll().contains(file))

            dir.deleteFile(file)
            kotlin.test.assertFalse(dir.listAll().contains(file))
        }
    }

    @Throws(Exception::class)
    open fun testByte() {
        newDirectory().use { dir ->
            dir.createOutput("byte", IOContext.DEFAULT).use { it.writeByte(128.toByte()) }
            dir.openInput("byte", IOContext.DEFAULT).use { input ->
                kotlin.test.assertEquals(1L, input.length())
                kotlin.test.assertEquals(128.toByte(), input.readByte())
            }
        }
    }

    @Throws(Exception::class)
    open fun testShort() {
        newDirectory().use { dir ->
            dir.createOutput("short", IOContext.DEFAULT).use { it.writeShort((-20).toShort()) }
            dir.openInput("short", IOContext.DEFAULT).use { input ->
                kotlin.test.assertEquals(2L, input.length())
                kotlin.test.assertEquals((-20).toShort(), input.readShort())
            }
        }
    }

    @Throws(Exception::class)
    open fun testInt() {
        newDirectory().use { dir ->
            dir.createOutput("int", IOContext.DEFAULT).use { it.writeInt(-500) }
            dir.openInput("int", IOContext.DEFAULT).use { input ->
                kotlin.test.assertEquals(4L, input.length())
                kotlin.test.assertEquals(-500, input.readInt())
            }
        }
    }

    @Throws(Exception::class)
    open fun testLong() {
        newDirectory().use { dir ->
            dir.createOutput("long", IOContext.DEFAULT).use { it.writeLong(-5000L) }
            dir.openInput("long", IOContext.DEFAULT).use { input ->
                kotlin.test.assertEquals(8L, input.length())
                kotlin.test.assertEquals(-5000L, input.readLong())
            }
        }
    }

    @Throws(Exception::class)
    open fun testAlignedLittleEndianLongs() {
        newDirectory().use { dir ->
            dir.createOutput("littleEndianLongs", IOContext.DEFAULT).use { out ->
                out.writeLong(3L)
                out.writeLong(Long.MAX_VALUE)
                out.writeLong(-3L)
            }
            dir.openInput("littleEndianLongs", IOContext.DEFAULT).use { input ->
                kotlin.test.assertEquals(24L, input.length())
                val l = LongArray(4)
                input.readLongs(l, 1, 3)
                kotlin.test.assertContentEquals(longArrayOf(0L, 3L, Long.MAX_VALUE, -3L), l)
                kotlin.test.assertEquals(24L, input.filePointer)
            }
        }
    }

    @Throws(Exception::class)
    open fun testUnalignedLittleEndianLongs() {
        newDirectory().use { dir ->
            dir.createOutput("littleEndianLongs", IOContext.DEFAULT).use { out ->
                out.writeByte(2)
                out.writeLong(3L)
                out.writeLong(Long.MAX_VALUE)
                out.writeLong(-3L)
            }
            dir.openInput("littleEndianLongs", IOContext.DEFAULT).use { input ->
                kotlin.test.assertEquals(25L, input.length())
                kotlin.test.assertEquals(2, input.readByte().toInt())
                val l = LongArray(4)
                input.readLongs(l, 1, 3)
                kotlin.test.assertContentEquals(longArrayOf(0L, 3L, Long.MAX_VALUE, -3L), l)
                kotlin.test.assertEquals(25L, input.filePointer)
            }
        }
    }

    @Throws(Exception::class)
    open fun testLittleEndianLongsUnderflow() {
        newDirectory().use { dir ->
            val offset = Random.nextInt(8)
            val length = TestUtil.nextInt(Random, 1, 16)
            dir.createOutput("littleEndianLongs", IOContext.DEFAULT).use { out ->
                val b = ByteArray(offset + length * Long.SIZE_BYTES - TestUtil.nextInt(Random, 1, Long.SIZE_BYTES))
                Random.nextBytes(b)
                out.writeBytes(b, b.size)
            }
            dir.openInput("littleEndianLongs", IOContext.DEFAULT).use { input ->
                input.seek(offset.toLong())
                LuceneTestCase.expectThrows(EOFException::class) {
                    input.readLongs(LongArray(length), 0, length)
                }
            }
        }
    }

    @Throws(Exception::class)
    open fun testAlignedInts() {
        newDirectory().use { dir ->
            dir.createOutput("Ints", IOContext.DEFAULT).use { out ->
                out.writeInt(3)
                out.writeInt(Int.MAX_VALUE)
                out.writeInt(-3)
            }
            dir.openInput("Ints", IOContext.DEFAULT).use { input ->
                kotlin.test.assertEquals(12L, input.length())
                val i = IntArray(4)
                input.readInts(i, 1, 3)
                kotlin.test.assertContentEquals(intArrayOf(0, 3, Int.MAX_VALUE, -3), i)
                kotlin.test.assertEquals(12L, input.filePointer)
            }
        }
    }

    @Throws(Exception::class)
    open fun testUnalignedInts() {
        val padding = Random.nextInt(3) + 1
        newDirectory().use { dir ->
            dir.createOutput("Ints", IOContext.DEFAULT).use { out ->
                repeat(padding) { out.writeByte(2) }
                out.writeInt(3)
                out.writeInt(Int.MAX_VALUE)
                out.writeInt(-3)
            }
            dir.openInput("Ints", IOContext.DEFAULT).use { input ->
                kotlin.test.assertEquals((12 + padding).toLong(), input.length())
                repeat(padding) { kotlin.test.assertEquals(2, input.readByte().toInt()) }
                val i = IntArray(4)
                input.readInts(i, 1, 3)
                kotlin.test.assertContentEquals(intArrayOf(0, 3, Int.MAX_VALUE, -3), i)
                kotlin.test.assertEquals((12 + padding).toLong(), input.filePointer)
            }
        }
    }

    @Throws(Exception::class)
    open fun testIntsUnderflow() {
        newDirectory().use { dir ->
            val offset = Random.nextInt(4)
            val length = TestUtil.nextInt(Random, 1, 16)
            dir.createOutput("Ints", IOContext.DEFAULT).use { out ->
                val b = ByteArray(offset + length * Int.SIZE_BYTES - TestUtil.nextInt(Random, 1, Int.SIZE_BYTES))
                Random.nextBytes(b)
                out.writeBytes(b, b.size)
            }
            dir.openInput("Ints", IOContext.DEFAULT).use { input ->
                input.seek(offset.toLong())
                LuceneTestCase.expectThrows(EOFException::class) {
                    input.readInts(IntArray(length), 0, length)
                }
            }
        }
    }

    @Throws(Exception::class)
    open fun testAlignedFloats() {
        newDirectory().use { dir ->
            dir.createOutput("Floats", IOContext.DEFAULT).use { out ->
                out.writeInt(3f.toBits())
                out.writeInt(Float.MAX_VALUE.toBits())
                out.writeInt((-3f).toBits())
            }
            dir.openInput("Floats", IOContext.DEFAULT).use { input ->
                kotlin.test.assertEquals(12L, input.length())
                val ff = FloatArray(4)
                input.readFloats(ff, 1, 3)
                kotlin.test.assertContentEquals(floatArrayOf(0f, 3f, Float.MAX_VALUE, -3f), ff)
                kotlin.test.assertEquals(12L, input.filePointer)
            }
        }
    }

    @Throws(Exception::class)
    open fun testUnalignedFloats() {
        val padding = Random.nextInt(3) + 1
        newDirectory().use { dir ->
            dir.createOutput("Floats", IOContext.DEFAULT).use { out ->
                repeat(padding) { out.writeByte(2) }
                out.writeInt(3f.toBits())
                out.writeInt(Float.MAX_VALUE.toBits())
                out.writeInt((-3f).toBits())
            }
            dir.openInput("Floats", IOContext.DEFAULT).use { input ->
                kotlin.test.assertEquals((12 + padding).toLong(), input.length())
                repeat(padding) { kotlin.test.assertEquals(2, input.readByte().toInt()) }
                val ff = FloatArray(4)
                input.readFloats(ff, 1, 3)
                kotlin.test.assertContentEquals(floatArrayOf(0f, 3f, Float.MAX_VALUE, -3f), ff)
                kotlin.test.assertEquals((12 + padding).toLong(), input.filePointer)
            }
        }
    }

    @Throws(Exception::class)
    open fun testFloatsUnderflow() {
        newDirectory().use { dir ->
            val offset = Random.nextInt(4)
            val length = TestUtil.nextInt(Random, 1, 16)
            dir.createOutput("Floats", IOContext.DEFAULT).use { out ->
                val b = ByteArray(offset + length * Float.SIZE_BYTES - TestUtil.nextInt(Random, 1, Float.SIZE_BYTES))
                Random.nextBytes(b)
                out.writeBytes(b, b.size)
            }
            dir.openInput("Floats", IOContext.DEFAULT).use { input ->
                input.seek(offset.toLong())
                LuceneTestCase.expectThrows(EOFException::class) {
                    input.readFloats(FloatArray(length), 0, length)
                }
            }
        }
    }

    @Throws(Exception::class)
    open fun testString() {
        newDirectory().use { dir ->
            dir.createOutput("string", IOContext.DEFAULT).use { it.writeString("hello!") }
            dir.openInput("string", IOContext.DEFAULT).use { input ->
                kotlin.test.assertEquals("hello!", input.readString())
                kotlin.test.assertEquals(7L, input.length())
            }
        }
    }

    @Throws(Exception::class)
    open fun testVInt() {
        newDirectory().use { dir ->
            dir.createOutput("vint", IOContext.DEFAULT).use { it.writeVInt(500) }
            dir.openInput("vint", IOContext.DEFAULT).use { input ->
                kotlin.test.assertEquals(2L, input.length())
                kotlin.test.assertEquals(500, input.readVInt())
            }
        }
    }

    @Throws(Exception::class)
    open fun testVLong() {
        newDirectory().use { dir ->
            dir.createOutput("vlong", IOContext.DEFAULT).use { it.writeVLong(Long.MAX_VALUE) }
            dir.openInput("vlong", IOContext.DEFAULT).use { input ->
                kotlin.test.assertEquals(9L, input.length())
                kotlin.test.assertEquals(Long.MAX_VALUE, input.readVLong())
            }
        }
    }

    @Throws(Exception::class)
    open fun testZInt() {
        val ints = IntArray(Random.nextInt(10))
        for (i in ints.indices) {
            ints[i] = when (Random.nextInt(3)) {
                0 -> Random.nextInt()
                1 -> if (Random.nextBoolean()) Int.MIN_VALUE else Int.MAX_VALUE
                else -> (if (Random.nextBoolean()) -1 else 1) * Random.nextInt(1024)
            }
        }

        newDirectory().use { dir ->
            dir.createOutput("zint", IOContext.DEFAULT).use { out ->
                for (v in ints) out.writeZInt(v)
            }
            dir.openInput("zint", IOContext.DEFAULT).use { input ->
                for (v in ints) kotlin.test.assertEquals(v, input.readZInt())
                kotlin.test.assertEquals(input.length(), input.filePointer)
            }
        }
    }

    @Throws(Exception::class)
    open fun testZLong() {
        val longs = LongArray(Random.nextInt(10))
        for (i in longs.indices) {
            longs[i] = when (Random.nextInt(3)) {
                0 -> Random.nextLong()
                1 -> if (Random.nextBoolean()) Long.MIN_VALUE else Long.MAX_VALUE
                else -> (if (Random.nextBoolean()) -1 else 1) * Random.nextInt(1024).toLong()
            }
        }

        newDirectory().use { dir ->
            dir.createOutput("zlong", IOContext.DEFAULT).use { out ->
                for (v in longs) out.writeZLong(v)
            }
            dir.openInput("zlong", IOContext.DEFAULT).use { input ->
                for (v in longs) kotlin.test.assertEquals(v, input.readZLong())
                kotlin.test.assertEquals(input.length(), input.filePointer)
            }
        }
    }

    @Throws(Exception::class)
    open fun testSetOfStrings() {
        newDirectory().use { dir ->
            dir.createOutput("stringset", IOContext.DEFAULT).use { out ->
                out.writeSetOfStrings(mutableSetOf("test1", "test2"))
                out.writeSetOfStrings(mutableSetOf())
                out.writeSetOfStrings(mutableSetOf("test3"))
            }
            dir.openInput("stringset", IOContext.DEFAULT).use { input ->
                val set1 = input.readSetOfStrings()
                kotlin.test.assertEquals(setOf("test1", "test2"), set1)
                set1.add("bogus")

                val set2 = input.readSetOfStrings()
                kotlin.test.assertTrue(set2.isEmpty())
                set2.add("bogus")

                val set3 = input.readSetOfStrings()
                kotlin.test.assertEquals(setOf("test3"), set3)
                set3.add("bogus")

                kotlin.test.assertEquals(input.length(), input.filePointer)
            }
        }
    }

    @Throws(Exception::class)
    open fun testMapOfStrings() {
        val m = mutableMapOf("test1" to "value1", "test2" to "value2")

        newDirectory().use { dir ->
            dir.createOutput("stringmap", IOContext.DEFAULT).use { out ->
                out.writeMapOfStrings(m.toMutableMap())
                out.writeMapOfStrings(mutableMapOf())
                out.writeMapOfStrings(mutableMapOf("key" to "value"))
            }
            dir.openInput("stringmap", IOContext.DEFAULT).use { input ->
                val map1 = input.readMapOfStrings()
                kotlin.test.assertEquals(m, map1)
                map1["bogus1"] = "bogus2"

                val map2 = input.readMapOfStrings()
                kotlin.test.assertTrue(map2.isEmpty())
                map2["bogus1"] = "bogus2"

                val map3 = input.readMapOfStrings()
                kotlin.test.assertEquals(mapOf("key" to "value"), map3)
                map3["bogus1"] = "bogus2"

                kotlin.test.assertEquals(input.length(), input.filePointer)
            }
        }
    }

    @Throws(Exception::class)
    open fun testChecksum() {
        val expected = org.gnit.lucenekmp.jdkport.CRC32()
        val numBytes = Random.nextInt(20000)
        val bytes = ByteArray(numBytes).also { Random.nextBytes(it) }
        expected.update(bytes, 0, bytes.size)

        newDirectory().use { dir ->
            dir.createOutput("checksum", IOContext.DEFAULT).use { it.writeBytes(bytes, 0, bytes.size) }

            dir.openChecksumInput("checksum").use { input ->
                input.skipBytes(numBytes.toLong())
                kotlin.test.assertEquals(expected.getValue(), input.checksum)
            }
        }
    }

    @Throws(Throwable::class)
    open fun testDetectClose() {
        val dir = newDirectory()
        dir.close()
        LuceneTestCase.expectThrows(org.gnit.lucenekmp.store.AlreadyClosedException::class) {
            dir.createOutput("test", IOContext.DEFAULT)
        }
    }

    @Throws(Exception::class)
    open fun testThreadSafetyInListAll() {
        newDirectory().use { dir ->
            val max = TestUtil.nextInt(Random, 5, 10) // TODO reduced from 500, 1000 to 5, 10 for dev speed
            for (i in 0 until max) {
                val fileName = "file-" + i
                dir.createOutput(fileName, IOContext.DEFAULT).use { _ -> }
                kotlin.test.assertTrue(slowFileExists(dir, fileName))
                val files = dir.listAll()
                if (files.isNotEmpty()) {
                    val file = files[Random.nextInt(files.size)]
                    dir.openInput(file, IOContext.DEFAULT).use { _ -> }
                }
            }
        }
    }

    @Throws(IOException::class)
    open fun testFileExistsInListAfterCreated() {
        newDirectory().use { dir ->
            val name = "file"
            dir.createOutput(name, IOContext.DEFAULT).close()
            kotlin.test.assertTrue(slowFileExists(dir, name))
            kotlin.test.assertTrue(dir.listAll().contains(name))
        }
    }

    @Throws(Exception::class)
    open fun testSeekToEOFThenBack() {
        newDirectory().use { dir ->
            val bufferLength = 1024
            val bytes = ByteArray(3 * bufferLength)
            dir.createOutput("out", IOContext.DEFAULT).use { out ->
                out.writeBytes(bytes, 0, bytes.size)
            }
            dir.openInput("out", IOContext.DEFAULT).use { input ->
                input.seek(2L * bufferLength - 1)
                input.seek(3L * bufferLength)
                input.seek(bufferLength.toLong())
                input.readBytes(bytes, 0, 2 * bufferLength)
            }
        }
    }

    @Throws(Exception::class)
    open fun testIllegalEOF() {
        newDirectory().use { dir ->
            dir.createOutput("out", IOContext.DEFAULT).use { out ->
                val b = ByteArray(1024)
                out.writeBytes(b, 0, b.size)
            }
            dir.openInput("out", IOContext.DEFAULT).use { input ->
                input.seek(1024L)
            }
        }
    }

    @Throws(Exception::class)
    open fun testSeekPastEOF() {
        newDirectory().use { dir ->
            val len = Random.nextInt(2048)
            dir.createOutput("out", IOContext.DEFAULT).use { out ->
                val b = ByteArray(len)
                out.writeBytes(b, 0, len)
            }
            dir.openInput("out", IOContext.DEFAULT).use { input ->
                LuceneTestCase.expectThrows(EOFException::class) {
                    input.seek(len + TestUtil.nextInt(Random, 1, 2048).toLong())
                }
                input.seek(len.toLong())
                LuceneTestCase.expectThrows(EOFException::class) { input.readByte() }
                LuceneTestCase.expectThrows(EOFException::class) {
                    input.readBytes(ByteArray(1), 0, 1)
                }
            }
        }
    }

    @Throws(Exception::class)
    open fun testSliceOutOfBounds() {
        newDirectory().use { dir ->
            val len = Random.nextInt(2040) + 8
            dir.createOutput("out", IOContext.DEFAULT).use { out ->
                val b = ByteArray(len)
                out.writeBytes(b, 0, len)
            }
            dir.openInput("out", IOContext.DEFAULT).use { input ->
                LuceneTestCase.expectThrows(IllegalArgumentException::class) {
                    input.slice("slice1", 0, (len + 1).toLong())
                }
                LuceneTestCase.expectThrows(IllegalArgumentException::class) {
                    input.slice("slice2", -1, len.toLong())
                }
                val slice = input.slice("slice3", 4, (len / 2).toLong())
                LuceneTestCase.expectThrows(IllegalArgumentException::class) {
                    slice.slice("slice3sub", 1, (len / 2).toLong())
                }
                LuceneTestCase.expectThrows(IllegalArgumentException::class) {
                    input.slice("slice4", Long.MAX_VALUE - 1, 10)
                }
            }
        }
    }

    @Throws(Throwable::class)
    open fun testNoDir() {
        val tempDir = createTempDir("doesnotexist")
        IOUtils.rm(tempDir)
        getDirectory(tempDir).use { dir ->
            LuceneTestCase.expectThrowsAnyOf(
                mutableListOf(NoSuchFileException::class, IndexNotFoundException::class)
            ) {
                DirectoryReader.open(dir)
            }
        }
    }

    @Throws(Exception::class)
    open fun testCopyBytes() {
        newDirectory().use { dir ->
            var out = dir.createOutput("test", IOContext.DEFAULT)
            val bytes = ByteArray(TestUtil.nextInt(Random, 1, 77777))
            val size = TestUtil.nextInt(Random, 1, 1777777)
            var upto = 0
            var byteUpto = 0
            while (upto < size) {
                bytes[byteUpto++] = value(upto)
                upto++
                if (byteUpto == bytes.size) {
                    out.writeBytes(bytes, 0, bytes.size)
                    byteUpto = 0
                }
            }
            out.writeBytes(bytes, 0, byteUpto)
            kotlin.test.assertEquals(size.toLong(), out.filePointer)
            out.close()
            kotlin.test.assertEquals(size.toLong(), dir.fileLength("test"))

            val `in` = dir.openInput("test", IOContext.DEFAULT)
            out = dir.createOutput("test2", IOContext.DEFAULT)
            upto = 0
            while (upto < size) {
                if (Random.nextBoolean()) {
                    out.writeByte(`in`.readByte())
                    upto++
                } else {
                    val chunk = minOf(TestUtil.nextInt(Random, 1, bytes.size), size - upto)
                    out.copyBytes(`in`, chunk.toLong())
                    upto += chunk
                }
            }
            kotlin.test.assertEquals(size, upto)
            out.close()
            `in`.close()

            dir.openInput("test2", IOContext.DEFAULT).use { in2 ->
                upto = 0
                while (upto < size) {
                    if (Random.nextBoolean()) {
                        val v = in2.readByte()
                        kotlin.test.assertEquals(value(upto), v)
                        upto++
                    } else {
                        val limit = minOf(TestUtil.nextInt(Random, 1, bytes.size), size - upto)
                        in2.readBytes(bytes, 0, limit)
                        for (byteIdx in 0 until limit) {
                            kotlin.test.assertEquals(value(upto), bytes[byteIdx])
                            upto++
                        }
                    }
                }
            }

            dir.deleteFile("test")
            dir.deleteFile("test2")
        }
    }

    private fun value(idx: Int): Byte {
        return ((idx % 256) * (1 + (idx / 256))).toByte()
    }

    open fun testCopyBytesWithThreads() {
        newDirectory().use { d ->
            val headerLen = 100
            val data = ByteArray(TestUtil.nextInt(Random, headerLen + 1, 10000))
            d.createOutput("data", IOContext.DEFAULT).use { output ->
                output.writeBytes(data, 0, data.size)
            }

            val input = d.openInput("data", IOContext.DEFAULT)
            d.createOutput("header", IOContext.DEFAULT).use { outputHeader ->
                outputHeader.copyBytes(input, headerLen.toLong())
            }

            val threads = 10
            for (i in 0 until threads) {
                val src = input.clone()
                d.createOutput("copy$i", IOContext.DEFAULT).use { dst ->
                    dst.copyBytes(src, src.length() - headerLen.toLong())
                }
            }
            input.close()

            for (i in 0 until threads) {
                d.openInput("copy$i", IOContext.DEFAULT).use { copiedData ->
                    val dataCopy = ByteArray(data.size)
                    data.copyInto(dataCopy, 0, 0, headerLen)
                    copiedData.readBytes(dataCopy, headerLen, data.size - headerLen)
                    kotlin.test.assertContentEquals(data, dataCopy)
                }
            }
        }
    }

    @Throws(Exception::class)
    open fun testFsyncDoesntCreateNewFiles() {
        val path = createTempDir("nocreate")
        getDirectory(path).use { fsdir ->
            if (fsdir !is FSDirectory) {
                return
            }
            fsdir.createOutput("afile", IOContext.DEFAULT).use { out ->
                out.writeString("boo")
            }

            Files.delete(path / "afile")
            val fileCount = fsdir.listAll().size

            LuceneTestCase.expectThrowsAnyOf(
                mutableListOf(NoSuchFileException::class, FileNotFoundException::class)
            ) {
                fsdir.sync(mutableListOf("afile"))
            }

            kotlin.test.assertEquals(fileCount, fsdir.listAll().size)
        }
    }

    @Throws(Exception::class)
    open fun testRandomLong() {
        newDirectory().use { dir ->
            dir.createOutput("longs", IOContext.DEFAULT).use { output ->
                val num = TestUtil.nextInt(Random, 50, 3000)
                val longs = LongArray(num)
                for (i in longs.indices) {
                    longs[i] = Random.nextLong(Long.MIN_VALUE, Long.MAX_VALUE)
                    output.writeLong(longs[i])
                }
            }

            dir.openInput("longs", IOContext.DEFAULT).use { input ->
                val slice = input.randomAccessSlice(0, input.length())
                kotlin.test.assertEquals(input.length(), slice.length())
                val longs = LongArray((input.length() / 8).toInt())
                for (i in longs.indices) {
                    longs[i] = slice.readLong(i * 8L)
                }

                for (i in longs.indices) {
                    kotlin.test.assertEquals(longs[i], slice.readLong(i * 8L))
                }

                for (i in 1 until longs.size) {
                    val offset = i * 8L
                    val subslice = input.randomAccessSlice(offset, input.length() - offset)
                    kotlin.test.assertEquals(input.length() - offset, subslice.length())
                    for (j in i until longs.size) {
                        kotlin.test.assertEquals(longs[j], subslice.readLong((j - i) * 8L))
                    }
                }

                for (i in 0 until 7) {
                    val name = "longs-$i"
                    dir.createOutput(name, IOContext.DEFAULT).use { o ->
                        val junk = ByteArray(i)
                        Random.nextBytes(junk)
                        o.writeBytes(junk, junk.size)
                        input.seek(0)
                        o.copyBytes(input, input.length())
                    }
                    dir.openInput(name, IOContext.DEFAULT).use { padded ->
                        val whole = padded.randomAccessSlice(i.toLong(), padded.length() - i.toLong())
                        kotlin.test.assertEquals(padded.length() - i.toLong(), whole.length())
                        for (j in longs.indices) {
                            kotlin.test.assertEquals(longs[j], whole.readLong(j * 8L))
                        }
                    }
                }
            }
        }
    }

    @Throws(Exception::class)
    open fun testRandomInt() {
        newDirectory().use { dir ->
            dir.createOutput("ints", IOContext.DEFAULT).use { output ->
                val num = TestUtil.nextInt(Random, 50, 3000)
                val ints = IntArray(num)
                for (i in ints.indices) {
                    ints[i] = Random.nextInt()
                    output.writeInt(ints[i])
                }
            }

            dir.openInput("ints", IOContext.DEFAULT).use { input ->
                val slice = input.randomAccessSlice(0, input.length())
                kotlin.test.assertEquals(input.length(), slice.length())
                val ints = IntArray((input.length() / 4).toInt())
                for (i in ints.indices) {
                    ints[i] = slice.readInt(i * 4L)
                }

                for (i in 1 until ints.size) {
                    val offset = i * 4L
                    val subslice = input.randomAccessSlice(offset, input.length() - offset)
                    kotlin.test.assertEquals(input.length() - offset, subslice.length())
                    for (j in i until ints.size) {
                        kotlin.test.assertEquals(ints[j], subslice.readInt((j - i) * 4L))
                    }
                }

                for (i in 0 until 7) {
                    val name = "ints-$i"
                    dir.createOutput(name, IOContext.DEFAULT).use { o ->
                        val junk = ByteArray(i)
                        Random.nextBytes(junk)
                        o.writeBytes(junk, junk.size)
                        input.seek(0)
                        o.copyBytes(input, input.length())
                    }
                    dir.openInput(name, IOContext.DEFAULT).use { padded ->
                        val whole = padded.randomAccessSlice(i.toLong(), padded.length() - i.toLong())
                        kotlin.test.assertEquals(padded.length() - i.toLong(), whole.length())
                        for (j in ints.indices) {
                            kotlin.test.assertEquals(ints[j], whole.readInt(j * 4L))
                        }
                    }
                }
            }
        }
    }

    @Throws(Exception::class)
    open fun testRandomShort() {
        newDirectory().use { dir ->
            dir.createOutput("shorts", IOContext.DEFAULT).use { output ->
                val num = TestUtil.nextInt(Random, 50, 3000)
                val shorts = ShortArray(num)
                for (i in shorts.indices) {
                    shorts[i] = Random.nextInt().toShort()
                    output.writeShort(shorts[i])
                }
            }

            dir.openInput("shorts", IOContext.DEFAULT).use { input ->
                val slice = input.randomAccessSlice(0, input.length())
                kotlin.test.assertEquals(input.length(), slice.length())
                val shorts = ShortArray((input.length() / 2).toInt())
                for (i in shorts.indices) {
                    shorts[i] = slice.readShort(i * 2L)
                }

                for (i in 1 until shorts.size) {
                    val offset = i * 2L
                    val subslice = input.randomAccessSlice(offset, input.length() - offset)
                    kotlin.test.assertEquals(input.length() - offset, subslice.length())
                    for (j in i until shorts.size) {
                        kotlin.test.assertEquals(shorts[j], subslice.readShort((j - i) * 2L))
                    }
                }

                for (i in 0 until 7) {
                    val name = "shorts-$i"
                    dir.createOutput(name, IOContext.DEFAULT).use { o ->
                        val junk = ByteArray(i)
                        Random.nextBytes(junk)
                        o.writeBytes(junk, junk.size)
                        input.seek(0)
                        o.copyBytes(input, input.length())
                    }
                    dir.openInput(name, IOContext.DEFAULT).use { padded ->
                        val whole = padded.randomAccessSlice(i.toLong(), padded.length() - i.toLong())
                        kotlin.test.assertEquals(padded.length() - i.toLong(), whole.length())
                        for (j in shorts.indices) {
                            kotlin.test.assertEquals(shorts[j], whole.readShort(j * 2L))
                        }
                    }
                }
            }
        }
    }

    @Throws(Exception::class)
    open fun testRandomByte() {
        newDirectory().use { dir ->
            dir.createOutput("bytes", IOContext.DEFAULT).use { output ->
                val num = if (LuceneTestCase.TEST_NIGHTLY) TestUtil.nextInt(Random, 1000, 3000)
                else TestUtil.nextInt(Random, 50, 1000)
                val bytes = ByteArray(num)
                Random.nextBytes(bytes)
                for (b in bytes) {
                    output.writeByte(b)
                }
            }

            dir.openInput("bytes", IOContext.DEFAULT).use { input ->
                val slice = input.randomAccessSlice(0, input.length())
                kotlin.test.assertEquals(input.length(), slice.length())
                val bytes = ByteArray(input.length().toInt())
                for (i in bytes.indices) {
                    bytes[i] = slice.readByte(i.toLong())
                }

                for (offset in 1 until bytes.size) {
                    val subslice = input.randomAccessSlice(offset.toLong(), input.length() - offset.toLong())
                    kotlin.test.assertEquals(input.length() - offset.toLong(), subslice.length())
                    assertBytes(subslice, bytes, offset)
                }

                for (i in 1 until 7) {
                    val name = "bytes-$i"
                    dir.createOutput(name, IOContext.DEFAULT).use { o ->
                        val junk = ByteArray(i)
                        Random.nextBytes(junk)
                        o.writeBytes(junk, junk.size)
                        input.seek(0)
                        o.copyBytes(input, input.length())
                    }
                    dir.openInput(name, IOContext.DEFAULT).use { padded ->
                        val whole = padded.randomAccessSlice(i.toLong(), padded.length() - i.toLong())
                        kotlin.test.assertEquals(padded.length() - i.toLong(), whole.length())
                        assertBytes(whole, bytes, 0)
                    }
                }
            }
        }
    }

    @Throws(Exception::class)
    open fun testSliceOfSlice() {
        newDirectory().use { dir ->
            dir.createOutput("bytes", IOContext.DEFAULT).use { output ->
                val num = if (LuceneTestCase.TEST_NIGHTLY) TestUtil.nextInt(Random, 250, 2500)
                else TestUtil.nextInt(Random, 50, 250)
                val bytes = ByteArray(num)
                Random.nextBytes(bytes)
                for (b in bytes) output.writeByte(b)
            }

            val input = dir.openInput("bytes", IOContext.DEFAULT)
            // Read the entire content first to build the expected reference bytes
            val bytes = ByteArray(input.length().toInt())
            input.readBytes(bytes, 0, bytes.size)
            // Seek to a random spot; this should not impact subsequent slicing behavior
            input.seek(Random.nextLong(0, input.length()))
            for (i in bytes.indices step 16) {
                val slice1 = input.slice("slice1", i.toLong(), bytes.size - i.toLong())
                kotlin.test.assertEquals(0L, slice1.filePointer)
                kotlin.test.assertEquals(bytes.size - i.toLong(), slice1.length())
                slice1.seek(Random.nextLong(0, slice1.length()))
                for (j in 0 until slice1.length() step 16) {
                    var slice2: org.gnit.lucenekmp.store.IndexInput = slice1.slice("slice2", j.toLong(), slice1.length() - j.toLong())
                    if (Random.nextBoolean()) slice2 = slice2.clone()
                    kotlin.test.assertEquals(0L, slice2.filePointer)
                    kotlin.test.assertEquals(slice1.length() - j.toLong(), slice2.length())
                    val data = ByteArray(bytes.size)
                    bytes.copyInto(data, 0, 0, i + j.toInt())
                    if (Random.nextBoolean()) {
                        slice2.readBytes(data, i + j.toInt(), bytes.size - i - j.toInt())
                    } else {
                        val seek = Random.nextLong(0, slice2.length())
                        slice2.seek(seek)
                        slice2.readBytes(data, (i + j + seek).toInt(), (bytes.size - i - j.toInt() - seek.toInt()))
                        slice2.seek(0)
                        slice2.readBytes(data, i + j.toInt(), seek.toInt())
                    }
                    kotlin.test.assertContentEquals(bytes, data)
                }
            }

            input.close()
        }
    }

    @Throws(Exception::class)
    open fun testLargeWrites() {
        newDirectory().use { dir ->
            val os = dir.createOutput("testBufferStart.txt", IOContext.DEFAULT)
            val largeBuf = ByteArray(2048)
            Random.nextBytes(largeBuf)
            val currentPos = os.filePointer
            os.writeBytes(largeBuf, largeBuf.size)
            try {
                kotlin.test.assertEquals(currentPos + largeBuf.size, os.filePointer)
            } finally {
                os.close()
            }
        }
    }

    @Throws(Exception::class)
    open fun testIndexOutputToString() {
        newDirectory().use { dir ->
            dir.createOutput("camelCase.txt", IOContext.DEFAULT).use { out ->
                kotlin.test.assertTrue(out.toString().contains("camelCase.txt"))
            }
        }
    }

    @Throws(Exception::class)
    open fun testDoubleCloseOutput() {
        newDirectory().use { dir ->
            val out = dir.createOutput("foobar", IOContext.DEFAULT)
            out.writeString("testing")
            out.close()
            out.close()
        }
    }

    @Throws(Exception::class)
    open fun testDoubleCloseInput() {
        newDirectory().use { dir ->
            dir.createOutput("foobar", IOContext.DEFAULT).use { out -> out.writeString("testing") }
            val input = dir.openInput("foobar", IOContext.DEFAULT)
            kotlin.test.assertEquals("testing", input.readString())
            input.close()
            input.close()
        }
    }

    @Throws(Exception::class)
    open fun testCreateTempOutput() {
        newDirectory().use { dir ->
            val names = mutableListOf<String>()
            val iters = LuceneTestCase.atLeast(50)
            for (iter in 0 until iters) {
                val out = dir.createTempOutput("foo", "bar", IOContext.DEFAULT)
                names.add(out.name!!)
                out.writeVInt(iter)
                out.close()
            }
            for (iter in 0 until iters) {
                dir.openInput(names[iter], IOContext.DEFAULT).use { input ->
                    kotlin.test.assertEquals(iter, input.readVInt())
                }
            }
            val files = dir.listAll().toSet()
            kotlin.test.assertEquals(names.toSet(), files)
        }
    }

    @Throws(Exception::class)
    open fun testCreateOutputForExistingFile() {
        newDirectory().use { dir ->
            val name = "file"
            dir.createOutput(name, IOContext.DEFAULT).use { }
            LuceneTestCase.expectThrows(FileAlreadyExistsException::class) {
                dir.createOutput(name, IOContext.DEFAULT).use { }
            }
            dir.deleteFile(name)
            dir.createOutput(name, IOContext.DEFAULT).close()
        }
    }

    @Throws(Exception::class)
    open fun testSeekToEndOfFile() {
        newDirectory().use { dir ->
            dir.createOutput("a", IOContext.DEFAULT).use { out ->
                repeat(1024) { out.writeByte(0) }
            }
            dir.openInput("a", IOContext.DEFAULT).use { input ->
                input.seek(100)
                kotlin.test.assertEquals(100L, input.filePointer)
                input.seek(1024)
                kotlin.test.assertEquals(1024L, input.filePointer)
            }
        }
    }

    @Throws(Exception::class)
    open fun testSeekBeyondEndOfFile() {
        newDirectory().use { dir ->
            dir.createOutput("a", IOContext.DEFAULT).use { out ->
                repeat(1024) { out.writeByte(0) }
            }
            dir.openInput("a", IOContext.DEFAULT).use { input ->
                input.seek(100)
                kotlin.test.assertEquals(100L, input.filePointer)
                LuceneTestCase.expectThrows(EOFException::class) { input.seek(1025) }
            }
        }
    }

    @Throws(Exception::class)
    open fun testPendingDeletions() {
        val path = createTempDir("pending")
        getDirectory(path).use { dir ->
            if (dir !is FSDirectory) return
            val fsDir = dir
            val candidate = IndexFileNames.segmentFileName(
                randomSimpleString(Random, 6),
                randomSimpleString(Random),
                "test"
            )
            fsDir.createOutput(candidate, IOContext.DEFAULT).use { }
            fsDir.deleteFile(candidate)
            kotlin.test.assertTrue(fsDir.pendingDeletions.isEmpty())
        }
    }

    @Throws(Exception::class)
    open fun testListAllIsSorted() {
        newDirectory().use { dir ->
            val count = LuceneTestCase.atLeast(20)
            val names = mutableSetOf<String>()
            while (names.size < count) {
                val name = IndexFileNames.segmentFileName(
                    randomSimpleString(Random, 6),
                    randomSimpleString(Random),
                    "test"
                )
                if (Random.nextInt(5) == 1) {
                    dir.createTempOutput(name, "foo", IOContext.DEFAULT).use { out ->
                        names.add(out.name!!)
                    }
                } else if (!names.contains(name)) {
                    dir.createOutput(name, IOContext.DEFAULT).use { }
                    names.add(name)
                }
            }
            val actual = dir.listAll()
            val expected = actual.copyOf()
            expected.sort()
            kotlin.test.assertContentEquals(expected, actual)
        }
    }

    @Throws(Exception::class)
    open fun testDataTypes() {
        val values = longArrayOf(43, 12345, 123456, 1234567890)
        getDirectory(createTempDir("testDataTypes")).use { dir ->
            dir.createOutput("test", IOContext.DEFAULT).use { out ->
                out.writeByte(43.toByte())
                out.writeShort(12345.toShort())
                out.writeInt(1234567890)
                out.writeGroupVInts(values, 4)
                out.writeLong(1234567890123456789L)
            }

            val restored = LongArray(4)
            dir.openInput("test", IOContext.DEFAULT).use { input ->
                kotlin.test.assertEquals(43, input.readByte().toInt())
                kotlin.test.assertEquals(12345.toShort(), input.readShort())
                kotlin.test.assertEquals(1234567890, input.readInt())
                GroupVIntUtil.readGroupVInts(input, restored, 4)
                kotlin.test.assertContentEquals(values, restored)
                kotlin.test.assertEquals(1234567890123456789L, input.readLong())
            }
        }
    }

    @Throws(Exception::class)
    open fun testGroupVIntOverflow() {
        getDirectory(createTempDir("testGroupVIntOverflow")).use { dir ->
            val size = 32
            val values = LongArray(size)
            val restore = LongArray(size)
            values[0] = 1L shl 31
            for (i in 0 until size) {
                if (Random.nextBoolean()) {
                    values[i] = values[0]
                }
            }

            val limit = Random.nextInt(1, size)
            val out = dir.createOutput("test", IOContext.DEFAULT)
            out.writeGroupVInts(values, limit)
            out.close()
            dir.openInput("test", IOContext.DEFAULT).use { input ->
                GroupVIntUtil.readGroupVInts(input, restore, limit)
                for (i in 0 until limit) {
                    kotlin.test.assertEquals(values[i], restore[i])
                }
            }

            values[0] = 0xFFFFFFFFL + 1
            kotlin.test.assertFailsWith<ArithmeticException> { out.writeGroupVInts(values, 4) }
        }
    }

    @Throws(Exception::class)
    open fun testGroupVInt() {
        getDirectory(createTempDir("testGroupVInt")).use { dir ->
            doTestGroupVInt(dir, 5, 1, 6, 8)
            doTestGroupVInt(dir, LuceneTestCase.atLeast(100), 1, 31, 128)
        }
    }

    @Throws(Exception::class)
    open fun testPrefetch() {
        doTestPrefetch(0)
    }

    @Throws(Exception::class)
    open fun testPrefetchOnSlice() {
        doTestPrefetch(TestUtil.nextInt(Random, 1, 1024))
    }

    @Throws(Exception::class)
    open fun testUpdateReadAdvice() {
        getDirectory(createTempDir("testUpdateReadAdvice")).use { dir ->
            val totalLength = TestUtil.nextInt(Random, 16384, 65536)
            val arr = ByteArray(totalLength)
            Random.nextBytes(arr)
            dir.createOutput("temp.bin", IOContext.DEFAULT).use { out ->
                out.writeBytes(arr, arr.size)
            }

            dir.openInput("temp.bin", IOContext.DEFAULT).use { orig ->
                val input = if (Random.nextBoolean()) orig.clone() else orig
                input.updateReadAdvice(ReadAdvice.values().random())
                for (i in 0 until totalLength) {
                    val offset = TestUtil.nextInt(Random, 0, input.length().toInt() - 1)
                    input.seek(offset.toLong())
                    kotlin.test.assertEquals(arr[offset], input.readByte())
                }

                for (i in 0 until 10_000) {
                    val offset = TestUtil.nextInt(Random, 0, input.length().toInt() - 1)
                    input.seek(offset.toLong())
                    kotlin.test.assertEquals(arr[offset], input.readByte())
                    if (Random.nextBoolean()) {
                        input.updateReadAdvice(ReadAdvice.values().random())
                    }
                }
            }
        }
    }

    @Throws(Exception::class)
    open fun testIsLoaded() {
        testIsLoaded(0)
    }

    @Throws(Exception::class)
    open fun testIsLoadedOnSlice() {
        testIsLoaded(TestUtil.nextInt(Random, 1, 1024))
    }

    private fun doTestGroupVInt(
        dir: Directory,
        iterations: Int,
        minBpv: Int,
        maxBpv: Int,
        maxNumValues: Int
    ) {
        val values = LongArray(maxNumValues)
        val numValuesArray = IntArray(iterations)
        val groupVIntOut = dir.createOutput("group-varint", IOContext.DEFAULT)
        val vIntOut = dir.createOutput("vint", IOContext.DEFAULT)

        for (iter in 0 until iterations) {
            val bpv = TestUtil.nextInt(Random, minBpv, maxBpv)
            numValuesArray[iter] = TestUtil.nextInt(Random, 1, maxNumValues)
            for (j in 0 until numValuesArray[iter]) {
                val maxVal = PackedInts.maxValue(bpv)
                values[j] = if (maxVal >= Int.MAX_VALUE.toLong()) {
                    // Avoid Int overflow when bpv=31 (maxVal=Int.MAX_VALUE); use long range [0, Int.MAX_VALUE]
                    Random.nextLong(0L, Int.MAX_VALUE.toLong() + 1)
                } else {
                    Random.nextInt(0, (maxVal + 1).toInt()).toLong()
                }
                vIntOut.writeVInt(values[j].toInt())
            }
            groupVIntOut.writeGroupVInts(values, numValuesArray[iter])
        }
        groupVIntOut.close()
        vIntOut.close()

        val groupVIntIn = dir.openInput("group-varint", IOContext.DEFAULT)
        val vIntIn = dir.openInput("vint", IOContext.DEFAULT)
        for (iter in 0 until iterations) {
            GroupVIntUtil.readGroupVInts(groupVIntIn, values, numValuesArray[iter])
            for (j in 0 until numValuesArray[iter]) {
                kotlin.test.assertEquals(vIntIn.readVInt(), values[j].toInt())
            }
        }
        groupVIntIn.close()
        vIntIn.close()
        dir.deleteFile("group-varint")
        dir.deleteFile("vint")
    }

    private fun doTestPrefetch(startOffset: Int) {
        newDirectory().use { dir ->
            val totalLength = startOffset + TestUtil.nextInt(Random, 16384, 65536)
            val arr = ByteArray(totalLength)
            Random.nextBytes(arr)
            dir.createOutput("temp.bin", IOContext.DEFAULT).use { out ->
                out.writeBytes(arr, arr.size)
            }
            val temp = ByteArray(2048)

            dir.openInput("temp.bin", IOContext.DEFAULT).use { orig ->
                val input = if (startOffset == 0) orig.clone() else orig.slice("slice", startOffset.toLong(), (totalLength - startOffset).toLong())
                for (i in 0 until 10_000) {
                    val offset = TestUtil.nextInt(Random, 0, input.length().toInt() - 1)
                    if (Random.nextBoolean()) {
                        val prefetchLength = Random.nextLong(1, input.length() - offset.toLong() + 1)
                        input.prefetch(offset.toLong(), prefetchLength)
                    }
                    input.seek(offset.toLong())
                    kotlin.test.assertEquals(offset.toLong(), input.filePointer)
                    when (Random.nextInt(100)) {
                        0 -> kotlin.test.assertEquals(arr[startOffset + offset], input.readByte())
                        1 -> if (input.length() - offset.toLong() >= Long.SIZE_BYTES) {
                            kotlin.test.assertEquals(BitUtil.VH_LE_LONG.get(arr, startOffset + offset).toLong(), input.readLong())
                        }
                        else -> {
                            val readLength = TestUtil.nextInt(Random, 1, minOf(temp.size, (input.length() - offset.toLong()).toInt()))
                            input.readBytes(temp, 0, readLength)
                            kotlin.test.assertContentEquals(
                                arr.copyOfRange(startOffset + offset, startOffset + offset + readLength),
                                temp.copyOfRange(0, readLength)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun testIsLoaded(startOffset: Int) {
        newDirectory().use { dir ->
            if (FilterDirectory.unwrap(dir) is MMapDirectory) {
                (FilterDirectory.unwrap(dir) as MMapDirectory).setPreload(MMapDirectory.ALL_FILES)
            }
            val totalLength = startOffset + TestUtil.nextInt(Random, 16384, 65536)
            val arr = ByteArray(totalLength)
            Random.nextBytes(arr)
            dir.createOutput("temp.bin", IOContext.DEFAULT).use { out ->
                out.writeBytes(arr, arr.size)
            }

            dir.openInput("temp.bin", IOContext.DEFAULT).use { orig ->
                val input = if (startOffset == 0) orig.clone() else orig.slice("slice", startOffset.toLong(), (totalLength - startOffset).toLong())
                val loaded = input.isLoaded
                if (Constants.WINDOWS) {
                    // skip check on Windows for now
                } else if (FilterDirectory.unwrap(dir) is MMapDirectory && !dir::class.simpleName!!.contains("DirectIO")) {
                    kotlin.test.assertTrue(loaded.isPresent)
                    kotlin.test.assertTrue(loaded.get() == true)
                } else {
                    kotlin.test.assertFalse(loaded.isPresent)
                }
            }
        }
    }

    protected fun assertBytes(slice: RandomAccessInput, bytes: ByteArray, bytesOffset: Int) {
        val toRead = bytes.size - bytesOffset
        for (i in 0 until toRead) {
            kotlin.test.assertEquals(bytes[bytesOffset + i], slice.readByte(i.toLong()))
            val offset = Random.nextInt(1000)
            val sub1 = ByteArray(offset + i)
            slice.readBytes(0, sub1, offset, i)
            kotlin.test.assertContentEquals(bytes.copyOfRange(bytesOffset, bytesOffset + i), sub1.copyOfRange(offset, sub1.size))
            val sub2 = ByteArray(offset + toRead - i)
            slice.readBytes(i.toLong(), sub2, offset, toRead - i)
            kotlin.test.assertContentEquals(bytes.copyOfRange(bytesOffset + i, bytes.size), sub2.copyOfRange(offset, sub2.size))
        }
    }

    private fun runCopyFrom(source: Directory, dest: Directory) {
        val output = source.createOutput("foobar", IOContext.DEFAULT)
        val bytes = ByteArray(20000).apply { Random.nextBytes(this) }
        output.writeBytes(bytes, bytes.size)
        output.close()

        dest.copyFrom(source, "foobar", "foobaz", IOContext.DEFAULT)
        kotlin.test.assertTrue(slowFileExists(dest, "foobaz"))

        dest.openInput("foobaz", IOContext.DEFAULT).use { input ->
            val bytes2 = ByteArray(bytes.size)
            input.readBytes(bytes2, 0, bytes2.size)
            kotlin.test.assertContentEquals(bytes, bytes2)
        }
    }

    private fun randomSimpleString(r: Random, maxLength: Int): String {
        val length = TestUtil.nextInt(r, 0, maxLength)
        val sb = StringBuilder(length)
        repeat(length) { sb.append(('a' + r.nextInt(26)).toChar()) }
        return sb.toString()
    }

    private fun randomSimpleString(r: Random): String = randomSimpleString(r, 20)
}
