package org.gnit.lucenekmp.jdkport

import okio.Buffer
import okio.BufferedSink
import okio.IOException

actual fun kmpWrite(
    sink: BufferedSink?,
    buffer: Buffer?,
    b: ByteArray,
    off: Int,
    len: Int
) {
    if (sink != null) {
        sink.write(b, off, len)
        return
    }
    if (buffer != null) {
        buffer.write(b, off, len)
        return
    }
    throw IOException("No sink or buffer available")
}
