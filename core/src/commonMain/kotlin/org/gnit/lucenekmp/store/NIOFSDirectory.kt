package org.gnit.lucenekmp.store

import okio.Buffer
import org.gnit.lucenekmp.util.IOUtils
import okio.EOFException
import okio.FileHandle
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.SYSTEM
import org.gnit.lucenekmp.jdkport.ByteBuffer
import org.gnit.lucenekmp.jdkport.assert
import kotlin.math.min

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
    lockFactory: LockFactory = FSLockFactory.default,
    val fileSystem: FileSystem = FileSystem.SYSTEM
) : FSDirectory(path, lockFactory) {
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
            if (success == false) {
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
            var pos: Long = filePointer + off

            if (pos + b.remaining() > end) {
                throw EOFException("read past EOF: $this")
            }

            try {
                var readLength: Int = b.remaining()
                while (readLength > 0) {
                    val toRead = min(CHUNK_SIZE, readLength)
                    b.limit(b.position + toRead)
                    assert(b.remaining() == toRead)
                    /*val i: Int = channel.read(b, pos)*/
                    val tempOkioBuffer = Buffer()

                    val numBytesReadFromHandle: Long = handle.read(
                        fileOffset = pos,
                        sink = tempOkioBuffer,
                        byteCount = toRead.toLong()
                    )

                    val i: Int

                    if(numBytesReadFromHandle == -1L){
                        // EOF reached at the given fileOffset
                        i = -1
                    }else{
                        val actualByteCount = numBytesReadFromHandle.toInt()
                        if(actualByteCount > 0){
                            val dataToTransfer = tempOkioBuffer.readByteArray(actualByteCount.toLong())
                            b.put(dataToTransfer)
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
                throw IOException(ioe.message + ": " + this, ioe)
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
