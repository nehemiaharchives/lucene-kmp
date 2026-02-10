package org.gnit.lucenekmp.codecs.lucene90.compressing

import okio.IOException
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.CodecReader
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.NoMergePolicy
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.index.TermsEnum.SeekStatus
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.codecs.compressing.CompressingCodec
import org.gnit.lucenekmp.tests.index.BaseTermVectorsFormatTestCase
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.util.BytesRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestCompressingTermVectorsFormat : BaseTermVectorsFormatTestCase() {
    override val codec: Codec
        get() {
            if (TEST_NIGHTLY) {
                return CompressingCodec.randomInstance(random())
            } else {
                return CompressingCodec.reasonableInstance(random())
            }
        }

    // https://issues.apache.org/jira/browse/LUCENE-5156
    @Test
    @Throws(Exception::class)
    fun testNoOrds() {
        val dir: Directory = newDirectory()
        val iw = RandomIndexWriter(random(), dir)
        val doc = Document()
        val ft = FieldType(TextField.TYPE_NOT_STORED)
        ft.setStoreTermVectors(true)
        doc.add(Field("foo", "this is a test", ft))
        iw.addDocument(doc)
        val ir: LeafReader = getOnlyLeafReader(iw.reader)
        val terms: Terms? = ir.termVectors().get(0, "foo")
        assertNotNull(terms)
        val termsEnum: TermsEnum = terms.iterator()
        assertEquals(SeekStatus.FOUND, termsEnum.seekCeil(BytesRef("this")))

        expectThrows(UnsupportedOperationException::class) { termsEnum.ord() }
        expectThrows(UnsupportedOperationException::class) { termsEnum.seekExact(0) }

        ir.close()
        iw.close()
        dir.close()
    }

    /**
     * writes some tiny segments with incomplete compressed blocks, and ensures merge recompresses
     * them.
     */
    @Test
    @Throws(IOException::class)
    fun testChunkCleanup() {
        val dir: Directory = newDirectory()
        val iwConf: IndexWriterConfig = newIndexWriterConfig(MockAnalyzer(random()))
        iwConf.setMergePolicy(NoMergePolicy.INSTANCE)

        // we have to enforce certain things like maxDocsPerChunk to cause dirty chunks to be created
        // by this test.
        iwConf.setCodec(CompressingCodec.randomInstance(random(), 4 * 1024, 4, false, 8))
        val iw = IndexWriter(dir, iwConf)
        var ir: DirectoryReader = DirectoryReader.open(iw)
        for (i in 0..4) {
            val doc = Document()
            val ft = FieldType(TextField.TYPE_NOT_STORED)
            ft.setStoreTermVectors(true)
            doc.add(Field("text", "not very long at all", ft))
            iw.addDocument(doc)
            // force flush
            val ir2: DirectoryReader? = DirectoryReader.openIfChanged(ir)
            assertNotNull(ir2)
            ir.close()
            ir = ir2
            // examine dirty counts:
            for (leaf in ir2.leaves()) {
                val sr: CodecReader = leaf.reader() as CodecReader
                val reader: Lucene90CompressingTermVectorsReader =
                    sr.termVectorsReader as Lucene90CompressingTermVectorsReader
                assertTrue(reader.getNumDirtyDocs() > 0)
                assertEquals(1, reader.getNumDirtyChunks())
            }
        }
        iw.config.setMergePolicy(newLogMergePolicy())
        iw.forceMerge(1)
        // add one more doc and merge again
        val doc = Document()
        val ft = FieldType(TextField.TYPE_NOT_STORED)
        ft.setStoreTermVectors(true)
        doc.add(Field("text", "not very long at all", ft))
        iw.addDocument(doc)
        iw.forceMerge(1)
        val ir2: DirectoryReader? = DirectoryReader.openIfChanged(ir)
        assertNotNull(ir2)
        ir.close()
        ir = ir2
        val sr: CodecReader = getOnlyLeafReader(ir) as CodecReader
        val reader: Lucene90CompressingTermVectorsReader =
            sr.termVectorsReader as Lucene90CompressingTermVectorsReader
        // at most 2: the 5 chunks from 5 doc segment will be collapsed into a single chunk
        assertTrue(reader.getNumDirtyChunks() <= 2)
        ir.close()
        iw.close()
        dir.close()
    }

    // tests inherited from BaseTermVectorsFormatTestCase

    @Test
    override fun testRareVectors() = super.testRareVectors()

    @Test
    override fun testHighFreqs() = super.testHighFreqs()

    @Test
    override fun testLotsOfFields() = super.testLotsOfFields()

    @Test
    override fun testMixedOptions() = super.testMixedOptions()

    @Test
    override fun testRandom() = super.testRandom()

    @Test
    override fun testMerge() = super.testMerge()

    @Test
    override fun testMergeWithDeletes() = super.testMergeWithDeletes()

    @Test
    override fun testMergeWithIndexSort() = super.testMergeWithIndexSort()

    @Test
    override fun testMergeWithIndexSortAndDeletes() = super.testMergeWithIndexSortAndDeletes()

    @Test
    override fun testClone() = super.testClone()

    @Test
    override fun testPostingsEnumFreqs() = super.testPostingsEnumFreqs()

    @Test
    override fun testPostingsEnumPositions() = super.testPostingsEnumPositions()

    @Test
    override fun testPostingsEnumOffsets() = super.testPostingsEnumOffsets()

    @Test
    override fun testPostingsEnumOffsetsWithoutPositions() = super.testPostingsEnumOffsetsWithoutPositions()

    @Test
    override fun testPostingsEnumPayloads() = super.testPostingsEnumPayloads()

    @Test
    override fun testPostingsEnumAll() = super.testPostingsEnumAll()

}
