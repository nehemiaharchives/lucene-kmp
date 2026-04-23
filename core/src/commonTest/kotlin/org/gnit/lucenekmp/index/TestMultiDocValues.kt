/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.document.BinaryDocValuesField
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.SortedDocValuesField
import org.gnit.lucenekmp.document.SortedNumericDocValuesField
import org.gnit.lucenekmp.document.SortedSetDocValuesField
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.DocIdSetIterator.Companion.NO_MORE_DOCS
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Tests MultiDocValues versus ordinary segment merging */
class TestMultiDocValues : LuceneTestCase() {
    @Test
    @Throws(Exception::class)
    fun testNumerics() {
        val dir = newDirectory()
        val doc = Document()
        val field: Field = NumericDocValuesField("numbers", 0)
        doc.add(field)

        val iwc = newIndexWriterConfig(random(), MockAnalyzer(random()))
        iwc.setMergePolicy(newLogMergePolicy())
        val iw = RandomIndexWriter(random(), dir, iwc)

        val numDocs = if (TEST_NIGHTLY) atLeast(500) else atLeast(50)
        for (i in 0 until numDocs) {
            field.setLongValue(random().nextLong())
            iw.addDocument(doc)
            if (random().nextInt(17) == 0) {
                iw.commit()
            }
        }
        val ir = iw.getReader(true, false)
        iw.forceMerge(1)
        val ir2 = iw.getReader(true, false)
        val merged = getOnlyLeafReader(ir2)
        iw.close()

        val multi = MultiDocValues.getNumericValues(ir, "numbers")
        val single = merged.getNumericDocValues("numbers")
        for (i in 0 until numDocs) {
            assertEquals(i, multi!!.nextDoc())
            assertEquals(i, single!!.nextDoc())
            assertEquals(single.longValue(), multi.longValue())
        }
        testRandomAdvance(
            merged.getNumericDocValues("numbers")!!,
            MultiDocValues.getNumericValues(ir, "numbers")!!
        )
        testRandomAdvanceExact(
            merged.getNumericDocValues("numbers")!!,
            MultiDocValues.getNumericValues(ir, "numbers")!!,
            merged.maxDoc()
        )

        ir.close()
        ir2.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testBinary() {
        val dir = newDirectory()
        val doc = Document()
        val field: Field = BinaryDocValuesField("bytes", BytesRef())
        doc.add(field)

        val iwc = newIndexWriterConfig(random(), MockAnalyzer(random()))
        iwc.setMergePolicy(newLogMergePolicy())
        val iw = RandomIndexWriter(random(), dir, iwc)

        val numDocs = if (TEST_NIGHTLY) atLeast(500) else atLeast(50)

        for (i in 0 until numDocs) {
            val ref = BytesRef(TestUtil.randomUnicodeString(random()))
            field.setBytesValue(ref)
            iw.addDocument(doc)
            if (random().nextInt(17) == 0) {
                iw.commit()
            }
        }
        val ir = iw.getReader(true, false)
        iw.forceMerge(1)
        val ir2 = iw.getReader(true, false)
        val merged = getOnlyLeafReader(ir2)
        iw.close()

        val multi = MultiDocValues.getBinaryValues(ir, "bytes")
        val single = merged.getBinaryDocValues("bytes")
        for (i in 0 until numDocs) {
            assertEquals(i, multi!!.nextDoc())
            assertEquals(i, single!!.nextDoc())
            val expected = BytesRef.deepCopyOf(requireNotNull(single.binaryValue()))
            val actual = multi.binaryValue()
            assertEquals(expected, actual)
        }
        testRandomAdvance(
            merged.getBinaryDocValues("bytes")!!,
            MultiDocValues.getBinaryValues(ir, "bytes")!!
        )
        testRandomAdvanceExact(
            merged.getBinaryDocValues("bytes")!!,
            MultiDocValues.getBinaryValues(ir, "bytes")!!,
            merged.maxDoc()
        )

        ir.close()
        ir2.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testSorted() {
        val dir = newDirectory()
        val doc = Document()
        val field: Field = SortedDocValuesField("bytes", BytesRef())
        doc.add(field)

        val iwc = newIndexWriterConfig(random(), MockAnalyzer(random()))
        iwc.setMergePolicy(newLogMergePolicy())
        val iw = RandomIndexWriter(random(), dir, iwc)

        val numDocs = if (TEST_NIGHTLY) atLeast(500) else atLeast(50)
        for (i in 0 until numDocs) {
            val ref = BytesRef(TestUtil.randomUnicodeString(random()))
            field.setBytesValue(ref)
            if (random().nextInt(7) == 0) {
                iw.addDocument(Document())
            }
            iw.addDocument(doc)
            if (random().nextInt(17) == 0) {
                iw.commit()
            }
        }
        val ir = iw.getReader(true, false)
        iw.forceMerge(1)
        val ir2 = iw.getReader(true, false)
        val merged = getOnlyLeafReader(ir2)
        iw.close()
        val multi = MultiDocValues.getSortedValues(ir, "bytes")
        val single = merged.getSortedDocValues("bytes")
        assertEquals(single!!.valueCount, multi!!.valueCount)
        while (true) {
            assertEquals(single.nextDoc(), multi.nextDoc())
            if (single.docID() == NO_MORE_DOCS) {
                break
            }

            // check value
            val expected = BytesRef.deepCopyOf(requireNotNull(single.lookupOrd(single.ordValue())))
            val actual = multi.lookupOrd(multi.ordValue())
            assertEquals(expected, actual)

            // check ord
            assertEquals(single.ordValue(), multi.ordValue())
        }
        testRandomAdvance(
            merged.getSortedDocValues("bytes")!!,
            MultiDocValues.getSortedValues(ir, "bytes")!!
        )
        testRandomAdvanceExact(
            merged.getSortedDocValues("bytes")!!,
            MultiDocValues.getSortedValues(ir, "bytes")!!,
            merged.maxDoc()
        )
        ir.close()
        ir2.close()
        dir.close()
    }

    // tries to make more dups than testSorted
    @Test
    @Throws(Exception::class)
    fun testSortedWithLotsOfDups() {
        val dir = newDirectory()
        val doc = Document()
        val field: Field = SortedDocValuesField("bytes", BytesRef())
        doc.add(field)

        val iwc = newIndexWriterConfig(random(), MockAnalyzer(random()))
        iwc.setMergePolicy(newLogMergePolicy())
        val iw = RandomIndexWriter(random(), dir, iwc)

        val numDocs = if (TEST_NIGHTLY) atLeast(500) else atLeast(50)
        for (i in 0 until numDocs) {
            val ref = BytesRef(TestUtil.randomSimpleString(random(), 2))
            field.setBytesValue(ref)
            iw.addDocument(doc)
            if (random().nextInt(17) == 0) {
                iw.commit()
            }
        }
        val ir = iw.getReader(true, false)
        iw.forceMerge(1)
        val ir2 = iw.getReader(true, false)
        val merged = getOnlyLeafReader(ir2)
        iw.close()

        val multi = MultiDocValues.getSortedValues(ir, "bytes")
        val single = merged.getSortedDocValues("bytes")
        assertEquals(single!!.valueCount, multi!!.valueCount)
        for (i in 0 until numDocs) {
            assertEquals(i, multi.nextDoc())
            assertEquals(i, single.nextDoc())
            // check ord
            assertEquals(single.ordValue(), multi.ordValue())
            // check ord value
            val expected = BytesRef.deepCopyOf(requireNotNull(single.lookupOrd(single.ordValue())))
            val actual = multi.lookupOrd(multi.ordValue())
            assertEquals(expected, actual)
        }
        testRandomAdvance(
            merged.getSortedDocValues("bytes")!!,
            MultiDocValues.getSortedValues(ir, "bytes")!!
        )
        testRandomAdvanceExact(
            merged.getSortedDocValues("bytes")!!,
            MultiDocValues.getSortedValues(ir, "bytes")!!,
            merged.maxDoc()
        )

        ir.close()
        ir2.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testSortedSet() {
        val dir = newDirectory()

        val iwc = newIndexWriterConfig(random(), MockAnalyzer(random()))
        iwc.setMergePolicy(newLogMergePolicy())
        val iw = RandomIndexWriter(random(), dir, iwc)

        val numDocs = if (TEST_NIGHTLY) atLeast(500) else atLeast(50)
        for (i in 0 until numDocs) {
            val doc = Document()
            val numValues = random().nextInt(5)
            for (j in 0 until numValues) {
                doc.add(
                    SortedSetDocValuesField(
                        "bytes",
                        BytesRef(TestUtil.randomUnicodeString(random()))
                    )
                )
            }
            iw.addDocument(doc)
            if (random().nextInt(17) == 0) {
                iw.commit()
            }
        }
        val ir = iw.getReader(true, false)
        iw.forceMerge(1)
        val ir2 = iw.getReader(true, false)
        val merged = getOnlyLeafReader(ir2)
        iw.close()

        val multi = MultiDocValues.getSortedSetValues(ir, "bytes")
        val single = merged.getSortedSetDocValues("bytes")
        if (multi == null) {
            assertNull(single)
        } else {
            assertEquals(single!!.valueCount, multi.valueCount)
            // check values
            for (i in 0L until single.valueCount) {
                val expected = BytesRef.deepCopyOf(requireNotNull(single.lookupOrd(i)))
                val actual = multi.lookupOrd(i)
                assertEquals(expected, actual)
            }
            // check ord list
            while (true) {
                val docID = single.nextDoc()
                assertEquals(docID, multi.nextDoc())
                if (docID == NO_MORE_DOCS) {
                    break
                }

                assertEquals(single.docValueCount(), multi.docValueCount())
                for (i in 0 until single.docValueCount()) {
                    assertEquals(single.nextOrd(), multi.nextOrd())
                }
            }
        }
        testRandomAdvance(
            merged.getSortedSetDocValues("bytes")!!,
            MultiDocValues.getSortedSetValues(ir, "bytes")!!
        )
        testRandomAdvanceExact(
            merged.getSortedSetDocValues("bytes")!!,
            MultiDocValues.getSortedSetValues(ir, "bytes")!!,
            merged.maxDoc()
        )

        ir.close()
        ir2.close()
        dir.close()
    }

    // tries to make more dups than testSortedSet
    @Test
    @Throws(Exception::class)
    fun testSortedSetWithDups() {
        val dir = newDirectory()

        val iwc = newIndexWriterConfig(random(), MockAnalyzer(random()))
        iwc.setMergePolicy(newLogMergePolicy())
        val iw = RandomIndexWriter(random(), dir, iwc)

        val numDocs = if (TEST_NIGHTLY) atLeast(500) else atLeast(50)
        for (i in 0 until numDocs) {
            val doc = Document()
            val numValues = random().nextInt(5)
            for (j in 0 until numValues) {
                doc.add(
                    SortedSetDocValuesField(
                        "bytes",
                        BytesRef(TestUtil.randomSimpleString(random(), 2))
                    )
                )
            }
            iw.addDocument(doc)
            if (random().nextInt(17) == 0) {
                iw.commit()
            }
        }
        val ir = iw.getReader(true, false)
        iw.forceMerge(1)
        val ir2 = iw.getReader(true, false)
        val merged = getOnlyLeafReader(ir2)
        iw.close()

        val multi = MultiDocValues.getSortedSetValues(ir, "bytes")
        val single = merged.getSortedSetDocValues("bytes")
        if (multi == null) {
            assertNull(single)
        } else {
            assertEquals(single!!.valueCount, multi.valueCount)
            // check values
            for (i in 0L until single.valueCount) {
                val expected = BytesRef.deepCopyOf(requireNotNull(single.lookupOrd(i)))
                val actual = multi.lookupOrd(i)
                assertEquals(expected, actual)
            }
            // check ord list
            while (true) {
                val docID = single.nextDoc()
                assertEquals(docID, multi.nextDoc())
                if (docID == NO_MORE_DOCS) {
                    break
                }

                assertEquals(single.docValueCount(), multi.docValueCount())
                for (i in 0 until single.docValueCount()) {
                    assertEquals(single.nextOrd(), multi.nextOrd())
                }
            }
        }
        testRandomAdvance(
            merged.getSortedSetDocValues("bytes")!!,
            MultiDocValues.getSortedSetValues(ir, "bytes")!!
        )
        testRandomAdvanceExact(
            merged.getSortedSetDocValues("bytes")!!,
            MultiDocValues.getSortedSetValues(ir, "bytes")!!,
            merged.maxDoc()
        )

        ir.close()
        ir2.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testSortedNumeric() {
        val dir = newDirectory()

        val iwc = newIndexWriterConfig(random(), MockAnalyzer(random()))
        iwc.setMergePolicy(newLogMergePolicy())
        val iw = RandomIndexWriter(random(), dir, iwc)

        val numDocs = if (TEST_NIGHTLY) atLeast(500) else atLeast(50)
        for (i in 0 until numDocs) {
            val doc = Document()
            val numValues = random().nextInt(5)
            for (j in 0 until numValues) {
                doc.add(
                    SortedNumericDocValuesField(
                        "nums",
                        TestUtil.nextLong(random(), Long.MIN_VALUE, Long.MAX_VALUE)
                    )
                )
            }
            iw.addDocument(doc)
            if (random().nextInt(17) == 0) {
                iw.commit()
            }
        }
        val ir = iw.getReader(true, false)
        iw.forceMerge(1)
        val ir2 = iw.getReader(true, false)
        val merged = getOnlyLeafReader(ir2)
        iw.close()

        val multi = MultiDocValues.getSortedNumericValues(ir, "nums")
        val single = merged.getSortedNumericDocValues("nums")
        if (multi == null) {
            assertNull(single)
        } else {
            // check values
            for (i in 0 until numDocs) {
                if (i > single!!.docID()) {
                    assertEquals(single.nextDoc(), multi.nextDoc())
                }
                if (i == single!!.docID()) {
                    assertEquals(single.docValueCount(), multi.docValueCount())
                    for (j in 0 until single.docValueCount()) {
                        assertEquals(single.nextValue(), multi.nextValue())
                    }
                }
            }
        }
        testRandomAdvance(
            merged.getSortedNumericDocValues("nums")!!,
            MultiDocValues.getSortedNumericValues(ir, "nums")!!
        )
        testRandomAdvanceExact(
            merged.getSortedNumericDocValues("nums")!!,
            MultiDocValues.getSortedNumericValues(ir, "nums")!!,
            merged.maxDoc()
        )

        ir.close()
        ir2.close()
        dir.close()
    }

    @Throws(IOException::class)
    private fun testRandomAdvance(iter1: DocIdSetIterator, iter2: DocIdSetIterator) {
        assertEquals(-1, iter1.docID())
        assertEquals(-1, iter2.docID())

        while (iter1.docID() != NO_MORE_DOCS) {
            if (random().nextBoolean()) {
                assertEquals(iter1.nextDoc(), iter2.nextDoc())
            } else {
                val target = iter1.docID() + TestUtil.nextInt(random(), 1, 100)
                assertEquals(iter1.advance(target), iter2.advance(target))
            }
        }
    }

    @Throws(IOException::class)
    private fun testRandomAdvanceExact(iter1: DocValuesIterator, iter2: DocValuesIterator, maxDoc: Int) {
        var target = random().nextInt(min(maxDoc, 10))
        while (target < maxDoc) {
            val exists1 = iter1.advanceExact(target)
            val exists2 = iter2.advanceExact(target)
            assertEquals(exists1, exists2)
            target += random().nextInt(10)
        }
    }
}
