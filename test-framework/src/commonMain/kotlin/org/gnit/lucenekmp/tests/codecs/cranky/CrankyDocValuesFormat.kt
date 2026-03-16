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
package org.gnit.lucenekmp.tests.codecs.cranky

import kotlin.random.Random
import okio.IOException
import org.gnit.lucenekmp.codecs.DocValuesConsumer
import org.gnit.lucenekmp.codecs.DocValuesFormat
import org.gnit.lucenekmp.codecs.DocValuesProducer
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.SegmentWriteState

class CrankyDocValuesFormat(val delegate: DocValuesFormat, val random: Random) :
    DocValuesFormat(delegate.name) {

    @Throws(IOException::class)
    override fun fieldsConsumer(state: SegmentWriteState): DocValuesConsumer {
        if (random.nextInt(100) == 0) {
            throw IOException("Fake IOException from DocValuesFormat.fieldsConsumer()")
        }
        return CrankyDocValuesConsumer(delegate.fieldsConsumer(state), random)
    }

    @Throws(IOException::class)
    override fun fieldsProducer(state: SegmentReadState): DocValuesProducer {
        return delegate.fieldsProducer(state)
    }

    class CrankyDocValuesConsumer(val delegate: DocValuesConsumer, val random: Random) :
        DocValuesConsumer() {

        @Throws(IOException::class)
        override fun close() {
            delegate.close()
            if (random.nextInt(100) == 0) {
                throw IOException("Fake IOException from DocValuesConsumer.close()")
            }
        }

        @Throws(IOException::class)
        override fun addNumericField(field: FieldInfo, valuesProducer: DocValuesProducer) {
            if (random.nextInt(100) == 0) {
                throw IOException("Fake IOException from DocValuesConsumer.addNumericField()")
            }
            delegate.addNumericField(field, valuesProducer)
        }

        @Throws(IOException::class)
        override fun addBinaryField(field: FieldInfo, valuesProducer: DocValuesProducer) {
            if (random.nextInt(100) == 0) {
                throw IOException("Fake IOException from DocValuesConsumer.addBinaryField()")
            }
            delegate.addBinaryField(field, valuesProducer)
        }

        @Throws(IOException::class)
        override fun addSortedField(field: FieldInfo, valuesProducer: DocValuesProducer) {
            if (random.nextInt(100) == 0) {
                throw IOException("Fake IOException from DocValuesConsumer.addSortedField()")
            }
            delegate.addSortedField(field, valuesProducer)
        }

        @Throws(IOException::class)
        override fun addSortedNumericField(field: FieldInfo, valuesProducer: DocValuesProducer) {
            if (random.nextInt(100) == 0) {
                throw IOException("Fake IOException from DocValuesConsumer.addSortedNumericField()")
            }
            delegate.addSortedNumericField(field, valuesProducer)
        }

        @Throws(IOException::class)
        override fun addSortedSetField(field: FieldInfo, valuesProducer: DocValuesProducer) {
            if (random.nextInt(100) == 0) {
                throw IOException("Fake IOException from DocValuesConsumer.addSortedSetField()")
            }
            delegate.addSortedSetField(field, valuesProducer)
        }
    }
}
