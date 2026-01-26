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
import org.gnit.lucenekmp.codecs.NormsConsumer
import org.gnit.lucenekmp.codecs.NormsFormat
import org.gnit.lucenekmp.codecs.NormsProducer
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.SegmentWriteState
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.DocIdSetIterator.Companion.NO_MORE_DOCS
import org.gnit.lucenekmp.tests.index.AssertingLeafReader
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlinx.coroutines.Job

/** Just like the default but with additional asserts. */
class AssertingNormsFormat : NormsFormat() {
    private val `in`: NormsFormat = TestUtil.getDefaultCodec().normsFormat()

    @Throws(IOException::class)
    override fun normsConsumer(state: SegmentWriteState): NormsConsumer {
        val consumer = `in`.normsConsumer(state)
        return AssertingNormsConsumer(consumer, state.segmentInfo.maxDoc())
    }

    @Throws(IOException::class)
    override fun normsProducer(state: SegmentReadState): NormsProducer {
        assert(state.fieldInfos.hasNorms())
        val producer = `in`.normsProducer(state)
        return AssertingNormsProducer(producer, state.segmentInfo.maxDoc(), false)
    }

    internal class AssertingNormsConsumer(
        private val `in`: NormsConsumer,
        private val maxDoc: Int
    ) : NormsConsumer() {
        @Throws(IOException::class)
        override fun addNormsField(field: FieldInfo, valuesProducer: NormsProducer) {
            val values: NumericDocValues? = valuesProducer.getNorms(field)

            var lastDocID = -1
            while (true) {
                val docID = values!!.nextDoc()
                if (docID == NO_MORE_DOCS) {
                    break
                }
                assert(docID in 0..<maxDoc)
                assert(docID > lastDocID)
                lastDocID = docID
                values.longValue()
            }

            `in`.addNormsField(field, valuesProducer)
        }

        @Throws(IOException::class)
        override fun close() {
            `in`.close()
            `in`.close() // close again
        }
    }

    internal class AssertingNormsProducer(
        private val `in`: NormsProducer,
        private val maxDoc: Int,
        private val merging: Boolean
    ) : NormsProducer() {
        private val creationThread: Job? = AssertingCodec.currentJob()

        init {
            // do a few simple checks on init
            assert(toString() != null)
        }

        @Throws(IOException::class)
        override fun getNorms(field: FieldInfo): NumericDocValues {
            if (merging) {
                AssertingCodec.assertThread("NormsProducer", creationThread)
            }
            assert(field.hasNorms())
            val values: NumericDocValues? = `in`.getNorms(field)
            return AssertingLeafReader.AssertingNumericDocValues(values!!, maxDoc)
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

        override val mergeInstance: NormsProducer
            get() = AssertingNormsProducer(`in`.mergeInstance, maxDoc, true)

        override fun toString(): String {
            return "${this::class.simpleName}($`in`)"
        }
    }
}
