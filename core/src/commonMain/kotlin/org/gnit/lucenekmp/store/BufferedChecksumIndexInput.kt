@file:OptIn(ExperimentalAtomicApi::class)

package org.gnit.lucenekmp.store

import okio.IOException
import org.gnit.lucenekmp.jdkport.CRC32
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Clock


/**
 * Simple implementation of [ChecksumIndexInput] that wraps another input and delegates calls.
 */
class BufferedChecksumIndexInput(val main: IndexInput) :
    ChecksumIndexInput("BufferedChecksumIndexInput($main)") {
    val digest: BufferedChecksum = BufferedChecksum(CRC32())

    @Throws(IOException::class)
    override fun readByte(): Byte {
        if (!isProfileEnabled()) {
            val b = main.readByte()
            digest.update(b.toInt())
            return b
        }

        readByteCalls.addAndFetch(1L)
        val readStartMs = Clock.System.now().toEpochMilliseconds()
        val b = main.readByte()
        readByteDelegateReadMs.addAndFetch(Clock.System.now().toEpochMilliseconds() - readStartMs)
        val checksumStartMs = Clock.System.now().toEpochMilliseconds()
        digest.update(b.toInt())
        readByteChecksumMs.addAndFetch(Clock.System.now().toEpochMilliseconds() - checksumStartMs)
        return b
    }

    @Throws(IOException::class)
    override fun readBytes(b: ByteArray, offset: Int, len: Int) {
        if (!isProfileEnabled()) {
            main.readBytes(b, offset, len)
            digest.update(b, offset, len)
            return
        }

        readBytesCalls.addAndFetch(1L)
        readBytesRequestedBytes.addAndFetch(len.toLong())
        val stepTimesNs = readBytesWithChecksumStepTimesNs(
            main = main,
            digest = digest,
            buffer = b,
            offset = offset,
            len = len,
            collectTiming = true
        )
        readBytesDelegateReadNs.addAndFetch(stepTimesNs.delegateReadNs)
        readBytesChecksumNs.addAndFetch(stepTimesNs.checksumUpdateNs)
        readBytesDelegateReadMs.addAndFetch(stepTimesNs.delegateReadNs / 1_000_000L)
        readBytesChecksumMs.addAndFetch(stepTimesNs.checksumUpdateNs / 1_000_000L)
    }

    @Throws(IOException::class)
    override fun readShort(): Short {
        val v = main.readShort()
        digest.updateShort(v)
        return v
    }

    @Throws(IOException::class)
    override fun readInt(): Int {
        val v = main.readInt()
        digest.updateInt(v)
        return v
    }

    @Throws(IOException::class)
    override fun readLong(): Long {
        val v = main.readLong()
        digest.updateLong(v)
        return v
    }

    @Throws(IOException::class)
    override fun readLongs(dst: LongArray, offset: Int, length: Int) {
        main.readLongs(dst, offset, length)
        digest.updateLongs(dst, offset, length)
    }

    override val checksum: Long
        get() = digest.getValue()

    @Throws(IOException::class)
    override fun close() {
        main.close()
    }

    override val filePointer: Long
        get() = main.filePointer

    override fun length(): Long {
        return main.length()
    }

    override fun clone(): IndexInput {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun slice(sliceDescription: String, offset: Long, length: Long): IndexInput {
        throw UnsupportedOperationException()
    }

    companion object {
        @OptIn(ExperimentalAtomicApi::class)
        private val readByteCalls: AtomicLong = AtomicLong(0L)
        private val readByteDelegateReadMs: AtomicLong = AtomicLong(0L)
        private val readByteChecksumMs: AtomicLong = AtomicLong(0L)
        private val readBytesCalls: AtomicLong = AtomicLong(0L)
        private val readBytesRequestedBytes: AtomicLong = AtomicLong(0L)
        private val readBytesDelegateReadMs: AtomicLong = AtomicLong(0L)
        private val readBytesChecksumMs: AtomicLong = AtomicLong(0L)
        private val readBytesDelegateReadNs: AtomicLong = AtomicLong(0L)
        private val readBytesChecksumNs: AtomicLong = AtomicLong(0L)
        private var profileEnabled: Boolean = false

        data class Profile(
            val readByteCalls: Long,
            val readByteDelegateReadMs: Long,
            val readByteChecksumMs: Long,
            val readBytesCalls: Long,
            val readBytesRequestedBytes: Long,
            val readBytesDelegateReadMs: Long,
            val readBytesChecksumMs: Long,
            val readBytesDelegateReadNs: Long,
            val readBytesChecksumNs: Long
        )

        @OptIn(ExperimentalAtomicApi::class)
        fun resetProfile() {
            readByteCalls.store(0L)
            readByteDelegateReadMs.store(0L)
            readByteChecksumMs.store(0L)
            readBytesCalls.store(0L)
            readBytesRequestedBytes.store(0L)
            readBytesDelegateReadMs.store(0L)
            readBytesChecksumMs.store(0L)
            readBytesDelegateReadNs.store(0L)
            readBytesChecksumNs.store(0L)
        }

        fun enableProfile() {
            profileEnabled = true
        }

        fun disableProfile() {
            profileEnabled = false
        }

        fun isProfileEnabled(): Boolean {
            return profileEnabled
        }

        @OptIn(ExperimentalAtomicApi::class)
        fun profile(): Profile {
            return Profile(
                readByteCalls = readByteCalls.load(),
                readByteDelegateReadMs = readByteDelegateReadMs.load(),
                readByteChecksumMs = readByteChecksumMs.load(),
                readBytesCalls = readBytesCalls.load(),
                readBytesRequestedBytes = readBytesRequestedBytes.load(),
                readBytesDelegateReadMs = readBytesDelegateReadMs.load(),
                readBytesChecksumMs = readBytesChecksumMs.load(),
                readBytesDelegateReadNs = readBytesDelegateReadNs.load(),
                readBytesChecksumNs = readBytesChecksumNs.load()
            )
        }
    }
}
