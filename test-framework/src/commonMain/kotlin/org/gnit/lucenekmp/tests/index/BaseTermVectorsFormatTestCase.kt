package org.gnit.lucenekmp.tests.index

import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PayloadAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.codecs.TermVectorsFormat
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.Field.Store
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.Fields
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.IndexableField
import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.TermVectors
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.index.TermsEnum.SeekStatus
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.EnumSet
import org.gnit.lucenekmp.jdkport.InterruptedException
import org.gnit.lucenekmp.jdkport.UncheckedIOException
import org.gnit.lucenekmp.jdkport.computeIfAbsent
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Sort
import org.gnit.lucenekmp.search.SortField
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.CannedTokenStream
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.analysis.Token
import org.gnit.lucenekmp.tests.util.RandomPicks
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.AttributeImpl
import org.gnit.lucenekmp.util.AttributeReflector
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.CollectionUtil
import org.gnit.lucenekmp.util.IOUtils
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.reflect.KClass
import kotlin.test.DefaultAsserter.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/**
 * Base class aiming at testing [term vectors formats][TermVectorsFormat]. To test a new
 * format, all you need is to register a new [Codec] which uses it and extend this class and
 * override [.getCodec].
 *
 * @lucene.experimental
 */
@OptIn(ExperimentalAtomicApi::class)
abstract class BaseTermVectorsFormatTestCase : BaseIndexFileFormatTestCase() {
    /** A combination of term vectors options.  */
    protected enum class Options(val positions: Boolean, val offsets: Boolean, val payloads: Boolean) {
        NONE(false, false, false),
        POSITIONS(true, false, false),
        OFFSETS(false, true, false),
        POSITIONS_AND_OFFSETS(true, true, false),
        POSITIONS_AND_PAYLOADS(true, false, true),
        POSITIONS_AND_OFFSETS_AND_PAYLOADS(true, true, true)
    }

    protected fun validOptions(): MutableSet<Options> {
        return EnumSet.allOf<Options>(Options::class)
    }

    protected fun randomOptions(): Options {
        return RandomPicks.randomFrom<Options>(random(), ArrayList(validOptions()))
    }

    protected fun fieldType(options: Options): FieldType {
        val ft = FieldType(TextField.TYPE_NOT_STORED)
        ft.setStoreTermVectors(true)
        ft.setStoreTermVectorPositions(options.positions)
        ft.setStoreTermVectorOffsets(options.offsets)
        ft.setStoreTermVectorPayloads(options.payloads)
        ft.freeze()
        return ft
    }

    override fun addRandomFields(doc: Document) {
        for (opts in validOptions()) {
            val ft = fieldType(opts)
            val numFields: Int = random().nextInt(5)
            for (j in 0..<numFields) {
                doc.add(Field("f_$opts", TestUtil.randomSimpleString(random(), 2), ft))
            }
        }
    }

    // custom impl to test cases that are forbidden by the default OffsetAttribute impl
    private class PermissiveOffsetAttributeImpl : AttributeImpl(), OffsetAttribute {
        var start: Int = 0
        var end: Int = 0

        override fun startOffset(): Int {
            return start
        }

        override fun endOffset(): Int {
            return end
        }

        override fun setOffset(startOffset: Int, endOffset: Int) {
            // no check!
            start = startOffset
            end = endOffset
        }

        override fun clear() {
            end = 0
            start = end
        }

        override fun equals(other: Any?): Boolean {
            if (other === this) {
                return true
            }

            if (other is PermissiveOffsetAttributeImpl) {
                return other.start == start && other.end == end
            }

            return false
        }

        override fun hashCode(): Int {
            return start + 31 * end
        }

        override fun copyTo(target: AttributeImpl) {
            val t: OffsetAttribute = target as OffsetAttribute
            t.setOffset(start, end)
        }

        override fun newInstance(): AttributeImpl {
            TODO("Not yet implemented")
        }

        override fun reflectWith(reflector: AttributeReflector) {
            reflector.reflect(OffsetAttribute::class, "startOffset", start)
            reflector.reflect(OffsetAttribute::class, "endOffset", end)
        }
    }

    // TODO: use CannedTokenStream
    // TODO: pull out and make top-level-utility, separate from TermVectors
    /** Produces a random TokenStream based off of provided terms.  */
    open class RandomTokenStream(len: Int, sampleTerms: Array<String>, sampleTermBytes: Array<BytesRef>) : TokenStream() {
        val terms: Array<String>
        val termBytes: Array<BytesRef>
        val positionsIncrements: IntArray
        val positions: IntArray
        val startOffsets: IntArray
        val endOffsets: IntArray
        val payloads: Array<BytesRef?>

        val freqs: MutableMap<String, Int>
        val positionToTerms: MutableMap<Int, MutableSet<Int>>
        val startOffsetToTerms: MutableMap<Int, MutableSet<Int>>

        val termAtt: CharTermAttribute
        val piAtt: PositionIncrementAttribute
        val oAtt: OffsetAttribute
        val pAtt: PayloadAttribute
        var i: Int = 0

        init {
            terms = kotlin.arrayOfNulls<String>(len) as Array<String>
            termBytes = kotlin.arrayOfNulls<BytesRef>(len) as Array<BytesRef>
            positionsIncrements = IntArray(len)
            positions = IntArray(len)
            startOffsets = IntArray(len)
            endOffsets = IntArray(len)
            payloads = kotlin.arrayOfNulls<BytesRef>(len)
            for (i in 0..<len) {
                val o: Int = random().nextInt(sampleTerms.size)
                terms[i] = sampleTerms[o]
                termBytes[i] = sampleTermBytes[o]
                positionsIncrements[i] = TestUtil.nextInt(random(), if (i == 0) 1 else 0, 10)
                if (i == 0) {
                    startOffsets[i] = TestUtil.nextInt(random(), 0, 1 shl 16)
                } else {
                    startOffsets[i] =
                        startOffsets[i - 1] + TestUtil.nextInt(random(), 0, if (rarely()) 1 shl 16 else 20)
                }
                endOffsets[i] = startOffsets[i] + TestUtil.nextInt(random(), 0, if (rarely()) 1 shl 10 else 20)
            }

            for (i in 0..<len) {
                if (i == 0) {
                    positions[i] = positionsIncrements[i] - 1
                } else {
                    positions[i] = positions[i - 1] + positionsIncrements[i]
                }
            }
            if (rarely()) {
                Arrays.fill(payloads, randomPayload())
            } else {
                for (i in 0..<len) {
                    payloads[i] = randomPayload()
                }
            }

            positionToTerms = CollectionUtil.newHashMap<Int, MutableSet<Int>>(len)
            startOffsetToTerms = CollectionUtil.newHashMap<Int, MutableSet<Int>>(len)
            for (i in 0..<len) {
                positionToTerms.computeIfAbsent(positions[i]) { `_`: Int -> HashSet<Int>(1) }!!.add(i)
                startOffsetToTerms.computeIfAbsent(startOffsets[i]) { `_`: Int -> HashSet<Int>(1) }!!.add(i)
            }

            freqs = mutableMapOf()
            for (term in terms) {
                freqs[term] = (freqs[term] ?: 0) + 1
            }

            addAttributeImpl(PermissiveOffsetAttributeImpl())

            termAtt = addAttribute<CharTermAttribute>(CharTermAttribute::class)
            piAtt = addAttribute<PositionIncrementAttribute>(PositionIncrementAttribute::class)
            oAtt = addAttribute<OffsetAttribute>(OffsetAttribute::class)
            pAtt = addAttribute<PayloadAttribute>(PayloadAttribute::class)
        }

        protected open fun randomPayload(): BytesRef? {
            val len: Int = random().nextInt(5)
            if (len == 0) {
                return null
            }
            val payload: BytesRef = BytesRef(len)
            random().nextBytes(payload.bytes)
            payload.length = len

            return newBytesRef(payload)
        }

        fun hasPayloads(): Boolean {
            for (payload in payloads) {
                if (payload != null && payload.length > 0) {
                    return true
                }
            }
            return false
        }

        /*fun getTermBytes(): Array<BytesRef> {
            return termBytes
        }*/

        /*fun getPayloads(): Array<BytesRef?> {
            return payloads
        }*/

        @Throws(IOException::class)
        override fun reset() {
            i = 0
            super.reset()
        }

        @Throws(IOException::class)
        override fun incrementToken(): Boolean {
            if (i < terms.size) {
                clearAttributes()
                termAtt.setLength(0).append(terms[i])
                piAtt.setPositionIncrement(positionsIncrements[i])
                oAtt.setOffset(startOffsets[i], endOffsets[i])
                pAtt.payload = payloads[i]
                ++i
                return true
            } else {
                return false
            }
        }
    }

    /** Randomly generated document: call toDocument to index it  */
    protected inner class RandomDocument(
        fieldCount: Int,
        maxTermCount: Int,
        options: Options,
        fieldNames: Array<String>,
        sampleTerms: Array<String>,
        sampleTermBytes: Array<BytesRef>
    ) {
        val fieldNames: Array<String>
        val fieldTypes: Array<FieldType>
        val tokenStreams: Array<RandomTokenStream>

        init {
            require(fieldCount <= fieldNames.size)
            this.fieldNames = kotlin.arrayOfNulls<String>(fieldCount) as Array<String>
            fieldTypes = kotlin.arrayOfNulls<FieldType>(fieldCount) as Array<FieldType>
            tokenStreams = kotlin.arrayOfNulls<RandomTokenStream>(fieldCount) as Array<RandomTokenStream>
            Arrays.fill(fieldTypes, fieldType(options))
            val usedFileNames: MutableSet<String> = mutableSetOf()
            for (i in 0..<fieldCount) {
                do {
                    this.fieldNames[i] = RandomPicks.randomFrom<String>(random(), fieldNames)
                } while (usedFileNames.contains(this.fieldNames[i]))
                usedFileNames.add(this.fieldNames[i])
                tokenStreams[i] =
                    RandomTokenStream(
                        TestUtil.nextInt(random(), 1, maxTermCount), sampleTerms, sampleTermBytes
                    )
            }
        }

        fun toDocument(): Document {
            val doc = Document()
            for (i in fieldNames.indices) {
                doc.add(Field(fieldNames[i], tokenStreams[i], fieldTypes[i]))
            }
            return doc
        }
    }

    /** Factory for generating random documents, call newDocument to generate each one  */
    protected inner class RandomDocumentFactory(distinctFieldNames: Int, disctinctTerms: Int) {
        private val fieldNames: Array<String>
        private val terms: Array<String>
        private val termBytes: Array<BytesRef>

        init {
            val fieldNames: MutableSet<String> = mutableSetOf()
            while (fieldNames.size < distinctFieldNames) {
                fieldNames.add(TestUtil.randomSimpleString(random()))
                fieldNames.remove("id")
            }
            this.fieldNames = fieldNames.toTypedArray<String>()
            terms = kotlin.arrayOfNulls<String>(disctinctTerms) as Array<String>
            termBytes = kotlin.arrayOfNulls<BytesRef>(disctinctTerms) as Array<BytesRef>
            for (i in 0..<disctinctTerms) {
                terms[i] = TestUtil.randomRealisticUnicodeString(random())
                termBytes[i] = newBytesRef(terms[i])
            }
        }

        fun newDocument(fieldCount: Int, maxTermCount: Int, options: Options): RandomDocument {
            return RandomDocument(fieldCount, maxTermCount, options, fieldNames, terms, termBytes)
        }
    }

    @Throws(IOException::class)
    protected fun assertEquals(doc: RandomDocument, fields: Fields) {
        // compare field names
        assertNotNull(doc)
        assertNotNull(fields)
        assertEquals(doc.fieldNames.size.toLong(), fields.size().toLong())
        val fields1: MutableSet<String> = mutableSetOf()
        val fields2: MutableSet<String> = mutableSetOf()
        fields1.addAll(doc.fieldNames)
        for (field in fields) {
            fields2.add(field)
        }
        assertEquals(fields1, fields2)

        for (i in doc.fieldNames.indices) {
            assertEquals(doc.tokenStreams[i], doc.fieldTypes[i], fields.terms(doc.fieldNames[i])!!)
        }
    }

    private class JobLocal<T> {
        private val mutex = Mutex()
        private val values: MutableMap<Job, T?> = mutableMapOf()
        private val defaultJob = Job()

        fun set(value: T, job: Job? = null) {
            val key = job ?: defaultJob
            runBlocking {
                mutex.withLock {
                    values[key] = value
                }
            }
        }

        fun get(job: Job? = null): T? {
            val key = job ?: defaultJob
            return runBlocking {
                mutex.withLock {
                    values[key]
                }
            }
        }
    }

    // to test reuse
    private val docsEnum = JobLocal<PostingsEnum>()

    @Throws(IOException::class)
    protected fun assertEquals(tk: RandomTokenStream, ft: FieldType, terms: Terms) {
        assertEquals(1, terms.docCount.toLong())
        val termCount: Int = tk.terms.toSet().size
        assertEquals(termCount.toLong(), terms.size())
        assertEquals(termCount.toLong(), terms.sumDocFreq)
        assertEquals(ft.storeTermVectorPositions(), terms.hasPositions())
        assertEquals(ft.storeTermVectorOffsets(), terms.hasOffsets())
        assertEquals(ft.storeTermVectorPayloads() && tk.hasPayloads(), terms.hasPayloads())
        val uniqueTerms: MutableSet<BytesRef> = mutableSetOf()
        for (term in tk.freqs.keys) {
            uniqueTerms.add(BytesRef(term))
        }
        val sortedTerms: Array<BytesRef> = uniqueTerms.toTypedArray<BytesRef>()
        Arrays.sort(sortedTerms)
        val termsEnum: TermsEnum = terms.iterator()
        for (i in sortedTerms.indices) {
            val nextTerm: BytesRef? = termsEnum.next()
            assertEquals(sortedTerms[i], nextTerm)
            assertEquals(sortedTerms[i], termsEnum.term())
            assertEquals(1, termsEnum.docFreq().toLong())

            var postingsEnum: PostingsEnum? = termsEnum.postings(null)
            postingsEnum = termsEnum.postings(if (random().nextBoolean()) null else postingsEnum)
            assertNotNull(postingsEnum)
            assertEquals(0, postingsEnum.nextDoc().toLong())
            assertEquals(0, postingsEnum.docID().toLong())
            assertEquals(tk.freqs.get(termsEnum.term()!!.utf8ToString()), postingsEnum.freq() as Int)
            assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), postingsEnum.nextDoc().toLong())
            this.docsEnum.set(postingsEnum)

            var docsAndPositionsEnum: PostingsEnum = termsEnum.postings(null)!!
            docsAndPositionsEnum = termsEnum.postings(if (random().nextBoolean()) null else docsAndPositionsEnum, PostingsEnum.POSITIONS.toInt())!!
            if (terms.hasPositions() || terms.hasOffsets()) {
                assertEquals(0, docsAndPositionsEnum.nextDoc().toLong())
                val freq: Int = docsAndPositionsEnum.freq()
                assertEquals(tk.freqs[termsEnum.term()!!.utf8ToString()], freq as Int)
                for (k in 0..<freq) {
                    val position: Int = docsAndPositionsEnum.nextPosition()
                    val indexes: MutableSet<Int>?
                    if (terms.hasPositions()) {
                        indexes = tk.positionToTerms[position]
                        assertNotNull(indexes)
                    } else {
                        indexes = tk.startOffsetToTerms[docsAndPositionsEnum.startOffset()]
                        assertNotNull(indexes)
                    }
                    if (terms.hasPositions()) {
                        var foundPosition = false
                        for (index in indexes) {
                            if (tk.termBytes[index] == termsEnum.term() && tk.positions[index] == position) {
                                foundPosition = true
                                break
                            }
                        }
                        assertTrue(foundPosition)
                    }
                    if (terms.hasOffsets()) {
                        var foundOffset = false
                        for (index in indexes) {
                            if (tk.termBytes[index] == termsEnum.term()
                                && tk.startOffsets[index] == docsAndPositionsEnum.startOffset() && tk.endOffsets[index] == docsAndPositionsEnum.endOffset()
                            ) {
                                foundOffset = true
                                break
                            }
                        }
                        assertTrue(foundOffset)
                    }
                    if (terms.hasPayloads()) {
                        var foundPayload = false
                        for (index in indexes!!) {
                            if (tk.termBytes[index!!] == termsEnum.term()
                                && equals(tk.payloads[index], docsAndPositionsEnum.payload)
                            ) {
                                foundPayload = true
                                break
                            }
                        }
                        assertTrue(foundPayload)
                    }
                }
                expectThrows(this.readPastLastPositionExceptionClass) { docsAndPositionsEnum.nextPosition() }
                assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum.nextDoc().toLong())
            }
            this.docsEnum.set(docsAndPositionsEnum)
        }
        assertNull(termsEnum.next())
        for (i in 0..4) {
            if (random().nextBoolean()) {
                assertTrue(termsEnum.seekExact(RandomPicks.randomFrom(random(), tk.termBytes)))
            } else {
                assertEquals(SeekStatus.FOUND, termsEnum.seekCeil(RandomPicks.randomFrom(random(), tk.termBytes)))
            }
        }
    }

    protected val readPastLastPositionExceptionClass: KClass<out Throwable>
        get() = IllegalStateException::class

    protected fun addId(doc: Document, id: String): Document {
        doc.add(StringField("id", id, Store.NO))
        return doc
    }

    @Throws(IOException::class)
    protected fun docID(reader: IndexReader, id: String): Int {
        return IndexSearcher(reader).search(TermQuery(Term("id", id)), 1).scoreDocs[0].doc
    }

    // only one doc with vectors
    @Throws(IOException::class)
    open fun testRareVectors() {
        val docFactory = RandomDocumentFactory(10, 20)
        for (options in validOptions()) {
            val numDocs: Int = atLeast(200)
            val docWithVectors: Int = random().nextInt(numDocs)
            val emptyDoc = Document()
            val dir: Directory = newDirectory()
            val writer: RandomIndexWriter = RandomIndexWriter(random(), dir)
            val doc =
                docFactory.newDocument(TestUtil.nextInt(random(), 1, 3), 20, options)
            for (i in 0..<numDocs) {
                if (i == docWithVectors) {
                    writer.addDocument<IndexableField>(addId(doc.toDocument(), "42"))
                } else {
                    writer.addDocument<IndexableField>(emptyDoc)
                }
            }
            val reader: IndexReader = writer.reader
            val docWithVectorsID = docID(reader, "42")
            val termVectors: TermVectors = reader.termVectors()
            for (i in 0..9) {
                val docID: Int = random().nextInt(numDocs)
                val fields: Fields? = termVectors.get(docID)
                if (docID == docWithVectorsID) {
                    assertEquals(doc, fields!!)
                } else {
                    assertNull(fields)
                }
            }
            val fields: Fields? = termVectors.get(docWithVectorsID)
            assertEquals(doc, fields!!)
            reader.close()
            writer.close()
            dir.close()
        }
    }

    @Throws(IOException::class)
    open fun testHighFreqs() {
        val docFactory = RandomDocumentFactory(3, 5)
        for (options in validOptions()) {
            if (options == Options.NONE) {
                continue
            }
            val dir: Directory = newDirectory()
            val writer: RandomIndexWriter = RandomIndexWriter(random(), dir)
            val doc =
                docFactory.newDocument(TestUtil.nextInt(random(), 1, 2), atLeast(2000), options)
            writer.addDocument<IndexableField>(doc.toDocument())
            val reader: IndexReader = writer.reader
            assertEquals(doc as Fields?, reader.termVectors().get(0))
            reader.close()
            writer.close()
            dir.close()
        }
    }

    @Throws(IOException::class)
    open fun testLotsOfFields() {
        val fieldCount: Int = if (TEST_NIGHTLY) atLeast(100) else atLeast(10)
        val docFactory = RandomDocumentFactory(fieldCount, 10)
        for (options in validOptions()) {
            val dir: Directory = newDirectory()
            val writer: RandomIndexWriter = RandomIndexWriter(random(), dir)
            val doc =
                docFactory.newDocument(TestUtil.nextInt(random(), 5, fieldCount), 5, options)
            writer.addDocument<IndexableField>(doc.toDocument())
            val reader: IndexReader = writer.reader
            assertEquals(doc as Fields?, reader.termVectors().get(0))
            reader.close()
            writer.close()
            dir.close()
        }
    }

    // different options for the same field
    @Throws(IOException::class)
    open fun testMixedOptions() {
        val numFields: Int = TestUtil.nextInt(random(), 1, 3)
        val docFactory = RandomDocumentFactory(numFields, 10)
        for (options1 in validOptions()) {
            for (options2 in validOptions()) {
                if (options1 == options2) {
                    continue
                }
                val dir: Directory = newDirectory()
                val writer: RandomIndexWriter = RandomIndexWriter(random(), dir)
                val doc1 = docFactory.newDocument(numFields, 20, options1)
                val doc2 = docFactory.newDocument(numFields, 20, options2)
                writer.addDocument<IndexableField>(addId(doc1.toDocument(), "1"))
                writer.addDocument<IndexableField>(addId(doc2.toDocument(), "2"))
                val reader: IndexReader = writer.reader
                val doc1ID = docID(reader, "1")
                assertEquals(doc1 as Fields?, reader.termVectors().get(doc1ID))
                val doc2ID = docID(reader, "2")
                assertEquals(doc2 as Fields?, reader.termVectors().get(doc2ID))
                reader.close()
                writer.close()
                dir.close()
            }
        }
    }

    @Throws(IOException::class)
    open fun testRandom() {
        val docFactory = RandomDocumentFactory(5, 20)
        val numDocs: Int = atLeast(50)
        val docs = kotlin.arrayOfNulls<RandomDocument>(numDocs)
        for (i in 0..<numDocs) {
            docs[i] =
                docFactory.newDocument(
                    TestUtil.nextInt(random(), 1, 3),
                    TestUtil.nextInt(random(), 10, 50),
                    randomOptions()!!
                )
        }
        val dir: Directory = newDirectory()
        val writer: RandomIndexWriter = RandomIndexWriter(random(), dir)
        for (i in 0..<numDocs) {
            writer.addDocument<IndexableField>(addId(docs[i]!!.toDocument(), "" + i))
        }
        val reader: IndexReader = writer.reader
        val termVectors: TermVectors = reader.termVectors()
        for (i in 0..<numDocs) {
            val docID = docID(reader, "" + i)
            assertEquals(docs[i] as Fields?, termVectors.get(docID))
        }
        reader.close()
        writer.close()
        dir.close()
    }

    @Throws(IOException::class)
    private fun doTestMerge(indexSort: Sort?, allowDeletes: Boolean) {
        val docFactory = RandomDocumentFactory(5, 20)
        val numDocs: Int = if (TEST_NIGHTLY) atLeast(100) else atLeast(10)
        for (options in validOptions()) {
            val docs: MutableMap<String, RandomDocument> = mutableMapOf()
            for (i in 0..<numDocs) {
                docs[i.toString()] = docFactory.newDocument(TestUtil.nextInt(random(), 1, 3), atLeast(10), options)
            }
            val dir: Directory = newDirectory()
            val iwc = newIndexWriterConfig()
            if (indexSort != null) {
                iwc.setIndexSort(indexSort)
            }
            val writer: RandomIndexWriter = RandomIndexWriter(random(), dir, iwc)
            val liveDocIDs: MutableList<String> = mutableListOf()
            val ids: MutableList<String> = ArrayList<String>(docs.keys)
            ids.shuffle(random())
            val verifyTermVectors: Runnable =
                Runnable {
                    try {
                        maybeWrapWithMergingReader(writer.reader).use { reader ->
                            val termVectors: TermVectors = reader.termVectors()
                            for (id in liveDocIDs) {
                                val docID = docID(reader, id)
                                assertEquals(docs[id] as Fields?, termVectors.get(docID))
                            }
                        }
                    } catch (e: IOException) {
                        throw UncheckedIOException(e)
                    }
                }
            for (id in ids) {
                val doc = addId(docs.get(id)!!.toDocument(), id)
                if (indexSort != null) {
                    for (sortField in indexSort.sort) {
                        doc.add(
                            NumericDocValuesField(
                                sortField.field!!, TestUtil.nextInt(random(), 0, 1024).toLong()
                            )
                        )
                    }
                }
                if (random().nextInt(100) < 5) {
                    // add via foreign writer
                    val otherIwc = newIndexWriterConfig()
                    if (indexSort != null) {
                        otherIwc.setIndexSort(indexSort)
                    }
                    newDirectory().use { otherDir ->
                        RandomIndexWriter(random(), otherDir, otherIwc).use { otherIw ->
                            otherIw.addDocument<IndexableField>(doc)
                            otherIw.reader.use { otherReader ->
                                TestUtil.addIndexesSlowly(writer.w, otherReader)
                            }
                        }
                    }
                } else {
                    writer.addDocument<IndexableField>(doc)
                }
                liveDocIDs.add(id)
                if (allowDeletes && random().nextInt(100) < 20) {
                    val deleteId = liveDocIDs.removeAt(random().nextInt(liveDocIDs.size))
                    writer.deleteDocuments(Term("id", deleteId))
                }
                if (rarely()) {
                    writer.commit()
                    verifyTermVectors.run()
                }
                if (rarely()) {
                    writer.forceMerge(1)
                    verifyTermVectors.run()
                }
            }
            verifyTermVectors.run()
            writer.forceMerge(1)
            verifyTermVectors.run()
            IOUtils.close(writer, dir)
        }
    }

    @Throws(IOException::class)
    open fun testMerge() {
        doTestMerge(null, false)
    }

    @Throws(IOException::class)
    open fun testMergeWithDeletes() {
        doTestMerge(null, true)
    }

    @Throws(IOException::class)
    open fun testMergeWithIndexSort() {
        val sortFields: Array<SortField> = kotlin.arrayOfNulls<SortField>(TestUtil.nextInt(random(), 1, 2)) as Array<SortField>
        for (i in sortFields.indices) {
            sortFields[i] = SortField("sort_field_$i", SortField.Type.LONG)
        }
        doTestMerge(Sort(*sortFields), false)
    }

    @Throws(IOException::class)
    open fun testMergeWithIndexSortAndDeletes() {
        val sortFields: Array<SortField> = kotlin.arrayOfNulls<SortField>(TestUtil.nextInt(random(), 1, 2)) as Array<SortField>
        for (i in sortFields.indices) {
            sortFields[i] = SortField("sort_field_$i", SortField.Type.LONG)
        }
        doTestMerge(Sort(*sortFields), true)
    }

    // run random tests from different threads to make sure the per-thread clones
    // don't share mutable data
    @Throws(IOException::class, InterruptedException::class)
    open fun testClone() = runTest {
        val docFactory = RandomDocumentFactory(5, 20)
        val numDocs: Int = atLeast(50)
        for (options in validOptions()) {
            val docs = kotlin.arrayOfNulls<RandomDocument>(numDocs)
            for (i in 0..<numDocs) {
                docs[i] = docFactory.newDocument(TestUtil.nextInt(random(), 1, 3), atLeast(10), options)
            }
            val dir: Directory = newDirectory()
            val writer: RandomIndexWriter = RandomIndexWriter(random(), dir)
            for (i in 0..<numDocs) {
                writer.addDocument<IndexableField>(addId(docs[i]!!.toDocument(), "" + i))
            }
            val reader: IndexReader = writer.reader
            val termVectors: TermVectors = reader.termVectors()
            for (i in 0..<numDocs) {
                val docID = docID(reader, "" + i)
                assertEquals(docs[i] as Fields?, termVectors.get(docID))
            }

            val exception: AtomicReference<Throwable?> = AtomicReference(null)
            val jobs: Array<Job> = Array(2) {
                launch {
                    try {
                        val termVectors: TermVectors = reader.termVectors()
                        for (i in 0..<atLeast(100)) {
                            val idx: Int = random().nextInt(numDocs)
                            val docID = docID(reader, "" + idx)
                            assertEquals(docs[idx] as Fields?, termVectors.get(docID))
                        }
                    } catch (t: Throwable) {
                        exception.store(t)
                    }
                }
            }
            for (job in jobs) {
                job.join()
            }
            reader.close()
            writer.close()
            dir.close()
            assertNull("One thread threw an exception", exception.load())
        }
    }

    @Throws(Exception::class)
    open fun testPostingsEnumFreqs() {
        val dir: Directory = newDirectory()
        val iwc =
            IndexWriterConfig(
                object : Analyzer() {
                    protected override fun createComponents(fieldName: String): Analyzer.TokenStreamComponents {
                        return Analyzer.TokenStreamComponents(MockTokenizer())
                    }
                })
        val iw = IndexWriter(dir, iwc)
        val doc = Document()
        val ft = FieldType(TextField.TYPE_NOT_STORED)
        ft.setStoreTermVectors(true)
        doc.add(Field("foo", "bar bar", ft))
        iw.addDocument(doc)
        val reader: DirectoryReader = DirectoryReader.open(iw)

        val terms: Terms = getOnlyLeafReader(reader).termVectors().get(0, "foo")!!
        val termsEnum: TermsEnum = terms.iterator()
        assertNotNull(termsEnum)
        assertEquals(newBytesRef("bar"), termsEnum.next())

        // simple use (FREQS)
        var postings: PostingsEnum = termsEnum.postings(null)!!
        assertEquals(-1, postings.docID().toLong())
        assertEquals(0, postings.nextDoc().toLong())
        assertEquals(2, postings.freq().toLong())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), postings.nextDoc().toLong())

        // termsenum reuse (FREQS)
        var postings2: PostingsEnum = termsEnum.postings(postings)!!
        assertNotNull(postings2)
        // and it had better work
        assertEquals(-1, postings2.docID().toLong())
        assertEquals(0, postings2.nextDoc().toLong())
        assertEquals(2, postings2.freq().toLong())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), postings2.nextDoc().toLong())

        // asking for docs only: ok
        val docsOnly: PostingsEnum = termsEnum.postings(null, PostingsEnum.NONE.toInt())!!
        assertEquals(-1, docsOnly.docID().toLong())
        assertEquals(0, docsOnly.nextDoc().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsOnly.freq() == 1 || docsOnly.freq() == 2)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsOnly.nextDoc().toLong())
        // reuse that too
        val docsOnly2: PostingsEnum = termsEnum.postings(docsOnly, PostingsEnum.NONE.toInt())!!
        assertNotNull(docsOnly2)
        // and it had better work
        assertEquals(-1, docsOnly2.docID().toLong())
        assertEquals(0, docsOnly2.nextDoc().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsOnly.freq() == 1 || docsOnly.freq() == 2)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsOnly2.nextDoc().toLong())

        // asking for any flags: ok
        for (flag in intArrayOf(
            PostingsEnum.NONE.toInt(),
            PostingsEnum.FREQS.toInt(),
            PostingsEnum.POSITIONS.toInt(),
            PostingsEnum.PAYLOADS.toInt(),
            PostingsEnum.OFFSETS.toInt(),
            PostingsEnum.ALL.toInt()
        )) {
            postings = termsEnum.postings(null, flag)!!
            assertEquals(-1, postings.docID().toLong())
            assertEquals(0, postings.nextDoc().toLong())
            if (flag != PostingsEnum.NONE.toInt()) {
                assertEquals(2, postings.freq().toLong())
            }
            assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), postings.nextDoc().toLong())
            // reuse that too
            postings2 = termsEnum.postings(postings, flag)!!
            assertNotNull(postings2)
            // and it had better work
            assertEquals(-1, postings2.docID().toLong())
            assertEquals(0, postings2.nextDoc().toLong())
            if (flag != PostingsEnum.NONE.toInt()) {
                assertEquals(2, postings2.freq().toLong())
            }
            assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), postings2.nextDoc().toLong())
        }

        iw.close()
        reader.close()
        dir.close()
    }

    @Throws(Exception::class)
    open fun testPostingsEnumPositions() {
        val dir: Directory = newDirectory()
        val iwc =
            IndexWriterConfig(
                object : Analyzer() {
                    protected override fun createComponents(fieldName: String): Analyzer.TokenStreamComponents {
                        return Analyzer.TokenStreamComponents(MockTokenizer())
                    }
                })
        val iw = IndexWriter(dir, iwc)
        val doc = Document()
        val ft = FieldType(TextField.TYPE_NOT_STORED)
        ft.setStoreTermVectors(true)
        ft.setStoreTermVectorPositions(true)
        doc.add(Field("foo", "bar bar", ft))
        iw.addDocument(doc)
        val reader: DirectoryReader = DirectoryReader.open(iw)

        val terms: Terms = getOnlyLeafReader(reader).termVectors().get(0, "foo")!!
        val termsEnum: TermsEnum = terms.iterator()
        assertNotNull(termsEnum)
        assertEquals(newBytesRef("bar"), termsEnum.next())

        // simple use (FREQS)
        val postings: PostingsEnum = termsEnum.postings(null)!!
        assertEquals(-1, postings.docID().toLong())
        assertEquals(0, postings.nextDoc().toLong())
        assertEquals(2, postings.freq().toLong())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), postings.nextDoc().toLong())

        // termsenum reuse (FREQS)
        val postings2: PostingsEnum = termsEnum.postings(postings)!!
        assertNotNull(postings2)
        // and it had better work
        assertEquals(-1, postings2.docID().toLong())
        assertEquals(0, postings2.nextDoc().toLong())
        assertEquals(2, postings2.freq().toLong())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), postings2.nextDoc().toLong())

        // asking for docs only: ok
        val docsOnly: PostingsEnum = termsEnum.postings(null, PostingsEnum.NONE.toInt())!!
        assertEquals(-1, docsOnly.docID().toLong())
        assertEquals(0, docsOnly.nextDoc().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsOnly.freq() == 1 || docsOnly.freq() == 2)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsOnly.nextDoc().toLong())
        // reuse that too
        val docsOnly2: PostingsEnum = termsEnum.postings(docsOnly, PostingsEnum.NONE.toInt())!!
        assertNotNull(docsOnly2)
        // and it had better work
        assertEquals(-1, docsOnly2.docID().toLong())
        assertEquals(0, docsOnly2.nextDoc().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsOnly2.freq() == 1 || docsOnly2.freq() == 2)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsOnly2.nextDoc().toLong())

        // asking for positions, ok
        var docsAndPositionsEnum: PostingsEnum = termsEnum.postings(null, PostingsEnum.POSITIONS.toInt())!!
        assertEquals(-1, docsAndPositionsEnum.docID().toLong())
        assertEquals(0, docsAndPositionsEnum.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum.freq().toLong())
        assertEquals(0, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum.endOffset().toLong())
        assertNull(docsAndPositionsEnum.payload)
        assertEquals(1, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum.endOffset().toLong())
        assertNull(docsAndPositionsEnum.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum.nextDoc().toLong())

        // now reuse the positions
        var docsAndPositionsEnum2: PostingsEnum = termsEnum.postings(docsAndPositionsEnum, PostingsEnum.POSITIONS.toInt())!!
        assertEquals(-1, docsAndPositionsEnum2.docID().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum2.freq().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum2.endOffset().toLong())
        assertNull(docsAndPositionsEnum2.payload)
        assertEquals(1, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum2.endOffset().toLong())
        assertNull(docsAndPositionsEnum2.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum2.nextDoc().toLong())

        // payloads, offsets, etc don't cause an error if they aren't there
        docsAndPositionsEnum = termsEnum.postings(null, PostingsEnum.PAYLOADS.toInt())!!
        assertNotNull(docsAndPositionsEnum)
        // but make sure they work
        assertEquals(-1, docsAndPositionsEnum.docID().toLong())
        assertEquals(0, docsAndPositionsEnum.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum.freq().toLong())
        assertEquals(0, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum.endOffset().toLong())
        assertNull(docsAndPositionsEnum.payload)
        assertEquals(1, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum.endOffset().toLong())
        assertNull(docsAndPositionsEnum.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum.nextDoc().toLong())
        // reuse
        docsAndPositionsEnum2 = termsEnum.postings(docsAndPositionsEnum, PostingsEnum.PAYLOADS.toInt())!!
        assertEquals(-1, docsAndPositionsEnum2.docID().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum2.freq().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum2.endOffset().toLong())
        assertNull(docsAndPositionsEnum2.payload)
        assertEquals(1, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum2.endOffset().toLong())
        assertNull(docsAndPositionsEnum2.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum2.nextDoc().toLong())

        docsAndPositionsEnum = termsEnum.postings(null, PostingsEnum.OFFSETS.toInt())!!
        assertNotNull(docsAndPositionsEnum)
        assertEquals(-1, docsAndPositionsEnum.docID().toLong())
        assertEquals(0, docsAndPositionsEnum.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum.freq().toLong())
        assertEquals(0, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum.endOffset().toLong())
        assertNull(docsAndPositionsEnum.payload)
        assertEquals(1, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum.endOffset().toLong())
        assertNull(docsAndPositionsEnum.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum.nextDoc().toLong())
        // reuse
        docsAndPositionsEnum2 = termsEnum.postings(docsAndPositionsEnum, PostingsEnum.OFFSETS.toInt())!!
        assertEquals(-1, docsAndPositionsEnum2.docID().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum2.freq().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum2.endOffset().toLong())
        assertNull(docsAndPositionsEnum2.payload)
        assertEquals(1, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum2.endOffset().toLong())
        assertNull(docsAndPositionsEnum2.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum2.nextDoc().toLong())

        docsAndPositionsEnum = termsEnum.postings(null, PostingsEnum.ALL.toInt())!!
        assertNotNull(docsAndPositionsEnum)
        assertEquals(-1, docsAndPositionsEnum.docID().toLong())
        assertEquals(0, docsAndPositionsEnum.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum.freq().toLong())
        assertEquals(0, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum.endOffset().toLong())
        assertNull(docsAndPositionsEnum.payload)
        assertEquals(1, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum.endOffset().toLong())
        assertNull(docsAndPositionsEnum.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum.nextDoc().toLong())
        docsAndPositionsEnum2 = termsEnum.postings(docsAndPositionsEnum, PostingsEnum.ALL.toInt())!!
        assertEquals(-1, docsAndPositionsEnum2.docID().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum2.freq().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum2.endOffset().toLong())
        assertNull(docsAndPositionsEnum2.payload)
        assertEquals(1, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum2.endOffset().toLong())
        assertNull(docsAndPositionsEnum2.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum2.nextDoc().toLong())

        iw.close()
        reader.close()
        dir.close()
    }

    @Throws(Exception::class)
    open fun testPostingsEnumOffsets() {
        val dir: Directory = newDirectory()
        val iwc =
            IndexWriterConfig(
                object : Analyzer() {
                    protected override fun createComponents(fieldName: String): Analyzer.TokenStreamComponents {
                        return Analyzer.TokenStreamComponents(MockTokenizer())
                    }
                })
        val iw = IndexWriter(dir, iwc)
        val doc = Document()
        val ft = FieldType(TextField.TYPE_NOT_STORED)
        ft.setStoreTermVectors(true)
        ft.setStoreTermVectorPositions(true)
        ft.setStoreTermVectorOffsets(true)
        doc.add(Field("foo", "bar bar", ft))
        iw.addDocument(doc)
        val reader: DirectoryReader = DirectoryReader.open(iw)

        val terms: Terms = getOnlyLeafReader(reader).termVectors().get(0, "foo")!!
        val termsEnum: TermsEnum = terms.iterator()
        assertNotNull(termsEnum)
        assertEquals(newBytesRef("bar"), termsEnum.next())

        // simple usage (FREQS)
        val postings: PostingsEnum = termsEnum.postings(null)!!
        assertEquals(-1, postings.docID().toLong())
        assertEquals(0, postings.nextDoc().toLong())
        assertEquals(2, postings.freq().toLong())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), postings.nextDoc().toLong())

        // termsenum reuse (FREQS)
        val postings2: PostingsEnum = termsEnum.postings(postings)!!
        assertNotNull(postings2)
        // and it had better work
        assertEquals(-1, postings2.docID().toLong())
        assertEquals(0, postings2.nextDoc().toLong())
        assertEquals(2, postings2.freq().toLong())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), postings2.nextDoc().toLong())

        // asking for docs only: ok
        val docsOnly: PostingsEnum = termsEnum.postings(null, PostingsEnum.NONE.toInt())!!
        assertEquals(-1, docsOnly.docID().toLong())
        assertEquals(0, docsOnly.nextDoc().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsOnly.freq() == 1 || docsOnly.freq() == 2)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsOnly.nextDoc().toLong())
        // reuse that too
        val docsOnly2: PostingsEnum = termsEnum.postings(docsOnly, PostingsEnum.NONE.toInt())!!
        assertNotNull(docsOnly2)
        // and it had better work
        assertEquals(-1, docsOnly2.docID().toLong())
        assertEquals(0, docsOnly2.nextDoc().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsOnly2.freq() == 1 || docsOnly2.freq() == 2)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsOnly2.nextDoc().toLong())

        // asking for positions, ok
        var docsAndPositionsEnum: PostingsEnum = termsEnum.postings(null, PostingsEnum.POSITIONS.toInt())!!
        assertEquals(-1, docsAndPositionsEnum.docID().toLong())
        assertEquals(0, docsAndPositionsEnum.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum.freq().toLong())
        assertEquals(0, docsAndPositionsEnum.nextPosition().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum.startOffset() == -1 || docsAndPositionsEnum.startOffset() == 0)
        assertTrue(docsAndPositionsEnum.endOffset() == -1 || docsAndPositionsEnum.endOffset() == 3)
        assertNull(docsAndPositionsEnum.payload)
        assertEquals(1, docsAndPositionsEnum.nextPosition().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum.startOffset() == -1 || docsAndPositionsEnum.startOffset() == 4)
        assertTrue(docsAndPositionsEnum.endOffset() == -1 || docsAndPositionsEnum.endOffset() == 7)
        assertNull(docsAndPositionsEnum.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum.nextDoc().toLong())

        // now reuse the positions
        var docsAndPositionsEnum2: PostingsEnum = termsEnum.postings(docsAndPositionsEnum, PostingsEnum.POSITIONS.toInt())!!
        assertEquals(-1, docsAndPositionsEnum2.docID().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum2.freq().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextPosition().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(
            docsAndPositionsEnum2.startOffset() == -1 || docsAndPositionsEnum2.startOffset() == 0
        )
        assertTrue(docsAndPositionsEnum2.endOffset() == -1 || docsAndPositionsEnum2.endOffset() == 3)
        assertNull(docsAndPositionsEnum2.payload)
        assertEquals(1, docsAndPositionsEnum2.nextPosition().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(
            docsAndPositionsEnum2.startOffset() == -1 || docsAndPositionsEnum2.startOffset() == 4
        )
        assertTrue(docsAndPositionsEnum2.endOffset() == -1 || docsAndPositionsEnum2.endOffset() == 7)
        assertNull(docsAndPositionsEnum2.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum2.nextDoc().toLong())

        // payloads don't cause an error if they aren't there
        docsAndPositionsEnum = termsEnum.postings(null, PostingsEnum.PAYLOADS.toInt())!!
        assertNotNull(docsAndPositionsEnum)
        // but make sure they work
        assertEquals(-1, docsAndPositionsEnum.docID().toLong())
        assertEquals(0, docsAndPositionsEnum.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum.freq().toLong())
        assertEquals(0, docsAndPositionsEnum.nextPosition().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum.startOffset() == -1 || docsAndPositionsEnum.startOffset() == 0)
        assertTrue(docsAndPositionsEnum.endOffset() == -1 || docsAndPositionsEnum.endOffset() == 3)
        assertNull(docsAndPositionsEnum.payload)
        assertEquals(1, docsAndPositionsEnum.nextPosition().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum.startOffset() == -1 || docsAndPositionsEnum.startOffset() == 4)
        assertTrue(docsAndPositionsEnum.endOffset() == -1 || docsAndPositionsEnum.endOffset() == 7)
        assertNull(docsAndPositionsEnum.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum.nextDoc().toLong())
        // reuse
        docsAndPositionsEnum2 = termsEnum.postings(docsAndPositionsEnum, PostingsEnum.PAYLOADS.toInt())!!
        assertEquals(-1, docsAndPositionsEnum2.docID().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum2.freq().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextPosition().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(
            docsAndPositionsEnum2.startOffset() == -1 || docsAndPositionsEnum2.startOffset() == 0
        )
        assertTrue(docsAndPositionsEnum2.endOffset() == -1 || docsAndPositionsEnum2.endOffset() == 3)
        assertNull(docsAndPositionsEnum2.payload)
        assertEquals(1, docsAndPositionsEnum2.nextPosition().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(
            docsAndPositionsEnum2.startOffset() == -1 || docsAndPositionsEnum2.startOffset() == 4
        )
        assertTrue(docsAndPositionsEnum2.endOffset() == -1 || docsAndPositionsEnum2.endOffset() == 7)
        assertNull(docsAndPositionsEnum2.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum2.nextDoc().toLong())

        docsAndPositionsEnum = termsEnum.postings(null, PostingsEnum.OFFSETS.toInt())!!
        assertNotNull(docsAndPositionsEnum)
        assertEquals(-1, docsAndPositionsEnum.docID().toLong())
        assertEquals(0, docsAndPositionsEnum.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum.freq().toLong())
        assertEquals(0, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(0, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(3, docsAndPositionsEnum.endOffset().toLong())
        assertNull(docsAndPositionsEnum.payload)
        assertEquals(1, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(4, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(7, docsAndPositionsEnum.endOffset().toLong())
        assertNull(docsAndPositionsEnum.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum.nextDoc().toLong())
        // reuse
        docsAndPositionsEnum2 = termsEnum.postings(docsAndPositionsEnum, PostingsEnum.OFFSETS.toInt())!!
        assertEquals(-1, docsAndPositionsEnum2.docID().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum2.freq().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(0, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(3, docsAndPositionsEnum2.endOffset().toLong())
        assertNull(docsAndPositionsEnum2.payload)
        assertEquals(1, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(4, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(7, docsAndPositionsEnum2.endOffset().toLong())
        assertNull(docsAndPositionsEnum2.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum2.nextDoc().toLong())

        docsAndPositionsEnum = termsEnum.postings(null, PostingsEnum.ALL.toInt())!!
        assertNotNull(docsAndPositionsEnum)
        assertEquals(-1, docsAndPositionsEnum.docID().toLong())
        assertEquals(0, docsAndPositionsEnum.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum.freq().toLong())
        assertEquals(0, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(0, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(3, docsAndPositionsEnum.endOffset().toLong())
        assertNull(docsAndPositionsEnum.payload)
        assertEquals(1, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(4, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(7, docsAndPositionsEnum.endOffset().toLong())
        assertNull(docsAndPositionsEnum.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum.nextDoc().toLong())
        docsAndPositionsEnum2 = termsEnum.postings(docsAndPositionsEnum, PostingsEnum.ALL.toInt())!!
        assertEquals(-1, docsAndPositionsEnum2.docID().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum2.freq().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(0, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(3, docsAndPositionsEnum2.endOffset().toLong())
        assertNull(docsAndPositionsEnum2.payload)
        assertEquals(1, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(4, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(7, docsAndPositionsEnum2.endOffset().toLong())
        assertNull(docsAndPositionsEnum2.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum2.nextDoc().toLong())

        iw.close()
        reader.close()
        dir.close()
    }

    @Throws(Exception::class)
    open fun testPostingsEnumOffsetsWithoutPositions() {
        val dir: Directory = newDirectory()
        val iwc =
            IndexWriterConfig(
                object : Analyzer() {
                    protected override fun createComponents(fieldName: String): Analyzer.TokenStreamComponents {
                        return Analyzer.TokenStreamComponents(MockTokenizer())
                    }
                })
        val iw = IndexWriter(dir, iwc)
        val doc = Document()
        val ft = FieldType(TextField.TYPE_NOT_STORED)
        ft.setStoreTermVectors(true)
        ft.setStoreTermVectorOffsets(true)
        doc.add(Field("foo", "bar bar", ft))
        iw.addDocument(doc)
        val reader: DirectoryReader = DirectoryReader.open(iw)

        val terms: Terms = getOnlyLeafReader(reader).termVectors().get(0, "foo")!!
        val termsEnum: TermsEnum = terms.iterator()
        assertNotNull(termsEnum)
        assertEquals(newBytesRef("bar"), termsEnum.next())

        // simple usage (FREQS)
        val postings: PostingsEnum = termsEnum.postings(null)!!
        assertEquals(-1, postings.docID().toLong())
        assertEquals(0, postings.nextDoc().toLong())
        assertEquals(2, postings.freq().toLong())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), postings.nextDoc().toLong())

        // termsenum reuse (FREQS)
        val postings2: PostingsEnum = termsEnum.postings(postings)!!
        assertNotNull(postings2)
        // and it had better work
        assertEquals(-1, postings2.docID().toLong())
        assertEquals(0, postings2.nextDoc().toLong())
        assertEquals(2, postings2.freq().toLong())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), postings2.nextDoc().toLong())

        // asking for docs only: ok
        val docsOnly: PostingsEnum = termsEnum.postings(null, PostingsEnum.NONE.toInt())!!
        assertEquals(-1, docsOnly.docID().toLong())
        assertEquals(0, docsOnly.nextDoc().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsOnly.freq() == 1 || docsOnly.freq() == 2)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsOnly.nextDoc().toLong())
        // reuse that too
        val docsOnly2: PostingsEnum = termsEnum.postings(docsOnly, PostingsEnum.NONE.toInt())!!
        assertNotNull(docsOnly2)
        // and it had better work
        assertEquals(-1, docsOnly2.docID().toLong())
        assertEquals(0, docsOnly2.nextDoc().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsOnly2.freq() == 1 || docsOnly2.freq() == 2)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsOnly2.nextDoc().toLong())

        // asking for positions, ok
        var docsAndPositionsEnum: PostingsEnum = termsEnum.postings(null, PostingsEnum.POSITIONS.toInt())!!
        assertEquals(-1, docsAndPositionsEnum.docID().toLong())
        assertEquals(0, docsAndPositionsEnum.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum.freq().toLong())
        assertEquals(-1, docsAndPositionsEnum.nextPosition().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum.startOffset() == -1 || docsAndPositionsEnum.startOffset() == 0)
        assertTrue(docsAndPositionsEnum.endOffset() == -1 || docsAndPositionsEnum.endOffset() == 3)
        assertNull(docsAndPositionsEnum.payload)
        assertEquals(-1, docsAndPositionsEnum.nextPosition().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum.startOffset() == -1 || docsAndPositionsEnum.startOffset() == 4)
        assertTrue(docsAndPositionsEnum.endOffset() == -1 || docsAndPositionsEnum.endOffset() == 7)
        assertNull(docsAndPositionsEnum.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum.nextDoc().toLong())

        // now reuse the positions
        var docsAndPositionsEnum2: PostingsEnum = termsEnum.postings(docsAndPositionsEnum, PostingsEnum.POSITIONS.toInt())!!
        assertEquals(-1, docsAndPositionsEnum2.docID().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum2.freq().toLong())
        assertEquals(-1, docsAndPositionsEnum2.nextPosition().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum2.startOffset() == -1 || docsAndPositionsEnum2.startOffset() == 0)
        assertTrue(docsAndPositionsEnum2.endOffset() == -1 || docsAndPositionsEnum2.endOffset() == 3)
        assertNull(docsAndPositionsEnum2.payload)
        assertEquals(-1, docsAndPositionsEnum2.nextPosition().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum2.startOffset() == -1 || docsAndPositionsEnum2.startOffset() == 4)
        assertTrue(docsAndPositionsEnum2.endOffset() == -1 || docsAndPositionsEnum2.endOffset() == 7)
        assertNull(docsAndPositionsEnum2.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum2.nextDoc().toLong())

        // payloads don't cause an error if they aren't there
        docsAndPositionsEnum = termsEnum.postings(null, PostingsEnum.PAYLOADS.toInt())!!
        assertNotNull(docsAndPositionsEnum)
        // but make sure they work
        assertEquals(-1, docsAndPositionsEnum.docID().toLong())
        assertEquals(0, docsAndPositionsEnum.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum.freq().toLong())
        assertEquals(-1, docsAndPositionsEnum.nextPosition().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum.startOffset() == -1 || docsAndPositionsEnum.startOffset() == 0)
        assertTrue(docsAndPositionsEnum.endOffset() == -1 || docsAndPositionsEnum.endOffset() == 3)
        assertNull(docsAndPositionsEnum.payload)
        assertEquals(-1, docsAndPositionsEnum.nextPosition().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum.startOffset() == -1 || docsAndPositionsEnum.startOffset() == 4)
        assertTrue(docsAndPositionsEnum.endOffset() == -1 || docsAndPositionsEnum.endOffset() == 7)
        assertNull(docsAndPositionsEnum.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum.nextDoc().toLong())
        // reuse
        docsAndPositionsEnum2 = termsEnum.postings(docsAndPositionsEnum, PostingsEnum.PAYLOADS.toInt())!!
        assertEquals(-1, docsAndPositionsEnum2.docID().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum2.freq().toLong())
        assertEquals(-1, docsAndPositionsEnum2.nextPosition().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum2.startOffset() == -1 || docsAndPositionsEnum2.startOffset() == 0)
        assertTrue(docsAndPositionsEnum2.endOffset() == -1 || docsAndPositionsEnum2.endOffset() == 3)
        assertNull(docsAndPositionsEnum2.payload)
        assertEquals(-1, docsAndPositionsEnum2.nextPosition().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum2.startOffset() == -1 || docsAndPositionsEnum2.startOffset() == 4)
        assertTrue(docsAndPositionsEnum2.endOffset() == -1 || docsAndPositionsEnum2.endOffset() == 7)
        assertNull(docsAndPositionsEnum2.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum2.nextDoc().toLong())

        docsAndPositionsEnum = termsEnum.postings(null, PostingsEnum.OFFSETS.toInt())!!
        assertNotNull(docsAndPositionsEnum)
        assertEquals(-1, docsAndPositionsEnum.docID().toLong())
        assertEquals(0, docsAndPositionsEnum.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum.freq().toLong())
        assertEquals(-1, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(0, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(3, docsAndPositionsEnum.endOffset().toLong())
        assertNull(docsAndPositionsEnum.payload)
        assertEquals(-1, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(4, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(7, docsAndPositionsEnum.endOffset().toLong())
        assertNull(docsAndPositionsEnum.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum.nextDoc().toLong())
        // reuse
        docsAndPositionsEnum2 = termsEnum.postings(docsAndPositionsEnum, PostingsEnum.OFFSETS.toInt())!!
        assertEquals(-1, docsAndPositionsEnum2.docID().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum2.freq().toLong())
        assertEquals(-1, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(0, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(3, docsAndPositionsEnum2.endOffset().toLong())
        assertNull(docsAndPositionsEnum2.payload)
        assertEquals(-1, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(4, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(7, docsAndPositionsEnum2.endOffset().toLong())
        assertNull(docsAndPositionsEnum2.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum2.nextDoc().toLong())

        docsAndPositionsEnum = termsEnum.postings(null, PostingsEnum.ALL.toInt())!!
        assertNotNull(docsAndPositionsEnum)
        assertEquals(-1, docsAndPositionsEnum.docID().toLong())
        assertEquals(0, docsAndPositionsEnum.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum.freq().toLong())
        assertEquals(-1, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(0, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(3, docsAndPositionsEnum.endOffset().toLong())
        assertNull(docsAndPositionsEnum.payload)
        assertEquals(-1, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(4, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(7, docsAndPositionsEnum.endOffset().toLong())
        assertNull(docsAndPositionsEnum.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum.nextDoc().toLong())
        docsAndPositionsEnum2 = termsEnum.postings(docsAndPositionsEnum, PostingsEnum.ALL.toInt())!!
        assertEquals(-1, docsAndPositionsEnum2.docID().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum2.freq().toLong())
        assertEquals(-1, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(0, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(3, docsAndPositionsEnum2.endOffset().toLong())
        assertNull(docsAndPositionsEnum2.payload)
        assertEquals(-1, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(4, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(7, docsAndPositionsEnum2.endOffset().toLong())
        assertNull(docsAndPositionsEnum2.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum2.nextDoc().toLong())

        iw.close()
        reader.close()
        dir.close()
    }

    @Throws(Exception::class)
    open fun testPostingsEnumPayloads() {
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig(/*null*/)
        val iw = IndexWriter(dir, iwc)
        val doc = Document()
        val token1 = Token("bar", 0, 3)
        token1.payload = newBytesRef("pay1")
        val token2 = Token("bar", 4, 7)
        token2.payload = newBytesRef("pay2")
        val ft = FieldType(TextField.TYPE_NOT_STORED)
        ft.setStoreTermVectors(true)
        ft.setStoreTermVectorPositions(true)
        ft.setStoreTermVectorPayloads(true)
        doc.add(Field("foo", CannedTokenStream(token1, token2), ft))
        iw.addDocument(doc)
        val reader: DirectoryReader = DirectoryReader.open(iw)

        val terms: Terms = getOnlyLeafReader(reader).termVectors().get(0, "foo")!!
        val termsEnum: TermsEnum = terms.iterator()
        assertNotNull(termsEnum)
        assertEquals(newBytesRef("bar"), termsEnum.next())

        // sugar method (FREQS)
        val postings: PostingsEnum = termsEnum.postings(null)!!
        assertEquals(-1, postings.docID().toLong())
        assertEquals(0, postings.nextDoc().toLong())
        assertEquals(2, postings.freq().toLong())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), postings.nextDoc().toLong())

        // termsenum reuse (FREQS)
        val postings2: PostingsEnum = termsEnum.postings(postings)!!
        assertNotNull(postings2)
        // and it had better work
        assertEquals(-1, postings2.docID().toLong())
        assertEquals(0, postings2.nextDoc().toLong())
        assertEquals(2, postings2.freq().toLong())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), postings2.nextDoc().toLong())

        // asking for docs only: ok
        val docsOnly: PostingsEnum = termsEnum.postings(null, PostingsEnum.NONE.toInt())!!
        assertEquals(-1, docsOnly.docID().toLong())
        assertEquals(0, docsOnly.nextDoc().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsOnly.freq() == 1 || docsOnly.freq() == 2)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsOnly.nextDoc().toLong())
        // reuse that too
        val docsOnly2: PostingsEnum = termsEnum.postings(docsOnly, PostingsEnum.NONE.toInt())!!
        assertNotNull(docsOnly2)
        // and it had better work
        assertEquals(-1, docsOnly2.docID().toLong())
        assertEquals(0, docsOnly2.nextDoc().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsOnly2.freq() == 1 || docsOnly2.freq() == 2)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsOnly2.nextDoc().toLong())

        // asking for positions, ok
        var docsAndPositionsEnum: PostingsEnum = termsEnum.postings(null, PostingsEnum.POSITIONS.toInt())!!
        assertEquals(-1, docsAndPositionsEnum.docID().toLong())
        assertEquals(0, docsAndPositionsEnum.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum.freq().toLong())
        assertEquals(0, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum.endOffset().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(
            docsAndPositionsEnum.payload == null
                    || newBytesRef("pay1") == docsAndPositionsEnum.payload
        )
        assertEquals(1, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum.endOffset().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(
            docsAndPositionsEnum.payload == null
                    || newBytesRef("pay2") == docsAndPositionsEnum.payload
        )
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum.nextDoc().toLong())

        // now reuse the positions
        var docsAndPositionsEnum2: PostingsEnum = termsEnum.postings(docsAndPositionsEnum, PostingsEnum.POSITIONS.toInt())!!
        assertEquals(-1, docsAndPositionsEnum2.docID().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum2.freq().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum2.endOffset().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum2.payload == null || newBytesRef("pay1") == docsAndPositionsEnum2.payload)
        assertEquals(1, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum2.endOffset().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum2.payload == null || newBytesRef("pay2") == docsAndPositionsEnum2.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum2.nextDoc().toLong())

        // payloads
        docsAndPositionsEnum = termsEnum.postings(null, PostingsEnum.PAYLOADS.toInt())!!
        assertNotNull(docsAndPositionsEnum)
        assertEquals(-1, docsAndPositionsEnum.docID().toLong())
        assertEquals(0, docsAndPositionsEnum.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum.freq().toLong())
        assertEquals(0, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum.endOffset().toLong())
        assertEquals(newBytesRef("pay1"), docsAndPositionsEnum.payload)
        assertEquals(1, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum.endOffset().toLong())
        assertEquals(newBytesRef("pay2"), docsAndPositionsEnum.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum.nextDoc().toLong())
        // reuse
        docsAndPositionsEnum2 = termsEnum.postings(docsAndPositionsEnum, PostingsEnum.PAYLOADS.toInt())!!
        assertEquals(-1, docsAndPositionsEnum2.docID().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum2.freq().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum2.endOffset().toLong())
        assertEquals(newBytesRef("pay1"), docsAndPositionsEnum2.payload)
        assertEquals(1, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum2.endOffset().toLong())
        assertEquals(newBytesRef("pay2"), docsAndPositionsEnum2.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum2.nextDoc().toLong())

        docsAndPositionsEnum = termsEnum.postings(null, PostingsEnum.OFFSETS.toInt())!!
        assertNotNull(docsAndPositionsEnum)
        assertEquals(-1, docsAndPositionsEnum.docID().toLong())
        assertEquals(0, docsAndPositionsEnum.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum.freq().toLong())
        assertEquals(0, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum.endOffset().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum.payload == null || newBytesRef("pay1") == docsAndPositionsEnum.payload)
        assertEquals(1, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum.endOffset().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum.payload == null || newBytesRef("pay2") == docsAndPositionsEnum.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum.nextDoc().toLong())
        // reuse
        docsAndPositionsEnum2 = termsEnum.postings(docsAndPositionsEnum, PostingsEnum.OFFSETS.toInt())!!
        assertEquals(-1, docsAndPositionsEnum2.docID().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum2.freq().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum2.endOffset().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum2.payload == null || newBytesRef("pay1") == docsAndPositionsEnum2.payload)
        assertEquals(1, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum2.endOffset().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum2.payload == null || newBytesRef("pay2") == docsAndPositionsEnum2.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum2.nextDoc().toLong())

        docsAndPositionsEnum = termsEnum.postings(null, PostingsEnum.ALL.toInt())!!
        assertNotNull(docsAndPositionsEnum)
        assertEquals(-1, docsAndPositionsEnum.docID().toLong())
        assertEquals(0, docsAndPositionsEnum.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum.freq().toLong())
        assertEquals(0, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum.endOffset().toLong())
        assertEquals(newBytesRef("pay1"), docsAndPositionsEnum.payload)
        assertEquals(1, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum.endOffset().toLong())
        assertEquals(newBytesRef("pay2"), docsAndPositionsEnum.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum.nextDoc().toLong())
        docsAndPositionsEnum2 = termsEnum.postings(docsAndPositionsEnum, PostingsEnum.ALL.toInt())!!
        assertEquals(-1, docsAndPositionsEnum2.docID().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum2.freq().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum2.endOffset().toLong())
        assertEquals(newBytesRef("pay1"), docsAndPositionsEnum2.payload)
        assertEquals(1, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(-1, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(-1, docsAndPositionsEnum2.endOffset().toLong())
        assertEquals(newBytesRef("pay2"), docsAndPositionsEnum2.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum2.nextDoc().toLong())

        iw.close()
        reader.close()
        dir.close()
    }

    @Throws(Exception::class)
    open fun testPostingsEnumAll() {
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig(/*null*/)
        val iw = IndexWriter(dir, iwc)
        val doc = Document()
        val token1 = Token("bar", 0, 3)
        token1.payload = newBytesRef("pay1")
        val token2 = Token("bar", 4, 7)
        token2.payload = newBytesRef("pay2")
        val ft = FieldType(TextField.TYPE_NOT_STORED)
        ft.setStoreTermVectors(true)
        ft.setStoreTermVectorPositions(true)
        ft.setStoreTermVectorPayloads(true)
        ft.setStoreTermVectorOffsets(true)
        doc.add(Field("foo", CannedTokenStream(token1, token2), ft))
        iw.addDocument(doc)
        val reader: DirectoryReader = DirectoryReader.open(iw)

        val terms: Terms = getOnlyLeafReader(reader).termVectors().get(0, "foo")!!
        val termsEnum: TermsEnum = terms.iterator()
        assertNotNull(termsEnum)
        assertEquals(newBytesRef("bar"), termsEnum.next())

        // sugar method (FREQS)
        val postings: PostingsEnum = termsEnum.postings(null)!!
        assertEquals(-1, postings.docID().toLong())
        assertEquals(0, postings.nextDoc().toLong())
        assertEquals(2, postings.freq().toLong())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), postings.nextDoc().toLong())

        // termsenum reuse (FREQS)
        val postings2: PostingsEnum = termsEnum.postings(postings)!!
        assertNotNull(postings2)
        // and it had better work
        assertEquals(-1, postings2.docID().toLong())
        assertEquals(0, postings2.nextDoc().toLong())
        assertEquals(2, postings2.freq().toLong())
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), postings2.nextDoc().toLong())

        // asking for docs only: ok
        val docsOnly: PostingsEnum = termsEnum.postings(null, PostingsEnum.NONE.toInt())!!
        assertEquals(-1, docsOnly.docID().toLong())
        assertEquals(0, docsOnly.nextDoc().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsOnly.freq() == 1 || docsOnly.freq() == 2)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsOnly.nextDoc().toLong())
        // reuse that too
        val docsOnly2: PostingsEnum = termsEnum.postings(docsOnly, PostingsEnum.NONE.toInt())!!
        assertNotNull(docsOnly2)
        // and it had better work
        assertEquals(-1, docsOnly2.docID().toLong())
        assertEquals(0, docsOnly2.nextDoc().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsOnly2.freq() == 1 || docsOnly2.freq() == 2)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsOnly2.nextDoc().toLong())

        // asking for positions, ok
        var docsAndPositionsEnum: PostingsEnum = termsEnum.postings(null, PostingsEnum.POSITIONS.toInt())!!
        assertEquals(-1, docsAndPositionsEnum.docID().toLong())
        assertEquals(0, docsAndPositionsEnum.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum.freq().toLong())
        assertEquals(0, docsAndPositionsEnum.nextPosition().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum.startOffset() == -1 || docsAndPositionsEnum.startOffset() == 0)
        assertTrue(docsAndPositionsEnum.endOffset() == -1 || docsAndPositionsEnum.endOffset() == 3)
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum.payload == null || newBytesRef("pay1") == docsAndPositionsEnum.payload)
        assertEquals(1, docsAndPositionsEnum.nextPosition().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum.startOffset() == -1 || docsAndPositionsEnum.startOffset() == 4)
        assertTrue(docsAndPositionsEnum.endOffset() == -1 || docsAndPositionsEnum.endOffset() == 7)
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum.payload == null || newBytesRef("pay2") == docsAndPositionsEnum.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum.nextDoc().toLong())

        // now reuse the positions
        var docsAndPositionsEnum2: PostingsEnum = termsEnum.postings(docsAndPositionsEnum, PostingsEnum.POSITIONS.toInt())!!
        assertEquals(-1, docsAndPositionsEnum2.docID().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum2.freq().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextPosition().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(
            docsAndPositionsEnum2.startOffset() == -1 || docsAndPositionsEnum2.startOffset() == 0
        )
        assertTrue(docsAndPositionsEnum2.endOffset() == -1 || docsAndPositionsEnum2.endOffset() == 3)
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(
            docsAndPositionsEnum2.payload == null
                    || newBytesRef("pay1") == docsAndPositionsEnum2.payload
        )
        assertEquals(1, docsAndPositionsEnum2.nextPosition().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(
            docsAndPositionsEnum2.startOffset() == -1 || docsAndPositionsEnum2.startOffset() == 4
        )
        assertTrue(docsAndPositionsEnum2.endOffset() == -1 || docsAndPositionsEnum2.endOffset() == 7)
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(
            docsAndPositionsEnum2.payload == null
                    || newBytesRef("pay2") == docsAndPositionsEnum2.payload
        )
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum2.nextDoc().toLong())

        // payloads
        docsAndPositionsEnum = termsEnum.postings(null, PostingsEnum.PAYLOADS.toInt())!!
        assertNotNull(docsAndPositionsEnum)
        assertEquals(-1, docsAndPositionsEnum.docID().toLong())
        assertEquals(0, docsAndPositionsEnum.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum.freq().toLong())
        assertEquals(0, docsAndPositionsEnum.nextPosition().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum.startOffset() == -1 || docsAndPositionsEnum.startOffset() == 0)
        assertTrue(docsAndPositionsEnum.endOffset() == -1 || docsAndPositionsEnum.endOffset() == 3)
        assertEquals(newBytesRef("pay1"), docsAndPositionsEnum.payload)
        assertEquals(1, docsAndPositionsEnum.nextPosition().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum.startOffset() == -1 || docsAndPositionsEnum.startOffset() == 4)
        assertTrue(docsAndPositionsEnum.endOffset() == -1 || docsAndPositionsEnum.endOffset() == 7)
        assertEquals(newBytesRef("pay2"), docsAndPositionsEnum.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum.nextDoc().toLong())
        // reuse
        docsAndPositionsEnum2 = termsEnum.postings(docsAndPositionsEnum, PostingsEnum.PAYLOADS.toInt())!!
        assertEquals(-1, docsAndPositionsEnum2.docID().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum2.freq().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextPosition().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum2.startOffset() == -1 || docsAndPositionsEnum2.startOffset() == 0)
        assertTrue(docsAndPositionsEnum2.endOffset() == -1 || docsAndPositionsEnum2.endOffset() == 3)
        assertEquals(newBytesRef("pay1"), docsAndPositionsEnum2.payload)
        assertEquals(1, docsAndPositionsEnum2.nextPosition().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum2.startOffset() == -1 || docsAndPositionsEnum2.startOffset() == 4)
        assertTrue(docsAndPositionsEnum2.endOffset() == -1 || docsAndPositionsEnum2.endOffset() == 7)
        assertEquals(newBytesRef("pay2"), docsAndPositionsEnum2.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum2.nextDoc().toLong())

        docsAndPositionsEnum = termsEnum.postings(null, PostingsEnum.OFFSETS.toInt())!!
        assertNotNull(docsAndPositionsEnum)
        assertEquals(-1, docsAndPositionsEnum.docID().toLong())
        assertEquals(0, docsAndPositionsEnum.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum.freq().toLong())
        assertEquals(0, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(0, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(3, docsAndPositionsEnum.endOffset().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum.payload == null || newBytesRef("pay1") == docsAndPositionsEnum.payload)
        assertEquals(1, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(4, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(7, docsAndPositionsEnum.endOffset().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum.payload == null || newBytesRef("pay2") == docsAndPositionsEnum.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum.nextDoc().toLong())
        // reuse
        docsAndPositionsEnum2 = termsEnum.postings(docsAndPositionsEnum, PostingsEnum.OFFSETS.toInt())!!
        assertEquals(-1, docsAndPositionsEnum2.docID().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum2.freq().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(0, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(3, docsAndPositionsEnum2.endOffset().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum2.payload == null || newBytesRef("pay1") == docsAndPositionsEnum2.payload)
        assertEquals(1, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(4, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(7, docsAndPositionsEnum2.endOffset().toLong())
        // we don't define what it is, but if its something else, we should look into it
        assertTrue(docsAndPositionsEnum2.payload == null || newBytesRef("pay2") == docsAndPositionsEnum2.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum2.nextDoc().toLong())

        docsAndPositionsEnum = termsEnum.postings(null, PostingsEnum.ALL.toInt())!!
        assertNotNull(docsAndPositionsEnum)
        assertEquals(-1, docsAndPositionsEnum.docID().toLong())
        assertEquals(0, docsAndPositionsEnum.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum.freq().toLong())
        assertEquals(0, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(0, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(3, docsAndPositionsEnum.endOffset().toLong())
        assertEquals(newBytesRef("pay1"), docsAndPositionsEnum.payload)
        assertEquals(1, docsAndPositionsEnum.nextPosition().toLong())
        assertEquals(4, docsAndPositionsEnum.startOffset().toLong())
        assertEquals(7, docsAndPositionsEnum.endOffset().toLong())
        assertEquals(newBytesRef("pay2"), docsAndPositionsEnum.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum.nextDoc().toLong())
        docsAndPositionsEnum2 = termsEnum.postings(docsAndPositionsEnum, PostingsEnum.ALL.toInt())!!
        assertEquals(-1, docsAndPositionsEnum2.docID().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextDoc().toLong())
        assertEquals(2, docsAndPositionsEnum2.freq().toLong())
        assertEquals(0, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(0, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(3, docsAndPositionsEnum2.endOffset().toLong())
        assertEquals(newBytesRef("pay1"), docsAndPositionsEnum2.payload)
        assertEquals(1, docsAndPositionsEnum2.nextPosition().toLong())
        assertEquals(4, docsAndPositionsEnum2.startOffset().toLong())
        assertEquals(7, docsAndPositionsEnum2.endOffset().toLong())
        assertEquals(newBytesRef("pay2"), docsAndPositionsEnum2.payload)
        assertEquals(DocIdSetIterator.NO_MORE_DOCS.toLong(), docsAndPositionsEnum2.nextDoc().toLong())

        iw.close()
        reader.close()
        dir.close()
    }

    companion object {
        protected fun equals(o1: Any?, o2: Any?): Boolean {
            if (o1 == null) {
                return o2 == null
            } else {
                return o1 == o2
            }
        }
    }
}
