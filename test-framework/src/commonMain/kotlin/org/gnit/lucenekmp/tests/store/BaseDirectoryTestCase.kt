package org.gnit.lucenekmp.tests.store

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.EOFException
import okio.IOException
import okio.FileNotFoundException
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexNotFoundException
import org.gnit.lucenekmp.store.FSDirectory
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.jdkport.Files
import org.gnit.lucenekmp.jdkport.NoSuchFileException
import org.gnit.lucenekmp.store.RandomAccessInput
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
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
        val base = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "lucenekmp_${prefix}_${Random.nextInt()}")
        FileSystem.SYSTEM.createDirectories(base)
        return base
    }

    /** Simple existence check helper. */
    protected fun slowFileExists(dir: Directory, name: String): Boolean {
        return dir.listAll().contains(name)
    }

    @Throws(Exception::class)
    fun testCopyFrom() {
        newDirectory().use { source ->
            newDirectory().use { dest ->
                runCopyFrom(source, dest)
            }
        }
    }

    @Throws(Exception::class)
    fun testRename() {
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
    fun testDeleteFile() {
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
    fun testByte() {
        newDirectory().use { dir ->
            dir.createOutput("byte", IOContext.DEFAULT).use { it.writeByte(128.toByte()) }
            dir.openInput("byte", IOContext.DEFAULT).use { input ->
                kotlin.test.assertEquals(1L, input.length())
                kotlin.test.assertEquals(128.toByte(), input.readByte())
            }
        }
    }

    @Throws(Exception::class)
    fun testShort() {
        newDirectory().use { dir ->
            dir.createOutput("short", IOContext.DEFAULT).use { it.writeShort((-20).toShort()) }
            dir.openInput("short", IOContext.DEFAULT).use { input ->
                kotlin.test.assertEquals(2L, input.length())
                kotlin.test.assertEquals((-20).toShort(), input.readShort())
            }
        }
    }

    @Throws(Exception::class)
    fun testInt() {
        newDirectory().use { dir ->
            dir.createOutput("int", IOContext.DEFAULT).use { it.writeInt(-500) }
            dir.openInput("int", IOContext.DEFAULT).use { input ->
                kotlin.test.assertEquals(4L, input.length())
                kotlin.test.assertEquals(-500, input.readInt())
            }
        }
    }

    @Throws(Exception::class)
    fun testLong() {
        newDirectory().use { dir ->
            dir.createOutput("long", IOContext.DEFAULT).use { it.writeLong(-5000L) }
            dir.openInput("long", IOContext.DEFAULT).use { input ->
                kotlin.test.assertEquals(8L, input.length())
                kotlin.test.assertEquals(-5000L, input.readLong())
            }
        }
    }

    @Throws(Exception::class)
    fun testAlignedLittleEndianLongs() {
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
    fun testUnalignedLittleEndianLongs() {
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
    fun testLittleEndianLongsUnderflow() {
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
    fun testAlignedInts() {
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
    fun testUnalignedInts() {
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
    fun testIntsUnderflow() {
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
    fun testAlignedFloats() {
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
    fun testUnalignedFloats() {
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
    fun testFloatsUnderflow() {
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
    fun testString() {
        newDirectory().use { dir ->
            dir.createOutput("string", IOContext.DEFAULT).use { it.writeString("hello!") }
            dir.openInput("string", IOContext.DEFAULT).use { input ->
                kotlin.test.assertEquals("hello!", input.readString())
                kotlin.test.assertEquals(7L, input.length())
            }
        }
    }

    @Throws(Exception::class)
    fun testVInt() {
        newDirectory().use { dir ->
            dir.createOutput("vint", IOContext.DEFAULT).use { it.writeVInt(500) }
            dir.openInput("vint", IOContext.DEFAULT).use { input ->
                kotlin.test.assertEquals(2L, input.length())
                kotlin.test.assertEquals(500, input.readVInt())
            }
        }
    }

    @Throws(Exception::class)
    fun testVLong() {
        newDirectory().use { dir ->
            dir.createOutput("vlong", IOContext.DEFAULT).use { it.writeVLong(Long.MAX_VALUE) }
            dir.openInput("vlong", IOContext.DEFAULT).use { input ->
                kotlin.test.assertEquals(9L, input.length())
                kotlin.test.assertEquals(Long.MAX_VALUE, input.readVLong())
            }
        }
    }

    @Throws(Exception::class)
    fun testZInt() {
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
    fun testZLong() {
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
    fun testSetOfStrings() {
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
    fun testMapOfStrings() {
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
    fun testChecksum() {
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
    fun testDetectClose() {
        val dir = newDirectory()
        dir.close()
        LuceneTestCase.expectThrows(org.gnit.lucenekmp.store.AlreadyClosedException::class) {
            dir.createOutput("test", IOContext.DEFAULT)
        }
    }

    @Throws(Exception::class)
    fun testThreadSafetyInListAll() {
        newDirectory().use { dir ->
            val max = TestUtil.nextInt(Random, 500, 1000)
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
    fun testFileExistsInListAfterCreated() {
        newDirectory().use { dir ->
            val name = "file"
            dir.createOutput(name, IOContext.DEFAULT).close()
            kotlin.test.assertTrue(slowFileExists(dir, name))
            kotlin.test.assertTrue(dir.listAll().contains(name))
        }
    }

    @Throws(Exception::class)
    fun testSeekToEOFThenBack() {
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
    fun testIllegalEOF() {
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
    fun testSeekPastEOF() {
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
    fun testSliceOutOfBounds() {
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
    fun testNoDir() {
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
    fun testCopyBytes() {
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

    fun testCopyBytesWithThreads() {
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
    fun testFsyncDoesntCreateNewFiles() {
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
    fun testRandomLong() {
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
    fun testRandomInt() {
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
}
