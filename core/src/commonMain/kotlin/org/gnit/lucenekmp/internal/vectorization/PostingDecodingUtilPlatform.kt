package org.gnit.lucenekmp.internal.vectorization

import org.gnit.lucenekmp.store.IndexInput

internal expect fun splitIntsPlatform(
    input: IndexInput,
    count: Int,
    b: IntArray,
    bShift: Int,
    dec: Int,
    bMask: Int,
    c: IntArray,
    cIndex: Int,
    cMask: Int
)
