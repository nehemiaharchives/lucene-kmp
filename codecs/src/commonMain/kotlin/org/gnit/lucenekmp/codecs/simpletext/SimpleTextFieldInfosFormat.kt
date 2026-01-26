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
import org.gnit.lucenekmp.codecs.FieldInfosFormat
import org.gnit.lucenekmp.index.DocValuesSkipIndexType
import org.gnit.lucenekmp.index.DocValuesType
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.FieldInfos
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.index.SegmentInfo
import org.gnit.lucenekmp.index.VectorEncoding
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.jdkport.StandardCharsets
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.fromByteArray
import org.gnit.lucenekmp.store.ChecksumIndexInput
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.StringHelper

/**
 * plaintext field infos format
 *
 * <p><b>FOR RECREATIONAL USE ONLY</b>
 *
 * @lucene.experimental
 */
class SimpleTextFieldInfosFormat : FieldInfosFormat() {

    /** Extension of field infos */
    companion object {
        const val FIELD_INFOS_EXTENSION = "inf"

        val NUMFIELDS: BytesRef = BytesRef("number of fields ")
        val NAME: BytesRef = BytesRef("  name ")
        val NUMBER: BytesRef = BytesRef("  number ")
        val STORETV: BytesRef = BytesRef("  term vectors ")
        val STORETVPOS: BytesRef = BytesRef("  term vector positions ")
        val STORETVOFF: BytesRef = BytesRef("  term vector offsets ")
        val PAYLOADS: BytesRef = BytesRef("  payloads ")
        val NORMS: BytesRef = BytesRef("  norms ")
        val DOCVALUES: BytesRef = BytesRef("  doc values ")
        val DOCVALUES_SKIP_INDEX: BytesRef = BytesRef("  doc values skip index")
        val DOCVALUES_GEN: BytesRef = BytesRef("  doc values gen ")
        val INDEXOPTIONS: BytesRef = BytesRef("  index options ")
        val NUM_ATTS: BytesRef = BytesRef("  attributes ")
        val ATT_KEY: BytesRef = BytesRef("    key ")
        val ATT_VALUE: BytesRef = BytesRef("    value ")
        val DATA_DIM_COUNT: BytesRef = BytesRef("  data dimensional count ")
        val INDEX_DIM_COUNT: BytesRef = BytesRef("  index dimensional count ")
        val DIM_NUM_BYTES: BytesRef = BytesRef("  dimensional num bytes ")
        val VECTOR_NUM_DIMS: BytesRef = BytesRef("  vector number of dimensions ")
        val VECTOR_ENCODING: BytesRef = BytesRef("  vector encoding ")
        val VECTOR_SIMILARITY: BytesRef = BytesRef("  vector similarity ")
        val SOFT_DELETES: BytesRef = BytesRef("  soft-deletes ")
        val PARENT: BytesRef = BytesRef("  parent ")
    }

    @Throws(IOException::class)
    override fun read(
        directory: Directory,
        segmentInfo: SegmentInfo,
        segmentSuffix: String,
        iocontext: IOContext
    ): FieldInfos {
        val fileName =
            IndexFileNames.segmentFileName(segmentInfo.name, segmentSuffix, FIELD_INFOS_EXTENSION)
        val input: ChecksumIndexInput = directory.openChecksumInput(fileName)
        val scratch = BytesRefBuilder()

        var success = false
        try {
            SimpleTextUtil.readLine(input, scratch)
            assert(StringHelper.startsWith(scratch.get(), NUMFIELDS))
            val size = readString(NUMFIELDS.length, scratch).toInt()
            val infos = arrayOfNulls<FieldInfo>(size)

            for (i in 0 until size) {
                SimpleTextUtil.readLine(input, scratch)
                assert(StringHelper.startsWith(scratch.get(), NAME))
                val name = readString(NAME.length, scratch)

                SimpleTextUtil.readLine(input, scratch)
                assert(StringHelper.startsWith(scratch.get(), NUMBER))
                val fieldNumber = readString(NUMBER.length, scratch).toInt()

                SimpleTextUtil.readLine(input, scratch)
                assert(StringHelper.startsWith(scratch.get(), INDEXOPTIONS))
                val indexOptions = IndexOptions.valueOf(readString(INDEXOPTIONS.length, scratch))

                SimpleTextUtil.readLine(input, scratch)
                assert(StringHelper.startsWith(scratch.get(), STORETV))
                val storeTermVector =
                    readString(STORETV.length, scratch).toBoolean()

                SimpleTextUtil.readLine(input, scratch)
                assert(StringHelper.startsWith(scratch.get(), PAYLOADS))
                val storePayloads = readString(PAYLOADS.length, scratch).toBoolean()

                SimpleTextUtil.readLine(input, scratch)
                assert(StringHelper.startsWith(scratch.get(), NORMS))
                val omitNorms = !readString(NORMS.length, scratch).toBoolean()

                SimpleTextUtil.readLine(input, scratch)
                assert(StringHelper.startsWith(scratch.get(), DOCVALUES))
                val docValuesType = docValuesType(readString(DOCVALUES.length, scratch))

                SimpleTextUtil.readLine(input, scratch)
                assert(StringHelper.startsWith(scratch.get(), DOCVALUES_SKIP_INDEX))
                val docValueSkipper =
                    docValuesSkipIndexType(readString(DOCVALUES_SKIP_INDEX.length, scratch))

                SimpleTextUtil.readLine(input, scratch)
                assert(StringHelper.startsWith(scratch.get(), DOCVALUES_GEN))
                val dvGen = readString(DOCVALUES_GEN.length, scratch).toLong()

                SimpleTextUtil.readLine(input, scratch)
                assert(StringHelper.startsWith(scratch.get(), NUM_ATTS))
                val numAtts = readString(NUM_ATTS.length, scratch).toInt()
                val atts: MutableMap<String, String> = HashMap()

                for (j in 0 until numAtts) {
                    SimpleTextUtil.readLine(input, scratch)
                    assert(StringHelper.startsWith(scratch.get(), ATT_KEY))
                    val key = readString(ATT_KEY.length, scratch)

                    SimpleTextUtil.readLine(input, scratch)
                    assert(StringHelper.startsWith(scratch.get(), ATT_VALUE))
                    val value = readString(ATT_VALUE.length, scratch)
                    atts[key] = value
                }

                SimpleTextUtil.readLine(input, scratch)
                assert(StringHelper.startsWith(scratch.get(), DATA_DIM_COUNT))
                val dimensionalCount = readString(DATA_DIM_COUNT.length, scratch).toInt()

                SimpleTextUtil.readLine(input, scratch)
                assert(StringHelper.startsWith(scratch.get(), INDEX_DIM_COUNT))
                val indexDimensionalCount = readString(INDEX_DIM_COUNT.length, scratch).toInt()

                SimpleTextUtil.readLine(input, scratch)
                assert(StringHelper.startsWith(scratch.get(), DIM_NUM_BYTES))
                val dimensionalNumBytes = readString(DIM_NUM_BYTES.length, scratch).toInt()

                SimpleTextUtil.readLine(input, scratch)
                assert(StringHelper.startsWith(scratch.get(), VECTOR_NUM_DIMS))
                val vectorNumDimensions = readString(VECTOR_NUM_DIMS.length, scratch).toInt()

                SimpleTextUtil.readLine(input, scratch)
                assert(StringHelper.startsWith(scratch.get(), VECTOR_ENCODING))
                val vectorEncoding = vectorEncoding(readString(VECTOR_ENCODING.length, scratch))

                SimpleTextUtil.readLine(input, scratch)
                assert(StringHelper.startsWith(scratch.get(), VECTOR_SIMILARITY))
                val vectorDistFunc = distanceFunction(readString(VECTOR_SIMILARITY.length, scratch))

                SimpleTextUtil.readLine(input, scratch)
                assert(StringHelper.startsWith(scratch.get(), SOFT_DELETES))
                val isSoftDeletesField =
                    readString(SOFT_DELETES.length, scratch).toBoolean()
                SimpleTextUtil.readLine(input, scratch)
                assert(StringHelper.startsWith(scratch.get(), PARENT))
                val isParentField = readString(PARENT.length, scratch).toBoolean()

                infos[i] =
                    FieldInfo(
                        name,
                        fieldNumber,
                        storeTermVector,
                        omitNorms,
                        storePayloads,
                        indexOptions,
                        docValuesType,
                        docValueSkipper,
                        dvGen,
                        atts.toMutableMap(),
                        dimensionalCount,
                        indexDimensionalCount,
                        dimensionalNumBytes,
                        vectorNumDimensions,
                        vectorEncoding,
                        vectorDistFunc,
                        isSoftDeletesField,
                        isParentField
                    )
            }

            SimpleTextUtil.checkFooter(input)

            val fieldInfos = FieldInfos(infos.requireNoNulls())
            success = true
            return fieldInfos
        } finally {
            if (success) {
                input.close()
            } else {
                IOUtils.closeWhileHandlingException(input)
            }
        }
    }

    fun docValuesType(dvType: String): DocValuesType {
        return DocValuesType.valueOf(dvType)
    }

    fun docValuesSkipIndexType(dvSkipIndexType: String): DocValuesSkipIndexType {
        return DocValuesSkipIndexType.valueOf(dvSkipIndexType)
    }

    fun vectorEncoding(vectorEncoding: String): VectorEncoding {
        return VectorEncoding.valueOf(vectorEncoding)
    }

    fun distanceFunction(scoreFunction: String): VectorSimilarityFunction {
        return VectorSimilarityFunction.valueOf(scoreFunction)
    }

    private fun readString(offset: Int, scratch: BytesRefBuilder): String {
        val bytes = scratch.bytes().copyOfRange(offset, scratch.length())
        return bytes.decodeToString()
    }

    @Throws(IOException::class)
    override fun write(
        directory: Directory,
        segmentInfo: SegmentInfo,
        segmentSuffix: String,
        infos: FieldInfos,
        context: IOContext
    ) {
        val fileName =
            IndexFileNames.segmentFileName(segmentInfo.name, segmentSuffix, FIELD_INFOS_EXTENSION)
        val out = directory.createOutput(fileName, context)
        val scratch = BytesRefBuilder()
        var success = false
        try {
            SimpleTextUtil.write(out, NUMFIELDS)
            SimpleTextUtil.write(out, infos.size().toString(), scratch)
            SimpleTextUtil.writeNewline(out)

            for (fi in infos) {
                SimpleTextUtil.write(out, NAME)
                SimpleTextUtil.write(out, fi.name, scratch)
                SimpleTextUtil.writeNewline(out)

                SimpleTextUtil.write(out, NUMBER)
                SimpleTextUtil.write(out, fi.number.toString(), scratch)
                SimpleTextUtil.writeNewline(out)

                SimpleTextUtil.write(out, INDEXOPTIONS)
                val indexOptions = fi.indexOptions
                assert(indexOptions.compareTo(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS) >= 0 || !fi.hasPayloads())
                SimpleTextUtil.write(out, indexOptions.toString(), scratch)
                SimpleTextUtil.writeNewline(out)

                SimpleTextUtil.write(out, STORETV)
                SimpleTextUtil.write(out, fi.hasTermVectors().toString(), scratch)
                SimpleTextUtil.writeNewline(out)

                SimpleTextUtil.write(out, PAYLOADS)
                SimpleTextUtil.write(out, fi.hasPayloads().toString(), scratch)
                SimpleTextUtil.writeNewline(out)

                SimpleTextUtil.write(out, NORMS)
                SimpleTextUtil.write(out, (!fi.omitsNorms()).toString(), scratch)
                SimpleTextUtil.writeNewline(out)

                SimpleTextUtil.write(out, DOCVALUES)
                SimpleTextUtil.write(out, getDocValuesType(fi.docValuesType), scratch)
                SimpleTextUtil.writeNewline(out)

                SimpleTextUtil.write(out, DOCVALUES_SKIP_INDEX)
                SimpleTextUtil.write(out, getDocValuesSkipIndexType(fi.docValuesSkipIndexType()), scratch)
                SimpleTextUtil.writeNewline(out)

                SimpleTextUtil.write(out, DOCVALUES_GEN)
                SimpleTextUtil.write(out, fi.docValuesGen.toString(), scratch)
                SimpleTextUtil.writeNewline(out)

                val atts = fi.attributes()
                val numAtts = atts.size
                SimpleTextUtil.write(out, NUM_ATTS)
                SimpleTextUtil.write(out, numAtts.toString(), scratch)
                SimpleTextUtil.writeNewline(out)

                if (numAtts > 0) {
                    for ((key, value) in atts) {
                        SimpleTextUtil.write(out, ATT_KEY)
                        SimpleTextUtil.write(out, key, scratch)
                        SimpleTextUtil.writeNewline(out)

                        SimpleTextUtil.write(out, ATT_VALUE)
                        SimpleTextUtil.write(out, value, scratch)
                        SimpleTextUtil.writeNewline(out)
                    }
                }

                SimpleTextUtil.write(out, DATA_DIM_COUNT)
                SimpleTextUtil.write(out, fi.pointDimensionCount.toString(), scratch)
                SimpleTextUtil.writeNewline(out)

                SimpleTextUtil.write(out, INDEX_DIM_COUNT)
                SimpleTextUtil.write(out, fi.pointIndexDimensionCount.toString(), scratch)
                SimpleTextUtil.writeNewline(out)

                SimpleTextUtil.write(out, DIM_NUM_BYTES)
                SimpleTextUtil.write(out, fi.pointNumBytes.toString(), scratch)
                SimpleTextUtil.writeNewline(out)

                SimpleTextUtil.write(out, VECTOR_NUM_DIMS)
                SimpleTextUtil.write(out, fi.vectorDimension.toString(), scratch)
                SimpleTextUtil.writeNewline(out)

                SimpleTextUtil.write(out, VECTOR_ENCODING)
                SimpleTextUtil.write(out, fi.vectorEncoding.name, scratch)
                SimpleTextUtil.writeNewline(out)

                SimpleTextUtil.write(out, VECTOR_SIMILARITY)
                SimpleTextUtil.write(out, fi.vectorSimilarityFunction.name, scratch)
                SimpleTextUtil.writeNewline(out)

                SimpleTextUtil.write(out, SOFT_DELETES)
                SimpleTextUtil.write(out, fi.isSoftDeletesField.toString(), scratch)
                SimpleTextUtil.writeNewline(out)

                SimpleTextUtil.write(out, PARENT)
                SimpleTextUtil.write(out, fi.isParentField.toString(), scratch)
                SimpleTextUtil.writeNewline(out)
            }
            SimpleTextUtil.writeChecksum(out, scratch)
            success = true
        } finally {
            if (success) {
                out.close()
            } else {
                IOUtils.closeWhileHandlingException(out)
            }
        }
    }

    private fun getDocValuesType(type: DocValuesType): String {
        return type.toString()
    }

    private fun getDocValuesSkipIndexType(type: DocValuesSkipIndexType): String {
        return type.toString()
    }
}
