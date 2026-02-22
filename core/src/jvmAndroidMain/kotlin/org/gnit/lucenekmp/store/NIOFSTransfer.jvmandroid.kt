@file:OptIn(ExperimentalAtomicApi::class)

package org.gnit.lucenekmp.store

import io.github.oshai.kotlinlogging.KotlinLogging
import okio.Buffer
import org.gnit.lucenekmp.jdkport.ByteBuffer
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
    val prepareElapsedNs = prepareStart.elapsedNow().inWholeNanoseconds

    val readStart = TimeSource.Monotonic.markNow()
    val dataToTransfer = tempOkioBuffer.readByteArray(actualByteCount.toLong())
    val readElapsedNs = readStart.elapsedNow().inWholeNanoseconds

    val advanceStart = TimeSource.Monotonic.markNow()
    destination.put(dataToTransfer)
    val advanceElapsedNs = advanceStart.elapsedNow().inWholeNanoseconds

    val positionStart = TimeSource.Monotonic.markNow()
    val positionElapsedNs = positionStart.elapsedNow().inWholeNanoseconds

    val totalElapsedNs = totalStart.elapsedNow().inWholeNanoseconds
    if (shouldLog) {
        val message =
            "[NIOFSTransfer] sample call=$callNumber actualByteCount=$actualByteCount " +
                "bytesReadTotal=${dataToTransfer.size} iterations=1 prepareNs=$prepareElapsedNs " +
                "readNs=$readElapsedNs advanceNs=$advanceElapsedNs positionNs=$positionElapsedNs " +
                "totalNs=$totalElapsedNs"
        logger.info { message }
        println(message)
    }
}
