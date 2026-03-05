package org.gnit.lucenekmp.jdkport

internal actual inline fun longNumberOfLeadingZerosPlatform(value: Long): Int =
    java.lang.Long.numberOfLeadingZeros(value)

internal actual inline fun longNumberOfTrailingZerosPlatform(value: Long): Int =
    java.lang.Long.numberOfTrailingZeros(value)

internal actual inline fun longBitCountPlatform(value: Long): Int =
    java.lang.Long.bitCount(value)
