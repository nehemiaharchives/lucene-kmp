package org.gnit.lucenekmp.jdkport

internal expect fun arraysEqualsByteRange(
    a: ByteArray, aFromIndex: Int,
    b: ByteArray, bFromIndex: Int,
    aToIndex: Int,
    bToIndex: Int
): Boolean

internal expect fun arraysEqualsLongRange(
    a: LongArray, aFromIndex: Int,
    b: LongArray, bFromIndex: Int,
    aToIndex: Int,
    bToIndex: Int
): Boolean

internal expect fun arraysEqualsFloatRange(
    a: FloatArray, aFromIndex: Int,
    b: FloatArray, bFromIndex: Int,
    aToIndex: Int,
    bToIndex: Int
): Boolean

internal expect fun arraysEqualsCharRange(
    a: CharArray, aFromIndex: Int,
    b: CharArray, bFromIndex: Int,
    aToIndex: Int,
    bToIndex: Int
): Boolean

internal expect fun arraysEqualsIntRange(
    a: IntArray, aFromIndex: Int,
    b: IntArray, bFromIndex: Int,
    aToIndex: Int,
    bToIndex: Int
): Boolean
