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
import org.gnit.lucenekmp.codecs.DocValuesConsumer
import org.gnit.lucenekmp.codecs.DocValuesProducer
import org.gnit.lucenekmp.index.BinaryDocValues
import org.gnit.lucenekmp.index.DocValuesType
import org.gnit.lucenekmp.index.EmptyDocValuesProducer
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.SegmentWriteState
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.valueOf
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.IOUtils

internal class SimpleTextDocValuesWriter(state: SegmentWriteState, ext: String) : DocValuesConsumer() {
    companion object {
        val END: BytesRef = BytesRef("END")
        val FIELD: BytesRef = BytesRef("field ")
        val TYPE: BytesRef = BytesRef("  type ")
        val DOCCOUNT: BytesRef = BytesRef("  doccount ")
        // used for numerics
        val ORIGIN: BytesRef = BytesRef("  origin ") // for deltas

        val MINVALUE: BytesRef = BytesRef("  minalue ")
        val MAXVALUE: BytesRef = BytesRef("  maxvalue ")

        val PATTERN: BytesRef = BytesRef("  pattern ")
        // used for bytes
        val LENGTH: BytesRef = BytesRef("length ")
        val MAXLENGTH: BytesRef = BytesRef("  maxlength ")
        // used for sorted bytes
        val NUMVALUES: BytesRef = BytesRef("  numvalues ")
        val ORDPATTERN: BytesRef = BytesRef("  ordpattern ")
    }

    private var data: IndexOutput?
    private val scratch = BytesRefBuilder()
    private val numDocs: Int
    private val fieldsSeen: MutableSet<String> = HashSet() // for asserting

    init {
        data =
            state.directory.createOutput(
                IndexFileNames.segmentFileName(state.segmentInfo.name, state.segmentSuffix, ext),
                state.context
            )
        numDocs = state.segmentInfo.maxDoc()
    }

    // for asserting
    private fun fieldSeen(field: String): Boolean {
        assert(!fieldsSeen.contains(field)) {
            "field \"$field\" was added more than once during flush"
        }
        fieldsSeen.add(field)
        return true
    }

    @Throws(IOException::class)
    override fun addNumericField(field: FieldInfo, valuesProducer: DocValuesProducer) {
        assert(fieldSeen(field.name))
        assert(field.docValuesType == DocValuesType.NUMERIC || field.hasNorms())
        writeFieldEntry(field, DocValuesType.NUMERIC)

        // first pass to find min/max
        var minValue = Long.MAX_VALUE
        var maxValue = Long.MIN_VALUE
        var values = valuesProducer.getNumeric(field)
        var numValues = 0
        var doc = values!!.nextDoc()
        while (doc != DocIdSetIterator.NO_MORE_DOCS) {
            val v = values.longValue()
            minValue = kotlin.math.min(minValue, v)
            maxValue = kotlin.math.max(maxValue, v)
            numValues++
            doc = values.nextDoc()
        }

        // write absolute min and max for skipper
        SimpleTextUtil.write(data!!, MINVALUE)
        SimpleTextUtil.write(data!!, minValue.toString(), scratch)
        SimpleTextUtil.writeNewline(data!!)

        SimpleTextUtil.write(data!!, MAXVALUE)
        SimpleTextUtil.write(data!!, maxValue.toString(), scratch)
        SimpleTextUtil.writeNewline(data!!)

        SimpleTextUtil.write(data!!, DOCCOUNT)
        SimpleTextUtil.write(data!!, numValues.toString(), scratch)
        SimpleTextUtil.writeNewline(data!!)

        if (numValues != numDocs) {
            minValue = kotlin.math.min(minValue, 0L)
            maxValue = kotlin.math.max(maxValue, 0L)
        }

        // write our minimum value to the .dat, all entries are deltas from that
        SimpleTextUtil.write(data!!, ORIGIN)
        SimpleTextUtil.write(data!!, minValue.toString(), scratch)
        SimpleTextUtil.writeNewline(data!!)

        // build up our fixed-width "simple text packed ints" format
        val maxBig = BigInteger.valueOf(maxValue)
        val minBig = BigInteger.valueOf(minValue)
        val diffBig = maxBig - minBig
        val maxBytesPerValue = diffBig.toString().length
        val sb = StringBuilder()
        repeat(maxBytesPerValue) { sb.append('0') }

        // write our pattern to the .dat
        SimpleTextUtil.write(data!!, PATTERN)
        SimpleTextUtil.write(data!!, sb.toString(), scratch)
        SimpleTextUtil.writeNewline(data!!)

        val patternString = sb.toString()

        var numDocsWritten = 0

        // second pass to write the values
        values = valuesProducer.getNumeric(field)
        for (i in 0 until numDocs) {
            if (values!!.docID() < i) {
                values.nextDoc()
                assert(values.docID() >= i)
            }
            val value = if (values.docID() != i) 0L else values.longValue()
            assert(value >= minValue)
            val delta = BigInteger.valueOf(value) - BigInteger.valueOf(minValue)
            val s = formatPadded(delta, patternString.length)
            assert(s.length == patternString.length)
            SimpleTextUtil.write(data!!, s, scratch)
            SimpleTextUtil.writeNewline(data!!)
            if (values.docID() != i) {
                SimpleTextUtil.write(data!!, "F", scratch)
            } else {
                SimpleTextUtil.write(data!!, "T", scratch)
            }
            SimpleTextUtil.writeNewline(data!!)
            numDocsWritten++
            assert(numDocsWritten <= numDocs)
        }

        assert(numDocs == numDocsWritten) { "numDocs=$numDocs numDocsWritten=$numDocsWritten" }
    }

    @Throws(IOException::class)
    override fun addBinaryField(field: FieldInfo, valuesProducer: DocValuesProducer) {
        assert(fieldSeen(field.name))
        assert(field.docValuesType == DocValuesType.BINARY)
        writeFieldEntry(field, DocValuesType.BINARY)
        doAddBinaryField(field, valuesProducer)
    }

    @Throws(IOException::class)
    private fun doAddBinaryField(field: FieldInfo, valuesProducer: DocValuesProducer) {
        var maxLength = 0
        var values = valuesProducer.getBinary(field)
        var docCount = 0
        var doc = values!!.nextDoc()
        while (doc != DocIdSetIterator.NO_MORE_DOCS) {
            ++docCount
            maxLength = kotlin.math.max(maxLength, values.binaryValue().toString().length)
            doc = values.nextDoc()
        }

        SimpleTextUtil.write(data!!, DOCCOUNT)
        SimpleTextUtil.write(data!!, docCount.toString(), scratch)
        SimpleTextUtil.writeNewline(data!!)

        // write maxLength
        SimpleTextUtil.write(data!!, MAXLENGTH)
        SimpleTextUtil.write(data!!, maxLength.toString(), scratch)
        SimpleTextUtil.writeNewline(data!!)

        val maxBytesLength = maxLength.toString().length
        val sb = StringBuilder()
        repeat(maxBytesLength) { sb.append('0') }
        // write our pattern for encoding lengths
        SimpleTextUtil.write(data!!, PATTERN)
        SimpleTextUtil.write(data!!, sb.toString(), scratch)
        SimpleTextUtil.writeNewline(data!!)
        val patternLen = sb.length

        values = valuesProducer.getBinary(field)
        var numDocsWritten = 0
        for (i in 0 until numDocs) {
            if (values!!.docID() < i) {
                values.nextDoc()
                assert(values.docID() >= i)
            }
            val stringVal = if (values.docID() == i) values.binaryValue().toString() else null
            // write length
            val length = stringVal?.length ?: 0
            SimpleTextUtil.write(data!!, LENGTH)
            SimpleTextUtil.write(data!!, formatPadded(length, patternLen), scratch)
            SimpleTextUtil.writeNewline(data!!)

            // write bytes as hex array
            if (stringVal != null) {
                SimpleTextUtil.write(data!!, stringVal, scratch)
            }

            // pad to fit
            for (j in length until maxLength) {
                data!!.writeByte(' '.code.toByte())
            }
            SimpleTextUtil.writeNewline(data!!)
            if (stringVal == null) {
                SimpleTextUtil.write(data!!, "F", scratch)
            } else {
                SimpleTextUtil.write(data!!, "T", scratch)
            }
            SimpleTextUtil.writeNewline(data!!)
            numDocsWritten++
        }

        assert(numDocs == numDocsWritten)
    }

    @Throws(IOException::class)
    override fun addSortedField(field: FieldInfo, valuesProducer: DocValuesProducer) {
        assert(fieldSeen(field.name))
        assert(field.docValuesType == DocValuesType.SORTED)
        writeFieldEntry(field, DocValuesType.SORTED)

        var docCount = 0
        var values = valuesProducer.getSorted(field)
        var doc = values!!.nextDoc()
        while (doc != DocIdSetIterator.NO_MORE_DOCS) {
            ++docCount
            doc = values.nextDoc()
        }
        SimpleTextUtil.write(data!!, DOCCOUNT)
        SimpleTextUtil.write(data!!, docCount.toString(), scratch)
        SimpleTextUtil.writeNewline(data!!)

        var valueCount = 0
        var maxLength = -1
        var terms = valuesProducer.getSorted(field)!!.termsEnum()
        var value = terms!!.next()
        while (value != null) {
            maxLength = kotlin.math.max(maxLength, value.length)
            valueCount++
            value = terms.next()
        }

        // write numValues
        SimpleTextUtil.write(data!!, NUMVALUES)
        SimpleTextUtil.write(data!!, valueCount.toString(), scratch)
        SimpleTextUtil.writeNewline(data!!)

        // write maxLength
        SimpleTextUtil.write(data!!, MAXLENGTH)
        SimpleTextUtil.write(data!!, maxLength.toString(), scratch)
        SimpleTextUtil.writeNewline(data!!)

        val maxBytesLength = maxLength.toString().length
        val sb = StringBuilder()
        repeat(maxBytesLength) { sb.append('0') }

        // write our pattern for encoding lengths
        SimpleTextUtil.write(data!!, PATTERN)
        SimpleTextUtil.write(data!!, sb.toString(), scratch)
        SimpleTextUtil.writeNewline(data!!)
        val lengthPatternLen = sb.length

        val maxOrdBytes = (valueCount + 1L).toString().length
        sb.setLength(0)
        repeat(maxOrdBytes) { sb.append('0') }

        // write our pattern for ords
        SimpleTextUtil.write(data!!, ORDPATTERN)
        SimpleTextUtil.write(data!!, sb.toString(), scratch)
        SimpleTextUtil.writeNewline(data!!)
        val ordPatternLen = sb.length

        // for asserts:
        var valuesSeen = 0

        terms = valuesProducer.getSorted(field)!!.termsEnum()
        value = terms!!.next()
        while (value != null) {
            // write length
            SimpleTextUtil.write(data!!, LENGTH)
            SimpleTextUtil.write(data!!, formatPadded(value.length, lengthPatternLen), scratch)
            SimpleTextUtil.writeNewline(data!!)

            // write bytes -- don't use SimpleText.write because it escapes
            data!!.writeBytes(value.bytes, value.offset, value.length)

            // pad to fit
            for (i in value.length until maxLength) {
                data!!.writeByte(' '.code.toByte())
            }
            SimpleTextUtil.writeNewline(data!!)
            valuesSeen++
            assert(valuesSeen <= valueCount)
            value = terms.next()
        }

        assert(valuesSeen == valueCount)

        values = valuesProducer.getSorted(field)
        for (i in 0 until numDocs) {
            if (values!!.docID() < i) {
                values.nextDoc()
                assert(values.docID() >= i)
            }
            var ord = -1
            if (values.docID() == i) {
                ord = values.ordValue()
            }
            SimpleTextUtil.write(data!!, formatPadded(ord + 1L, ordPatternLen), scratch)
            SimpleTextUtil.writeNewline(data!!)
        }
    }

    @Throws(IOException::class)
    override fun addSortedNumericField(field: FieldInfo, valuesProducer: DocValuesProducer) {
        assert(fieldSeen(field.name))
        assert(field.docValuesType == DocValuesType.SORTED_NUMERIC)
        writeFieldEntry(field, DocValuesType.SORTED_NUMERIC)

        var minValue = Long.MAX_VALUE
        var maxValue = Long.MIN_VALUE
        val values = valuesProducer.getSortedNumeric(field)
        var doc = values!!.nextDoc()
        while (doc != DocIdSetIterator.NO_MORE_DOCS) {
            for (i in 0 until values.docValueCount()) {
                val v = values.nextValue()
                minValue = kotlin.math.min(minValue, v)
                maxValue = kotlin.math.max(maxValue, v)
            }
            doc = values.nextDoc()
        }

        // write absolute min and max for skipper
        SimpleTextUtil.write(data!!, MINVALUE)
        SimpleTextUtil.write(data!!, minValue.toString(), scratch)
        SimpleTextUtil.writeNewline(data!!)

        SimpleTextUtil.write(data!!, MAXVALUE)
        SimpleTextUtil.write(data!!, maxValue.toString(), scratch)
        SimpleTextUtil.writeNewline(data!!)

        doAddBinaryField(
            field,
            object : EmptyDocValuesProducer() {
                @Throws(IOException::class)
                override fun getBinary(field: FieldInfo): BinaryDocValues {
                    val values = valuesProducer.getSortedNumeric(field)
                    return object : BinaryDocValues() {
                        private val builder = StringBuilder()
                        private var binaryValue: BytesRef? = null

                        @Throws(IOException::class)
                        override fun nextDoc(): Int {
                            val doc = values!!.nextDoc()
                            setCurrentDoc()
                            return doc
                        }

                        override fun docID(): Int {
                            return values!!.docID()
                        }

                        override fun cost(): Long {
                            return values!!.cost()
                        }

                        @Throws(IOException::class)
                        override fun advance(target: Int): Int {
                            val doc = values!!.advance(target)
                            setCurrentDoc()
                            return doc
                        }

                        @Throws(IOException::class)
                        override fun advanceExact(target: Int): Boolean {
                            if (values!!.advanceExact(target)) {
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
                            builder.setLength(0)
                            for (i in 0 until values!!.docValueCount()) {
                                if (i > 0) {
                                    builder.append(',')
                                }
                                builder.append(values.nextValue().toString())
                            }
                            binaryValue = BytesRef(builder.toString())
                        }

                        @Throws(IOException::class)
                        override fun binaryValue(): BytesRef {
                            return binaryValue!!
                        }
                    }
                }
            }
        )
    }

    @Throws(IOException::class)
    override fun addSortedSetField(field: FieldInfo, valuesProducer: DocValuesProducer) {
        assert(fieldSeen(field.name))
        assert(field.docValuesType == DocValuesType.SORTED_SET)
        writeFieldEntry(field, DocValuesType.SORTED_SET)

        var docCount = 0
        var values = valuesProducer.getSortedSet(field)
        var doc = values!!.nextDoc()
        while (doc != DocIdSetIterator.NO_MORE_DOCS) {
            ++docCount
            doc = values.nextDoc()
        }
        SimpleTextUtil.write(data!!, DOCCOUNT)
        SimpleTextUtil.write(data!!, docCount.toString(), scratch)
        SimpleTextUtil.writeNewline(data!!)

        var valueCount = 0L
        var maxLength = 0
        var terms = valuesProducer.getSortedSet(field)!!.termsEnum()
        var value = terms.next()
        while (value != null) {
            maxLength = kotlin.math.max(maxLength, value.length)
            valueCount++
            value = terms.next()
        }

        // write numValues
        SimpleTextUtil.write(data!!, NUMVALUES)
        SimpleTextUtil.write(data!!, valueCount.toString(), scratch)
        SimpleTextUtil.writeNewline(data!!)

        // write maxLength
        SimpleTextUtil.write(data!!, MAXLENGTH)
        SimpleTextUtil.write(data!!, maxLength.toString(), scratch)
        SimpleTextUtil.writeNewline(data!!)

        val maxBytesLength = maxLength.toString().length
        val sb = StringBuilder()
        repeat(maxBytesLength) { sb.append('0') }

        // write our pattern for encoding lengths
        SimpleTextUtil.write(data!!, PATTERN)
        SimpleTextUtil.write(data!!, sb.toString(), scratch)
        SimpleTextUtil.writeNewline(data!!)
        val lengthPatternLen = sb.length

        // compute ord pattern: this is funny, we encode all values for all docs to find the maximum length
        var maxOrdListLength = 0
        val sb2 = StringBuilder()
        values = valuesProducer.getSortedSet(field)
        doc = values!!.nextDoc()
        while (doc != DocIdSetIterator.NO_MORE_DOCS) {
            sb2.setLength(0)
            for (i in 0 until values.docValueCount()) {
                if (sb2.isNotEmpty()) {
                    sb2.append(",")
                }
                sb2.append(values.nextOrd().toString())
            }
            maxOrdListLength = kotlin.math.max(maxOrdListLength, sb2.length)
            doc = values.nextDoc()
        }

        sb2.setLength(0)
        repeat(maxOrdListLength) { sb2.append('X') }

        // write our pattern for ord lists
        SimpleTextUtil.write(data!!, ORDPATTERN)
        SimpleTextUtil.write(data!!, sb2.toString(), scratch)
        SimpleTextUtil.writeNewline(data!!)

        // for asserts:
        var valuesSeen = 0L

        terms = valuesProducer.getSortedSet(field)!!.termsEnum()
        value = terms.next()
        while (value != null) {
            // write length
            SimpleTextUtil.write(data!!, LENGTH)
            SimpleTextUtil.write(data!!, formatPadded(value.length, lengthPatternLen), scratch)
            SimpleTextUtil.writeNewline(data!!)

            // write bytes -- don't use SimpleText.write because it escapes
            data!!.writeBytes(value.bytes, value.offset, value.length)

            // pad to fit
            for (i in value.length until maxLength) {
                data!!.writeByte(' '.code.toByte())
            }
            SimpleTextUtil.writeNewline(data!!)
            valuesSeen++
            assert(valuesSeen <= valueCount)
            value = terms.next()
        }

        assert(valuesSeen == valueCount)

        values = valuesProducer.getSortedSet(field)

        // write the ords for each doc comma-separated
        for (i in 0 until numDocs) {
            if (values!!.docID() < i) {
                values.nextDoc()
                assert(values.docID() >= i)
            }
            sb2.setLength(0)
            if (values.docID() == i) {
                for (j in 0 until values.docValueCount()) {
                    if (sb2.isNotEmpty()) {
                        sb2.append(",")
                    }
                    sb2.append(values.nextOrd().toString())
                }
            }
            // now pad to fit: these are numbers so spaces work well. reader calls trim()
            val numPadding = maxOrdListLength - sb2.length
            repeat(numPadding) { sb2.append(' ') }
            SimpleTextUtil.write(data!!, sb2.toString(), scratch)
            SimpleTextUtil.writeNewline(data!!)
        }
    }

    /** write the header for this field */
    @Throws(IOException::class)
    private fun writeFieldEntry(field: FieldInfo, type: DocValuesType) {
        SimpleTextUtil.write(data!!, FIELD)
        SimpleTextUtil.write(data!!, field.name, scratch)
        SimpleTextUtil.writeNewline(data!!)

        SimpleTextUtil.write(data!!, TYPE)
        SimpleTextUtil.write(data!!, type.toString(), scratch)
        SimpleTextUtil.writeNewline(data!!)
    }

    @Throws(IOException::class)
    override fun close() {
        if (data != null) {
            var success = false
            try {
                assert(fieldsSeen.isNotEmpty())
                // TODO: sheisty to do this here?
                SimpleTextUtil.write(data!!, END)
                SimpleTextUtil.writeNewline(data!!)
                SimpleTextUtil.writeChecksum(data!!, scratch)
                success = true
            } finally {
                if (success) {
                    IOUtils.close(data)
                } else {
                    IOUtils.closeWhileHandlingException(data)
                }
                data = null
            }
        }
    }

    private fun formatPadded(value: BigInteger, width: Int): String {
        return value.toString().padStart(width, '0')
    }

    private fun formatPadded(value: Long, width: Int): String {
        return value.toString().padStart(width, '0')
    }

    private fun formatPadded(value: Int, width: Int): String {
        return value.toString().padStart(width, '0')
    }
}
