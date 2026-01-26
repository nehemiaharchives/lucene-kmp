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
import org.gnit.lucenekmp.codecs.StoredFieldsFormat
import org.gnit.lucenekmp.codecs.StoredFieldsReader
import org.gnit.lucenekmp.codecs.StoredFieldsWriter
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.FieldInfos
import org.gnit.lucenekmp.index.SegmentInfo
import org.gnit.lucenekmp.index.StoredFieldDataInput
import org.gnit.lucenekmp.index.StoredFieldVisitor
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.BytesRef
import kotlinx.coroutines.Job

/** Just like the default stored fields format but with additional asserts. */
class AssertingStoredFieldsFormat : StoredFieldsFormat() {
    private val `in`: StoredFieldsFormat = TestUtil.getDefaultCodec().storedFieldsFormat()

    @Throws(IOException::class)
    override fun fieldsReader(
        directory: Directory,
        si: SegmentInfo,
        fn: FieldInfos?,
        context: IOContext
    ): StoredFieldsReader {
        return AssertingStoredFieldsReader(
            `in`.fieldsReader(directory, si, fn, context),
            si.maxDoc(),
            false
        )
    }

    @Throws(IOException::class)
    override fun fieldsWriter(directory: Directory, si: SegmentInfo, context: IOContext): StoredFieldsWriter {
        return AssertingStoredFieldsWriter(`in`.fieldsWriter(directory, si, context))
    }

    internal class AssertingStoredFieldsReader(
        private val `in`: StoredFieldsReader,
        private val maxDoc: Int,
        private val merging: Boolean
    ) : StoredFieldsReader() {
        private val creationThread: Job? = AssertingCodec.currentJob()

        @Throws(IOException::class)
        override fun close() {
            `in`.close()
            `in`.close() // close again
        }

        @Throws(IOException::class)
        override fun document(n: Int, visitor: StoredFieldVisitor) {
            AssertingCodec.assertThread("StoredFieldsReader", creationThread)
            assert(n in 0..<maxDoc)
            `in`.document(n, visitor)
        }

        override fun clone(): StoredFieldsReader {
            assert(!merging) { "Merge instances do not support cloning" }
            return AssertingStoredFieldsReader(`in`.clone(), maxDoc, false)
        }

        @Throws(IOException::class)
        override fun checkIntegrity() {
            `in`.checkIntegrity()
        }

        override val mergeInstance: StoredFieldsReader
            get() = AssertingStoredFieldsReader(`in`.mergeInstance, maxDoc, true)

        override fun toString(): String {
            return "${this::class.simpleName}($`in`)"
        }
    }

    enum class Status {
        UNDEFINED,
        STARTED,
        FINISHED
    }

    internal class AssertingStoredFieldsWriter(private val `in`: StoredFieldsWriter) : StoredFieldsWriter() {
        private var numWritten = 0
        private var docStatus = Status.UNDEFINED

        @Throws(IOException::class)
        override fun startDocument() {
            assert(docStatus != Status.STARTED)
            `in`.startDocument()
            numWritten++
            docStatus = Status.STARTED
        }

        @Throws(IOException::class)
        override fun finishDocument() {
            assert(docStatus == Status.STARTED)
            `in`.finishDocument()
            docStatus = Status.FINISHED
        }

        @Throws(IOException::class)
        override fun writeField(info: FieldInfo?, value: Int) {
            assert(docStatus == Status.STARTED)
            `in`.writeField(info, value)
        }

        @Throws(IOException::class)
        override fun writeField(info: FieldInfo?, value: Long) {
            assert(docStatus == Status.STARTED)
            `in`.writeField(info, value)
        }

        @Throws(IOException::class)
        override fun writeField(info: FieldInfo?, value: Float) {
            assert(docStatus == Status.STARTED)
            `in`.writeField(info, value)
        }

        @Throws(IOException::class)
        override fun writeField(info: FieldInfo?, value: Double) {
            assert(docStatus == Status.STARTED)
            `in`.writeField(info, value)
        }

        @Throws(IOException::class)
        override fun writeField(info: FieldInfo?, value: BytesRef) {
            assert(docStatus == Status.STARTED)
            `in`.writeField(info, value)
        }

        @Throws(IOException::class)
        override fun writeField(info: FieldInfo?, value: StoredFieldDataInput) {
            assert(docStatus == Status.STARTED)
            `in`.writeField(info, value)
        }

        @Throws(IOException::class)
        override fun writeField(info: FieldInfo?, value: String) {
            assert(docStatus == Status.STARTED)
            `in`.writeField(info, value)
        }

        @Throws(IOException::class)
        override fun finish(numDocs: Int) {
            assert(docStatus == if (numDocs > 0) Status.FINISHED else Status.UNDEFINED)
            `in`.finish(numDocs)
            assert(numDocs == numWritten)
        }

        override fun close() {
            `in`.close()
            `in`.close() // close again
        }

        override fun ramBytesUsed(): Long {
            return `in`.ramBytesUsed()
        }

        override val childResources: MutableCollection<Accountable>
            get() = `in`.childResources
    }
}
