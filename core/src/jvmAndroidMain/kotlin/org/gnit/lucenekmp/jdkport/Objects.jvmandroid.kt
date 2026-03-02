package org.gnit.lucenekmp.jdkport

actual fun stringBuilderGetChars(
    src: StringBuilder,
    srcBegin: Int,
    srcEnd: Int,
    dst: CharArray,
    dstBegin: Int
) {
    for (i in srcBegin until srcEnd) {
        dst[dstBegin + i - srcBegin] = src[i]
    }
}
