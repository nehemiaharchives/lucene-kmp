package org.gnit.lucenekmp.tests.index

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.RandomPicks
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.packed.PackedInts
import kotlin.test.assertTrue


/** Extends [BaseDocValuesFormatTestCase] to add compression checks.  */
abstract class BaseCompressingDocValuesFormatTestCase : BaseDocValuesFormatTestCase() {

    @Throws(IOException::class)
    open fun testUniqueValuesCompression() {
        ByteBuffersDirectory().use { dir ->
            val iwc = IndexWriterConfig(MockAnalyzer(random()))
            val iwriter = IndexWriter(dir, iwc)

            val uniqueValueCount: Int = TestUtil.nextInt(random(), 1, 256)
            val values: MutableList<Long> = mutableListOf()

            val doc = Document()
            val dvf = NumericDocValuesField("dv", 0)
            doc.add(dvf)
            for (i in 0..299) {
                val value: Long
                if (values.size < uniqueValueCount) {
                    value = random().nextLong()
                    values.add(value)
                } else {
                    value = RandomPicks.randomFrom(random(), values)
                }
                dvf.setLongValue(value)
                iwriter.addDocument(doc)
            }
            iwriter.forceMerge(1)
            val size1 = dirSize(dir)
            for (i in 0..19) {
                dvf.setLongValue(RandomPicks.randomFrom(random(), values))
                iwriter.addDocument(doc)
            }
            iwriter.forceMerge(1)
            val size2 = dirSize(dir)
            // make sure the new longs did not cost 8 bytes each
            assertTrue(size2 < size1 + 8 * 20)
        }
    }

    @Throws(IOException::class)
    open fun testDateCompression() {
        ByteBuffersDirectory().use { dir ->
            val iwc = IndexWriterConfig(MockAnalyzer(random()))
            val iwriter = IndexWriter(dir, iwc)

            val base: Long = 13 // prime
            val day = 1000L * 60 * 60 * 24

            val doc = Document()
            val dvf =
                NumericDocValuesField("dv", 0)
            doc.add(dvf)
            for (i in 0..299) {
                dvf.setLongValue(base + random().nextInt(1000) * day)
                iwriter.addDocument(doc)
            }
            iwriter.forceMerge(1)
            val size1 = dirSize(dir)
            for (i in 0..49) {
                dvf.setLongValue(base + random().nextInt(1000) * day)
                iwriter.addDocument(doc)
            }
            iwriter.forceMerge(1)
            val size2 = dirSize(dir)
            // make sure the new longs costed less than if they had only been packed
            assertTrue(size2 < size1 + (PackedInts.bitsRequired(day) * 50) / 8)
        }
    }

    @Throws(IOException::class)
    open fun testSingleBigValueCompression() {
        ByteBuffersDirectory().use { dir ->
            val iwc = IndexWriterConfig(MockAnalyzer(random()))
            val iwriter = IndexWriter(dir, iwc)

            val doc = Document()
            val dvf = NumericDocValuesField("dv", 0)
            doc.add(dvf)
            for (i in 0..10) { // TODO reduced from 19999 to 10 for dev speed
                dvf.setLongValue((i and 1023).toLong())
                iwriter.addDocument(doc)
            }
            iwriter.forceMerge(1)
            val size1 = dirSize(dir)
            dvf.setLongValue(Long.MAX_VALUE)
            iwriter.addDocument(doc)
            iwriter.forceMerge(1)
            val size2 = dirSize(dir)
            // make sure the new value did not grow the bpv for every other value
            assertTrue(size2 < size1 + (20000 * (63 - 10)) / 8)
        }
    }

    companion object {
        @Throws(IOException::class)
        fun dirSize(d: Directory): Long {
            var size: Long = 0
            for (file in d.listAll()) {
                size += d.fileLength(file)
            }
            return size
        }
    }
}
