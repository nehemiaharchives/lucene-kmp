package org.gnit.lucenekmp.jdkport

import okio.BufferedSink

private class NativeKmpSink(
    private val sink: BufferedSink
) : KmpSink {
    override fun writeByte(b: Int) {
        sink.writeByte(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        sink.write(b, off, len)
    }

    override fun flush() {
        sink.flush()
    }

    override fun close() {
        sink.close()
    }
}

actual fun kmpSink(sink: BufferedSink): KmpSink = NativeKmpSink(sink)
