package org.gnit.lucenekmp.store

import okio.Buffer
import org.gnit.lucenekmp.jdkport.ByteBuffer

internal actual fun transferTempOkioBufferToByteBuffer(
    tempOkioBuffer: Buffer,
    actualByteCount: Int,
    destination: ByteBuffer
) {
    val dataToTransfer = tempOkioBuffer.readByteArray(actualByteCount.toLong())
    destination.put(dataToTransfer)
}
