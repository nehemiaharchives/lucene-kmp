package org.gnit.lucenekmp.internal.vectorization

import org.gnit.lucenekmp.store.IndexInput

internal actual fun splitIntsPlatform(
    input: IndexInput,
    count: Int,
    b: IntArray,
    bShift: Int,
    dec: Int,
    bMask: Int,
    c: IntArray,
    cIndex: Int,
    cMask: Int
) {
    input.readInts(c, cIndex, count)
    val maxIter = (bShift - 1) / dec
    for (i in 0..<count) {
        val ci = cIndex + i
        val value = c[ci]
        for (j in 0..maxIter) {
            b[count * j + i] = (value ushr (bShift - j * dec)) and bMask
        }
        c[ci] = value and cMask
    }
}
