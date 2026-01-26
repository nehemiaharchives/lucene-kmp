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
import org.gnit.lucenekmp.codecs.DocValuesConsumer
import org.gnit.lucenekmp.codecs.DocValuesFormat
import org.gnit.lucenekmp.codecs.DocValuesProducer
import org.gnit.lucenekmp.index.BinaryDocValues
import org.gnit.lucenekmp.index.DocValuesSkipIndexType
import org.gnit.lucenekmp.index.DocValuesSkipper
import org.gnit.lucenekmp.index.DocValuesType
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.FieldInfos
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.SegmentWriteState
import org.gnit.lucenekmp.index.SortedDocValues
import org.gnit.lucenekmp.index.SortedNumericDocValues
import org.gnit.lucenekmp.index.SortedSetDocValues
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.DocIdSetIterator.Companion.NO_MORE_DOCS
import org.gnit.lucenekmp.tests.index.AssertingLeafReader
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.LongBitSet
import kotlinx.coroutines.Job

/** Just like the default but with additional asserts. */
class AssertingDocValuesFormat : DocValuesFormat("Asserting") {
    private val `in`: DocValuesFormat = TestUtil.getDefaultDocValuesFormat()

    @Throws(IOException::class)
    override fun fieldsConsumer(state: SegmentWriteState): DocValuesConsumer {
        val consumer = `in`.fieldsConsumer(state)
        return AssertingDocValuesConsumer(consumer, state.segmentInfo.maxDoc())
    }

    @Throws(IOException::class)
    override fun fieldsProducer(state: SegmentReadState): DocValuesProducer {
        assert(state.fieldInfos.hasDocValues())
        val producer = `in`.fieldsProducer(state)
        return AssertingDocValuesProducer(producer, state.fieldInfos, state.segmentInfo.maxDoc(), false)
    }

    internal class AssertingDocValuesConsumer(
        private val `in`: DocValuesConsumer,
        private val maxDoc: Int
    ) : DocValuesConsumer() {
        @Throws(IOException::class)
        override fun addNumericField(field: FieldInfo, valuesProducer: DocValuesProducer) {
            val values: NumericDocValues = requireNotNull(valuesProducer.getNumeric(field))

            var lastDocID = -1
            while (true) {
                val docID = values.nextDoc()
                if (docID == NO_MORE_DOCS) {
                    break
                }
                assert(docID in 0..<maxDoc)
                assert(docID > lastDocID)
                lastDocID = docID
                values.longValue()
            }

            `in`.addNumericField(field, valuesProducer)
        }

        @Throws(IOException::class)
        override fun addBinaryField(field: FieldInfo, valuesProducer: DocValuesProducer) {
            val values: BinaryDocValues = requireNotNull(valuesProducer.getBinary(field))

            var lastDocID = -1
            while (true) {
                val docID = values.nextDoc()
                if (docID == NO_MORE_DOCS) {
                    break
                }
                assert(docID in 0..<maxDoc)
                assert(docID > lastDocID)
                lastDocID = docID
                val value: BytesRef = values.binaryValue()!!
                assert(value.isValid())
            }

            `in`.addBinaryField(field, valuesProducer)
        }

        @Throws(IOException::class)
        override fun addSortedField(field: FieldInfo, valuesProducer: DocValuesProducer) {
            val values: SortedDocValues = requireNotNull(valuesProducer.getSorted(field))

            val valueCount = values.valueCount
            assert(valueCount <= maxDoc)
            var lastValue: BytesRef? = null
            for (ord in 0 until valueCount) {
                val b: BytesRef = requireNotNull(values.lookupOrd(ord))
                assert(b.isValid())
                if (ord > 0) {
                    assert(b.compareTo(lastValue!!) > 0)
                }
                lastValue = BytesRef.deepCopyOf(b)
            }

            val seenOrds = FixedBitSet(valueCount)

            var lastDocID = -1
            while (true) {
                val docID = values.nextDoc()
                if (docID == NO_MORE_DOCS) {
                    break
                }
                assert(docID in 0..<maxDoc)
                assert(docID > lastDocID)
                lastDocID = docID
                val ord = values.ordValue()
                assert(ord in 0..<valueCount)
                seenOrds.set(ord)
            }

            assert(seenOrds.cardinality() == valueCount)
            `in`.addSortedField(field, valuesProducer)
        }

        @Throws(IOException::class)
        override fun addSortedNumericField(field: FieldInfo, valuesProducer: DocValuesProducer) {
            val values: SortedNumericDocValues = requireNotNull(valuesProducer.getSortedNumeric(field))

            var lastDocID = -1
            while (true) {
                val docID = values.nextDoc()
                if (docID == NO_MORE_DOCS) {
                    break
                }
                assert(values.docID() > lastDocID)
                lastDocID = values.docID()
                val count = values.docValueCount()
                assert(count > 0)
                var previous = Long.MIN_VALUE
                for (i in 0 until count) {
                    val nextValue = values.nextValue()
                    assert(nextValue >= previous)
                    previous = nextValue
                }
            }
            `in`.addSortedNumericField(field, valuesProducer)
        }

        @Throws(IOException::class)
        override fun addSortedSetField(field: FieldInfo, valuesProducer: DocValuesProducer) {
            val values: SortedSetDocValues = requireNotNull(valuesProducer.getSortedSet(field))

            val valueCount = values.valueCount
            var lastValue: BytesRef? = null
            var ord = 0L
            while (ord < valueCount) {
                val b: BytesRef = requireNotNull(values.lookupOrd(ord))
                assert(b.isValid())
                if (ord > 0) {
                    assert(b > lastValue!!)
                }
                lastValue = BytesRef.deepCopyOf(b)
                ord++
            }

            val seenOrds = LongBitSet(valueCount)
            while (true) {
                val docID = values.nextDoc()
                if (docID == NO_MORE_DOCS) {
                    break
                }

                var lastOrd = -1L
                for (i in 0 until values.docValueCount()) {
                    val nextOrd = values.nextOrd()
                    assert(nextOrd in 0..<valueCount) {
                        "ord=$nextOrd is not in bounds 0 ..${valueCount - 1}"
                    }
                    assert(nextOrd > lastOrd) { "ord=$nextOrd,lastOrd=$lastOrd" }
                    seenOrds.set(nextOrd)
                    lastOrd = nextOrd
                }
            }

            assert(seenOrds.cardinality() == valueCount)
            `in`.addSortedSetField(field, valuesProducer)
        }

        @Throws(IOException::class)
        override fun close() {
            `in`.close()
            `in`.close() // close again
        }
    }

    internal class AssertingDocValuesProducer(
        private val `in`: DocValuesProducer,
        private val fieldInfos: FieldInfos,
        private val maxDoc: Int,
        private val merging: Boolean
    ) : DocValuesProducer() {
        private val creationThread: Job? = AssertingCodec.currentJob()

        @Throws(IOException::class)
        override fun getNumeric(field: FieldInfo): NumericDocValues {
            assert(fieldInfos.fieldInfo(field.name)?.number == field.number)
            if (merging) {
                AssertingCodec.assertThread("DocValuesProducer", creationThread)
            }
            assert(field.docValuesType == DocValuesType.NUMERIC)
            val values: NumericDocValues = requireNotNull(`in`.getNumeric(field))
            return AssertingLeafReader.AssertingNumericDocValues(values, maxDoc)
        }

        @Throws(IOException::class)
        override fun getBinary(field: FieldInfo): BinaryDocValues {
            assert(fieldInfos.fieldInfo(field.name)?.number == field.number)
            if (merging) {
                AssertingCodec.assertThread("DocValuesProducer", creationThread)
            }
            assert(field.docValuesType == DocValuesType.BINARY)
            val values: BinaryDocValues = requireNotNull(`in`.getBinary(field))
            return AssertingLeafReader.AssertingBinaryDocValues(values, maxDoc)
        }

        @Throws(IOException::class)
        override fun getSorted(field: FieldInfo): SortedDocValues {
            assert(fieldInfos.fieldInfo(field.name)?.number == field.number)
            if (merging) {
                AssertingCodec.assertThread("DocValuesProducer", creationThread)
            }
            assert(field.docValuesType == DocValuesType.SORTED)
            val values: SortedDocValues = requireNotNull(`in`.getSorted(field))
            return AssertingLeafReader.AssertingSortedDocValues(values, maxDoc)
        }

        @Throws(IOException::class)
        override fun getSortedNumeric(field: FieldInfo): SortedNumericDocValues {
            assert(fieldInfos.fieldInfo(field.name)?.number == field.number)
            if (merging) {
                AssertingCodec.assertThread("DocValuesProducer", creationThread)
            }
            assert(field.docValuesType == DocValuesType.SORTED_NUMERIC)
            val values: SortedNumericDocValues = requireNotNull(`in`.getSortedNumeric(field))
            return AssertingLeafReader.AssertingSortedNumericDocValues.create(values, maxDoc)
        }

        @Throws(IOException::class)
        override fun getSortedSet(field: FieldInfo): SortedSetDocValues {
            assert(fieldInfos.fieldInfo(field.name)?.number == field.number)
            if (merging) {
                AssertingCodec.assertThread("DocValuesProducer", creationThread)
            }
            assert(field.docValuesType == DocValuesType.SORTED_SET)
            val values: SortedSetDocValues = requireNotNull(`in`.getSortedSet(field))
            return AssertingLeafReader.AssertingSortedSetDocValues.create(values, maxDoc)
        }

        @Throws(IOException::class)
        override fun getSkipper(field: FieldInfo): DocValuesSkipper {
            assert(fieldInfos.fieldInfo(field.name)?.number == field.number)
            assert(field.docValuesSkipIndexType() != DocValuesSkipIndexType.NONE)
            val skipper: DocValuesSkipper = requireNotNull(`in`.getSkipper(field))
            return AssertingLeafReader.AssertingDocValuesSkipper(skipper)
        }

        @Throws(IOException::class)
        override fun close() {
            `in`.close()
            `in`.close() // close again
        }

        @Throws(IOException::class)
        override fun checkIntegrity() {
            `in`.checkIntegrity()
        }

        override val mergeInstance: DocValuesProducer
            get() = AssertingDocValuesProducer(`in`.mergeInstance, fieldInfos, maxDoc, true)

        override fun toString(): String {
            return "${this::class.simpleName}($`in`)"
        }
    }
}
