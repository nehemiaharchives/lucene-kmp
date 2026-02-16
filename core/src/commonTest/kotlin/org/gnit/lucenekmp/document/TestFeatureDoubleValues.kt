package org.gnit.lucenekmp.document

import okio.IOException
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.search.DoubleValues
import org.gnit.lucenekmp.search.DoubleValuesSource
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TestFeatureDoubleValues : LuceneTestCase() {

    @Test
    @Throws(IOException::class)
    fun testFeature() {
        val dir: Directory = newDirectory()
        val config: IndexWriterConfig = newIndexWriterConfig().setMergePolicy(newLogMergePolicy(random().nextBoolean()))
        val writer = RandomIndexWriter(random(), dir, config)
        var doc = Document()
        doc.add(FeatureField("field", "name", 30f))
        writer.addDocument(doc)
        doc = Document()
        doc.add(FeatureField("field", "name", 1f))
        writer.addDocument(doc)
        doc = Document()
        doc.add(FeatureField("field", "name", 4f))
        writer.addDocument(doc)
        writer.forceMerge(1)
        val ir: IndexReader = writer.reader
        writer.close()

        assertEquals(1, ir.leaves().size.toLong())
        val context: LeafReaderContext = ir.leaves()[0]
        val valuesSource: DoubleValuesSource = FeatureField.newDoubleValues("field", "name")
        val values: DoubleValues = valuesSource.getValues(context, null)

        assertTrue(values.advanceExact(0))
        assertEquals(30.0, values.doubleValue(), 0.0)
        assertTrue(values.advanceExact(1))
        assertEquals(1.0, values.doubleValue(), 0.0)
        assertTrue(values.advanceExact(2))
        assertEquals(4.0, values.doubleValue(), 0.0)
        assertFalse(values.advanceExact(3))

        ir.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testFeatureMissing() {
        val dir: Directory = newDirectory()
        val config: IndexWriterConfig = newIndexWriterConfig().setMergePolicy(newLogMergePolicy(random().nextBoolean()))
        val writer = RandomIndexWriter(random(), dir, config)
        var doc = Document()
        writer.addDocument(doc)
        doc = Document()
        doc.add(FeatureField("field", "name", 1f))
        writer.addDocument(doc)
        doc = Document()
        doc.add(FeatureField("field", "name", 4f))
        writer.addDocument(doc)
        writer.forceMerge(1)
        val ir: IndexReader = writer.reader
        writer.close()

        assertEquals(1, ir.leaves().size.toLong())
        val context: LeafReaderContext = ir.leaves()[0]
        val valuesSource: DoubleValuesSource = FeatureField.newDoubleValues("field", "name")
        val values: DoubleValues = valuesSource.getValues(context, null)

        assertFalse(values.advanceExact(0))
        assertTrue(values.advanceExact(1))
        assertEquals(1.0, values.doubleValue(), 0.0)
        assertTrue(values.advanceExact(2))
        assertEquals(4.0, values.doubleValue(), 0.0)
        assertFalse(values.advanceExact(3))

        ir.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testFeatureMissingFieldInSegment() {
        val dir: Directory = newDirectory()
        val config: IndexWriterConfig = newIndexWriterConfig().setMergePolicy(newLogMergePolicy(random().nextBoolean()))
        val writer = RandomIndexWriter(random(), dir, config)
        val doc = Document()
        writer.addDocument(doc)
        writer.commit()
        val ir: IndexReader = writer.reader
        writer.close()

        assertEquals(1, ir.leaves().size.toLong())
        val context: LeafReaderContext = ir.leaves()[0]
        val valuesSource: DoubleValuesSource = FeatureField.newDoubleValues("field", "name")
        val values: DoubleValues = valuesSource.getValues(context, null)

        assertFalse(values.advanceExact(0))
        assertFalse(values.advanceExact(1))

        ir.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testFeatureMissingFeatureNameInSegment() {
        val dir: Directory = newDirectory()
        val config: IndexWriterConfig = newIndexWriterConfig().setMergePolicy(newLogMergePolicy(random().nextBoolean()))
        val writer = RandomIndexWriter(random(), dir, config)
        val doc = Document()
        doc.add(FeatureField("field", "different_name", 0.5f))
        writer.addDocument(doc)
        writer.commit()
        val ir: IndexReader = writer.reader
        writer.close()

        assertEquals(1, ir.leaves().size.toLong())
        val context: LeafReaderContext = ir.leaves()[0]
        val valuesSource: DoubleValuesSource = FeatureField.newDoubleValues("field", "name")
        val values: DoubleValues = valuesSource.getValues(context, null)

        assertFalse(values.advanceExact(0))
        assertFalse(values.advanceExact(1))

        ir.close()
        dir.close()
    }

    @Test
    @Throws(IOException::class)
    fun testFeatureMultipleMissing() {
        val dir: Directory = newDirectory()
        val config: IndexWriterConfig = newIndexWriterConfig().setMergePolicy(newLogMergePolicy(random().nextBoolean()))
        val writer = RandomIndexWriter(random(), dir, config)
        var doc = Document()
        writer.addDocument(doc)
        doc = Document()
        writer.addDocument(doc)
        doc = Document()
        writer.addDocument(doc)
        doc = Document()
        writer.addDocument(doc)
        doc = Document()
        writer.addDocument(doc)
        doc = Document()
        doc.add(FeatureField("field", "name", 1f))
        writer.addDocument(doc)
        doc = Document()
        doc.add(FeatureField("field", "name", 4f))
        writer.addDocument(doc)
        writer.forceMerge(1)
        val ir: IndexReader = writer.reader
        writer.close()

        assertEquals(1, ir.leaves().size.toLong())
        val context: LeafReaderContext = ir.leaves()[0]
        val valuesSource: DoubleValuesSource = FeatureField.newDoubleValues("field", "name")
        val values: DoubleValues = valuesSource.getValues(context, null)

        assertFalse(values.advanceExact(0))
        assertFalse(values.advanceExact(1))
        assertFalse(values.advanceExact(2))
        assertFalse(values.advanceExact(3))
        assertFalse(values.advanceExact(4))
        assertTrue(values.advanceExact(5))
        assertEquals(1.0, values.doubleValue(), 0.0)
        assertTrue(values.advanceExact(6))
        assertEquals(4.0, values.doubleValue(), 0.0)
        assertFalse(values.advanceExact(7))

        ir.close()
        dir.close()
    }

    @Test
    fun testHashCodeAndEquals() {
        val valuesSource = FeatureDoubleValuesSource("test_field", "test_feature")
        val equal = FeatureDoubleValuesSource("test_field", "test_feature")

        val differentField = FeatureDoubleValuesSource("other field", "test_feature")
        val differentFeature = FeatureDoubleValuesSource("test_field", "other_feature")
        val otherImpl: DoubleValuesSource =
            object : DoubleValuesSource() {
                override fun isCacheable(ctx: LeafReaderContext): Boolean {
                    return false
                }

                override fun toString(): String {
                    /*return null*/
                    TODO("returning null is not good in kotlin, need to thin what to do here")
                }

                @Throws(IOException::class)
                override fun rewrite(reader: IndexSearcher): DoubleValuesSource {
                    /*return null*/
                    TODO("returning null is not good in kotlin, need to thin what to do here")
                }

                override fun needsScores(): Boolean {
                    return false
                }

                override fun hashCode(): Int {
                    return 0
                }

                @Throws(IOException::class)
                override fun getValues(ctx: LeafReaderContext, scores: DoubleValues?): DoubleValues {
                    /*return null*/
                    TODO("returning null is not good in kotlin, need to thin what to do here")
                }

                override fun equals(obj: Any?): Boolean {
                    return false
                }
            }

        assertTrue(valuesSource == equal)
        assertEquals(valuesSource.hashCode().toLong(), equal.hashCode().toLong())
        assertFalse(valuesSource == null)
        assertFalse(valuesSource == otherImpl)
        assertNotEquals(valuesSource.hashCode().toLong(), otherImpl.hashCode().toLong())
        assertFalse(valuesSource == differentField)
        assertNotEquals(valuesSource.hashCode().toLong(), differentField.hashCode().toLong())
        assertFalse(valuesSource == differentFeature)
        assertNotEquals(valuesSource.hashCode().toLong(), differentFeature.hashCode().toLong())
    }
}
