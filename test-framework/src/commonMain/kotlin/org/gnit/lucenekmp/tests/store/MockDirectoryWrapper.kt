package org.gnit.lucenekmp.tests.store

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import okio.FileNotFoundException
import okio.IOException
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.NoDeletionPolicy
import org.gnit.lucenekmp.index.SegmentInfos
import org.gnit.lucenekmp.jdkport.AccessDeniedException
import org.gnit.lucenekmp.jdkport.AtomicInteger
import org.gnit.lucenekmp.jdkport.FileAlreadyExistsException
import org.gnit.lucenekmp.jdkport.NoSuchFileException
import org.gnit.lucenekmp.jdkport.TreeSet
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.store.AlreadyClosedException
import org.gnit.lucenekmp.store.ChecksumIndexInput
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.store.Lock
import org.gnit.lucenekmp.store.ReadAdvice
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.tests.util.ThrottledIndexOutput
import org.gnit.lucenekmp.util.CollectionUtil
import org.gnit.lucenekmp.util.IOUtils
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.min
import kotlin.random.Random

/**
 * This is a Directory Wrapper that adds methods intended to be used only by unit tests. It also
 * adds a number of features useful for testing:
 *
 *
 *  * Instances created by [LuceneTestCase.newDirectory] are tracked to ensure they are
 * closed by the test.
 *  * When a MockDirectoryWrapper is closed, it will throw an exception if it has any open files
 * against it (with a stacktrace indicating where they were opened from).
 *  * When a MockDirectoryWrapper is closed, it runs CheckIndex to test if the index was
 * corrupted.
 *  * MockDirectoryWrapper simulates some "features" of Windows, such as refusing to write/delete
 * to open files.
 *
 */
class MockDirectoryWrapper(random: Random, delegate: Directory) : BaseDirectoryWrapper(delegate) {
    private val logger = KotlinLogging.logger {}
    var maxSizeInBytes: Long = 0

    /** Returns the peek actual storage used (bytes) in this directory.  */
    // Max actual bytes used. This is set by MockRAMOutputStream:
    var maxUsedSizeInBytes: Long = 0

    /**
     * If 0.0, no exceptions will be thrown. Else this should be a double 0.0 - 1.0. We will randomly
     * throw an IOException on the first write to an OutputStream based on this probability.
     */
    var randomIOExceptionRate: Double = 0.0

    /**
     * If 0.0, no exceptions will be thrown during openInput and createOutput. Else this should be a
     * double 0.0 - 1.0 and we will randomly throw an IOException in openInput and createOutput with
     * this probability.
     */
    var randomIOExceptionRateOnOpen: Double = 0.0
    var randomState: Random

    /** Trip a test assert if there is an attempt to delete an open file.  */
    var assertNoDeleteOpenFile: Boolean = false
    var trackDiskUsage: Boolean = false
    var useSlowOpenClosers: Boolean = LuceneTestCase.TEST_NIGHTLY
    var allowRandomFileNotFoundException: Boolean = true
    var allowReadingFilesStillOpenForWrite: Boolean = false
    private var unSyncedFiles: MutableSet<String>? = null
    private var createdFiles: MutableSet<String>? = null
    private var openFilesForWrite: MutableSet<String> = mutableSetOf()
    private val openLocksMutex = Mutex()
    private val openLocks: MutableMap<String, RuntimeException> = mutableMapOf()
    private val openFilesMutex = Mutex()

    @Volatile
    var crashed: Boolean = false
    private val throttledOutput: ThrottledIndexOutput
    private var throttling: Throttling =
        if (LuceneTestCase.TEST_NIGHTLY) Throttling.SOMETIMES else Throttling.NEVER

    // for testing
    var alwaysCorrupt: Boolean = false

    @OptIn(ExperimentalAtomicApi::class)
    val inputCloneCount: AtomicInteger = AtomicInteger(0)

    // use this for tracking files for crash.
    // additionally: provides debugging information in case you leave one open
    private val openFileHandles: MutableMap<AutoCloseable, Exception> = mutableMapOf()
        /*java.util.Collections.synchronizedMap<AutoCloseable, Exception>(java.util.IdentityHashMap<AutoCloseable, Exception>())*/

    // NOTE: we cannot initialize the Map here due to the
    // order in which our constructor actually does this
    // member initialization vs when it calls super.  It seems
    // like super is called, then our members are initialized:
    private var openFiles: MutableMap<String, Int>? = null

    // Only tracked if noDeleteOpenFile is true: if an attempt
    // is made to delete an open file, we enroll it here.
    private var openFilesDeleted: MutableSet<String>? = null

    private fun <T> withOpenFilesLock(action: () -> T): T =
        runBlocking { openFilesMutex.withLock { action() } }

    /*@Synchronized*/
    private fun init() {
        withOpenFilesLock {
            if (openFiles == null) {
                openFiles = mutableMapOf()
                openFilesDeleted = mutableSetOf()
            }

            if (createdFiles == null) createdFiles = mutableSetOf()
            if (unSyncedFiles == null) unSyncedFiles = mutableSetOf()
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun getInputCloneCount(): Int {
        return inputCloneCount.load()
    }

    var verboseClone: Boolean = false

    /**
     * If set to true, we print a fake exception with filename and stacktrace on every indexinput
     * clone()
     */
    /*fun setVerboseClone(v: Boolean) {
        verboseClone = v
    }*/

    /*fun setTrackDiskUsage(v: Boolean) {
        trackDiskUsage = v
    }*/

    /**
     * If set to true (the default), when we throw random IOException on openInput or createOutput, we
     * may sometimes throw FileNotFoundException or NoSuchFileException.
     */
    /*fun setAllowRandomFileNotFoundException(value: Boolean) {
        allowRandomFileNotFoundException = value
    }*/

    /** If set to true, you can open an inputstream on a file that is still open for writes.  */
    /*fun setAllowReadingFilesStillOpenForWrite(value: Boolean) {
        allowReadingFilesStillOpenForWrite = value
    }*/

    /**
     * Enum for controlling hard disk throttling. Set via [ #setThrottling(Throttling)][MockDirectoryWrapper]
     *
     *
     * WARNING: can make tests very slow.
     */
    enum class Throttling {
        /** always emulate a slow hard disk. could be very slow!  */
        ALWAYS,

        /** sometimes (0.5% of the time) emulate a slow hard disk.  */
        SOMETIMES,

        /** never throttle output  */
        NEVER
    }

    fun setThrottling(throttling: Throttling) {
        this.throttling = throttling
    }

    /**
     * Add a rare small sleep to catch race conditions in open/close
     *
     *
     * You can enable this if you need it.
     */
    /*fun setUseSlowOpenClosers(v: Boolean) {
        useSlowOpenClosers = v
    }*/

    /*@Synchronized*/
    @Throws(IOException::class)
    override fun sync(names: MutableCollection<String>) {
        maybeYield()
        maybeThrowDeterministicException()
        if (crashed) {
            throw IOException("cannot sync after crash")
        }
        // always pass thru fsync, directories rely on this.
        // 90% of time, we use DisableFsyncFS which omits the real calls.
        for (name in names) {
            // randomly fail with IOE on any file
            maybeThrowIOException(name)
            `in`.sync(mutableSetOf(name))
            unSyncedFiles!!.remove(name)
        }
    }

    /*@Synchronized*/
    @Throws(IOException::class)
    override fun rename(source: String, dest: String) {
        maybeYield()
        maybeThrowDeterministicException()

        if (crashed) {
            throw IOException("cannot rename after crash")
        }

        withOpenFilesLock {
            if (openFiles!!.containsKey(source) && assertNoDeleteOpenFile) {
                throw fillOpenTrace(
                    AssertionError(
                        "MockDirectoryWrapper: source file \"$source\" is still open: cannot rename"
                    ),
                    source,
                    true
                )
            }

            if (openFiles!!.containsKey(dest) && assertNoDeleteOpenFile) {
                throw fillOpenTrace(
                    AssertionError(
                        "MockDirectoryWrapper: dest file \"$dest\" is still open: cannot rename"
                    ),
                    dest,
                    true
                )
            }
        }

        var success = false
        try {
            `in`.rename(source, dest)
            success = true
        } finally {
            if (success) {
                // we don't do this stuff with lucene's commit, but it's just for completeness
                if (unSyncedFiles!!.contains(source)) {
                    unSyncedFiles!!.remove(source)
                    unSyncedFiles!!.add(dest)
                }
                withOpenFilesLock { openFilesDeleted!!.remove(source) }
                createdFiles!!.remove(source)
                createdFiles!!.add(dest)
            }
        }
    }

    /*@Synchronized*/
    @Throws(IOException::class)
    override fun syncMetaData() {
        maybeYield()
        maybeThrowDeterministicException()
        if (crashed) {
            throw IOException("cannot sync metadata after crash")
        }
        `in`.syncMetaData()
    }

    /*@Synchronized*/
    @Throws(IOException::class)
    fun sizeInBytes(): Long {
        var size: Long = 0
        for (file in `in`.listAll()) {
            // hack 2: see TODO in ExtrasFS (ideally it would always return 0 byte
            // size for extras it creates, even though the size of non-regular files is not defined)
            if (!file.startsWith("extra")) {
                size += `in`.fileLength(file)
            }
        }
        return size
    }

    /*@Synchronized*/
    @Throws(IOException::class)
    fun corruptUnknownFiles() {
        if (LuceneTestCase.VERBOSE) {
            println("MDW: corrupt unknown files")
        }
        val knownFiles: MutableSet<String> = mutableSetOf()
        for (fileName in listAll()) {
            if (fileName.startsWith(IndexFileNames.SEGMENTS)) {
                if (LuceneTestCase.VERBOSE) {
                    println("MDW: read $fileName to gather files it references")
                }
                val infos: SegmentInfos
                try {
                    infos = SegmentInfos.readCommit(this, fileName)
                } catch (ioe: IOException) {
                    if (LuceneTestCase.VERBOSE) {
                        println(
                            ("MDW: exception reading segment infos "
                                    + fileName
                                    + "; files: "
                                    + listAll().contentToString())
                        )
                    }
                    throw ioe
                }
                knownFiles.addAll(infos.files(true))
            }
        }

        /*val toCorrupt: MutableSet<String> = mutableSetOf()
        val m: java.util.regex.Matcher =
            IndexFileNames.CODEC_FILE_PATTERN.matcher("")
        for (fileName in listAll()) {
            m.reset(fileName)
            if (knownFiles.contains(fileName) == false && fileName.endsWith("write.lock") == false && (m.matches() || fileName.startsWith(
                    IndexFileNames.PENDING_SEGMENTS
                ))
            ) {
                toCorrupt.add(fileName)
            }
        }*/
        val toCorrupt: MutableSet<String> = mutableSetOf()
        val codecRegex = Regex(IndexFileNames.CODEC_FILE_PATTERN.pattern)
        for (fileName in listAll()) {
            if (!knownFiles.contains(fileName)
                && !fileName.endsWith("write.lock")
                && (codecRegex.matches(fileName) || fileName.startsWith(IndexFileNames.PENDING_SEGMENTS))
            ) {
                toCorrupt.add(fileName)
            }
        }

        corruptFiles(toCorrupt)
    }

    /*@Synchronized*/
    @Throws(IOException::class)
    fun corruptFiles(files: MutableCollection<String>) {
        // TODO too difficult for now, implement if possible later

        /*val disabled: Boolean = TestUtil.disableVirusChecker(`in`)
        try {
            _corruptFiles(files)
        } finally {
            if (disabled) {
                TestUtil.enableVirusChecker(`in`)
            }
        }*/
    }

    /*@Synchronized*/
    @Throws(IOException::class)
    private fun _corruptFiles(files: MutableCollection<String>) {
        // TODO: we should also mess with any recent file renames, file deletions, if
        // syncMetaData was not called!!

        // Must make a copy because we change the incoming unsyncedFiles
        // when we create temp files, delete, etc., below:

        val filesToCorrupt: MutableList<String> = files.toMutableList() /*java.util.ArrayList<String>(files)*/
        // sort the files otherwise we have reproducibility issues
        // across JVMs if the incoming collection is a hashSet etc.
        CollectionUtil.timSort(filesToCorrupt)
        for (name in filesToCorrupt) {
            var damage: Int = randomState.nextInt(6)
            if (alwaysCorrupt && damage == 3) {
                damage = 4
            }
            var action: String? = null

            when (damage) {
                0 -> {
                    action = "deleted"
                    deleteFile(name)
                }

                1 -> {
                    action = "zeroed"
                    // Zero out file entirely
                    val length: Long
                    try {
                        length = fileLength(name)
                    } catch (ioe: IOException) {
                        throw RuntimeException(
                            "hit unexpected IOException while trying to corrupt file $name", ioe
                        )
                    }

                    // Delete original and write zeros back:
                    deleteFile(name)

                    val zeroes = ByteArray(256)
                    var upto: Long = 0
                    try {
                        `in`.createOutput(
                            name,
                            LuceneTestCase.newIOContext(randomState)
                        ).use { out ->
                            while (upto < length) {
                                val limit = min(length - upto, zeroes.size.toLong()).toInt()
                                out.writeBytes(zeroes, 0, limit)
                                upto += limit.toLong()
                            }
                        }
                    } catch (ioe: IOException) {
                        throw RuntimeException(
                            "hit unexpected IOException while trying to corrupt file $name", ioe
                        )
                    }
                }

                2 -> {
                    action = "partially truncated"

                    // Partially Truncate the file:

                    // First, make temp file and copy only half this
                    // file over:
                    var tempFileName: String? = null
                    try {
                        `in`.createTempOutput(
                            "name",
                            "mdw_corrupt",
                            LuceneTestCase.newIOContext(randomState)
                        ).use { tempOut ->
                            `in`.openInput(
                                name,
                                LuceneTestCase.newIOContext(randomState)
                            ).use { ii ->
                                tempFileName = tempOut.name
                                tempOut.copyBytes(ii, ii.length() / 2)
                            }
                        }
                    } catch (ioe: IOException) {
                        throw RuntimeException(
                            "hit unexpected IOException while trying to corrupt file $name", ioe
                        )
                    }

                    // Delete original and copy bytes back:
                    deleteFile(name)

                    try {
                        `in`.createOutput(
                            name,
                            LuceneTestCase.newIOContext(randomState)
                        ).use { out ->
                            `in`.openInput(
                                tempFileName!!,
                                LuceneTestCase.newIOContext(randomState)
                            ).use { ii ->
                                out.copyBytes(ii, ii.length())
                            }
                        }
                    } catch (ioe: IOException) {
                        throw RuntimeException(
                            "hit unexpected IOException while trying to corrupt file $name", ioe
                        )
                    }
                    deleteFile(tempFileName!!)
                }

                3 ->           // The file survived intact:
                    action = "didn't change"

                4 ->           // Corrupt one bit randomly in the file:
                {
                    var tempFileName: String? = null
                    try {
                        `in`.createTempOutput(
                            "name",
                            "mdw_corrupt",
                            LuceneTestCase.newIOContext(randomState)
                        ).use { tempOut ->
                            `in`.openInput(
                                name,
                                LuceneTestCase.newIOContext(randomState)
                            ).use { ii ->
                                tempFileName = tempOut.name
                                if (ii.length() > 0) {
                                    // Copy first part unchanged:
                                    val byteToCorrupt =
                                        (randomState.nextDouble() * ii.length()).toLong()
                                    if (byteToCorrupt > 0) {
                                        tempOut.copyBytes(ii, byteToCorrupt)
                                    }

                                    // Randomly flip one bit from this byte:
                                    var b: Byte = ii.readByte()
                                    val bitToFlip: Int = randomState.nextInt(8)
                                    b = (b.toInt() xor (1 shl bitToFlip)).toByte()
                                    tempOut.writeByte(b)

                                    action =
                                        ("flip bit "
                                                + bitToFlip
                                                + " of byte "
                                                + byteToCorrupt
                                                + " out of "
                                                + ii.length()
                                                + " bytes")

                                    // Copy last part unchanged:
                                    val bytesLeft: Long = ii.length() - byteToCorrupt - 1
                                    if (bytesLeft > 0) {
                                        tempOut.copyBytes(ii, bytesLeft)
                                    }
                                } else {
                                    action = "didn't change"
                                }
                            }
                        }
                    } catch (ioe: IOException) {
                        throw RuntimeException(
                            "hit unexpected IOException while trying to corrupt file $name", ioe
                        )
                    }

                    // Delete original and copy bytes back:
                    deleteFile(name)

                    try {
                        `in`.createOutput(
                            name,
                            LuceneTestCase.newIOContext(randomState)
                        ).use { out ->
                            `in`.openInput(
                                tempFileName!!,
                                LuceneTestCase.newIOContext(randomState)
                            ).use { ii ->
                                out.copyBytes(ii, ii.length())
                            }
                        }
                    } catch (ioe: IOException) {
                        throw RuntimeException(
                            "hit unexpected IOException while trying to corrupt file $name", ioe
                        )
                    }

                    deleteFile(tempFileName!!)
                }

                5 -> {
                    action = "fully truncated"
                    // Totally truncate the file to zero bytes
                    deleteFile(name)

                    try {
                        `in`.createOutput(
                            name,
                            LuceneTestCase.newIOContext(randomState)
                        ).use { out ->
                            out.filePointer // just fake access to prevent compiler warning
                        }
                    } catch (ioe: IOException) {
                        throw RuntimeException(
                            "hit unexpected IOException while trying to corrupt file $name", ioe
                        )
                    }
                }

                else -> throw AssertionError()
            }

            if (LuceneTestCase.VERBOSE) {
                println("MockDirectoryWrapper: $action unsynced file: $name")
            }
        }
    }

    /** Simulates a crash of OS or machine by overwriting unsynced files.  */
    /*@Synchronized*/
    @Throws(IOException::class)
    fun crash() {
        openFiles = mutableMapOf()
        openFilesForWrite = mutableSetOf()
        openFilesDeleted = mutableSetOf()
        // first force-close all files, so we can corrupt on windows etc.
        // clone the file map, as these guys want to remove themselves on close.
        val m: MutableMap<AutoCloseable, Exception> = openFileHandles.toMutableMap()
            /*java.util.IdentityHashMap<AutoCloseable, Exception>(openFileHandles)*/
        for (f in m.keys) {
            try {
                f.close()
            } catch (ignored: Exception) {
            }
        }
        corruptFiles(unSyncedFiles!!)
        crashed = true
        unSyncedFiles = mutableSetOf()
    }

    /*@Synchronized*/
    fun clearCrash() {
        crashed = false
        runBlocking {
            openLocksMutex.withLock {
                openLocks.clear()
            }
        }
    }

    @Throws(IOException::class)
    fun resetMaxUsedSizeInBytes() {
        this.maxUsedSizeInBytes = sizeInBytes()
    }

    @Throws(IOException::class)
    fun maybeThrowIOException(message: String) {
        if (randomState.nextDouble() < randomIOExceptionRate) {
            val ioe =
                IOException("a random IOException" + (if (message == null) "" else " ($message)"))
            if (LuceneTestCase.VERBOSE) {
                val jobName = runBlocking { currentCoroutineContext()[Job]?.toString() ?: "unknown" }
                println(
                    (jobName
                            + ": MockDirectoryWrapper: now throw random exception"
                            + (if (message == null) "" else " ($message)"))
                )
                ioe.printStackTrace(/*java.lang.System.out*/)
            }
            throw ioe
        }
    }

    @Throws(IOException::class)
    fun maybeThrowIOExceptionOnOpen(name: String) {
        if (randomState.nextDouble() < randomIOExceptionRateOnOpen) {
            if (LuceneTestCase.VERBOSE) {
                val jobName = runBlocking { currentCoroutineContext()[Job]?.toString() ?: "unknown" }
                println(
                    (jobName
                            + ": MockDirectoryWrapper: now throw random exception during open file="
                            + name)
                )
                Throwable().printStackTrace(/*java.lang.System.out*/)
            }
            if (allowRandomFileNotFoundException == false || randomState.nextBoolean()) {
                throw IOException("a random IOException ($name)")
            } else {
                throw if (randomState.nextBoolean())
                    FileNotFoundException("a random IOException ($name)")
                else
                    NoSuchFileException("a random IOException ($name)")
            }
        }
    }

    /*@get:Synchronized*/
    val fileHandleCount: Long
        /** returns current open file handle count  */
        get() = openFileHandles.size.toLong()

    /*@Synchronized*/
    @Throws(IOException::class)
    override fun deleteFile(name: String) {
        maybeYield()

        maybeThrowDeterministicException()

        if (crashed) {
            throw IOException("cannot delete after crash")
        }

        withOpenFilesLock {
            if (openFiles!!.containsKey(name)) {
                openFilesDeleted!!.add(name)
                if (assertNoDeleteOpenFile) {
                    throw fillOpenTrace(
                        IOException(
                            "MockDirectoryWrapper: file \"$name\" is still open: cannot delete"
                        ),
                        name,
                        true
                    )
                }
            } else {
                openFilesDeleted!!.remove(name)
            }
        }

        unSyncedFiles!!.remove(name)
        `in`.deleteFile(name)
        createdFiles!!.remove(name)
    }

    // sets the cause of the incoming ioe to be the stack
    // trace when the offending file name was opened
    /*@Synchronized*/
    private fun <T : Throwable> fillOpenTrace(t: T, name: String, input: Boolean): T {
        var thrown: T = t

        for (ent in openFileHandles.entries) {
            if (input
                && ent.key is MockIndexInputWrapper
                && (ent.key as MockIndexInputWrapper).name == name
            ) {
                thrown = t.cause!! as T
                break
            } else if (!input && ent.key is MockIndexOutputWrapper
                && (ent.key as MockIndexOutputWrapper).name == name
            ) {
                thrown = ent.value as T
                break
            }
        }
        return thrown
    }

    private fun maybeYield() {
        if (randomState.nextBoolean()) {
            runBlocking { yield() }
        }
    }

    /*@get:Synchronized*/
    val openDeletedFiles: MutableSet<String>
        get() = /*java.util.HashSet<String>(openFilesDeleted)*/ openFilesDeleted?.toMutableSet()
            ?: mutableSetOf()

    private var failOnCreateOutput = true

    fun setFailOnCreateOutput(v: Boolean) {
        failOnCreateOutput = v
    }

    /*@Synchronized*/
    @Throws(IOException::class)
    override fun createOutput(
        name: String,
        context: IOContext
    ): IndexOutput {
        maybeThrowDeterministicException()
        maybeThrowIOExceptionOnOpen(name)
        maybeYield()
        if (failOnCreateOutput) {
            maybeThrowDeterministicException()
        }
        if (crashed) {
            throw IOException("cannot createOutput after crash")
        }
        init()

        if (createdFiles!!.contains(name)) {
            throw FileAlreadyExistsException("File \"$name\" was already written to.")
        }

        if (assertNoDeleteOpenFile && withOpenFilesLock { openFiles!!.containsKey(name) }) {
            throw AssertionError(
                "MockDirectoryWrapper: file \"$name\" is still open: cannot overwrite"
            )
        }

        unSyncedFiles!!.add(name)
        createdFiles!!.add(name)

        // System.out.println(Thread.currentThread().name + ": MDW: create " + name);
        val delegateOutput: IndexOutput =
            `in`.createOutput(
                name,
                LuceneTestCase.newIOContext(randomState, context)
            )
        val io: IndexOutput =
            MockIndexOutputWrapper(this, delegateOutput, name)
        addFileHandle(io, name, Handle.Output)
        withOpenFilesLock { openFilesForWrite.add(name) }
        return maybeThrottle(name, io)
    }

    private fun maybeThrottle(
        name: String,
        output: IndexOutput
    ): IndexOutput {
        // throttling REALLY slows down tests, so don't do it very often for SOMETIMES.
        if (throttling == Throttling.ALWAYS
            || (throttling == Throttling.SOMETIMES && randomState.nextInt(200) == 0)
        ) {
            if (LuceneTestCase.VERBOSE) {
                println("MockDirectoryWrapper: throttling indexOutput ($name)")
            }
            return throttledOutput.newFromDelegate(output)
        } else {
            return output
        }
    }

    /*@Synchronized*/
    @Throws(IOException::class)
    override fun createTempOutput(
        prefix: String,
        suffix: String,
        context: IOContext
    ): IndexOutput {
        maybeThrowDeterministicException()
        maybeThrowIOExceptionOnOpen("temp: prefix=$prefix suffix=$suffix")
        maybeYield()
        if (failOnCreateOutput) {
            maybeThrowDeterministicException()
        }
        if (crashed) {
            throw IOException("cannot createTempOutput after crash")
        }
        init()

        val delegateOutput: IndexOutput =
            `in`.createTempOutput(
                prefix,
                suffix,
                LuceneTestCase.newIOContext(randomState, context)
            )
        val name: String = delegateOutput.name!!
        check(
            name.lowercase().endsWith(".tmp") != false
        ) { "wrapped directory failed to use .tmp extension: got: $name" }

        unSyncedFiles!!.add(name)
        createdFiles!!.add(name)
        val io: IndexOutput =
            MockIndexOutputWrapper(this, delegateOutput, name)
        addFileHandle(io, name, Handle.Output)
        withOpenFilesLock { openFilesForWrite.add(name) }

        return maybeThrottle(name, io)
    }

    enum class Handle {
        Input,
        Output,
        Slice
    }

    /*@Synchronized*/
    fun addFileHandle(c: AutoCloseable, name: String, handle: Handle) {
        withOpenFilesLock {
            var v = openFiles!![name]
            if (v != null) {
                v += 1
                openFiles!![name] = v
            } else {
                openFiles!![name] = 1
            }

            openFileHandles[c] = RuntimeException("unclosed Index" + handle.name + ": " + name)
            if (name.endsWith(".cfs")) {
                logger.debug { "MDW addFileHandle: name=$name handle=$handle count=${openFiles!![name]}" }
                if ((openFiles!![name] ?: 0) > 1) {
                    logger.debug {
                        "MDW addFileHandle duplicate-open: name=$name handle=$handle count=${openFiles!![name]}\n${RuntimeException("cfs duplicate open stack").stackTraceToString()}"
                    }
                }
            }
        }
    }

    private var failOnOpenInput = true

    fun setFailOnOpenInput(v: Boolean) {
        failOnOpenInput = v
    }

    /*@Synchronized*/
    @Throws(IOException::class)
    override fun openInput(
        name: String,
        context: IOContext
    ): IndexInput {
        var context: IOContext = context
        maybeThrowDeterministicException()
        maybeThrowIOExceptionOnOpen(name)
        maybeYield()
        if (failOnOpenInput) {
            maybeThrowDeterministicException()
        }
        if (!LuceneTestCase.slowFileExists(`in`, name)) {
            throw if (randomState.nextBoolean())
                FileNotFoundException("$name in dir=$`in`")
            else
                NoSuchFileException("$name in dir=$`in`")
        }

        // cannot open a file for input if it's still open for output.
        withOpenFilesLock {
            if (!allowReadingFilesStillOpenForWrite && openFilesForWrite.contains(name)) {
                throw fillOpenTrace(
                    AccessDeniedException(
                        "MockDirectoryWrapper: file \"$name\" is still open for writing"
                    ),
                    name,
                    false
                )
            }
        }

        // record the read advice before randomizing the context
        val readAdvice: ReadAdvice = context.readAdvice
        context = LuceneTestCase.newIOContext(randomState, context)
        val confined = context === IOContext.READONCE
        if (name.startsWith(IndexFileNames.SEGMENTS) && confined == false) {
            throw RuntimeException(
                ("MockDirectoryWrapper: opening segments file ["
                        + name
                        + "] with a non-READONCE context["
                        + context
                        + "]")
            )
        }
        val delegateInput: IndexInput = `in`.openInput(name, context)

        val ii: IndexInput
        val randomInt: Int = randomState.nextInt(500)
        if (useSlowOpenClosers && randomInt == 0) {
            if (LuceneTestCase.VERBOSE) {
                println(
                    "MockDirectoryWrapper: using SlowClosingMockIndexInputWrapper for file $name"
                )
            }
            ii = SlowClosingMockIndexInputWrapper(this, name, delegateInput, readAdvice, confined)
        } else if (useSlowOpenClosers && randomInt == 1) {
            if (LuceneTestCase.VERBOSE) {
                println(
                    "MockDirectoryWrapper: using SlowOpeningMockIndexInputWrapper for file $name"
                )
            }
            ii = SlowOpeningMockIndexInputWrapper(this, name, delegateInput, readAdvice, confined)
        } else {
            ii = MockIndexInputWrapper(this, name, delegateInput, null, readAdvice, confined)
        }
        addFileHandle(ii, name, Handle.Input)
        return ii
    }

    // NOTE: This is off by default; see LUCENE-5574
    @Volatile
    private var assertNoUnreferencedFilesOnClose = false

    fun setAssertNoUnrefencedFilesOnClose(v: Boolean) {
        assertNoUnreferencedFilesOnClose = v
    }

    /*@Synchronized*/
    override fun close() {
        if (!isOpen()) {
            `in`.close() // but call it again on our wrapped dir
            return
        }

        var success = false
        try {
            // files that we tried to delete, but couldn't because readers were open.
            // all that matters is that we tried! (they will eventually go away)
            //   still open when we tried to delete
            maybeYield()
            var openFilesSnapshot: Map<String, Int> = emptyMap()
            var openFileHandlesSnapshot: Map<AutoCloseable, Exception> = emptyMap()
            withOpenFilesLock {
                if (openFiles == null) {
                    openFiles = mutableMapOf()
                    openFilesDeleted = mutableSetOf()
                }
                openFilesSnapshot = openFiles!!.toMap()
                openFileHandlesSnapshot = openFileHandles.toMap()
            }
            if (openFilesSnapshot.isNotEmpty()) {
                // print the first one as it's very verbose otherwise
                var cause: Exception? = null
                val stacktraces: Iterator<Exception> =
                    openFileHandlesSnapshot.values.iterator()
                if (stacktraces.hasNext()) {
                    cause = stacktraces.next()
                }
                // RuntimeException instead of IOException because
                // super() does not throw IOException currently:
                throw RuntimeException(
                    ("MockDirectoryWrapper: cannot close: there are still "
                            + openFilesSnapshot.size
                            + " open files: "
                            + openFilesSnapshot),
                    cause
                )
            }
            val openLocksSnapshot: Map<String, RuntimeException> = runBlocking {
                openLocksMutex.withLock {
                    openLocks.toMap()
                }
            }
            if (openLocksSnapshot.isNotEmpty()) {
                var cause: Exception? = null
                val stacktraces: Iterator<RuntimeException> =
                    openLocksSnapshot.values.iterator()
                if (stacktraces.hasNext()) {
                    cause = stacktraces.next()
                }
                throw RuntimeException(
                    "MockDirectoryWrapper: cannot close: there are still open locks: $openLocksSnapshot",
                    cause
                )
            }
            randomIOExceptionRate = 0.0
            randomIOExceptionRateOnOpen = 0.0

            if ((checkIndexOnClose || assertNoUnreferencedFilesOnClose)
                && DirectoryReader.indexExists(this)
            ) {
                if (checkIndexOnClose) {
                    if (LuceneTestCase.VERBOSE) {
                        println("\nNOTE: MockDirectoryWrapper: now crush")
                    }
                    crash() // corrupt any unsynced-files
                    if (LuceneTestCase.VERBOSE) {
                        println("\nNOTE: MockDirectoryWrapper: now run CheckIndex")
                    }

                    // Methods in MockDirectoryWrapper hold locks on this, which will cause deadlock when
                    // TestUtil#checkIndex checks segment concurrently using another thread, but making
                    // call back to synchronized methods such as MockDirectoryWrapper#fileLength.
                    // Hence passing concurrent = false to this method to turn off concurrent checks.
                    // Methods in MockDirectoryWrapper hold locks on this, which will cause deadlock when
                    // TestUtil#checkIndex checks segment concurrently using another thread, but making
                    // call back to synchronized methods such as MockDirectoryWrapper#fileLength.
                    // Hence passing concurrent = false to this method to turn off concurrent checks.
                    val checkLevel = if (levelForCheckOnClose < 1) 1 else levelForCheckOnClose
                    TestUtil.checkIndex(
                        this,
                        checkLevel,
                        true,
                        false,
                        null
                    )
                }

                // TODO: factor this out / share w/ TestIW.assertNoUnreferencedFiles
                if (assertNoUnreferencedFilesOnClose) {
                    if (LuceneTestCase.VERBOSE) {
                        println("MDW: now assert no unref'd files at close")
                    }
                    // now look for unreferenced files: discount ones that we tried to delete but could not
                    val allFiles: MutableSet<String> = listAll().toMutableSet()
                        /*java.util.HashSet<String>(Arrays.asList<String>(*listAll()))*/
                    var startFiles: Array<String> = allFiles.toTypedArray<String>()
                    val iwc = IndexWriterConfig(/*null*/) //TODO not sure if it works with empty constructor
                    iwc.setIndexDeletionPolicy(NoDeletionPolicy.INSTANCE)

                    // We must do this before opening writer otherwise writer will be angry if there are
                    // pending deletions:
                    // TODO: disableVirusChecker is not ported; no-op for now.

                    IndexWriter(`in`, iwc).rollback()
                    var endFiles: Array<String> = `in`.listAll()

                    val startSet: MutableSet<String> =
                        TreeSet(/*Arrays.asList<String>(*)*/ startFiles.toMutableList() )
                    val endSet: MutableSet<String> =
                        TreeSet(/*Arrays.asList<String>(*endFiles)*/ endFiles.toMutableList() )

                    startFiles = startSet.toTypedArray<String>()
                    endFiles = endSet.toTypedArray<String>()

                    if (!startFiles.contentEquals(endFiles)) {
                        val removed: MutableList<String> = mutableListOf()
                        for (fileName in startFiles) {
                            if (!endSet.contains(fileName)) {
                                removed.add(fileName)
                            }
                        }

                        val added: MutableList<String> = mutableListOf()
                        for (fileName in endFiles) {
                            if (!startSet.contains(fileName)) {
                                added.add(fileName)
                            }
                        }

                        var extras: String
                        if (removed.isNotEmpty()) {
                            extras = "\n\nThese files were removed: $removed"
                        } else {
                            extras = ""
                        }

                        if (added.isNotEmpty()) {
                            extras += "\n\nThese files were added (waaaaaaaaaat!): $added"
                        }

                        throw RuntimeException(
                            ("unreferenced files: before delete:\n    "
                                    + startFiles.contentToString() + "\n  after delete:\n    "
                                    + endFiles.contentToString() + extras)
                        )
                    }

                    val ir1: DirectoryReader =
                        DirectoryReader.open(this)
                    val numDocs1: Int = ir1.numDocs()
                    ir1.close()
                    // Don't commit on close, so that no merges will be scheduled.
                    IndexWriter(
                        this,
                        IndexWriterConfig(/*null*/) //TODO not sure if it works with empty constructor
                            .setCommitOnClose(false)
                    ).close()
                    val ir2: DirectoryReader =
                        DirectoryReader.open(this)
                    val numDocs2: Int = ir2.numDocs()
                    ir2.close()
                    assert(
                        numDocs1 == numDocs2
                    ) {
                        ("numDocs changed after opening/closing IW: before="
                                + numDocs1
                                + " after="
                                + numDocs2)
                    }
                }
            }
            success = true
        } finally {
            if (success) {
                IOUtils.close(`in`)
            } else {
                IOUtils.closeWhileHandlingException(`in`)
            }
        }
    }

    /*@Synchronized*/
    fun removeOpenFile(c: AutoCloseable, name: String) {
        withOpenFilesLock {
            removeOpenFileUnsafe(c, name)
        }
    }

    private fun removeOpenFileUnsafe(c: AutoCloseable, name: String) {
        var v = openFiles!![name]
        // Could be null when crash() was called
        if (v != null) {
            if (v == 1) {
                openFiles!!.remove(name)
            } else {
                v -= 1
                openFiles!![name] = v
            }
        }

        openFileHandles.remove(c)
        if (name.endsWith(".cfs")) {
            val remaining = openFiles!![name] ?: 0
            logger.debug { "MDW removeOpenFile: name=$name remaining=$remaining" }
            if (remaining > 0) {
                val traces =
                    openFileHandles.values
                        .filter { it.message?.contains(name) == true }
                        .joinToString(separator = "\n\n") { it.stackTraceToString() }
                logger.debug {
                    "MDW removeOpenFile outstanding-handles: name=$name remaining=$remaining\n$traces"
                }
            }
        }
    }

    /*@Synchronized*/
    fun removeIndexOutput(out: IndexOutput, name: String) {
        withOpenFilesLock {
            openFilesForWrite.remove(name)
            removeOpenFileUnsafe(out, name)
        }
    }

    /*@Synchronized*/
    fun removeIndexInput(`in`: IndexInput, name: String) {
        withOpenFilesLock {
            removeOpenFileUnsafe(`in`, name)
        }
    }

    /**
     * Objects that represent fail-able conditions. Objects of a derived class are created and
     * registered with the mock directory. After register, each object will be invoked once for each
     * first write of a file, giving the object a chance to throw an IOException.
     */
    open class Failure {
        /** eval is called on the first write of every new file.  */
        @Throws(IOException::class)
        open fun eval(dir: MockDirectoryWrapper) {
        }

        /**
         * reset should set the state of the failure to its default (freshly constructed) state. Reset
         * is convenient for tests that want to create one failure object and then reuse it in multiple
         * cases. This, combined with the fact that Failure subclasses are often anonymous classes makes
         * reset difficult to do otherwise.
         *
         *
         * A typical example of use is Failure failure = new Failure() { ... }; ...
         * mock.failOn(failure.reset())
         */
        fun reset(): Failure {
            return this
        }

        protected var doFail: Boolean = false

        fun setDoFail() {
            doFail = true
        }

        fun clearDoFail() {
            doFail = false
        }
    }

    var failures: ArrayList<Failure>? = null

    init {
        // must make a private random since our methods are
        // called from different threads; else test failures may
        // not be reproducible from the original seed
        this.randomState = Random(random.nextInt().toLong())
        this.throttledOutput =
            ThrottledIndexOutput(
                ThrottledIndexOutput.mBitsToBytes(
                    40 + randomState.nextInt(
                        10
                    )
                ),
                (1 + randomState.nextInt(5)).toLong(),
                null
            )
        init()
    }

    /**
     * add a Failure object to the list of objects to be evaluated at every potential failure point
     */
    /*@Synchronized*/
    fun failOn(fail: Failure) {
        if (failures == null) {
            failures = ArrayList()
        }
        failures!!.add(fail)
    }

    /** Iterate through the failures list, giving each object a chance to throw an IOE  */
    /*@Synchronized*/
    @Throws(IOException::class)
    fun maybeThrowDeterministicException() {
        if (failures != null) {
            for (i in failures!!.indices) {
                try {
                    failures!![i].eval(this)
                } catch (t: Throwable) {
                    if (LuceneTestCase.VERBOSE) {
                        println("MockDirectoryWrapper: throw exc")
                        /*t.printStackTrace(java.lang.System.out)*/
                        t.printStackTrace()
                    }
                    throw IOUtils.rethrowAlways(t)
                }
            }
        }
    }

    /*@Synchronized*/
    @Throws(IOException::class)
    override fun listAll(): Array<String> {
        maybeYield()
        return `in`.listAll()
    }

    /*@Synchronized*/
    @Throws(IOException::class)
    override fun fileLength(name: String): Long {
        maybeYield()
        return `in`.fileLength(name)
    }

    /*@Synchronized*/
    @Throws(IOException::class)
    override fun obtainLock(name: String): Lock {
        maybeYield()
        return super.obtainLock(name)
        // TODO: consider mocking locks, but not all the time, can hide bugs
    }

    /**
     * Use this when throwing fake `IOException`, e.g. from [ ].
     */
    class FakeIOException : IOException()

    override fun toString(): String {
        if (this.maxSizeInBytes != 0L) {
            return "MockDirectoryWrapper(" + `in` + ", current=" + this.maxUsedSizeInBytes + ",max=" + this.maxSizeInBytes + ")"
        } else {
            return super.toString()
        }
    }

    // don't override optional methods like copyFrom: we need the default impl for things like disk
    // full checks. we randomly exercise "raw" directories anyway. We ensure default impls are used:
    @Throws(IOException::class)
    override fun openChecksumInput(name: String): ChecksumIndexInput {
        return super.openChecksumInput(name)
    }

    @Throws(IOException::class)
    override fun copyFrom(
        from: Directory,
        src: String,
        dest: String,
        context: IOContext
    ) {
        super.copyFrom(from, src, dest, context)
    }

    @Throws(AlreadyClosedException::class)
    override fun ensureOpen() {
        super.ensureOpen()
    }
}
