package org.gnit.lucenekmp.codecs.lucene90.compressing

import okio.IOException
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.StoredField
import org.gnit.lucenekmp.index.CodecReader
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.NoMergePolicy
import org.gnit.lucenekmp.jdkport.doubleToLongBits
import org.gnit.lucenekmp.jdkport.floatToIntBits
import org.gnit.lucenekmp.store.ByteArrayDataInput
import org.gnit.lucenekmp.store.ByteArrayDataOutput
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.codecs.compressing.CompressingCodec
import org.gnit.lucenekmp.tests.index.BaseStoredFieldsFormatTestCase
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestCompressingStoredFieldsFormat : BaseStoredFieldsFormatTestCase() {
    override val codec: Codec
        get() {
            return if (TEST_NIGHTLY) {
                CompressingCodec.randomInstance(random())
            } else {
                CompressingCodec.reasonableInstance(random())
            }
        }

    @Test
    @Throws(Exception::class)
    fun testZFloat() {
        val buffer = ByteArray(5) // we never need more than 5 bytes
        val out = ByteArrayDataOutput(buffer)
        val `in` = ByteArrayDataInput(buffer)

        // round-trip small integer values
        for (i in Short.MIN_VALUE..<Short.MAX_VALUE) {
            val f = i.toFloat()
            Lucene90CompressingStoredFieldsWriter.writeZFloat(out, f)
            `in`.reset(buffer, 0, out.position)
            val g: Float = Lucene90CompressingStoredFieldsReader.readZFloat(`in`)
            assertTrue(`in`.eof())
            assertEquals(Float.floatToIntBits(f).toLong(), Float.floatToIntBits(g).toLong())

            // check that compression actually works
            if (i >= -1 && i <= 123) {
                assertEquals(
                    1,
                    out.position.toLong()
                ) // single byte compression
            }
            out.reset(buffer)
        }

        // round-trip special values
        val special = floatArrayOf(
            -0.0f,
            +0.0f,
            Float.NEGATIVE_INFINITY,
            Float.POSITIVE_INFINITY,
            Float.MIN_VALUE,
            Float.MAX_VALUE,
            Float.NaN,
        )

        for (f in special) {
            Lucene90CompressingStoredFieldsWriter.writeZFloat(out, f)
            `in`.reset(buffer, 0, out.position)
            val g: Float = Lucene90CompressingStoredFieldsReader.readZFloat(`in`)
            assertTrue(`in`.eof())
            assertEquals(Float.floatToIntBits(f).toLong(), Float.floatToIntBits(g).toLong())
            out.reset(buffer)
        }

        // round-trip random values
        val r: Random = random()
        for (i in 0..99999) {
            val f: Float = r.nextFloat() * (random().nextInt(100) - 50)
            Lucene90CompressingStoredFieldsWriter.writeZFloat(out, f)
            assertTrue(out.position <= (if ((Float.floatToIntBits(f) ushr 31) == 1) 5 else 4), "length=" + out.position + ", f=" + f)
            `in`.reset(buffer, 0, out.position)
            val g: Float = Lucene90CompressingStoredFieldsReader.readZFloat(`in`)
            assertTrue(`in`.eof())
            assertEquals(Float.floatToIntBits(f).toLong(), Float.floatToIntBits(g).toLong())
            out.reset(buffer)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testZDouble() {
        val buffer = ByteArray(9) // we never need more than 9 bytes
        val out = ByteArrayDataOutput(buffer)
        val `in` = ByteArrayDataInput(buffer)

        // round-trip small integer values
        for (i in Short.MIN_VALUE..<Short.MAX_VALUE) {
            val x = i.toDouble()
            Lucene90CompressingStoredFieldsWriter.writeZDouble(out, x)
            `in`.reset(buffer, 0, out.position)
            val y: Double = Lucene90CompressingStoredFieldsReader.readZDouble(`in`)
            assertTrue(`in`.eof())
            assertEquals(Double.doubleToLongBits(x), Double.doubleToLongBits(y))

            // check that compression actually works
            if (i >= -1 && i <= 124) {
                assertEquals(
                    1,
                    out.position.toLong()
                ) // single byte compression
            }
            out.reset(buffer)
        }

        // round-trip special values
        val special = doubleArrayOf(
            -0.0,
            +0.0,
            Double.NEGATIVE_INFINITY,
            Double.POSITIVE_INFINITY,
            Double.MIN_VALUE,
            Double.MAX_VALUE,
            Double.NaN
        )

        for (x in special) {
            Lucene90CompressingStoredFieldsWriter.writeZDouble(out, x)
            `in`.reset(buffer, 0, out.position)
            val y: Double = Lucene90CompressingStoredFieldsReader.readZDouble(`in`)
            assertTrue(`in`.eof())
            assertEquals(Double.doubleToLongBits(x), Double.doubleToLongBits(y))
            out.reset(buffer)
        }

        // round-trip random values
        val r: Random = random()
        for (i in 0..99999) {
            val x: Double = r.nextDouble() * (random().nextInt(100) - 50)
            Lucene90CompressingStoredFieldsWriter.writeZDouble(out, x)
            assertTrue(out.position <= (if (x < 0) 9 else 8), "length=" + out.position + ", d=" + x)
            `in`.reset(buffer, 0, out.position)
            val y: Double = Lucene90CompressingStoredFieldsReader.readZDouble(`in`)
            assertTrue(`in`.eof())
            assertEquals(Double.doubleToLongBits(x), Double.doubleToLongBits(y))
            out.reset(buffer)
        }

        // same with floats
        for (i in 0..99999) {
            val x = (r.nextFloat() * (random().nextInt(100) - 50)).toDouble()
            Lucene90CompressingStoredFieldsWriter.writeZDouble(out, x)
            assertTrue(out.position <= 5, "length=" + out.position + ", d=" + x)
            `in`.reset(buffer, 0, out.position)
            val y: Double = Lucene90CompressingStoredFieldsReader.readZDouble(`in`)
            assertTrue(`in`.eof())
            assertEquals(Double.doubleToLongBits(x), Double.doubleToLongBits(y))
            out.reset(buffer)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testTLong() {
        val buffer = ByteArray(10) // we never need more than 10 bytes
        val out = ByteArrayDataOutput(buffer)
        val `in` = ByteArrayDataInput(buffer)

        // round-trip small integer values
        for (i in Short.MIN_VALUE..<Short.MAX_VALUE) {
            for (mul in longArrayOf(SECOND, HOUR, DAY)) {
                val l1 = i.toLong() * mul
                Lucene90CompressingStoredFieldsWriter.writeTLong(out, l1)
                `in`.reset(buffer, 0, out.position)
                val l2: Long = Lucene90CompressingStoredFieldsReader.readTLong(`in`)
                assertTrue(`in`.eof())
                assertEquals(l1, l2)

                // check that compression actually works
                if (i >= -16 && i <= 15) {
                    assertEquals(
                        1,
                        out.position.toLong()
                    ) // single byte compression
                }
                out.reset(buffer)
            }
        }

        // round-trip random values
        val r: Random = random()
        for (i in 0..99999) {
            val numBits: Int = r.nextInt(65)
            var l1: Long = r.nextLong() and ((1L shl numBits) - 1)
            when (r.nextInt(4)) {
                0 -> l1 *= SECOND
                1 -> l1 *= HOUR
                2 -> l1 *= DAY
                else -> {}
            }
            Lucene90CompressingStoredFieldsWriter.writeTLong(out, l1)
            `in`.reset(buffer, 0, out.position)
            val l2: Long = Lucene90CompressingStoredFieldsReader.readTLong(`in`)
            assertTrue(`in`.eof())
            assertEquals(l1, l2)
            out.reset(buffer)
        }
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
        iwConf.setCodec(
            CompressingCodec.randomInstance(
                random(),
                4 * 1024,
                4,
                false,
                8
            )
        )
        val iw = IndexWriter(dir, iwConf)
        var ir: DirectoryReader = DirectoryReader.open(iw)
        for (i in 0..4) {
            val doc = Document()
            doc.add(StoredField("text", "not very long at all"))
            iw.addDocument(doc)
            // force flush
            val ir2: DirectoryReader? = DirectoryReader.openIfChanged(ir)
            assertNotNull(ir2)
            ir.close()
            ir = ir2
            // examine dirty counts:
            for (leaf in ir2.leaves()) {
                val sr: CodecReader = leaf.reader() as CodecReader
                val reader: Lucene90CompressingStoredFieldsReader = sr.fieldsReader as Lucene90CompressingStoredFieldsReader
                assertTrue(reader.getNumDirtyDocs() > 0)
                assertTrue(reader.getNumDirtyDocs() < 100) // can't be gte the number of docs per chunk
                assertEquals(1, reader.getNumDirtyChunks())
            }
        }
        iw.config.setMergePolicy(newLogMergePolicy())
        iw.forceMerge(1)
        // add a single doc and merge again
        val doc = Document()
        doc.add(StoredField("text", "not very long at all"))
        iw.addDocument(doc)
        iw.forceMerge(1)
        val ir2: DirectoryReader? = DirectoryReader.openIfChanged(ir)
        assertNotNull(ir2)
        ir.close()
        ir = ir2
        val sr: CodecReader = getOnlyLeafReader(ir) as CodecReader
        val reader: Lucene90CompressingStoredFieldsReader =
            sr.fieldsReader as Lucene90CompressingStoredFieldsReader
        // at most 2: the 5 chunks from 5 doc segment will be collapsed into a single chunk
        assertTrue(reader.getNumDirtyChunks() <= 2)
        ir.close()
        iw.close()
        dir.close()
    }


    // tests inherited from BaseStoredFieldsFormatTestCase

    @Test
    override fun testRandomStoredFields() = super.testRandomStoredFields()

    @Test
    override fun testStoredFieldsOrder() = super.testStoredFieldsOrder()

    @Test
    override fun testBinaryFieldOffsetLength() = super.testBinaryFieldOffsetLength()

    @Test
    override fun testNumericField() = super.testNumericField()

    @Test
    override fun testIndexedBit() = super.testIndexedBit()

    @Test
    override fun testReadSkip() = super.testReadSkip()

    @Test
    override fun testEmptyDocs() = super.testEmptyDocs()

    @Test
    override fun testConcurrentReads() = super.testConcurrentReads()

    @Test
    override fun testWriteReadMerge() = super.testWriteReadMerge()

    @Test
    override fun testMergeFilterReader() = super.testMergeFilterReader()

    @Test
    override fun testBigDocuments() = super.testBigDocuments()

    @Test
    override fun testBulkMergeWithDeletes() = super.testBulkMergeWithDeletes()

    @Test
    override fun testMismatchedFields() = super.testMismatchedFields()

    @Test
    override fun testRandomStoredFieldsWithIndexSort() = super.testRandomStoredFieldsWithIndexSort()

    @Test
    override fun testLineFileDocs() = super.testLineFileDocs()

        companion object {
        const val SECOND: Long = 1000L
        const val HOUR: Long = 60 * 60 * SECOND
        const val DAY: Long = 24 * HOUR
    }
}
