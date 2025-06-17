package org.gnit.lucenekmp.store

import okio.IOException
import org.gnit.lucenekmp.jdkport.ByteBuffer
import org.gnit.lucenekmp.jdkport.ByteOrder
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.jdkport.NoSuchFileException
import org.gnit.lucenekmp.jdkport.FileAlreadyExistsException
import org.gnit.lucenekmp.jdkport.AccessDeniedException
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.jdkport.CRC32
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement

/** A [Directory] storing files as lists of [ByteBuffer]s in memory. */
class ByteBuffersDirectory : BaseDirectory {
    companion object {
        val OUTPUT_AS_MANY_BUFFERS: (String, ByteBuffersDataOutput) -> IndexInput = { fileName, output ->
            val dataInput = output.toDataInput()
            val inputName = "${ByteBuffersIndexInput::class.simpleName} (file=$fileName, buffers=${dataInput})"
            ByteBuffersIndexInput(dataInput, inputName)
        }

        val OUTPUT_AS_ONE_BUFFER: (String, ByteBuffersDataOutput) -> IndexInput = { fileName, output ->
            val dataInput = ByteBuffersDataInput(mutableListOf(ByteBuffer.wrap(output.toArrayCopy()).order(ByteOrder.LITTLE_ENDIAN)))
            val inputName = "${ByteBuffersIndexInput::class.simpleName} (file=$fileName, buffers=${dataInput})"
            ByteBuffersIndexInput(dataInput, inputName)
        }

        val OUTPUT_AS_BYTE_ARRAY = OUTPUT_AS_ONE_BUFFER
    }

    @OptIn(ExperimentalAtomicApi::class)
    private val tempFileName = object : (String) -> String {
        private val counter = AtomicLong(0)
        override fun invoke(suffix: String): String {
            return suffix + "_" + counter.fetchAndIncrement().toString(Character.MAX_RADIX)
        }
    }

    private val files = mutableMapOf<String, FileEntry>()
    private val outputToInput: (String, ByteBuffersDataOutput) -> IndexInput
    private val bbOutputSupplier: () -> ByteBuffersDataOutput

    constructor() : this(SingleInstanceLockFactory())

    constructor(lockFactory: LockFactory) : this(lockFactory, { ByteBuffersDataOutput() }, OUTPUT_AS_MANY_BUFFERS)

    constructor(
        factory: LockFactory,
        bbOutputSupplier: () -> ByteBuffersDataOutput,
        outputToInput: (String, ByteBuffersDataOutput) -> IndexInput
    ) : super(factory) {
        this.outputToInput = outputToInput
        this.bbOutputSupplier = bbOutputSupplier
    }

    @Throws(IOException::class)
    override fun listAll(): Array<String> {
        ensureOpen()
        return files.keys.sorted().toTypedArray()
    }

    @Throws(IOException::class)
    override fun deleteFile(name: String) {
        ensureOpen()
        val removed = files.remove(name)
        if (removed == null) {
            throw NoSuchFileException(name)
        }
    }

    @Throws(IOException::class)
    override fun fileLength(name: String): Long {
        ensureOpen()
        val file = files[name] ?: throw NoSuchFileException(name)
        return file.length()
    }

    fun fileExists(name: String): Boolean {
        ensureOpen()
        return files.containsKey(name)
    }

    @Throws(IOException::class)
    override fun createOutput(name: String, context: IOContext): IndexOutput {
        ensureOpen()
        if (files.containsKey(name)) {
            throw FileAlreadyExistsException("File already exists: $name")
        }
        val e = FileEntry(name)
        files[name] = e
        return e.createOutput(outputToInput)
    }

    @Throws(IOException::class)
    override fun createTempOutput(prefix: String, suffix: String, context: IOContext): IndexOutput {
        ensureOpen()
        while (true) {
            val name = IndexFileNames.segmentFileName(prefix, tempFileName.invoke(suffix), "tmp")
            if (!files.containsKey(name)) {
                val e = FileEntry(name)
                files[name] = e
                return e.createOutput(outputToInput)
            }
        }
    }

    @Throws(IOException::class)
    override fun rename(source: String, dest: String) {
        ensureOpen()
        val file = files[source] ?: throw NoSuchFileException(source)
        if (files.containsKey(dest)) {
            throw FileAlreadyExistsException(dest)
        }
        files[dest] = file
        if (files[source] !== file) {
            throw IllegalStateException("File was unexpectedly replaced: $source")
        }
        files.remove(source)
    }

    @Throws(IOException::class)
    override fun sync(names: MutableCollection<String>) {
        ensureOpen()
    }

    @Throws(IOException::class)
    override fun syncMetaData() {
        ensureOpen()
    }

    @Throws(IOException::class)
    override fun openInput(name: String, context: IOContext): IndexInput {
        ensureOpen()
        val e = files[name] ?: throw NoSuchFileException(name)
        return e.openInput()
    }

    @Throws(IOException::class)
    override fun close() {
        isOpen = false
        files.clear()
    }

    override val pendingDeletions: MutableSet<String>
        get() = mutableSetOf()

    private inner class FileEntry(private val fileName: String) {
        @Volatile
        private var content: IndexInput? = null
        @Volatile
        private var cachedLength: Long = 0

        fun length(): Long {
            return cachedLength
        }

        @Throws(IOException::class)
        fun openInput(): IndexInput {
            val local = content ?: throw AccessDeniedException("Can't open a file still open for writing: $fileName")
            return local.clone()
        }

        @Throws(IOException::class)
        fun createOutput(outputToInput: (String, ByteBuffersDataOutput) -> IndexInput): IndexOutput {
            if (content != null) {
                throw IOException("Can only write to a file once: $fileName")
            }
            val outputName = "${ByteBuffersDirectory::class.simpleName} output (file=$fileName)"
            return ByteBuffersIndexOutput(
                bbOutputSupplier.invoke(),
                outputName,
                fileName,
                CRC32()
            ) { output ->
                content = outputToInput.invoke(fileName, output)
                cachedLength = output.size()
            }
        }
    }
}
