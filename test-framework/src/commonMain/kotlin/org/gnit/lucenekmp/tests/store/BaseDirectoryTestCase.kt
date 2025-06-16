package org.gnit.lucenekmp.tests.store

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.EOFException
import okio.IOException
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
