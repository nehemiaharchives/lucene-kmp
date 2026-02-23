package org.gnit.lucenekmp.store

internal fun readBytesWithChecksum(
    main: IndexInput,
    digest: BufferedChecksum,
    buffer: ByteArray,
    offset: Int,
    len: Int
) {
    main.readBytes(buffer, offset, len)
    digest.update(buffer, offset, len)
}
