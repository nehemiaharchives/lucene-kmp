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
import org.gnit.lucenekmp.codecs.StoredFieldsFormat
import org.gnit.lucenekmp.codecs.StoredFieldsReader
import org.gnit.lucenekmp.codecs.StoredFieldsWriter
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.FieldInfos
import org.gnit.lucenekmp.index.MergeState
import org.gnit.lucenekmp.index.SegmentInfo
import org.gnit.lucenekmp.index.StoredFieldDataInput
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.BytesRef

class CrankyStoredFieldsFormat(val delegate: StoredFieldsFormat, val random: Random) :
    StoredFieldsFormat() {

    @Throws(IOException::class)
    override fun fieldsReader(
        directory: Directory,
        si: SegmentInfo,
        fn: FieldInfos?,
        context: IOContext
    ): StoredFieldsReader {
        return delegate.fieldsReader(directory, si, fn, context)
    }

    @Throws(IOException::class)
    override fun fieldsWriter(
        directory: Directory,
        si: SegmentInfo,
        context: IOContext
    ): StoredFieldsWriter {
        if (random.nextInt(100) == 0) {
            throw IOException("Fake IOException from StoredFieldsFormat.fieldsWriter()")
        }
        return CrankyStoredFieldsWriter(delegate.fieldsWriter(directory, si, context), random)
    }

    class CrankyStoredFieldsWriter(val delegate: StoredFieldsWriter, val random: Random) :
        StoredFieldsWriter() {

        @Throws(IOException::class)
        override fun finish(numDocs: Int) {
            if (random.nextInt(100) == 0) {
                throw IOException("Fake IOException from StoredFieldsWriter.finish()")
            }
            delegate.finish(numDocs)
        }

        @Throws(IOException::class)
        override fun merge(mergeState: MergeState): Int {
            if (random.nextInt(100) == 0) {
                throw IOException("Fake IOException from StoredFieldsWriter.merge()")
            }
            return super.merge(mergeState)
        }

        override fun close() {
            delegate.close()
            if (random.nextInt(1000) == 0) {
                throw IOException("Fake IOException from StoredFieldsWriter.close()")
            }
        }

        // per doc/field methods: lower probability since they are invoked so many times.
        @Throws(IOException::class)
        override fun startDocument() {
            if (random.nextInt(10000) == 0) {
                throw IOException("Fake IOException from StoredFieldsWriter.startDocument()")
            }
            delegate.startDocument()
        }

        @Throws(IOException::class)
        override fun finishDocument() {
            if (random.nextInt(10000) == 0) {
                throw IOException("Fake IOException from StoredFieldsWriter.finishDocument()")
            }
            delegate.finishDocument()
        }

        @Throws(IOException::class)
        override fun writeField(info: FieldInfo?, value: Int) {
            if (random.nextInt(10000) == 0) {
                throw IOException("Fake IOException from StoredFieldsWriter.writeField()")
            }
            delegate.writeField(info, value)
        }

        @Throws(IOException::class)
        override fun writeField(info: FieldInfo?, value: Long) {
            if (random.nextInt(10000) == 0) {
                throw IOException("Fake IOException from StoredFieldsWriter.writeField()")
            }
            delegate.writeField(info, value)
        }

        @Throws(IOException::class)
        override fun writeField(info: FieldInfo?, value: Float) {
            if (random.nextInt(10000) == 0) {
                throw IOException("Fake IOException from StoredFieldsWriter.writeField()")
            }
            delegate.writeField(info, value)
        }

        @Throws(IOException::class)
        override fun writeField(info: FieldInfo?, value: Double) {
            if (random.nextInt(10000) == 0) {
                throw IOException("Fake IOException from StoredFieldsWriter.writeField()")
            }
            delegate.writeField(info, value)
        }

        @Throws(IOException::class)
        override fun writeField(info: FieldInfo?, value: BytesRef) {
            if (random.nextInt(10000) == 0) {
                throw IOException("Fake IOException from StoredFieldsWriter.writeField()")
            }
            delegate.writeField(info, value)
        }

        @Throws(IOException::class)
        override fun writeField(info: FieldInfo?, value: StoredFieldDataInput) {
            if (random.nextInt(10000) == 0) {
                throw IOException("Fake IOException from StoredFieldsWriter.writeField()")
            }
            delegate.writeField(info, value)
        }

        @Throws(IOException::class)
        override fun writeField(info: FieldInfo?, value: String) {
            if (random.nextInt(10000) == 0) {
                throw IOException("Fake IOException from StoredFieldsWriter.writeField()")
            }
            delegate.writeField(info, value)
        }

        override fun ramBytesUsed(): Long {
            return delegate.ramBytesUsed()
        }

        override val childResources: MutableCollection<Accountable>
            get() = delegate.childResources
    }
}
