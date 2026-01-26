/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnit.lucenekmp.tests.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okio.IOException
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.TimeUnit
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.store.FilterIndexOutput
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.util.ThreadInterruptedException
import kotlin.properties.Delegates

/** Intentionally slow IndexOutput for testing. */
class ThrottledIndexOutput : FilterIndexOutput {
    private var bytesPerSecond by Delegates.notNull<Int>()
    private var flushDelayMillis by Delegates.notNull<Long>()
    private var closeDelayMillis by Delegates.notNull<Long>()
    private var seekDelayMillis by Delegates.notNull<Long>()
    private var pendingBytes: Long = 0
    private var minBytesWritten by Delegates.notNull<Long>()
    private var timeElapsed: Long = 0
    private val bytes = ByteArray(1)

    fun newFromDelegate(out: IndexOutput): ThrottledIndexOutput {
        return ThrottledIndexOutput(
            bytesPerSecond, flushDelayMillis, closeDelayMillis, seekDelayMillis, minBytesWritten, out
        )
    }

    constructor(bytesPerSecond: Int, delayInMillis: Long, out: IndexOutput?) : this(
        bytesPerSecond,
        delayInMillis,
        delayInMillis,
        delayInMillis,
        DEFAULT_MIN_WRITTEN_BYTES.toLong(),
        out
    )

    constructor(bytesPerSecond: Int, delays: Long, minBytesWritten: Int, out: IndexOutput?) : this(
        bytesPerSecond,
        delays,
        delays,
        delays,
        minBytesWritten.toLong(),
        out
    )

    constructor(
        bytesPerSecond: Int,
        flushDelayMillis: Long,
        closeDelayMillis: Long,
        seekDelayMillis: Long,
        minBytesWritten: Long,
        out: IndexOutput?
    ) : super("ThrottledIndexOutput($out)", out?.name, out) {
        assert(bytesPerSecond > 0)
        this.bytesPerSecond = bytesPerSecond
        this.flushDelayMillis = flushDelayMillis
        this.closeDelayMillis = closeDelayMillis
        this.seekDelayMillis = seekDelayMillis
        this.minBytesWritten = minBytesWritten
    }

    override fun close() {
        try {
            sleep(closeDelayMillis + getDelay(true))
        } finally {
            out!!.close()
        }
    }

    @Throws(IOException::class)
    override fun writeByte(b: Byte) {
        bytes[0] = b
        writeBytes(bytes, 0, 1)
    }

    @Throws(IOException::class)
    override fun writeBytes(b: ByteArray, offset: Int, length: Int) {
        val before = System.nanoTime()
        // TODO: sometimes, write only half the bytes, then
        // sleep, then 2nd half, then sleep, so we sometimes
        // interrupt having only written not all bytes
        out!!.writeBytes(b, offset, length)
        timeElapsed += System.nanoTime() - before
        pendingBytes += length.toLong()
        sleep(getDelay(false))
    }

    protected fun getDelay(closing: Boolean): Long {
        if (pendingBytes > 0 && (closing || pendingBytes > minBytesWritten)) {
            val actualBps = (timeElapsed / pendingBytes) * 1_000_000_000L // nano to sec
            if (actualBps > bytesPerSecond) {
                val expected = (pendingBytes * 1000L / bytesPerSecond)
                val delay = expected - TimeUnit.NANOSECONDS.toMillis(timeElapsed)
                pendingBytes = 0
                timeElapsed = 0
                return delay
            }
        }
        return 0
    }

    companion object {
        const val DEFAULT_MIN_WRITTEN_BYTES: Int = 1024

        fun mBitsToBytes(mbits: Int): Int {
            return mbits * 125000000
        }

        private fun sleep(ms: Long) {
            if (ms <= 0) {
                return
            }
            try {
                runBlocking {
                    delay(ms)
                }
            } catch (e: CancellationException) {
                throw ThreadInterruptedException(e)
            }
        }
    }
}
