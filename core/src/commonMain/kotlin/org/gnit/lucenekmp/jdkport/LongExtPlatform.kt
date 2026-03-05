package org.gnit.lucenekmp.jdkport

internal expect inline fun longNumberOfLeadingZerosPlatform(value: Long): Int

internal expect inline fun longNumberOfTrailingZerosPlatform(value: Long): Int

internal expect inline fun longBitCountPlatform(value: Long): Int
