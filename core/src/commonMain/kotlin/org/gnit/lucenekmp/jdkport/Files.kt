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

    // New overload that honors open options
    fun newOutputStream(path: Path, vararg options: OpenOption): OutputStream {
        // Defaults for Files.newOutputStream when no options are provided are CREATE & TRUNCATE_EXISTING
        val opts = options.toSet()
        val defaulted = opts.isEmpty()
        val append = opts.contains(StandardOpenOption.APPEND)
        val createNew = opts.contains(StandardOpenOption.CREATE_NEW)
        val create = opts.contains(StandardOpenOption.CREATE) || defaulted
        val truncate = opts.contains(StandardOpenOption.TRUNCATE_EXISTING) || defaulted

        if (append && truncate) {
            throw IllegalArgumentException("APPEND and TRUNCATE_EXISTING cannot be used together")
        }

        // Ensure parent directories exist when creating
        val parent = path.parent
        if ((create || createNew) && parent != null && !fileSystem.exists(parent)) {
            createDirectories(parent)
        }

        val exists = fileSystem.exists(path)
        if (createNew) {
            if (exists) {
                throw FileAlreadyExistsException(path.toString())
            }
            // Best-effort atomic create; prefer mustCreate when available
            val sink = try {
                // Okio FileSystem.sink has an overload with mustCreate
                fileSystem.sink(path, mustCreate = true).buffer()
            } catch (e: Throwable) {
                // Fallback: explicit existence check already done, create via write
                // This still prevents overwriting existing files
                fileSystem.sink(path).buffer()
            }
            return OkioSinkOutputStream(sink)
        }

        if (append) {
            val sink = fileSystem.appendingSink(path).buffer()
            return OkioSinkOutputStream(sink)
        }

        // Truncate or create if missing
        if (!exists && !create) {
            // In Java, default creates the file; if CREATE wasn't requested explicitly and file doesn't exist,
            // behavior is to fail. However, Lucene always requests the right options. We mirror Java defaults when no options.
            throw IOException("File does not exist: $path")
        }

        // Default behavior: create if needed, otherwise truncate
        val sink = fileSystem.sink(path).buffer()
        return OkioSinkOutputStream(sink)
    }

    fun newOutputStream(path: Path): OutputStream {
        // Delegate to the options-aware overload with defaults
        return newOutputStream(path, *emptyArray())
    }

    fun newBufferedReader(path: Path, charset: Charset): BufferedReader {
        // Create a new input stream for each reader to avoid stream closure issues
        val inputStream = newInputStream(path)
        val decoder = charset.newDecoder()
        val reader = InputStreamReader(inputStream, decoder)
        return BufferedReader(reader)
    }

    fun newBufferedWriter(path: Path, charset: Charset): BufferedWriter {
        val outputStream = newOutputStream(path)
        val writer = OutputStreamWriter(outputStream, charset)
        return BufferedWriter(writer)
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
        return try {
            fileSystem.metadata(path).isDirectory
        } catch (e: Throwable) {
            // Match java.nio.file.Files.isDirectory: return false if the file does not exist
            // or if its attributes cannot be read due to an I/O error.
            false
        }
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
