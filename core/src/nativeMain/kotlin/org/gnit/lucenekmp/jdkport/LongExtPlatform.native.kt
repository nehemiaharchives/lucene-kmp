package org.gnit.lucenekmp.jdkport

internal actual inline fun longNumberOfLeadingZerosPlatform(value: Long): Int {
    return value.countLeadingZeroBits()
}

internal actual inline fun longNumberOfTrailingZerosPlatform(value: Long): Int {
    return value.countTrailingZeroBits()
}

internal actual inline fun longBitCountPlatform(value: Long): Int {
    return value.countOneBits()
}
