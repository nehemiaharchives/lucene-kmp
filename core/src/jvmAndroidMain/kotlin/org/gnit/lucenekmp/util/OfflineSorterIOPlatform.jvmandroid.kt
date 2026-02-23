package org.gnit.lucenekmp.util

import okio.IOException
import org.gnit.lucenekmp.store.ChecksumIndexInput
import org.gnit.lucenekmp.store.IndexOutput

@Throws(IOException::class)
internal actual fun readLittleEndianUnsignedShort(input: ChecksumIndexInput, scratch: ByteArray): Int {
    return input.readShort().toInt() and 0xFFFF
}

@Throws(IOException::class)
internal actual fun writeLittleEndianShort(output: IndexOutput, value: Int, scratch: ByteArray) {
    output.writeShort(value.toShort())
}
