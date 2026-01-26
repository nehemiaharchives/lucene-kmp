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
import org.gnit.lucenekmp.codecs.StoredFieldsWriter
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.IOUtils

/**
 * Writes plain-text stored fields.
 *
 * <p><b>FOR RECREATIONAL USE ONLY</b>
 *
 * @lucene.experimental
 */
class SimpleTextStoredFieldsWriter(directory: Directory, segment: String, context: IOContext) :
    StoredFieldsWriter() {
    private var numDocsWritten = 0
    private var out: IndexOutput? = null

    private val scratch = BytesRefBuilder()

    init {
        var success = false
        try {
            out =
                directory.createOutput(
                    IndexFileNames.segmentFileName(segment, "", FIELDS_EXTENSION),
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
    override fun startDocument() {
        write(DOC)
        write(numDocsWritten.toString())
        newLine()

        numDocsWritten++
    }

    @Throws(IOException::class)
    override fun writeField(info: FieldInfo?, value: Int) {
        writeField(info)

        write(TYPE)
        write(TYPE_INT)
        newLine()

        write(VALUE)
        write(value.toString())
        newLine()
    }

    @Throws(IOException::class)
    override fun writeField(info: FieldInfo?, value: Long) {
        writeField(info)

        write(TYPE)
        write(TYPE_LONG)
        newLine()

        write(VALUE)
        write(value.toString())
        newLine()
    }

    @Throws(IOException::class)
    override fun writeField(info: FieldInfo?, value: Float) {
        writeField(info)

        write(TYPE)
        write(TYPE_FLOAT)
        newLine()

        write(VALUE)
        write(value.toString())
        newLine()
    }

    @Throws(IOException::class)
    override fun writeField(info: FieldInfo?, value: Double) {
        writeField(info)

        write(TYPE)
        write(TYPE_DOUBLE)
        newLine()

        write(VALUE)
        write(value.toString())
        newLine()
    }

    @Throws(IOException::class)
    override fun writeField(info: FieldInfo?, value: BytesRef) {
        writeField(info)

        write(TYPE)
        write(TYPE_BINARY)
        newLine()

        write(VALUE)
        write(value)
        newLine()
    }

    @Throws(IOException::class)
    override fun writeField(info: FieldInfo?, value: String) {
        writeField(info)

        write(TYPE)
        write(TYPE_STRING)
        newLine()

        write(VALUE)
        write(value)
        newLine()
    }

    @Throws(IOException::class)
    private fun writeField(info: FieldInfo?) {
        write(FIELD)
        write(info!!.number.toString())
        newLine()

        write(NAME)
        write(info.name)
        newLine()
    }

    @Throws(IOException::class)
    override fun finish(numDocs: Int) {
        if (numDocsWritten != numDocs) {
            throw RuntimeException(
                "mergeFields produced an invalid result: docCount is $numDocs but only saw $numDocsWritten file=$out; now aborting this merge to prevent index corruption"
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
    private fun write(bytes: BytesRef) {
        SimpleTextUtil.write(out!!, bytes)
    }

    @Throws(IOException::class)
    private fun newLine() {
        SimpleTextUtil.writeNewline(out!!)
    }

    override fun ramBytesUsed(): Long {
        return Int.SIZE_BYTES.toLong() // something > 0
    }

    companion object {
        const val FIELDS_EXTENSION = "fld"

        val TYPE_STRING: BytesRef = BytesRef("string")
        val TYPE_BINARY: BytesRef = BytesRef("binary")
        val TYPE_INT: BytesRef = BytesRef("int")
        val TYPE_LONG: BytesRef = BytesRef("long")
        val TYPE_FLOAT: BytesRef = BytesRef("float")
        val TYPE_DOUBLE: BytesRef = BytesRef("double")

        val END: BytesRef = BytesRef("END")
        val DOC: BytesRef = BytesRef("doc ")
        val FIELD: BytesRef = BytesRef("  field ")
        val NAME: BytesRef = BytesRef("    name ")
        val TYPE: BytesRef = BytesRef("    type ")
        val VALUE: BytesRef = BytesRef("    value ")
    }
}
