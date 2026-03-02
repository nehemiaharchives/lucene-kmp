package org.gnit.lucenekmp.jdkport

actual fun stringBuilderGetChars(
    src: StringBuilder,
    srcBegin: Int,
    srcEnd: Int,
    dst: CharArray,
    dstBegin: Int
) {
    src.substring(srcBegin, srcEnd).toCharArray().copyInto(dst, dstBegin)
}
