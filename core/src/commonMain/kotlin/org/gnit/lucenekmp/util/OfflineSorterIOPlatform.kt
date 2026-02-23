package org.gnit.lucenekmp.util

import okio.IOException
import org.gnit.lucenekmp.store.ChecksumIndexInput
import org.gnit.lucenekmp.store.IndexOutput

@Throws(IOException::class)
internal expect fun readLittleEndianUnsignedShort(input: ChecksumIndexInput, scratch: ByteArray): Int

@Throws(IOException::class)
internal expect fun writeLittleEndianShort(output: IndexOutput, value: Int, scratch: ByteArray)
