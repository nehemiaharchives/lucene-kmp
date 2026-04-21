package org.gnit.lucenekmp.jdkport

internal actual fun arraysEqualsByteRange(
    a: ByteArray, aFromIndex: Int,
    b: ByteArray, bFromIndex: Int,
    aToIndex: Int,
    bToIndex: Int
): Boolean {
    Arrays.rangeCheck(a.size, aFromIndex, aToIndex)
    Arrays.rangeCheck(b.size, bFromIndex, bToIndex)

    val aLength = aToIndex - aFromIndex
    val bLength = bToIndex - bFromIndex
    if (aLength != bLength) return false

    for (i in 0 until aLength) {
        if (a[aFromIndex + i] != b[bFromIndex + i]) return false
    }
    return true
}

internal actual fun arraysEqualsLongRange(
    a: LongArray, aFromIndex: Int,
    b: LongArray, bFromIndex: Int,
    aToIndex: Int,
    bToIndex: Int
): Boolean {
    if (aFromIndex > aToIndex) throw IllegalArgumentException("aFromIndex ($aFromIndex) > aToIndex ($aToIndex)")
    if (bFromIndex > bToIndex) throw IllegalArgumentException("bFromIndex ($bFromIndex) > bToIndex ($bToIndex)")
    if (aFromIndex < 0 || aToIndex > a.size) {
        throw IndexOutOfBoundsException("Range [$aFromIndex, $aToIndex) out of bounds for array of size ${a.size}")
    }
    if (bFromIndex < 0 || bToIndex > b.size) {
        throw IndexOutOfBoundsException("Range [$bFromIndex, $bToIndex) out of bounds for array of size ${b.size}")
    }

    val aLength = aToIndex - aFromIndex
    val bLength = bToIndex - bFromIndex
    if (aLength != bLength) return false

    for (i in 0 until aLength) {
        if (a[aFromIndex + i] != b[bFromIndex + i]) return false
    }
    return true
}

internal actual fun arraysEqualsFloatRange(
    a: FloatArray, aFromIndex: Int,
    b: FloatArray, bFromIndex: Int,
    aToIndex: Int,
    bToIndex: Int
): Boolean {
    Arrays.rangeCheck(a.size, aFromIndex, aToIndex)
    Arrays.rangeCheck(b.size, bFromIndex, bToIndex)

    val aLength = aToIndex - aFromIndex
    val bLength = bToIndex - bFromIndex
    if (aLength != bLength) return false

    for (i in 0 until aLength) {
        if (Float.floatToIntBits(a[aFromIndex + i]) != Float.floatToIntBits(b[bFromIndex + i])) return false
    }
    return true
}

internal actual fun arraysEqualsCharRange(
    a: CharArray, aFromIndex: Int,
    b: CharArray, bFromIndex: Int,
    aToIndex: Int,
    bToIndex: Int
): Boolean {
    if (aFromIndex > aToIndex) throw IllegalArgumentException("aFromIndex ($aFromIndex) > aToIndex ($aToIndex)")
    if (bFromIndex > bToIndex) throw IllegalArgumentException("bFromIndex ($bFromIndex) > bToIndex ($bToIndex)")
    if (aFromIndex < 0 || aToIndex > a.size) {
        throw IndexOutOfBoundsException("Range [$aFromIndex, $aToIndex) out of bounds for array of size ${a.size}")
    }
    if (bFromIndex < 0 || bToIndex > b.size) {
        throw IndexOutOfBoundsException("Range [$bFromIndex, $bToIndex) out of bounds for array of size ${b.size}")
    }

    val aLength = aToIndex - aFromIndex
    val bLength = bToIndex - bFromIndex
    if (aLength != bLength) return false

    for (i in 0 until aLength) {
        if (a[aFromIndex + i] != b[bFromIndex + i]) return false
    }
    return true
}

internal actual fun arraysEqualsIntRange(
    a: IntArray, aFromIndex: Int,
    b: IntArray, bFromIndex: Int,
    aToIndex: Int,
    bToIndex: Int
): Boolean {
    Arrays.rangeCheck(a.size, aFromIndex, aToIndex)
    Arrays.rangeCheck(b.size, bFromIndex, bToIndex)

    val aLength = aToIndex - aFromIndex
    val bLength = bToIndex - bFromIndex
    if (aLength != bLength) return false

    for (i in 0 until aLength) {
        if (a[aFromIndex + i] != b[bFromIndex + i]) return false
    }
    return true
}
