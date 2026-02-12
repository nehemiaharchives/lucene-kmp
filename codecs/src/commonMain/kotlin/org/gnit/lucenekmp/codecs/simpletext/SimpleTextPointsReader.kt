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
import org.gnit.lucenekmp.codecs.PointsReader
import org.gnit.lucenekmp.index.CorruptIndexException
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.jdkport.StandardCharsets
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.fromByteArray
import org.gnit.lucenekmp.store.BufferedChecksumIndexInput
import org.gnit.lucenekmp.store.ChecksumIndexInput
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.StringHelper

internal class SimpleTextPointsReader(readState: SegmentReadState) : PointsReader() {

    private val dataIn: IndexInput
    val readState: SegmentReadState
    val readers: MutableMap<String, SimpleTextBKDReader> = HashMap()
    val scratch: BytesRefBuilder = BytesRefBuilder()

    init {
        // Initialize readers now:

        // Read index:
        val fieldToFileOffset: MutableMap<String, Long> = HashMap()

        val indexFileName =
            IndexFileNames.segmentFileName(
                readState.segmentInfo.name,
                readState.segmentSuffix,
                SimpleTextPointsFormat.POINT_INDEX_EXTENSION
            )
        readState.directory.openChecksumInput(indexFileName).use { `in` ->
            readLine(`in`)
            var count = parseInt(SimpleTextPointsWriter.FIELD_COUNT)
            for (i in 0 until count) {
                readLine(`in`)
                val fieldName = stripPrefix(SimpleTextPointsWriter.FIELD_FP_NAME)
                readLine(`in`)
                val fp = parseLong(SimpleTextPointsWriter.FIELD_FP)
                fieldToFileOffset[fieldName] = fp
            }
            SimpleTextUtil.checkFooter(`in`)
        }

        var success = false
        val fileName =
            IndexFileNames.segmentFileName(
                readState.segmentInfo.name,
                readState.segmentSuffix,
                SimpleTextPointsFormat.POINT_EXTENSION
            )
        dataIn = readState.directory.openInput(fileName, IOContext.DEFAULT)
        try {
            for (ent in fieldToFileOffset.entries) {
                readers[ent.key] = initReader(ent.value)
            }
            success = true
        } finally {
            if (!success) {
                IOUtils.closeWhileHandlingException(this)
            }
        }

        this.readState = readState
    }

    @Throws(IOException::class)
    private fun initReader(fp: Long): SimpleTextBKDReader {
        // NOTE: matches what writeIndex does in SimpleTextPointsWriter
        dataIn.seek(fp)
        readLine(dataIn)
        val numDataDims = parseInt(SimpleTextPointsWriter.NUM_DATA_DIMS)

        readLine(dataIn)
        val numIndexDims = parseInt(SimpleTextPointsWriter.NUM_INDEX_DIMS)

        readLine(dataIn)
        val bytesPerDim = parseInt(SimpleTextPointsWriter.BYTES_PER_DIM)

        readLine(dataIn)
        val maxPointsInLeafNode = parseInt(SimpleTextPointsWriter.MAX_LEAF_POINTS)

        readLine(dataIn)
        var count = parseInt(SimpleTextPointsWriter.INDEX_COUNT)

        readLine(dataIn)
        assert(startsWith(SimpleTextPointsWriter.MIN_VALUE))
        val minValue = SimpleTextUtil.fromBytesRefString(stripPrefix(SimpleTextPointsWriter.MIN_VALUE))
        assert(minValue.length == numIndexDims * bytesPerDim)

        readLine(dataIn)
        assert(startsWith(SimpleTextPointsWriter.MAX_VALUE))
        val maxValue = SimpleTextUtil.fromBytesRefString(stripPrefix(SimpleTextPointsWriter.MAX_VALUE))
        assert(maxValue.length == numIndexDims * bytesPerDim)

        readLine(dataIn)
        assert(startsWith(SimpleTextPointsWriter.POINT_COUNT))
        val pointCount = parseLong(SimpleTextPointsWriter.POINT_COUNT)

        readLine(dataIn)
        assert(startsWith(SimpleTextPointsWriter.DOC_COUNT))
        val docCount = parseInt(SimpleTextPointsWriter.DOC_COUNT)

        val leafBlockFPs = LongArray(count)
        for (i in 0 until count) {
            readLine(dataIn)
            leafBlockFPs[i] = parseLong(SimpleTextPointsWriter.BLOCK_FP)
        }
        readLine(dataIn)
        count = parseInt(SimpleTextPointsWriter.SPLIT_COUNT)
        val bytesPerIndexEntry: Int = if (numIndexDims == 1) {
            bytesPerDim
        } else {
            1 + bytesPerDim
        }
        val splitPackedValues = ByteArray(count * bytesPerIndexEntry)
        for (i in 0 until count) {
            readLine(dataIn)
            var address = bytesPerIndexEntry * i
            val splitDim = parseInt(SimpleTextPointsWriter.SPLIT_DIM)
            if (numIndexDims != 1) {
                splitPackedValues[address++] = splitDim.toByte()
            }
            readLine(dataIn)
            assert(startsWith(SimpleTextPointsWriter.SPLIT_VALUE))
            val br = SimpleTextUtil.fromBytesRefString(stripPrefix(SimpleTextPointsWriter.SPLIT_VALUE))
            assert(br.length == bytesPerDim)
            br.bytes.copyInto(splitPackedValues, address, br.offset, br.offset + bytesPerDim)
        }

        return SimpleTextBKDReader(
            dataIn,
            numDataDims,
            numIndexDims,
            maxPointsInLeafNode,
            bytesPerDim,
            leafBlockFPs,
            splitPackedValues,
            minValue.bytes,
            maxValue.bytes,
            pointCount,
            docCount
        )
    }

    @Throws(IOException::class)
    private fun readLine(`in`: IndexInput) {
        SimpleTextUtil.readLine(`in`, scratch)
    }

    private fun startsWith(prefix: BytesRef): Boolean {
        return StringHelper.startsWith(scratch.get(), prefix)
    }

    private fun parseInt(prefix: BytesRef): Int {
        assert(startsWith(prefix))
        return stripPrefix(prefix).toInt()
    }

    private fun parseLong(prefix: BytesRef): Long {
        assert(startsWith(prefix))
        return stripPrefix(prefix).toLong()
    }

    private fun stripPrefix(prefix: BytesRef): String {
        return String.fromByteArray(
            scratch.bytes(),
            prefix.length,
            scratch.length() - prefix.length,
            StandardCharsets.UTF_8
        )
    }

    @Throws(IOException::class)
    override fun getValues(fieldName: String): PointValues? {
        val fieldInfo: FieldInfo? = readState.fieldInfos.fieldInfo(fieldName)
        if (fieldInfo == null) {
            throw IllegalArgumentException("field=\"$fieldName\" is unrecognized")
        }
        if (fieldInfo.pointDimensionCount == 0) {
            throw IllegalArgumentException("field=\"$fieldName\" did not index points")
        }
        return readers[fieldName]
    }

    @Throws(IOException::class)
    override fun checkIntegrity() {
        val scratch = BytesRefBuilder()
        val clone = dataIn.clone()
        clone.seek(0)

        // checksum is fixed-width encoded with 20 bytes, plus 1 byte for newline (the space is included
        // in SimpleTextUtil.CHECKSUM):
        val footerStartPos = clone.length() - (SimpleTextUtil.CHECKSUM.length + 21)
        val input: ChecksumIndexInput = BufferedChecksumIndexInput(clone)
        while (true) {
            SimpleTextUtil.readLine(input, scratch)
            if (input.filePointer >= footerStartPos) {
                // Make sure we landed at precisely the right location:
                if (input.filePointer != footerStartPos) {
                    throw CorruptIndexException(
                        "SimpleText failure: footer does not start at expected position current="
                            + input.filePointer
                            + " vs expected="
                            + footerStartPos,
                        input
                    )
                }
                SimpleTextUtil.checkFooter(input)
                break
            }
        }
    }

    @Throws(IOException::class)
    override fun close() {
        dataIn.close()
    }

    override fun toString(): String {
        return "SimpleTextPointsReader(segment=${readState.segmentInfo.name} maxDoc=${readState.segmentInfo.maxDoc()})"
    }
}
