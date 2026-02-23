package org.gnit.lucenekmp.util

import okio.IOException
import org.gnit.lucenekmp.store.ChecksumIndexInput
import org.gnit.lucenekmp.store.IndexOutput

@Throws(IOException::class)
internal actual fun readLittleEndianUnsignedShort(input: ChecksumIndexInput, scratch: ByteArray): Int {
    input.readBytes(scratch, 0, 2)
    return ((scratch[1].toInt() and 0xFF) shl 8) or (scratch[0].toInt() and 0xFF)
}

@Throws(IOException::class)
internal actual fun writeLittleEndianShort(output: IndexOutput, value: Int, scratch: ByteArray) {
    scratch[0] = (value and 0xFF).toByte()
    scratch[1] = ((value ushr 8) and 0xFF).toByte()
    output.writeBytes(scratch, 0, 2)
}
