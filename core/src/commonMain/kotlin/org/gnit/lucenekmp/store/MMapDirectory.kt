package org.gnit.lucenekmp.store

import io.github.oshai.kotlinlogging.KotlinLogging
import okio.FileHandle
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.SYSTEM
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.jdkport.Optional
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.numberOfLeadingZeros
import org.gnit.lucenekmp.util.Constants

// javadoc @link

/**
 * File-based [Directory] implementation that uses mmap for reading, and [ ] for writing.
 *
 *
 * **NOTE**: memory mapping uses up a portion of the virtual memory address space in your
 * process equal to the size of the file being mapped. Before using this class, be sure your have
 * plenty of virtual address space, e.g. by using a 64 bit JRE, or a 32 bit JRE with indexes that
 * are guaranteed to fit within the address space. On 32 bit platforms also consult [ ][.MMapDirectory] if you have problems with mmap failing because of
 * fragmented address space. If you get an [IOException] about mapping failed, it is
 * recommended to reduce the chunk size, until it works.
 *
 *
 * This class supports preloading files into physical memory upon opening. This can help improve
 * performance of searches on a cold page cache at the expense of slowing down opening an index. See
 * [.setPreload] for more details.
 *
 *
 * This class supports grouping of files that are part of the same logical group. This is a hint
 * that allows for better handling of resources. For example, individual files that are part of the
 * same segment can be considered part of the same logical group. See [ ][.setGroupingFunction] for more details.
 *
 *
 * This class will use the modern [foreign.MemorySegment] API available since
 * Java 21 which allows to safely unmap previously mmapped files after closing the [ ]s. There is no need to enable the "preview feature" of your Java version; it works out
 * of box with some compilation tricks. For more information about the foreign memory API read
 * documentation of the [foreign] package.
 *
 *
 * On some platforms like Linux and MacOS X, this class will invoke the syscall `madvise()`
 * to advise how OS kernel should handle paging after opening a file. For this to work, Java code
 * must be able to call native code. If this is not allowed, a warning is logged. To enable native
 * access for Lucene in a modularized application, pass `--enable-native-access=org.apache.lucene.core` to the Java command line. If Lucene is running in
 * a classpath-based application, use `--enable-native-access=ALL-UNNAMED`.
 *
 *
 * **NOTE:** Accessing this class either directly or indirectly from a thread while it's
 * interrupted can close the underlying channel immediately if at the same time the thread is
 * blocked on IO. The channel will remain closed and subsequent access to [MMapDirectory] will
 * throw a [ClosedChannelException]. If your application uses either [ ][Thread.interrupt] or [Future.cancel] you should use the legacy `RAFDirectory` from the Lucene `misc` module in favor of [MMapDirectory].
 *
 *
 * **NOTE:** If your application requires external synchronization, you should **not**
 * synchronize on the `MMapDirectory` instance as this may cause deadlock; use your own
 * (non-Lucene) objects instead.
 *
 * @see [Blog post
 * about MMapDirectory](https://blog.thetaphi.de/2012/07/use-lucenes-mmapdirectory-on-64bit.html)
 *
 *
 * **KMP note:** On Kotlin Multiplatform targets this class provides an mmap-compatible API but
 * uses a non-mmap implementation backed by regular file reads (`okio` + positional IO). This keeps
 * Lucene API behavior (random-access IndexInput, clone/slice semantics, thread-safe positional
 * reads, close semantics) without relying on JDK 21+ foreign memory APIs.
 */
class MMapDirectory(
    path: Path,
    lockFactory: LockFactory = FSLockFactory.default,
    maxChunkSize: Long = DEFAULT_MAX_CHUNK_SIZE
) : FSDirectory(path, lockFactory) {
    private var preload: (String, IOContext) -> Boolean = NO_FILES

    /** A provider specific context object or null, that will be passed to openInput.  */
    val attachment: Any? = PROVIDER.attachment()

    private var groupingFunction: (String) -> Optional<String> = GROUP_BY_SEGMENT

    val chunkSizePower: Int

    /**
     * Create a new MMapDirectory for the named location and [FSLockFactory.getDefault]. The
     * directory is created at the named location if it does not yet exist.
     *
     * @param path the path of the directory
     * @param maxChunkSize maximum chunk size (for default see [.DEFAULT_MAX_CHUNK_SIZE]) used
     * for memory mapping.
     * @throws IOException if there is a low-level I/O error
     */
    constructor(path: Path, maxChunkSize: Long) : this(
        path,
        FSLockFactory.default,
        maxChunkSize
    )

    /**
     * Configure which files to preload in physical memory upon opening. The default implementation
     * does not preload anything. The behavior is best effort and operating system-dependent.
     *
     * @param preload a [BiPredicate] whose first argument is the file name, and second argument
     * is the [IOContext] used to open the file
     * @see .ALL_FILES
     *
     * @see .NO_FILES
     */
    fun setPreload(preload: (String, IOContext) -> Boolean) {
        this.preload = preload
    }

    /**
     * Configures a grouping function for files that are part of the same logical group. The gathering
     * of files into a logical group is a hint that allows for better handling of resources.
     *
     *
     * By default, grouping is [.GROUP_BY_SEGMENT]. To disable, invoke this method with
     * [.NO_GROUPING].
     *
     * @param groupingFunction a function that accepts a file name and returns an optional group key.
     * If the optional is present, then its value is the logical group to which the file belongs.
     * Otherwise, the file name if not associated with any logical group.
     */
    fun setGroupingFunction(groupingFunction: (String) -> Optional<String>) {
        this.groupingFunction = groupingFunction
    }

    val maxChunkSize: Long
        /**
         * Returns the current mmap chunk size.
         *
         * @see .MMapDirectory
         */
        get() = 1L shl chunkSizePower

    /** Creates an IndexInput for the file with the given name.  */
    override fun openInput(
        name: String,
        context: IOContext
    ): IndexInput {
        ensureOpen()
        ensureCanRead(name)
        val path: Path = directory.resolve(name)
        return PROVIDER.openInput(
            path,
            context,
            chunkSizePower,
            preload(name, context),
            groupingFunction(name),
            attachment
        )
    }

    interface MMapIndexInputProvider<A> {
        @Throws(IOException::class)
        fun openInput(
            path: Path,
            context: IOContext,
            chunkSizePower: Int,
            preload: Boolean,
            group: Optional<String>,
            attachment: A?
        ): IndexInput

        val defaultMaxChunkSize: Long

        fun supportsMadvise(): Boolean

        /** An optional attachment of the provider, that will be passed to openInput.  */
        fun attachment(): A? {
            return null
        }

        fun convertMapFailedIOException(
            ioe: IOException, resourceDescription: String, bufSize: Long
        ): IOException {
            val originalMessage: String?
            val originalCause: Throwable?
            if (ioe.cause is /*OutOfMemory*/Error) {
                // nested OOM confuses users, because it's "incorrect", just print a plain message:
                originalMessage = "Map failed"
                originalCause = null
            } else {
                originalMessage = ioe.message
                originalCause = ioe.cause
            }
            val moreInfo: String
            if (!Constants.JRE_IS_64BIT) {
                moreInfo =
                    "MMapDirectory should only be used on 64bit platforms, because the address space on 32bit operating systems is too small. "
            } else if (Constants.WINDOWS) {
                moreInfo =
                    "Windows is unfortunately very limited on virtual address space. If your index size is several hundred Gigabytes, consider changing to Linux. "
            } else if (Constants.LINUX) {
                moreInfo =
                    "Please review 'ulimit -v', 'ulimit -m' (both should return 'unlimited'), and 'sysctl vm.max_map_count'. "
            } else {
                moreInfo = "Please review 'ulimit -v', 'ulimit -m' (both should return 'unlimited'). "
            }
            val newIoe = IOException(
                ("$originalMessage: $resourceDescription [this may be caused by lack of enough unfragmented virtual address space "
                        + "or too restrictive virtual memory limits enforced by the operating system, "
                        + "preventing us to map a chunk of $bufSize bytes. $moreInfo More information: "
                        + "https://blog.thetaphi.de/2012/07/use-lucenes-mmapdirectory-on-64bit.html]"
                        + ioe.stackTraceToString()
                        ),
                originalCause
            )

            return newIoe
        }
    }

    /**
     * Create a new MMapDirectory for the named location, specifying the maximum chunk size used for
     * memory mapping. The directory is created at the named location if it does not yet exist.
     *
     *
     * Especially on 32 bit platform, the address space can be very fragmented, so large index
     * files cannot be mapped. Using a lower chunk size makes the directory implementation a little
     * bit slower (as the correct chunk may be resolved on lots of seeks) but the chance is higher
     * that mmap does not fail. On 64 bit Java platforms, this parameter should always be large (like
     * 1 GiBytes, or even larger with recent Java versions), as the address space is big enough. If it
     * is larger, fragmentation of address space increases, but number of file handles and mappings is
     * lower for huge installations with many open indexes.
     *
     *
     * **Please note:** The chunk size is always rounded down to a power of 2.
     *
     * @param path the path of the directory
     * @param lockFactory the lock factory to use, or null for the default ([     ]);
     * @param maxChunkSize maximum chunk size (for default see [.DEFAULT_MAX_CHUNK_SIZE]) used
     * for memory mapping.
     * @throws IOException if there is a low-level I/O error
     */
    /**
     * Create a new MMapDirectory for the named location. The directory is created at the named
     * location if it does not yet exist.
     *
     * @param path the path of the directory
     * @param lockFactory the lock factory to use
     * @throws IOException if there is a low-level I/O error
     */
    /**
     * Create a new MMapDirectory for the named location and [FSLockFactory.getDefault]. The
     * directory is created at the named location if it does not yet exist.
     *
     * @param path the path of the directory
     * @throws IOException if there is a low-level I/O error
     */
    init {
        require(maxChunkSize > 0L) { "Maximum chunk size for mmap must be >0" }
        this.chunkSizePower = Long.SIZE_BITS - 1 - Long.numberOfLeadingZeros(maxChunkSize)
        assert((1L shl chunkSizePower) <= maxChunkSize)
        assert((1L shl chunkSizePower) > (maxChunkSize / 2))
    }

    companion object {
        /**
         * Argument for [.setPreload] that configures all files to be preloaded upon
         * opening them.
         */
        val ALL_FILES: (String, IOContext) -> Boolean =
            { string: String, context: IOContext -> true }

        /**
         * Argument for [.setPreload] that configures no files to be preloaded upon
         * opening them.
         */
        val NO_FILES: (String, IOContext) -> Boolean =
            { string: String, context: IOContext -> false }

        /**
         * This sysprop allows to control the total maximum number of mmapped files that can be associated
         * with a single shared [foreign Arena][foreign.Arena]. For example, to set the max
         * number of permits to 256, pass the following on the command line pass `-Dorg.apache.lucene.store.MMapDirectory.sharedArenaMaxPermits=256`. Setting a value of 1
         * associates one file to one shared arena.
         *
         * @lucene.internal
         */
        const val SHARED_ARENA_MAX_PERMITS_SYSPROP: String =
            "org.apache.lucene.store.MMapDirectory.sharedArenaMaxPermits"

        /** Argument for [.setGroupingFunction] that configures no grouping.  */
        val NO_GROUPING: (String) -> Optional<String> =
            { string: String -> Optional.empty<String>() }

        /** Argument for [.setGroupingFunction] that configures grouping by segment.  */
        val GROUP_BY_SEGMENT: (String) -> Optional<String> =
            Function@{ filename: String ->
                if (!IndexFileNames.CODEC_FILE_PATTERN.matches(filename)) {
                    return@Function Optional.empty<String>()
                }
                var groupKey = IndexFileNames.parseSegmentName(filename).substring(1)
                try {
                    // keep the original generation (=0) in base group, later generations in extra group
                    if (IndexFileNames.parseGeneration(filename) > 0) {
                        groupKey += "-g"
                    }
                } catch (unused: NumberFormatException) {
                    // does not confirm to the generation syntax, or trash
                }
                Optional.of<String>(groupKey)
            }

        /**
         * Argument for [.setPreload] that configures files to be preloaded upon
         * opening them if they use the [ReadAdvice.RANDOM_PRELOAD] advice.
         */
        val BASED_ON_LOAD_IO_CONTEXT: (String, IOContext) -> Boolean =
            { string: String, context: IOContext -> context.readAdvice == ReadAdvice.RANDOM_PRELOAD }

        /**
         * Default max chunk size:
         *
         *
         *  * 16 GiBytes for 64 bit JVMs
         *  * 256 MiBytes for 32 bit JVMs
         *
         */
        val DEFAULT_MAX_CHUNK_SIZE: Long

        // visible for tests:
        val PROVIDER: MMapIndexInputProvider<Any>

        private val logger = KotlinLogging.logger {}

        private val sharedArenaMaxPermitsSysprop: Int
            get() {
                var ret = 1024 // default value
                try {
                    val str: String? = System.getProperty(SHARED_ARENA_MAX_PERMITS_SYSPROP)
                    if (str != null) {
                        ret = str.toInt()
                    }
                } catch (ignored: NumberFormatException) {
                    logger.warn { "Cannot read sysprop $SHARED_ARENA_MAX_PERMITS_SYSPROP, so the default value will be used." }

                } catch (ignored: /*Security*/Exception) {
                    logger.warn { "Cannot read sysprop $SHARED_ARENA_MAX_PERMITS_SYSPROP, so the default value will be used." }

                }
                return ret
            }

        private fun <A> lookupProvider(): MMapIndexInputProvider<A> {
            logger.warn { "MMapDirectory uses KMP fallback provider backed by okio positional file reads (non-mmap implementation)." }
            return KmpMMapIndexInputProvider() as MMapIndexInputProvider<A>
        }

        /**
         * Returns true, if MMapDirectory uses the platform's `madvise()` syscall to advise how OS
         * kernel should handle paging after opening a file.
         */
        fun supportsMadvise(): Boolean {
            return PROVIDER.supportsMadvise()
        }

        init {
            PROVIDER = lookupProvider<Any>()
            DEFAULT_MAX_CHUNK_SIZE = PROVIDER.defaultMaxChunkSize
        }

        /**
         * KMP-safe provider that keeps MMapDirectory API behavior but uses regular file reads.
         *
         * It delegates to an IndexInput implementation backed by okio positional reads:
         * [NIOFSDirectory.NIOFSIndexInput].
         */
        private class KmpMMapIndexInputProvider : MMapIndexInputProvider<Any> {
            private val fileSystem: FileSystem = FileSystem.SYSTEM

            override fun openInput(
                path: Path,
                context: IOContext,
                chunkSizePower: Int,
                preload: Boolean,
                group: Optional<String>,
                attachment: Any?
            ): IndexInput {
                // chunkSizePower/preload/group/attachment are mmap-specific hints in Java Lucene.
                // The KMP fallback keeps functional behavior with positional file reads.
                val handle: FileHandle = fileSystem.openReadOnly(path)
                var success = false
                try {
                    val indexInput = NIOFSDirectory.NIOFSIndexInput(
                        "MMapIndexInput(path=\"$path\") [kmp-fallback]",
                        handle,
                        context
                    )
                    success = true
                    return indexInput
                } finally {
                    if (!success) {
                        org.gnit.lucenekmp.util.IOUtils.closeWhileHandlingException(handle)
                    }
                }
            }

            override val defaultMaxChunkSize: Long =
                if (Constants.JRE_IS_64BIT) 1L shl 34 else 1L shl 28

            override fun supportsMadvise(): Boolean = false
        }
    }
}
