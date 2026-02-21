package org.gnit.lucenekmp.jdkport

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import okio.Buffer
import okio.BufferedSink
import okio.IOException
import okio.use
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
private fun writeToSinkBufferNative(
    sink: BufferedSink,
    b: ByteArray,
    off: Int,
    len: Int
) {
    val targetBuffer = sink.buffer
    targetBuffer.readAndWriteUnsafe().use { cursor ->
        val writeStartOffset = targetBuffer.size
        cursor.resizeBuffer(writeStartOffset + len.toLong())

        var sourceOffset = off
        var remaining = len
        var segmentByteCount = cursor.seek(writeStartOffset)
        require(segmentByteCount != -1) { "Failed to seek writable segment at $writeStartOffset" }

        b.usePinned { sourcePinned ->
            while (remaining > 0) {
                val destination = cursor.data ?: throw IOException("No destination segment available")
                val writableInSegment = cursor.end - cursor.start
                if (writableInSegment <= 0) {
                    segmentByteCount = cursor.next()
                    if (segmentByteCount == -1) {
                        throw IOException("Unexpected end of writable segments")
                    }
                    continue
                }

                val toCopy = minOf(remaining, writableInSegment)
                destination.usePinned { destinationPinned ->
                    memcpy(
                        destinationPinned.addressOf(cursor.start),
                        sourcePinned.addressOf(sourceOffset),
                        toCopy.toULong()
                    )
                }

                sourceOffset += toCopy
                remaining -= toCopy

                if (remaining > 0) {
                    segmentByteCount = cursor.next()
                    if (segmentByteCount == -1) {
                        throw IOException("Unexpected end of writable segments")
                    }
                }
            }
        }
    }

    // Mirror RealBufferedSink.write(...) behavior: push complete segments downstream promptly.
    sink.emitCompleteSegments()
}

/**
 * Native implementation of [kmpWrite].
 *
 * This avoids the slow generic byte-array path observed on Kotlin/Native by copying directly
 * into Okio buffer segments via `UnsafeCursor` + `memcpy`, then emitting complete segments.
 */
actual fun kmpWrite(
    sink: BufferedSink?,
    buffer: Buffer?,
    b: ByteArray,
    off: Int,
    len: Int
) {
    if (sink != null) {
        writeToSinkBufferNative(sink, b, off, len)
        return
    }
    if (buffer != null) {
        buffer.write(b, off, len)
        return
    }
    throw IOException("No sink or buffer available")
}
