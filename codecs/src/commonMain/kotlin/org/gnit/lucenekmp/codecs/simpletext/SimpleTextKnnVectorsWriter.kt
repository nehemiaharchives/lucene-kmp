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
import org.gnit.lucenekmp.codecs.BufferingKnnVectorsWriter
import org.gnit.lucenekmp.index.ByteVectorValues
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.FloatVectorValues
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.SegmentWriteState
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.IOUtils

/** Writes vector-valued fields in a plain text format */
internal class SimpleTextKnnVectorsWriter(state: SegmentWriteState) : BufferingKnnVectorsWriter() {

    private val meta: IndexOutput
    private val vectorData: IndexOutput
    private val scratch = BytesRefBuilder()

    init {
        var success = false
        // exception handling to pass TestSimpleTextKnnVectorsFormat#testRandomExceptions
        try {
            val metaFileName =
                IndexFileNames.segmentFileName(
                    state.segmentInfo.name,
                    state.segmentSuffix,
                    SimpleTextKnnVectorsFormat.META_EXTENSION
                )
            meta = state.directory.createOutput(metaFileName, state.context)

            val vectorDataFileName =
                IndexFileNames.segmentFileName(
                    state.segmentInfo.name,
                    state.segmentSuffix,
                    SimpleTextKnnVectorsFormat.VECTOR_EXTENSION
                )
            vectorData = state.directory.createOutput(vectorDataFileName, state.context)
            success = true
        } finally {
            if (!success) {
                IOUtils.closeWhileHandlingException(this)
            }
        }
    }

    @Throws(IOException::class)
    override fun writeField(fieldInfo: FieldInfo, floatVectorValues: FloatVectorValues, maxDoc: Int) {
        val vectorDataOffset = vectorData.filePointer
        val docIds = ArrayList<Int>()
        val iter = floatVectorValues.iterator()
        var docId = iter.nextDoc()
        while (docId != DocIdSetIterator.NO_MORE_DOCS) {
            writeFloatVectorValue(floatVectorValues, iter.index())
            docIds.add(docId)
            docId = iter.nextDoc()
        }
        val vectorDataLength = vectorData.filePointer - vectorDataOffset
        writeMeta(fieldInfo, vectorDataOffset, vectorDataLength, docIds)
    }

    @Throws(IOException::class)
    private fun writeFloatVectorValue(vectors: FloatVectorValues, ord: Int) {
        // write vector value
        val value = vectors.vectorValue(ord)
        assert(value.size == vectors.dimension())
        write(vectorData, value.contentToString())
        newline(vectorData)
    }

    @Throws(IOException::class)
    override fun writeField(fieldInfo: FieldInfo, byteVectorValues: ByteVectorValues, maxDoc: Int) {
        val vectorDataOffset = vectorData.filePointer
        val docIds = ArrayList<Int>()
        val it = byteVectorValues.iterator()
        var docV = it.nextDoc()
        while (docV != DocIdSetIterator.NO_MORE_DOCS) {
            writeByteVectorValue(byteVectorValues, it.index())
            docIds.add(docV)
            docV = it.nextDoc()
        }
        val vectorDataLength = vectorData.filePointer - vectorDataOffset
        writeMeta(fieldInfo, vectorDataOffset, vectorDataLength, docIds)
    }

    @Throws(IOException::class)
    private fun writeByteVectorValue(vectors: ByteVectorValues, ord: Int) {
        // write vector value
        val value = vectors.vectorValue(ord)
        assert(value.size == vectors.dimension())
        write(vectorData, value.contentToString())
        newline(vectorData)
    }

    @Throws(IOException::class)
    private fun writeMeta(
        field: FieldInfo,
        vectorDataOffset: Long,
        vectorDataLength: Long,
        docIds: List<Int>
    ) {
        writeField(meta, FIELD_NUMBER, field.number)
        writeField(meta, FIELD_NAME, field.name)
        writeField(meta, VECTOR_DATA_OFFSET, vectorDataOffset)
        writeField(meta, VECTOR_DATA_LENGTH, vectorDataLength)
        writeField(meta, VECTOR_DIMENSION, field.vectorDimension)
        writeField(meta, SIZE, docIds.size)
        for (docId in docIds) {
            writeInt(meta, docId)
            newline(meta)
        }
    }

    @Throws(IOException::class)
    override fun finish() {
        writeField(meta, FIELD_NUMBER, -1)
        SimpleTextUtil.writeChecksum(meta, scratch)
        SimpleTextUtil.writeChecksum(vectorData, scratch)
    }

    @Throws(IOException::class)
    override fun close() {
        IOUtils.close(vectorData, meta)
    }

    @Throws(IOException::class)
    private fun writeField(out: IndexOutput, fieldName: BytesRef, value: Int) {
        write(out, fieldName)
        writeInt(out, value)
        newline(out)
    }

    @Throws(IOException::class)
    private fun writeField(out: IndexOutput, fieldName: BytesRef, value: Long) {
        write(out, fieldName)
        writeLong(out, value)
        newline(out)
    }

    @Throws(IOException::class)
    private fun writeField(out: IndexOutput, fieldName: BytesRef, value: String) {
        write(out, fieldName)
        write(out, value)
        newline(out)
    }

    @Throws(IOException::class)
    private fun write(out: IndexOutput, s: String) {
        SimpleTextUtil.write(out, s, scratch)
    }

    @Throws(IOException::class)
    private fun writeInt(out: IndexOutput, x: Int) {
        SimpleTextUtil.write(out, x.toString(), scratch)
    }

    @Throws(IOException::class)
    private fun writeLong(out: IndexOutput, x: Long) {
        SimpleTextUtil.write(out, x.toString(), scratch)
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
        val FIELD_NUMBER = BytesRef("field-number ")
        val FIELD_NAME = BytesRef("field-name ")
        val VECTOR_DATA_OFFSET = BytesRef("vector-data-offset ")
        val VECTOR_DATA_LENGTH = BytesRef("vector-data-length ")
        val VECTOR_DIMENSION = BytesRef("vector-dimension ")
        val SIZE = BytesRef("size ")
    }
}
