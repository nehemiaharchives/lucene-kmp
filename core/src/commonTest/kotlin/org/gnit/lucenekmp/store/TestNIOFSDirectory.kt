package org.gnit.lucenekmp.store

import okio.fakefilesystem.FakeFileSystem
import okio.FileHandle
import okio.Path
import okio.IOException
import okio.ForwardingFileSystem
import org.gnit.lucenekmp.jdkport.toRealPath
import org.gnit.lucenekmp.jdkport.Files
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.store.BaseDirectoryTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TestNIOFSDirectory : BaseDirectoryTestCase() {
    override fun getDirectory(path: Path): Directory {
        return NIOFSDirectory(path)
    }

    @Test
    fun testHandleExceptionInConstructor() {
        // Create a filesystem that throws when size() is called on a FileHandle
        val leakFs = object : ForwardingFileSystem(FakeFileSystem()) {
            override fun openReadOnly(file: Path): FileHandle {
                val delegate = super.openReadOnly(file)
                return object : FileHandle(delegate.readWrite) {
                    override fun protectedRead(
                        fileOffset: Long,
                        array: ByteArray,
                        arrayOffset: Int,
                        byteCount: Int,
                    ): Int {
                        return delegate.read(fileOffset, array, arrayOffset, byteCount)
                    }

                    override fun protectedWrite(
                        fileOffset: Long,
                        array: ByteArray,
                        arrayOffset: Int,
                        byteCount: Int,
                    ) {
                        delegate.write(fileOffset, array, arrayOffset, byteCount)
                    }

                    override fun protectedFlush() {
                        delegate.flush()
                    }

                    override fun protectedResize(size: Long) {
                        delegate.resize(size)
                    }

                    override fun protectedSize(): Long {
                        throw IOException("simulated")
                    }

                    override fun protectedClose() {
                        delegate.close()
                    }
                }
            }
        }

        Files.setFileSystem(leakFs)
        try {
            val path = createTempDir("niofs").toRealPath()
            NIOFSDirectory(path).use { dir ->
                dir.createOutput("test.bin", IOContext.DEFAULT).use { out ->
                    out.writeString("hello")
                }
                val error = assertFailsWith<IOException> {
                    dir.openInput("test.bin", IOContext.DEFAULT)
                }
                assertEquals("simulated", error.message)
            }
        } finally {
            Files.resetFileSystem()
        }
    }
}

