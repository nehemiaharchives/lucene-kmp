@file:OptIn(ExperimentalAtomicApi::class)

package org.gnit.lucenekmp.store

import okio.Buffer
import org.gnit.lucenekmp.util.IOUtils
import okio.EOFException
import okio.FileHandle
import okio.FileSystem
import okio.IOException
import okio.Path
import org.gnit.lucenekmp.jdkport.ByteBuffer
import org.gnit.lucenekmp.jdkport.Files
import org.gnit.lucenekmp.jdkport.assert
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.min
import kotlin.time.Clock

/**
 * An [FSDirectory] implementation that uses java.nio's FileChannel's positional read, which
 * allows multiple threads to read from the same file without synchronizing.
 *
 *
 * This class only uses FileChannel when reading; writing is achieved with [ ].
 *
 *
 * **NOTE**: NIOFSDirectory is not recommended on Windows because of a bug in how
 * FileChannel.read is implemented in Sun's JRE. Inside of the implementation the position is
 * apparently synchronized. See [here](https://bugs.java.com/bugdatabase/view_bugbug_id=6265734) for details.
 *
 *
 * **NOTE:** Accessing this class either directly or indirectly from a thread while it's
 * interrupted can close the underlying file descriptor immediately if at the same time the thread
 * is blocked on IO. The file descriptor will remain closed and subsequent access to [ ] will throw a [ClosedChannelException]. If your application uses either
 * [Thread.interrupt] or [Future.cancel] you should use the legacy `RAFDirectory` from the Lucene `misc` module in favor of [NIOFSDirectory].
 */
class NIOFSDirectory
/**
 * Create a new NIOFSDirectory for the named location. The directory is created at the named
 * location if it does not yet exist.
 *
 * @param path the path of the directory
 * @param lockFactory the lock factory to use
 * @throws IOException if there is a low-level I/O error
 */
/**
 * Create a new NIOFSDirectory for the named location and [FSLockFactory.getDefault]. The
 * directory is created at the named location if it does not yet exist.
 *
 * @param path the path of the directory
 * @throws IOException if there is a low-level I/O error
 */
constructor(
    path: Path,
    lockFactory: LockFactory = FSLockFactory.default
) : FSDirectory(path, lockFactory) {

    companion object {
        @OptIn(ExperimentalAtomicApi::class)
        private val readInternalCalls: AtomicLong = AtomicLong(0L)
        private val readInternalRequestedBytes: AtomicLong = AtomicLong(0L)
        private val readInternalTotalMs: AtomicLong = AtomicLong(0L)
        private val readInternalChunkIterations: AtomicLong = AtomicLong(0L)
        private val readInternalTempBufferCreateMs: AtomicLong = AtomicLong(0L)
        private val readInternalHandleReadCalls: AtomicLong = AtomicLong(0L)
        private val readInternalHandleReadBytes: AtomicLong = AtomicLong(0L)
        private val readInternalHandleReadMs: AtomicLong = AtomicLong(0L)
        private val readInternalTransferMs: AtomicLong = AtomicLong(0L)
        private val readInternalEofSignals: AtomicLong = AtomicLong(0L)

        data class ReadInternalProfile(
            val calls: Long,
            val requestedBytes: Long,
            val totalMs: Long,
            val chunkIterations: Long,
            val tempBufferCreateMs: Long,
            val handleReadCalls: Long,
            val handleReadBytes: Long,
            val handleReadMs: Long,
            val transferMs: Long,
            val eofSignals: Long
        )

        @OptIn(ExperimentalAtomicApi::class)
        fun resetReadInternalProfile() {
            readInternalCalls.store(0L)
            readInternalRequestedBytes.store(0L)
            readInternalTotalMs.store(0L)
            readInternalChunkIterations.store(0L)
            readInternalTempBufferCreateMs.store(0L)
            readInternalHandleReadCalls.store(0L)
            readInternalHandleReadBytes.store(0L)
            readInternalHandleReadMs.store(0L)
            readInternalTransferMs.store(0L)
            readInternalEofSignals.store(0L)
        }

        @OptIn(ExperimentalAtomicApi::class)
        fun readInternalProfile(): ReadInternalProfile {
            return ReadInternalProfile(
                calls = readInternalCalls.load(),
                requestedBytes = readInternalRequestedBytes.load(),
                totalMs = readInternalTotalMs.load(),
                chunkIterations = readInternalChunkIterations.load(),
                tempBufferCreateMs = readInternalTempBufferCreateMs.load(),
                handleReadCalls = readInternalHandleReadCalls.load(),
                handleReadBytes = readInternalHandleReadBytes.load(),
                handleReadMs = readInternalHandleReadMs.load(),
                transferMs = readInternalTransferMs.load(),
                eofSignals = readInternalEofSignals.load()
            )
        }
    }

    private val fileSystem: FileSystem = Files.getFileSystem()

    override fun openInput(
        name: String,
        context: IOContext
    ): IndexInput {
        ensureOpen()
        ensureCanRead(name)
        val path: Path = directory.resolve(name)
        //val fc: FileChannel = FileChannel.open(path, StandardOpenOption.READ)
        val fh: FileHandle = fileSystem.openReadOnly(path)
        var success = false
        try {
            val indexInput =
                NIOFSIndexInput("NIOFSIndexInput(path=\"$path\")", fh, context)
            success = true
            return indexInput
        } finally {
            if (!success) {
                IOUtils.closeWhileHandlingException(fh)
            }
        }
    }

    /** Reads bytes with [FileChannel.read]  */
    internal class NIOFSIndexInput : BufferedIndexInput {
        /** the file channel we will read from  */
        /*protected val channel: FileChannel*/
        protected val handle: FileHandle

        /** is this instance a clone and hence does not own the file to close it  */
        var isClone: Boolean = false

        /** start offset: non-zero in the slice case  */
        protected val off: Long

        /** end offset (start+length)  */
        protected val end: Long

        constructor(resourceDesc: String, /*fc: FileChannel,*/ fh: FileHandle, context: IOContext) : super(
            resourceDesc,
            context
        ) {
            /*this.channel = fc*/
            this.handle = fh
            this.off = 0L
            this.end = /*fc.size()*/ fh.size()
        }

        constructor(resourceDesc: String, /*fc: FileChannel,*/ fh: FileHandle, off: Long, length: Long, bufferSize: Int) : super(
            resourceDesc,
            bufferSize
        ) {
            /*this.channel = fc*/
            this.handle = fh
            this.off = off
            this.end = off + length
            this.isClone = true
        }

        @Throws(IOException::class)
        override fun close() {
            if (!isClone) {
                /*channel.close()*/
                handle.close()
            }
        }

        override fun clone(): NIOFSIndexInput {
            val clone = super.clone() as NIOFSIndexInput
            clone.isClone = true
            return clone
        }

        override fun slice(sliceDescription: String, offset: Long, length: Long): IndexInput {
            require(!((length or offset) < 0 || length > this.length() - offset)) {
                ("slice() "
                        + sliceDescription
                        + " out of bounds: offset="
                        + offset
                        + ",length="
                        + length
                        + ",fileLength="
                        + this.length()
                        + ": "
                        + this)
            }
            return NIOFSIndexInput(
                getFullSliceDescription(sliceDescription),
                handle,
                off + offset,
                length,
                bufferSize
            )
        }

        override fun length(): Long {
            return end - off
        }

        @Throws(IOException::class)
        override fun readInternal(b: ByteBuffer) {
            readInternalCalls.addAndFetch(1L)
            readInternalRequestedBytes.addAndFetch(b.remaining().toLong())
            val readInternalStartMs = Clock.System.now().toEpochMilliseconds()
            var pos: Long = filePointer + off

            if (pos + b.remaining() > end) {
                throw EOFException("read past EOF: $this")
            }

            try {
                var readLength: Int = b.remaining()
                while (readLength > 0) {
                    readInternalChunkIterations.addAndFetch(1L)
                    val toRead = min(CHUNK_SIZE, readLength)
                    b.limit(b.position + toRead)
                    assert(b.remaining() == toRead)
                    /*val i: Int = channel.read(b, pos)*/
                    val createBufferStartMs = Clock.System.now().toEpochMilliseconds()
                    val tempOkioBuffer = Buffer()
                    readInternalTempBufferCreateMs.addAndFetch(
                        Clock.System.now().toEpochMilliseconds() - createBufferStartMs
                    )

                    readInternalHandleReadCalls.addAndFetch(1L)
                    val handleReadStartMs = Clock.System.now().toEpochMilliseconds()
                    val numBytesReadFromHandle: Long = handle.read(
                        fileOffset = pos,
                        sink = tempOkioBuffer,
                        byteCount = toRead.toLong()
                    )
                    readInternalHandleReadMs.addAndFetch(
                        Clock.System.now().toEpochMilliseconds() - handleReadStartMs
                    )

                    val i: Int

                    if(numBytesReadFromHandle == -1L){
                        // EOF reached at the given fileOffset
                        i = -1
                        readInternalEofSignals.addAndFetch(1L)
                    }else{
                        val actualByteCount = numBytesReadFromHandle.toInt()
                        if(actualByteCount > 0){
                            readInternalHandleReadBytes.addAndFetch(actualByteCount.toLong())
                            val transferStartMs = Clock.System.now().toEpochMilliseconds()
                            transferTempOkioBufferToByteBuffer(tempOkioBuffer, actualByteCount, b)
                            readInternalTransferMs.addAndFetch(
                                Clock.System.now().toEpochMilliseconds() - transferStartMs
                            )
                        }
                        i = actualByteCount
                    }

                    if (i < 0) {
                        // be defensive here, even though we checked before hand, something could have changed
                        throw EOFException(
                            ("read past EOF: "
                                    + this
                                    + " buffer: "
                                    + b
                                    + " chunkLen: "
                                    + toRead
                                    + " end: "
                                    + end)
                        )
                    }
                    assert(
                        i > 0
                    ) {
                        ("FileChannel.read with non zero-length bb.remaining() must always read at least "
                                + "one byte (FileChannel is in blocking mode, see spec of ReadableByteChannel)")
                    }
                    pos += i.toLong()
                    readLength -= i
                }
                assert(readLength == 0)
            } catch (ioe: IOException) {
                val msg = ioe.message?.lowercase()
                if ((msg?.contains("bad file descriptor") == true) || (msg?.contains("closed") == true)) {
                    throw AlreadyClosedException("Already closed: $this", ioe)
                }
                throw IOException(ioe.message + ": " + this, ioe)
            } finally {
                readInternalTotalMs.addAndFetch(Clock.System.now().toEpochMilliseconds() - readInternalStartMs)
            }
        }

        @Throws(IOException::class)
        override fun seekInternal(pos: Long) {
            if (pos > length()) {
                throw EOFException(
                    "read past EOF: pos=" + pos + " vs length=" + length() + ": " + this
                )
            }
        }

        companion object {
            /** The maximum chunk size for reads of 16384 bytes.  */
            private const val CHUNK_SIZE = 16384
        }
    }
}
