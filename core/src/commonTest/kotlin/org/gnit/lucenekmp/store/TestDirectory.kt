package org.gnit.lucenekmp.store

import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.gnit.lucenekmp.jdkport.Files
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.store.AlreadyClosedException
import org.gnit.lucenekmp.store.FlushInfo
import okio.IOException
import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals

@Ignore
class TestDirectory : LuceneTestCase() {
    @Test
    fun testDirectInstantiation() {
        val fs = FakeFileSystem()
        Files.setFileSystem(fs)
        try {
            val path = "/testDirectInstantiation".toPath()
            Files.createDirectories(path)
            val largeBuffer = ByteArray(random().nextInt(256 * 1024))
            val largeReadBuffer = ByteArray(largeBuffer.size)
            for (i in largeBuffer.indices) {
                largeBuffer[i] = i.toByte()
            }
            val dirs = arrayOf(
                NIOFSDirectory(path, FSLockFactory.default),
                NIOFSDirectory(path, FSLockFactory.default)
            )
            for (i in dirs.indices) {
                val dir = dirs[i]
                dir.ensureOpen()
                val fname = "foo.$i"
                val lockname = "foo${i}.lck"
                val out = dir.createOutput(fname, IOContext(FlushInfo(0,0)))
                try {
                    out.writeByte(i.toByte())
                    out.writeBytes(largeBuffer, 0, largeBuffer.size)
                } finally {
                    out.close()
                }
                for (d2 in dirs) {
                    d2.ensureOpen()
                    assertTrue(fs.exists(d2.directory.resolve(fname)))
                    assertEquals(1 + largeBuffer.size.toLong(), d2.fileLength(fname))
                    val input = d2.openInput(fname, IOContext(FlushInfo(0,0)))
                    try {
                        assertEquals(i.toByte(), input.readByte())
                        largeReadBuffer.fill(0)
                        input.readBytes(largeReadBuffer, 0, largeReadBuffer.size)
                        assertContentEquals(largeBuffer, largeReadBuffer)
                        input.seek(1L)
                        largeReadBuffer.fill(0)
                        input.readBytes(largeReadBuffer, 0, largeReadBuffer.size)
                        assertContentEquals(largeBuffer, largeReadBuffer)
                    } finally {
                        input.close()
                    }
                }
                dirs[(i + 1) % dirs.size].deleteFile(fname)
                for (d2 in dirs) {
                    assertFalse(fs.exists(d2.directory.resolve(fname)))
                }
                val lock = dir.obtainLock(lockname)
                try {
                    for (other in dirs) {
                        if (other !== dir) {
                            expectThrows<LockObtainFailedException>(LockObtainFailedException::class) {
                                other.obtainLock(lockname).close()
                            }
                        }
                    }
                } finally {
                    lock.close()
                }
                dirs[(i + 1) % dirs.size].obtainLock(lockname).close()
            }
            for (dir in dirs) {
                dir.ensureOpen()
                dir.close()
                expectThrows<AlreadyClosedException>(AlreadyClosedException::class) {
                    dir.ensureOpen()
                }
            }
        } finally {
            Files.resetFileSystem()
        }
    }

    @Test
    fun testNotDirectory() {
        val fs = FakeFileSystem()
        Files.setFileSystem(fs)
        try {
            val path = "/testnotdir".toPath()
            Files.createDirectories(path)
            val fsDir = NIOFSDirectory(path, FSLockFactory.default)
            try {
                val out = fsDir.createOutput("afile", IOContext(FlushInfo(0,0)))
                out.close()
                assertTrue(fs.exists(path / "afile"))
                expectThrows<IOException>(IOException::class) {
                    NIOFSDirectory(path / "afile", FSLockFactory.default)
                }
            } finally {
                fsDir.close()
            }
        } finally {
            Files.resetFileSystem()
        }
    }

    @Test
    fun testListAll() {
        val fs = FakeFileSystem()
        Files.setFileSystem(fs)
        try {
            val dir = "/testdir".toPath()
            Files.createDirectories(dir)
            val file1 = dir / "tempfile1"
            val file2 = dir / "tempfile2"
            Files.createFile(file1)
            Files.createFile(file2)
            val files = fs.list(dir).map { it.name }.sorted().toSet()
            assertTrue(files.size == 2)
            assertTrue(files.contains(file1.name))
            assertTrue(files.contains(file2.name))
        } finally {
            Files.resetFileSystem()
        }
    }
}
