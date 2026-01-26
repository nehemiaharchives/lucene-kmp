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
import org.gnit.lucenekmp.codecs.FieldsProducer
import org.gnit.lucenekmp.index.BaseTermsEnum
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.FieldInfos
import org.gnit.lucenekmp.index.Impacts
import org.gnit.lucenekmp.index.ImpactsEnum
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.index.IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS
import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.SlowImpactsEnum
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.jdkport.StandardCharsets
import org.gnit.lucenekmp.jdkport.TreeMap
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.fromByteArray
import org.gnit.lucenekmp.store.BufferedChecksumIndexInput
import org.gnit.lucenekmp.store.ChecksumIndexInput
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.CharsRefBuilder
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.IntsRefBuilder
import org.gnit.lucenekmp.util.StringHelper
import org.gnit.lucenekmp.util.fst.BytesRefFSTEnum
import org.gnit.lucenekmp.util.fst.FST
import org.gnit.lucenekmp.util.fst.FSTCompiler
import org.gnit.lucenekmp.util.fst.PairOutputs
import org.gnit.lucenekmp.util.fst.PositiveIntOutputs
import org.gnit.lucenekmp.util.fst.Util

internal class SimpleTextFieldsReader(state: SegmentReadState) : FieldsProducer() {

    private val fields: TreeMap<String, Long>
    private val `in`: IndexInput = state.directory.openInput(
        SimpleTextPostingsFormat.getPostingsFileName(
            state.segmentInfo.name,
            state.segmentSuffix
        ),
        state.context
    )
    private val fieldInfos: FieldInfos = state.fieldInfos
    private val maxDoc: Int = state.segmentInfo.maxDoc()

    init {
        var success = false
        try {
            fields = readFields(`in`.clone())
            success = true
        } finally {
            if (!success) {
                IOUtils.closeWhileHandlingException(this)
            }
        }
    }

    @Throws(IOException::class)
    private fun readFields(`in`: IndexInput): TreeMap<String, Long> {
        val input: ChecksumIndexInput = BufferedChecksumIndexInput(`in`)
        val scratch = BytesRefBuilder()
        val fields = TreeMap<String, Long>()

        while (true) {
            SimpleTextUtil.readLine(input, scratch)
            if (scratch.get() == SimpleTextFieldsWriter.END) {
                SimpleTextUtil.checkFooter(input)
                return fields
            } else if (StringHelper.startsWith(scratch.get(), SimpleTextFieldsWriter.FIELD)) {
                val start = SimpleTextFieldsWriter.FIELD.length
                val len = scratch.length() - SimpleTextFieldsWriter.FIELD.length
                val bytes = scratch.bytes().copyOfRange(start, start + len)
                val fieldName = String.fromByteArray(bytes, StandardCharsets.UTF_8)
                fields[fieldName] = input.filePointer
            }
        }
    }

    private inner class SimpleTextTermsEnum(
        fst: FST<PairOutputs.Pair<PairOutputs.Pair<Long, Long>, PairOutputs.Pair<Long, Long>>>,
        private val indexOptions: IndexOptions
    ) : BaseTermsEnum() {
        private var docFreq = 0
        private var totalTermFreq = 0L
        private var docsStart = 0L
        private var skipPointer = 0L
        private var ended = false
        private val fstEnum = BytesRefFSTEnum(fst)

        @Throws(IOException::class)
        override fun seekExact(text: BytesRef): Boolean {

            val result:
                BytesRefFSTEnum.InputOutput<
                    PairOutputs.Pair<PairOutputs.Pair<Long, Long>, PairOutputs.Pair<Long, Long>>
                    > = fstEnum.seekExact(text) ?: return false
            val pair = result.output!!
            val pair1 = pair.output1
            val pair2 = pair.output2
            docsStart = pair1.output1
            skipPointer = pair1.output2
            docFreq = pair2.output1.toInt()
            totalTermFreq = pair2.output2
            return true
        }

        @Throws(IOException::class)
        override fun seekCeil(text: BytesRef): SeekStatus {

            // System.out.println("seek to text=" + text.utf8ToString());
            val result:
                BytesRefFSTEnum.InputOutput<
                    PairOutputs.Pair<PairOutputs.Pair<Long, Long>, PairOutputs.Pair<Long, Long>>
                    > = fstEnum.seekCeil(text) ?: return SeekStatus.END
            // System.out.println("  got text=" + term.utf8ToString());
            val pair = result.output!!
            val pair1 = pair.output1
            val pair2 = pair.output2
            docsStart = pair1.output1
            skipPointer = pair1.output2
            docFreq = pair2.output1.toInt()
            totalTermFreq = pair2.output2

            return if (result.input == text) {
                // System.out.println("  match docsStart=" + docsStart);
                SeekStatus.FOUND
            } else {
                // System.out.println("  not match docsStart=" + docsStart);
                SeekStatus.NOT_FOUND
            }
        }

        @Throws(IOException::class)
        override fun next(): BytesRef? {
            assert(!ended)
            val result:
                BytesRefFSTEnum.InputOutput<
                    PairOutputs.Pair<PairOutputs.Pair<Long, Long>, PairOutputs.Pair<Long, Long>>
                    > = fstEnum.next() ?: return null
            val pair = result.output!!
            val pair1 = pair.output1
            val pair2 = pair.output2
            docsStart = pair1.output1
            skipPointer = pair1.output2
            docFreq = pair2.output1.toInt()
            totalTermFreq = pair2.output2
            return result.input
        }

        override fun term(): BytesRef? {
            return fstEnum.current().input
        }

        @Throws(IOException::class)
        override fun ord(): Long {
            throw UnsupportedOperationException()
        }

        override fun seekExact(ord: Long) {
            throw UnsupportedOperationException()
        }

        override fun docFreq(): Int {
            return docFreq
        }

        override fun totalTermFreq(): Long {
            return if (indexOptions == IndexOptions.DOCS) docFreq.toLong() else totalTermFreq
        }

        @Throws(IOException::class)
        override fun postings(reuse: PostingsEnum?, flags: Int): PostingsEnum {

            val hasPositions = indexOptions >= IndexOptions.DOCS_AND_FREQS_AND_POSITIONS
            if (hasPositions && PostingsEnum.featureRequested(flags, PostingsEnum.POSITIONS)) {

                val docsAndPositionsEnum: SimpleTextPostingsEnum =
                    if (reuse is SimpleTextPostingsEnum && reuse.canReuse(this@SimpleTextFieldsReader.`in`)) {
                        reuse
                    } else {
                        SimpleTextPostingsEnum()
                    }
                return docsAndPositionsEnum.reset(docsStart, indexOptions, docFreq, skipPointer)
            }

            val docsEnum: SimpleTextDocsEnum =
                if (reuse is SimpleTextDocsEnum && reuse.canReuse(this@SimpleTextFieldsReader.`in`)) {
                    reuse
                } else {
                    SimpleTextDocsEnum()
                }
            return docsEnum.reset(docsStart, indexOptions == IndexOptions.DOCS, docFreq, skipPointer)
        }

        @Throws(IOException::class)
        override fun impacts(flags: Int): ImpactsEnum {
            if (docFreq <= SimpleTextSkipWriter.BLOCK_SIZE) {
                // no skip data
                return SlowImpactsEnum(postings(null, flags))
            }
            return postings(null, flags) as ImpactsEnum
        }
    }

    private inner class SimpleTextDocsEnum : ImpactsEnum() {
        private val inStart: IndexInput = this@SimpleTextFieldsReader.`in`
        private val `in`: IndexInput = inStart.clone()
        private var omitTF = false
        private var docID = -1
        private var tf = 0
        private val scratch = BytesRefBuilder()
        private val scratchUTF16 = CharsRefBuilder()
        private var cost = 0

        // for skip list data
        private val skipReader = SimpleTextSkipReader(inStart.clone())
        private var nextSkipDoc = 0
        private var seekTo = -1L

        fun canReuse(`in`: IndexInput): Boolean {
            return `in` == inStart
        }

        @Throws(IOException::class)
        fun reset(fp: Long, omitTF: Boolean, docFreq: Int, skipPointer: Long): SimpleTextDocsEnum {
            `in`.seek(fp)
            this.omitTF = omitTF
            docID = -1
            tf = 1
            cost = docFreq
            skipReader.reset(skipPointer, docFreq)
            nextSkipDoc = 0
            seekTo = -1
            return this
        }

        override fun docID(): Int {
            return docID
        }

        @Throws(IOException::class)
        override fun freq(): Int {
            return tf
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

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            return advance(docID + 1)
        }

        @Throws(IOException::class)
        private fun readDoc(): Int {
            if (docID == NO_MORE_DOCS) {
                return docID
            }
            var first = true
            var termFreq = 0
            while (true) {
                val lineStart = `in`.filePointer
                SimpleTextUtil.readLine(`in`, scratch)
                if (StringHelper.startsWith(scratch.get(), SimpleTextFieldsWriter.DOC)) {
                    if (!first) {
                        `in`.seek(lineStart)
                        if (!omitTF) {
                            tf = termFreq
                        }
                        return docID
                    }
                    scratchUTF16.copyUTF8Bytes(
                        scratch.bytes(),
                        SimpleTextFieldsWriter.DOC.length,
                        scratch.length() - SimpleTextFieldsWriter.DOC.length
                    )
                    docID = ArrayUtil.parseInt(scratchUTF16.chars(), 0, scratchUTF16.length())
                    termFreq = 0
                    first = false
                } else if (StringHelper.startsWith(scratch.get(), SimpleTextFieldsWriter.FREQ)) {
                    scratchUTF16.copyUTF8Bytes(
                        scratch.bytes(),
                        SimpleTextFieldsWriter.FREQ.length,
                        scratch.length() - SimpleTextFieldsWriter.FREQ.length
                    )
                    termFreq = ArrayUtil.parseInt(scratchUTF16.chars(), 0, scratchUTF16.length())
                } else if (StringHelper.startsWith(scratch.get(), SimpleTextFieldsWriter.POS)) {
                    // skip termFreq++;
                } else if (StringHelper.startsWith(scratch.get(), SimpleTextFieldsWriter.START_OFFSET)) {
                    // skip
                } else if (StringHelper.startsWith(scratch.get(), SimpleTextFieldsWriter.END_OFFSET)) {
                    // skip
                } else if (StringHelper.startsWith(scratch.get(), SimpleTextFieldsWriter.PAYLOAD)) {
                    // skip
                } else {
                    assert(
                        StringHelper.startsWith(scratch.get(), SimpleTextSkipWriter.SKIP_LIST) ||
                            StringHelper.startsWith(scratch.get(), SimpleTextFieldsWriter.TERM) ||
                            StringHelper.startsWith(scratch.get(), SimpleTextFieldsWriter.FIELD) ||
                            StringHelper.startsWith(scratch.get(), SimpleTextFieldsWriter.END)
                    ) { "scratch=${scratch.get().utf8ToString()}" }
                    if (!first) {
                        `in`.seek(lineStart)
                        if (!omitTF) {
                            tf = termFreq
                        }
                        return docID
                    }
                    docID = NO_MORE_DOCS
                    return docID
                }
            }
        }

        @Throws(IOException::class)
        private fun advanceTarget(target: Int): Int {
            if (seekTo > 0) {
                `in`.seek(seekTo)
                seekTo = -1
            }
            assert(docID() < target)
            var doc: Int
            do {
                doc = readDoc()
            } while (doc < target)
            return doc
        }

        @Throws(IOException::class)
        override fun advance(target: Int): Int {
            advanceShallow(target)
            return advanceTarget(target)
        }

        override fun cost(): Long {
            return cost.toLong()
        }

        @Throws(IOException::class)
        override fun advanceShallow(target: Int) {
            if (target > nextSkipDoc) {
                skipReader.skipTo(target)
                if (skipReader.getNextSkipDoc() != NO_MORE_DOCS) {
                    seekTo = skipReader.getNextSkipDocFP()
                }
                nextSkipDoc = skipReader.getNextSkipDoc()
            }
            assert(nextSkipDoc >= target)
        }

        override val impacts: Impacts
            get() {
                advanceShallow(docID)
                return skipReader.getImpacts()
            }
    }

    private inner class SimpleTextPostingsEnum : ImpactsEnum() {
        private val inStart: IndexInput = this@SimpleTextFieldsReader.`in`
        private val `in`: IndexInput = inStart.clone()
        private var docID = -1
        private var tf = 0
        private val scratch = BytesRefBuilder()
        private val scratch2 = BytesRefBuilder()
        private val scratchUTF16 = CharsRefBuilder()
        private val scratchUTF16_2 = CharsRefBuilder()
        private var pos = 0
        private var payloadValue: BytesRef? = null
        private var nextDocStart = 0L
        private var readOffsets = false
        private var readPositions = false
        private var startOffset = 0
        private var endOffset = 0
        private var cost = 0

        // for skip list data
        private val skipReader = SimpleTextSkipReader(inStart.clone())
        private var nextSkipDoc = 0
        private var seekTo = -1L

        fun canReuse(`in`: IndexInput): Boolean {
            return `in` == inStart
        }

        @Throws(IOException::class)
        fun reset(
            fp: Long,
            indexOptions: IndexOptions,
            docFreq: Int,
            skipPointer: Long
        ): SimpleTextPostingsEnum {
            nextDocStart = fp
            docID = -1
            readPositions =
                indexOptions >= IndexOptions.DOCS_AND_FREQS_AND_POSITIONS
            readOffsets =
                indexOptions >= DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS
            if (!readOffsets) {
                startOffset = -1
                endOffset = -1
            }
            cost = docFreq
            skipReader.reset(skipPointer, docFreq)
            nextSkipDoc = 0
            seekTo = -1
            return this
        }

        override fun docID(): Int {
            return docID
        }

        @Throws(IOException::class)
        override fun freq(): Int {
            return tf
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            return advance(docID + 1)
        }

        @Throws(IOException::class)
        private fun readDoc(): Int {
            var first = true
            `in`.seek(nextDocStart)
            var posStart = 0L
            while (true) {
                val lineStart = `in`.filePointer
                SimpleTextUtil.readLine(`in`, scratch)
                // System.out.println("NEXT DOC: " + scratch.utf8ToString());
                if (StringHelper.startsWith(scratch.get(), SimpleTextFieldsWriter.DOC)) {
                    if (!first) {
                        nextDocStart = lineStart
                        `in`.seek(posStart)
                        return docID
                    }
                    scratchUTF16.copyUTF8Bytes(
                        scratch.bytes(),
                        SimpleTextFieldsWriter.DOC.length,
                        scratch.length() - SimpleTextFieldsWriter.DOC.length
                    )
                    docID = ArrayUtil.parseInt(scratchUTF16.chars(), 0, scratchUTF16.length())
                    tf = 0
                    first = false
                } else if (StringHelper.startsWith(scratch.get(), SimpleTextFieldsWriter.FREQ)) {
                    scratchUTF16.copyUTF8Bytes(
                        scratch.bytes(),
                        SimpleTextFieldsWriter.FREQ.length,
                        scratch.length() - SimpleTextFieldsWriter.FREQ.length
                    )
                    tf = ArrayUtil.parseInt(scratchUTF16.chars(), 0, scratchUTF16.length())
                    posStart = `in`.filePointer
                } else if (StringHelper.startsWith(scratch.get(), SimpleTextFieldsWriter.POS)) {
                    // skip
                } else if (StringHelper.startsWith(scratch.get(), SimpleTextFieldsWriter.START_OFFSET)) {
                    // skip
                } else if (StringHelper.startsWith(scratch.get(), SimpleTextFieldsWriter.END_OFFSET)) {
                    // skip
                } else if (StringHelper.startsWith(scratch.get(), SimpleTextFieldsWriter.PAYLOAD)) {
                    // skip
                } else {
                    assert(
                        StringHelper.startsWith(scratch.get(), SimpleTextSkipWriter.SKIP_LIST) ||
                            StringHelper.startsWith(scratch.get(), SimpleTextFieldsWriter.TERM) ||
                            StringHelper.startsWith(scratch.get(), SimpleTextFieldsWriter.FIELD) ||
                            StringHelper.startsWith(scratch.get(), SimpleTextFieldsWriter.END)
                    )
                    if (!first) {
                        nextDocStart = lineStart
                        `in`.seek(posStart)
                        return docID
                    }
                    docID = NO_MORE_DOCS
                    return docID
                }
            }
        }

        @Throws(IOException::class)
        private fun advanceTarget(target: Int): Int {
            if (seekTo > 0) {
                nextDocStart = seekTo
                seekTo = -1
            }
            assert(docID() < target)
            var doc: Int
            do {
                doc = readDoc()
            } while (doc < target)
            return doc
        }

        @Throws(IOException::class)
        override fun advance(target: Int): Int {
            advanceShallow(target)
            return advanceTarget(target)
        }

        @Throws(IOException::class)
        override fun nextPosition(): Int {
            if (readPositions) {
                SimpleTextUtil.readLine(`in`, scratch)
                assert(StringHelper.startsWith(scratch.get(), SimpleTextFieldsWriter.POS)) {
                    "got line=${scratch.get().utf8ToString()}"
                }
                scratchUTF16_2.copyUTF8Bytes(
                    scratch.bytes(),
                    SimpleTextFieldsWriter.POS.length,
                    scratch.length() - SimpleTextFieldsWriter.POS.length
                )
                pos = ArrayUtil.parseInt(scratchUTF16_2.chars(), 0, scratchUTF16_2.length())
            } else {
                pos = -1
            }

            if (readOffsets) {
                SimpleTextUtil.readLine(`in`, scratch)
                assert(StringHelper.startsWith(scratch.get(), SimpleTextFieldsWriter.START_OFFSET)) {
                    "got line=${scratch.get().utf8ToString()}"
                }
                scratchUTF16_2.copyUTF8Bytes(
                    scratch.bytes(),
                    SimpleTextFieldsWriter.START_OFFSET.length,
                    scratch.length() - SimpleTextFieldsWriter.START_OFFSET.length
                )
                startOffset =
                    ArrayUtil.parseInt(scratchUTF16_2.chars(), 0, scratchUTF16_2.length())
                SimpleTextUtil.readLine(`in`, scratch)
                assert(StringHelper.startsWith(scratch.get(), SimpleTextFieldsWriter.END_OFFSET)) {
                    "got line=${scratch.get().utf8ToString()}"
                }
                scratchUTF16_2.grow(scratch.length() - SimpleTextFieldsWriter.END_OFFSET.length)
                scratchUTF16_2.copyUTF8Bytes(
                    scratch.bytes(),
                    SimpleTextFieldsWriter.END_OFFSET.length,
                    scratch.length() - SimpleTextFieldsWriter.END_OFFSET.length
                )
                endOffset =
                    ArrayUtil.parseInt(scratchUTF16_2.chars(), 0, scratchUTF16_2.length())
            }

            val fp = `in`.filePointer
            SimpleTextUtil.readLine(`in`, scratch)
            if (StringHelper.startsWith(scratch.get(), SimpleTextFieldsWriter.PAYLOAD)) {
                val len = scratch.length() - SimpleTextFieldsWriter.PAYLOAD.length
                scratch2.growNoCopy(len)
                scratch.bytes().copyInto(
                    scratch2.bytes(),
                    0,
                    SimpleTextFieldsWriter.PAYLOAD.length,
                    SimpleTextFieldsWriter.PAYLOAD.length + len
                )
                scratch2.setLength(len)
                payloadValue = scratch2.get()
            } else {
                payloadValue = null
                `in`.seek(fp)
            }
            return pos
        }

        @Throws(IOException::class)
        override fun startOffset(): Int {
            return startOffset
        }

        @Throws(IOException::class)
        override fun endOffset(): Int {
            return endOffset
        }

        override val payload: BytesRef?
            get() = payloadValue

        override fun cost(): Long {
            return cost.toLong()
        }

        @Throws(IOException::class)
        override fun advanceShallow(target: Int) {
            if (target > nextSkipDoc) {
                skipReader.skipTo(target)
                if (skipReader.getNextSkipDoc() != NO_MORE_DOCS) {
                    seekTo = skipReader.getNextSkipDocFP()
                }
            }
            nextSkipDoc = skipReader.getNextSkipDoc()
            assert(nextSkipDoc >= target)
        }

        override val impacts: Impacts
            get() {
                advanceShallow(docID)
                return skipReader.getImpacts()
            }
    }

    private inner class SimpleTextTerms(field: String, private val termsStart: Long,
                                        private val maxDoc: Int
    ) : Terms() {
        private val fieldInfo: FieldInfo? = fieldInfos.fieldInfo(field)
        override var sumTotalTermFreq = 0L
        override var sumDocFreq = 0L
        override var docCount = 0
        private var fst: FST<
            PairOutputs.Pair<PairOutputs.Pair<Long, Long>, PairOutputs.Pair<Long, Long>>
            >? = null
        private var termCount = 0
        private val scratch = BytesRefBuilder()
        private val scratchUTF16 = CharsRefBuilder()

        init {
            loadTerms()
        }

        @Throws(IOException::class)
        private fun loadTerms() {
            val posIntOutputs = PositiveIntOutputs.singleton
            val fstCompiler: FSTCompiler<
                PairOutputs.Pair<PairOutputs.Pair<Long, Long>, PairOutputs.Pair<Long, Long>>
                >
            val outputsOuter = PairOutputs(posIntOutputs, posIntOutputs)
            val outputsInner = PairOutputs(posIntOutputs, posIntOutputs)
            val outputs = PairOutputs(outputsOuter, outputsInner)
            fstCompiler = FSTCompiler.Builder(FST.INPUT_TYPE.BYTE1, outputs).build()
            val `in` = this@SimpleTextFieldsReader.`in`.clone()
            `in`.seek(termsStart)
            val lastTerm = BytesRefBuilder()
            var lastDocsStart = -1L
            var docFreq = 0
            var totalTermFreq = 0L
            var skipPointer = 0L
            val visitedDocs = FixedBitSet(maxDoc)
            val scratchIntsRef = IntsRefBuilder()
            while (true) {
                SimpleTextUtil.readLine(`in`, scratch)
                if (scratch.get() == SimpleTextFieldsWriter.END ||
                    StringHelper.startsWith(scratch.get(), SimpleTextFieldsWriter.FIELD)
                ) {
                    if (lastDocsStart != -1L) {
                        fstCompiler.add(
                            Util.toIntsRef(lastTerm.get(), scratchIntsRef),
                            outputs.newPair(
                                outputsOuter.newPair(lastDocsStart, skipPointer),
                                outputsInner.newPair(docFreq.toLong(), totalTermFreq)
                            )
                        )
                        sumTotalTermFreq += totalTermFreq
                    }
                    break
                } else if (StringHelper.startsWith(scratch.get(), SimpleTextFieldsWriter.DOC)) {
                    docFreq++
                    sumDocFreq++
                    totalTermFreq++
                    scratchUTF16.copyUTF8Bytes(
                        scratch.bytes(),
                        SimpleTextFieldsWriter.DOC.length,
                        scratch.length() - SimpleTextFieldsWriter.DOC.length
                    )
                    val docID = ArrayUtil.parseInt(scratchUTF16.chars(), 0, scratchUTF16.length())
                    visitedDocs.set(docID)
                } else if (StringHelper.startsWith(scratch.get(), SimpleTextFieldsWriter.FREQ)) {
                    scratchUTF16.copyUTF8Bytes(
                        scratch.bytes(),
                        SimpleTextFieldsWriter.FREQ.length,
                        scratch.length() - SimpleTextFieldsWriter.FREQ.length
                    )
                    totalTermFreq +=
                        ArrayUtil.parseInt(scratchUTF16.chars(), 0, scratchUTF16.length()) - 1
                } else if (StringHelper.startsWith(scratch.get(), SimpleTextSkipWriter.SKIP_LIST)) {
                    skipPointer = `in`.filePointer
                } else if (StringHelper.startsWith(scratch.get(), SimpleTextFieldsWriter.TERM)) {
                    if (lastDocsStart != -1L) {
                        fstCompiler.add(
                            Util.toIntsRef(lastTerm.get(), scratchIntsRef),
                            outputs.newPair(
                                outputsOuter.newPair(lastDocsStart, skipPointer),
                                outputsInner.newPair(docFreq.toLong(), totalTermFreq)
                            )
                        )
                    }
                    lastDocsStart = `in`.filePointer
                    val len = scratch.length() - SimpleTextFieldsWriter.TERM.length
                    lastTerm.growNoCopy(len)
                    scratch.bytes().copyInto(
                        lastTerm.bytes(),
                        0,
                        SimpleTextFieldsWriter.TERM.length,
                        SimpleTextFieldsWriter.TERM.length + len
                    )
                    lastTerm.setLength(len)
                    docFreq = 0
                    sumTotalTermFreq += totalTermFreq
                    totalTermFreq = 0
                    termCount++
                    skipPointer = 0
                }
            }
            docCount = visitedDocs.cardinality()
            fst = FST.fromFSTReader(fstCompiler.compile(), fstCompiler.getFSTReader())
            /*
            PrintStream ps = new PrintStream("out.dot");
            fst.toDot(ps);
            ps.close();
            System.out.println("SAVED out.dot");
            */
            // System.out.println("FST " + fst.sizeInBytes());
        }

        override fun toString(): String {
            return (
                this::class.simpleName +
                    "(terms=" +
                    termCount +
                    ",postings=" +
                    sumDocFreq +
                    ",positions=" +
                    sumTotalTermFreq +
                    ",docs=" +
                    docCount +
                    ")"
                )
        }

        @Throws(IOException::class)
        override fun iterator(): TermsEnum {
            return if (fst != null) {
                SimpleTextTermsEnum(fst!!, fieldInfo!!.indexOptions)
            } else {
                TermsEnum.EMPTY
            }
        }

        override fun size(): Long {
            return termCount.toLong()
        }

        /*override fun sumTotalTermFreq(): Long {
            return sumTotalTermFreq
        }

        @Throws(IOException::class)
        override fun sumDocFreq(): Long {
            return sumDocFreq
        }

        @Throws(IOException::class)
        override fun docCount(): Int {
            return docCount
        }*/

        override fun hasFreqs(): Boolean {
            return fieldInfo!!.indexOptions >= IndexOptions.DOCS_AND_FREQS
        }

        override fun hasOffsets(): Boolean {
            return fieldInfo!!.indexOptions >= DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS
        }

        override fun hasPositions(): Boolean {
            return fieldInfo!!.indexOptions >= IndexOptions.DOCS_AND_FREQS_AND_POSITIONS
        }

        override fun hasPayloads(): Boolean {
            return fieldInfo!!.hasPayloads()
        }
    }

    override fun iterator(): MutableIterator<String> {
        return fields.keys.iterator()
    }

    private val termsCache: MutableMap<String, SimpleTextTerms> = HashMap()

    /*@Synchronized*/
    @Throws(IOException::class)
    override fun terms(field: String?): Terms? {
        var terms = termsCache[field]
        if (terms == null) {
            val fp = fields[field]
            if (fp == null) {
                return null
            } else {
                terms = SimpleTextTerms(field!!, fp, maxDoc)
                termsCache[field] = terms
            }
        }
        return terms
    }

    override fun size(): Int {
        return -1
    }

    override fun close() {
        `in`.close()
    }

    override fun toString(): String {
        return this::class.simpleName + "(fields=" + fields.size + ")"
    }

    @Throws(IOException::class)
    override fun checkIntegrity() {}
}
