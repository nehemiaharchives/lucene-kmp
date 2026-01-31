package org.gnit.lucenekmp.codecs.lucene101

import okio.IOException
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.codecs.CompetitiveImpactAccumulator
import org.gnit.lucenekmp.codecs.lucene101.Lucene101PostingsReader.MutableImpactList
import org.gnit.lucenekmp.codecs.lucene90.blocktree.FieldReader
import org.gnit.lucenekmp.codecs.lucene90.blocktree.Stats
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.Impact
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.store.ByteArrayDataInput
import org.gnit.lucenekmp.store.ByteArrayDataOutput
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.BasePostingsFormatTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test
import kotlin.test.assertEquals

class TestLucene101PostingsFormat : BasePostingsFormatTestCase() {
    override val codec: Codec
        get() = TestUtil.alwaysPostingsFormat(Lucene101PostingsFormat())

    @Test
    @Throws(IOException::class)
    fun testVInt15() {
        val bytes = ByteArray(5)
        val out =
            ByteArrayDataOutput(bytes)
        val `in` =
            ByteArrayDataInput()
        for (i in intArrayOf(0, 1, 127, 128, 32767, 32768, Int.Companion.MAX_VALUE)) {
            out.reset(bytes)
            Lucene101PostingsWriter.writeVInt15(out, i)
            `in`.reset(bytes, 0, out.position)
            assertEquals(
                i.toLong(),
                Lucene101PostingsReader.readVInt15(`in`).toLong()
            )
            assertEquals(out.position.toLong(), `in`.position.toLong())
        }
    }

    @Test
    @Throws(IOException::class)
    fun testVLong15() {
        val bytes = ByteArray(9)
        val out =
            ByteArrayDataOutput(bytes)
        val `in` =
            ByteArrayDataInput()
        for (i in longArrayOf(
            0,
            1,
            127,
            128,
            32767,
            32768,
            Int.MAX_VALUE.toLong(),
            Long.MAX_VALUE
        )) {
            out.reset(bytes)
            Lucene101PostingsWriter.writeVLong15(out, i)
            `in`.reset(bytes, 0, out.position)
            assertEquals(
                i,
                Lucene101PostingsReader.readVLong15(`in`)
            )
            assertEquals(out.position.toLong(), `in`.position.toLong())
        }
    }

    /** Make sure the final sub-block(s) are not skipped.  */
    @Test
    @Throws(Exception::class)
    fun testFinalBlock() {
        val d: Directory =
            newDirectory()
        val w = IndexWriter(
            d,
            IndexWriterConfig(
                MockAnalyzer(
                    random()
                )
            )
        )
        for (i in 0..24) {
            val doc = Document()
            doc.add(
                newStringField(
                    "field",
                    (97 + i).toChar().toString(),
                    Field.Store.NO
                )
            )
            doc.add(
                newStringField(
                    "field",
                    "z" + (97 + i).toChar().toString(),
                    Field.Store.NO
                )
            )
            w.addDocument(doc)
        }
        w.forceMerge(1)

        val r: DirectoryReader =
            DirectoryReader.open(w)
        assertEquals(1, r.leaves().size.toLong())
        val field: FieldReader =
            r.leaves()[0].reader()
                .terms("field") as FieldReader
        // We should see exactly two blocks: one root block (prefix empty string) and one block for z*
        // terms (prefix z):
        val stats: Stats = field.getStats() as Stats
        assertEquals(0, stats.floorBlockCount.toLong())
        assertEquals(2, stats.nonFloorBlockCount.toLong())
        r.close()
        w.close()
        d.close()
    }

    @Test
    @Throws(IOException::class)
    fun testImpactSerialization() {
        // omit norms and omit freqs
        doTestImpactSerialization(
            mutableListOf(
                Impact(
                    1,
                    1L
                )
            )
        )

        // omit freqs
        doTestImpactSerialization(
            mutableListOf(
                Impact(
                    1,
                    42L
                )
            )
        )
        // omit freqs with very large norms
        doTestImpactSerialization(
            mutableListOf(
                Impact(
                    1,
                    -100L
                )
            )
        )

        // omit norms
        doTestImpactSerialization(
            mutableListOf(
                Impact(
                    30,
                    1L
                )
            )
        )
        // omit norms with large freq
        doTestImpactSerialization(
            mutableListOf(
                Impact(
                    500,
                    1L
                )
            )
        )

        // freqs and norms, basic
        doTestImpactSerialization(
            mutableListOf(
                Impact(1, 7L),
                Impact(3, 9L),
                Impact(7, 10L),
                Impact(15, 11L),
                Impact(20, 13L),
                Impact(28, 14L)
            )
        )

        // freqs and norms, high values
        doTestImpactSerialization(
            mutableListOf(
                Impact(2, 2L),
                Impact(10, 10L),
                Impact(12, 50L),
                Impact(50, -100L),
                Impact(1000, -80L),
                Impact(1005, -3L)
            )
        )
    }

    @Throws(IOException::class)
    private fun doTestImpactSerialization(impacts: MutableList<Impact>) {
        val acc =
            CompetitiveImpactAccumulator()
        for (impact in impacts) {
            acc.add(impact.freq, impact.norm)
        }
        newDirectory().use { dir ->
            dir.createOutput("foo", IOContext.DEFAULT).use { out ->
                Lucene101PostingsWriter.writeImpacts(
                    acc.getCompetitiveFreqNormPairs(),
                    out
                )
            }
            dir.openInput("foo", IOContext.DEFAULT).use { `in` ->
                val b = ByteArray(Math.toIntExact(`in`.length()))
                `in`.readBytes(b, 0, b.size)
                val impacts2: List<Impact> =
                    Lucene101PostingsReader.readImpacts(
                        ByteArrayDataInput(b),
                        MutableImpactList(
                            impacts.size + random()
                                .nextInt(3)
                        )
                    )
                assertEquals(impacts, impacts2)
            }
        }
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
