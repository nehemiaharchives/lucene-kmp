@file:OptIn(ExperimentalAtomicApi::class)

package org.gnit.lucenekmp.store

import io.github.oshai.kotlinlogging.KotlinLogging
import okio.Buffer
import org.gnit.lucenekmp.jdkport.ByteBuffer
import okio.EOFException
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.TimeSource

private val logger = KotlinLogging.logger {}
private const val LOG_SAMPLE_EVERY_CALLS = 50_000L
private val transferCallCounter = AtomicLong(0L)

internal actual fun transferTempOkioBufferToByteBuffer(
    tempOkioBuffer: Buffer,
    actualByteCount: Int,
    destination: ByteBuffer
) {
    val callNumber = transferCallCounter.addAndFetch(1L)
    val shouldLog = (callNumber % LOG_SAMPLE_EVERY_CALLS) == 0L
    val totalStart = TimeSource.Monotonic.markNow()

    val prepareStart = TimeSource.Monotonic.markNow()
    val destinationArray = destination.array()
    var destinationOffset = destination.position
    var remaining = actualByteCount
    val prepareElapsedNs = prepareStart.elapsedNow().inWholeNanoseconds

    var readElapsedNs = 0L
    var advanceElapsedNs = 0L
    var iterations = 0
    var bytesReadTotal = 0L

    while (remaining > 0) {
        val readStart = TimeSource.Monotonic.markNow()
        val bytesRead = tempOkioBuffer.read(destinationArray, destinationOffset, remaining)
        readElapsedNs += readStart.elapsedNow().inWholeNanoseconds
        iterations++

        if (bytesRead <= 0) {
            val failedElapsedNs = totalStart.elapsedNow().inWholeNanoseconds
            val message =
                "[NIOFSTransfer] eofError call=$callNumber iterations=$iterations remaining=$remaining " +
                    "bytesRead=$bytesRead bytesReadTotal=$bytesReadTotal totalNs=$failedElapsedNs " +
                    "actualByteCount=$actualByteCount"
            logger.info { message }
            println(message)
            throw EOFException("Unexpected EOF while draining NIOFS transfer buffer")
        }

        val advanceStart = TimeSource.Monotonic.markNow()
        destinationOffset += bytesRead
        remaining -= bytesRead
        advanceElapsedNs += advanceStart.elapsedNow().inWholeNanoseconds
        bytesReadTotal += bytesRead.toLong()
    }

    val positionStart = TimeSource.Monotonic.markNow()
    destination.position(destinationOffset)
    val positionElapsedNs = positionStart.elapsedNow().inWholeNanoseconds

    val totalElapsedNs = totalStart.elapsedNow().inWholeNanoseconds
    if (shouldLog) {
        val message =
            "[NIOFSTransfer] sample call=$callNumber actualByteCount=$actualByteCount " +
                "bytesReadTotal=$bytesReadTotal iterations=$iterations prepareNs=$prepareElapsedNs " +
                "readNs=$readElapsedNs advanceNs=$advanceElapsedNs positionNs=$positionElapsedNs " +
                "totalNs=$totalElapsedNs"
        logger.info { message }
        println(message)
    }
}
