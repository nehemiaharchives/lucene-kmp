@file:OptIn(ExperimentalForeignApi::class)

package org.gnit.lucenekmp.jdkport

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.posix.memcmp

private inline fun equalsByMemcmp(
    a: ByteArray,
    aFromIndex: Int,
    b: ByteArray,
    bFromIndex: Int,
    length: Int
): Boolean {
    if (length == 0) return true
    return a.usePinned { aPinned ->
        b.usePinned { bPinned ->
            memcmp(
                aPinned.addressOf(aFromIndex),
                bPinned.addressOf(bFromIndex),
                length.convert()
            ) == 0
        }
    }
}

private inline fun equalsByMemcmp(
    a: IntArray,
    aFromIndex: Int,
    b: IntArray,
    bFromIndex: Int,
    length: Int
): Boolean {
    if (length == 0) return true
    val bytesToCompare = length * Int.SIZE_BYTES
    return a.usePinned { aPinned ->
        b.usePinned { bPinned ->
            memcmp(
                aPinned.addressOf(aFromIndex),
                bPinned.addressOf(bFromIndex),
                bytesToCompare.convert()
            ) == 0
        }
    }
}

private inline fun equalsByMemcmp(
    a: LongArray,
    aFromIndex: Int,
    b: LongArray,
    bFromIndex: Int,
    length: Int
): Boolean {
    if (length == 0) return true
    val bytesToCompare = length * Long.SIZE_BYTES
    return a.usePinned { aPinned ->
        b.usePinned { bPinned ->
            memcmp(
                aPinned.addressOf(aFromIndex),
                bPinned.addressOf(bFromIndex),
                bytesToCompare.convert()
            ) == 0
        }
    }
}

private inline fun equalsByMemcmp(
    a: CharArray,
    aFromIndex: Int,
    b: CharArray,
    bFromIndex: Int,
    length: Int
): Boolean {
    if (length == 0) return true
    val bytesToCompare = length * Char.SIZE_BYTES
    return a.usePinned { aPinned ->
        b.usePinned { bPinned ->
            memcmp(
                aPinned.addressOf(aFromIndex),
                bPinned.addressOf(bFromIndex),
                bytesToCompare.convert()
            ) == 0
        }
    }
}

private inline fun equalsByMemcmp(
    a: FloatArray,
    aFromIndex: Int,
    b: FloatArray,
    bFromIndex: Int,
    length: Int
): Boolean {
    if (length == 0) return true
    val bytesToCompare = length * Float.SIZE_BYTES
    return a.usePinned { aPinned ->
        b.usePinned { bPinned ->
            memcmp(
                aPinned.addressOf(aFromIndex),
                bPinned.addressOf(bFromIndex),
                bytesToCompare.convert()
            ) == 0
        }
    }
}

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

    return equalsByMemcmp(a, aFromIndex, b, bFromIndex, aLength)
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

    return equalsByMemcmp(a, aFromIndex, b, bFromIndex, aLength)
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

    return equalsByMemcmp(a, aFromIndex, b, bFromIndex, aLength)
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

    return equalsByMemcmp(a, aFromIndex, b, bFromIndex, aLength)
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

    return equalsByMemcmp(a, aFromIndex, b, bFromIndex, aLength)
}
