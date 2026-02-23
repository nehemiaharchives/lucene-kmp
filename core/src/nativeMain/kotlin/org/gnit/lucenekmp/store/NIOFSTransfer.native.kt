package org.gnit.lucenekmp.store

import okio.Buffer
import org.gnit.lucenekmp.jdkport.ByteBuffer
import okio.EOFException

internal actual fun transferTempOkioBufferToByteBuffer(
    tempOkioBuffer: Buffer,
    actualByteCount: Int,
    destination: ByteBuffer
) {
    val destinationArray = destination.array()
    var destinationOffset = destination.position
    var remaining = actualByteCount

    while (remaining > 0) {
        val bytesRead = tempOkioBuffer.read(destinationArray, destinationOffset, remaining)

        if (bytesRead <= 0) {
            throw EOFException("Unexpected EOF while draining NIOFS transfer buffer")
        }

        destinationOffset += bytesRead
        remaining -= bytesRead
    }

    destination.position(destinationOffset)
}
