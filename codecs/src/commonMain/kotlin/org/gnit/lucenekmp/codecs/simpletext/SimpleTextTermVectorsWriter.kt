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
import org.gnit.lucenekmp.codecs.TermVectorsWriter
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.IOUtils

/**
 * Writes plain-text term vectors.
 *
 * <p><b>FOR RECREATIONAL USE ONLY</b>
 *
 * @lucene.experimental
 */
class SimpleTextTermVectorsWriter(directory: Directory, segment: String, context: IOContext) :
    TermVectorsWriter() {

    private var out: IndexOutput? = null
    private var numDocsWritten = 0
    private val scratch = BytesRefBuilder()
    private var offsets = false
    private var positions = false
    private var payloads = false

    init {
        var success = false
        try {
            out =
                directory.createOutput(
                    IndexFileNames.segmentFileName(segment, "", VECTORS_EXTENSION),
                    context
                )
            success = true
        } finally {
            if (!success) {
                IOUtils.closeWhileHandlingException(this)
            }
        }
    }

    @Throws(IOException::class)
    override fun startDocument(numVectorFields: Int) {
        write(DOC)
        write(numDocsWritten.toString())
        newLine()

        write(NUMFIELDS)
        write(numVectorFields.toString())
        newLine()
        numDocsWritten++
    }

    @Throws(IOException::class)
    override fun startField(
        info: FieldInfo?,
        numTerms: Int,
        positions: Boolean,
        offsets: Boolean,
        payloads: Boolean
    ) {
        write(FIELD)
        write(info!!.number.toString())
        newLine()

        write(FIELDNAME)
        write(info.name)
        newLine()

        write(FIELDPOSITIONS)
        write(positions.toString())
        newLine()

        write(FIELDOFFSETS)
        write(offsets.toString())
        newLine()

        write(FIELDPAYLOADS)
        write(payloads.toString())
        newLine()

        write(FIELDTERMCOUNT)
        write(numTerms.toString())
        newLine()

        this.positions = positions
        this.offsets = offsets
        this.payloads = payloads
    }

    @Throws(IOException::class)
    override fun startTerm(term: BytesRef?, freq: Int) {
        write(TERMTEXT)
        write(term)
        newLine()

        write(TERMFREQ)
        write(freq.toString())
        newLine()
    }

    @Throws(IOException::class)
    override fun addPosition(
        position: Int,
        startOffset: Int,
        endOffset: Int,
        payload: BytesRef?
    ) {
        assert(positions || offsets)

        if (positions) {
            write(POSITION)
            write(position.toString())
            newLine()

            if (payloads) {
                write(PAYLOAD)
                if (payload != null) {
                    assert(payload.length > 0)
                    write(payload)
                }
                newLine()
            }
        }

        if (offsets) {
            write(STARTOFFSET)
            write(startOffset.toString())
            newLine()

            write(ENDOFFSET)
            write(endOffset.toString())
            newLine()
        }
    }

    @Throws(IOException::class)
    override fun finish(numDocs: Int) {
        if (numDocsWritten != numDocs) {
            throw RuntimeException(
                "mergeVectors produced an invalid result: mergedDocs is $numDocs but vec numDocs is $numDocsWritten file=$out; now aborting this merge to prevent index corruption"
            )
        }
        write(END)
        newLine()
        SimpleTextUtil.writeChecksum(out!!, scratch)
    }

    override fun close() {
        try {
            IOUtils.close(out)
        } finally {
            out = null
        }
    }

    @Throws(IOException::class)
    private fun write(s: String) {
        SimpleTextUtil.write(out!!, s, scratch)
    }

    @Throws(IOException::class)
    private fun write(bytes: BytesRef?) {
        SimpleTextUtil.write(out!!, bytes)
    }

    @Throws(IOException::class)
    private fun newLine() {
        SimpleTextUtil.writeNewline(out!!)
    }

    override fun ramBytesUsed(): Long {
        return scratch.get().bytes.size.toLong()
    }

    companion object {
        val END: BytesRef = BytesRef("END")
        val DOC: BytesRef = BytesRef("doc ")
        val NUMFIELDS: BytesRef = BytesRef("  numfields ")
        val FIELD: BytesRef = BytesRef("  field ")
        val FIELDNAME: BytesRef = BytesRef("    name ")
        val FIELDPOSITIONS: BytesRef = BytesRef("    positions ")
        val FIELDOFFSETS: BytesRef = BytesRef("    offsets   ")
        val FIELDPAYLOADS: BytesRef = BytesRef("    payloads  ")
        val FIELDTERMCOUNT: BytesRef = BytesRef("    numterms ")
        val TERMTEXT: BytesRef = BytesRef("    term ")
        val TERMFREQ: BytesRef = BytesRef("      freq ")
        val POSITION: BytesRef = BytesRef("      position ")
        val PAYLOAD: BytesRef = BytesRef("        payload ")
        val STARTOFFSET: BytesRef = BytesRef("        startoffset ")
        val ENDOFFSET: BytesRef = BytesRef("        endoffset ")

        const val VECTORS_EXTENSION = "tvc"
    }
}
