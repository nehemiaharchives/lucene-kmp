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
import org.gnit.lucenekmp.codecs.StoredFieldsReader
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.FieldInfos
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.SegmentInfo
import org.gnit.lucenekmp.index.StoredFieldVisitor
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.store.AlreadyClosedException
import org.gnit.lucenekmp.store.BufferedChecksumIndexInput
import org.gnit.lucenekmp.store.ChecksumIndexInput
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.CharsRefBuilder
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.StringHelper

/**
 * reads plaintext stored fields
 *
 * <p><b>FOR RECREATIONAL USE ONLY</b>
 *
 * @lucene.experimental
 */
class SimpleTextStoredFieldsReader : StoredFieldsReader {

    private var offsets: LongArray? = null /* docid -> offset in .fld file */
    private var `in`: IndexInput? = null
    private val scratch = BytesRefBuilder()
    private val scratchUTF16 = CharsRefBuilder()
    private var fieldInfos: FieldInfos?

    constructor(directory: Directory, si: SegmentInfo, fn: FieldInfos?, context: IOContext) :
        super() {
        fieldInfos = fn
        var success = false
        try {
            `in` =
                directory.openInput(
                    IndexFileNames.segmentFileName(
                        si.name,
                        "",
                        SimpleTextStoredFieldsWriter.FIELDS_EXTENSION
                    ),
                    context
                )
            success = true
        } finally {
            if (!success) {
                try {
                    close()
                } catch (_: Throwable) {
                    // ensure we throw our original exception
                }
            }
        }
        readIndex(si.maxDoc())
    }

    // used by clone
    private constructor(offsets: LongArray?, `in`: IndexInput?, fieldInfos: FieldInfos) : super() {
        this.offsets = offsets
        this.`in` = `in`
        this.fieldInfos = fieldInfos
    }

    // we don't actually write a .fdx-like index, instead we read the
    // stored fields file in entirety up-front and save the offsets
    // so we can seek to the documents later.
    @Throws(IOException::class)
    private fun readIndex(size: Int) {
        val input: ChecksumIndexInput = BufferedChecksumIndexInput(`in`!!)
        offsets = LongArray(size)
        var upto = 0
        while (scratch.get() != SimpleTextStoredFieldsWriter.END) {
            SimpleTextUtil.readLine(input, scratch)
            if (StringHelper.startsWith(scratch.get(), SimpleTextStoredFieldsWriter.DOC)) {
                offsets!![upto] = input.filePointer
                upto++
            }
        }
        SimpleTextUtil.checkFooter(input)
        assert(upto == offsets!!.size)
    }

    @Throws(IOException::class)
    override fun document(n: Int, visitor: StoredFieldVisitor) {
        `in`!!.seek(offsets!![n])

        while (true) {
            readLine()
            if (!StringHelper.startsWith(scratch.get(), SimpleTextStoredFieldsWriter.FIELD)) {
                break
            }
            val fieldNumber = parseIntAt(SimpleTextStoredFieldsWriter.FIELD.length)
            val fieldInfo = fieldInfos!!.fieldInfo(fieldNumber)
            readLine()
            assert(StringHelper.startsWith(scratch.get(), SimpleTextStoredFieldsWriter.NAME))
            readLine()
            assert(StringHelper.startsWith(scratch.get(), SimpleTextStoredFieldsWriter.TYPE))

            val type: BytesRef = when {
                equalsAt(
                    SimpleTextStoredFieldsWriter.TYPE_STRING,
                    scratch.get(),
                    SimpleTextStoredFieldsWriter.TYPE.length
                ) -> SimpleTextStoredFieldsWriter.TYPE_STRING
                equalsAt(
                    SimpleTextStoredFieldsWriter.TYPE_BINARY,
                    scratch.get(),
                    SimpleTextStoredFieldsWriter.TYPE.length
                ) -> SimpleTextStoredFieldsWriter.TYPE_BINARY
                equalsAt(
                    SimpleTextStoredFieldsWriter.TYPE_INT,
                    scratch.get(),
                    SimpleTextStoredFieldsWriter.TYPE.length
                ) -> SimpleTextStoredFieldsWriter.TYPE_INT
                equalsAt(
                    SimpleTextStoredFieldsWriter.TYPE_LONG,
                    scratch.get(),
                    SimpleTextStoredFieldsWriter.TYPE.length
                ) -> SimpleTextStoredFieldsWriter.TYPE_LONG
                equalsAt(
                    SimpleTextStoredFieldsWriter.TYPE_FLOAT,
                    scratch.get(),
                    SimpleTextStoredFieldsWriter.TYPE.length
                ) -> SimpleTextStoredFieldsWriter.TYPE_FLOAT
                equalsAt(
                    SimpleTextStoredFieldsWriter.TYPE_DOUBLE,
                    scratch.get(),
                    SimpleTextStoredFieldsWriter.TYPE.length
                ) -> SimpleTextStoredFieldsWriter.TYPE_DOUBLE
                else -> throw RuntimeException("unknown field type")
            }

            when (visitor.needsField(fieldInfo!!)) {
                StoredFieldVisitor.Status.YES -> {
                    readField(type, fieldInfo, visitor)
                }
                StoredFieldVisitor.Status.NO -> {
                    readLine()
                    assert(StringHelper.startsWith(scratch.get(), SimpleTextStoredFieldsWriter.VALUE))
                }
                StoredFieldVisitor.Status.STOP -> return
                else -> throw UnsupportedOperationException()
            }
        }
    }

    @Throws(IOException::class)
    private fun readField(type: BytesRef, fieldInfo: FieldInfo, visitor: StoredFieldVisitor) {
        readLine()
        assert(StringHelper.startsWith(scratch.get(), SimpleTextStoredFieldsWriter.VALUE))
        if (type == SimpleTextStoredFieldsWriter.TYPE_STRING) {
            scratchUTF16.copyUTF8Bytes(
                scratch.bytes(),
                SimpleTextStoredFieldsWriter.VALUE.length,
                scratch.length() - SimpleTextStoredFieldsWriter.VALUE.length
            )
            visitor.stringField(fieldInfo, scratchUTF16.toString())
        } else if (type == SimpleTextStoredFieldsWriter.TYPE_BINARY) {
            val copy = ByteArray(scratch.length() - SimpleTextStoredFieldsWriter.VALUE.length)
            scratch.bytes().copyInto(
                copy,
                0,
                SimpleTextStoredFieldsWriter.VALUE.length,
                SimpleTextStoredFieldsWriter.VALUE.length + copy.size
            )
            visitor.binaryField(fieldInfo, copy)
        } else if (type == SimpleTextStoredFieldsWriter.TYPE_INT) {
            scratchUTF16.copyUTF8Bytes(
                scratch.bytes(),
                SimpleTextStoredFieldsWriter.VALUE.length,
                scratch.length() - SimpleTextStoredFieldsWriter.VALUE.length
            )
            visitor.intField(fieldInfo, scratchUTF16.toString().toInt())
        } else if (type == SimpleTextStoredFieldsWriter.TYPE_LONG) {
            scratchUTF16.copyUTF8Bytes(
                scratch.bytes(),
                SimpleTextStoredFieldsWriter.VALUE.length,
                scratch.length() - SimpleTextStoredFieldsWriter.VALUE.length
            )
            visitor.longField(fieldInfo, scratchUTF16.toString().toLong())
        } else if (type == SimpleTextStoredFieldsWriter.TYPE_FLOAT) {
            scratchUTF16.copyUTF8Bytes(
                scratch.bytes(),
                SimpleTextStoredFieldsWriter.VALUE.length,
                scratch.length() - SimpleTextStoredFieldsWriter.VALUE.length
            )
            visitor.floatField(fieldInfo, scratchUTF16.toString().toFloat())
        } else if (type == SimpleTextStoredFieldsWriter.TYPE_DOUBLE) {
            scratchUTF16.copyUTF8Bytes(
                scratch.bytes(),
                SimpleTextStoredFieldsWriter.VALUE.length,
                scratch.length() - SimpleTextStoredFieldsWriter.VALUE.length
            )
            visitor.doubleField(fieldInfo, scratchUTF16.toString().toDouble())
        }
    }

    override fun clone(): StoredFieldsReader {
        if (`in` == null) {
            throw AlreadyClosedException("this FieldsReader is closed")
        }
        return SimpleTextStoredFieldsReader(offsets, `in`!!.clone(), fieldInfos!!)
    }

    @Throws(IOException::class)
    override fun close() {
        try {
            IOUtils.close(`in`)
        } finally {
            `in` = null
            offsets = null
        }
    }

    @Throws(IOException::class)
    private fun readLine() {
        SimpleTextUtil.readLine(`in`!!, scratch)
    }

    private fun parseIntAt(offset: Int): Int {
        scratchUTF16.copyUTF8Bytes(scratch.bytes(), offset, scratch.length() - offset)
        return ArrayUtil.parseInt(scratchUTF16.chars(), 0, scratchUTF16.length())
    }

    private fun equalsAt(a: BytesRef, b: BytesRef, bOffset: Int): Boolean {
        if (a.length != b.length - bOffset) {
            return false
        }
        val aBytes = a.bytes
        val bBytes = b.bytes
        for (i in 0 until a.length) {
            if (aBytes[a.offset + i] != bBytes[b.offset + bOffset + i]) {
                return false
            }
        }
        return true
    }

    override fun toString(): String {
        return this::class.simpleName ?: "SimpleTextStoredFieldsReader"
    }

    @Throws(IOException::class)
    override fun checkIntegrity() {}
}
