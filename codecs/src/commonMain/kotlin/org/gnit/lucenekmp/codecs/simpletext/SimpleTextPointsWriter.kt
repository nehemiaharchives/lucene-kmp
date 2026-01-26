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
import org.gnit.lucenekmp.codecs.PointsWriter
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.index.SegmentWriteState
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.bkd.BKDConfig
import org.gnit.lucenekmp.util.bkd.BKDConfig.Companion.DEFAULT_MAX_POINTS_IN_LEAF_NODE

internal class SimpleTextPointsWriter(writeState: SegmentWriteState) : PointsWriter() {

    private var dataOut: IndexOutput? = null
    private val scratch = BytesRefBuilder()
    private val writeState: SegmentWriteState
    private val indexFPs: MutableMap<String, Long> = HashMap()

    init {
        val fileName =
            IndexFileNames.segmentFileName(
                writeState.segmentInfo.name,
                writeState.segmentSuffix,
                SimpleTextPointsFormat.POINT_EXTENSION
            )
        dataOut = writeState.directory.createOutput(fileName, writeState.context)
        this.writeState = writeState
    }

    @Throws(IOException::class)
    override fun writeField(fieldInfo: FieldInfo, reader: PointsReader) {
        val values: PointValues.PointTree = reader.getValues(fieldInfo.name)!!.pointTree

        val config =
            BKDConfig(
                fieldInfo.pointDimensionCount,
                fieldInfo.pointIndexDimensionCount,
                fieldInfo.pointNumBytes,
                DEFAULT_MAX_POINTS_IN_LEAF_NODE
            )

        // We use our own fork of the BKDWriter to customize how it writes the index and blocks to disk:
        SimpleTextBKDWriter(
            writeState.segmentInfo.maxDoc(),
            writeState.directory,
            writeState.segmentInfo.name,
            config,
            SimpleTextBKDWriter.DEFAULT_MAX_MB_SORT_IN_HEAP.toDouble(),
            values.size()
        ).use { writer ->
            values.visitDocValues(
                object : PointValues.IntersectVisitor {
                    override fun visit(docID: Int) {
                        throw IllegalStateException()
                    }

                    @Throws(IOException::class)
                    override fun visit(docID: Int, packedValue: ByteArray) {
                        writer.add(packedValue, docID)
                    }

                    override fun compare(minPackedValue: ByteArray, maxPackedValue: ByteArray): PointValues.Relation {
                        return PointValues.Relation.CELL_CROSSES_QUERY
                    }
                }
            )

            // We could have 0 points on merge since all docs with points may be deleted:
            if (writer.pointCount > 0) {
                indexFPs[fieldInfo.name] = writer.finish(requireNotNull(dataOut))
            }
        }
    }

    @Throws(IOException::class)
    override fun finish() {
        SimpleTextUtil.write(requireNotNull(dataOut), END)
        SimpleTextUtil.writeNewline(requireNotNull(dataOut))
        SimpleTextUtil.writeChecksum(requireNotNull(dataOut), scratch)
    }

    @Throws(IOException::class)
    override fun close() {
        if (dataOut != null) {
            dataOut!!.close()
            dataOut = null

            // Write index file
            val fileName =
                IndexFileNames.segmentFileName(
                    writeState.segmentInfo.name,
                    writeState.segmentSuffix,
                    SimpleTextPointsFormat.POINT_INDEX_EXTENSION
                )
            writeState.directory.createOutput(fileName, writeState.context).use { indexOut ->
                val count = indexFPs.size
                write(indexOut, FIELD_COUNT)
                write(indexOut, count.toString())
                newline(indexOut)
                for (ent in indexFPs.entries) {
                    write(indexOut, FIELD_FP_NAME)
                    write(indexOut, ent.key)
                    newline(indexOut)
                    write(indexOut, FIELD_FP)
                    write(indexOut, ent.value.toString())
                    newline(indexOut)
                }
                SimpleTextUtil.writeChecksum(indexOut, scratch)
            }
        }
    }

    @Throws(IOException::class)
    private fun write(out: IndexOutput, s: String) {
        SimpleTextUtil.write(out, s, scratch)
    }

    @Throws(IOException::class)
    private fun write(out: IndexOutput, b: BytesRef) {
        SimpleTextUtil.write(out, b)
    }

    @Throws(IOException::class)
    private fun newline(out: IndexOutput) {
        SimpleTextUtil.writeNewline(out)
    }

    companion object {
        val NUM_DATA_DIMS = BytesRef("num data dims ")
        val NUM_INDEX_DIMS = BytesRef("num index dims ")
        val BYTES_PER_DIM = BytesRef("bytes per dim ")
        val MAX_LEAF_POINTS = BytesRef("max leaf points ")
        val INDEX_COUNT = BytesRef("index count ")
        val BLOCK_COUNT = BytesRef("block count ")
        val BLOCK_DOC_ID = BytesRef("  doc ")
        val BLOCK_FP = BytesRef("  block fp ")
        val BLOCK_VALUE = BytesRef("  block value ")
        val SPLIT_COUNT = BytesRef("split count ")
        val SPLIT_DIM = BytesRef("  split dim ")
        val SPLIT_VALUE = BytesRef("  split value ")
        val FIELD_COUNT = BytesRef("field count ")
        val FIELD_FP_NAME = BytesRef("  field fp name ")
        val FIELD_FP = BytesRef("  field fp ")
        val MIN_VALUE = BytesRef("min value ")
        val MAX_VALUE = BytesRef("max value ")
        val POINT_COUNT = BytesRef("point count ")
        val DOC_COUNT = BytesRef("doc count ")
        val END = BytesRef("END")
    }
}
