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

import com.ionspin.kotlin.bignum.integer.BigInteger
import okio.IOException
import org.gnit.lucenekmp.codecs.DocValuesProducer
import org.gnit.lucenekmp.index.BinaryDocValues
import org.gnit.lucenekmp.index.CorruptIndexException
import org.gnit.lucenekmp.index.DocValuesSkipper
import org.gnit.lucenekmp.index.DocValuesType
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.SortedDocValues
import org.gnit.lucenekmp.index.SortedNumericDocValues
import org.gnit.lucenekmp.index.SortedSetDocValues
import org.gnit.lucenekmp.jdkport.StandardCharsets
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.fromByteArray
import org.gnit.lucenekmp.jdkport.valueOf
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.store.BufferedChecksumIndexInput
import org.gnit.lucenekmp.store.ChecksumIndexInput
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.StringHelper

internal class SimpleTextDocValuesReader(state: SegmentReadState, ext: String) : DocValuesProducer() {

    internal class OneField {
        var docCount: Int = 0
        var dataStartFilePointer: Long = 0
        var pattern: String = ""
        var ordPattern: String = ""
        var maxLength: Int = 0
        var fixedLength: Boolean = false
        var origin: Long = 0
        var minValue: Long = 0
        var maxValue: Long = 0
        var numValues: Long = 0
    }

    private val maxDoc: Int
    private val data: IndexInput
    private val scratch = BytesRefBuilder()
    private val fields: MutableMap<String, OneField> = HashMap()

    init {
        data =
            state.directory.openInput(
                IndexFileNames.segmentFileName(state.segmentInfo.name, state.segmentSuffix, ext),
                state.context
            )
        maxDoc = state.segmentInfo.maxDoc()
        while (true) {
            readLine()
            if (scratch.get() == SimpleTextDocValuesWriter.END) {
                break
            }
            assert(startsWith(SimpleTextDocValuesWriter.FIELD)) { scratch.get().utf8ToString() }
            val fieldName = stripPrefix(SimpleTextDocValuesWriter.FIELD)

            val field = OneField()
            fields[fieldName] = field

            readLine()
            assert(startsWith(SimpleTextDocValuesWriter.TYPE)) { scratch.get().utf8ToString() }

            val dvType = DocValuesType.valueOf(stripPrefix(SimpleTextDocValuesWriter.TYPE))
            assert(dvType != DocValuesType.NONE)

            if (dvType == DocValuesType.NUMERIC || dvType == DocValuesType.SORTED_NUMERIC) {
                readLine()
                assert(startsWith(SimpleTextDocValuesWriter.MINVALUE))
                field.minValue = stripPrefix(SimpleTextDocValuesWriter.MINVALUE).toLong()
                readLine()
                assert(startsWith(SimpleTextDocValuesWriter.MAXVALUE))
                field.maxValue = stripPrefix(SimpleTextDocValuesWriter.MAXVALUE).toLong()
            }

            readLine()
            assert(startsWith(SimpleTextDocValuesWriter.DOCCOUNT))
            field.docCount = stripPrefix(SimpleTextDocValuesWriter.DOCCOUNT).toInt()

            if (dvType == DocValuesType.NUMERIC) {
                readLine()
                assert(startsWith(SimpleTextDocValuesWriter.ORIGIN))
                field.origin = stripPrefix(SimpleTextDocValuesWriter.ORIGIN).toLong()
                readLine()
                assert(startsWith(SimpleTextDocValuesWriter.PATTERN))
                field.pattern = stripPrefix(SimpleTextDocValuesWriter.PATTERN)
                field.dataStartFilePointer = data.filePointer
                data.seek(data.filePointer + (1 + field.pattern.length + 2) * maxDoc.toLong())
            } else if (dvType == DocValuesType.BINARY || dvType == DocValuesType.SORTED_NUMERIC) {
                readLine()
                assert(startsWith(SimpleTextDocValuesWriter.MAXLENGTH))
                field.maxLength = stripPrefix(SimpleTextDocValuesWriter.MAXLENGTH).toInt()
                readLine()
                assert(startsWith(SimpleTextDocValuesWriter.PATTERN))
                field.pattern = stripPrefix(SimpleTextDocValuesWriter.PATTERN)
                field.dataStartFilePointer = data.filePointer
                data.seek(
                    data.filePointer +
                        (9 + field.pattern.length + field.maxLength + 2) * maxDoc.toLong()
                )
            } else if (dvType == DocValuesType.SORTED || dvType == DocValuesType.SORTED_SET) {
                readLine()
                assert(startsWith(SimpleTextDocValuesWriter.NUMVALUES))
                field.numValues = stripPrefix(SimpleTextDocValuesWriter.NUMVALUES).toLong()
                readLine()
                assert(startsWith(SimpleTextDocValuesWriter.MAXLENGTH))
                field.maxLength = stripPrefix(SimpleTextDocValuesWriter.MAXLENGTH).toInt()
                readLine()
                assert(startsWith(SimpleTextDocValuesWriter.PATTERN))
                field.pattern = stripPrefix(SimpleTextDocValuesWriter.PATTERN)
                readLine()
                assert(startsWith(SimpleTextDocValuesWriter.ORDPATTERN))
                field.ordPattern = stripPrefix(SimpleTextDocValuesWriter.ORDPATTERN)
                field.dataStartFilePointer = data.filePointer
                data.seek(
                    data.filePointer +
                        (9 + field.pattern.length + field.maxLength) * field.numValues +
                        (1 + field.ordPattern.length) * maxDoc.toLong()
                )
            } else {
                throw AssertionError()
            }
        }

        assert(fields.isNotEmpty())
    }

    @Throws(IOException::class)
    override fun getNumeric(fieldInfo: FieldInfo): NumericDocValues {
        val values = getNumericNonIterator(fieldInfo)
        val docsWithField = getNumericDocsWithField(fieldInfo)
        return object : NumericDocValues() {
            @Throws(IOException::class)
            override fun nextDoc(): Int = docsWithField.nextDoc()

            override fun docID(): Int = docsWithField.docID()

            override fun cost(): Long = docsWithField.cost()

            @Throws(IOException::class)
            override fun advance(target: Int): Int = docsWithField.advance(target)

            @Throws(IOException::class)
            override fun advanceExact(target: Int): Boolean = docsWithField.advanceExact(target)

            @Throws(IOException::class)
            override fun longValue(): Long = values(docsWithField.docID())
        }
    }

    @Throws(IOException::class)
    internal fun getNumericNonIterator(fieldInfo: FieldInfo): (Int) -> Long {
        val field = fields[fieldInfo.name]
        assert(field != null)

        val `in` = data.clone()
        val scratch = BytesRefBuilder()

        return { docID ->
            try {
                if (docID !in 0..<maxDoc) {
                    throw IndexOutOfBoundsException(
                        "docID must be 0 .. ${maxDoc - 1}; got $docID"
                    )
                }
                `in`.seek(field!!.dataStartFilePointer + (1 + field.pattern.length + 2) * docID.toLong())
                SimpleTextUtil.readLine(`in`, scratch)
                val deltaStr = scratch.get().utf8ToString()
                val delta = parseBigInteger(deltaStr)
                SimpleTextUtil.readLine(`in`, scratch) // 'T' or 'F'
                (BigInteger.valueOf(field.origin) + delta).longValue()
            } catch (ioe: IOException) {
                throw RuntimeException(ioe)
            }
        }
    }

    private abstract class DocValuesIterator : DocIdSetIterator() {
        @Throws(IOException::class)
        abstract fun advanceExact(target: Int): Boolean
    }

    @Throws(IOException::class)
    private fun getNumericDocsWithField(fieldInfo: FieldInfo): DocValuesIterator {
        val field = fields[fieldInfo.name]
        val `in` = data.clone()
        val scratch = BytesRefBuilder()
        return object : DocValuesIterator() {
            var doc = -1

            @Throws(IOException::class)
            override fun nextDoc(): Int = advance(docID() + 1)

            override fun docID(): Int = doc

            override fun cost(): Long = maxDoc.toLong()

            @Throws(IOException::class)
            override fun advance(target: Int): Int {
                for (i in target until maxDoc) {
                    `in`.seek(field!!.dataStartFilePointer + (1 + field.pattern.length + 2) * i.toLong())
                    SimpleTextUtil.readLine(`in`, scratch) // data
                    SimpleTextUtil.readLine(`in`, scratch) // 'T' or 'F'
                    if (scratch.byteAt(0) == 'T'.code.toByte()) {
                        doc = i
                        return doc
                    }
                }
                doc = NO_MORE_DOCS
                return doc
            }

            @Throws(IOException::class)
            override fun advanceExact(target: Int): Boolean {
                doc = target
                `in`.seek(field!!.dataStartFilePointer + (1 + field.pattern.length + 2) * target.toLong())
                SimpleTextUtil.readLine(`in`, scratch) // data
                SimpleTextUtil.readLine(`in`, scratch) // 'T' or 'F'
                return scratch.byteAt(0) == 'T'.code.toByte()
            }
        }
    }

    @Throws(IOException::class)
    override fun getBinary(fieldInfo: FieldInfo): BinaryDocValues {
        val field = fields[fieldInfo.name]
        assert(field != null)

        val `in` = data.clone()
        val scratch = BytesRefBuilder()

        val docsWithField = getBinaryDocsWithField(fieldInfo)

        val values: (Int) -> BytesRef = { docID ->
            try {
                if (docID !in 0..<maxDoc) {
                    throw IndexOutOfBoundsException(
                        "docID must be 0 .. ${maxDoc - 1}; got $docID"
                    )
                }
                `in`.seek(
                    field!!.dataStartFilePointer +
                        (9 + field.pattern.length + field.maxLength + 2) * docID.toLong()
                )
                SimpleTextUtil.readLine(`in`, scratch)
                assert(StringHelper.startsWith(scratch.get(), SimpleTextDocValuesWriter.LENGTH))
                val len = parsePaddedInt(
                    String.fromByteArray(
                        scratch.bytes().copyOfRange(
                            SimpleTextDocValuesWriter.LENGTH.length,
                            scratch.length()
                        ),
                        StandardCharsets.UTF_8
                    )
                )
                val termByteArray = BytesRefBuilder()
                termByteArray.growNoCopy(len)
                termByteArray.setLength(len)
                `in`.readBytes(termByteArray.bytes(), 0, len)
                val term = BytesRefBuilder()
                term.copyBytes(SimpleTextUtil.fromBytesRefString(termByteArray.get().utf8ToString()))
                term.get()
            } catch (ioe: IOException) {
                throw RuntimeException(ioe)
            }
        }

        return object : BinaryDocValues() {
            @Throws(IOException::class)
            override fun nextDoc(): Int = docsWithField.nextDoc()

            override fun docID(): Int = docsWithField.docID()

            override fun cost(): Long = docsWithField.cost()

            @Throws(IOException::class)
            override fun advance(target: Int): Int = docsWithField.advance(target)

            @Throws(IOException::class)
            override fun advanceExact(target: Int): Boolean = docsWithField.advanceExact(target)

            @Throws(IOException::class)
            override fun binaryValue(): BytesRef = values.invoke(docsWithField.docID())
        }
    }

    @Throws(IOException::class)
    private fun getBinaryDocsWithField(fieldInfo: FieldInfo): DocValuesIterator {
        val field = fields[fieldInfo.name]
        val `in` = data.clone()
        val scratch = BytesRefBuilder()

        return object : DocValuesIterator() {
            var doc = -1

            @Throws(IOException::class)
            override fun nextDoc(): Int = advance(docID() + 1)

            override fun docID(): Int = doc

            override fun cost(): Long = maxDoc.toLong()

            @Throws(IOException::class)
            override fun advance(target: Int): Int {
                for (i in target until maxDoc) {
                    `in`.seek(
                        field!!.dataStartFilePointer +
                            (9 + field.pattern.length + field.maxLength + 2) * i.toLong()
                    )
                    SimpleTextUtil.readLine(`in`, scratch)
                    assert(StringHelper.startsWith(scratch.get(), SimpleTextDocValuesWriter.LENGTH))
                    val len = parsePaddedInt(
                        String.fromByteArray(
                            scratch.bytes().copyOfRange(
                                SimpleTextDocValuesWriter.LENGTH.length,
                                scratch.length()
                            ),
                            StandardCharsets.UTF_8
                        )
                    )
                    val bytes = ByteArray(len)
                    `in`.readBytes(bytes, 0, len)
                    SimpleTextUtil.readLine(`in`, scratch) // newline
                    SimpleTextUtil.readLine(`in`, scratch) // 'T' or 'F'
                    if (scratch.byteAt(0) == 'T'.code.toByte()) {
                        doc = i
                        return doc
                    }
                }
                doc = NO_MORE_DOCS
                return doc
            }

            @Throws(IOException::class)
            override fun advanceExact(target: Int): Boolean {
                doc = target
                `in`.seek(
                    field!!.dataStartFilePointer +
                        (9 + field.pattern.length + field.maxLength + 2) * target.toLong()
                )
                SimpleTextUtil.readLine(`in`, scratch)
                assert(StringHelper.startsWith(scratch.get(), SimpleTextDocValuesWriter.LENGTH))
                val len = parsePaddedInt(
                    String.fromByteArray(
                        scratch.bytes().copyOfRange(
                            SimpleTextDocValuesWriter.LENGTH.length,
                            scratch.length()
                        ),
                        StandardCharsets.UTF_8
                    )
                )
                val bytes = ByteArray(len)
                `in`.readBytes(bytes, 0, len)
                SimpleTextUtil.readLine(`in`, scratch) // newline
                SimpleTextUtil.readLine(`in`, scratch) // 'T' or 'F'
                return scratch.byteAt(0) == 'T'.code.toByte()
            }
        }
    }

    @Throws(IOException::class)
    override fun getSorted(fieldInfo: FieldInfo): SortedDocValues {
        val oneField = requireNotNull(fields[fieldInfo.name])

        val `in` = data.clone()
        val scratch = BytesRefBuilder()

        return object : SortedDocValues() {
            var doc = -1
            var ord = 0
            val term = BytesRefBuilder()

            @Throws(IOException::class)
            override fun nextDoc(): Int = advance(docID() + 1)

            override fun docID(): Int = doc

            override fun cost(): Long = maxDoc.toLong()

            @Throws(IOException::class)
            override fun advance(target: Int): Int {
                for (i in target until maxDoc) {
                    `in`.seek(
                    oneField.dataStartFilePointer +
                        oneField.numValues * (9 + oneField.pattern.length + oneField.maxLength) +
                        i.toLong() * (1 + oneField.ordPattern.length)
                    )
                    SimpleTextUtil.readLine(`in`, scratch)
                    ord = parsePaddedLong(scratch.get().utf8ToString()).toInt() - 1
                    if (ord >= 0) {
                        doc = i
                        return doc
                    }
                }
                doc = NO_MORE_DOCS
                return doc
            }

            @Throws(IOException::class)
            override fun advanceExact(target: Int): Boolean {
                doc = target
                `in`.seek(
                    oneField.dataStartFilePointer +
                        oneField.numValues * (9 + oneField.pattern.length + oneField.maxLength) +
                        target.toLong() * (1 + oneField.ordPattern.length)
                )
                SimpleTextUtil.readLine(`in`, scratch)
                ord = parsePaddedLong(scratch.get().utf8ToString()).toInt() - 1
                return ord >= 0
            }

            override fun ordValue(): Int = ord

            @Throws(IOException::class)
            override fun lookupOrd(ord: Int): BytesRef {
                if (ord < 0 || ord >= oneField.numValues) {
                    throw IndexOutOfBoundsException(
                        "ord must be 0 .. ${oneField.numValues - 1}; got $ord"
                    )
                }
                `in`.seek(
                    oneField.dataStartFilePointer +
                        ord.toLong() * (9 + oneField.pattern.length + oneField.maxLength)
                )
                SimpleTextUtil.readLine(`in`, scratch)
                assert(StringHelper.startsWith(scratch.get(), SimpleTextDocValuesWriter.LENGTH))
                val len = parsePaddedInt(
                    String.fromByteArray(
                        scratch.bytes().copyOfRange(
                            SimpleTextDocValuesWriter.LENGTH.length,
                            scratch.length()
                        ),
                        StandardCharsets.UTF_8
                    )
                )
                term.growNoCopy(len)
                term.setLength(len)
                `in`.readBytes(term.bytes(), 0, len)
                return term.get()
            }

            override val valueCount: Int
                get() = oneField.numValues.toInt()
        }
    }

    @Throws(IOException::class)
    override fun getSortedNumeric(field: FieldInfo): SortedNumericDocValues {
        val binary = getBinary(field)
        return object : SortedNumericDocValues() {
            private var values: LongArray = LongArray(0)
            private var index = 0

            @Throws(IOException::class)
            override fun nextDoc(): Int {
                val doc = binary.nextDoc()
                setCurrentDoc()
                return doc
            }

            override fun docID(): Int = binary.docID()

            override fun cost(): Long = binary.cost()

            @Throws(IOException::class)
            override fun advance(target: Int): Int {
                val doc = binary.advance(target)
                setCurrentDoc()
                return doc
            }

            @Throws(IOException::class)
            override fun advanceExact(target: Int): Boolean {
                if (binary.advanceExact(target)) {
                    setCurrentDoc()
                    return true
                }
                return false
            }

            @Throws(IOException::class)
            private fun setCurrentDoc() {
                if (docID() == NO_MORE_DOCS) {
                    return
                }
                val csv = binary.binaryValue()?.utf8ToString() ?: ""
                values = if (csv.isEmpty()) {
                    LongArray(0)
                } else {
                    val parts = csv.split(",")
                    LongArray(parts.size) { idx -> parts[idx].toLong() }
                }
                index = 0
            }

            @Throws(IOException::class)
            override fun nextValue(): Long = values[index++]

            override fun docValueCount(): Int = values.size
        }
    }

    @Throws(IOException::class)
    override fun getSortedSet(fieldInfo: FieldInfo): SortedSetDocValues {
        val oneField = requireNotNull(fields[fieldInfo.name])

        val `in` = data.clone()
        val scratch = BytesRefBuilder()

        return object : SortedSetDocValues() {
            private var currentOrds: Array<String> = emptyArray()
            private var currentIndex = 0
            private val term = BytesRefBuilder()
            private var doc = -1

            @Throws(IOException::class)
            override fun nextDoc(): Int = advance(doc + 1)

            override fun docID(): Int = doc

            override fun cost(): Long = maxDoc.toLong()

            @Throws(IOException::class)
            override fun advance(target: Int): Int {
                for (i in target until maxDoc) {
                    `in`.seek(
                    oneField.dataStartFilePointer +
                        oneField.numValues * (9 + oneField.pattern.length + oneField.maxLength) +
                        i.toLong() * (1 + oneField.ordPattern.length)
                    )
                    SimpleTextUtil.readLine(`in`, scratch)
                    val ordList = scratch.get().utf8ToString().trim()
                    if (ordList.isNotEmpty()) {
                        currentOrds = ordList.split(",").toTypedArray()
                        currentIndex = 0
                        doc = i
                        return doc
                    }
                }
                doc = NO_MORE_DOCS
                return doc
            }

            @Throws(IOException::class)
            override fun advanceExact(target: Int): Boolean {
                `in`.seek(
                    oneField.dataStartFilePointer +
                        oneField.numValues * (9 + oneField.pattern.length + oneField.maxLength) +
                        target.toLong() * (1 + oneField.ordPattern.length)
                )
                SimpleTextUtil.readLine(`in`, scratch)
                val ordList = scratch.get().utf8ToString().trim()
                doc = target
                if (ordList.isNotEmpty()) {
                    currentOrds = ordList.split(",").toTypedArray()
                    currentIndex = 0
                    return true
                }
                return false
            }

            @Throws(IOException::class)
            override fun nextOrd(): Long = currentOrds[currentIndex++].toLong()

            override fun docValueCount(): Int = currentOrds.size

            @Throws(IOException::class)
            override fun lookupOrd(ord: Long): BytesRef {
                if (ord < 0 || ord >= oneField.numValues) {
                    throw IndexOutOfBoundsException(
                        "ord must be 0 .. ${oneField.numValues - 1}; got $ord"
                    )
                }
                `in`.seek(
                    oneField.dataStartFilePointer +
                        ord * (9 + oneField.pattern.length + oneField.maxLength)
                )
                SimpleTextUtil.readLine(`in`, scratch)
                assert(StringHelper.startsWith(scratch.get(), SimpleTextDocValuesWriter.LENGTH))
                val len = parsePaddedInt(
                    String.fromByteArray(
                        scratch.bytes().copyOfRange(
                            SimpleTextDocValuesWriter.LENGTH.length,
                            scratch.length()
                        ),
                        StandardCharsets.UTF_8
                    )
                )
                term.growNoCopy(len)
                term.setLength(len)
                `in`.readBytes(term.bytes(), 0, len)
                return term.get()
            }

            override val valueCount: Long
                get() = oneField.numValues
        }
    }

    @Throws(IOException::class)
    override fun close() {
        data.close()
    }

    /** Used only in ctor: */
    @Throws(IOException::class)
    private fun readLine() {
        SimpleTextUtil.readLine(data, scratch)
    }

    /** Used only in ctor: */
    private fun startsWith(prefix: BytesRef): Boolean {
        return StringHelper.startsWith(scratch.get(), prefix)
    }

    /** Used only in ctor: */
    private fun stripPrefix(prefix: BytesRef): String {
        val bytes = scratch.bytes().copyOfRange(prefix.length, scratch.length())
        return String.fromByteArray(bytes, StandardCharsets.UTF_8)
    }

    override fun toString(): String {
        return this::class.simpleName + "(fields=" + fields.size + ")"
    }

    @Throws(IOException::class)
    override fun checkIntegrity() {
        val scratch = BytesRefBuilder()
        val clone = data.clone()
        clone.seek(0)
        // checksum is fixed-width encoded with 20 bytes, plus 1 byte for newline
        val footerStartPos = clone.length() - (SimpleTextUtil.CHECKSUM.length + 21)
        val input: ChecksumIndexInput = BufferedChecksumIndexInput(clone)
        while (true) {
            SimpleTextUtil.readLine(input, scratch)
            if (input.filePointer >= footerStartPos) {
                if (input.filePointer != footerStartPos) {
                    throw CorruptIndexException(
                        "SimpleText failure: footer does not start at expected position current=${input.filePointer} vs expected=$footerStartPos",
                        input
                    )
                }
                SimpleTextUtil.checkFooter(input)
                break
            }
        }
    }

    override fun getSkipper(fieldInfo: FieldInfo): DocValuesSkipper {
        val numeric =
            fieldInfo.docValuesType == DocValuesType.NUMERIC ||
                fieldInfo.docValuesType == DocValuesType.SORTED_NUMERIC
        val field = fields[fieldInfo.name]
        assert(field != null)

        return object : DocValuesSkipper() {
            private var doc = -1

            override fun numLevels(): Int = 1

            override fun minValue(level: Int): Long = minValue()

            override fun maxValue(level: Int): Long = maxValue()

            override fun docCount(level: Int): Int = docCount()

            override fun minValue(): Long = if (numeric) field!!.minValue else 0

            override fun maxValue(): Long = if (numeric) field!!.maxValue else field!!.numValues - 1

            override fun docCount(): Int = field!!.docCount

            override fun minDocID(level: Int): Int {
                return if (doc == -1) {
                    -1
                } else if (doc >= maxDoc || field!!.docCount == 0) {
                    DocIdSetIterator.NO_MORE_DOCS
                } else {
                    0
                }
            }

            override fun maxDocID(level: Int): Int {
                return if (doc == -1) {
                    -1
                } else if (doc >= maxDoc || field!!.docCount == 0) {
                    DocIdSetIterator.NO_MORE_DOCS
                } else {
                    maxDoc
                }
            }

            override fun advance(target: Int) {
                doc = target
            }
        }
    }

    private fun parsePaddedInt(s: String): Int {
        val trimmed = s.trim()
        return if (trimmed.isEmpty()) 0 else trimmed.toInt()
    }

    private fun parsePaddedLong(s: String): Long {
        val trimmed = s.trim()
        return if (trimmed.isEmpty()) 0L else trimmed.toLong()
    }

    private fun parseBigInteger(s: String): BigInteger {
        val trimmed = s.trim()
        return if (trimmed.isEmpty()) BigInteger.ZERO else BigInteger.parseString(trimmed, 10)
    }
}
