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

package org.gnit.lucenekmp.tests.codecs.asserting

import okio.IOException
import org.gnit.lucenekmp.codecs.KnnFieldVectorsWriter
import org.gnit.lucenekmp.codecs.KnnVectorsFormat
import org.gnit.lucenekmp.codecs.KnnVectorsReader
import org.gnit.lucenekmp.codecs.KnnVectorsWriter
import org.gnit.lucenekmp.codecs.hnsw.HnswGraphProvider
import org.gnit.lucenekmp.index.ByteVectorValues
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.FieldInfos
import org.gnit.lucenekmp.index.FloatVectorValues
import org.gnit.lucenekmp.index.MergeState
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.SegmentWriteState
import org.gnit.lucenekmp.index.Sorter
import org.gnit.lucenekmp.index.VectorEncoding
import org.gnit.lucenekmp.jdkport.AtomicInteger
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.get
import org.gnit.lucenekmp.jdkport.incrementAndGet
import org.gnit.lucenekmp.search.KnnCollector
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.hnsw.HnswGraph
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

/** Wraps the default KnnVectorsFormat and provides additional assertions. */
class AssertingKnnVectorsFormat : KnnVectorsFormat("Asserting") {

    private val delegate: KnnVectorsFormat = TestUtil.getDefaultKnnVectorsFormat()

    @Throws(IOException::class)
    override fun fieldsWriter(state: SegmentWriteState): KnnVectorsWriter {
        return AssertingKnnVectorsWriter(delegate.fieldsWriter(state))
    }

    @Throws(IOException::class)
    override fun fieldsReader(state: SegmentReadState): KnnVectorsReader {
        return AssertingKnnVectorsReader(delegate.fieldsReader(state), state.fieldInfos)
    }

    override fun getMaxDimensions(fieldName: String): Int {
        return DEFAULT_MAX_DIMENSIONS
    }

    override fun toString(): String {
        return "AssertingKnnVectorsFormat{delegate=$delegate}"
    }

    class AssertingKnnVectorsWriter internal constructor(
        val delegate: KnnVectorsWriter
    ) : KnnVectorsWriter() {
        @Throws(IOException::class)
        override fun addField(fieldInfo: FieldInfo): KnnFieldVectorsWriter<*> {
            return delegate.addField(fieldInfo)
        }

        @Throws(IOException::class)
        override fun flush(maxDoc: Int, sortMap: Sorter.DocMap?) {
            delegate.flush(maxDoc, sortMap)
        }

        @Throws(IOException::class)
        override fun mergeOneField(fieldInfo: FieldInfo, mergeState: MergeState) {
            delegate.mergeOneField(fieldInfo, mergeState)
        }

        @Throws(IOException::class)
        override fun finish() {
            delegate.finish()
        }

        @Throws(IOException::class)
        override fun close() {
            delegate.close()
        }

        override fun ramBytesUsed(): Long {
            return delegate.ramBytesUsed()
        }
    }

    open class AssertingKnnVectorsReader internal constructor(
        protected val delegate: KnnVectorsReader,
        protected val fis: FieldInfos,
        protected val isMergeInstance: Boolean
    ) : KnnVectorsReader(), HnswGraphProvider {
        @OptIn(ExperimentalAtomicApi::class)
        val mergeInstanceCount: AtomicInteger = AtomicInteger(0)
        @OptIn(ExperimentalAtomicApi::class)
        val finishMergeCount: AtomicInteger = AtomicInteger(0)

        constructor(delegate: KnnVectorsReader, fis: FieldInfos) : this(delegate, fis, false)

        @Throws(IOException::class)
        override fun checkIntegrity() {
            delegate.checkIntegrity()
        }

        @Throws(IOException::class)
        override fun getFloatVectorValues(field: String): FloatVectorValues? {
            val fi = fis.fieldInfo(field)
            assert(
                fi != null && fi.vectorDimension > 0 && fi.vectorEncoding == VectorEncoding.FLOAT32
            )
            val floatValues = requireNotNull(delegate.getFloatVectorValues(field))
            assert(floatValues.iterator().docID() == -1)
            assert(floatValues.size() >= 0)
            assert(floatValues.dimension() > 0)
            return floatValues
        }

        @Throws(IOException::class)
        override fun getByteVectorValues(field: String): ByteVectorValues? {
            val fi = fis.fieldInfo(field)
            assert(
                fi != null && fi.vectorDimension > 0 && fi.vectorEncoding == VectorEncoding.BYTE
            )
            val values = requireNotNull(delegate.getByteVectorValues(field))
            assert(values.iterator().docID() == -1)
            assert(values.size() >= 0)
            assert(values.dimension() > 0)
            return values
        }

        @Throws(IOException::class)
        override fun search(field: String, target: FloatArray, knnCollector: KnnCollector, acceptDocs: Bits?) {
            assert(!isMergeInstance)
            val fi = fis.fieldInfo(field)
            assert(
                fi != null && fi.vectorDimension > 0 && fi.vectorEncoding == VectorEncoding.FLOAT32
            )
            delegate.search(field, target, knnCollector, acceptDocs)
        }

        @Throws(IOException::class)
        override fun search(field: String, target: ByteArray, knnCollector: KnnCollector, acceptDocs: Bits?) {
            assert(!isMergeInstance)
            val fi = fis.fieldInfo(field)
            assert(
                fi != null && fi.vectorDimension > 0 && fi.vectorEncoding == VectorEncoding.BYTE
            )
            delegate.search(field, target, knnCollector, acceptDocs)
        }

        @OptIn(ExperimentalAtomicApi::class)
        override val mergeInstance: KnnVectorsReader
            get() {
                assert(!isMergeInstance)
                val mergeVectorsReader = delegate.mergeInstance
                val nonNullMergeVectorsReader = requireNotNull(mergeVectorsReader)
                mergeInstanceCount.incrementAndFetch()

                val parent = this
                return object : AssertingKnnVectorsReader(nonNullMergeVectorsReader, parent.fis, true) {
                    override val mergeInstance: KnnVectorsReader
                        get() {
                            assert(false) // merging from a merge instance it not allowed
                            return this
                        }

                    @Throws(IOException::class)
                    override fun finishMerge() {
                        assert(isMergeInstance)
                        delegate.finishMerge()
                        parent.finishMergeCount.incrementAndFetch()
                    }

                    override fun close() {
                        assert(false) // closing the merge instance it not allowed
                    }
                }
            }

        @OptIn(ExperimentalAtomicApi::class)
        @Throws(IOException::class)
        override fun finishMerge() {
            assert(isMergeInstance)
            delegate.finishMerge()
            finishMergeCount.incrementAndFetch()
        }

        @OptIn(ExperimentalAtomicApi::class)
        @Throws(IOException::class)
        override fun close() {
            assert(!isMergeInstance)
            delegate.close()
            delegate.close()
            assert(finishMergeCount.get() <= 0 || mergeInstanceCount.get() == finishMergeCount.get())
        }

        @Throws(IOException::class)
        override fun getGraph(field: String): HnswGraph? {
            return if (delegate is HnswGraphProvider) {
                delegate.getGraph(field)
            } else {
                null
            }
        }
    }
}
