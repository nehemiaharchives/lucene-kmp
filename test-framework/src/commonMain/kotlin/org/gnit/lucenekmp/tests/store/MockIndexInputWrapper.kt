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
import okio.IOException
import org.gnit.lucenekmp.internal.tests.TestSecrets
import org.gnit.lucenekmp.jdkport.Optional
import org.gnit.lucenekmp.jdkport.assert
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import org.gnit.lucenekmp.store.FilterIndexInput
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.ReadAdvice
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.incrementAndFetch

/**
 * Used by MockDirectoryWrapper to create an input stream that keeps track of when it's been closed.
 */
@OptIn(ExperimentalAtomicApi::class)
open class MockIndexInputWrapper(
    private val dir: MockDirectoryWrapper,
    val name: String,
    delegate: IndexInput,
    private val parent: MockIndexInputWrapper?,
    private var readAdvice: ReadAdvice,
    private val confined: Boolean
) : FilterIndexInput("MockIndexInputWrapper(name=$name delegate=$delegate)", delegate) {

    @Volatile
    private var closed: Boolean = false

    // KMP common code does not have a stable thread identity; skip strict confinement checks.
    private val thread: Job? = null

    init {
        // If we are a clone then our parent better not be a clone!
        assert(parent == null || parent.parent == null)
    }

    override fun close() {
        if (closed) {
            `in`.close() // don't mask double-close bugs
            return
        }
        closed = true

        try {
            // Pending resolution on LUCENE-686 we may want to
            // remove the conditional check so we also track that
            // all clones get closed:
            if (parent == null) {
                dir.removeIndexInput(this, name)
            }
            dir.maybeThrowDeterministicException()
        } finally {
            `in`.close()
        }
    }

    private fun ensureOpen() {
        // TODO: not great this is a volatile read (closed) ... we should deploy heavy JVM voodoo like
        // SwitchPoint to avoid this
        if (closed) {
            throw RuntimeException("Abusing closed IndexInput!")
        }
        if (parent != null && parent.closed) {
            throw RuntimeException("Abusing clone of a closed IndexInput!")
        }
    }

    private fun ensureAccessible() {
        if (confined && thread != null && thread !== runBlocking { currentCoroutineContext()[Job] }) {
            throw RuntimeException("Abusing from another thread!")
        }
    }

    override fun clone(): MockIndexInputWrapper {
        ensureOpen()
        if (dir.verboseClone) {
            println(Exception("clone: $this").stackTraceToString())
        }
        dir.inputCloneCount.incrementAndFetch()
        val iiclone = `in`.clone()
        val clone = MockIndexInputWrapper(
            dir,
            name,
            iiclone,
            parent ?: this,
            readAdvice,
            confined
        )
        // Pending resolution on LUCENE-686 we may want to
        // uncomment this code so that we also track that all
        // clones get closed:
        /*
        synchronized(dir.openFiles) {
          if (dir.openFiles.containsKey(name)) {
            Integer v = (Integer) dir.openFiles.get(name);
            v = Integer.valueOf(v.intValue()+1);
            dir.openFiles.put(name, v);
          } else {
            throw new RuntimeException("BUG: cloned file was not open?");
          }
        }
        */
        return clone
    }

    @Throws(IOException::class)
    override fun slice(sliceDescription: String, offset: Long, length: Long): IndexInput {
        ensureOpen()
        if (dir.verboseClone) {
            println(Exception("slice: $this").stackTraceToString())
        }
        dir.inputCloneCount.incrementAndFetch()
        val slice = `in`.slice(sliceDescription, offset, length)
        val clone = MockIndexInputWrapper(
            dir,
            sliceDescription,
            slice,
            parent ?: this,
            readAdvice,
            confined
        )
        return clone
    }

    @Throws(IOException::class)
    override fun slice(
        sliceDescription: String,
        offset: Long,
        length: Long,
        readAdvice: ReadAdvice
    ): IndexInput {
        if (this.readAdvice != ReadAdvice.NORMAL) {
            throw IllegalStateException(
                "slice() may only be called with a custom read advice on inputs that have been open with ReadAdvice.NORMAL"
            )
        }
        ensureOpen()
        if (dir.verboseClone) {
            println(Exception("slice: $this").stackTraceToString())
        }
        dir.inputCloneCount.incrementAndFetch()
        val slice = `in`.slice(sliceDescription, offset, length)
        val clone = MockIndexInputWrapper(
            dir,
            sliceDescription,
            slice,
            parent ?: this,
            readAdvice,
            confined
        )
        return clone
    }

    override val filePointer: Long
        get() {
            ensureOpen()
            ensureAccessible()
            return `in`.filePointer
        }

    @Throws(IOException::class)
    override fun seek(pos: Long) {
        ensureOpen()
        ensureAccessible()
        `in`.seek(pos)
    }

    @Throws(IOException::class)
    override fun prefetch(offset: Long, length: Long) {
        ensureOpen()
        ensureAccessible()
        `in`.prefetch(offset, length)
    }

    override val isLoaded: Optional<Boolean?>
        get() {
            ensureOpen()
            ensureAccessible()
            return `in`.isLoaded
        }

    @Throws(IOException::class)
    override fun updateReadAdvice(readAdvice: ReadAdvice?) {
        ensureOpen()
        ensureAccessible()
        requireNotNull(readAdvice)
        this.readAdvice = readAdvice
        `in`.updateReadAdvice(readAdvice)
    }

    override fun length(): Long {
        ensureOpen()
        return `in`.length()
    }

    @Throws(IOException::class)
    override fun readByte(): Byte {
        ensureOpen()
        ensureAccessible()
        return `in`.readByte()
    }

    @Throws(IOException::class)
    override fun readBytes(b: ByteArray, offset: Int, len: Int) {
        ensureOpen()
        ensureAccessible()
        `in`.readBytes(b, offset, len)
    }

    @Throws(IOException::class)
    override fun readBytes(b: ByteArray, offset: Int, len: Int, useBuffer: Boolean) {
        ensureOpen()
        ensureAccessible()
        `in`.readBytes(b, offset, len, useBuffer)
    }

    @Throws(IOException::class)
    override fun readFloats(floats: FloatArray, offset: Int, len: Int) {
        ensureOpen()
        ensureAccessible()
        `in`.readFloats(floats, offset, len)
    }

    @Throws(IOException::class)
    override fun readShort(): Short {
        ensureOpen()
        ensureAccessible()
        return `in`.readShort()
    }

    @Throws(IOException::class)
    override fun readInt(): Int {
        ensureOpen()
        ensureAccessible()
        return `in`.readInt()
    }

    @Throws(IOException::class)
    override fun readLong(): Long {
        ensureOpen()
        ensureAccessible()
        return `in`.readLong()
    }

    @Throws(IOException::class)
    override fun readString(): String {
        ensureOpen()
        ensureAccessible()
        return `in`.readString()
    }

    @Throws(IOException::class)
    override fun readVInt(): Int {
        ensureOpen()
        ensureAccessible()
        return `in`.readVInt()
    }

    @Throws(IOException::class)
    override fun readVLong(): Long {
        ensureOpen()
        ensureAccessible()
        return `in`.readVLong()
    }

    @Throws(IOException::class)
    override fun readZInt(): Int {
        ensureOpen()
        ensureAccessible()
        return `in`.readZInt()
    }

    @Throws(IOException::class)
    override fun readZLong(): Long {
        ensureOpen()
        ensureAccessible()
        return `in`.readZLong()
    }

    @Throws(IOException::class)
    override fun skipBytes(numBytes: Long) {
        ensureOpen()
        ensureAccessible()
        super.skipBytes(numBytes)
    }

    @Throws(IOException::class)
    override fun readMapOfStrings(): MutableMap<String, String> {
        ensureOpen()
        ensureAccessible()
        return `in`.readMapOfStrings()
    }

    @Throws(IOException::class)
    override fun readSetOfStrings(): MutableSet<String> {
        ensureOpen()
        ensureAccessible()
        return `in`.readSetOfStrings()
    }

    override fun toString(): String {
        return "MockIndexInputWrapper(${`in`})"
    }

    companion object {
        init {
            @Suppress("UNUSED_VARIABLE")
            val ensureFilterIndexInputInit = FilterIndexInput.TEST_FILTER_INPUTS
            runCatching {
                TestSecrets.filterInputIndexAccess.addTestFilterType(MockIndexInputWrapper::class)
            }.onFailure {
                FilterIndexInput.TEST_FILTER_INPUTS.add(MockIndexInputWrapper::class)
            }
        }
    }
}
