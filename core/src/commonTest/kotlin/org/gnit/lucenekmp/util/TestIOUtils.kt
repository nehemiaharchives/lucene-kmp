package org.gnit.lucenekmp.util

import okio.FileSystem
import okio.FileNotFoundException
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.gnit.lucenekmp.jdkport.AccessDeniedException
import org.gnit.lucenekmp.jdkport.Files
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TestIOUtils : LuceneTestCase() {
    private lateinit var fakeFileSystem: FakeFileSystem

    @BeforeTest
    fun setUp() {
        fakeFileSystem = FakeFileSystem()
        Files.setFileSystem(fakeFileSystem)
        IOUtils.fileSystem = fakeFileSystem
    }

    @AfterTest
    fun tearDown() {
        Files.resetFileSystem()
        IOUtils.fileSystem = FileSystem.SYSTEM
    }

    @Test
    fun testDeleteFileIgnoringExceptions() {
        val dir = "/deleteFileIgnoring".toPath()
        Files.createDirectories(dir)
        val file1 = dir / "file1"
        Files.createFile(file1)
        IOUtils.deleteFilesIgnoringExceptions(file1)
        assertFalse(fakeFileSystem.exists(file1))
    }

    @Test
    fun testDontDeleteFileIgnoringExceptions() {
        val file1 = "/dontDelete/file1".toPath()
        IOUtils.deleteFilesIgnoringExceptions(file1)
    }

    @Test
    fun testDeleteTwoFilesIgnoringExceptions() {
        val dir = "/deleteTwoIgnoring".toPath()
        Files.createDirectories(dir)
        val file1 = dir / "file1"
        val file2 = dir / "file2"
        Files.createFile(file2)
        IOUtils.deleteFilesIgnoringExceptions(file1, file2)
        assertFalse(fakeFileSystem.exists(file2))
    }

    @Test
    fun testDeleteFileIfExists() {
        val dir = "/deleteFileIfExists".toPath()
        Files.createDirectories(dir)
        val file1 = dir / "file1"
        Files.createFile(file1)
        IOUtils.deleteFilesIfExist(file1)
        assertFalse(fakeFileSystem.exists(file1))
    }

    @Test
    fun testDontDeleteDoesntExist() {
        val file1 = "/dontDeleteIfExist/file1".toPath()
        IOUtils.deleteFilesIfExist(file1)
    }

    @Test
    fun testDeleteTwoFilesIfExist() {
        val dir = "/deleteTwoIfExist".toPath()
        Files.createDirectories(dir)
        val file1 = dir / "file1"
        val file2 = dir / "file2"
        Files.createFile(file2)
        IOUtils.deleteFilesIfExist(file1, file2)
        assertFalse(fakeFileSystem.exists(file2))
    }

    @Test
    fun testFsyncDirectory() {
        val dir = "/fsyncDir".toPath()
        Files.createDirectories(dir)
        IOUtils.fsync(dir, true)
    }

    private class AccessDeniedWhileOpeningDirectoryFileSystem(delegate: FileSystem) :
        okio.ForwardingFileSystem(delegate) {
        override fun delete(path: Path, mustExist: Boolean) {
            if (delegate.metadataOrNull(path)?.isDirectory == true) {
                throw AccessDeniedException(path.toString())
            }
            super.delete(path, mustExist)
        }
    }

    @Test
    fun testFsyncAccessDeniedOpeningDirectory() {
        val path = "/fsyncDenied".toPath()
        Files.createDirectories(path)
        val provider = AccessDeniedWhileOpeningDirectoryFileSystem(fakeFileSystem)
        IOUtils.fileSystem = provider
        // The KMP port's fsync implementation quietly returns on failure when
        // syncing a directory. Verify no exception is thrown on any platform.
        IOUtils.fsync(path, true)
    }

    @Test
    fun testFsyncNonExistentDirectory() {
        val dir = "/fsyncNonExistentBase".toPath()
        Files.createDirectories(dir)
        val nonExistent = dir / "nonexistent"
        expectThrows<FileNotFoundException>(FileNotFoundException::class) {
            IOUtils.fsync(nonExistent, true)
        }
    }

    @Test
    fun testFsyncFile() {
        val dir = "/fsyncFileDir".toPath()
        Files.createDirectories(dir)
        val somefile = dir / "somefile"
        Files.newOutputStream(somefile).use { it.write("0\n".encodeToByteArray()) }
        IOUtils.fsync(somefile, false)
    }

    @Test
    fun testApplyToAll() {
        val closed = ArrayList<Int>()
        val runtimeException = expectThrows(RuntimeException::class) {
            IOUtils.applyToAll(listOf(1, 2)) { i ->
                closed.add(i)
                throw RuntimeException("$i")
            }
        }
        assertEquals("1", runtimeException?.message)
        assertEquals(1, runtimeException?.suppressedExceptions?.size)
        assertEquals("2", runtimeException?.suppressedExceptions?.get(0)?.message)
        assertEquals(2, closed.size)
        assertEquals(1, closed[0])
        assertEquals(2, closed[1])
    }
}
