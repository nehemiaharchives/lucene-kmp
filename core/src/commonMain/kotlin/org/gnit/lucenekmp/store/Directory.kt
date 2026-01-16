package org.gnit.lucenekmp.store

import okio.IOException
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.util.IOUtils


/**
 * A `Directory` provides an abstraction layer for storing a list of files. A directory
 * contains only files (no sub-folder hierarchy).
 *
 *
 * Implementing classes must comply with the following:
 *
 *
 *  * A file in a directory can be created ([.createOutput]), appended to, then closed.
 *  * A file open for writing may not be available for read access until the corresponding [       ] is closed.
 *  * Once a file is created it must only be opened for input ([.openInput]), or deleted
 * ([.deleteFile]). Calling [.createOutput] on an existing file must throw [       ].
 *
 *
 *
 * **NOTE:** If your application requires external synchronization, you should **not**
 * synchronize on the `Directory` implementation instance as this may cause deadlock; use
 * your own (non-Lucene) objects instead.
 *
 * @see FSDirectory
 *
 * @see ByteBuffersDirectory
 *
 * @see FilterDirectory
 */
abstract class Directory : AutoCloseable {
    /**
     * Returns names of all files stored in this directory. The output must be in sorted (UTF-16,
     * java's [String.compareTo]) order.
     *
     * @throws IOException in case of I/O error
     */
    @Throws(IOException::class)
    abstract fun listAll(): Array<String>

    /**
     * Removes an existing file in the directory.
     *
     *
     * This method must throw either [NoSuchFileException] or [FileNotFoundException]
     * if `name` points to a non-existing file.
     *
     * @param name the name of an existing file.
     * @throws IOException in case of I/O error
     */
    @Throws(IOException::class)
    abstract fun deleteFile(name: String)

    /**
     * Returns the byte length of a file in the directory.
     *
     *
     * This method must throw either [NoSuchFileException] or [FileNotFoundException]
     * if `name` points to a non-existing file.
     *
     * @param name the name of an existing file.
     * @throws IOException in case of I/O error
     */
    @Throws(IOException::class)
    abstract fun fileLength(name: String): Long

    /**
     * Creates a new, empty file in the directory and returns an [IndexOutput] instance for
     * appending data to this file.
     *
     *
     * This method must throw [java.nio.file.FileAlreadyExistsException] if the file already
     * exists.
     *
     * @param name the name of the file to create.
     * @throws IOException in case of I/O error
     */
    @Throws(IOException::class)
    abstract fun createOutput(name: String, context: IOContext): IndexOutput

    /**
     * Creates a new, empty, temporary file in the directory and returns an [IndexOutput]
     * instance for appending data to this file.
     *
     *
     * The temporary file name (accessible via [IndexOutput.getName]) will start with
     * `prefix`, end with `suffix` and have a reserved file extension `.tmp`.
     */
    @Throws(IOException::class)
    abstract fun createTempOutput(prefix: String, suffix: String, context: IOContext): IndexOutput

    /**
     * Ensures that any writes to these files are moved to stable storage (made durable).
     *
     *
     * Lucene uses this to properly commit changes to the index, to prevent a machine/OS crash from
     * corrupting the index.
     *
     * @see .syncMetaData
     */
    @Throws(IOException::class)
    abstract fun sync(names: MutableCollection<String>)

    /**
     * Ensures that directory metadata, such as recent file renames, are moved to stable storage.
     *
     * @see .sync
     */
    @Throws(IOException::class)
    abstract fun syncMetaData()

    /**
     * Renames `source` file to `dest` file where `dest` must not already exist in
     * the directory.
     *
     *
     * It is permitted for this operation to not be truly atomic, for example both `source`
     * and `dest` can be visible temporarily in [.listAll]. However, the implementation
     * of this method must ensure the content of `dest` appears as the entire `source`
     * atomically. So once `dest` is visible for readers, the entire content of previous `source` is visible.
     *
     *
     * This method is used by IndexWriter to publish commits.
     */
    @Throws(IOException::class)
    abstract fun rename(source: String, dest: String)

    /**
     * Opens a stream for reading an existing file.
     *
     *
     * This method must throw either [NoSuchFileException] or [FileNotFoundException]
     * if `name` points to a non-existing file.
     *
     * @param name the name of an existing file.
     * @throws IOException in case of I/O error
     */
    @Throws(IOException::class)
    abstract fun openInput(name: String, context: IOContext): IndexInput

    /**
     * Opens a checksum-computing stream for reading an existing file.
     *
     *
     * This method must throw either [NoSuchFileException] or [FileNotFoundException]
     * if `name` points to a non-existing file.
     *
     * @param name the name of an existing file.
     * @throws IOException in case of I/O error
     */
    @Throws(IOException::class)
    open fun openChecksumInput(name: String): ChecksumIndexInput {
        return BufferedChecksumIndexInput(openInput(name, IOContext.READONCE))
    }

    /**
     * Acquires and returns a [Lock] for a file with the given name.
     *
     * @param name the name of the lock file
     * @throws LockObtainFailedException (optional specific exception) if the lock could not be
     * obtained because it is currently held elsewhere.
     * @throws IOException if any i/o error occurs attempting to gain the lock
     */
    @Throws(IOException::class)
    abstract fun obtainLock(name: String): Lock

    /** Closes the directory.  */
    abstract override fun close()

    /**
     * Copies an existing `src` file from directory `from` to a non-existent file `dest` in this directory. The given IOContext is only used for opening the destination file.
     */
    @Throws(IOException::class)
    open fun copyFrom(from: Directory, src: String, dest: String, context: IOContext) {
        var success = false
        try {
            from.openInput(src, IOContext.READONCE).use { `is` ->
                createOutput(dest, context).use { os ->
                    os.copyBytes(`is`, `is`.length())
                    success = true
                }
            }
        } finally {
            if (!success) {
                IOUtils.deleteFilesIgnoringExceptions(this, dest)
            }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun toString(): String {
        return this::class.simpleName + '@' + hashCode().toHexString()
    }

    /**
     * Ensures this directory is still open.
     *
     * @throws AlreadyClosedException if this directory is closed.
     */
    @Throws(AlreadyClosedException::class)
    open fun ensureOpen() {
    }

    abstract val pendingDeletions: MutableSet<String>

    companion object {
        /**
         * Creates a file name for a temporary file. The name will start with `prefix`, end with
         * `suffix` and have a reserved file extension `.tmp`.
         *
         * @see .createTempOutput
         */
        fun getTempFileName(prefix: String, suffix: String, counter: Long): String {
            return IndexFileNames.segmentFileName(
                prefix, suffix + "_" + counter.toString(Character.MAX_RADIX), "tmp"
            ).toString()
        }
    }
}
