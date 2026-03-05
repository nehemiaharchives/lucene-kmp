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
    var i = 0
    while (i < count) {
        val ci = cIndex + i
        val value = c[ci]
        var j = 0
        while (j <= maxIter) {
            b[count * j + i] = (value ushr (bShift - j * dec)) and bMask
            j++
        }
        c[ci] = value and cMask
        i++
    }
}
