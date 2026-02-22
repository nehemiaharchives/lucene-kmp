package org.gnit.lucenekmp.store

import kotlin.time.TimeSource

internal data class ReadBytesChecksumStepTimesNs(
    val delegateReadNs: Long,
    val checksumUpdateNs: Long
)

internal fun readBytesWithChecksumStepTimesNs(
    main: IndexInput,
    digest: BufferedChecksum,
    buffer: ByteArray,
    offset: Int,
    len: Int
): ReadBytesChecksumStepTimesNs {
    val readStart = TimeSource.Monotonic.markNow()
    main.readBytes(buffer, offset, len)
    val delegateReadNs = readStart.elapsedNow().inWholeNanoseconds

    val checksumStart = TimeSource.Monotonic.markNow()
    digest.update(buffer, offset, len)
    val checksumUpdateNs = checksumStart.elapsedNow().inWholeNanoseconds

    return ReadBytesChecksumStepTimesNs(
        delegateReadNs = delegateReadNs,
        checksumUpdateNs = checksumUpdateNs
    )
}
