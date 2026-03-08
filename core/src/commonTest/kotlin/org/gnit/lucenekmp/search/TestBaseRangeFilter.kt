package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.DoublePoint
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FloatPoint
import org.gnit.lucenekmp.document.IntPoint
import org.gnit.lucenekmp.document.LongPoint
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.SortedDocValuesField
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriterConfig.OpenMode
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

open class TestBaseRangeFilter : LuceneTestCase() {

    /**
     * Collation interacts badly with hyphens -- collation produces different ordering than Unicode
     * code-point ordering -- so two indexes are created: one which can't have negative random
     * integers, for testing collated ranges, and the other which can have negative random integers,
     * for all other tests.
     */
    class TestIndex(
        var minR: Int,
        var maxR: Int,
        val allowNegativeRandomInts: Boolean,
        val index: Directory
    )

    companion object {
        const val F: Boolean = false
        const val T: Boolean = true

        var signedIndexReader: IndexReader? = null
        var unsignedIndexReader: IndexReader? = null

        var signedIndexDir: TestIndex? = null
        var unsignedIndexDir: TestIndex? = null

        const val minId: Int = 0
        var maxId: Int = 0

        val intLength: Int = Int.MAX_VALUE.toString().length

        /** a simple padding function that should work with any int */
        fun pad(n: Int): String {
            val b = StringBuilder(40)
            var p = "0"
            var n = n
            if (n < 0) {
                p = "-"
                n = Int.MAX_VALUE + n + 1
            }
            b.append(p)
            val s = n.toString()
            for (i in s.length..intLength) {
                b.append("0")
            }
            b.append(s)

            return b.toString()
        }
    }

    @BeforeTest
    @Throws(Exception::class)
    open fun beforeClassBaseTestRangeFilter() {
        maxId = atLeast(500)
        signedIndexDir = TestIndex(Int.MAX_VALUE, Int.MIN_VALUE, true, newDirectory(random()))
        unsignedIndexDir = TestIndex(Int.MAX_VALUE, 0, false, newDirectory(random()))
        signedIndexReader = build(random(), signedIndexDir!!)
        unsignedIndexReader = build(random(), unsignedIndexDir!!)
    }

    @AfterTest
    @Throws(Exception::class)
    open fun afterClassBaseTestRangeFilter() {
        signedIndexReader!!.close()
        unsignedIndexReader!!.close()
        signedIndexDir!!.index.close()
        unsignedIndexDir!!.index.close()
        signedIndexReader = null
        unsignedIndexReader = null
        signedIndexDir = null
        unsignedIndexDir = null
    }

    @Throws(IOException::class)
    private fun build(random: Random, index: TestIndex): IndexReader {
        /* build an index */

        val doc = Document()
        val idField = newStringField(random, "id", "", Field.Store.YES)
        val idDVField = SortedDocValuesField("id", BytesRef())
        val intIdField = IntPoint("id_int", 0)
        val intDVField = NumericDocValuesField("id_int", 0)
        val floatIdField = FloatPoint("id_float", 0f)
        val floatDVField = NumericDocValuesField("id_float", 0)
        val longIdField = LongPoint("id_long", 0)
        val longDVField = NumericDocValuesField("id_long", 0)
        val doubleIdField = DoublePoint("id_double", 0.0)
        val doubleDVField = NumericDocValuesField("id_double", 0)
        val randField = newStringField(random, "rand", "", Field.Store.YES)
        val randDVField = SortedDocValuesField("rand", BytesRef())
        val bodyField = newStringField(random, "body", "", Field.Store.NO)
        val bodyDVField = SortedDocValuesField("body", BytesRef())
        doc.add(idField)
        doc.add(idDVField)
        doc.add(intIdField)
        doc.add(intDVField)
        doc.add(floatIdField)
        doc.add(floatDVField)
        doc.add(longIdField)
        doc.add(longDVField)
        doc.add(doubleIdField)
        doc.add(doubleDVField)
        doc.add(randField)
        doc.add(randDVField)
        doc.add(bodyField)
        doc.add(bodyDVField)

        val writer = RandomIndexWriter(
            random,
            index.index,
            newIndexWriterConfig(random, MockAnalyzer(random))
                .setOpenMode(OpenMode.CREATE)
                .setMaxBufferedDocs(TestUtil.nextInt(random, 50, 1000))
                .setMergePolicy(newLogMergePolicy())
        )

        while (true) {
            var minCount = 0
            var maxCount = 0

            for (d in minId..maxId) {
                idField.setStringValue(pad(d))
                idDVField.setBytesValue(BytesRef(pad(d)))
                intIdField.setIntValue(d)
                intDVField.setLongValue(d.toLong())
                floatIdField.setFloatValue(d.toFloat())
                floatDVField.setLongValue(d.toFloat().toRawBits().toLong())
                longIdField.setLongValue(d.toLong())
                longDVField.setLongValue(d.toLong())
                doubleIdField.setDoubleValue(d.toDouble())
                doubleDVField.setLongValue(d.toDouble().toRawBits())
                val r = if (index.allowNegativeRandomInts) random.nextInt() else random.nextInt(Int.MAX_VALUE)
                if (index.maxR < r) {
                    index.maxR = r
                    maxCount = 1
                } else if (index.maxR == r) {
                    maxCount++
                }

                if (r < index.minR) {
                    index.minR = r
                    minCount = 1
                } else if (r == index.minR) {
                    minCount++
                }
                randField.setStringValue(pad(r))
                randDVField.setBytesValue(BytesRef(pad(r)))
                bodyField.setStringValue("body")
                bodyDVField.setBytesValue(BytesRef("body"))
                writer.addDocument(doc)
            }

            if (minCount == 1 && maxCount == 1) {
                val ir = writer.getReader(true, false)
                writer.close()
                return ir
            }

            // try again
            writer.deleteAll()
        }
    }

    @Test
    open fun testPad() {
        val tests = intArrayOf(-9999999, -99560, -100, -3, -1, 0, 3, 9, 10, 1000, 999999999)
        for (i in 0..<tests.size - 1) {
            val a = tests[i]
            val b = tests[i + 1]
            val aa = pad(a)
            val bb = pad(b)
            val label = "$a:$aa vs $b:$bb"
            assertEquals(aa.length, bb.length, "length of $label")
            assertTrue(aa.compareTo(bb) < 0, "compare less than $label")
        }
    }
}
