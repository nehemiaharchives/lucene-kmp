package org.gnit.lucenekmp.store

import okio.IOException
import okio.FileNotFoundException
import okio.Path
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.AtomicInteger
import org.gnit.lucenekmp.jdkport.FileAlreadyExistsException
import org.gnit.lucenekmp.jdkport.Files
import org.gnit.lucenekmp.jdkport.FilterOutputStream
import org.gnit.lucenekmp.jdkport.NoSuchFileException
import org.gnit.lucenekmp.jdkport.StandardCopyOption
import org.gnit.lucenekmp.jdkport.StandardOpenOption
import org.gnit.lucenekmp.jdkport.OpenOption
import org.gnit.lucenekmp.jdkport.toRealPath
import org.gnit.lucenekmp.util.Constants
import org.gnit.lucenekmp.util.IOUtils
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement
import kotlin.concurrent.atomics.incrementAndFetch

import kotlin.jvm.JvmOverloads
import kotlin.math.min


// javadoc @link

/**
 * Base class for Directory implementations that store index files in the file system. <a id="subclasses"></a> There are currently two core subclasses:
 *
 *
 *  * [MMapDirectory] uses memory-mapped IO when reading. This is a good choice if you have
 * plenty of virtual memory relative to your index size, eg if you are running on a 64 bit
 * JRE, or you are running on a 32 bit JRE but your index sizes are small enough to fit into
 * the virtual memory space. This class will use the modern [       ] API available since Java 21 which allows to safely unmap
 * previously mmapped files after closing the [IndexInput]s. There is no need to enable
 * the "preview feature" of your Java version; it works out of box with some compilation
 * tricks. For more information about the foreign memory API read documentation of the [       ] package and [Uwe's blog
 * post](https://blog.thetaphi.de/2012/07/use-lucenes-mmapdirectory-on-64bit.html).
 *  * [NIOFSDirectory] uses java.nio's FileChannel's positional io when reading to avoid
 * synchronization when reading from the same file. Unfortunately, due to a Windows-only [Sun JRE bug](https://bugs.java.com/bugdatabase/view_bugbug_id=6265734) this is a
 * poor choice for Windows, but on all other platforms this is the preferred choice.
 * Applications using [Thread.interrupt] or [Future.cancel] should use
 * `RAFDirectory` instead, which is provided in the `misc` module. See [       ] javadoc for details.
 *
 *
 *
 * Unfortunately, because of system peculiarities, there is no single overall best
 * implementation. Therefore, we've added the [.open] method, to allow Lucene to choose the
 * best FSDirectory implementation given your environment, and the known limitations of each
 * implementation. For users who have no reason to prefer a specific implementation, it's best to
 * simply use [.open]. For all others, you should instantiate the desired implementation
 * directly.
 *
 *
 * **NOTE:** Accessing one of the above subclasses either directly or indirectly from a thread
 * while it's interrupted can close the underlying channel immediately if at the same time the
 * thread is blocked on IO. The channel will remain closed and subsequent access to the index will
 * throw a [ClosedChannelException]. Applications using [Thread.interrupt] or [ ][Future.cancel] should use the slower legacy `RAFDirectory` from the `misc`
 * Lucene module instead.
 *
 *
 * The locking implementation is by default [NativeFSLockFactory], but can be changed by
 * passing in a custom [LockFactory] instance.
 *
 * @see Directory
 */
abstract class FSDirectory protected constructor(path: Path, lockFactory: LockFactory) :
    BaseDirectory(lockFactory) {
    val directory: Path // The underlying filesystem directory

    /**
     * Maps files that we are trying to delete (or we tried already but failed) before attempting to
     * delete that key.
     */
    private val pendingDeletes: MutableSet<String> = mutableSetOf() /*ConcurrentHashMap.newKeySet<String>()*/

    @OptIn(ExperimentalAtomicApi::class)
    private val opsSinceLastDelete: AtomicInteger = AtomicInteger(0)

    /** Used to generate temp file names in [.createTempOutput].  */
    @OptIn(ExperimentalAtomicApi::class)
    private val nextTempFileCounter: AtomicLong = AtomicLong(0L)

    /**
     * Create a new FSDirectory for the named location (ctor for subclasses). The directory is created
     * at the named location if it does not yet exist.
     *
     *
     * `FSDirectory` resolves the given Path to a canonical / real path to ensure it can
     * correctly lock the index directory and no other process can interfere with changing possible
     * symlinks to the index directory inbetween. If you want to use symlinks and change them
     * dynamically, close all `IndexWriters` and create a new `FSDirectory` instance.
     *
     * @param path the path of the directory
     * @param lockFactory the lock factory to use, or null for the default ([     ]);
     * @throws IOException if there is a low-level I/O error
     */
    init {
        // If only read access is permitted, createDirectories fails even if the directory already
        // exists.
        if (!Files.isDirectory(path)) {
            Files.createDirectories(path) // create directory, if it doesn't exist
        }
        directory = path.toRealPath()
    }

    override fun listAll(): Array<String> {
        ensureOpen()
        return listAll(directory, pendingDeletes)
    }

    override fun fileLength(name: String): Long {
        ensureOpen()
        if (pendingDeletes.contains(name)) {
            throw NoSuchFileException("file \"$name\" is pending delete")
        }
        return Files.size(directory.resolve(name))
    }

    override fun createOutput(
        name: String,
        context: IOContext
    ): IndexOutput {
        ensureOpen()
        maybeDeletePendingFiles()
        // If this file was pending delete, we are now bringing it back to life:
        if (pendingDeletes.remove(name)) {
            privateDeleteFile(name, true) // try again to delete it - this is the best effort
            pendingDeletes.remove(name) // watch out - if the delete fails it put
        }
        return FSIndexOutput(name)
    }

    @OptIn(ExperimentalAtomicApi::class)
    override fun createTempOutput(
        prefix: String,
        suffix: String,
        context: IOContext
    ): IndexOutput {
        ensureOpen()
        maybeDeletePendingFiles()
        while (true) {
            try {
                val name: String = getTempFileName(
                    prefix,
                    suffix,
                    nextTempFileCounter.fetchAndIncrement()/*.getAndIncrement()*/
                )
                if (pendingDeletes.contains(name)) {
                    continue
                }
                return FSIndexOutput(name, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)
            } catch (faee: FileAlreadyExistsException) {
                // Retry with next incremented name
            }
        }
    }

    @Throws(IOException::class)
    protected fun ensureCanRead(name: String) {
        if (pendingDeletes.contains(name)) {
            throw NoSuchFileException(
                "file \"$name\" is pending delete and cannot be opened for read"
            )
        }
    }

    override fun sync(names: MutableCollection<String>) {
        ensureOpen()

        for (name in names) {
            fsync(name)
        }
        maybeDeletePendingFiles()
    }

    override fun rename(source: String, dest: String) {
        ensureOpen()
        if (pendingDeletes.contains(source)) {
            throw NoSuchFileException(
                "file \"$source\" is pending delete and cannot be moved"
            )
        }
        maybeDeletePendingFiles()
        if (pendingDeletes.remove(dest)) {
            privateDeleteFile(dest, true) // try again to delete it - this is the best effort
            pendingDeletes.remove(dest) // watch out if the delete fails, it's back in here
        }
        Files.move(
            directory.resolve(source),
            directory.resolve(dest),
            StandardCopyOption.ATOMIC_MOVE
        )
    }

    override fun syncMetaData() {
        // TODO: to improve listCommits(), IndexFileDeleter could call this after deleting segments_Ns
        ensureOpen()
        IOUtils.fsync(directory, true)
        maybeDeletePendingFiles()
    }

    @Throws(IOException::class)
    override fun close() {
        isOpen = false
        deletePendingFiles()
    }

    override fun toString(): String {
        return this::class.simpleName + "@" + directory + " lockFactory=" + lockFactory
    }

    @Throws(IOException::class)
    protected fun fsync(name: String) {
        IOUtils.fsync(directory.resolve(name), false)
    }

    override fun deleteFile(name: String) {
        if (pendingDeletes.contains(name)) {
            throw NoSuchFileException("file \"$name\" is already pending delete")
        }
        privateDeleteFile(name, false)
        maybeDeletePendingFiles()
    }

    /**
     * Try to delete any pending files that we had previously tried to delete but failed because we
     * are on Windows and the files were still held open.
     */
    @Throws(IOException::class)
    fun deletePendingFiles() {
        if (pendingDeletes.isEmpty() == false) {
            // TODO: we could fix IndexInputs from FSDirectory subclasses to call this when they are
            // closed

            // Clone the set since we mutate it in privateDeleteFile:

            for (name in HashSet<String>(pendingDeletes)) {
                privateDeleteFile(name, true)
            }
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    @Throws(IOException::class)
    private fun maybeDeletePendingFiles() {
        if (pendingDeletes.isEmpty() == false) {
            // This is a silly heuristic to try to avoid O(N^2), where N = number of files pending
            // deletion, behaviour on Windows:
            val count: Int = opsSinceLastDelete.incrementAndFetch()
            if (count >= pendingDeletes.size) {
                opsSinceLastDelete.addAndFetch(-count)
                deletePendingFiles()
            }
        }
    }

    @Throws(IOException::class)
    private fun privateDeleteFile(name: String, isPendingDelete: Boolean) {
        try {
            Files.delete(directory.resolve(name))
            pendingDeletes.remove(name)
        } catch (e: NoSuchFileException) {
            // We were asked to delete a non-existent file:
            pendingDeletes.remove(name)
            if (isPendingDelete && Constants.WINDOWS) {
                // TODO: can we remove this OS-specific hacky logic  If windows deleteFile is buggy, we
                // should instead contain this workaround in
                // a WindowsFSDirectory ...
                // LUCENE-6684: we suppress this check for Windows, since a file could be in a confusing
                // "pending delete" state, failing the first
                // delete attempt with access denied and then apparently falsely failing here when we try to
                // delete it again, with NSFE/FNFE
            } else {
                throw e
            }
        } catch (e: FileNotFoundException) {
            pendingDeletes.remove(name)
            if (isPendingDelete && Constants.WINDOWS) {
            } else {
                throw e
            }
        } catch (ioe: IOException) {
            // On windows, a file delete can fail because there's still an open
            // file handle against it.  We record this in pendingDeletes and
            // try again later.

            // TODO: this is hacky/lenient (we don't know which IOException this is), and
            // it should only happen on filesystems that can do this, so really we should
            // move this logic to WindowsDirectory or something

            // TODO: can/should we do if (Constants.WINDOWS) here, else throw the exc
            // but what about a Linux box with a CIFS mount

            pendingDeletes.add(name)
        }
    }

    internal inner class FSIndexOutput(
        name: String,
        vararg options: OpenOption // currently we do not support any options but implement if needed
    ) : OutputStreamIndexOutput(
        "FSIndexOutput(path=\"" + directory.resolve(name) + "\")",
        name,
        object : FilterOutputStream(Files.newOutputStream(directory.resolve(name)/*, *options*/)) {
            // This implementation ensures, that we never write more than CHUNK_SIZE bytes:
            @Throws(IOException::class)
            override fun write(b: ByteArray, offset: Int, length: Int) {
                var offset = offset
                var length = length
                while (length > 0) {
                    val chunk = min(length, CHUNK_SIZE)
                    out!!.write(b, offset, chunk)
                    length -= chunk
                    offset += chunk
                }
            }
        },
        CHUNK_SIZE
    ) {
        constructor(name: String) : this(name, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)
    }

    override val pendingDeletions: MutableSet<String>
        get() {
            deletePendingFiles()
            if (pendingDeletes.isEmpty()) {
                return mutableSetOf<String>()
            } else {
                return pendingDeletes.toMutableSet()
            }
        }

    companion object {
        /**
         * The maximum chunk size is 8192 bytes, because file channel mallocs a native buffer outside of
         * stack if the write buffer size is larger.
         */
        const val CHUNK_SIZE: Int = 8192

                /** Just like [.open], but allows you to also specify a custom [LockFactory].  */
        /**
         * Creates an FSDirectory instance, trying to pick the best implementation given the current
         * environment. The directory returned uses the [NativeFSLockFactory]. The directory is
         * created at the named location if it does not yet exist.
         *
         *
         * `FSDirectory` resolves the given Path when calling this method to a canonical / real
         * path to ensure it can correctly lock the index directory and no other process can interfere
         * with changing possible symlinks to the index directory inbetween. If you want to use symlinks
         * and change them dynamically, close all `IndexWriters` and create a new `FSDirectory` instance.
         *
         *
         * Currently this returns [MMapDirectory] for Linux, MacOSX, Solaris, and Windows 64-bit
         * JREs, and [NIOFSDirectory] for other JREs. It is highly recommended that you consult the
         * implementation's documentation for your platform before using this method.
         *
         *
         * **NOTE**: this method may suddenly change which implementation is returned from release
         * to release, in the event that higher performance defaults become possible; if the precise
         * implementation is important to your application, please instantiate it directly, instead. For
         * optimal performance you should consider using [MMapDirectory] on 64 bit JVMs.
         *
         *
         * See [above](#subclasses)
         */
        @JvmOverloads
        @Throws(IOException::class)
        fun open(path: Path, lockFactory: LockFactory = FSLockFactory.default): FSDirectory {
            /*if (Constants.JRE_IS_64BIT) {
                return MMapDirectory(path, lockFactory, true)
            } else {
                return NIOFSDirectory(path, lockFactory)
            }*/

            return NIOFSDirectory(path, lockFactory)
        }

        /**
         * Lists all files (including subdirectories) in the directory.
         *
         * @throws IOException if there was an I/O error during listing
         */
        @Throws(IOException::class)
        private fun listAll(dir: Path, skipNames: Set<String> = emptySet<String>()): Array<String> {
            val entries: MutableList<String> = ArrayList()

            /*Files.newDirectoryStream(dir).use { stream ->
                for (path in stream) {
                    val name = path.getFileName().toString()
                    if (skipNames.isEmpty() || skipNames.contains(name) == false) {
                        entries.add(name)
                    }
                }
            }*/

            Files.newDirectoryStream(dir).forEach { path->
                val name = path.name
                if (skipNames.isEmpty() || skipNames.contains(name) == false) {
                    entries.add(name)
                }
            }

            val array = entries.toTypedArray<String>()
            // Directory.listAll javadocs state that we sort the results here, so we don't let filesystem
            // specifics leak out of this abstraction:
            Arrays.sort(array)
            return array
        }
    }
}
