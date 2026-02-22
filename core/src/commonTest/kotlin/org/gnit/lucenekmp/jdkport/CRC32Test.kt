package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.TimeSource

class CRC32Test {

    private data class PerfStats(
        val elapsedNs: Long,
        val bytesProcessed: Long
    )

    private fun formatMbPerSec(stats: PerfStats): Double {
        if (stats.elapsedNs <= 0L) return 0.0
        val seconds = stats.elapsedNs.toDouble() / 1_000_000_000.0
        val megaBytes = stats.bytesProcessed.toDouble() / (1024.0 * 1024.0)
        return megaBytes / seconds
    }

    private fun formatNsPerByte(stats: PerfStats): Double {
        if (stats.bytesProcessed <= 0L) return 0.0
        return stats.elapsedNs.toDouble() / stats.bytesProcessed.toDouble()
    }

    @Test
    fun testUpdate() {
        val crc32 = CRC32()
        val data = "Hello, World!".encodeToByteArray()
        crc32.update(data, 0, data.size)
        assertEquals(0xEC4AC3D0, crc32.getValue())
    }

    @Test
    fun testGetValue() {
        val crc32 = CRC32()
        val data = "Hello, World!".encodeToByteArray()
        crc32.update(data, 0, data.size)
        assertEquals(0xEC4AC3D0, crc32.getValue())
    }

    @Test
    fun testReset() {
        val crc32 = CRC32()
        val data = "Hello, World!".encodeToByteArray()
        crc32.update(data, 0, data.size)
        crc32.reset()
        assertEquals(0, crc32.getValue())
    }

    @Test
    fun testPerformanceUpdate() {
        val payloadSize = 256 * 1024
        val iterations = 800
        val payload = ByteArray(payloadSize) { ((it * 17) and 0xFF).toByte() }

        repeat(100) {
            val warmup = CRC32()
            warmup.update(payload, 0, payload.size)
        }

        val crc32 = CRC32()
        val start = TimeSource.Monotonic.markNow()
        repeat(iterations) {
            crc32.update(payload, 0, payload.size)
        }
        val elapsedNs = start.elapsedNow().inWholeNanoseconds
        val totalBytes = payloadSize.toLong() * iterations.toLong()
        val stats = PerfStats(elapsedNs = elapsedNs, bytesProcessed = totalBytes)

        println(
            "[PERF][CRC32][update] payloadBytes=$payloadSize iterations=$iterations " +
                "elapsedNs=${stats.elapsedNs} totalBytes=${stats.bytesProcessed} " +
                "nsPerByte=${formatNsPerByte(stats)} MBps=${formatMbPerSec(stats)} crc=${crc32.getValue()}"
        )

        assertTrue(crc32.getValue() >= 0L)
    }
}
