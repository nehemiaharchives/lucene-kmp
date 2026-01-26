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
import org.gnit.lucenekmp.codecs.KnnVectorsReader
import org.gnit.lucenekmp.index.ByteVectorValues
import org.gnit.lucenekmp.index.CorruptIndexException
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.FloatVectorValues
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.internal.hppc.IntObjectHashMap
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.KnnCollector
import org.gnit.lucenekmp.search.VectorScorer
import org.gnit.lucenekmp.store.BufferedChecksumIndexInput
import org.gnit.lucenekmp.store.ChecksumIndexInput
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.StringHelper
import org.gnit.lucenekmp.jdkport.StandardCharsets
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.fromByteArray

/**
 * Reads vector values from a simple text format. All vectors are read up front and cached in RAM in
 * order to support random access. <b>FOR RECREATIONAL USE ONLY</b>
 *
 * @lucene.experimental
 */
internal class SimpleTextKnnVectorsReader(readState: SegmentReadState) : KnnVectorsReader() {

    private val readState: SegmentReadState
    private val dataIn: IndexInput
    private val scratch = BytesRefBuilder()
    private val fieldEntries: IntObjectHashMap<FieldEntry> = IntObjectHashMap()

    init {
        this.readState = readState
        val metaFileName =
            IndexFileNames.segmentFileName(
                readState.segmentInfo.name,
                readState.segmentSuffix,
                SimpleTextKnnVectorsFormat.META_EXTENSION
            )
        val vectorFileName =
            IndexFileNames.segmentFileName(
                readState.segmentInfo.name,
                readState.segmentSuffix,
                SimpleTextKnnVectorsFormat.VECTOR_EXTENSION
            )

        var success = false
        try {
            readState.directory.openChecksumInput(metaFileName).use { `in` ->
                var fieldNumber = readInt(`in`, SimpleTextKnnVectorsWriter.FIELD_NUMBER)
                while (fieldNumber != -1) {
                    val fieldName = readString(`in`, SimpleTextKnnVectorsWriter.FIELD_NAME)
                    val vectorDataOffset = readLong(`in`, SimpleTextKnnVectorsWriter.VECTOR_DATA_OFFSET)
                    val vectorDataLength = readLong(`in`, SimpleTextKnnVectorsWriter.VECTOR_DATA_LENGTH)
                    val dimension = readInt(`in`, SimpleTextKnnVectorsWriter.VECTOR_DIMENSION)
                    val size = readInt(`in`, SimpleTextKnnVectorsWriter.SIZE)
                    val docIds = IntArray(size)
                    for (i in 0 until size) {
                        docIds[i] = readInt(`in`, EMPTY)
                    }
                    assert(!fieldEntries.containsKey(fieldNumber))
                    fieldEntries.put(
                        fieldNumber,
                        FieldEntry(
                            dimension,
                            vectorDataOffset,
                            vectorDataLength,
                            docIds,
                            readState.fieldInfos.fieldInfo(fieldName)!!.vectorSimilarityFunction
                        )
                    )
                    fieldNumber = readInt(`in`, SimpleTextKnnVectorsWriter.FIELD_NUMBER)
                }
                SimpleTextUtil.checkFooter(`in`)
            }

            dataIn = readState.directory.openInput(vectorFileName, IOContext.DEFAULT)
            success = true
        } finally {
            if (!success) {
                IOUtils.closeWhileHandlingException(this)
            }
        }
    }

    @Throws(IOException::class)
    override fun getFloatVectorValues(field: String): FloatVectorValues? {
        val info = readState.fieldInfos.fieldInfo(field)
        if (info == null) {
            // mirror the handling in Lucene90VectorReader#getVectorValues
            // needed to pass TestSimpleTextKnnVectorsFormat#testDeleteAllVectorDocs
            return null
        }
        val dimension = info.vectorDimension
        if (dimension == 0) {
            throw IllegalStateException(
                "KNN vectors readers should not be called on fields that don't enable KNN vectors"
            )
        }
        val fieldEntry = fieldEntries[info.number]
        if (fieldEntry == null) {
            // mirror the handling in Lucene90VectorReader#getVectorValues
            // needed to pass TestSimpleTextKnnVectorsFormat#testDeleteAllVectorDocs
            return null
        }
        if (dimension != fieldEntry.dimension) {
            throw IllegalStateException(
                "Inconsistent vector dimension for field=\"$field\"; $dimension != ${fieldEntry.dimension}"
            )
        }
        val bytesSlice =
            dataIn.slice("vector-data", fieldEntry.vectorDataOffset, fieldEntry.vectorDataLength)
        return SimpleTextFloatVectorValues(fieldEntry, bytesSlice)
    }

    @Throws(IOException::class)
    override fun getByteVectorValues(field: String): ByteVectorValues? {
        val info = readState.fieldInfos.fieldInfo(field)
        if (info == null) {
            // mirror the handling in Lucene90VectorReader#getVectorValues
            // needed to pass TestSimpleTextKnnVectorsFormat#testDeleteAllVectorDocs
            return null
        }
        val dimension = info.vectorDimension
        if (dimension == 0) {
            throw IllegalStateException(
                "KNN vectors readers should not be called on fields that don't enable KNN vectors"
            )
        }
        val fieldEntry = fieldEntries[info.number]
        if (fieldEntry == null) {
            // mirror the handling in Lucene90VectorReader#getVectorValues
            // needed to pass TestSimpleTextKnnVectorsFormat#testDeleteAllVectorDocs
            return null
        }
        if (dimension != fieldEntry.dimension) {
            throw IllegalStateException(
                "Inconsistent vector dimension for field=\"$field\"; $dimension != ${fieldEntry.dimension}"
            )
        }
        val bytesSlice =
            dataIn.slice("vector-data", fieldEntry.vectorDataOffset, fieldEntry.vectorDataLength)
        return SimpleTextByteVectorValues(fieldEntry, bytesSlice)
    }

    @Throws(IOException::class)
    override fun search(field: String, target: FloatArray, knnCollector: KnnCollector, acceptDocs: Bits?) {
        val values = getFloatVectorValues(field) ?: return
        if (target.size != values.dimension()) {
            throw IllegalArgumentException(
                "vector query dimension: ${target.size} differs from field dimension: ${values.dimension()}"
            )
        }
        val info = readState.fieldInfos.fieldInfo(field)!!
        val vectorSimilarity = info.vectorSimilarityFunction
        for (ord in 0 until values.size()) {
            val doc = values.ordToDoc(ord)
            if (acceptDocs != null && !acceptDocs.get(doc)) {
                continue
            }

            if (knnCollector.earlyTerminated()) {
                break
            }

            val vector = values.vectorValue(ord)
            val score = vectorSimilarity.compare(vector, target)
            knnCollector.collect(doc, score)
            knnCollector.incVisitedCount(1)
        }
    }

    @Throws(IOException::class)
    override fun search(field: String, target: ByteArray, knnCollector: KnnCollector, acceptDocs: Bits?) {
        val values = getByteVectorValues(field) ?: return
        if (target.size != values.dimension()) {
            throw IllegalArgumentException(
                "vector query dimension: ${target.size} differs from field dimension: ${values.dimension()}"
            )
        }
        val info = readState.fieldInfos.fieldInfo(field)!!
        val vectorSimilarity = info.vectorSimilarityFunction

        for (ord in 0 until values.size()) {
            val doc = values.ordToDoc(ord)
            if (acceptDocs != null && !acceptDocs.get(doc)) {
                continue
            }

            if (knnCollector.earlyTerminated()) {
                break
            }

            val vector = values.vectorValue(ord)
            val score = vectorSimilarity.compare(vector, target)
            knnCollector.collect(doc, score)
            knnCollector.incVisitedCount(1)
        }
    }

    @Throws(IOException::class)
    override fun checkIntegrity() {
        val clone = dataIn.clone()
        clone.seek(0)

        // checksum is fixed-width encoded with 20 bytes, plus 1 byte for newline (the space is included
        // in SimpleTextUtil.CHECKSUM):
        val footerStartPos = dataIn.length() - (SimpleTextUtil.CHECKSUM.length + 21)
        val input: ChecksumIndexInput = BufferedChecksumIndexInput(clone)

        // when there's no actual vector data written (e.g. tested in
        // TestSimpleTextKnnVectorsFormat#testDeleteAllVectorDocs)
        // the first line in dataInput will be, checksum 00000000000000000000
        if (footerStartPos == 0L) {
            SimpleTextUtil.checkFooter(input)
            return
        }

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

    private data class FieldEntry(
        val dimension: Int,
        val vectorDataOffset: Long,
        val vectorDataLength: Long,
        val ordToDoc: IntArray,
        val similarityFunction: VectorSimilarityFunction
    ) {
        fun size(): Int {
            return ordToDoc.size
        }
    }

    private class SimpleTextFloatVectorValues(
        private val entry: FieldEntry,
        private val `in`: IndexInput
    ) : FloatVectorValues() {

        private val scratch = BytesRefBuilder()
        private val values: Array<FloatArray>
        private var curOrd: Int

        init {
            values = Array(entry.size()) { FloatArray(entry.dimension) }
            curOrd = -1
            readAllVectors()
        }

        private constructor(other: SimpleTextFloatVectorValues) : this(other.entry, other.`in`.clone()) {
            this.curOrd = other.curOrd
            // values are shared
            for (i in values.indices) {
                values[i] = other.values[i]
            }
        }

        override fun dimension(): Int {
            return entry.dimension
        }

        override fun size(): Int {
            return entry.size()
        }

        override fun vectorValue(ord: Int): FloatArray {
            return values[ord]
        }

        override fun ordToDoc(ord: Int): Int {
            return entry.ordToDoc[ord]
        }

        override fun iterator(): DocIndexIterator {
            return createSparseIterator()
        }

        override fun scorer(target: FloatArray): VectorScorer? {
            if (size() == 0) {
                return null
            }
            val simpleTextFloatVectorValues = SimpleTextFloatVectorValues(this)
            val iterator = simpleTextFloatVectorValues.iterator()
            return object : VectorScorer {
                @Throws(IOException::class)
                override fun score(): Float {
                    val ord = iterator.index()
                    return entry.similarityFunction.compare(
                        simpleTextFloatVectorValues.vectorValue(ord),
                        target
                    )
                }

                override fun iterator(): DocIdSetIterator {
                    return iterator
                }
            }
        }

        @Throws(IOException::class)
        private fun readAllVectors() {
            for (value in values) {
                readVector(value)
            }
        }

        @Throws(IOException::class)
        private fun readVector(value: FloatArray) {
            SimpleTextUtil.readLine(`in`, scratch)
            // skip leading "[" and strip trailing "]"
            val s = BytesRef(scratch.bytes(), 1, scratch.length() - 2).utf8ToString()
            val floatStrings = s.split(",")
            assert(floatStrings.size == value.size) { " read $s when expecting ${value.size} floats" }
            for (i in floatStrings.indices) {
                value[i] = floatStrings[i].toFloat()
            }
        }

        override fun copy(): SimpleTextFloatVectorValues {
            return this
        }
    }

    private class SimpleTextByteVectorValues(
        private val entry: FieldEntry,
        private val `in`: IndexInput
    ) : ByteVectorValues() {

        private val scratch = BytesRefBuilder()
        private val binaryValue: BytesRef
        private val values: Array<ByteArray>
        private var curOrd: Int

        init {
            values = Array(entry.size()) { ByteArray(entry.dimension) }
            binaryValue = BytesRef(entry.dimension)
            binaryValue.length = binaryValue.bytes.size
            curOrd = -1
            readAllVectors()
        }

        private constructor(other: SimpleTextByteVectorValues) : this(other.entry, other.`in`.clone()) {
            this.curOrd = other.curOrd
            for (i in values.indices) {
                values[i] = other.values[i]
            }
        }

        override fun dimension(): Int {
            return entry.dimension
        }

        override fun size(): Int {
            return entry.size()
        }

        override fun vectorValue(ord: Int): ByteArray {
            binaryValue.bytes = values[ord]
            return binaryValue.bytes
        }

        override fun ordToDoc(ord: Int): Int {
            return entry.ordToDoc[ord]
        }

        override fun iterator(): DocIndexIterator {
            return createSparseIterator()
        }

        override fun scorer(target: ByteArray): VectorScorer? {
            if (size() == 0) {
                return null
            }
            val simpleTextByteVectorValues = SimpleTextByteVectorValues(this)
            return object : VectorScorer {
                private val it = simpleTextByteVectorValues.iterator()

                @Throws(IOException::class)
                override fun score(): Float {
                    val ord = it.index()
                    return entry.similarityFunction.compare(
                        simpleTextByteVectorValues.vectorValue(ord),
                        target
                    )
                }

                override fun iterator(): DocIdSetIterator {
                    return it
                }
            }
        }

        @Throws(IOException::class)
        private fun readAllVectors() {
            for (value in values) {
                readVector(value)
            }
        }

        @Throws(IOException::class)
        private fun readVector(value: ByteArray) {
            SimpleTextUtil.readLine(`in`, scratch)
            // skip leading "[" and strip trailing "]"
            val s = BytesRef(scratch.bytes(), 1, scratch.length() - 2).utf8ToString()
            val floatStrings = s.split(",")
            assert(floatStrings.size == value.size) { " read $s when expecting ${value.size} floats" }
            for (i in floatStrings.indices) {
                value[i] = floatStrings[i].toFloat().toInt().toByte()
            }
        }

        override fun copy(): SimpleTextByteVectorValues {
            return this
        }
    }

    @Throws(IOException::class)
    private fun readInt(`in`: IndexInput, field: BytesRef): Int {
        SimpleTextUtil.readLine(`in`, scratch)
        return parseInt(field)
    }

    @Throws(IOException::class)
    private fun readLong(`in`: IndexInput, field: BytesRef): Long {
        SimpleTextUtil.readLine(`in`, scratch)
        return parseLong(field)
    }

    @Throws(IOException::class)
    private fun readString(`in`: IndexInput, field: BytesRef): String {
        SimpleTextUtil.readLine(`in`, scratch)
        return stripPrefix(field)
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
        val prefixLen = prefix.length
        return String.fromByteArray(
            scratch.bytes(),
            prefixLen,
            scratch.length() - prefixLen,
            StandardCharsets.UTF_8
        )
    }

    companion object {
        private val EMPTY = BytesRef("")
    }
}
