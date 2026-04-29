package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.CannedBinaryTokenStream
import org.gnit.lucenekmp.tests.analysis.CannedBinaryTokenStream.BinaryToken
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import kotlin.test.Test
import kotlin.test.assertEquals

class TestTerms : LuceneTestCase() {

    @Test
    @Throws(Exception::class)
    fun testTermMinMaxBasic() {
        val dir: Directory = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        val doc = Document()
        doc.add(newTextField("field", "a b c cc ddd", Field.Store.NO))
        w.addDocument(doc)
        val r: IndexReader = w.reader
        val terms: Terms = MultiTerms.getTerms(r, "field")!!
        assertEquals(BytesRef("a"), terms.min)
        assertEquals(BytesRef("ddd"), terms.max)
        r.close()
        w.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testTermMinMaxRandom() {
        val dir: Directory = newDirectory()
        val w = RandomIndexWriter(random(), dir)
        val numDocs = atLeast(100)
        var minTerm: BytesRef? = null
        var maxTerm: BytesRef? = null
        for (i in 0..<numDocs) {
            val doc = Document()
            // System.out.println("  doc " + i);
            val tokens: Array<BinaryToken> =
                arrayOfNulls<BinaryToken>(atLeast(10)) as Array<BinaryToken>
            for (j in tokens.indices) {
                val bytes = ByteArray(TestUtil.nextInt(random(), 1, 20))
                random().nextBytes(bytes)
                val tokenBytes = BytesRef(bytes)
                // System.out.println("    token " + tokenBytes);
                if (minTerm == null || tokenBytes.compareTo(minTerm) < 0) {
                    // System.out.println("      ** new min");
                    minTerm = tokenBytes
                }
                if (maxTerm == null || tokenBytes.compareTo(maxTerm) > 0) {
                    // System.out.println("      ** new max");
                    maxTerm = tokenBytes
                }
                tokens[j] = BinaryToken(tokenBytes)
            }
            val field: Field =
                Field("field", CannedBinaryTokenStream(*tokens), TextField.TYPE_NOT_STORED)
            doc.add(field)
            w.addDocument(doc)
        }

        val r: IndexReader = w.reader
        val terms: Terms = MultiTerms.getTerms(r, "field")!!
        assertEquals(minTerm, terms.min)
        assertEquals(maxTerm, terms.max)

        r.close()
        w.close()
        dir.close()
    }
}
