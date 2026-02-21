package org.gnit.lucenekmp.jdkport

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
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
 * Native [KmpSink] that keeps Okio [BufferedSink] semantics while using a faster
 * bulk byte-array write path via `UnsafeCursor` + `memcpy`.
 *
 * This avoids the slower generic Kotlin/Native byte-array write path in hot loops with many
 * small records while preserving output bytes, ordering, flushing and close behavior.
 */
private class NativeKmpSink(
    private val sink: BufferedSink
) : KmpSink {
    private val pending = ByteArray(32768)
    private var pendingSize = 0

    private fun flushPending() {
        if (pendingSize == 0) {
            return
        }
        writeToSinkBufferNative(sink, pending, 0, pendingSize)
        pendingSize = 0
    }

    override fun writeByte(b: Int) {
        if (pendingSize == pending.size) {
            flushPending()
        }
        pending[pendingSize] = b.toByte()
        pendingSize++
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        if (len == 0) {
            return
        }

        if (len >= pending.size) {
            flushPending()
            writeToSinkBufferNative(sink, b, off, len)
            return
        }

        if (pendingSize + len > pending.size) {
            flushPending()
        }

        b.copyInto(
            destination = pending,
            destinationOffset = pendingSize,
            startIndex = off,
            endIndex = off + len
        )
        pendingSize += len
    }

    override fun flush() {
        flushPending()
        sink.flush()
    }

    override fun close() {
        flushPending()
        sink.close()
    }
}

actual fun kmpSink(sink: BufferedSink): KmpSink = NativeKmpSink(sink)
