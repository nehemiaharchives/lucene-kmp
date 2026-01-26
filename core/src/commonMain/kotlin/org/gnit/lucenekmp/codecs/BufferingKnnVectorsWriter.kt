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
package org.gnit.lucenekmp.codecs

import okio.IOException
import org.gnit.lucenekmp.index.ByteVectorValues
import org.gnit.lucenekmp.index.DocsWithFieldSet
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.FloatVectorValues
import org.gnit.lucenekmp.index.MergeState
import org.gnit.lucenekmp.index.Sorter
import org.gnit.lucenekmp.index.SortingCodecReader
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.DocIdSet
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.RamUsageEstimator

/**
 * Buffers up pending vector value(s) per doc, then flushes when segment flushes. Used for `
 * SimpleTextKnnVectorsWriter` and for vectors writers before v 9.3 .
 *
 * @lucene.experimental
 */
abstract class BufferingKnnVectorsWriter protected constructor() : KnnVectorsWriter() {
    private val fields: MutableList<FieldWriter<*>> = ArrayList()

    /** Sole constructor */
    protected constructor(dummy: Boolean = true) : this()

    @Throws(IOException::class)
    override fun addField(fieldInfo: FieldInfo): KnnFieldVectorsWriter<*> {
        val newField: FieldWriter<*>
        when (fieldInfo.vectorEncoding) {
            org.gnit.lucenekmp.index.VectorEncoding.FLOAT32 -> {
                newField =
                    object : FieldWriter<FloatArray>(fieldInfo) {
                        override fun copyValue(vectorValue: FloatArray): FloatArray {
                            return ArrayUtil.copyOfSubArray(
                                vectorValue,
                                0,
                                fieldInfo.vectorDimension
                            )
                        }
                    }
            }

            org.gnit.lucenekmp.index.VectorEncoding.BYTE -> {
                newField =
                    object : FieldWriter<ByteArray>(fieldInfo) {
                        override fun copyValue(vectorValue: ByteArray): ByteArray {
                            return ArrayUtil.copyOfSubArray(
                                vectorValue,
                                0,
                                fieldInfo.vectorDimension
                            )
                        }
                    }
            }

            else -> {
                throw UnsupportedOperationException()
            }
        }
        fields.add(newField)
        return newField
    }

    @Throws(IOException::class)
    override fun flush(maxDoc: Int, sortMap: Sorter.DocMap?) {
        for (fieldData in fields) {
            when (fieldData.fieldInfo.vectorEncoding) {
                org.gnit.lucenekmp.index.VectorEncoding.FLOAT32 -> {
                    val bufferedFloatVectorValues =
                        BufferedFloatVectorValues(
                            fieldData.vectors as List<FloatArray>,
                            fieldData.fieldInfo.vectorDimension,
                            fieldData.docsWithField
                        )
                    val floatVectorValues =
                        if (sortMap != null) {
                            SortingFloatVectorValues(
                                bufferedFloatVectorValues,
                                fieldData.docsWithField,
                                sortMap
                            )
                        } else {
                            bufferedFloatVectorValues
                        }
                    writeField(fieldData.fieldInfo, floatVectorValues, maxDoc)
                }

                org.gnit.lucenekmp.index.VectorEncoding.BYTE -> {
                    val bufferedByteVectorValues =
                        BufferedByteVectorValues(
                            fieldData.vectors as List<ByteArray>,
                            fieldData.fieldInfo.vectorDimension,
                            fieldData.docsWithField
                        )
                    val byteVectorValues =
                        if (sortMap != null) {
                            SortingByteVectorValues(
                                bufferedByteVectorValues,
                                fieldData.docsWithField,
                                sortMap
                            )
                        } else {
                            bufferedByteVectorValues
                        }
                    writeField(fieldData.fieldInfo, byteVectorValues, maxDoc)
                }
            }
        }
    }

    /** Sorting FloatVectorValues that iterate over documents in the order of the provided sortMap */
    private class SortingFloatVectorValues(
        delegate: BufferedFloatVectorValues,
        docsWithField: DocsWithFieldSet,
        sortMap: Sorter.DocMap
    ) : FloatVectorValues() {
        private val delegate: BufferedFloatVectorValues
        private val iteratorSupplier: SortingCodecReader.SortingIteratorSupplier

        init {
            this.delegate = delegate.copy()
            iteratorSupplier = SortingCodecReader.iteratorSupplier(delegate, sortMap)
        }

        @Throws(IOException::class)
        override fun vectorValue(ord: Int): FloatArray {
            return delegate.vectorValue(ord)
        }

        override fun dimension(): Int {
            return delegate.dimension()
        }

        override fun size(): Int {
            return delegate.size()
        }

        override fun copy(): SortingFloatVectorValues {
            throw UnsupportedOperationException()
        }

        override fun iterator(): DocIndexIterator {
            return iteratorSupplier.get()
        }
    }

    /** Sorting ByteVectorValues that iterate over documents in the order of the provided sortMap */
    private class SortingByteVectorValues(
        delegate: BufferedByteVectorValues,
        docsWithField: DocsWithFieldSet,
        sortMap: Sorter.DocMap
    ) : ByteVectorValues() {
        private val delegate: BufferedByteVectorValues
        private val iteratorSupplier: SortingCodecReader.SortingIteratorSupplier

        init {
            this.delegate = delegate
            iteratorSupplier = SortingCodecReader.iteratorSupplier(delegate, sortMap)
        }

        @Throws(IOException::class)
        override fun vectorValue(ord: Int): ByteArray {
            return delegate.vectorValue(ord)
        }

        override fun dimension(): Int {
            return delegate.dimension()
        }

        override fun size(): Int {
            return delegate.size()
        }

        override fun copy(): SortingByteVectorValues {
            throw UnsupportedOperationException()
        }

        override fun iterator(): DocIndexIterator {
            return iteratorSupplier.get()
        }
    }

    override fun ramBytesUsed(): Long {
        var total = 0L
        for (field in fields) {
            total += field.ramBytesUsed()
        }
        return total
    }

    @Throws(IOException::class)
    override fun mergeOneField(fieldInfo: FieldInfo, mergeState: MergeState) {
        when (fieldInfo.vectorEncoding) {
            org.gnit.lucenekmp.index.VectorEncoding.FLOAT32 -> {
                val floatVectorValues =
                    KnnVectorsWriter.MergedVectorValues.mergeFloatVectorValues(fieldInfo, mergeState)
                writeField(fieldInfo, floatVectorValues, mergeState.segmentInfo.maxDoc())
            }

            org.gnit.lucenekmp.index.VectorEncoding.BYTE -> {
                val byteVectorValues =
                    KnnVectorsWriter.MergedVectorValues.mergeByteVectorValues(fieldInfo, mergeState)
                writeField(fieldInfo, byteVectorValues, mergeState.segmentInfo.maxDoc())
            }

            else -> {
                throw UnsupportedOperationException()
            }
        }
    }

    /** Write the provided float vector field */
    @Throws(IOException::class)
    protected abstract fun writeField(
        fieldInfo: FieldInfo,
        floatVectorValues: FloatVectorValues,
        maxDoc: Int
    )

    /** Write the provided byte vector field */
    @Throws(IOException::class)
    protected abstract fun writeField(
        fieldInfo: FieldInfo,
        byteVectorValues: ByteVectorValues,
        maxDoc: Int
    )

    private abstract class FieldWriter<T>(val fieldInfo: FieldInfo) : KnnFieldVectorsWriter<T>() {
        val dim: Int = fieldInfo.vectorDimension
        val docsWithField: DocsWithFieldSet = DocsWithFieldSet()
        val vectors: MutableList<T> = ArrayList()
        private var lastDocID = -1

        override fun addValue(docID: Int, value: T) {
            if (docID == lastDocID) {
                throw IllegalArgumentException(
                    "VectorValuesField \"" +
                        fieldInfo.name +
                        "\" appears more than once in this document (only one value is allowed per field)"
                )
            }
            assert(docID > lastDocID)
            docsWithField.add(docID)
            vectors.add(copyValue(value))
            lastDocID = docID
        }

        abstract override fun copyValue(vectorValue: T): T

        override fun ramBytesUsed(): Long {
            if (vectors.isEmpty()) {
                return 0
            }
            return docsWithField.ramBytesUsed() +
                vectors.size.toLong() *
                (RamUsageEstimator.NUM_BYTES_OBJECT_REF + RamUsageEstimator.NUM_BYTES_ARRAY_HEADER) +
                vectors.size.toLong() * dim.toLong() * Float.SIZE_BYTES
        }
    }

    private class BufferedFloatVectorValues(
        val vectors: List<FloatArray>,
        val dimension: Int,
        private val docsWithField: DocIdSet
    ) : FloatVectorValues() {
        private val iterator: DocIndexIterator

        init {
            iterator = fromDISI(docsWithField.iterator())
        }

        override fun dimension(): Int {
            return dimension
        }

        override fun size(): Int {
            return vectors.size
        }

        override fun ordToDoc(ord: Int): Int {
            return ord
        }

        @Throws(IOException::class)
        override fun vectorValue(targetOrd: Int): FloatArray {
            return vectors[targetOrd]
        }

        override fun iterator(): DocIndexIterator {
            return iterator
        }

        @Throws(IOException::class)
        override fun copy(): BufferedFloatVectorValues {
            return BufferedFloatVectorValues(vectors, dimension, docsWithField)
        }
    }

    private class BufferedByteVectorValues(
        val vectors: List<ByteArray>,
        val dimension: Int,
        private val docsWithField: DocIdSet
    ) : ByteVectorValues() {
        private val iterator: DocIndexIterator

        init {
            iterator = fromDISI(docsWithField.iterator())
        }

        override fun dimension(): Int {
            return dimension
        }

        override fun size(): Int {
            return vectors.size
        }

        @Throws(IOException::class)
        override fun vectorValue(targetOrd: Int): ByteArray {
            return vectors[targetOrd]
        }

        override fun iterator(): DocIndexIterator {
            return iterator
        }

        @Throws(IOException::class)
        override fun copy(): BufferedByteVectorValues {
            return BufferedByteVectorValues(vectors, dimension, docsWithField)
        }
    }
}
