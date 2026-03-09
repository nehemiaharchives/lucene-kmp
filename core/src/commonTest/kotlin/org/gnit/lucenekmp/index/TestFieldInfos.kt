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

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.fail

class TestFieldInfos : LuceneTestCase() {
    @Test
    fun testFieldInfos() {
        val dir = newDirectory()
        val writer = IndexWriter(
            dir,
            newIndexWriterConfig(MockAnalyzer(random()))
                .setMergePolicy(NoMergePolicy.INSTANCE)
        )

        val d1 = Document()
        for (i in 0 until 15) {
            d1.add(StringField("f$i", "v$i", Field.Store.YES))
        }
        writer.addDocument(d1)
        writer.commit()

        val d2 = Document()
        d2.add(StringField("f0", "v0", Field.Store.YES))
        d2.add(StringField("f15", "v15", Field.Store.YES))
        d2.add(StringField("f16", "v16", Field.Store.YES))
        writer.addDocument(d2)
        writer.commit()

        val d3 = Document()
        writer.addDocument(d3)
        writer.close()

        val sis = SegmentInfos.readLatestCommit(dir)
        assertEquals(3, sis.size())

        val fis1 = IndexWriter.readFieldInfos(sis.info(0))
        val fis2 = IndexWriter.readFieldInfos(sis.info(1))
        val fis3 = IndexWriter.readFieldInfos(sis.info(2))

        // testing dense FieldInfos
        val it = fis1.iterator()
        var i = 0
        while (it.hasNext()) {
            val fi = it.next()
            assertEquals(i, fi.number)
            assertEquals("f$i", fi.name)
            assertEquals("f$i", fis1.fieldInfo(i)!!.name) // lookup by number
            assertEquals("f$i", fis1.fieldInfo("f$i")!!.name) // lookup by name
            i++
        }

        // testing sparse FieldInfos
        assertEquals("f0", fis2.fieldInfo(0)!!.name) // lookup by number
        assertEquals("f0", fis2.fieldInfo("f0")!!.name) // lookup by name
        assertNull(fis2.fieldInfo(1))
        assertNull(fis2.fieldInfo("f1"))
        assertEquals("f15", fis2.fieldInfo(15)!!.name)
        assertEquals("f15", fis2.fieldInfo("f15")!!.name)
        assertEquals("f16", fis2.fieldInfo(16)!!.name)
        assertEquals("f16", fis2.fieldInfo("f16")!!.name)

        // testing empty FieldInfos
        assertNull(fis3.fieldInfo(0)) // lookup by number
        assertNull(fis3.fieldInfo("f0")) // lookup by name
        assertEquals(0, fis3.size())
        val it3 = fis3.iterator()
        assertFalse(it3.hasNext())
        dir.close()
    }

    @Test
    fun testFieldAttributes() {
        val dir = newDirectory()
        val writer = IndexWriter(
            dir,
            newIndexWriterConfig(MockAnalyzer(random()))
                .setMergePolicy(NoMergePolicy.INSTANCE)
        )

        val type1 = FieldType()
        type1.setStored(true)
        type1.putAttribute("testKey1", "testValue1")

        val d1 = Document()
        d1.add(Field("f1", "v1", type1))
        val type2 = FieldType(type1)
        // changing the value after copying shouldn't impact the original type1
        type2.putAttribute("testKey1", "testValue2")
        writer.addDocument(d1)
        writer.commit()

        val d2 = Document()
        type1.putAttribute("testKey1", "testValueX")
        type1.putAttribute("testKey2", "testValue2")
        d2.add(Field("f1", "v2", type1))
        d2.add(Field("f2", "v2", type2))
        writer.addDocument(d2)
        writer.commit()
        writer.forceMerge(1)

        val reader: IndexReader = DirectoryReader.open(writer)
        val fis = FieldInfos.getMergedFieldInfos(reader)
        assertEquals(fis.size(), 2)
        val it = fis.iterator()
        while (it.hasNext()) {
            val fi = it.next()
            when (fi.name) {
                "f1" -> {
                    // testKey1 can point to either testValue1 or testValueX based on the order
                    // of merge, but we see textValueX winning here since segment_2 is merged on segment_1.
                    assertEquals("testValueX", fi.getAttribute("testKey1"))
                    assertEquals("testValue2", fi.getAttribute("testKey2"))
                }

                "f2" -> {
                    assertEquals("testValue2", fi.getAttribute("testKey1"))
                }

                else -> fail("Unknown field")
            }
        }
        reader.close()
        writer.close()
        dir.close()
    }

    @Test
    fun testFieldAttributesSingleSegment() {
        val dir = newDirectory()
        val writer = IndexWriter(
            dir,
            newIndexWriterConfig(MockAnalyzer(random()))
                .setMergePolicy(NoMergePolicy.INSTANCE)
        )

        val d1 = Document()
        val type1 = FieldType()
        type1.setStored(true)
        type1.putAttribute("att1", "attdoc1")
        d1.add(Field("f1", "v1", type1))
        // add field with the same name and an extra attribute
        type1.putAttribute("att2", "attdoc1")
        d1.add(Field("f1", "v1", type1))
        writer.addDocument(d1)

        val d2 = Document()
        type1.putAttribute("att1", "attdoc2")
        type1.putAttribute("att2", "attdoc2")
        type1.putAttribute("att3", "attdoc2")
        val type2 = FieldType()
        type2.setStored(true)
        type2.putAttribute("att4", "attdoc2")
        d2.add(Field("f1", "v2", type1))
        d2.add(Field("f2", "v2", type2))
        writer.addDocument(d2)
        writer.commit()

        val reader: IndexReader = DirectoryReader.open(writer)
        val fis = FieldInfos.getMergedFieldInfos(reader)

        // test that attributes for f1 are introduced by d1,
        // and not modified by d2
        val fi1 = fis.fieldInfo("f1")!!
        assertEquals("attdoc1", fi1.getAttribute("att1"))
        assertEquals("attdoc1", fi1.getAttribute("att2"))
        assertNull(fi1.getAttribute("att3"))

        // test that attributes for f2 are introduced by d2
        val fi2 = fis.fieldInfo("f2")!!
        assertEquals("attdoc2", fi2.getAttribute("att4"))

        reader.close()
        writer.close()
        dir.close()
    }

    @Test
    fun testMergedFieldInfos_empty() {
        val dir = newDirectory()
        val writer = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))

        val reader: IndexReader = DirectoryReader.open(writer)
        val actual = FieldInfos.getMergedFieldInfos(reader)

        assertSame(FieldInfos.EMPTY, actual)

        reader.close()
        writer.close()
        dir.close()
    }

    @Test
    fun testMergedFieldInfos_singleLeaf() {
        val dir = newDirectory()
        val writer = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))

        val d1 = Document()
        d1.add(StringField("f1", "v1", Field.Store.YES))
        writer.addDocument(d1)
        writer.commit()

        val d2 = Document()
        d2.add(StringField("f2", "v2", Field.Store.YES))
        writer.addDocument(d2)
        writer.commit()

        writer.forceMerge(1)

        val reader: IndexReader = DirectoryReader.open(writer)
        val actual = FieldInfos.getMergedFieldInfos(reader)
        val expected = reader.leaves()[0].reader().fieldInfos

        assertEquals(1, reader.leaves().size)
        assertSame(expected, actual)

        reader.close()
        writer.close()
        dir.close()
    }

    @Test
    fun testFieldNumbersAutoIncrement() {
        val fieldNumbers = FieldInfos.FieldNumbers("softDeletes", "parentDoc")
        for (i in 0 until 10) {
            fieldNumbers.addOrGet(
                FieldInfo(
                    "field$i",
                    -1,
                    false,
                    false,
                    false,
                    IndexOptions.NONE,
                    DocValuesType.NONE,
                    DocValuesSkipIndexType.NONE,
                    -1,
                    hashMapOf(),
                    0,
                    0,
                    0,
                    0,
                    VectorEncoding.FLOAT32,
                    VectorSimilarityFunction.EUCLIDEAN,
                    false,
                    false
                )
            )
        }
        var idx = fieldNumbers.addOrGet(
            FieldInfo(
                "EleventhField",
                -1,
                false,
                false,
                false,
                IndexOptions.NONE,
                DocValuesType.NONE,
                DocValuesSkipIndexType.NONE,
                -1,
                hashMapOf(),
                0,
                0,
                0,
                0,
                VectorEncoding.FLOAT32,
                VectorSimilarityFunction.EUCLIDEAN,
                false,
                false
            )
        )
        assertEquals(10, idx, "Field numbers 0 through 9 were allocated")

        fieldNumbers.clear()
        idx = fieldNumbers.addOrGet(
            FieldInfo(
                "PostClearField",
                -1,
                false,
                false,
                false,
                IndexOptions.NONE,
                DocValuesType.NONE,
                DocValuesSkipIndexType.NONE,
                -1,
                hashMapOf(),
                0,
                0,
                0,
                0,
                VectorEncoding.FLOAT32,
                VectorSimilarityFunction.EUCLIDEAN,
                false,
                false
            )
        )
        assertEquals(0, idx, "Field numbers should reset after clear()")
    }
}
