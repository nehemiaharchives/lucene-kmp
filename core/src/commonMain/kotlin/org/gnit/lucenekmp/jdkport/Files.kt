package org.gnit.lucenekmp.jdkport

import okio.FileMetadata
import okio.Path
import okio.FileSystem
import okio.IOException
import okio.SYSTEM
import okio.buffer

/**
 * port of java.nio.file.Files
 */
object Files {
    // FileSystem to use - defaults to SystemFileSystem but can be overridden for testing
    private var fileSystem: FileSystem = FileSystem.SYSTEM

    /**
     * Sets the file system provider to use for file operations.
     * This is primarily used for testing to avoid accessing the actual file system.
     */
    fun setFileSystem(fileSystem: FileSystem) {
        this.fileSystem = fileSystem
    }

    fun getFileSystem(): FileSystem {
        return fileSystem
    }

    /**
     * Resets the file system provider to the default (SystemFileSystem).
     */
    fun resetFileSystem() {
        fileSystem = FileSystem.SYSTEM
    }

    fun newInputStream(path: Path): InputStream {
        val source = fileSystem.source(path).buffer()
        return OkioSourceInputStream(source)
    }

    fun newOutputStream(path: Path): OutputStream {
        val sink = fileSystem.sink(path).buffer()
        return OkioSinkOutputStream(sink)
    }

    fun newBufferedReader(path: Path, charset: Charset): Reader {
        // Create a new input stream for each reader to avoid stream closure issues
        val inputStream = newInputStream(path)
        val decoder = charset.newDecoder()
        val reader = InputStreamReader(inputStream, decoder)
        return BufferedReader(reader)
    }

    fun createDirectories(path: Path){
        // Ensure the parent directories exist
        val parent = path.parent ?: throw IOException("Cannot create directories for root path") as Throwable
        if (!fileSystem.exists(parent)) {
            createDirectories(parent)
        }
        // Create the directory itself
        fileSystem.createDirectories(path)
    }

    fun createFile(path: Path) {
        // Ensure the parent directory exists
        val parent = path.parent ?: throw IOException("Cannot create file for root path") as Throwable
        if (!fileSystem.exists(parent)) {
            createDirectories(parent)
        }
        // Create the file
        fileSystem.write(file = path, mustCreate = true){
            // This will create the file if it does not exist
        }
    }

    fun readAttributes(path: Path): FileMetadata{
        return fileSystem.metadata(path)
    }

    fun creationTime(path: Path) : Long? {
        val attributes = readAttributes(path)
        return attributes.createdAtMillis ?: attributes.lastModifiedAtMillis
    }

    fun isDirectory(path: Path): Boolean {
        return fileSystem.metadata(path).isDirectory
    }

    fun size(path: Path): Long {
        return fileSystem.metadata(path).size?: throw IOException("File does not exist or size is not available")
    }

    fun move(source: Path, target: Path, vararg options: StandardCopyOption) {
        if (!fileSystem.exists(source)) {
            throw IOException("Source file does not exist: $source")
        }
        // Ensure the target directory exists
        val targetParent = target.parent ?: throw IOException("Cannot move to root path") as Throwable
        if (!fileSystem.exists(targetParent)) {
            createDirectories(targetParent)
        }
        // Move the file by copying and then deleting the source

        if(options.contains(StandardCopyOption.ATOMIC_MOVE)){
            fileSystem.atomicMove(source, target)
        }else{
            fileSystem.copy(source, target)
            fileSystem.delete(source)
        }
    }

    fun delete(path: Path) {
        if (!fileSystem.exists(path)) {
            throw IOException("File does not exist: $path")
        }
        fileSystem.delete(path)
    }

    fun newDirectoryStream(path: Path): Sequence<Path> {
        if (!fileSystem.exists(path)) {
            throw IOException("File does not exist: $path")
        }
        if (!fileSystem.metadata(path).isDirectory) {
            throw IOException("Path is not a directory: $path")
        }
        return fileSystem.listRecursively(path)
    }
}

fun Path.toRealPath() = this.normalized()

