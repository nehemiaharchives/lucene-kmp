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
import org.gnit.lucenekmp.codecs.CompetitiveImpactAccumulator
import org.gnit.lucenekmp.codecs.FieldsConsumer
import org.gnit.lucenekmp.codecs.NormsProducer
import org.gnit.lucenekmp.index.FieldInfos
import org.gnit.lucenekmp.index.Fields
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.index.SegmentWriteState
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder

internal class SimpleTextFieldsWriter(writeState: SegmentWriteState) : FieldsConsumer() {

    private var out: IndexOutput?
    private val scratch = BytesRefBuilder()
    private val writeState: SegmentWriteState
    val segment: String

    /** for write skip data. */
    private var docCount = 0

    private val skipWriter: SimpleTextSkipWriter
    private val competitiveImpactAccumulator = CompetitiveImpactAccumulator()
    private var lastDocFilePointer = -1L

    init {
        val fileName =
            SimpleTextPostingsFormat.getPostingsFileName(
                writeState.segmentInfo.name,
                writeState.segmentSuffix
            )
        segment = writeState.segmentInfo.name
        out = writeState.directory.createOutput(fileName, writeState.context)
        this.writeState = writeState
        this.skipWriter = SimpleTextSkipWriter(writeState)
    }

    @Throws(IOException::class)
    override fun write(fields: Fields, norms: NormsProducer?) {
        write(writeState.fieldInfos!!, fields, norms)
    }

    @Throws(IOException::class)
    fun write(fieldInfos: FieldInfos, fields: Fields, normsProducer: NormsProducer?) {

        // for each field
        for (field in fields) {
            val terms = fields.terms(field) ?: run {
                // Annoyingly, this can happen!
                continue
            }
            val fieldInfo = fieldInfos.fieldInfo(field)!!

            var wroteField = false

            val hasPositions = terms.hasPositions()
            val hasFreqs = terms.hasFreqs()
            val hasPayloads = fieldInfo.hasPayloads()
            val hasOffsets = terms.hasOffsets()
            val fieldHasNorms = fieldInfo.hasNorms()

            var norms: NumericDocValues? = null
            if (fieldHasNorms && normsProducer != null) {
                norms = normsProducer.getNorms(fieldInfo)
            }

            var flags = 0
            if (hasPositions) {
                flags = PostingsEnum.POSITIONS.toInt()
                if (hasPayloads) {
                    flags = flags or PostingsEnum.PAYLOADS.toInt()
                }
                if (hasOffsets) {
                    flags = flags or PostingsEnum.OFFSETS.toInt()
                }
            } else {
                if (hasFreqs) {
                    flags = flags or PostingsEnum.FREQS.toInt()
                }
            }

            val termsEnum = terms.iterator()
            var postingsEnum: PostingsEnum? = null

            // for each term in field
            while (true) {
                val term = termsEnum.next() ?: break
                docCount = 0
                skipWriter.resetSkip()
                competitiveImpactAccumulator.clear()
                lastDocFilePointer = -1L

                postingsEnum = termsEnum.postings(postingsEnum, flags)

                /*requireNotNull(postingsEnum) {
                    "termsEnum=$termsEnum hasPos=$hasPositions flags=$flags"
                }*/

                var wroteTerm = false

                // for each doc in field+term
                while (true) {
                    val doc = postingsEnum!!.nextDoc()
                    if (doc == DocIdSetIterator.NO_MORE_DOCS) {
                        break
                    }

                    if (!wroteTerm) {

                        if (!wroteField) {
                            // we lazily do this, in case the field had
                            // no terms
                            write(FIELD)
                            write(field)
                            newline()
                            wroteField = true
                        }

                        // we lazily do this, in case the term had
                        // zero docs
                        write(TERM)
                        write(term)
                        newline()
                        wroteTerm = true
                    }
                    if (lastDocFilePointer == -1L) {
                        lastDocFilePointer = out!!.filePointer
                    }
                    write(DOC)
                    write(doc.toString())
                    newline()
                    if (hasFreqs) {
                        val freq = postingsEnum.freq()
                        write(FREQ)
                        write(freq.toString())
                        newline()

                        if (hasPositions) {
                            // for assert:
                            var lastStartOffset = 0

                            // for each pos in field+term+doc
                            for (i in 0 until freq) {
                                val position = postingsEnum.nextPosition()

                                write(POS)
                                write(position.toString())
                                newline()

                                if (hasOffsets) {
                                    val startOffset = postingsEnum.startOffset()
                                    val endOffset = postingsEnum.endOffset()
                                    assert(endOffset >= startOffset)
                                    assert(startOffset >= lastStartOffset) {
                                        "startOffset=$startOffset lastStartOffset=$lastStartOffset"
                                    }
                                    lastStartOffset = startOffset
                                    write(START_OFFSET)
                                    write(startOffset.toString())
                                    newline()
                                    write(END_OFFSET)
                                    write(endOffset.toString())
                                    newline()
                                }

                                val payload = postingsEnum.payload

                                if (payload != null && payload.length > 0) {
                                    assert(payload.length != 0)
                                    write(PAYLOAD)
                                    write(payload)
                                    newline()
                                }
                            }
                        }
                        competitiveImpactAccumulator.add(freq, getNorm(doc, norms))
                    } else {
                        competitiveImpactAccumulator.add(1, getNorm(doc, norms))
                    }
                    docCount++
                    if (docCount != 0 && docCount % SimpleTextSkipWriter.BLOCK_SIZE == 0) {
                        skipWriter.bufferSkip(
                            doc,
                            lastDocFilePointer,
                            docCount,
                            competitiveImpactAccumulator
                        )
                        competitiveImpactAccumulator.clear()
                        lastDocFilePointer = -1L
                    }
                }
                if (docCount >= SimpleTextSkipWriter.BLOCK_SIZE) {
                    skipWriter.writeSkip(out!!)
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun write(s: String) {
        SimpleTextUtil.write(out!!, s, scratch)
    }

    @Throws(IOException::class)
    private fun write(b: BytesRef) {
        SimpleTextUtil.write(out!!, b)
    }

    @Throws(IOException::class)
    private fun newline() {
        SimpleTextUtil.writeNewline(out!!)
    }

    override fun close() {
        if (out != null) {
            try {
                write(END)
                newline()
                SimpleTextUtil.writeChecksum(out!!, scratch)
            } finally {
                out!!.close()
                out = null
            }
        }
    }

    @Throws(IOException::class)
    private fun getNorm(doc: Int, norms: NumericDocValues?): Long {
        if (norms == null) {
            return 1L
        }
        val found = norms.advanceExact(doc)
        if (found == false) {
            return 1L
        }
        return norms.longValue()
    }

    companion object {
        val END: BytesRef = BytesRef("END")
        val FIELD: BytesRef = BytesRef("field ")
        val TERM: BytesRef = BytesRef("  term ")
        val DOC: BytesRef = BytesRef("    doc ")
        val FREQ: BytesRef = BytesRef("      freq ")
        val POS: BytesRef = BytesRef("      pos ")
        val START_OFFSET: BytesRef = BytesRef("      startOffset ")
        val END_OFFSET: BytesRef = BytesRef("      endOffset ")
        val PAYLOAD: BytesRef = BytesRef("        payload ")
    }
}
