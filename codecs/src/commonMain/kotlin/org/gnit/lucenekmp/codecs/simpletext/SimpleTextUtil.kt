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
package org.gnit.lucenekmp.codecs.simpletext

import okio.IOException
import org.gnit.lucenekmp.index.CorruptIndexException
import org.gnit.lucenekmp.store.ChecksumIndexInput
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.StringHelper

internal object SimpleTextUtil {
    const val NEWLINE: Byte = 10
    const val ESCAPE: Byte = 92
    val CHECKSUM: BytesRef = BytesRef("checksum ")

    @Throws(IOException::class)
    fun write(out: DataOutput, s: String, scratch: BytesRefBuilder) {
        scratch.copyChars(s, 0, s.length)
        write(out, scratch.get())
    }

    @Throws(IOException::class)
    fun write(out: DataOutput, b: BytesRef?) {
        for (i in 0 until b!!.length) {
            val bx = b.bytes[b.offset + i]
            if (bx == NEWLINE || bx == ESCAPE) {
                out.writeByte(ESCAPE)
            }
            out.writeByte(bx)
        }
    }

    @Throws(IOException::class)
    fun writeNewline(out: DataOutput) {
        out.writeByte(NEWLINE)
    }

    @Throws(IOException::class)
    fun readLine(`in`: DataInput, scratch: BytesRefBuilder) {
        var upto = 0
        while (true) {
            val b = `in`.readByte()
            scratch.grow(1 + upto)
            if (b == ESCAPE) {
                scratch.setByteAt(upto++, `in`.readByte())
            } else {
                if (b == NEWLINE) {
                    break
                } else {
                    scratch.setByteAt(upto++, b)
                }
            }
        }
        scratch.setLength(upto)
    }

    @Throws(IOException::class)
    fun writeChecksum(out: IndexOutput, scratch: BytesRefBuilder) {
        // Pad with zeros so different checksum values use the
        // same number of bytes
        // (BaseIndexFileFormatTestCase.testMergeStability cares):
        val checksum = out.getChecksum().toString().padStart(20, '0')
        write(out, CHECKSUM)
        write(out, checksum, scratch)
        writeNewline(out)
    }

    @Throws(IOException::class)
    fun checkFooter(input: ChecksumIndexInput) {
        val scratch = BytesRefBuilder()
        val expectedChecksum = input.checksum.toString().padStart(20, '0')
        readLine(input, scratch)
        if (!StringHelper.startsWith(scratch.get(), CHECKSUM)) {
            throw CorruptIndexException(
                "SimpleText failure: expected checksum line but got ${scratch.get().utf8ToString()}",
                input
            )
        }
        val actualChecksum =
            BytesRef(scratch.bytes(), CHECKSUM.length, scratch.length() - CHECKSUM.length)
                .utf8ToString()
        if (expectedChecksum != actualChecksum) {
            throw CorruptIndexException(
                "SimpleText checksum failure: $actualChecksum != $expectedChecksum",
                input
            )
        }
        if (input.length() != input.filePointer) {
            throw CorruptIndexException(
                "Unexpected stuff at the end of file, please be careful with your text editor!",
                input
            )
        }
    }

    /** Inverse of [BytesRef.toString]. */
    fun fromBytesRefString(s: String): BytesRef {
        if (s.length < 2) {
            throw IllegalArgumentException(
                "string $s was not created from BytesRef.toString?"
            )
        }
        if (s[0] != '[' || s[s.length - 1] != ']') {
            throw IllegalArgumentException(
                "string $s was not created from BytesRef.toString?"
            )
        }
        if (s.length == 2) {
            return BytesRef(BytesRef.EMPTY_BYTES)
        }
        val parts = s.substring(1, s.length - 1).split(" ")
        val bytes = ByteArray(parts.size)
        for (i in parts.indices) {
            bytes[i] = parts[i].toInt(16).toByte()
        }

        return BytesRef(bytes)
    }
}
