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
package org.gnit.lucenekmp.index

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.codecs.FieldsConsumer
import org.gnit.lucenekmp.codecs.NormsProducer
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field.Store
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.InfoStream
import org.gnit.lucenekmp.util.StringHelper
import org.gnit.lucenekmp.util.Version
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.concurrent.Volatile

// TODO: test multiple codecs here?

// TODO
//   - test across fields
//   - fix this test to run once for all codecs
//   - make more docs per term, to test > 1 level skipping
//   - test all combinations of payloads/not and omitTF/not
//   - test w/ different indexDivisor
//   - test field where payload length rarely changes
//   - 0-term fields
//   - seek/skip to same term/doc i'm already on
//   - mix in deleted docs
//   - seek, skip beyond end -- assert returns false
//   - seek, skip to things that don't exist -- ensure it
//     goes to 1 before next one known to exist
//   - skipTo(term)
//   - skipTo(doc)

class TestCodecs : LuceneTestCase() {
    companion object {
        private val fieldNames = arrayOf("one", "two", "three", "four")

        private const val NUM_TEST_THREADS = 3
        private const val NUM_FIELDS = 4
        private const val NUM_TERMS_RAND = 50 // must be > 16 to test skipping
        private const val DOC_FREQ_RAND = 500 // must be > 16 to test skipping
        private const val TERM_DOC_FREQ_RAND = 20
        private const val SEGMENT = "0"
    }

    class FieldData(
        name: String,
        fieldInfos: FieldInfos.Builder,
        val terms: Array<TermData>,
        val omitTF: Boolean,
        val storePayloads: Boolean
    ) : Comparable<FieldData> {
        val fieldInfo: FieldInfo

        init {
            // TODO: change this test to use all three
            val fieldInfo0 = fieldInfos.fieldInfo(name)
            if (fieldInfo0 != null) {
                fieldInfo = fieldInfo0
            } else {
                val indexOptions =
                    if (omitTF) {
                        IndexOptions.DOCS
                    } else {
                        IndexOptions.DOCS_AND_FREQS_AND_POSITIONS
                    }
                fieldInfo =
                    fieldInfos.add(
                        FieldInfo(
                            name,
                            -1,
                            false,
                            false,
                            storePayloads,
                            indexOptions,
                            DocValuesType.NONE,
                            DocValuesSkipIndexType.NONE,
                            -1,
                            mutableMapOf(),
                            0,
                            0,
                            0,
                            0,
                            VectorEncoding.FLOAT32,
                            VectorSimilarityFunction.EUCLIDEAN,
                            false,
                            false
                        )
                    )
            }
            this.terms.forEach { it.field = this }
            this.terms.sort()
        }

        override fun compareTo(other: FieldData): Int {
            return fieldInfo.name.compareTo(other.fieldInfo.name)
        }
    }

    class PositionData(var pos: Int, var payload: BytesRef?)

    class TermData(text: String, var docs: IntArray, var positions: Array<Array<PositionData>>?) :
        Comparable<TermData> {
        var text2: String = text
        val text: BytesRef = BytesRef(text)
        lateinit var field: FieldData

        override fun compareTo(o: TermData): Int {
            return text.compareTo(o.text)
        }
    }

    fun makeRandomTerms(omitTF: Boolean, storePayloads: Boolean): Array<TermData> {
        val numTerms = 1 + random().nextInt(NUM_TERMS_RAND)
        val terms = arrayOfNulls<TermData>(numTerms)

        val termsSeen = hashSetOf<String>()

        for (i in 0 until numTerms) {
            var text2: String
            while (true) {
                text2 = TestUtil.randomUnicodeString(random())
                if (!termsSeen.contains(text2) && !text2.endsWith(".")) {
                    termsSeen.add(text2)
                    break
                }
            }

            val docFreq = 1 + random().nextInt(DOC_FREQ_RAND)
            val docs = IntArray(docFreq)
            val positions: Array<Array<PositionData>>? =
                if (!omitTF) {
                    arrayOfNulls<Array<PositionData>>(docFreq) as Array<Array<PositionData>>
                } else {
                    null
                }

            var docID = 0
            for (j in 0 until docFreq) {
                docID += TestUtil.nextInt(random(), 1, 10)
                docs[j] = docID

                if (!omitTF) {
                    val termFreq = 1 + random().nextInt(TERM_DOC_FREQ_RAND)
                    val positionArray = arrayOfNulls<PositionData>(termFreq) as Array<PositionData>
                    var position = 0
                    for (k in 0 until termFreq) {
                        position += TestUtil.nextInt(random(), 1, 10)

                        val payload =
                            if (storePayloads && random().nextInt(4) == 0) {
                                val bytes = ByteArray(1 + random().nextInt(5))
                                for (l in bytes.indices) {
                                    bytes[l] = random().nextInt(255).toByte()
                                }
                                BytesRef(bytes)
                            } else {
                                null
                            }

                        positionArray[k] = PositionData(position, payload)
                    }
                    positions!![j] = positionArray
                }
            }

            terms[i] = TermData(text2, docs, positions)
        }

        return terms.requireNoNulls()
    }

    @Test
    @Throws(Throwable::class)
    fun testFixedPostings() {
        val NUM_TERMS = 100
        val terms = Array(NUM_TERMS) { i ->
            val docs = intArrayOf(i)
            val text = i.toString(Character.MAX_RADIX.coerceIn(2, 36))
            TermData(text, docs, null)
        }

        val builder = FieldInfos.Builder(FieldInfos.FieldNumbers(null, null))

        val field = FieldData("field", builder, terms, true, false)
        val fields = arrayOf(field)
        val fieldInfos = builder.finish()
        val dir = newDirectory()
        val codec = Codec.default
        val si =
            SegmentInfo(
                dir,
                Version.LATEST,
                Version.LATEST,
                SEGMENT,
                10000,
                false,
                false,
                codec,
                mutableMapOf(),
                StringHelper.randomId(),
                mutableMapOf(),
                null
            )

        write(si, fieldInfos, dir, fields)
        val reader =
            codec.postingsFormat()
                .fieldsProducer(SegmentReadState(dir, si, fieldInfos, newIOContext(random())))

        val fieldsEnum = reader.iterator()
        val fieldName = fieldsEnum.next()
        assertNotNull(fieldName)
        val terms2 = requireNotNull(reader.terms(fieldName))

        val termsEnum = terms2.iterator()

        var postingsEnum: PostingsEnum? = null
        for (i in 0 until NUM_TERMS) {
            val term = termsEnum.next()
            assertNotNull(term)
            assertEquals(terms[i].text2, term.utf8ToString())

            for (iter in 0 until 2) {
                postingsEnum =
                    TestUtil.docs(random(), termsEnum, postingsEnum, PostingsEnum.NONE.toInt())
                assertEquals(terms[i].docs[0], postingsEnum!!.nextDoc())
                assertEquals(DocIdSetIterator.NO_MORE_DOCS, postingsEnum!!.nextDoc())
            }
        }
        assertNull(termsEnum.next())

        for (i in 0 until NUM_TERMS) {
            assertEquals(TermsEnum.SeekStatus.FOUND, termsEnum.seekCeil(BytesRef(terms[i].text2)))
        }

        assertFalse(fieldsEnum.hasNext())
        reader.close()
        dir.close()
    }

    @Test
    @Throws(Throwable::class)
    fun testRandomPostings() {
        val builder = FieldInfos.Builder(FieldInfos.FieldNumbers(null, null))

        val fields = Array(NUM_FIELDS) { i ->
            val omitTF = 0 == (i % 3)
            val storePayloads = 1 == (i % 3)
            FieldData(
                fieldNames[i],
                builder,
                makeRandomTerms(omitTF, storePayloads),
                omitTF,
                storePayloads
            )
        }

        val dir = newDirectory()
        val fieldInfos = builder.finish()

        if (VERBOSE) {
            println("TEST: now write postings")
        }

        val codec = Codec.default
        val si =
            SegmentInfo(
                dir,
                Version.LATEST,
                Version.LATEST,
                SEGMENT,
                10000,
                false,
                false,
                codec,
                mutableMapOf(),
                StringHelper.randomId(),
                mutableMapOf(),
                null
            )
        write(si, fieldInfos, dir, fields)

        if (VERBOSE) {
            println("TEST: now read postings")
        }
        val terms =
            codec.postingsFormat()
                .fieldsProducer(SegmentReadState(dir, si, fieldInfos, newIOContext(random())))
        val numTestIter = atLeast(20)

        val verifyWorkers = Array(NUM_TEST_THREADS - 1) { Verify(fields, terms, numTestIter) }
        runBlocking {
            val scope = CoroutineScope(Dispatchers.Default)
            val jobs = verifyWorkers.map { it.start(scope) }
            try {
                Verify(fields, terms, numTestIter).run()
                jobs.joinAll()
            } finally {
                for (job in jobs) {
                    if (job.isActive) {
                        job.cancelAndJoin()
                    }
                }
            }
        }

        for (worker in verifyWorkers) {
            assertFalse(worker.failed)
        }

        terms.close()
        dir.close()
    }

    private inner class Verify(
        private val fields: Array<FieldData>,
        private val termsDict: Fields,
        private val numTestIter: Int
    ) {
        @Volatile
        var failed: Boolean = false

        fun start(scope: CoroutineScope): Job {
            return scope.launch {
                run()
            }
        }

        suspend fun run() {
            try {
                _run()
            } catch (t: Throwable) {
                failed = true
                throw RuntimeException(t)
            }
        }

        private fun verifyDocs(
            docs: IntArray,
            positions: Array<Array<PositionData>>?,
            postingsEnum: PostingsEnum,
            doPos: Boolean
        ) {
            for (i in docs.indices) {
                val doc = postingsEnum.nextDoc()
                assertTrue(doc != DocIdSetIterator.NO_MORE_DOCS)
                assertEquals(docs[i], doc)
                if (doPos) {
                    verifyPositions(positions!![i], postingsEnum)
                }
            }
            assertEquals(DocIdSetIterator.NO_MORE_DOCS, postingsEnum.nextDoc())
        }

        private fun verifyPositions(positions: Array<PositionData>, posEnum: PostingsEnum) {
            for (i in positions.indices) {
                val pos = posEnum.nextPosition()
                assertEquals(positions[i].pos, pos)
                if (positions[i].payload != null) {
                    assertNotNull(posEnum.payload)
                    if (random().nextInt(3) < 2) {
                        val otherPayload = posEnum.payload
                        assertNotNull(otherPayload)
                        assertTrue(
                            positions[i].payload == otherPayload,
                            "expected=${positions[i].payload} got=$otherPayload"
                        )
                    }
                } else {
                    assertNull(posEnum.payload)
                }
            }
        }

        private suspend fun _run() {
            for (iter in 0 until numTestIter) {
                val field = fields[random().nextInt(fields.size)]
                val termsEnum = termsDict.terms(field.fieldInfo.name)!!.iterator()

                var upto = 0
                while (true) {
                    val term = termsEnum.next() ?: break
                    val expected = BytesRef(field.terms[upto++].text2)
                    assertTrue(expected.bytesEquals(term), "expected=$expected vs actual $term")
                }
                assertEquals(upto, field.terms.size)

                var term = field.terms[random().nextInt(field.terms.size)]
                var status = termsEnum.seekCeil(BytesRef(term.text2))
                assertEquals(TermsEnum.SeekStatus.FOUND, status)
                assertEquals(term.docs.size, termsEnum.docFreq())
                if (field.omitTF) {
                    verifyDocs(
                        term.docs,
                        term.positions,
                        TestUtil.docs(random(), termsEnum, null, PostingsEnum.NONE.toInt()),
                        false
                    )
                } else {
                    verifyDocs(term.docs, term.positions, termsEnum.postings(null, PostingsEnum.ALL.toInt())!!, true)
                }

                val idx = random().nextInt(field.terms.size)
                term = field.terms[idx]
                var success = false
                try {
                    termsEnum.seekExact(idx.toLong())
                    success = true
                } catch (_: UnsupportedOperationException) {
                }
                if (success) {
                    assertEquals(TermsEnum.SeekStatus.FOUND, status)
                    assertTrue(termsEnum.term()!!.bytesEquals(BytesRef(term.text2)))
                    assertEquals(term.docs.size, termsEnum.docFreq())
                    if (field.omitTF) {
                        verifyDocs(
                            term.docs,
                            term.positions,
                            TestUtil.docs(random(), termsEnum, null, PostingsEnum.NONE.toInt()),
                            false
                        )
                    } else {
                        verifyDocs(term.docs, term.positions, termsEnum.postings(null, PostingsEnum.ALL.toInt())!!, true)
                    }
                }

                if (VERBOSE) {
                    println("TEST: seek non-exist terms")
                }
                for (i in 0 until 100) {
                    val text2 = TestUtil.randomUnicodeString(random()) + "."
                    status = termsEnum.seekCeil(BytesRef(text2))
                    assertTrue(
                        status == TermsEnum.SeekStatus.NOT_FOUND || status == TermsEnum.SeekStatus.END
                    )
                }

                if (VERBOSE) {
                    println("TEST: seek terms backwards")
                }
                for (i in field.terms.size - 1 downTo 0) {
                    assertEquals(
                        TermsEnum.SeekStatus.FOUND,
                        termsEnum.seekCeil(BytesRef(field.terms[i].text2)),
                        "verify: field=${field.fieldInfo.name} term=${field.terms[i].text2}"
                    )
                    assertEquals(field.terms[i].docs.size, termsEnum.docFreq())
                }

                for (i in field.terms.size - 1 downTo 0) {
                    try {
                        termsEnum.seekExact(i.toLong())
                        assertEquals(field.terms[i].docs.size, termsEnum.docFreq())
                        assertTrue(termsEnum.term()!!.bytesEquals(BytesRef(field.terms[i].text2)))
                    } catch (_: UnsupportedOperationException) {
                    }
                }

                status = termsEnum.seekCeil(BytesRef(""))
                assertNotNull(status)
                assertTrue(termsEnum.term()!!.bytesEquals(BytesRef(field.terms[0].text2)))

                termsEnum.seekCeil(BytesRef(""))
                upto = 0
                do {
                    term = field.terms[upto]
                    if (random().nextInt(3) == 1) {
                        val postings =
                            if (!field.omitTF) {
                                termsEnum.postings(null, PostingsEnum.ALL.toInt())!!
                            } else {
                                TestUtil.docs(random(), termsEnum, null, PostingsEnum.FREQS.toInt())
                            }
                        assertNotNull(postings)
                        var upto2 = -1
                        var ended = false
                        while (upto2 < term.docs.size - 1) {
                            val left = term.docs.size - upto2
                            val doc: Int
                            if (random().nextInt(3) == 1 && left >= 1) {
                                val inc = 1 + random().nextInt(left - 1)
                                upto2 += inc
                                if (random().nextInt(2) == 1) {
                                    doc = postings.advance(term.docs[upto2])
                                    assertEquals(term.docs[upto2], doc)
                                } else {
                                    doc = postings.advance(1 + term.docs[upto2])
                                    if (doc == DocIdSetIterator.NO_MORE_DOCS) {
                                        assertTrue(upto2 == term.docs.size - 1)
                                        ended = true
                                        break
                                    } else {
                                        assertTrue(upto2 < term.docs.size - 1)
                                        if (doc >= term.docs[1 + upto2]) {
                                            upto2++
                                        }
                                    }
                                }
                            } else {
                                doc = postings.nextDoc()
                                assertTrue(doc != -1)
                                upto2++
                            }
                            assertEquals(term.docs[upto2], doc)
                            if (!field.omitTF) {
                                assertEquals(term.positions!![upto2].size, postings.freq())
                                if (random().nextInt(2) == 1) {
                                    verifyPositions(term.positions!![upto2], postings)
                                }
                            }
                        }

                        if (!ended) {
                            assertEquals(DocIdSetIterator.NO_MORE_DOCS, postings.nextDoc())
                        }
                    }
                    upto++
                } while (termsEnum.next() != null)

                assertEquals(upto, field.terms.size)
            }
        }
    }

    private class DataFields(private val fields: Array<FieldData>) : Fields() {
        override fun iterator(): MutableIterator<String> {
            return object : MutableIterator<String> {
                var upto = -1

                override fun hasNext(): Boolean {
                    return upto + 1 < fields.size
                }

                override fun next(): String {
                    upto++
                    return fields[upto].fieldInfo.name
                }

                override fun remove() {
                    throw UnsupportedOperationException()
                }
            }
        }

        override fun terms(field: String?): Terms? {
            for (fieldData in fields) {
                if (fieldData.fieldInfo.name == field) {
                    return DataTerms(fieldData)
                }
            }
            return null
        }

        override fun size(): Int {
            return fields.size
        }
    }

    private class DataTerms(val fieldData: FieldData) : Terms() {
        override fun iterator(): TermsEnum {
            return DataTermsEnum(fieldData)
        }

        override fun size(): Long {
            throw UnsupportedOperationException()
        }

        override val sumTotalTermFreq: Long
            get() = throw UnsupportedOperationException()

        override val sumDocFreq: Long
            get() = throw UnsupportedOperationException()

        override val docCount: Int
            get() = throw UnsupportedOperationException()

        override fun hasFreqs(): Boolean {
            return fieldData.fieldInfo.indexOptions >= IndexOptions.DOCS_AND_FREQS
        }

        override fun hasOffsets(): Boolean {
            return fieldData.fieldInfo.indexOptions >= IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS
        }

        override fun hasPositions(): Boolean {
            return fieldData.fieldInfo.indexOptions >= IndexOptions.DOCS_AND_FREQS_AND_POSITIONS
        }

        override fun hasPayloads(): Boolean {
            return fieldData.fieldInfo.hasPayloads()
        }
    }

    private class DataTermsEnum(val fieldData: FieldData) : BaseTermsEnum() {
        private var upto = -1

        override fun next(): BytesRef? {
            upto++
            if (upto == fieldData.terms.size) {
                return null
            }
            return term()
        }

        override fun term(): BytesRef {
            return fieldData.terms[upto].text
        }

        override fun seekCeil(text: BytesRef): TermsEnum.SeekStatus {
            for (i in fieldData.terms.indices) {
                val cmp = fieldData.terms[i].text.compareTo(text)
                if (cmp == 0) {
                    upto = i
                    return TermsEnum.SeekStatus.FOUND
                } else if (cmp > 0) {
                    upto = i
                    return TermsEnum.SeekStatus.NOT_FOUND
                }
            }
            return TermsEnum.SeekStatus.END
        }

        override fun seekExact(ord: Long) {
            throw UnsupportedOperationException()
        }

        override fun ord(): Long {
            throw UnsupportedOperationException()
        }

        override fun docFreq(): Int {
            throw UnsupportedOperationException()
        }

        override fun totalTermFreq(): Long {
            throw UnsupportedOperationException()
        }

        override fun postings(reuse: PostingsEnum?, flags: Int): PostingsEnum {
            return DataPostingsEnum(fieldData.terms[upto])
        }

        override fun impacts(flags: Int): ImpactsEnum {
            throw UnsupportedOperationException()
        }
    }

    private class DataPostingsEnum(val termData: TermData) : PostingsEnum() {
        var docUpto = -1
        var posUpto = 0

        override fun cost(): Long {
            throw UnsupportedOperationException()
        }

        override fun nextDoc(): Int {
            docUpto++
            if (docUpto == termData.docs.size) {
                return NO_MORE_DOCS
            }
            posUpto = -1
            return docID()
        }

        override fun docID(): Int {
            return when {
                docUpto < 0 -> -1
                docUpto >= termData.docs.size -> NO_MORE_DOCS
                else -> termData.docs[docUpto]
            }
        }

        override fun advance(target: Int): Int {
            nextDoc()
            while (docID() < target) {
                nextDoc()
            }
            return docID()
        }

        override fun freq(): Int {
            return termData.positions!![docUpto].size
        }

        override fun nextPosition(): Int {
            posUpto++
            return termData.positions!![docUpto][posUpto].pos
        }

        override val payload: BytesRef?
            get() = termData.positions!![docUpto][posUpto].payload

        override fun startOffset(): Int {
            throw UnsupportedOperationException()
        }

        override fun endOffset(): Int {
            throw UnsupportedOperationException()
        }
    }

    @Throws(Throwable::class)
    private fun write(
        si: SegmentInfo,
        fieldInfos: FieldInfos,
        dir: Directory,
        fields: Array<FieldData>
    ) {
        val codec = si.codec
        val state =
            SegmentWriteState(
                InfoStream.default,
                dir,
                si,
                fieldInfos,
                null,
                newIOContext(random())
            )

        fields.sort()
        val consumer: FieldsConsumer = codec.postingsFormat().fieldsConsumer(state)
        val fakeNorms =
            object : NormsProducer() {
                override fun close() {}

                override fun getNorms(field: FieldInfo): NumericDocValues {
                    return object : NumericDocValues() {
                        var doc = -1

                        override fun nextDoc(): Int {
                            return advance(doc + 1)
                        }

                        override fun docID(): Int {
                            return doc
                        }

                        override fun cost(): Long {
                            return si.maxDoc().toLong()
                        }

                        override fun advance(target: Int): Int {
                            if (target >= si.maxDoc()) {
                                doc = NO_MORE_DOCS
                            } else {
                                doc = target
                            }
                            return doc
                        }

                        override fun advanceExact(target: Int): Boolean {
                            doc = target
                            return true
                        }

                        override fun longValue(): Long {
                            return 1
                        }
                    }
                }

                override fun checkIntegrity() {}
            }
        var success = false
        try {
            consumer.write(DataFields(fields), fakeNorms)
            success = true
        } finally {
            if (success) {
                IOUtils.close(consumer)
            } else {
                IOUtils.closeWhileHandlingException(consumer)
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testDocsOnlyFreq() {
        // tests that when fields are indexed with DOCS_ONLY, the Codec
        // returns 1 in docsEnum.freq()
        val dir = newDirectory()
        val random = random()
        val writer = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random)))
        val numDocs = atLeast(random, 50)
        for (i in 0 until numDocs) {
            val doc = Document()
            doc.add(StringField("f", "doc", Store.NO))
            writer.addDocument(doc)
        }
        writer.close()

        val term = Term("f", BytesRef("doc"))
        val reader = DirectoryReader.open(dir)
        for (ctx in reader.leaves()) {
            val de = requireNotNull(ctx.reader().postings(term))
            while (de.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                assertEquals(1, de.freq(), "wrong freq for doc ${de.docID()}")
            }
        }
        reader.close()

        dir.close()
    }
}
