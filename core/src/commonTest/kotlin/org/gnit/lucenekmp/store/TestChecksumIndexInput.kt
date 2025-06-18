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
package org.gnit.lucenekmp.store

import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.gnit.lucenekmp.jdkport.Files
import org.gnit.lucenekmp.jdkport.CRC32
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.store.BufferedChecksum
import org.gnit.lucenekmp.store.FlushInfo
import org.gnit.lucenekmp.store.FSLockFactory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.NIOFSDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals

class TestChecksumIndexInput : LuceneTestCase() {
    private lateinit var fakeFileSystem: FakeFileSystem

    @BeforeTest
    fun setUp() {
        fakeFileSystem = FakeFileSystem()
        Files.setFileSystem(fakeFileSystem)
    }

    @AfterTest
    fun tearDown() {
        Files.resetFileSystem()
    }

    @Test
    fun testSkipBytes() {
        val numTestBytes = TestUtil.nextInt(random(), 100, 1000)
        val testBytes = ByteArray(numTestBytes)
        val path = "/dir".toPath()
        fakeFileSystem.createDirectories(path)
        val dir = NIOFSDirectory(path, FSLockFactory.default, fakeFileSystem)
        val out = dir.createOutput("foo", IOContext(FlushInfo(0, 0)))
        out.use { it.writeBytes(testBytes, numTestBytes) }

        val `in` = dir.openInput("foo", IOContext(FlushInfo(0, 0)))
        `in`.use {
            val checksumIndexInput = InterceptingChecksumIndexInput(it, numTestBytes)
            var skipped = 0
            while (skipped < numTestBytes) {
                val remaining = numTestBytes - skipped
                val step = if (remaining < 10) remaining else random().nextInt(remaining)
                checksumIndexInput.skipBytes(step.toLong())
                skipped += step
            }
            assertContentEquals(testBytes, checksumIndexInput.readBytes)
        }

        dir.close()
    }

    private class InterceptingChecksumIndexInput(
        private val main: IndexInput,
        len: Int
    ) : ChecksumIndexInput("InterceptingChecksumIndexInput($main)") {
        private val digest = BufferedChecksum(CRC32())
        val readBytes: ByteArray = ByteArray(len)
        private var off = 0

        override fun readByte(): Byte {
            val b = main.readByte()
            digest.update(b.toInt())
            readBytes[off++] = b
            return b
        }

        override fun readBytes(b: ByteArray, offset: Int, len: Int) {
            main.readBytes(b, offset, len)
            digest.update(b, offset, len)
            b.copyInto(readBytes, off, offset, offset + len)
            off += len
        }

        override val checksum: Long
            get() = digest.getValue()

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

        override fun slice(sliceDescription: String, offset: Long, length: Long): IndexInput {
            throw UnsupportedOperationException()
        }
    }
}
