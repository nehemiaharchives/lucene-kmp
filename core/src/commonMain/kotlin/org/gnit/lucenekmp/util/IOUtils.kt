package org.gnit.lucenekmp.util

import okio.Closeable
import okio.IOException
import okio.FileNotFoundException
import okio.Path
import okio.FileSystem
import org.gnit.lucenekmp.jdkport.BufferedReader
import org.gnit.lucenekmp.jdkport.Charset
import org.gnit.lucenekmp.jdkport.CharsetDecoder
import org.gnit.lucenekmp.jdkport.CodingErrorAction
import org.gnit.lucenekmp.jdkport.Files
import org.gnit.lucenekmp.jdkport.InputStream
import org.gnit.lucenekmp.jdkport.InputStreamReader
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.util.IOUtils.rethrowAlways
import kotlin.jvm.JvmName

object IOUtils {

    // Do not cache the FileSystem; always read the current instance so tests can swap it.
    private fun fs(): FileSystem = Files.getFileSystem()

    /** UTF-8 charset string.  */
    const val UTF_8: String = "UTF-8"

    fun <T: Throwable> useOrSuppress(first: T?, second: T): T {
        if (first == null) {
            return second
        } else {
            // Add second exception as suppressed to first
            first.addSuppressed(second)
            return first
        }
    }

    /** Closes all given AutoCloseables. Some of the objects may be null; they are ignored.
     * After everything is closed, throws the first exception encountered, or completes normally if none. */
    @Throws(IOException::class)
    fun close(vararg objects: AutoCloseable) {
        close(objects.asList())
    }

    @JvmName("closeNullable")
    @Throws(IOException::class)
    fun close(vararg objects: AutoCloseable?) {
        close(objects.asList().filterNotNull())
    }

    /** Closes all given AutoCloseables.:contentReference[oaicite:0]{index=0}
     * @see #close(vararg AutoCloseable) */
    @Throws(IOException::class)
    fun close(objects: Iterable<AutoCloseable>) {
        var th: Throwable? = null
        for (obj in objects) {
            try {
                obj.close()
            } catch (t: Throwable) {
                th = useOrSuppress(th, t)  // accumulate the first exception
            }
        }
        if (th != null) {
            throw rethrowAlways(th)      // rethrow first exception (wrap if needed)
        }
    }

    /** Closes all given AutoCloseables, suppressing all thrown exceptions.
     * Some of the objects may be null; they are ignored. */
    fun closeWhileHandlingException(vararg objects: AutoCloseable) {
        closeWhileHandlingException(objects.asList())
    }

    @JvmName("closeWhileHandlingExceptionNullable")
    fun closeWhileHandlingException(vararg objects: AutoCloseable?) {
        closeWhileHandlingException(objects.asList().filterNotNull())
    }

    /** Closes all given AutoCloseables, suppressing all thrown non-Error exceptions.:contentReference[oaicite:1]{index=1}
     * Even if a VirtualMachineError (or equivalent fatal Error) is thrown, all given resources are closed. */
    fun closeWhileHandlingException(objects: Iterable<AutoCloseable>) {
        var firstError: Error? = null
        var firstThrowable: Throwable? = null
        for (obj in objects) {
            try {
                obj.close()
            } catch (e: Error) {
                firstError = useOrSuppress(firstError, e)   // track first fatal error
            } catch (t: Throwable) {
                firstThrowable = useOrSuppress(firstThrowable, t) // track first non-fatal exception
            }
        }
        if (firstError != null) {
            // If a fatal error occurred, add any non-fatal exception as suppressed and throw the error
            if (firstThrowable != null) {
                firstError.addSuppressed(firstThrowable)
            }
            throw firstError
        }
        // If no fatal Error, all exceptions are suppressed and we return normally (exception already logged as suppressed in firstThrowable if any)
    }

    fun closeWhileHandlingException(closable: Closeable){
        try {
            closable.close()
        } catch (e: Error) {
            // If a fatal error occurred, rethrow it.
            throw e
        } catch (_: Throwable) {
            // For any other Throwable, suppress it and return normally.
            // This matches the behavior of closeWhileHandlingException(objects: Iterable<AutoCloseable>)
            // when no Error is thrown.
        }
    }

    /**
     * Wrapping the given [InputStream] in a reader using a [CharsetDecoder]. Unlike
     * Java's defaults this reader will throw an exception if your it detects the read charset doesn't
     * match the expected [Charset].
     *
     *
     * Decoding readers are useful to load configuration files, stopword lists or synonym files to
     * detect character set problems. However, it's not recommended to use as a common purpose reader.
     *
     * @param stream the stream to wrap in a reader
     * @param charSet the expected charset
     * @return a wrapping reader
     */
    fun getDecodingReader(stream: InputStream, charSet: Charset): Reader {
        val charSetDecoder: CharsetDecoder =
            charSet
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
        return BufferedReader(InputStreamReader(stream, charSetDecoder))
    }

    /**
     * Wrap all calls to [Class.getResource] or [ ][Class.getResourceAsStream] using this method to enforce existence of the resource. This
     * code works around those methods returning `null` to signal non-existence.
     *
     * @param resource return value of above methods
     * @param name of resource
     * @return the resource passed in if existent
     * @throws FileNotFoundException if resource was not found
     */
    @Throws(IOException::class)
    fun <T> requireResourceNonNull(resource: T?, name: String?): T? {
        if (resource == null) {
            throw FileNotFoundException("The resource '" + name + "' was not found.")
        }
        return resource
    }

    /** Deletes all given files in a Directory, suppressing all thrown IOExceptions.
     * Note that the file names should not be null.
     * @param dir the Directory to delete files from
     * @param files collection of file names to delete */
    fun deleteFilesIgnoringExceptions(dir: Directory, files: Collection<String>) {
        for (name in files) {
            try {
                dir.deleteFile(name)     // Lucene Directory deletion:contentReference[oaicite:3]{index=3}
            } catch (_: Throwable) {
                // ignore any exception
            }
        }
    }

    /** Deletes all given files in a Directory, suppressing all thrown IOExceptions.:contentReference[oaicite:4]{index=4}
     * @param dir the Directory to delete files from
     * @param files vararg of file names to delete */
    fun deleteFilesIgnoringExceptions(dir: Directory, vararg files: String) {
        deleteFilesIgnoringExceptions(dir, files.asList())
    }

    /** Deletes all given file names from a Directory.:contentReference[oaicite:5]{index=5}
     * Some of the names may be null; they are ignored.
     * After deletion, throws the first exception encountered (with others suppressed) if any failures occurred.
     * @param dir Directory to delete files from
     * @param names file names to delete */
    @Throws(IOException::class)
    fun deleteFiles(dir: Directory, names: Collection<String>) {
        var th: Throwable? = null
        for (name in names) {
            if (name != null) {
                try {
                    dir.deleteFile(name)
                } catch (t: Throwable) {
                    th = useOrSuppress(th, t)   // accumulate exceptions
                }
            }
        }
        if (th != null) {
            throw rethrowAlways(th)
        }
    }

    /** Deletes all given [Path]s, suppressing all thrown IOExceptions.
     * Some of the paths may be null; they are ignored. */
    fun deleteFilesIgnoringExceptions(vararg files: Path) {
        deleteFilesIgnoringExceptions(files.asList())
    }

    /** Deletes all given [Path]s, suppressing all thrown IOExceptions.
     * Some of the paths may be null; they are ignored. */
    fun deleteFilesIgnoringExceptions(files: Collection<Path>) {
        for (path in files) {
            if (path != null) {
                try {
                    fs().delete(path, mustExist = false)  // ignore if doesn't exist
                } catch (_: Throwable) {
                    // ignore all failures
                }
            }
        }
    }

    /** Deletes all given [Path]s if they exist.
     * Some may be null; they are ignored. After deletion, throws the first exception encountered (others suppressed) if any deletion failed. */
    @Throws(IOException::class)
    fun deleteFilesIfExist(vararg files: Path) {
        deleteFilesIfExist(files.asList())
    }

    /** Deletes all given [Path]s if they exist.
     * After deletion, throws the first exception encountered (others suppressed) if any deletion failed. */
    @Throws(IOException::class)
    fun deleteFilesIfExist(files: Collection<Path>) {
        var th: Throwable? = null
        for (file in files) {
            try {
                if (file != null) {
                    fs().delete(file, mustExist = false) // won't throw if file not present
                }
            } catch (t: Throwable) {
                th = useOrSuppress(th, t)
            }
        }
        if (th != null) {
            throw rethrowAlways(th)
        }
    }

    /** Deletes one or more files or directories (and everything underneath them).
     * If any file or sub-file cannot be removed, throws IOException with details.
     * @throws IOException if any of the given files (or their descendants) cannot be fully removed. */
    @Throws(IOException::class)
    fun rm(vararg locations: Path) {
        val unremoved = rm(LinkedHashMap(), *locations)   // attempt removals, gather failures
        if (unremoved.isNotEmpty()) {
            // Build exception message listing files that could not be removed
            val b = StringBuilder("Could not remove the following files (in the order of attempts):\n")
            for ((path, cause) in unremoved) {
                b.append("   ").append(path.toString()).append(": ").append(cause).append("\n")
            }
            throw IOException(b.toString())
        }
    }

    // Attempts to delete the given locations (non-recursively), recording any failures in the provided map.
    private fun rm(unremoved: LinkedHashMap<Path, Throwable>, vararg locations: Path?): LinkedHashMap<Path, Throwable> {
        if (locations != null) {
            for (location in locations) {
                if (location != null) {
                    try {
                        fs().delete(location, mustExist = false)
                    } catch (e: IOException) {
                        // If directory not empty or other issue, record it
                        unremoved[location] = e
                    } catch (t: Throwable) {
                        unremoved[location] = t
                    }
                }
            }
        }
        return unremoved
    }

    /** Always rethrows the given Throwable as an [IOException] or unchecked exception.
     * This method never actually returns; its return type [Error] (or in this port, [Nothing]) is only to satisfy the compiler that no code follows.
     * @param th the throwable to rethrow (must not be null)
     * @throws IOException if `th` is an IOException
     * @throws RuntimeException (wrapped) if `th` is not an IOException or Error */
    @Throws(IOException::class, RuntimeException::class)
    fun rethrowAlways(th: Throwable): Nothing {
        if (th is IOException) {
            throw th
        }
        if (th is RuntimeException) {
            throw th
        }
        if (th is Error) {
            throw th
        }
        throw RuntimeException(th)
    }

    /** Rethrows the argument as IOException or RuntimeException if it's not null.
     * @deprecated Use [rethrowAlways] instead (and ensure `th` is not null before calling). */
    @Deprecated("Use rethrowAlways(Throwable) instead and guard against null", ReplaceWith("if (th != null) IOUtils.rethrowAlways(th)"))
    @Throws(IOException::class)
    fun reThrow(th: Throwable?) {
        if (th != null) {
            rethrowAlways(th)
        }
    }

    /** Rethrows the argument unchecked (as is if it's an Error or RuntimeException, or wrapped in RuntimeException).
     * @deprecated Use [rethrowAlways] instead. */
    @Deprecated("Use rethrowAlways(Throwable) instead and guard against null")
    fun reThrowUnchecked(th: Throwable?) {
        if (th != null) {
            if (th is Error) {
                throw th
            }
            if (th is RuntimeException) {
                throw th
            }
            throw RuntimeException(th)
        }
    }

    /** Ensure that any writes to the given file are persisted to the storage device.:contentReference[oaicite:6]{index=6}
     * On non-JVM platforms, this is implemented as a no-op (except for checking file existence) since fsync is not universally available.
     * @param fileToSync the file or directory to fsync
     * @param isDir true if [fileToSync] is a directory (in which case we attempt a best-effort no-op) */
    @Throws(IOException::class)
    fun fsync(fileToSync: Path, isDir: Boolean) {
        val fileSystem = fs()
        if (isDir) {
            // Many platforms do not support fsync on directories. Just verify existence.
            try {
                val meta = fileSystem.metadataOrNull(fileToSync)
                if (meta == null) {
                    throw FileNotFoundException("The directory '$fileToSync' was not found.")
                }
            } catch (e: FileNotFoundException) {
                throw e
            } catch (_: Throwable) {
                // Best-effort on directories: swallow non-existence/access errors
            }
            return
        }
        // Regular file: ensure it exists; no explicit flush available in common/Okio.
        val meta = fileSystem.metadataOrNull(fileToSync)
            ?: throw FileNotFoundException("The file '$fileToSync' was not found.")
        if (meta.isDirectory) {
            // Called with isDir=false but path is a directory; nothing we can do here.
            return
        }
        // No-op fsync in KMP common code.
    }

    /** Applies the [consumer] to all non-null elements in the [collection].
     * If the consumer throws an exception for one element, that exception is re-thrown after applying to all elements, and later exceptions are suppressed. */
    @JvmName("applyToAllNullable")
    @Throws(IOException::class)
    fun <T> applyToAll(collection: Collection<T?>, consumer: IOConsumer<T>) {
        var firstException: Throwable? = null
        for (item in collection) {
            if (item != null) {
                try {
                    consumer.accept(item)
                } catch (t: Throwable) {
                    firstException = useOrSuppress(firstException, t)  // first exception gets rethrown, others suppressed
                }
            }
        }
        if (firstException != null) {
            throw firstException
        }
    }

    @Throws(IOException::class)
    fun <T> applyToAll(collection: Collection<T>, consumer: IOConsumer<T>) {
        var firstException: Throwable? = null
        for (item in collection) {
            if (item != null) {
                try {
                    consumer.accept(item)
                } catch (t: Throwable) {
                    firstException = useOrSuppress(firstException, t)  // first exception gets rethrown, others suppressed
                }
            }
        }
        if (firstException != null) {
            throw firstException
        }
    }
}
