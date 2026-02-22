package org.gnit.lucenekmp.store

import okio.Buffer
import org.gnit.lucenekmp.jdkport.ByteBuffer

internal expect fun transferTempOkioBufferToByteBuffer(
    tempOkioBuffer: Buffer,
    actualByteCount: Int,
    destination: ByteBuffer
)
