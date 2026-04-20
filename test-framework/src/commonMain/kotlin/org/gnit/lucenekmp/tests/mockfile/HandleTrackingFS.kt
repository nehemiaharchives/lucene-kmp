package org.gnit.lucenekmp.tests.mockfile

import okio.FileHandle
import okio.FileSystem
import okio.ForwardingSource
import okio.Closeable
import okio.Path
import okio.Sink
import okio.Source
import okio.Timeout
import org.gnit.lucenekmp.util.IOUtils

abstract class HandleTrackingFS(
    scheme: String,
    delegate: FileSystem
) : FilterFileSystemProvider(delegate, scheme) {
    protected abstract fun onOpen(path: Path, stream: Any)

    protected abstract fun onClose(path: Path, stream: Any)

    protected fun callOpenHook(path: Path, stream: Closeable) {
        var success = false
        try {
            onOpen(path, stream)
            success = true
        } finally {
            if (!success) {
                IOUtils.closeWhileHandlingException(stream)
            }
        }
    }

    override fun source(file: Path): Source {
        val delegateSource = super.source(file)
        val stream =
            object : ForwardingSource(delegateSource) {
                var closed: Boolean = false

                override fun close() {
                    try {
                        if (!closed) {
                            closed = true
                            onClose(file, this)
                        }
                    } finally {
                        super.close()
                    }
                }

                override fun toString(): String {
                    return "InputStream($file)"
                }

                override fun hashCode(): Int {
                    return super.hashCode()
                }

                override fun equals(other: Any?): Boolean {
                    return this === other
                }
            }
        callOpenHook(file, stream)
        return stream
    }

    override fun sink(file: Path, mustCreate: Boolean): Sink {
        val delegateSink = super.sink(file, mustCreate)
        val stream =
            object : Sink {
                var closed: Boolean = false

                override fun write(source: okio.Buffer, byteCount: Long) {
                    delegateSink.write(source, byteCount)
                }

                override fun flush() {
                    delegateSink.flush()
                }

                override fun close() {
                    try {
                        if (!closed) {
                            closed = true
                            onClose(file, this)
                        }
                    } finally {
                        delegateSink.close()
                    }
                }

                override fun timeout(): Timeout {
                    return delegateSink.timeout()
                }

                override fun toString(): String {
                    return "OutputStream($file)"
                }

                override fun hashCode(): Int {
                    return super.hashCode()
                }

                override fun equals(other: Any?): Boolean {
                    return this === other
                }
            }
        callOpenHook(file, stream)
        return stream
    }

    override fun appendingSink(file: Path, mustExist: Boolean): Sink {
        val delegateSink = super.appendingSink(file, mustExist)
        val stream =
            object : Sink {
                var closed: Boolean = false

                override fun write(source: okio.Buffer, byteCount: Long) {
                    delegateSink.write(source, byteCount)
                }

                override fun flush() {
                    delegateSink.flush()
                }

                override fun close() {
                    try {
                        if (!closed) {
                            closed = true
                            onClose(file, this)
                        }
                    } finally {
                        delegateSink.close()
                    }
                }

                override fun timeout(): Timeout {
                    return delegateSink.timeout()
                }

                override fun toString(): String {
                    return "OutputStream($file)"
                }

                override fun hashCode(): Int {
                    return super.hashCode()
                }

                override fun equals(other: Any?): Boolean {
                    return this === other
                }
            }
        callOpenHook(file, stream)
        return stream
    }

    override fun openReadOnly(file: Path): FileHandle {
        val delegatePath = toDelegate(file)
        val delegateHandle = openSharedReadOnlyFileHandleOrNull(delegate, delegatePath) ?: super.openReadOnly(file)
        val handle =
            object : FileHandle(delegateHandle.readWrite) {
                var closed: Boolean = false

                override fun protectedRead(
                    fileOffset: Long,
                    array: ByteArray,
                    arrayOffset: Int,
                    byteCount: Int
                ): Int {
                    return delegateHandle.read(fileOffset, array, arrayOffset, byteCount)
                }

                override fun protectedWrite(
                    fileOffset: Long,
                    array: ByteArray,
                    arrayOffset: Int,
                    byteCount: Int
                ) {
                    delegateHandle.write(fileOffset, array, arrayOffset, byteCount)
                }

                override fun protectedFlush() {
                    delegateHandle.flush()
                }

                override fun protectedResize(size: Long) {
                    delegateHandle.resize(size)
                }

                override fun protectedSize(): Long {
                    return delegateHandle.size()
                }

                override fun protectedClose() {
                    if (!closed) {
                        closed = true
                        try {
                            onClose(file, this)
                        } finally {
                            delegateHandle.close()
                        }
                    }
                }

                override fun toString(): String {
                    return "FileHandle($file)"
                }

                override fun hashCode(): Int {
                    return super.hashCode()
                }

                override fun equals(other: Any?): Boolean {
                    return this === other
                }
            }
        callOpenHook(file, handle)
        return handle
    }

    override fun openReadWrite(file: Path, mustCreate: Boolean, mustExist: Boolean): FileHandle {
        val delegateHandle = super.openReadWrite(file, mustCreate, mustExist)
        val handle =
            object : FileHandle(delegateHandle.readWrite) {
                var closed: Boolean = false

                override fun protectedRead(
                    fileOffset: Long,
                    array: ByteArray,
                    arrayOffset: Int,
                    byteCount: Int
                ): Int {
                    return delegateHandle.read(fileOffset, array, arrayOffset, byteCount)
                }

                override fun protectedWrite(
                    fileOffset: Long,
                    array: ByteArray,
                    arrayOffset: Int,
                    byteCount: Int
                ) {
                    delegateHandle.write(fileOffset, array, arrayOffset, byteCount)
                }

                override fun protectedFlush() {
                    delegateHandle.flush()
                }

                override fun protectedResize(size: Long) {
                    delegateHandle.resize(size)
                }

                override fun protectedSize(): Long {
                    return delegateHandle.size()
                }

                override fun protectedClose() {
                    if (!closed) {
                        closed = true
                        try {
                            onClose(file, this)
                        } finally {
                            delegateHandle.close()
                        }
                    }
                }

                override fun toString(): String {
                    return "FileHandle($file)"
                }

                override fun hashCode(): Int {
                    return super.hashCode()
                }

                override fun equals(other: Any?): Boolean {
                    return this === other
                }
            }
        callOpenHook(file, handle)
        return handle
    }
}
