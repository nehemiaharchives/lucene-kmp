@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.gnit.lucenekmp.jdkport

import kotlinx.cinterop.ByteVarOf
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import okio.FileHandle
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.buffer
import platform.windows.CREATE_NEW
import platform.windows.CreateFileA
import platform.windows.DWORD
import platform.windows.ERROR_FILE_NOT_FOUND
import platform.windows.ERROR_HANDLE_EOF
import platform.windows.ERROR_PATH_NOT_FOUND
import platform.windows.FILE_ATTRIBUTE_NORMAL
import platform.windows.FILE_BEGIN
import platform.windows.FILE_SHARE_DELETE
import platform.windows.FILE_SHARE_READ
import platform.windows.FILE_SHARE_WRITE
import platform.windows.FORMAT_MESSAGE_FROM_SYSTEM
import platform.windows.FORMAT_MESSAGE_IGNORE_INSERTS
import platform.windows.FlushFileBuffers
import platform.windows.FormatMessageA
import platform.windows.GENERIC_READ
import platform.windows.GENERIC_WRITE
import platform.windows.GetFileSizeEx
import platform.windows.GetLastError
import platform.windows.HANDLE
import platform.windows.INVALID_HANDLE_VALUE
import platform.windows.INVALID_SET_FILE_POINTER
import platform.windows.LANG_NEUTRAL
import platform.windows.LARGE_INTEGER
import platform.windows.NO_ERROR
import platform.windows.OPEN_ALWAYS
import platform.windows.OPEN_EXISTING
import platform.windows.ReadFile
import platform.windows.SUBLANG_DEFAULT
import platform.windows.SetEndOfFile
import platform.windows.SetFilePointer
import platform.windows.WriteFile
import platform.windows._OVERLAPPED
import platform.windows.CloseHandle

internal actual fun openReadOnlyFileHandlePlatform(path: Path, fileSystem: FileSystem): FileHandle? {
    if (fileSystem !== FileSystem.SYSTEM) {
        return null
    }
    val openFile = CreateFileA(
        lpFileName = path.toString(),
        dwDesiredAccess = GENERIC_READ,
        dwShareMode = (FILE_SHARE_READ or FILE_SHARE_WRITE or FILE_SHARE_DELETE).toUInt(),
        lpSecurityAttributes = null,
        dwCreationDisposition = OPEN_EXISTING.toUInt(),
        dwFlagsAndAttributes = FILE_ATTRIBUTE_NORMAL.toUInt(),
        hTemplateFile = null,
    )
    if (openFile == INVALID_HANDLE_VALUE) {
        throw lastErrorToIOException()
    }
    return WindowsFileHandle(false, openFile)
}

internal actual fun newOutputStreamPlatform(
    path: Path,
    fileSystem: FileSystem,
    options: Array<out OpenOption>,
): OutputStream? {
    if (fileSystem !== FileSystem.SYSTEM) {
        return null
    }
    val opts = options.toSet()
    val defaulted = opts.isEmpty()
    val append = opts.contains(StandardOpenOption.APPEND)
    val createNew = opts.contains(StandardOpenOption.CREATE_NEW)
    val create = opts.contains(StandardOpenOption.CREATE) || defaulted
    val truncate = opts.contains(StandardOpenOption.TRUNCATE_EXISTING) || defaulted

    val creationDisposition =
        when {
            createNew -> CREATE_NEW.toUInt()
            create -> OPEN_ALWAYS.toUInt()
            else -> OPEN_EXISTING.toUInt()
        }

    val openFile = CreateFileA(
        lpFileName = path.toString(),
        dwDesiredAccess = (GENERIC_READ or GENERIC_WRITE.toUInt()),
        dwShareMode = (FILE_SHARE_READ or FILE_SHARE_WRITE or FILE_SHARE_DELETE).toUInt(),
        lpSecurityAttributes = null,
        dwCreationDisposition = creationDisposition,
        dwFlagsAndAttributes = FILE_ATTRIBUTE_NORMAL.toUInt(),
        hTemplateFile = null,
    )
    if (openFile == INVALID_HANDLE_VALUE) {
        throw lastErrorToIOException()
    }

    val handle = WindowsFileHandle(true, openFile)
    if (truncate && !append) {
        handle.resize(0L)
    }
    val fileOffset = if (append) handle.size() else 0L
    return FileHandleOutputStream(handle, fileOffset)
}

private class FileHandleOutputStream(
    private val handle: FileHandle,
    fileOffset: Long,
) : OutputStream() {
    private val delegate = OkioSinkOutputStream(handle.sink(fileOffset).buffer())
    private var closed = false

    override fun write(b: Int) {
        delegate.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        delegate.write(b, off, len)
    }

    override fun flush() {
        delegate.flush()
        handle.flush()
    }

    override fun close() {
        if (closed) return
        closed = true
        try {
            delegate.close()
        } finally {
            handle.close()
        }
    }
}

private class WindowsFileHandle(
    readWrite: Boolean,
    private val file: HANDLE?,
) : FileHandle(readWrite) {
    override fun protectedSize(): Long {
        memScoped {
            val result = alloc<LARGE_INTEGER>()
            if (GetFileSizeEx(file, result.ptr) == 0) {
                throw lastErrorToIOException()
            }
            return result.toLong()
        }
    }

    override fun protectedRead(
        fileOffset: Long,
        array: ByteArray,
        arrayOffset: Int,
        byteCount: Int,
    ): Int {
        val bytesRead =
            if (array.isNotEmpty()) {
                array.usePinned { pinned ->
                    variantPread(pinned.addressOf(arrayOffset), byteCount, fileOffset)
                }
            } else {
                0
            }
        if (bytesRead == 0) return -1
        return bytesRead
    }

    private fun variantPread(target: CValuesRef<*>, byteCount: Int, offset: Long): Int {
        memScoped {
            val overlapped = alloc<_OVERLAPPED>()
            overlapped.Offset = offset.toUInt()
            overlapped.OffsetHigh = (offset ushr 32).toUInt()
            val readFileResult = ReadFile(file, target.getPointer(this), byteCount.toUInt(), null, overlapped.ptr)
            if (readFileResult == 0 && GetLastError().toInt() != ERROR_HANDLE_EOF) {
                throw lastErrorToIOException()
            }
            return overlapped.InternalHigh.toInt()
        }
    }

    override fun protectedWrite(
        fileOffset: Long,
        array: ByteArray,
        arrayOffset: Int,
        byteCount: Int,
    ) {
        val bytesWritten =
            if (array.isNotEmpty()) {
                array.usePinned { pinned ->
                    variantPwrite(pinned.addressOf(arrayOffset), byteCount, fileOffset)
                }
            } else {
                0
            }
        if (bytesWritten != byteCount) throw IOException("bytesWritten=$bytesWritten")
    }

    private fun variantPwrite(source: CValuesRef<*>, byteCount: Int, offset: Long): Int {
        memScoped {
            val overlapped = alloc<_OVERLAPPED>()
            overlapped.Offset = offset.toUInt()
            overlapped.OffsetHigh = (offset ushr 32).toUInt()
            val writeFileResult = WriteFile(file, source.getPointer(this), byteCount.toUInt(), null, overlapped.ptr)
            if (writeFileResult == 0) {
                throw lastErrorToIOException()
            }
            return overlapped.InternalHigh.toInt()
        }
    }

    override fun protectedFlush() {
        if (FlushFileBuffers(file) == 0) {
            throw lastErrorToIOException()
        }
    }

    override fun protectedResize(size: Long) {
        memScoped {
            val distanceToMoveHigh = alloc<IntVar>()
            distanceToMoveHigh.value = (size ushr 32).toInt()
            val movePointerResult = SetFilePointer(file, size.toInt(), distanceToMoveHigh.ptr, FILE_BEGIN.toUInt())
            val lastError = GetLastError()
            if (movePointerResult == INVALID_SET_FILE_POINTER.toUInt() && lastError != NO_ERROR.toUInt()) {
                throw lastErrorToIOException()
            }
            if (SetEndOfFile(file) == 0) {
                throw lastErrorToIOException()
            }
        }
    }

    override fun protectedClose() {
        if (CloseHandle(file) == 0) {
            throw lastErrorToIOException()
        }
    }

    private fun LARGE_INTEGER.toLong(): Long {
        return (HighPart.toLong() shl 32) + (LowPart.toLong() and 0xffffffffL)
    }
}

private fun lastErrorToIOException(): IOException {
    val lastError = GetLastError()
    return when (lastError.toInt()) {
        ERROR_FILE_NOT_FOUND, ERROR_PATH_NOT_FOUND -> okio.FileNotFoundException(lastErrorString(lastError))
        else -> IOException(lastErrorString(lastError))
    }
}

private fun lastErrorString(lastError: DWORD): String {
    memScoped {
        val messageMaxSize = 2048
        val message = allocArray<ByteVarOf<Byte>>(messageMaxSize)
        FormatMessageA(
            dwFlags = (FORMAT_MESSAGE_FROM_SYSTEM or FORMAT_MESSAGE_IGNORE_INSERTS).toUInt(),
            lpSource = null,
            dwMessageId = lastError,
            dwLanguageId = (SUBLANG_DEFAULT * 1024 + LANG_NEUTRAL).toUInt(),
            lpBuffer = message,
            nSize = messageMaxSize.toUInt(),
            Arguments = null,
        )
        return message.toKString().trim()
    }
}
