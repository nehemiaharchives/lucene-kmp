package org.gnit.lucenekmp.store

import kotlinx.io.IOException
import org.gnit.lucenekmp.jdkport.CRC32
import org.gnit.lucenekmp.jdkport.Checksum


/** An [IndexOutput] writing to a [ByteBuffersDataOutput].  */
class ByteBuffersIndexOutput(
    delegate: ByteBuffersDataOutput,
    resourceDescription: String,
    name: String,
    checksum: Checksum,
    onClose: ((ByteBuffersDataOutput) -> Unit)?
) : IndexOutput(resourceDescription, name) {
    private val onClose: ((ByteBuffersDataOutput) -> Unit)?

    val checksum: Checksum
    private var lastChecksumPosition: Long = 0
    private var lastChecksum: Long = 0

    private var delegate: ByteBuffersDataOutput?

    constructor(delegate: ByteBuffersDataOutput, resourceDescription: String, name: String) : this(
        delegate,
        resourceDescription,
        name,
        CRC32(),
        null
    )

    init {
        this.delegate = delegate
        this.checksum = checksum
        this.onClose = onClose
    }

    override fun close() {
        // No special effort to be thread-safe here since IndexOutputs are not required to be
        // thread-safe.
        val local = delegate
        delegate = null
        if (local != null && onClose != null) {
            onClose.invoke(local)
        }
    }

    override val filePointer: Long
        get() {
            ensureOpen()
            return delegate!!.size()
        }

    override fun getChecksum(): Long {
        ensureOpen()

        if (checksum == null) {
            throw IOException("This index output has no checksum computing ability: " + toString())
        }

        // Compute checksum on the current content of the delegate.
        //
        // This way we can override more methods and pass them directly to the delegate for efficiency
        // of writing,
        // while allowing the checksum to be correctly computed on the current content of the output
        // buffer (IndexOutput
        // is per-thread, so no concurrent changes).
        if (lastChecksumPosition != delegate!!.size()) {
            lastChecksumPosition = delegate!!.size()
            checksum.reset()
            for (bb in delegate!!.toBufferList()) {
                checksum.update(bb)
            }
            lastChecksum = checksum.getValue()
        }
        return lastChecksum
    }

    @Throws(IOException::class)
    override fun writeByte(b: Byte) {
        ensureOpen()
        delegate!!.writeByte(b)
    }

    @Throws(IOException::class)
    override fun writeBytes(b: ByteArray, offset: Int, length: Int) {
        ensureOpen()
        delegate!!.writeBytes(b, offset, length)
    }

    @Throws(IOException::class)
    override fun writeBytes(b: ByteArray, length: Int) {
        ensureOpen()
        delegate!!.writeBytes(b, length)
    }

    @Throws(IOException::class)
    override fun writeInt(i: Int) {
        ensureOpen()
        delegate!!.writeInt(i)
    }

    @Throws(IOException::class)
    override fun writeShort(i: Short) {
        ensureOpen()
        delegate!!.writeShort(i)
    }

    @Throws(IOException::class)
    override fun writeLong(i: Long) {
        ensureOpen()
        delegate!!.writeLong(i)
    }

    @Throws(IOException::class)
    override fun writeString(s: String) {
        ensureOpen()
        delegate!!.writeString(s)
    }

    @Throws(IOException::class)
    override fun copyBytes(input: DataInput, numBytes: Long) {
        ensureOpen()
        delegate!!.copyBytes(input, numBytes)
    }

    @Throws(IOException::class)
    override fun writeMapOfStrings(map: MutableMap<String, String>) {
        ensureOpen()
        delegate!!.writeMapOfStrings(map)
    }

    @Throws(IOException::class)
    override fun writeSetOfStrings(set: MutableSet<String>) {
        ensureOpen()
        delegate!!.writeSetOfStrings(set)
    }

    private fun ensureOpen() {
        if (delegate == null) {
            throw AlreadyClosedException("Already closed.")
        }
    }

    fun toArrayCopy(): ByteArray {
        return delegate!!.toArrayCopy()
    }
}
