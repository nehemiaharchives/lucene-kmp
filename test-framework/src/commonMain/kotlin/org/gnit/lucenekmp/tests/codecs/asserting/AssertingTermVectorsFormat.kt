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
import org.gnit.lucenekmp.codecs.TermVectorsFormat
import org.gnit.lucenekmp.codecs.TermVectorsReader
import org.gnit.lucenekmp.codecs.TermVectorsWriter
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.FieldInfos
import org.gnit.lucenekmp.index.Fields
import org.gnit.lucenekmp.index.SegmentInfo
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.tests.index.AssertingLeafReader
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.BytesRef

/** Just like the default vectors format but with additional asserts. */
class AssertingTermVectorsFormat : TermVectorsFormat() {
    private val `in`: TermVectorsFormat = TestUtil.getDefaultCodec().termVectorsFormat()

    @Throws(IOException::class)
    override fun vectorsReader(
        directory: Directory,
        segmentInfo: SegmentInfo,
        fieldInfos: FieldInfos?,
        context: IOContext
    ): TermVectorsReader {
        return AssertingTermVectorsReader(
            `in`.vectorsReader(directory, segmentInfo, fieldInfos, context)
        )
    }

    @Throws(IOException::class)
    override fun vectorsWriter(directory: Directory, segmentInfo: SegmentInfo, context: IOContext): TermVectorsWriter {
        return AssertingTermVectorsWriter(`in`.vectorsWriter(directory, segmentInfo, context))
    }

    internal class AssertingTermVectorsReader(private val `in`: TermVectorsReader) : TermVectorsReader() {

        @Throws(IOException::class)
        override fun close() {
            `in`.close()
            `in`.close() // close again
        }

        @Throws(IOException::class)
        override fun get(doc: Int): Fields? {
            val fields = `in`.get(doc)
            return if (fields == null) null else AssertingLeafReader.AssertingFields(fields)
        }

        override fun clone(): TermVectorsReader {
            return AssertingTermVectorsReader(`in`.clone())
        }

        @Throws(IOException::class)
        override fun checkIntegrity() {
            `in`.checkIntegrity()
        }

        override val mergeInstance: TermVectorsReader
            get() = AssertingTermVectorsReader(`in`.mergeInstance)

        override fun toString(): String {
            return "${this::class.simpleName}($`in`)"
        }
    }

    enum class Status {
        UNDEFINED,
        STARTED,
        FINISHED
    }

    internal class AssertingTermVectorsWriter(private val `in`: TermVectorsWriter) : TermVectorsWriter() {
        private var docStatus: Status = Status.UNDEFINED
        private var fieldStatus: Status = Status.UNDEFINED
        private var termStatus: Status = Status.UNDEFINED
        private var docCount = 0
        private var fieldCount = 0
        private var termCount = 0
        private var positionCount = 0
        private var hasPositions = false

        @Throws(IOException::class)
        override fun startDocument(numVectorFields: Int) {
            assert(fieldCount == 0)
            assert(docStatus != Status.STARTED)
            `in`.startDocument(numVectorFields)
            docStatus = Status.STARTED
            fieldCount = numVectorFields
            docCount++
        }

        @Throws(IOException::class)
        override fun finishDocument() {
            assert(fieldCount == 0)
            assert(docStatus == Status.STARTED)
            `in`.finishDocument()
            docStatus = Status.FINISHED
        }

        @Throws(IOException::class)
        override fun startField(
            info: FieldInfo?,
            numTerms: Int,
            positions: Boolean,
            offsets: Boolean,
            payloads: Boolean
        ) {
            assert(termCount == 0)
            assert(docStatus == Status.STARTED)
            assert(fieldStatus != Status.STARTED)
            `in`.startField(info, numTerms, positions, offsets, payloads)
            fieldStatus = Status.STARTED
            termCount = numTerms
            hasPositions = positions || offsets || payloads
        }

        @Throws(IOException::class)
        override fun finishField() {
            assert(termCount == 0)
            assert(fieldStatus == Status.STARTED)
            `in`.finishField()
            fieldStatus = Status.FINISHED
            fieldCount--
        }

        @Throws(IOException::class)
        override fun startTerm(term: BytesRef?, freq: Int) {
            assert(docStatus == Status.STARTED)
            assert(fieldStatus == Status.STARTED)
            assert(termStatus != Status.STARTED)
            `in`.startTerm(term, freq)
            termStatus = Status.STARTED
            positionCount = if (hasPositions) freq else 0
        }

        @Throws(IOException::class)
        override fun finishTerm() {
            assert(positionCount == 0)
            assert(docStatus == Status.STARTED)
            assert(fieldStatus == Status.STARTED)
            assert(termStatus == Status.STARTED)
            `in`.finishTerm()
            termStatus = Status.FINISHED
            termCount--
        }

        @Throws(IOException::class)
        override fun addPosition(position: Int, startOffset: Int, endOffset: Int, payload: BytesRef?) {
            assert(docStatus == Status.STARTED)
            assert(fieldStatus == Status.STARTED)
            assert(termStatus == Status.STARTED)
            `in`.addPosition(position, startOffset, endOffset, payload)
            positionCount--
        }

        @Throws(IOException::class)
        override fun finish(numDocs: Int) {
            assert(docCount == numDocs)
            assert(docStatus == if (numDocs > 0) Status.FINISHED else Status.UNDEFINED)
            assert(fieldStatus != Status.STARTED)
            assert(termStatus != Status.STARTED)
            `in`.finish(numDocs)
        }

        override fun close() {
            `in`.close()
            `in`.close() // close again
        }

        override fun ramBytesUsed(): Long {
            return `in`.ramBytesUsed()
        }

        override val childResources: MutableCollection<Accountable>
            get() = mutableListOf(`in`)
    }
}
