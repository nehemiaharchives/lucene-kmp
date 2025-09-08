package org.gnit.lucenekmp.index

import kotlin.test.*
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.codecs.lucene101.Lucene101Codec
import org.gnit.lucenekmp.codecs.TermVectorsReader
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.TextField
import okio.IOException
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.AfterTest
import kotlin.test.Ignore

/**
 * Kotlin port of Lucene's TestTermVectorsReader.
 */
class TestTermVectorsReader : LuceneTestCase() {
    private val testFields = arrayOf("f1", "f2", "f3", "f4")
    private val testFieldsStorePos = booleanArrayOf(true, false, true, false)
    private val testFieldsStoreOff = booleanArrayOf(true, false, false, true)
    private val testTerms = arrayOf("this", "is", "a", "test")
    private val positions = Array(testTerms.size) { IntArray(0) }
    private lateinit var dir: Directory
    private var seg: SegmentCommitInfo? = null
    private var fieldInfos: FieldInfos = FieldInfos.EMPTY
    private val tokens = Array(testTerms.size * TERM_FREQ) { TestToken() }

    private class TestToken : Comparable<TestToken> {
        var text: String = ""
        var pos: Int = 0
        var startOffset: Int = 0
        var endOffset: Int = 0
        override fun compareTo(other: TestToken): Int {
            return pos - other.pos
        }
    }

    @BeforeTest
    fun setUp() {
        testTerms.sort()
        var tokenUpto = 0
        val rnd: Random = random()
        for (i in testTerms.indices) {
            positions[i] = IntArray(TERM_FREQ)
            for (j in 0 until TERM_FREQ) {
                positions[i][j] = (j * 10 + rnd.nextDouble() * 10).toInt()
                val token = tokens[tokenUpto++]
                token.text = testTerms[i]
                token.pos = positions[i][j]
                token.startOffset = j * 10
                token.endOffset = j * 10 + testTerms[i].length
            }
        }
        tokens.sort()

        dir = newDirectory()
        val writer = IndexWriter(
            dir,
            IndexWriterConfig(MyAnalyzer())
                .setMaxBufferedDocs(-1)
                .setUseCompoundFile(false)
        )
        val doc = Document()
        for (i in testFields.indices) {
            val customType = FieldType(TextField.TYPE_NOT_STORED)
            if (testFieldsStorePos[i] && testFieldsStoreOff[i]) {
                customType.setStoreTermVectors(true)
                customType.setStoreTermVectorPositions(true)
                customType.setStoreTermVectorOffsets(true)
            } else if (testFieldsStorePos[i] && !testFieldsStoreOff[i]) {
                customType.setStoreTermVectors(true)
                customType.setStoreTermVectorPositions(true)
            } else if (!testFieldsStorePos[i] && testFieldsStoreOff[i]) {
                customType.setStoreTermVectors(true)
                customType.setStoreTermVectorOffsets(true)
            } else {
                customType.setStoreTermVectors(true)
            }
            doc.add(Field(testFields[i], "", customType))
        }

        for (j in 0 until 5) {
            writer.addDocument(doc)
        }
        writer.commit()
        seg = writer.newestSegment()
        writer.close()

        fieldInfos = IndexWriter.readFieldInfos(seg!!)
    }

    @AfterTest
    fun tearDown() {
        dir.close()
    }

    private inner class MyTokenizer : Tokenizer() {
        private var tokenUpto = 0
        private val termAtt = addAttribute(CharTermAttribute::class)
        private val posIncrAtt = addAttribute(PositionIncrementAttribute::class)
        private val offsetAtt = addAttribute(OffsetAttribute::class)
        override fun incrementToken(): Boolean {
            return if (tokenUpto >= tokens.size) {
                false
            } else {
                val testToken = tokens[tokenUpto++]
                clearAttributes()
                termAtt.append(testToken.text)
                offsetAtt.setOffset(testToken.startOffset, testToken.endOffset)
                if (tokenUpto > 1) {
                    posIncrAtt.setPositionIncrement(testToken.pos - tokens[tokenUpto - 2].pos)
                } else {
                    posIncrAtt.setPositionIncrement(testToken.pos + 1)
                }
                true
            }
        }
        @Throws(IOException::class)
        override fun reset() {
            super.reset()
            tokenUpto = 0
        }
    }

    private inner class MyAnalyzer : Analyzer() {
        override fun createComponents(fieldName: String): TokenStreamComponents {
            return TokenStreamComponents(MyTokenizer())
        }
    }

    private fun newDirectory(): Directory {
        return ByteBuffersDirectory()
    }

    companion object {
        private const val TERM_FREQ = 3
    }

    @Test
    fun test() {
        val reader = DirectoryReader.open(dir)
        for (ctx in reader.leaves()) {
            val sr = ctx.reader() as SegmentReader
            assertTrue(sr.fieldInfos.hasTermVectors())
        }
        reader.close()
    }

    @Test
    @Ignore("TermVectorsReader not yet fully implemented")
    fun testReader() {
        val reader: TermVectorsReader = Lucene101Codec().termVectorsFormat()
            .vectorsReader(dir, seg!!.info, fieldInfos, IOContext.DEFAULT)
        for (j in 0 until 5) {
            val vector = reader.get(j)!!.terms(testFields[0])
            assertNotNull(vector)
            assertEquals(testTerms.size.toLong(), vector!!.size())
            val termsEnum = vector.iterator()
            for (i in testTerms.indices) {
                val text = termsEnum.next()
                assertNotNull(text)
                val term = text!!.utf8ToString()
                assertEquals(testTerms[i], term)
            }
            assertNull(termsEnum.next())
        }
        reader.close()
    }

    @Test
    @Ignore("TermVectorsReader not yet fully implemented")
    fun testDocsEnum() {
        val reader: TermVectorsReader = Lucene101Codec().termVectorsFormat()
            .vectorsReader(dir, seg!!.info, fieldInfos, IOContext.DEFAULT)
        for (j in 0 until 5) {
            val vector = reader.get(j)!!.terms(testFields[0])
            assertNotNull(vector)
            assertEquals(testTerms.size.toLong(), vector!!.size())
            val termsEnum = vector.iterator()
            var postingsEnum: PostingsEnum? = null
            for (i in testTerms.indices) {
                val text = termsEnum.next()
                assertNotNull(text)
                val term = text!!.utf8ToString()
                assertEquals(testTerms[i], term)
                postingsEnum = termsEnum.postings(postingsEnum, PostingsEnum.NONE.toInt())
                assertNotNull(postingsEnum)
                var doc = postingsEnum!!.docID()
                assertEquals(-1, doc)
                assertTrue(postingsEnum.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
                assertEquals(DocIdSetIterator.NO_MORE_DOCS, postingsEnum.nextDoc())
            }
            assertNull(termsEnum.next())
        }
        reader.close()
    }

    @Test
    @Ignore("TermVectorsReader not yet fully implemented")
    fun testPositionReader() {
        val reader: TermVectorsReader = Lucene101Codec().termVectorsFormat()
            .vectorsReader(dir, seg!!.info, fieldInfos, IOContext.DEFAULT)
        var vector = reader.get(0)!!.terms(testFields[0])
        assertNotNull(vector)
        assertEquals(testTerms.size.toLong(), vector!!.size())
        var termsEnum = vector.iterator()
        var dpEnum: PostingsEnum? = null
        for (i in testTerms.indices) {
            val text = termsEnum.next()
            assertNotNull(text)
            val term = text!!.utf8ToString()
            assertEquals(testTerms[i], term)
            dpEnum = termsEnum.postings(dpEnum, PostingsEnum.ALL.toInt())
            assertNotNull(dpEnum)
            var doc = dpEnum!!.docID()
            assertEquals(-1, doc)
            assertTrue(dpEnum.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
            assertEquals(dpEnum.freq(), positions[i].size)
            for (j in positions[i].indices) {
                assertEquals(positions[i][j], dpEnum.nextPosition())
            }
            assertEquals(DocIdSetIterator.NO_MORE_DOCS, dpEnum.nextDoc())

            dpEnum = termsEnum.postings(dpEnum, PostingsEnum.ALL.toInt())
            doc = dpEnum!!.docID()
            assertEquals(-1, doc)
            assertTrue(dpEnum.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
            assertNotNull(dpEnum)
            assertEquals(dpEnum.freq(), positions[i].size)
            for (j in positions[i].indices) {
                assertEquals(positions[i][j], dpEnum.nextPosition())
                assertEquals(j * 10, dpEnum.startOffset())
                assertEquals(j * 10 + testTerms[i].length, dpEnum.endOffset())
            }
            assertEquals(DocIdSetIterator.NO_MORE_DOCS, dpEnum.nextDoc())
        }
        vector = reader.get(0)!!.terms(testFields[1])
        assertNotNull(vector)
        assertEquals(testTerms.size.toLong(), vector!!.size())
        termsEnum = vector.iterator()
        assertNotNull(termsEnum)
        for (i in testTerms.indices) {
            val text = termsEnum.next()
            assertNotNull(text)
            val term = text!!.utf8ToString()
            assertEquals(testTerms[i], term)
            assertNotNull(termsEnum.postings(null, PostingsEnum.FREQS.toInt()))
            assertNotNull(termsEnum.postings(null, PostingsEnum.ALL.toInt()))
        }
        reader.close()
    }

    @Test
    @Ignore("TermVectorsReader not yet fully implemented")
    fun testOffsetReader() {
        val reader: TermVectorsReader = Lucene101Codec().termVectorsFormat()
            .vectorsReader(dir, seg!!.info, fieldInfos, IOContext.DEFAULT)
        val vector = reader.get(0)!!.terms(testFields[0])
        assertNotNull(vector)
        var termsEnum = vector!!.iterator()
        assertNotNull(termsEnum)
        assertEquals(testTerms.size.toLong(), vector.size())
        var dpEnum: PostingsEnum? = null
        for (i in testTerms.indices) {
            val text = termsEnum.next()
            assertNotNull(text)
            val term = text!!.utf8ToString()
            assertEquals(testTerms[i], term)
            dpEnum = termsEnum.postings(dpEnum, PostingsEnum.ALL.toInt())
            assertNotNull(dpEnum)
            assertTrue(dpEnum!!.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
            assertEquals(dpEnum.freq(), positions[i].size)
            for (j in positions[i].indices) {
                assertEquals(positions[i][j], dpEnum.nextPosition())
            }
            assertEquals(DocIdSetIterator.NO_MORE_DOCS, dpEnum.nextDoc())

            dpEnum = termsEnum.postings(dpEnum, PostingsEnum.ALL.toInt())
            assertTrue(dpEnum!!.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
            assertNotNull(dpEnum)
            assertEquals(dpEnum.freq(), positions[i].size)
            for (j in positions[i].indices) {
                assertEquals(positions[i][j], dpEnum.nextPosition())
                assertEquals(j * 10, dpEnum.startOffset())
                assertEquals(j * 10 + testTerms[i].length, dpEnum.endOffset())
            }
            assertEquals(DocIdSetIterator.NO_MORE_DOCS, dpEnum.nextDoc())
        }
        reader.close()
    }

    @Test
    @Ignore
    fun testIllegalPayloadsWithoutPositions() {
        // TODO: implement after RandomIndexWriter is ported
    }

    @Test
    @Ignore
    fun testIllegalOffsetsWithoutVectors() {
        // TODO: implement after RandomIndexWriter is ported
    }

    @Test
    @Ignore
    fun testIllegalPositionsWithoutVectors() {
        // TODO: implement after RandomIndexWriter is ported
    }

    @Test
    @Ignore
    fun testIllegalVectorPayloadsWithoutVectors() {
        // TODO: implement after RandomIndexWriter is ported
    }

    @Test
    @Ignore
    fun testIllegalVectorsWithoutIndexed() {
        // TODO: implement after RandomIndexWriter is ported
    }

    @Test
    @Ignore
    fun testIllegalVectorPositionsWithoutIndexed() {
        // TODO: implement after RandomIndexWriter is ported
    }

    @Test
    @Ignore
    fun testIllegalVectorOffsetsWithoutIndexed() {
        // TODO: implement after RandomIndexWriter is ported
    }

    @Test
    @Ignore
    fun testIllegalVectorPayloadsWithoutIndexed() {
        // TODO: implement after RandomIndexWriter is ported
    }
}
