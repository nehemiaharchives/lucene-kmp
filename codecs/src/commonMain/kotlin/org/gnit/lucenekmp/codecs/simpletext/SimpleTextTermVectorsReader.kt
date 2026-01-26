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
import org.gnit.lucenekmp.codecs.TermVectorsReader
import org.gnit.lucenekmp.index.BaseTermsEnum
import org.gnit.lucenekmp.index.Fields
import org.gnit.lucenekmp.index.ImpactsEnum
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.index.SegmentInfo
import org.gnit.lucenekmp.index.SlowImpactsEnum
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.jdkport.TreeMap
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
 * Reads plain-text term vectors.
 *
 * <p><b>FOR RECREATIONAL USE ONLY</b>
 *
 * @lucene.experimental
 */
class SimpleTextTermVectorsReader : TermVectorsReader {

    private var offsets: LongArray? = null /* docid -> offset in .vec file */
    private var `in`: IndexInput? = null
    private val scratch = BytesRefBuilder()
    private val scratchUTF16 = CharsRefBuilder()

    constructor(directory: Directory, si: SegmentInfo, context: IOContext) : super() {
        var success = false
        try {
            `in` =
                directory.openInput(
                    IndexFileNames.segmentFileName(si.name, "", SimpleTextTermVectorsWriter.VECTORS_EXTENSION),
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
    private constructor(offsets: LongArray?, `in`: IndexInput?) : super() {
        this.offsets = offsets
        this.`in` = `in`
    }

    // we don't actually write a .tvx-like index, instead we read the
    // vectors file in entirety up-front and save the offsets
    // so we can seek to the data later.
    @Throws(IOException::class)
    private fun readIndex(maxDoc: Int) {
        val input: ChecksumIndexInput = BufferedChecksumIndexInput(`in`!!)
        offsets = LongArray(maxDoc)
        var upto = 0
        while (scratch.get() != SimpleTextTermVectorsWriter.END) {
            SimpleTextUtil.readLine(input, scratch)
            if (StringHelper.startsWith(scratch.get(), SimpleTextTermVectorsWriter.DOC)) {
                offsets!![upto] = input.filePointer
                upto++
            }
        }
        SimpleTextUtil.checkFooter(input)
        assert(upto == offsets!!.size)
    }

    @Throws(IOException::class)
    override fun get(doc: Int): Fields? {
        val fields: TreeMap<String, SimpleTVTerms> = TreeMap()
        `in`!!.seek(offsets!![doc])
        readLine()
        assert(StringHelper.startsWith(scratch.get(), SimpleTextTermVectorsWriter.NUMFIELDS))
        val numFields = parseIntAt(SimpleTextTermVectorsWriter.NUMFIELDS.length)
        if (numFields == 0) {
            return null // no vectors for this doc
        }
        for (i in 0 until numFields) {
            readLine()
            assert(StringHelper.startsWith(scratch.get(), SimpleTextTermVectorsWriter.FIELD))
            // skip fieldNumber:
            parseIntAt(SimpleTextTermVectorsWriter.FIELD.length)

            readLine()
            assert(StringHelper.startsWith(scratch.get(), SimpleTextTermVectorsWriter.FIELDNAME))
            val fieldName = readString(SimpleTextTermVectorsWriter.FIELDNAME.length, scratch)

            readLine()
            assert(StringHelper.startsWith(scratch.get(), SimpleTextTermVectorsWriter.FIELDPOSITIONS))
            val positions =
                readString(SimpleTextTermVectorsWriter.FIELDPOSITIONS.length, scratch).toBoolean()

            readLine()
            assert(StringHelper.startsWith(scratch.get(), SimpleTextTermVectorsWriter.FIELDOFFSETS))
            val offsets =
                readString(SimpleTextTermVectorsWriter.FIELDOFFSETS.length, scratch).toBoolean()

            readLine()
            assert(StringHelper.startsWith(scratch.get(), SimpleTextTermVectorsWriter.FIELDPAYLOADS))
            val payloads =
                readString(SimpleTextTermVectorsWriter.FIELDPAYLOADS.length, scratch).toBoolean()

            readLine()
            assert(StringHelper.startsWith(scratch.get(), SimpleTextTermVectorsWriter.FIELDTERMCOUNT))
            val termCount = parseIntAt(SimpleTextTermVectorsWriter.FIELDTERMCOUNT.length)

            val terms = SimpleTVTerms(offsets, positions, payloads)
            fields[fieldName] = terms

            val term = BytesRefBuilder()
            for (j in 0 until termCount) {
                readLine()
                assert(StringHelper.startsWith(scratch.get(), SimpleTextTermVectorsWriter.TERMTEXT))
                val termLength = scratch.length() - SimpleTextTermVectorsWriter.TERMTEXT.length
                term.growNoCopy(termLength)
                term.setLength(termLength)
                scratch.bytes().copyInto(
                    term.bytes(),
                    0,
                    SimpleTextTermVectorsWriter.TERMTEXT.length,
                    SimpleTextTermVectorsWriter.TERMTEXT.length + termLength
                )

                val postings = SimpleTVPostings()
                terms.terms[term.toBytesRef()] = postings

                readLine()
                assert(StringHelper.startsWith(scratch.get(), SimpleTextTermVectorsWriter.TERMFREQ))
                postings.freq = parseIntAt(SimpleTextTermVectorsWriter.TERMFREQ.length)

                if (positions || offsets) {
                    if (positions) {
                        postings.positions = IntArray(postings.freq)
                        if (payloads) {
                            postings.payloads = arrayOfNulls(postings.freq)
                        }
                    }

                    if (offsets) {
                        postings.startOffsets = IntArray(postings.freq)
                        postings.endOffsets = IntArray(postings.freq)
                    }

                    for (k in 0 until postings.freq) {
                        if (positions) {
                            readLine()
                            assert(StringHelper.startsWith(scratch.get(), SimpleTextTermVectorsWriter.POSITION))
                            postings.positions!![k] =
                                parseIntAt(SimpleTextTermVectorsWriter.POSITION.length)
                            if (payloads) {
                                readLine()
                                assert(StringHelper.startsWith(scratch.get(), SimpleTextTermVectorsWriter.PAYLOAD))
                                if (scratch.length() - SimpleTextTermVectorsWriter.PAYLOAD.length == 0) {
                                    postings.payloads!![k] = null
                                } else {
                                    val payloadBytes =
                                        ByteArray(scratch.length() - SimpleTextTermVectorsWriter.PAYLOAD.length)
                                    scratch.bytes().copyInto(
                                        payloadBytes,
                                        0,
                                        SimpleTextTermVectorsWriter.PAYLOAD.length,
                                        SimpleTextTermVectorsWriter.PAYLOAD.length + payloadBytes.size
                                    )
                                    postings.payloads!![k] = BytesRef(payloadBytes)
                                }
                            }
                        }

                        if (offsets) {
                            readLine()
                            assert(StringHelper.startsWith(scratch.get(), SimpleTextTermVectorsWriter.STARTOFFSET))
                            postings.startOffsets!![k] =
                                parseIntAt(SimpleTextTermVectorsWriter.STARTOFFSET.length)

                            readLine()
                            assert(StringHelper.startsWith(scratch.get(), SimpleTextTermVectorsWriter.ENDOFFSET))
                            postings.endOffsets!![k] =
                                parseIntAt(SimpleTextTermVectorsWriter.ENDOFFSET.length)
                        }
                    }
                }
            }
        }
        return SimpleTVFields(fields)
    }

    override fun clone(): TermVectorsReader {
        if (`in` == null) {
            throw AlreadyClosedException("this TermVectorsReader is closed")
        }
        return SimpleTextTermVectorsReader(offsets, `in`!!.clone())
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

    private fun readString(offset: Int, scratch: BytesRefBuilder): String {
        scratchUTF16.copyUTF8Bytes(scratch.bytes(), offset, scratch.length() - offset)
        return scratchUTF16.toString()
    }

    private class SimpleTVFields(private val fields: TreeMap<String, SimpleTVTerms>) : Fields() {
        override fun iterator(): MutableIterator<String> {
            return fields.keys.iterator()
        }

        @Throws(IOException::class)
        override fun terms(field: String?): Terms? {
            return fields[field]
        }

        override fun size(): Int {
            return fields.size
        }
    }

    private class SimpleTVTerms(
        val hasOffsets: Boolean,
        val hasPositions: Boolean,
        val hasPayloads: Boolean
    ) : Terms() {
        val terms: TreeMap<BytesRef, SimpleTVPostings> = TreeMap()

        @Throws(IOException::class)
        override fun iterator(): TermsEnum {
            // TODO: reuse
            return SimpleTVTermsEnum(terms)
        }

        @Throws(IOException::class)
        override fun size(): Long {
            return terms.size.toLong()
        }

        override val sumTotalTermFreq
            get(): Long {
                // TODO: make it constant-time
                var ttf = 0L
                val iterator = iterator()
                var b = iterator.next()
                while (b != null) {
                    ttf += iterator.totalTermFreq()
                    b = iterator.next()
                }
                return ttf
            }

        override val sumDocFreq
            get(): Long {
                return terms.size.toLong()
            }

        override val docCount
            get(): Int {
                return 1
            }

        override fun hasFreqs(): Boolean {
            return true
        }

        override fun hasOffsets(): Boolean {
            return hasOffsets
        }

        override fun hasPositions(): Boolean {
            return hasPositions
        }

        override fun hasPayloads(): Boolean {
            return hasPayloads
        }
    }

    private class SimpleTVPostings {
        var freq: Int = 0
        var positions: IntArray? = null
        var startOffsets: IntArray? = null
        var endOffsets: IntArray? = null
        var payloads: Array<BytesRef?>? = null
    }

    private class SimpleTVTermsEnum(private val terms: TreeMap<BytesRef, SimpleTVPostings>) :
        BaseTermsEnum() {
        private var iterator: MutableIterator<MutableMap.MutableEntry<BytesRef, SimpleTVPostings>> =
            terms.entries.iterator()
        private lateinit var current: MutableMap.MutableEntry<BytesRef, SimpleTVPostings>

        @Throws(IOException::class)
        override fun seekCeil(text: BytesRef): SeekStatus {
            iterator = terms.tailMap(text).entries.iterator()
            return if (!iterator.hasNext()) {
                SeekStatus.END
            } else {
                if (next() == text) SeekStatus.FOUND else SeekStatus.NOT_FOUND
            }
        }

        @Throws(IOException::class)
        override fun seekExact(ord: Long) {
            throw UnsupportedOperationException()
        }

        @Throws(IOException::class)
        override fun next(): BytesRef? {
            return if (!iterator.hasNext()) {
                null
            } else {
                current = iterator.next()
                current.key
            }
        }

        @Throws(IOException::class)
        override fun term(): BytesRef {
            return current.key
        }

        @Throws(IOException::class)
        override fun ord(): Long {
            throw UnsupportedOperationException()
        }

        @Throws(IOException::class)
        override fun docFreq(): Int {
            return 1
        }

        @Throws(IOException::class)
        override fun totalTermFreq(): Long {
            return current.value.freq.toLong()
        }

        @Throws(IOException::class)
        override fun postings(reuse: PostingsEnum?, flags: Int): PostingsEnum {

            if (PostingsEnum.featureRequested(flags, PostingsEnum.POSITIONS)) {
                val postings = current.value
                if (postings.positions != null || postings.startOffsets != null) {
                    // TODO: reuse
                    val e = SimpleTVPostingsEnum()
                    e.reset(postings.positions, postings.startOffsets, postings.endOffsets, postings.payloads)
                    return e
                }
            }

            // TODO: reuse
            val e = SimpleTVDocsEnum()
            e.reset(
                if (!PostingsEnum.featureRequested(flags, PostingsEnum.FREQS)) 1 else current.value.freq
            )
            return e
        }

        @Throws(IOException::class)
        override fun impacts(flags: Int): ImpactsEnum {
            return SlowImpactsEnum(postings(null, PostingsEnum.FREQS.toInt()))
        }
    }

    // note: these two enum classes are exactly like the Default impl...
    private class SimpleTVDocsEnum : PostingsEnum() {
        private var didNext = false
        private var doc = -1
        private var freq = -1

        @Throws(IOException::class)
        override fun freq(): Int {
            assert(freq != -1)
            return freq
        }

        @Throws(IOException::class)
        override fun nextPosition(): Int {
            return -1
        }

        @Throws(IOException::class)
        override fun startOffset(): Int {
            return -1
        }

        @Throws(IOException::class)
        override fun endOffset(): Int {
            return -1
        }

        override val payload: BytesRef?
            get() = null

        override fun docID(): Int {
            return doc
        }

        override fun nextDoc(): Int {
            return if (!didNext) {
                didNext = true
                doc = 0
                doc
            } else {
                doc = NO_MORE_DOCS
                doc
            }
        }

        @Throws(IOException::class)
        override fun advance(target: Int): Int {
            return slowAdvance(target)
        }

        fun reset(freq: Int) {
            this.freq = freq
            this.doc = -1
            didNext = false
        }

        override fun cost(): Long {
            return 1
        }
    }

    private class SimpleTVPostingsEnum : PostingsEnum() {
        private var didNext = false
        private var doc = -1
        private var nextPos = 0
        private var positions: IntArray? = null
        private var payloads: Array<BytesRef?>? = null
        private var startOffsets: IntArray? = null
        private var endOffsets: IntArray? = null

        @Throws(IOException::class)
        override fun freq(): Int {
            return if (positions != null) {
                positions!!.size
            } else {
                requireNotNull(startOffsets)
                startOffsets!!.size
            }
        }

        override fun docID(): Int {
            return doc
        }

        override fun nextDoc(): Int {
            return if (!didNext) {
                didNext = true
                doc = 0
                doc
            } else {
                doc = NO_MORE_DOCS
                doc
            }
        }

        @Throws(IOException::class)
        override fun advance(target: Int): Int {
            return slowAdvance(target)
        }

        fun reset(
            positions: IntArray?,
            startOffsets: IntArray?,
            endOffsets: IntArray?,
            payloads: Array<BytesRef?>?
        ) {
            this.positions = positions
            this.startOffsets = startOffsets
            this.endOffsets = endOffsets
            this.payloads = payloads
            this.doc = -1
            didNext = false
            nextPos = 0
        }

        override val payload: BytesRef?
            get() = if (payloads == null) null else payloads!![nextPos - 1]

        override fun nextPosition(): Int {
            return if (positions != null) {
                assert(nextPos < positions!!.size) { "nextPosition() called more than freq() times!" }
                positions!![nextPos++]
            } else {
                assert(nextPos < startOffsets!!.size) { "nextPosition() called more than freq() times!" }
                nextPos++
                -1
            }
        }

        override fun startOffset(): Int {
            return if (startOffsets == null) -1 else startOffsets!![nextPos - 1]
        }

        override fun endOffset(): Int {
            return if (endOffsets == null) -1 else endOffsets!![nextPos - 1]
        }

        override fun cost(): Long {
            return 1
        }
    }

    override fun toString(): String {
        return this::class.simpleName ?: "SimpleTextTermVectorsReader"
    }

    @Throws(IOException::class)
    override fun checkIntegrity() {}
}
