package org.gnit.lucenekmp.codecs.blocktreeords

import okio.IOException
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.codecs.registerCodecsPostingsFormats
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.MultiTerms
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.BasePostingsFormatTestCase
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TestOrdsBlockTree : BasePostingsFormatTestCase() {
    companion object {
        init {
            registerCodecsPostingsFormats()
        }
    }

    override val codec: Codec =
        TestUtil.alwaysPostingsFormat(BlockTreeOrdsPostingsFormat())

    @Test
    @Throws(Exception::class)
    fun testBasic() {
        val dir: Directory =
            newDirectory()
        val iwc =
            IndexWriterConfig(
                MockAnalyzer(random())
            )
        iwc.setCodec(codec)
        val w =
            RandomIndexWriter(
                random(),
                dir,
                iwc
            )
        val doc = Document()
        doc.add(
            newTextField(
                "field",
                "a b c",
                Field.Store.NO
            )
        )
        w.addDocument(doc)
        val r: IndexReader = w.reader
        val te: TermsEnum =
            MultiTerms.getTerms(r, "field")!!.iterator()

        // Test next()
        assertEquals(BytesRef("a"), te.next())
        assertEquals(0L, te.ord())
        assertEquals(BytesRef("b"), te.next())
        assertEquals(1L, te.ord())
        assertEquals(BytesRef("c"), te.next())
        assertEquals(2L, te.ord())
        assertNull(te.next())

        // Test seekExact by term
        assertTrue(te.seekExact(BytesRef("b")))
        assertEquals(1, te.ord())
        assertTrue(te.seekExact(BytesRef("a")))
        assertEquals(0, te.ord())
        assertTrue(te.seekExact(BytesRef("c")))
        assertEquals(2, te.ord())

        // Test seekExact by ord
        te.seekExact(1)
        assertEquals(BytesRef("b"), te.term())
        te.seekExact(0)
        assertEquals(BytesRef("a"), te.term())
        te.seekExact(2)
        assertEquals(BytesRef("c"), te.term())

        r.close()
        w.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testTwoBlocks() {
        val dir: Directory =
            newDirectory()
        val iwc =
            IndexWriterConfig(
                MockAnalyzer(random())
            )
        iwc.setCodec(codec)
        val w =
            RandomIndexWriter(
                random(),
                dir,
                iwc
            )
        val terms: MutableList<String> = mutableListOf()
        for (i in 0..35) {
            val doc = Document()
            val term = "" + (97 + i).toChar()
            terms.add(term)
            if (VERBOSE) {
                println("i=$i term=$term")
            }
            doc.add(
                newTextField(
                    "field",
                    term,
                    Field.Store.NO
                )
            )
            w.addDocument(doc)
        }
        for (i in 0..35) {
            val doc = Document()
            val term = "m" + (97 + i).toChar()
            terms.add(term)
            if (VERBOSE) {
                println("i=$i term=$term")
            }
            doc.add(
                newTextField(
                    "field",
                    term,
                    Field.Store.NO
                )
            )
            w.addDocument(doc)
        }
        if (VERBOSE) {
            println("TEST: now forceMerge")
        }
        w.forceMerge(1)
        val r: IndexReader = w.reader
        val te: TermsEnum =
            MultiTerms.getTerms(r, "field")!!.iterator()

        assertTrue(te.seekExact(BytesRef("mo")))
        assertEquals(27, te.ord())

        te.seekExact(54)
        assertEquals(BytesRef("s"), te.term())

        terms.sort()

        for (i in terms.indices.reversed()) {
            te.seekExact(i.toLong())
            assertEquals(i.toLong(), te.ord())
            val actual = te.term()!!.utf8ToString()
            if (terms[i] != actual) {
                error("ord=$i expected='${terms[i]}' expectedBytes=${BytesRef(terms[i])} actual='${actual}' actualBytes=${te.term()}")
            }
        }

        val iters: Int = atLeast(1000)
        for (iter in 0..<iters) {
            val ord: Int = random().nextInt(terms.size)
            val term =
                BytesRef(terms[ord])
            if (random().nextBoolean()) {
                if (VERBOSE) {
                    println("TEST: iter=" + iter + " seek to ord=" + ord + " of " + terms.size)
                }
                te.seekExact(ord.toLong())
            } else {
                if (VERBOSE) {
                    println(
                        ("TEST: iter="
                                + iter
                                + " seek to term="
                                + terms[ord]
                                + " ord="
                                + ord
                                + " of "
                                + terms.size)
                    )
                }
                te.seekExact(term)
            }
            assertEquals(ord.toLong(), te.ord())
            assertEquals(term, te.term())
        }

        r.close()
        w.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testThreeBlocks() {
        val dir: Directory =
            newDirectory()
        val iwc =
            IndexWriterConfig(
                MockAnalyzer(random())
            )
        iwc.setCodec(codec)
        val w =
            RandomIndexWriter(
                random(),
                dir,
                iwc
            )
        val terms: MutableList<String> = mutableListOf()
        for (i in 0..35) {
            val doc = Document()
            val term = "" + (97 + i).toChar()
            terms.add(term)
            if (VERBOSE) {
                println("i=$i term=$term")
            }
            doc.add(
                newTextField(
                    "field",
                    term,
                    Field.Store.NO
                )
            )
            w.addDocument(doc)
        }
        for (i in 0..35) {
            val doc = Document()
            val term = "m" + (97 + i).toChar()
            terms.add(term)
            if (VERBOSE) {
                println("i=$i term=$term")
            }
            doc.add(
                newTextField(
                    "field",
                    term,
                    Field.Store.NO
                )
            )
            w.addDocument(doc)
        }
        for (i in 0..35) {
            val doc = Document()
            val term = "mo" + (97 + i).toChar()
            terms.add(term)
            if (VERBOSE) {
                println("i=$i term=$term")
            }
            doc.add(
                newTextField(
                    "field",
                    term,
                    Field.Store.NO
                )
            )
            w.addDocument(doc)
        }
        w.forceMerge(1)
        val r: IndexReader = w.reader
        val te: TermsEnum =
            MultiTerms.getTerms(r, "field")!!.iterator()

        if (VERBOSE) {
            while (te.next() != null) {
                println("TERM: " + te.ord() + " " + te.term()!!.utf8ToString())
            }
        }

        assertTrue(te.seekExact(BytesRef("mo")))
        assertEquals(27, te.ord())

        te.seekExact(90)
        assertEquals(BytesRef("s"), te.term())

        testEnum(te, terms)

        r.close()
        w.close()
        dir.close()
    }

    @Throws(IOException::class)
    private fun testEnum(te: TermsEnum, terms: MutableList<String>) {
        terms.sort()
        for (i in terms.indices.reversed()) {
            if (VERBOSE) {
                println("TEST: seek to ord=$i")
            }
            te.seekExact(i.toLong())
            assertEquals(i.toLong(), te.ord())
            assertEquals(terms[i], te.term()!!.utf8ToString())
        }

        val iters: Int = atLeast(1000)
        for (iter in 0..<iters) {
            val ord: Int = random().nextInt(terms.size)
            if (random().nextBoolean()) {
                te.seekExact(ord.toLong())
                assertEquals(terms[ord], te.term()!!.utf8ToString())
            } else {
                te.seekExact(BytesRef(terms[ord]))
                assertEquals(ord.toLong(), te.ord())
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testFloorBlocks() {
        val dir: Directory =
            newDirectory()
        val iwc =
            IndexWriterConfig(
                MockAnalyzer(random())
            )
        iwc.setCodec(codec)
        val w = IndexWriter(dir, iwc)
        for (i in 0..127) {
            val doc = Document()
            val term = "" + i.toChar()
            if (VERBOSE) {
                println(
                    "i=$i term=$term bytes=" + BytesRef(
                        term
                    )
                )
            }
            doc.add(
                newStringField(
                    "field",
                    term,
                    Field.Store.NO
                )
            )
            w.addDocument(doc)
        }
        w.forceMerge(1)
        val r: IndexReader = DirectoryReader.open(w)
        val te: TermsEnum =
            MultiTerms.getTerms(r, "field")!!.iterator()

        if (VERBOSE) {
            var term: BytesRef?
            while ((te.next().also { term = it }) != null) {
                println("  " + te.ord() + ": " + term!!.utf8ToString())
            }
        }

        assertTrue(te.seekExact(BytesRef("a")))
        assertEquals(97, te.ord())

        te.seekExact(98)
        assertEquals(BytesRef("b"), te.term())

        assertTrue(te.seekExact(BytesRef("z")))
        assertEquals(122, te.ord())

        r.close()
        w.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testNonRootFloorBlocks() {
        val dir: Directory =
            newDirectory()
        val iwc =
            IndexWriterConfig(
                MockAnalyzer(random())
            )
        iwc.setCodec(codec)
        val w = IndexWriter(dir, iwc)
        val terms: MutableList<String> = mutableListOf()
        for (i in 0..35) {
            val doc = Document()
            val term = "" + (97 + i).toChar()
            terms.add(term)
            if (VERBOSE) {
                println("i=$i term=$term")
            }
            doc.add(
                newStringField(
                    "field",
                    term,
                    Field.Store.NO
                )
            )
            w.addDocument(doc)
        }
        for (i in 0..127) {
            val doc = Document()
            val term = "m" + i.toChar()
            terms.add(term)
            if (VERBOSE) {
                println(
                    "i=$i term=$term bytes=" + BytesRef(
                        term
                    )
                )
            }
            doc.add(
                newStringField(
                    "field",
                    term,
                    Field.Store.NO
                )
            )
            w.addDocument(doc)
        }
        w.forceMerge(1)
        val r: IndexReader = DirectoryReader.open(w)
        val te: TermsEnum =
            MultiTerms.getTerms(r, "field")!!.iterator()

        var term: BytesRef?
        var ord = 0
        while ((te.next().also { term = it }) != null) {
            if (VERBOSE) {
                println("TEST: " + te.ord() + ": " + term!!.utf8ToString())
            }
            assertEquals(ord.toLong(), te.ord())
            ord++
        }

        testEnum(te, terms)

        r.close()
        w.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testSeveralNonRootBlocks() {
        val dir: Directory =
            newDirectory()
        val iwc =
            IndexWriterConfig(
                MockAnalyzer(random())
            )
        iwc.setCodec(codec)
        val w = IndexWriter(dir, iwc)
        val terms: MutableList<String> = mutableListOf()
        for (i in 0..29) {
            for (j in 0..29) {
                val doc = Document()
                val term = "" + (97 + i).toChar() + (97 + j).toChar()
                terms.add(term)
                if (VERBOSE) {
                    println("term=$term")
                }
                doc.add(
                    newTextField(
                        "body",
                        term,
                        Field.Store.NO
                    )
                )
                w.addDocument(doc)
            }
        }
        w.forceMerge(1)
        val r: IndexReader = DirectoryReader.open(w)
        val te: TermsEnum =
            MultiTerms.getTerms(r, "body")!!.iterator()

        for (i in 0..29) {
            for (j in 0..29) {
                val term = "" + (97 + i).toChar() + (97 + j).toChar()
                if (VERBOSE) {
                    println("TEST: check term=$term")
                }
                assertEquals(term, te.next()!!.utf8ToString())
                assertEquals((30 * i + j).toLong(), te.ord())
            }
        }

        testEnum(te, terms)

        te.seekExact(0)
        assertEquals("aa", te.term()!!.utf8ToString())

        r.close()
        w.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testSeekCeilNotFound() {
        val dir: Directory =
            newDirectory()
        val iwc =
            IndexWriterConfig(
                MockAnalyzer(random())
            )
        iwc.setCodec(codec)
        val w =
            RandomIndexWriter(
                random(),
                dir,
                iwc
            )
        var doc = Document()
        // Get empty string in there!
        doc.add(
            newStringField(
                "field",
                "",
                Field.Store.NO
            )
        )
        w.addDocument(doc)

        for (i in 0..35) {
            doc = Document()
            val term = "" + (97 + i).toChar()
            val term2 = "a" + (97 + i).toChar()
            doc.add(
                newStringField(
                    "field",
                    term,
                    Field.Store.NO
                )
            )
            doc.add(
                newStringField(
                    "field",
                    term2,
                    Field.Store.NO
                )
            )
            w.addDocument(doc)
        }

        w.forceMerge(1)
        val r: IndexReader = w.reader
        val te: TermsEnum =
            MultiTerms.getTerms(r, "field")!!.iterator()
        assertEquals(
            TermsEnum.SeekStatus.NOT_FOUND,
            te.seekCeil(BytesRef(byteArrayOf(0x22)))
        )
        assertEquals("a", te.term()!!.utf8ToString())
        assertEquals(1L, te.ord())
        r.close()
        w.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    override fun testDocsOnly() = super.testDocsOnly()

    @Test
    @Throws(Exception::class)
    override fun testDocsAndFreqs() = super.testDocsAndFreqs()

    @Test
    @Throws(Exception::class)
    override fun testDocsAndFreqsAndPositions() = super.testDocsAndFreqsAndPositions()

    @Test
    @Throws(Exception::class)
    override fun testDocsAndFreqsAndPositionsAndPayloads() = super.testDocsAndFreqsAndPositionsAndPayloads()

    @Test
    @Throws(Exception::class)
    override fun testDocsAndFreqsAndPositionsAndOffsets() = super.testDocsAndFreqsAndPositionsAndOffsets()

    @Test
    @Throws(Exception::class)
    override fun testDocsAndFreqsAndPositionsAndOffsetsAndPayloads() = super.testDocsAndFreqsAndPositionsAndOffsetsAndPayloads()

    @Test
    @Throws(Exception::class)
    override fun testRandom() = super.testRandom()

    @Test
    @Throws(Exception::class)
    override fun testPostingsEnumReuse() = super.testPostingsEnumReuse()

    @Test
    @Throws(Exception::class)
    override fun testJustEmptyField() = super.testJustEmptyField()

    @Test
    @Throws(Exception::class)
    override fun testEmptyFieldAndEmptyTerm() = super.testEmptyFieldAndEmptyTerm()

    @Test
    @Throws(Exception::class)
    override fun testDidntWantFreqsButAskedAnyway() = super.testDidntWantFreqsButAskedAnyway()

    @Test
    @Throws(Exception::class)
    override fun testAskForPositionsWhenNotThere() = super.testAskForPositionsWhenNotThere()

    @Test
    @Throws(Exception::class)
    override fun testGhosts() = super.testGhosts()

    @Test
    @Throws(Exception::class)
    override fun testDisorder() = super.testDisorder()

    @Test
    @Throws(Exception::class)
    override fun testBinarySearchTermLeaf() = super.testBinarySearchTermLeaf()

    @Test
    @Throws(Exception::class)
    override fun testLevel2Ghosts() = super.testLevel2Ghosts()

    @Test
    @Throws(Exception::class)
    override fun testInvertedWrite() = super.testInvertedWrite()

    @Test
    @Throws(Exception::class)
    override fun testPostingsEnumDocsOnly() = super.testPostingsEnumDocsOnly()

    @Test
    @Throws(Exception::class)
    override fun testPostingsEnumFreqs() = super.testPostingsEnumFreqs()

    @Test
    @Throws(Exception::class)
    override fun testPostingsEnumPositions() = super.testPostingsEnumPositions()

    @Test
    @Throws(Exception::class)
    override fun testPostingsEnumOffsets() = super.testPostingsEnumOffsets()

    @Test
    @Throws(Exception::class)
    override fun testPostingsEnumPayloads() = super.testPostingsEnumPayloads()

    @Test
    @Throws(Exception::class)
    override fun testPostingsEnumAll() = super.testPostingsEnumAll()

    @Test
    override fun testLineFileDocs() = super.testLineFileDocs()

    @Test
    @Throws(Exception::class)
    override fun testMismatchedFields() = super.testMismatchedFields()

}
