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
import org.gnit.lucenekmp.codecs.TermVectorsFormat
import org.gnit.lucenekmp.codecs.TermVectorsReader
import org.gnit.lucenekmp.codecs.TermVectorsWriter
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.FieldInfos
import org.gnit.lucenekmp.index.MergeState
import org.gnit.lucenekmp.index.SegmentInfo
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.BytesRef

class CrankyTermVectorsFormat(val delegate: TermVectorsFormat, val random: Random) :
    TermVectorsFormat() {

    @Throws(IOException::class)
    override fun vectorsReader(
        directory: Directory,
        segmentInfo: SegmentInfo,
        fieldInfos: FieldInfos?,
        context: IOContext
    ): TermVectorsReader {
        return delegate.vectorsReader(directory, segmentInfo, fieldInfos, context)
    }

    @Throws(IOException::class)
    override fun vectorsWriter(
        directory: Directory,
        segmentInfo: SegmentInfo,
        context: IOContext
    ): TermVectorsWriter {
        if (random.nextInt(100) == 0) {
            throw IOException("Fake IOException from TermVectorsFormat.vectorsWriter()")
        }
        return CrankyTermVectorsWriter(delegate.vectorsWriter(directory, segmentInfo, context), random)
    }

    class CrankyTermVectorsWriter(val delegate: TermVectorsWriter, val random: Random) :
        TermVectorsWriter() {

        @Throws(IOException::class)
        override fun merge(mergeState: MergeState): Int {
            if (random.nextInt(100) == 0) {
                throw IOException("Fake IOException from TermVectorsWriter.merge()")
            }
            return super.merge(mergeState)
        }

        @Throws(IOException::class)
        override fun finish(numDocs: Int) {
            if (random.nextInt(100) == 0) {
                throw IOException("Fake IOException from TermVectorsWriter.finish()")
            }
            delegate.finish(numDocs)
        }

        override fun close() {
            delegate.close()
            if (random.nextInt(100) == 0) {
                throw IOException("Fake IOException from TermVectorsWriter.close()")
            }
        }

        // per doc/field methods: lower probability since they are invoked so many times.
        @Throws(IOException::class)
        override fun startDocument(numVectorFields: Int) {
            if (random.nextInt(10000) == 0) {
                throw IOException("Fake IOException from TermVectorsWriter.startDocument()")
            }
            delegate.startDocument(numVectorFields)
        }

        @Throws(IOException::class)
        override fun finishDocument() {
            if (random.nextInt(10000) == 0) {
                throw IOException("Fake IOException from TermVectorsWriter.finishDocument()")
            }
            delegate.finishDocument()
        }

        @Throws(IOException::class)
        override fun startField(
            info: FieldInfo?,
            numTerms: Int,
            positions: Boolean,
            offsets: Boolean,
            payloads: Boolean
        ) {
            if (random.nextInt(10000) == 0) {
                throw IOException("Fake IOException from TermVectorsWriter.startField()")
            }
            delegate.startField(info, numTerms, positions, offsets, payloads)
        }

        @Throws(IOException::class)
        override fun finishField() {
            if (random.nextInt(10000) == 0) {
                throw IOException("Fake IOException from TermVectorsWriter.finishField()")
            }
            delegate.finishField()
        }

        @Throws(IOException::class)
        override fun startTerm(term: BytesRef?, freq: Int) {
            if (random.nextInt(10000) == 0) {
                throw IOException("Fake IOException from TermVectorsWriter.startTerm()")
            }
            delegate.startTerm(term, freq)
        }

        @Throws(IOException::class)
        override fun finishTerm() {
            if (random.nextInt(10000) == 0) {
                throw IOException("Fake IOException from TermVectorsWriter.finishTerm()")
            }
            delegate.finishTerm()
        }

        @Throws(IOException::class)
        override fun addPosition(
            position: Int,
            startOffset: Int,
            endOffset: Int,
            payload: BytesRef?
        ) {
            if (random.nextInt(10000) == 0) {
                throw IOException("Fake IOException from TermVectorsWriter.addPosition()")
            }
            delegate.addPosition(position, startOffset, endOffset, payload)
        }

        @Throws(IOException::class)
        override fun addProx(numProx: Int, positions: DataInput?, offsets: DataInput?) {
            if (random.nextInt(10000) == 0) {
                throw IOException("Fake IOException from TermVectorsWriter.addProx()")
            }
            super.addProx(numProx, positions, offsets)
        }

        override fun ramBytesUsed(): Long {
            return delegate.ramBytesUsed()
        }

        override val childResources: MutableCollection<Accountable>
            get() = mutableListOf(delegate)
    }
}
