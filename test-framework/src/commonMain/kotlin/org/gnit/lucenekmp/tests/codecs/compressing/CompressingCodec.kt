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
package org.gnit.lucenekmp.tests.codecs.compressing

import org.gnit.lucenekmp.codecs.FilterCodec
import org.gnit.lucenekmp.codecs.StoredFieldsFormat
import org.gnit.lucenekmp.codecs.TermVectorsFormat
import org.gnit.lucenekmp.codecs.compressing.CompressionMode
import org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingStoredFieldsFormat
import org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingTermVectorsFormat
import org.gnit.lucenekmp.tests.codecs.compressing.dummy.DummyCompressingCodec
import org.gnit.lucenekmp.tests.util.RandomNumbers
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.packed.DirectMonotonicWriter
import kotlin.random.Random

/**
 * A codec that uses [Lucene90CompressingStoredFieldsFormat] for its stored fields and
 * delegates to the default codec for everything else.
 */
abstract class CompressingCodec : FilterCodec {

    /** Create a random instance. */
    companion object {
        fun randomInstance(
            random: Random,
            chunkSize: Int,
            maxDocsPerChunk: Int,
            withSegmentSuffix: Boolean,
            blockShift: Int
        ): CompressingCodec {
            return when (random.nextInt(6)) {
                0 -> FastCompressingCodec(chunkSize, maxDocsPerChunk, withSegmentSuffix, blockShift)
                1 -> FastDecompressionCompressingCodec(
                    chunkSize,
                    maxDocsPerChunk,
                    withSegmentSuffix,
                    blockShift
                )
                2 -> HighCompressionCompressingCodec(
                    chunkSize,
                    maxDocsPerChunk,
                    withSegmentSuffix,
                    blockShift
                )
                3 -> DummyCompressingCodec(chunkSize, maxDocsPerChunk, withSegmentSuffix, blockShift)
                4 -> DeflateWithPresetCompressingCodec(
                    chunkSize,
                    maxDocsPerChunk,
                    withSegmentSuffix,
                    blockShift
                )
                5 -> LZ4WithPresetCompressingCodec(
                    chunkSize,
                    maxDocsPerChunk,
                    withSegmentSuffix,
                    blockShift
                )
                else -> throw AssertionError()
            }
        }

        /** Creates a random [CompressingCodec] that is using an empty segment suffix */
        fun randomInstance(random: Random): CompressingCodec {
            val chunkSize =
                if (random.nextBoolean()) {
                    RandomNumbers.randomIntBetween(random, 10, 100)
                } else {
                    RandomNumbers.randomIntBetween(random, 10, 1 shl 15)
                }
            val chunkDocs =
                if (random.nextBoolean()) {
                    RandomNumbers.randomIntBetween(random, 1, 10)
                } else {
                    RandomNumbers.randomIntBetween(random, 64, 1024)
                }
            val blockSize =
                if (random.nextBoolean()) {
                    RandomNumbers.randomIntBetween(random, DirectMonotonicWriter.MIN_BLOCK_SHIFT, 10)
                } else {
                    RandomNumbers.randomIntBetween(
                        random,
                        DirectMonotonicWriter.MIN_BLOCK_SHIFT,
                        DirectMonotonicWriter.MAX_BLOCK_SHIFT
                    )
                }
            return randomInstance(random, chunkSize, chunkDocs, false, blockSize)
        }

        /** Creates a random [CompressingCodec] with more reasonable parameters for big tests. */
        fun reasonableInstance(random: Random): CompressingCodec {
            // e.g. defaults use 2^14 for FAST and ~ 2^16 for HIGH
            val chunkSize = TestUtil.nextInt(random, 1 shl 13, 1 shl 17)
            // e.g. defaults use 128 for FAST and 512 for HIGH
            val chunkDocs = TestUtil.nextInt(random, 1 shl 6, 1 shl 10)
            // e.g. defaults use 1024 for both cases
            val blockShift = TestUtil.nextInt(random, 8, 12)
            return randomInstance(random, chunkSize, chunkDocs, false, blockShift)
        }

        /** Creates a random [CompressingCodec] that is using a segment suffix */
        fun randomInstance(random: Random, withSegmentSuffix: Boolean): CompressingCodec {
            return randomInstance(
                random,
                RandomNumbers.randomIntBetween(random, 1, 1 shl 15),
                RandomNumbers.randomIntBetween(random, 64, 1024),
                withSegmentSuffix,
                RandomNumbers.randomIntBetween(random, 1, 1024)
            )
        }
    }

    private val storedFieldsFormat: Lucene90CompressingStoredFieldsFormat
    private val termVectorsFormat: Lucene90CompressingTermVectorsFormat

    /** Creates a compressing codec with a given segment suffix */
    constructor(
        name: String,
        segmentSuffix: String,
        compressionMode: CompressionMode,
        chunkSize: Int,
        maxDocsPerChunk: Int,
        blockShift: Int
    ) : super(name, TestUtil.getDefaultCodec()) {
        storedFieldsFormat =
            Lucene90CompressingStoredFieldsFormat(
                name,
                segmentSuffix,
                compressionMode,
                chunkSize,
                maxDocsPerChunk,
                blockShift
            )
        termVectorsFormat =
            Lucene90CompressingTermVectorsFormat(
                name,
                segmentSuffix,
                compressionMode,
                chunkSize,
                maxDocsPerChunk,
                blockShift
            )
    }

    /** Creates a compressing codec with an empty segment suffix */
    constructor(
        name: String,
        compressionMode: CompressionMode,
        chunkSize: Int,
        maxDocsPerChunk: Int,
        blockSize: Int
    ) : this(name, "", compressionMode, chunkSize, maxDocsPerChunk, blockSize)

    override fun storedFieldsFormat(): StoredFieldsFormat {
        return storedFieldsFormat
    }

    override fun termVectorsFormat(): TermVectorsFormat {
        return termVectorsFormat
    }

    override fun toString(): String {
        return name +
            "(storedFieldsFormat=" +
            storedFieldsFormat +
            ", termVectorsFormat=" +
            termVectorsFormat +
            ")"
    }
}
