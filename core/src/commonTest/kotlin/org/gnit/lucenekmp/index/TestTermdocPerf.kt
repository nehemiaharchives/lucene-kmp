package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.TimeUnit
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import kotlin.random.Random
import kotlin.test.Ignore
import kotlin.test.Test

internal class RepeatingTokenizer(var value: String?, private val random: Random, private val percentDocs: Float, private val maxTF: Int) : Tokenizer() {
    private var num = 0
    var termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)

    @Throws(IOException::class)
    override fun incrementToken(): Boolean {
        num--
        if (num >= 0) {
            clearAttributes()
            termAtt.append(value)
            return true
        }
        return false
    }

    @Throws(IOException::class)
    override fun reset() {
        super.reset()
        if (random.nextFloat() < percentDocs) {
            num = random.nextInt(maxTF) + 1
        } else {
            num = 0
        }
    }
}

class TestTermdocPerf : LuceneTestCase() {

    @Throws(IOException::class)
    fun addDocs(
        random: Random,
        dir: Directory,
        ndocs: Int,
        field: String?,
        `val`: String?,
        maxTF: Int,
        percentDocs: Float
    ) {
        val analyzer: Analyzer =
            object : Analyzer() {
                public override fun createComponents(fieldName: String): TokenStreamComponents {
                    return TokenStreamComponents(
                        RepeatingTokenizer(`val`, random, percentDocs, maxTF)
                    )
                }
            }

        val doc = Document()

        doc.add(newStringField(field!!, `val`, Field.Store.NO))
        val writer: IndexWriter =
            IndexWriter(
                dir,
                newIndexWriterConfig(analyzer)
                    .setOpenMode(IndexWriterConfig.OpenMode.CREATE)
                    .setMaxBufferedDocs(100)
                    .setMergePolicy(newLogMergePolicy(100))
            )

        for (i in 0..<ndocs) {
            writer.addDocument(doc)
        }

        writer.forceMerge(1)
        writer.close()
    }

    @Throws(IOException::class)
    fun doTest(iter: Int, ndocs: Int, maxTF: Int, percentDocs: Float): Int {
        val dir: Directory = newDirectory()

        var start: Long = System.nanoTime()
        addDocs(random(), dir, ndocs, "foo", "val", maxTF, percentDocs)
        var end: Long = System.nanoTime()
        if (VERBOSE) println(
            ("milliseconds for creation of "
                    + ndocs
                    + " docs = "
                    + TimeUnit.NANOSECONDS.toMillis(end - start))
        )

        val reader: IndexReader = DirectoryReader.open(dir)

        val tenum: TermsEnum = MultiTerms.getTerms(reader, "foo")!!.iterator()

        start = System.nanoTime()

        var ret = 0
        var tdocs: PostingsEnum? = null
        val random: Random = Random(random().nextLong())
        for (i in 0..<iter) {
            tenum.seekCeil(BytesRef("val"))
            tdocs = TestUtil.docs(random, tenum, tdocs, PostingsEnum.NONE.toInt())
            while (tdocs.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                ret += tdocs.docID()
            }
        }

        end = System.nanoTime()
        if (VERBOSE) println(
            ("milliseconds for "
                    + iter
                    + " TermDocs iteration: "
                    + TimeUnit.NANOSECONDS.toMillis(end - start))
        )

        return ret
    }

    @Ignore // confirmed to pass in local runs (jvm and linuxX64) but in Java lucene the test is commented out so we ignore it here as well
    @Test
    @Throws(IOException::class)
    fun testTermDocPerf() {
        // performance test for 10% of documents containing a term
        //doTest(100000, 10000,3,.1f);
    }
}
