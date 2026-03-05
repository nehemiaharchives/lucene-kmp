package org.gnit.lucenekmp.jdkport

internal actual inline fun byteBufferGetIntPlatform(array: ByteArray, index: Int, bigEndian: Boolean): Int {
    return if (bigEndian) {
        ((array[index].toInt() and 0xFF) shl 24) or
            ((array[index + 1].toInt() and 0xFF) shl 16) or
            ((array[index + 2].toInt() and 0xFF) shl 8) or
            (array[index + 3].toInt() and 0xFF)
    } else {
        (array[index].toInt() and 0xFF) or
            ((array[index + 1].toInt() and 0xFF) shl 8) or
            ((array[index + 2].toInt() and 0xFF) shl 16) or
            ((array[index + 3].toInt() and 0xFF) shl 24)
    }
}

internal actual inline fun byteBufferGetShortPlatform(array: ByteArray, index: Int, bigEndian: Boolean): Short {
    return if (bigEndian) {
        val hi = array[index].toInt() and 0xFF
        val lo = array[index + 1].toInt() and 0xFF
        ((hi shl 8) or lo).toShort()
    } else {
        val lo = array[index].toInt() and 0xFF
        val hi = array[index + 1].toInt() and 0xFF
        ((hi shl 8) or lo).toShort()
    }
}

internal actual inline fun byteBufferGetLongPlatform(array: ByteArray, index: Int, bigEndian: Boolean): Long {
    return if (bigEndian) {
        ((array[index].toLong() and 0xFF) shl 56) or
            ((array[index + 1].toLong() and 0xFF) shl 48) or
            ((array[index + 2].toLong() and 0xFF) shl 40) or
            ((array[index + 3].toLong() and 0xFF) shl 32) or
            ((array[index + 4].toLong() and 0xFF) shl 24) or
            ((array[index + 5].toLong() and 0xFF) shl 16) or
            ((array[index + 6].toLong() and 0xFF) shl 8) or
            (array[index + 7].toLong() and 0xFF)
    } else {
        (array[index].toLong() and 0xFF) or
            ((array[index + 1].toLong() and 0xFF) shl 8) or
            ((array[index + 2].toLong() and 0xFF) shl 16) or
            ((array[index + 3].toLong() and 0xFF) shl 24) or
            ((array[index + 4].toLong() and 0xFF) shl 32) or
            ((array[index + 5].toLong() and 0xFF) shl 40) or
            ((array[index + 6].toLong() and 0xFF) shl 48) or
            ((array[index + 7].toLong() and 0xFF) shl 56)
    }
}

internal actual inline fun byteBufferProfileGettersPlatform(): Boolean = true
