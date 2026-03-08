package org.gnit.lucenekmp.tests.store

import okio.IOException
import org.gnit.lucenekmp.internal.hppc.LongHashSet
import org.gnit.lucenekmp.store.ChecksumIndexInput
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.FilterDirectory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.ReadAdvice
import org.gnit.lucenekmp.util.CloseableThreadLocal
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

/**
 * A [Directory] wrapper that counts the number of times that Lucene may wait for I/O to
 * return serially. Lower counts mean that Lucene better takes advantage of I/O parallelism.
 */
@OptIn(ExperimentalAtomicApi::class)
class SerialIOCountingDirectory(`in`: Directory) : FilterDirectory(`in`) {
    private val counter = AtomicLong(0L)
    private val pendingFetch =
        object : CloseableThreadLocal<Boolean>() {
            override fun initialValue(): Boolean {
                return false
            }
        }

    override fun close() {
        pendingFetch.close()
        super.close()
    }

    /** Return the number of I/O request performed serially. */
    fun count(): Long {
        return counter.load()
    }

    @Throws(IOException::class)
    override fun openChecksumInput(name: String): ChecksumIndexInput {
        // sequential access, count 1 for the whole file
        counter.incrementAndFetch()
        return super.openChecksumInput(name)
    }

    @Throws(IOException::class)
    override fun openInput(name: String, context: IOContext): IndexInput {
        if (context.readAdvice == ReadAdvice.RANDOM_PRELOAD) {
            // expected to be loaded in memory, only count 1 at open time
            counter.incrementAndFetch()
            return super.openInput(name, context)
        }
        return SerializedIOCountingIndexInput(super.openInput(name, context), context.readAdvice)
    }

    private inner class SerializedIOCountingIndexInput : IndexInput {
        private val `in`: IndexInput
        private val sliceOffset: Long
        private val sliceLength: Long
        private val readAdvice: ReadAdvice
        private val pendingPages = LongHashSet()
        private var currentPage = Long.MIN_VALUE

        constructor(`in`: IndexInput, readAdvice: ReadAdvice) : this(`in`, readAdvice, 0L, `in`.length())

        constructor(`in`: IndexInput, readAdvice: ReadAdvice, offset: Long, length: Long) : super(`in`.toString()) {
            this.`in` = `in`
            this.sliceOffset = offset
            this.sliceLength = length
            this.readAdvice = readAdvice
        }

        private fun onRead(offset: Long, len: Int) {
            if (len == 0) {
                return
            }
            val firstPage = (sliceOffset + offset) shr PAGE_SHIFT
            val lastPage = (sliceOffset + offset + len - 1) shr PAGE_SHIFT

            for (page in firstPage..lastPage) {
                val readAheadUpto =
                    if (readAdvice == ReadAdvice.RANDOM) {
                        currentPage
                    } else {
                        // Assume that the next few pages are always free to read thanks to read-ahead.
                        currentPage + PAGE_READAHEAD
                    }

                if (!pendingPages.contains(page) && page !in currentPage..readAheadUpto) {
                    counter.incrementAndFetch()
                }
                currentPage = page
            }
            pendingFetch.set(false)
        }

        @Throws(IOException::class)
        override fun prefetch(offset: Long, length: Long) {
            val firstPage = (sliceOffset + offset) shr PAGE_SHIFT
            val lastPage = (sliceOffset + offset + length - 1) shr PAGE_SHIFT

            val readAheadUpto =
                if (readAdvice == ReadAdvice.RANDOM) {
                    currentPage
                } else {
                    // Assume that the next few pages are always free to read thanks to read-ahead.
                    currentPage + PAGE_READAHEAD
                }

            if (firstPage >= currentPage && lastPage <= readAheadUpto) {
                // seeking within the current (or next page if ReadAdvice.NORMAL) doesn't increment the
                // counter
            } else if (pendingFetch.get() == false) {
                // If multiple prefetch calls are performed without a readXXX() call in-between, count a
                // single increment as these I/O requests can be performed in parallel.
                counter.incrementAndFetch()
                pendingPages.clear()
                pendingFetch.set(true)
            }

            for (page in firstPage..lastPage) {
                pendingPages.add(page)
            }
        }

        @Throws(IOException::class)
        override fun readByte(): Byte {
            onRead(filePointer, Byte.SIZE_BYTES)
            return `in`.readByte()
        }

        @Throws(IOException::class)
        override fun readBytes(b: ByteArray, offset: Int, len: Int) {
            onRead(filePointer, len)
            `in`.readBytes(b, offset, len)
        }

        override fun close() {
            `in`.close()
        }

        override val filePointer: Long
            get() = `in`.filePointer - sliceOffset

        @Throws(IOException::class)
        override fun seek(pos: Long) {
            `in`.seek(sliceOffset + pos)
        }

        override fun length(): Long {
            return sliceLength
        }

        @Throws(IOException::class)
        override fun slice(sliceDescription: String, offset: Long, length: Long): IndexInput {
            return slice(sliceDescription, offset, length, readAdvice)
        }

        @Throws(IOException::class)
        override fun slice(sliceDescription: String, offset: Long, length: Long, readAdvice: ReadAdvice): IndexInput {
            if ((length or offset) < 0 || length > sliceLength - offset) {
                throw IllegalArgumentException()
            }
            val clone = `in`.clone()
            clone.seek(sliceOffset + offset)
            return SerializedIOCountingIndexInput(clone, readAdvice, sliceOffset + offset, length)
        }

        override fun clone(): IndexInput {
            val clone = `in`.clone()
            return SerializedIOCountingIndexInput(clone, readAdvice, sliceOffset, sliceLength)
        }

        override val isLoaded
            get() = `in`.isLoaded
    }

    companion object {
        private const val PAGE_SHIFT = 12 // 4096 bytes per page

        // Assumed number of pages that are read ahead
        private const val PAGE_READAHEAD = 4
    }
}
