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
package org.gnit.lucenekmp.tests.store

import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import okio.IOException
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.store.AlreadyClosedException
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.FilterIndexOutput
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.tests.util.LuceneTestCase

/**
 * Used to create an output stream that will throw an IOException on fake disk full, track max disk
 * space actually used, and maybe throw random IOExceptions.
 */
class MockIndexOutputWrapper(private val dir: MockDirectoryWrapper, out: IndexOutput, override var name: String?) :
    FilterIndexOutput("MockIndexOutputWrapper($out)", out!!.name, out) {

    private var first: Boolean = true
    private val singleByte = ByteArray(1)
    private var closed: Boolean = false

    /** Construct an empty output buffer. */
    private fun checkCrashed() {
        // If crashed since we were opened, then don't write anything
        if (dir.crashed) {
            val simpleName = dir::class.simpleName ?: dir.toString()
            throw IOException("$simpleName has crashed; cannot write to $name")
        }
    }

    @Throws(IOException::class)
    private fun checkDiskFull(b: ByteArray?, offset: Int, input: DataInput?, len: Long) {
        var freeSpace = if (dir.maxSizeInBytes == 0L) 0L else dir.maxSizeInBytes - dir.sizeInBytes()
        var realUsage = 0L

        // Enforce disk full:
        if (dir.maxSizeInBytes != 0L && freeSpace <= len) {
            // Compute the real disk free.  This will greatly slow
            // down our test but makes it more accurate:
            realUsage = dir.sizeInBytes()
            freeSpace = dir.maxSizeInBytes - realUsage
        }

        if (dir.maxSizeInBytes != 0L && freeSpace <= len) {
            if (freeSpace > 0) {
                realUsage += freeSpace
                if (b != null) {
                    out!!.writeBytes(b, offset, freeSpace.toInt())
                } else {
                    out!!.copyBytes(input!!, freeSpace)
                }
            }
            if (realUsage > dir.maxUsedSizeInBytes) {
                dir.maxUsedSizeInBytes = realUsage
            }
            var message =
                "fake disk full at ${dir.sizeInBytes()} bytes when writing $name (file length=${out!!.filePointer}"
            if (freeSpace > 0) {
                message += "; wrote $freeSpace of $len bytes"
            }
            message += ")"
            if (LuceneTestCase.VERBOSE) {
                val job = runBlocking { currentCoroutineContext()[Job] }
                println("$job: MDW: now throw fake disk full")
                println(Throwable().stackTraceToString())
            }
            throw IOException(message)
        }
    }

    override fun close() {
        if (closed) {
            out!!.close() // don't mask double-close bugs
            return
        }
        closed = true

        try {
            assert(out != null)
            dir.maybeThrowDeterministicException()
        } finally {
            out!!.close()
            dir.removeIndexOutput(this, name!!)
            if (dir.trackDiskUsage) {
                // Now compute actual disk usage & track the maxUsedSize
                // in the MockDirectoryWrapper:
                val size = dir.sizeInBytes()
                if (size > dir.maxUsedSizeInBytes) {
                    dir.maxUsedSizeInBytes = size
                }
            }
        }
    }

    private fun ensureOpen() {
        if (closed) {
            throw AlreadyClosedException("Already closed: $this")
        }
    }

    @Throws(IOException::class)
    override fun writeByte(b: Byte) {
        singleByte[0] = b
        writeBytes(singleByte, 0, 1)
    }

    @Throws(IOException::class)
    override fun writeBytes(b: ByteArray, offset: Int, len: Int) {
        ensureOpen()
        checkCrashed()
        checkDiskFull(b, offset, null, len.toLong())

        if (dir.randomState.nextInt(200) == 0) {
            val half = len / 2
            out!!.writeBytes(b, offset, half)
            runBlocking { yield() }
            out!!.writeBytes(b, offset + half, len - half)
        } else {
            out!!.writeBytes(b, offset, len)
        }

        dir.maybeThrowDeterministicException()

        if (first) {
            // Maybe throw random exception; only do this on first
            // write to a new file:
            first = false
            dir.maybeThrowIOException(name!!)
        }
    }

    @Throws(IOException::class)
    override fun writeBytes(b: ByteArray, len: Int) {
        writeBytes(b, 0, len)
    }

    @Throws(IOException::class)
    override fun copyBytes(input: DataInput, numBytes: Long) {
        ensureOpen()
        checkCrashed()
        checkDiskFull(null, 0, input, numBytes)

        out!!.copyBytes(input, numBytes)
        dir.maybeThrowDeterministicException()
    }
}
