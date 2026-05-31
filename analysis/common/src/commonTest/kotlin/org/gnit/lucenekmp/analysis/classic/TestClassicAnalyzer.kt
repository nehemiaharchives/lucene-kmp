package org.gnit.lucenekmp.analysis.classic

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.MultiTerms
import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.util.BytesRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestClassicAnalyzer : BaseTokenStreamTestCase() {
    private val a: Analyzer = ClassicAnalyzer()

    @Test
    fun testMaxTermLength() {
        val sa = ClassicAnalyzer()
        sa.setMaxTokenLength(5)
        assertAnalyzesTo(sa, "ab cd toolong xy z", arrayOf("ab", "cd", "xy", "z"))
        sa.close()
    }

    @Test
    fun testMaxTermLength2() {
        val sa = ClassicAnalyzer()
        assertAnalyzesTo(sa, "ab cd toolong xy z", arrayOf("ab", "cd", "toolong", "xy", "z"))
        sa.setMaxTokenLength(5)
        assertAnalyzesTo(sa, "ab cd toolong xy z", arrayOf("ab", "cd", "xy", "z"), intArrayOf(1, 1, 2, 1))
        sa.close()
    }

    @Test
    fun testMaxTermLength3() {
        val chars = CharArray(255) { 'a' }
        val longTerm = chars.concatToString(0, 255)
        assertAnalyzesTo(a, "ab cd $longTerm xy z", arrayOf("ab", "cd", longTerm, "xy", "z"))
        assertAnalyzesTo(a, "ab cd ${longTerm}a xy z", arrayOf("ab", "cd", "xy", "z"))
    }

    @Test
    fun testAlphanumeric() {
        // alphanumeric tokens
        assertAnalyzesTo(a, "B2B", arrayOf("b2b"))
        assertAnalyzesTo(a, "2B", arrayOf("2b"))
    }

    @Test
    fun testUnderscores() {
        // underscores are delimiters, but not in email addresses (below)
        assertAnalyzesTo(a, "word_having_underscore", arrayOf("word", "having", "underscore"))
        assertAnalyzesTo(a, "word_with_underscore_and_stopwords", arrayOf("word", "underscore", "stopwords"))
    }

    @Test
    fun testDelimiters() {
        // other delimiters: "-", "/", ","
        assertAnalyzesTo(a, "some-dashed-phrase", arrayOf("some", "dashed", "phrase"))
        assertAnalyzesTo(a, "dogs,chase,cats", arrayOf("dogs", "chase", "cats"))
        assertAnalyzesTo(a, "ac/dc", arrayOf("ac", "dc"))
    }

    @Test
    fun testApostrophes() {
        // internal apostrophes: O'Reilly, you're, O'Reilly's
        // possessives are actually removed by StardardFilter, not the tokenizer
        assertAnalyzesTo(a, "O'Reilly", arrayOf("o'reilly"))
        assertAnalyzesTo(a, "you're", arrayOf("you're"))
        assertAnalyzesTo(a, "she's", arrayOf("she"))
        assertAnalyzesTo(a, "Jim's", arrayOf("jim"))
        assertAnalyzesTo(a, "don't", arrayOf("don't"))
        assertAnalyzesTo(a, "O'Reilly's", arrayOf("o'reilly"))
    }

    @Test
    fun testTSADash() {
        // t and s had been stopwords in Lucene <= 2.0, which made it impossible
        // to correctly search for these terms:
        assertAnalyzesTo(a, "s-class", arrayOf("s", "class"))
        assertAnalyzesTo(a, "t-com", arrayOf("t", "com"))
        // 'a' is still a stopword:
        assertAnalyzesTo(a, "a-class", arrayOf("class"))
    }

    @Test
    fun testCompanyNames() {
        // company names
        assertAnalyzesTo(a, "AT&T", arrayOf("at&t"))
        assertAnalyzesTo(a, "Excite@Home", arrayOf("excite@home"))
    }

    // should not throw NPE
    @Test
    fun testLucene1140() {
        val analyzer = ClassicAnalyzer()
        assertAnalyzesTo(analyzer, "www.nutch.org.", arrayOf("www.nutch.org"), arrayOf("<HOST>"))
        analyzer.close()
    }

    @Test
    fun testDomainNames() {
        // Current lucene should not show the bug
        var a2 = ClassicAnalyzer()

        // domain names
        assertAnalyzesTo(a2, "www.nutch.org", arrayOf("www.nutch.org"))
        // Notice the trailing .  See https://issues.apache.org/jira/browse/LUCENE-1068.
        // the following should be recognized as HOST:
        assertAnalyzesTo(a2, "www.nutch.org.", arrayOf("www.nutch.org"), arrayOf("<HOST>"))
        a2.close()

        // 2.4 should not show the bug. But, alas, it's also obsolete,
        // so we check latest released
        a2 = ClassicAnalyzer()
        assertAnalyzesTo(a2, "www.nutch.org.", arrayOf("www.nutch.org"), arrayOf("<HOST>"))
        a2.close()
    }

    @Test
    fun testEMailAddresses() {
        // email addresses, possibly with underscores, periods, etc
        assertAnalyzesTo(a, "test@example.com", arrayOf("test@example.com"))
        assertAnalyzesTo(a, "first.lastname@example.com", arrayOf("first.lastname@example.com"))
        assertAnalyzesTo(a, "first_lastname@example.com", arrayOf("first_lastname@example.com"))
    }

    @Test
    fun testNumeric() {
        // floating point, serial, model numbers, ip addresses, etc.
        // every other segment must have at least one digit
        assertAnalyzesTo(a, "21.35", arrayOf("21.35"))
        assertAnalyzesTo(a, "R2D2 C3PO", arrayOf("r2d2", "c3po"))
        assertAnalyzesTo(a, "216.239.63.104", arrayOf("216.239.63.104"))
        assertAnalyzesTo(a, "1-2-3", arrayOf("1-2-3"))
        assertAnalyzesTo(a, "a1-b2-c3", arrayOf("a1-b2-c3"))
        assertAnalyzesTo(a, "a1-b-c3", arrayOf("a1-b-c3"))
    }

    @Test
    fun testTextWithNumbers() {
        // numbers
        assertAnalyzesTo(a, "David has 5000 bones", arrayOf("david", "has", "5000", "bones"))
    }

    @Test
    fun testVariousText() {
        // various
        assertAnalyzesTo(a, "C embedded developers wanted", arrayOf("c", "embedded", "developers", "wanted"))
        assertAnalyzesTo(a, "foo bar FOO BAR", arrayOf("foo", "bar", "foo", "bar"))
        assertAnalyzesTo(a, "foo      bar .  FOO <> BAR", arrayOf("foo", "bar", "foo", "bar"))
        assertAnalyzesTo(a, "\"QUOTED\" word", arrayOf("quoted", "word"))
    }

    @Test
    fun testAcronyms() {
        // acronyms have their dots stripped
        assertAnalyzesTo(a, "U.S.A.", arrayOf("usa"))
    }

    @Test
    fun testCPlusPlusHash() {
        // It would be nice to change the grammar in StandardTokenizer.jj to make "C#" and "C++" end up as tokens.
        assertAnalyzesTo(a, "C++", arrayOf("c"))
        assertAnalyzesTo(a, "C#", arrayOf("c"))
    }

    @Test
    fun testKorean() {
        // Korean words
        assertAnalyzesTo(a, "안녕하세요 한글입니다", arrayOf("안녕하세요", "한글입니다"))
    }

    // Compliance with the "old" JavaCC-based analyzer, see:
    // https://issues.apache.org/jira/browse/LUCENE-966#action_12516752
    @Test
    fun testComplianceFileName() {
        assertAnalyzesTo(a, "2004.jpg", arrayOf("2004.jpg"), arrayOf("<HOST>"))
    }

    @Test
    fun testComplianceNumericIncorrect() {
        assertAnalyzesTo(a, "62.46", arrayOf("62.46"), arrayOf("<HOST>"))
    }

    @Test
    fun testComplianceNumericLong() {
        assertAnalyzesTo(a, "978-0-94045043-1", arrayOf("978-0-94045043-1"), arrayOf("<NUM>"))
    }

    @Test
    fun testComplianceNumericFile() {
        assertAnalyzesTo(a, "78academyawards/rules/rule02.html", arrayOf("78academyawards/rules/rule02.html"), arrayOf("<NUM>"))
    }

    @Test
    fun testComplianceNumericWithUnderscores() {
        assertAnalyzesTo(
            a,
            "2006-03-11t082958z_01_ban130523_rtridst_0_ozabs",
            arrayOf("2006-03-11t082958z_01_ban130523_rtridst_0_ozabs"),
            arrayOf("<NUM>")
        )
    }

    @Test
    fun testComplianceNumericWithDash() {
        assertAnalyzesTo(a, "mid-20th", arrayOf("mid-20th"), arrayOf("<NUM>"))
    }

    @Test
    fun testComplianceManyTokens() {
        assertAnalyzesTo(
            a,
            "/money.cnn.com/magazines/fortune/fortune_archive/2007/03/19/8402357/index.htm safari-0-sheikh-zayed-grand-mosque.jpg",
            arrayOf("money.cnn.com", "magazines", "fortune", "fortune", "archive/2007/03/19/8402357", "index.htm", "safari-0-sheikh", "zayed", "grand", "mosque.jpg"),
            arrayOf("<HOST>", "<ALPHANUM>", "<ALPHANUM>", "<ALPHANUM>", "<NUM>", "<HOST>", "<NUM>", "<ALPHANUM>", "<ALPHANUM>", "<HOST>")
        )
    }

    @Test
    fun testJava14BWCompatibility() {
        val sa = ClassicAnalyzer()
        assertAnalyzesTo(sa, "test\u02C6test", arrayOf("test", "test"))
        sa.close()
    }

    /** Make sure we skip wicked long terms. */
    @Test
    fun testWickedLongTerm() {
        val dir: Directory = ByteBuffersDirectory()
        val analyzer: Analyzer = ClassicAnalyzer()
        var writer = IndexWriter(dir, IndexWriterConfig(analyzer))

        val chars = CharArray(IndexWriter.MAX_TERM_LENGTH) { 'x' }
        var doc = Document()
        val bigTerm = chars.concatToString()

        // This produces a too-long term:
        val contents = "abc xyz x$bigTerm another term"
        doc.add(TextField("content", contents, Field.Store.NO))
        writer.addDocument(doc)

        // Make sure we can add another normal document
        doc = Document()
        doc.add(TextField("content", "abc bbb ccc", Field.Store.NO))
        writer.addDocument(doc)
        writer.close()

        var reader: IndexReader = DirectoryReader.open(dir)

        // Make sure all terms < max size were indexed
        assertEquals(2, reader.docFreq(Term("content", "abc")))
        assertEquals(1, reader.docFreq(Term("content", "bbb")))
        assertEquals(1, reader.docFreq(Term("content", "term")))
        assertEquals(1, reader.docFreq(Term("content", "another")))

        // Make sure position is still incremented when massive term is skipped:
        val tps: PostingsEnum = MultiTerms.getTermPostingsEnum(reader, "content", BytesRef("another"))!!
        assertTrue(tps.nextDoc() != DocIdSetIterator.NO_MORE_DOCS)
        assertEquals(1, tps.freq())
        assertEquals(3, tps.nextPosition())

        // Make sure the doc that has the massive term is in the index:
        assertEquals(2, reader.numDocs())
        reader.close()

        // Make sure we can add a document with exactly the maximum length term, and search on that term:
        doc = Document()
        doc.add(TextField("content", bigTerm, Field.Store.NO))
        val sa = ClassicAnalyzer()
        sa.setMaxTokenLength(100000)
        writer = IndexWriter(dir, IndexWriterConfig(sa))
        writer.addDocument(doc)
        writer.close()
        reader = DirectoryReader.open(dir)
        assertEquals(1, reader.docFreq(Term("content", bigTerm)))
        reader.close()

        dir.close()
        analyzer.close()
        sa.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    fun testRandomStrings() {
        val analyzer: Analyzer = ClassicAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }

    /** blast some random large strings through the analyzer */
    @Test
    fun testRandomHugeStrings() {
        val analyzer: Analyzer = ClassicAnalyzer()
        checkRandomData(random(), analyzer, 10 * RANDOM_MULTIPLIER, 8192)
        analyzer.close()
    }
}
