package org.gnit.lucenekmp.jdkport

internal actual inline fun byteBufferGetIntPlatform(array: ByteArray, index: Int, bigEndian: Boolean): Int {
    if (bigEndian) {
        return (array[index].toInt() shl 24) or
            ((array[index + 1].toInt() and 0xFF) shl 16) or
            ((array[index + 2].toInt() and 0xFF) shl 8) or
            (array[index + 3].toInt() and 0xFF)
    }
    return (array[index].toInt() and 0xFF) or
        ((array[index + 1].toInt() and 0xFF) shl 8) or
        ((array[index + 2].toInt() and 0xFF) shl 16) or
        (array[index + 3].toInt() shl 24)
}

internal actual inline fun byteBufferGetShortPlatform(array: ByteArray, index: Int, bigEndian: Boolean): Short {
    if (bigEndian) {
        return ((array[index].toInt() shl 8) or
            (array[index + 1].toInt() and 0xFF)).toShort()
    }
    return ((array[index + 1].toInt() shl 8) or
        (array[index].toInt() and 0xFF)).toShort()
}

internal actual inline fun byteBufferGetLongPlatform(array: ByteArray, index: Int, bigEndian: Boolean): Long {
    if (bigEndian) {
        val hi = (array[index].toInt() shl 24) or
            ((array[index + 1].toInt() and 0xFF) shl 16) or
            ((array[index + 2].toInt() and 0xFF) shl 8) or
            (array[index + 3].toInt() and 0xFF)
        val lo = (array[index + 4].toInt() shl 24) or
            ((array[index + 5].toInt() and 0xFF) shl 16) or
            ((array[index + 6].toInt() and 0xFF) shl 8) or
            (array[index + 7].toInt() and 0xFF)
        return (hi.toLong() shl 32) or (lo.toLong() and 0xFFFFFFFFL)
    }
    val lo = (array[index].toInt() and 0xFF) or
        ((array[index + 1].toInt() and 0xFF) shl 8) or
        ((array[index + 2].toInt() and 0xFF) shl 16) or
        (array[index + 3].toInt() shl 24)
    val hi = (array[index + 4].toInt() and 0xFF) or
        ((array[index + 5].toInt() and 0xFF) shl 8) or
        ((array[index + 6].toInt() and 0xFF) shl 16) or
        (array[index + 7].toInt() shl 24)
    return (lo.toLong() and 0xFFFFFFFFL) or (hi.toLong() shl 32)
}

internal actual inline fun byteBufferProfileGettersPlatform(): Boolean = false
