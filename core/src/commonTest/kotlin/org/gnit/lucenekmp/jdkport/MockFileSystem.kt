package org.gnit.lucenekmp.jdkport

import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.Sink
import kotlinx.io.files.Path
import kotlinx.io.buffered
import kotlinx.io.readByteArray

/**
 * A mock file system implementation for testing file operations without relying on the actual file system.
 * This class stores file content in memory using a map of paths to buffers.
 */
object MockFileSystem {
    private val fileContents = mutableMapOf<String, Buffer>()

    /**
     * Clears all files from the mock file system.
     */
    fun reset() {
        fileContents.clear()
    }

    /**
     * Creates a source for reading from a file.
     * @param path The path to the file
     * @return A Source for reading from the file
     * @throws kotlinx.io.files.FileNotFoundException if the file doesn't exist
     */
    fun source(path: Path): Source {
        val pathStr = path.toString()
        val buffer = fileContents[pathStr] ?: throw kotlinx.io.files.FileNotFoundException("File not found: $pathStr")
        // Create a copy of the buffer to avoid modifying the original
        val copy = Buffer()
        val bufferCopy = buffer.copy()
        val bytes = bufferCopy.readByteArray(bufferCopy.size.toInt())
        copy.write(bytes)
        return copy
    }

    /**
     * Creates a sink for writing to a file.
     * @param path The path to the file
     * @return A Sink for writing to the file
     */
    fun sink(path: Path): Sink {
        val pathStr = path.toString()
        val buffer = Buffer()
        fileContents[pathStr] = buffer
        return buffer
    }

    /**
     * Creates directories for the given path.
     * In this mock implementation, this is a no-op since we don't need to create actual directories.
     * @param path The path to create directories for
     */
    fun createDirectories(path: Path) {
        // No-op in mock implementation
    }

    /**
     * Deletes a file or directory.
     * @param path The path to delete
     * @param mustExist If true, throws an exception if the path doesn't exist
     * @throws kotlinx.io.files.FileNotFoundException if mustExist is true and the path doesn't exist
     */
    fun delete(path: Path, mustExist: Boolean = false) {
        val pathStr = path.toString()
        if (mustExist && !fileContents.containsKey(pathStr)) {
            throw kotlinx.io.files.FileNotFoundException("File not found: $pathStr")
        }
        fileContents.remove(pathStr)
    }
}

/**
 * Implementation of FileSystemProvider that uses MockFileSystem.
 * This is used for testing file operations without relying on the actual file system.
 */
class MockFileSystemProvider : FileSystemProvider {
    init {
        // Reset the mock file system when a new provider is created
        MockFileSystem.reset()
    }

    override fun source(path: Path): Source {
        return MockFileSystem.source(path)
    }

    override fun sink(path: Path): Sink {
        return MockFileSystem.sink(path)
    }
}
