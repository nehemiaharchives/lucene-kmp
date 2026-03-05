package org.gnit.lucenekmp.jdkport

internal expect inline fun byteBufferGetIntPlatform(array: ByteArray, index: Int, bigEndian: Boolean): Int

internal expect inline fun byteBufferGetShortPlatform(array: ByteArray, index: Int, bigEndian: Boolean): Short

internal expect inline fun byteBufferGetLongPlatform(array: ByteArray, index: Int, bigEndian: Boolean): Long

internal expect inline fun byteBufferProfileGettersPlatform(): Boolean
