package org.gnit.lucenekmp.jdkport

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.Source
import kotlinx.io.Sink

/**
 * port of java.nio.file.Files
 */
object Files {
    // FileSystem to use - defaults to SystemFileSystem but can be overridden for testing
    private var fileSystem: FileSystemProvider = DefaultFileSystemProvider()

    /**
     * Sets the file system provider to use for file operations.
     * This is primarily used for testing to avoid accessing the actual file system.
     */
    fun setFileSystemProvider(provider: FileSystemProvider) {
        fileSystem = provider
    }

    /**
     * Resets the file system provider to the default (SystemFileSystem).
     */
    fun resetFileSystemProvider() {
        fileSystem = DefaultFileSystemProvider()
    }

    fun newInputStream(path: Path): InputStream {
        val source = fileSystem.source(path)
        return KIOSourceInputStream(source)
    }

    fun newOutputStream(path: Path): OutputStream {
        val sink = fileSystem.sink(path)
        return KIOSinkOutputStream(sink)
    }

    fun newBufferedReader(path: Path, charset: Charset): Reader {
        // Create a new input stream for each reader to avoid stream closure issues
        val inputStream = newInputStream(path)
        val decoder = charset.newDecoder()
        val reader = InputStreamReader(inputStream, decoder)
        return BufferedReader(reader)
    }
}

/**
 * Interface for file system providers that can be used by Files.
 */
interface FileSystemProvider {
    fun source(path: Path): Source
    fun sink(path: Path): Sink
}

/**
 * Default implementation of FileSystemProvider that uses SystemFileSystem.
 */
class DefaultFileSystemProvider : FileSystemProvider {
    override fun source(path: Path): Source {
        return SystemFileSystem.source(path).buffered()
    }

    override fun sink(path: Path): Sink {
        return SystemFileSystem.sink(path).buffered()
    }
}
