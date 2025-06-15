package org.gnit.lucenekmp.tests.store

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.util.LuceneTestCase
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
