@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.gnit.lucenekmp.tests.mockfile

import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import okio.FileHandle
import okio.FileSystem
import okio.IOException
import okio.Path
import platform.windows.CloseHandle
import platform.windows.CreateFileA
import platform.windows.FILE_ATTRIBUTE_NORMAL
import platform.windows.FILE_SHARE_DELETE
import platform.windows.FILE_SHARE_READ
import platform.windows.FILE_SHARE_WRITE
import platform.windows.GENERIC_READ
import platform.windows.GetFileSizeEx
import platform.windows.GetLastError
import platform.windows.HANDLE
import platform.windows.INVALID_HANDLE_VALUE
import platform.windows.LARGE_INTEGER
import platform.windows.OPEN_EXISTING
import platform.windows.ReadFile
import platform.windows._OVERLAPPED

internal actual fun openSharedReadOnlyFileHandleOrNull(
    delegate: FileSystem,
    file: Path,
): FileHandle? {
    if (delegate !== FileSystem.SYSTEM) {
        return null
    }
    val openFile = CreateFileA(
        lpFileName = file.toString(),
        dwDesiredAccess = GENERIC_READ,
        dwShareMode = (FILE_SHARE_READ or FILE_SHARE_WRITE or FILE_SHARE_DELETE).toUInt(),
        lpSecurityAttributes = null,
        dwCreationDisposition = OPEN_EXISTING.toUInt(),
        dwFlagsAndAttributes = FILE_ATTRIBUTE_NORMAL.toUInt(),
        hTemplateFile = null,
    )
    if (openFile == INVALID_HANDLE_VALUE) {
        throw IOException("openReadOnly failed for $file: ${GetLastError()}")
    }
    return SharedWindowsReadOnlyFileHandle(openFile)
}

private class SharedWindowsReadOnlyFileHandle(
    private val file: HANDLE?,
) : FileHandle(false) {
    override fun protectedSize(): Long {
        memScoped {
            val result = alloc<LARGE_INTEGER>()
            if (GetFileSizeEx(file, result.ptr) == 0) {
                throw IOException("GetFileSizeEx failed: ${GetLastError()}")
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
        if (array.isEmpty()) {
            return 0
        }
        val bytesRead =
            array.usePinned { pinned ->
                variantPread(pinned.addressOf(arrayOffset), byteCount, fileOffset)
            }
        if (bytesRead == 0) {
            return -1
        }
        return bytesRead
    }

    private fun variantPread(target: CValuesRef<*>, byteCount: Int, offset: Long): Int {
        memScoped {
            val overlapped = alloc<_OVERLAPPED>()
            overlapped.Offset = offset.toUInt()
            overlapped.OffsetHigh = (offset ushr 32).toUInt()
            val readFileResult = ReadFile(file, target.getPointer(this), byteCount.toUInt(), null, overlapped.ptr)
            if (readFileResult == 0) {
                return 0
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
        error("read-only handle")
    }

    override fun protectedFlush() = Unit

    override fun protectedResize(size: Long) {
        error("read-only handle")
    }

    override fun protectedClose() {
        if (CloseHandle(file) == 0) {
            throw IOException("CloseHandle failed: ${GetLastError()}")
        }
    }

    private fun LARGE_INTEGER.toLong(): Long {
        return (HighPart.toLong() shl 32) + (LowPart.toLong() and 0xffffffffL)
    }
}
