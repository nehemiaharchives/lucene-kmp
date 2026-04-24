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

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PayloadAttribute
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.IndexWriterConfig.OpenMode
import org.gnit.lucenekmp.jdkport.ReentrantLock
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.jdkport.Thread
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.withLock
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.CannedTokenStream
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.analysis.Token
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import kotlin.math.ln
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class TestPayloads : LuceneTestCase() {

    // Simple tests to test the Payload class
    @Test
    fun testPayload() {
        val payload = BytesRef("This is a test!")
        assertEquals("This is a test!".length, payload.length, "Wrong payload length.")

        val clone = payload.clone()
        assertEquals(payload.length, clone.length)
        for (i in 0..<payload.length) {
            assertEquals(payload.bytes[i + payload.offset], clone.bytes[i + clone.offset])
        }
    }

    // Tests whether the DocumentWriter and SegmentMerger correctly enable the
    // payload bit in the FieldInfo
    @Test
    fun testPayloadFieldBit() {
        val ram = newDirectory()
        var analyzer = PayloadAnalyzer()
        var writer = IndexWriter(ram, newIndexWriterConfig(analyzer))
        var d = Document()
        // this field won't have any payloads
        d.add(newTextField("f1", "This field has no payloads", Field.Store.NO))
        // this field will have payloads in all docs, however not for all term positions,
        // so this field is used to check if the DocumentWriter correctly enables the payloads bit
        // even if only some term positions have payloads
        d.add(newTextField("f2", "This field has payloads in all docs", Field.Store.NO))
        d.add(newTextField("f2", "This field has payloads in all docs NO PAYLOAD", Field.Store.NO))
        // this field is used to verify if the SegmentMerger enables payloads for a field if it has
        // payloads
        // enabled in only some documents
        d.add(newTextField("f3", "This field has payloads in some docs", Field.Store.NO))
        // only add payload data for field f2
        analyzer.setPayloadData("f2", "somedata".encodeToByteArray(), 0, 1)
        writer.addDocument(d)
        // flush
        writer.close()

        var reader = getOnlyLeafReader(DirectoryReader.open(ram))
        var fi = reader.fieldInfos
        assertFalse(requireNotNull(fi.fieldInfo("f1")).hasPayloads(), "Payload field bit should not be set.")
        assertTrue(requireNotNull(fi.fieldInfo("f2")).hasPayloads(), "Payload field bit should be set.")
        assertFalse(requireNotNull(fi.fieldInfo("f3")).hasPayloads(), "Payload field bit should not be set.")
        reader.close()

        // now we add another document which has payloads for field f3 and verify if the SegmentMerger
        // enabled payloads for that field
        analyzer = PayloadAnalyzer() // Clear payload state for each field
        writer = IndexWriter(ram, newIndexWriterConfig(analyzer).setOpenMode(OpenMode.CREATE))
        d = Document()
        d.add(newTextField("f1", "This field has no payloads", Field.Store.NO))
        d.add(newTextField("f2", "This field has payloads in all docs", Field.Store.NO))
        d.add(newTextField("f2", "This field has payloads in all docs", Field.Store.NO))
        d.add(newTextField("f3", "This field has payloads in some docs", Field.Store.NO))
        // add payload data for field f2 and f3
        analyzer.setPayloadData("f2", "somedata".encodeToByteArray(), 0, 1)
        analyzer.setPayloadData("f3", "somedata".encodeToByteArray(), 0, 3)
        writer.addDocument(d)

        // force merge
        writer.forceMerge(1)
        // flush
        writer.close()

        reader = getOnlyLeafReader(DirectoryReader.open(ram))
        fi = reader.fieldInfos
        assertFalse(requireNotNull(fi.fieldInfo("f1")).hasPayloads(), "Payload field bit should not be set.")
        assertTrue(requireNotNull(fi.fieldInfo("f2")).hasPayloads(), "Payload field bit should be set.")
        assertTrue(requireNotNull(fi.fieldInfo("f3")).hasPayloads(), "Payload field bit should be set.")
        reader.close()
        ram.close()
    }

    // Tests if payloads are correctly stored and loaded.
    @Test
    fun testPayloadsEncoding() {
        val dir = newDirectory()
        performTest(dir)
        dir.close()
    }

    // builds an index with payloads in the given Directory and performs
    // different tests to verify the payload encoding
    private fun performTest(dir: Directory) {
        var analyzer = PayloadAnalyzer()
        var writer =
            IndexWriter(
                dir,
                newIndexWriterConfig(analyzer)
                    .setOpenMode(OpenMode.CREATE)
                    .setMergePolicy(newLogMergePolicy()),
            )

        // should be in sync with value in TermInfosWriter
        val skipInterval = 16

        val numTerms = 5
        val fieldName = "f1"

        val numDocs = skipInterval + 1
        // create content for the test documents with just a few terms
        val terms = generateTerms(fieldName, numTerms)
        val sb = StringBuilder()
        for (i in terms.indices) {
            sb.append(terms[i].text())
            sb.append(" ")
        }
        val content = sb.toString()

        val payloadDataLength = numTerms * numDocs * 2 + numTerms * numDocs * (numDocs - 1) / 2
        var payloadData = generateRandomData(payloadDataLength)

        var d = Document()
        d.add(newTextField(fieldName, content, Field.Store.NO))
        // add the same document multiple times to have the same payload lengths for all
        // occurrences within two consecutive skip intervals
        var offset = 0
        repeat(2 * numDocs) {
            analyzer.setPayloadData(fieldName, payloadData, offset, 1)
            offset += numTerms
            writer.addDocument(d)
        }

        // make sure we create more than one segment to test merging
        writer.commit()

        // now we make sure to have different payload lengths next at the next skip point
        for (i in 0..<numDocs) {
            analyzer.setPayloadData(fieldName, payloadData, offset, i)
            offset += i * numTerms
            writer.addDocument(d)
        }

        writer.forceMerge(1)
        // flush
        writer.close()

        /*
         * Verify the index
         * first we test if all payloads are stored correctly
         */
        var reader: IndexReader = DirectoryReader.open(dir)

        val verifyPayloadData = ByteArray(payloadDataLength)
        offset = 0
        val tps = arrayOfNulls<PostingsEnum>(numTerms)
        for (i in 0..<numTerms) {
            tps[i] =
                MultiTerms.getTermPostingsEnum(reader, terms[i].field(), BytesRef(terms[i].text()))
        }

        while (tps[0]!!.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
            for (i in 1..<numTerms) {
                tps[i]!!.nextDoc()
            }
            val freq = tps[0]!!.freq()

            repeat(freq) {
                for (j in 0..<numTerms) {
                    tps[j]!!.nextPosition()
                    val br = tps[j]!!.payload
                    if (br != null) {
                        br.bytes.copyInto(verifyPayloadData, offset, br.offset, br.offset + br.length)
                        offset += br.length
                    }
                }
            }
        }

        assertByteArrayEquals(payloadData, verifyPayloadData)

        /*
         *  test lazy skipping
         */
        var tp =
            MultiTerms.getTermPostingsEnum(reader, terms[0].field(), BytesRef(terms[0].text()))!!
        tp.nextDoc()
        tp.nextPosition()
        // NOTE: prior rev of this test was failing to first
        // call next here:
        tp.nextDoc()
        // now we don't read this payload
        tp.nextPosition()
        var payload = tp.payload!!
        assertEquals(1, payload.length, "Wrong payload length.")
        assertEquals(payload.bytes[payload.offset], payloadData[numTerms])
        tp.nextDoc()
        tp.nextPosition()

        // we don't read this payload and skip to a different document
        tp.advance(5)
        tp.nextPosition()
        payload = tp.payload!!
        assertEquals(1, payload.length, "Wrong payload length.")
        assertEquals(payload.bytes[payload.offset], payloadData[5 * numTerms])

        /*
         * Test different lengths at skip points
         */
        tp = MultiTerms.getTermPostingsEnum(reader, terms[1].field(), BytesRef(terms[1].text()))!!
        tp.nextDoc()
        tp.nextPosition()
        assertEquals(1, tp.payload!!.length, "Wrong payload length.")
        tp.advance(skipInterval - 1)
        tp.nextPosition()
        assertEquals(1, tp.payload!!.length, "Wrong payload length.")
        tp.advance(2 * skipInterval - 1)
        tp.nextPosition()
        assertEquals(1, tp.payload!!.length, "Wrong payload length.")
        tp.advance(3 * skipInterval - 1)
        tp.nextPosition()
        assertEquals(3 * skipInterval - 2 * numDocs - 1, tp.payload!!.length, "Wrong payload length.")

        reader.close()

        // test long payload
        analyzer = PayloadAnalyzer()
        writer = IndexWriter(dir, newIndexWriterConfig(analyzer).setOpenMode(OpenMode.CREATE))
        val singleTerm = "lucene"

        d = Document()
        d.add(newTextField(fieldName, singleTerm, Field.Store.NO))
        // add a payload whose length is greater than the buffer size of BufferedIndexOutput
        payloadData = generateRandomData(2000)
        analyzer.setPayloadData(fieldName, payloadData, 100, 1500)
        writer.addDocument(d)

        writer.forceMerge(1)
        // flush
        writer.close()

        reader = DirectoryReader.open(dir)
        tp = MultiTerms.getTermPostingsEnum(reader, fieldName, BytesRef(singleTerm))!!
        tp.nextDoc()
        tp.nextPosition()

        val br = tp.payload!!
        val portion = ByteArray(1500)
        payloadData.copyInto(portion, 0, 100, 100 + 1500)

        assertByteArrayEquals(portion, br.bytes, br.offset, br.length)
        reader.close()
    }

    private fun generateRandomData(data: ByteArray) {
        // this test needs the random data to be valid unicode
        val s = TestUtil.randomFixedByteLengthUnicodeString(random(), data.size)
        val b = s.encodeToByteArray()
        assert(b.size == data.size)
        b.copyInto(data, 0, 0, b.size)
    }

    private fun generateRandomData(n: Int): ByteArray {
        val data = ByteArray(n)
        generateRandomData(data)
        return data
    }

    @Suppress("SameParameterValue")
    private fun generateTerms(fieldName: String, n: Int): Array<Term> {
        val maxDigits = (ln(n.toDouble()) / ln(10.0)).toInt()
        val terms = arrayOfNulls<Term>(n)
        val sb = StringBuilder()
        for (i in 0..<n) {
            sb.setLength(0)
            sb.append("t")
            val zeros = maxDigits - (ln(i.toDouble()) / ln(10.0)).toInt()
            repeat(zeros) {
                sb.append("0")
            }
            sb.append(i)
            terms[i] = Term(fieldName, sb.toString())
        }
        @Suppress("UNCHECKED_CAST")
        return terms as Array<Term>
    }

    private fun assertByteArrayEquals(b1: ByteArray, b2: ByteArray) {
        if (b1.size != b2.size) {
            fail("Byte arrays have different lengths: ${b1.size}, ${b2.size}")
        }

        for (i in b1.indices) {
            if (b1[i] != b2[i]) {
                fail("Byte arrays different at index $i: ${b1[i]}, ${b2[i]}")
            }
        }
    }

    private fun assertByteArrayEquals(b1: ByteArray, b2: ByteArray, b2offset: Int, b2length: Int) {
        if (b1.size != b2length) {
            fail("Byte arrays have different lengths: ${b1.size}, $b2length")
        }

        for (i in b1.indices) {
            if (b1[i] != b2[b2offset + i]) {
                fail("Byte arrays different at index $i: ${b1[i]}, ${b2[b2offset + i]}")
            }
        }
    }

    private class PayloadData(val data: ByteArray, val offset: Int, val length: Int)

    /** This Analyzer uses an MockTokenizer and PayloadFilter. */
    private class PayloadAnalyzer : Analyzer(PER_FIELD_REUSE_STRATEGY) {
        val fieldToData: MutableMap<String, PayloadData> = HashMap()

        fun setPayloadData(field: String, data: ByteArray, offset: Int, length: Int) {
            fieldToData[field] = PayloadData(data, offset, length)
        }

        override fun createComponents(fieldName: String): TokenStreamComponents {
            val payload = fieldToData[fieldName]
            val ts: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, false)
            val tokenStream: TokenStream =
                if (payload != null) PayloadFilter(ts, fieldName, fieldToData) else ts
            return TokenStreamComponents(ts, tokenStream)
        }
    }

    /** This Filter adds payloads to the tokens. */
    private class PayloadFilter(
        input: TokenStream,
        private val fieldName: String,
        private val fieldToData: Map<String, PayloadData>,
    ) : TokenFilter(input) {
        val payloadAtt: PayloadAttribute = addAttribute(PayloadAttribute::class)
        val termAttribute: CharTermAttribute = addAttribute(CharTermAttribute::class)
        private lateinit var payloadData: PayloadData
        private var offset = 0

        override fun incrementToken(): Boolean {
            val hasNext = input.incrementToken()
            if (!hasNext) {
                return false
            }

            // Some values of the same field are to have payloads and others not
            if (offset + payloadData.length <= payloadData.data.size
                && !termAttribute.toString().endsWith("NO PAYLOAD")
            ) {
                val p = BytesRef(payloadData.data, offset, payloadData.length)
                payloadAtt.payload = p
                offset += payloadData.length
            } else {
                payloadAtt.payload = null
            }

            return true
        }

        override fun reset() {
            super.reset()
            this.payloadData = requireNotNull(fieldToData[fieldName])
            this.offset = payloadData.offset
        }
    }

    @Test
    fun testThreadSafety() {
        val numThreads = 5
        val numDocs = atLeast(50)
        val pool = ByteArrayPool(numThreads, 5)

        val dir = newDirectory()
        val writer =
            IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
        val field = "test"
        val failureLock = ReentrantLock()
        var failure: Throwable? = null

        val ingesters = arrayOfNulls<Thread>(numThreads)
        for (i in 0..<numThreads) {
            ingesters[i] =
                object : Thread() {
                    override fun run() {
                        try {
                            repeat(numDocs) {
                                val d = Document()
                                d.add(TextField(field, PoolingPayloadTokenStream(pool)))
                                writer.addDocument(d)
                            }
                        } catch (e: Throwable) {
                            failureLock.withLock {
                                if (failure == null) {
                                    failure = e
                                }
                            }
                        }
                    }
                }
            ingesters[i]!!.start()
        }

        for (i in 0..<numThreads) {
            ingesters[i]!!.join()
        }
        failure?.let { fail(it.toString()) }
        writer.close()
        val reader: IndexReader = DirectoryReader.open(dir)
        val terms = MultiTerms.getTerms(reader, field)!!.iterator()
        var tp: PostingsEnum? = null
        while (terms.next() != null) {
            val termText = terms.term()!!.utf8ToString()
            tp = terms.postings(tp, PostingsEnum.PAYLOADS.toInt())
            while (tp!!.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                val freq = tp.freq()
                repeat(freq) {
                    tp.nextPosition()
                    val payload = tp.payload!!
                    assertEquals(termText, payload.utf8ToString())
                }
            }
        }
        reader.close()
        dir.close()
        assertEquals(pool.size(), numThreads)
    }

    private inner class PoolingPayloadTokenStream(private val pool: ByteArrayPool) : TokenStream() {
        private val payload: ByteArray = pool.get()
        private var first: Boolean
        private val term: String

        val termAtt: CharTermAttribute
        val payloadAtt: PayloadAttribute

        init {
            generateRandomData(payload)
            term = payload.decodeToString()
            first = true
            payloadAtt = addAttribute(PayloadAttribute::class)
            termAtt = addAttribute(CharTermAttribute::class)
        }

        override fun incrementToken(): Boolean {
            if (!first) return false
            first = false
            clearAttributes()
            termAtt.append(term)
            payloadAtt.payload = BytesRef(payload)
            return true
        }

        override fun close() {
            pool.release(payload)
        }
    }

    private class ByteArrayPool(capacity: Int, size: Int) {
        private val lock = ReentrantLock()
        private val pool: MutableList<ByteArray> = ArrayList()

        init {
            repeat(capacity) {
                pool.add(ByteArray(size))
            }
        }

        fun get(): ByteArray {
            return lock.withLock {
                pool.removeAt(0)
            }
        }

        fun release(b: ByteArray) {
            lock.withLock {
                pool.add(b)
            }
        }

        fun size(): Int {
            return lock.withLock {
                pool.size
            }
        }
    }

    @Test
    fun testAcrossFields() {
        val dir = newDirectory()
        var writer =
            RandomIndexWriter(
                random(), dir, MockAnalyzer(random(), MockTokenizer.WHITESPACE, true)
            )
        var doc = Document()
        doc.add(TextField("hasMaybepayload", "here we go", Field.Store.YES))
        writer.addDocument(doc)
        writer.close()

        writer =
            RandomIndexWriter(
                random(), dir, MockAnalyzer(random(), MockTokenizer.WHITESPACE, true)
            )
        doc = Document()
        doc.add(TextField("hasMaybepayload2", "here we go", Field.Store.YES))
        writer.addDocument(doc)
        writer.addDocument(doc)
        writer.forceMerge(1)
        writer.close()

        dir.close()
    }

    /** some docs have payload att, some not */
    @Test
    fun testMixupDocs() {
        val dir = newDirectory()
        val iwc = IndexWriterConfig()
        iwc.setMergePolicy(newLogMergePolicy())
        val writer = RandomIndexWriter(random(), dir, iwc)
        val doc = Document()
        var ts: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, true)
        (ts as Tokenizer).setReader(StringReader("here we go"))
        val field = Field("field", ts, TextField.TYPE_NOT_STORED)
        doc.add(field)
        writer.addDocument(doc)
        val withPayload = Token("withPayload", 0, 11)
        withPayload.payload = BytesRef("test")
        ts = CannedTokenStream(withPayload)
        assertTrue(ts.hasAttribute(PayloadAttribute::class))
        field.setTokenStream(ts)
        writer.addDocument(doc)
        ts = MockTokenizer(MockTokenizer.WHITESPACE, true)
        (ts as Tokenizer).setReader(StringReader("another"))
        field.setTokenStream(ts)
        writer.addDocument(doc)
        val reader = writer.getReader(applyDeletions = true, writeAllDeletes = true)
        val te = MultiTerms.getTerms(reader, "field")!!.iterator()
        assertTrue(te.seekExact(BytesRef("withPayload")))
        val de = te.postings(null, PostingsEnum.PAYLOADS.toInt())!!
        de.nextDoc()
        de.nextPosition()
        assertEquals(BytesRef("test"), de.payload)
        writer.close()
        reader.close()
        dir.close()
    }

    /** some field instances have payload att, some not */
    @Test
    fun testMixupMultiValued() {
        val dir = newDirectory()
        val writer = RandomIndexWriter(random(), dir)
        val doc = Document()
        var ts: TokenStream = MockTokenizer(MockTokenizer.WHITESPACE, true)
        val field = Field("field", ts, TextField.TYPE_NOT_STORED)
        (ts as Tokenizer).setReader(StringReader("here we go"))
        field.setTokenStream(ts)
        doc.add(field)
        val withPayload = Token("withPayload", 0, 11)
        withPayload.payload = BytesRef("test")
        ts = CannedTokenStream(withPayload)
        assertTrue(ts.hasAttribute(PayloadAttribute::class))
        val field2 = Field("field", ts, TextField.TYPE_NOT_STORED)
        doc.add(field2)
        ts = MockTokenizer(MockTokenizer.WHITESPACE, true)
        (ts as Tokenizer).setReader(StringReader("nopayload"))
        val field3 = Field("field", ts, TextField.TYPE_NOT_STORED)
        doc.add(field3)
        writer.addDocument(doc)
        val reader = writer.getReader(applyDeletions = true, writeAllDeletes = true)
        val sr = getOnlyLeafReader(reader)
        val de = sr.postings(Term("field", "withPayload"), PostingsEnum.PAYLOADS.toInt())!!
        de.nextDoc()
        de.nextPosition()
        assertEquals(BytesRef("test"), de.payload)
        writer.close()
        reader.close()
        dir.close()
    }
}
