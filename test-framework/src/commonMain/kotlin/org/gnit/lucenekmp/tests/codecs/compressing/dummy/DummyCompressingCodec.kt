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
package org.gnit.lucenekmp.tests.codecs.compressing.dummy

import okio.IOException
import org.gnit.lucenekmp.codecs.compressing.CompressionMode
import org.gnit.lucenekmp.codecs.compressing.Compressor
import org.gnit.lucenekmp.codecs.compressing.Decompressor
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.store.ByteBuffersDataInput
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.tests.codecs.compressing.CompressingCodec
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef

/** CompressionCodec that does not compress data, useful for testing. */
// In its own package to make sure the oal.codecs.compressing classes are
// visible enough to let people write their own CompressionMode
class DummyCompressingCodec : CompressingCodec {

    companion object {
        val DUMMY: CompressionMode = object : CompressionMode() {
            override fun newCompressor(): Compressor {
                return DUMMY_COMPRESSOR
            }

            override fun newDecompressor(): Decompressor {
                return DUMMY_DECOMPRESSOR
            }

            override fun toString(): String {
                return "DUMMY"
            }
        }

        private val DUMMY_DECOMPRESSOR: Decompressor = object : Decompressor() {
            @Throws(IOException::class)
            override fun decompress(
                `in`: DataInput,
                originalLength: Int,
                offset: Int,
                length: Int,
                bytes: BytesRef
            ) {
                assert(offset + length <= originalLength)
                if (bytes.bytes.size < originalLength) {
                    bytes.bytes = ByteArray(ArrayUtil.oversize(originalLength, 1))
                }
                `in`.readBytes(bytes.bytes, 0, offset + length)
                bytes.offset = offset
                bytes.length = length
            }

            override fun clone(): Decompressor {
                return this
            }
        }

        private val DUMMY_COMPRESSOR: Compressor = object : Compressor() {
            @Throws(IOException::class)
            override fun compress(buffersInput: ByteBuffersDataInput, out: DataOutput) {
                out.copyBytes(buffersInput, buffersInput.length())
            }

            @Throws(IOException::class)
            override fun close() {
            }
        }
    }

    /** Constructor that allows to configure the chunk size. */
    constructor(
        chunkSize: Int,
        maxDocsPerChunk: Int,
        withSegmentSuffix: Boolean,
        blockSize: Int
    ) : super(
        "DummyCompressingStoredFieldsData",
        if (withSegmentSuffix) "DummyCompressingStoredFields" else "",
        DUMMY,
        chunkSize,
        maxDocsPerChunk,
        blockSize
    )

    /** Default constructor. */
    constructor() : this(1 shl 14, 128, false, 10)
}
