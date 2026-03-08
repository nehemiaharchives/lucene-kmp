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
import org.gnit.lucenekmp.document.BinaryPoint
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.KnnFloatVectorField
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.StoredField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.jdkport.ByteArrayOutputStream
import org.gnit.lucenekmp.jdkport.StandardCharsets
import org.gnit.lucenekmp.search.MatchAllDocsQuery
import org.gnit.lucenekmp.search.Sort
import org.gnit.lucenekmp.search.SortField
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.CannedTokenStream
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.Token
import org.gnit.lucenekmp.tests.index.BaseTestCheckIndex
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.NumericUtils
import org.gnit.lucenekmp.util.VectorUtil
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TestCheckIndex : BaseTestCheckIndex() {
    private lateinit var directory: Directory

    override fun getDirectory(): Directory {
        return directory
    }

    @BeforeTest
    @Throws(Exception::class)
    fun setUp() {
        directory = newDirectory()
    }

    @AfterTest
    @Throws(Exception::class)
    fun tearDown() {
        directory.close()
    }

    @Test
    @Throws(Exception::class)
    fun testCheckIndexAllValid() {
        newDirectory().use { dir ->
            val liveDocCount = 1 + random().nextInt(10)
            val config = newIndexWriterConfig()
            config.indexSort = Sort(SortField("sort_field", SortField.Type.INT, true))
            config.setSoftDeletesField("soft_delete")
            // preserves soft-deletes across merges
            config.mergePolicy =
                SoftDeletesRetentionMergePolicy(
                    "soft_delete",
                    { MatchAllDocsQuery() },
                    config.mergePolicy
                )
            IndexWriter(dir, config).use { w ->
                for (i in 0 until liveDocCount) {
                    val doc = Document()

                    // stored field
                    doc.add(StringField("id", random().nextInt().toString(), Field.Store.YES))
                    doc.add(StoredField("field", "value" + TestUtil.randomSimpleString(random())))

                    // vector
                    doc.add(KnnFloatVectorField("v1", randomVector(3)))
                    doc.add(KnnFloatVectorField("v2", randomVector(3)))

                    // doc value
                    doc.add(NumericDocValuesField("dv", random().nextLong()))

                    // doc value with skip index
                    doc.add(NumericDocValuesField.indexedField("dv_skip", random().nextLong()))

                    // point value
                    val point = ByteArray(4)
                    NumericUtils.intToSortableBytes(random().nextInt(), point, 0)
                    doc.add(BinaryPoint("point", arrayOf(point)))

                    // term vector
                    val token1 =
                        Token("bar", 0, 3).apply {
                            payload = BytesRef("pay1")
                        }
                    val token2 =
                        Token("bar", 4, 8).apply {
                            payload = BytesRef("pay2")
                        }
                    val ft = FieldType(TextField.TYPE_NOT_STORED)
                    ft.setStoreTermVectors(true)
                    ft.setStoreTermVectorPositions(true)
                    ft.setStoreTermVectorPayloads(true)
                    doc.add(Field("termvector", CannedTokenStream(token1, token2), ft))

                    w.addDocument(doc)
                }

                val tombstone = Document()
                tombstone.add(NumericDocValuesField("soft_delete", 1))
                w.softUpdateDocument(
                    Term("id", "1"),
                    tombstone,
                    NumericDocValuesField("soft_delete", 1)
                )
                w.forceMerge(1)
            }

            val output = ByteArrayOutputStream()
            val status =
                TestUtil.checkIndex(
                    dir,
                    CheckIndex.Level.MIN_LEVEL_FOR_INTEGRITY_CHECKS,
                    true,
                    true,
                    output
                )

            assertEquals(1, status.segmentInfos.size)

            val segStatus = status.segmentInfos[0]

            // confirm live docs testing status
            val liveDocStatus = requireNotNull(segStatus.liveDocStatus)
            assertEquals(0, liveDocStatus.numDeleted)
            assertTrue(output.toString(StandardCharsets.UTF_8).contains("test: check live docs"))
            assertNull(liveDocStatus.error)

            // confirm field infos testing status
            val fieldInfoStatus = requireNotNull(segStatus.fieldInfoStatus)
            assertEquals(9L, fieldInfoStatus.totFields)
            assertTrue(output.toString(StandardCharsets.UTF_8).contains("test: field infos"))
            assertNull(fieldInfoStatus.error)

            // confirm field norm (from term vector) testing status
            val fieldNormStatus = requireNotNull(segStatus.fieldNormStatus)
            assertEquals(1L, fieldNormStatus.totFields)
            assertTrue(output.toString(StandardCharsets.UTF_8).contains("test: field norms"))
            assertNull(fieldNormStatus.error)

            // confirm term index testing status
            val termIndexStatus = requireNotNull(segStatus.termIndexStatus)
            assertTrue(termIndexStatus.termCount > 0)
            assertTrue(termIndexStatus.totFreq > 0)
            assertTrue(termIndexStatus.totPos > 0)
            assertTrue(output.toString(StandardCharsets.UTF_8).contains("test: terms, freq, prox"))
            assertNull(termIndexStatus.error)

            // confirm stored field testing status
            // add storedField from tombstone doc
            val storedFieldStatus = requireNotNull(segStatus.storedFieldStatus)
            assertEquals(liveDocCount + 1, storedFieldStatus.docCount)
            assertEquals((2 * liveDocCount).toLong(), storedFieldStatus.totFields)
            assertTrue(output.toString(StandardCharsets.UTF_8).contains("test: stored fields"))
            assertNull(storedFieldStatus.error)

            // confirm term vector testing status
            val termVectorStatus = requireNotNull(segStatus.termVectorStatus)
            assertEquals(liveDocCount, termVectorStatus.docCount)
            assertEquals(liveDocCount.toLong(), termVectorStatus.totVectors)
            assertTrue(output.toString(StandardCharsets.UTF_8).contains("test: term vectors"))
            assertNull(termVectorStatus.error)

            // confirm doc values testing status
            val docValuesStatus = requireNotNull(segStatus.docValuesStatus)
            assertEquals(3L, docValuesStatus.totalNumericFields)
            assertEquals(1L, docValuesStatus.totalSkippingIndex)
            assertTrue(output.toString(StandardCharsets.UTF_8).contains("test: docvalues"))
            assertNull(docValuesStatus.error)

            // confirm point values testing status
            val pointsStatus = requireNotNull(segStatus.pointsStatus)
            assertEquals(1, pointsStatus.totalValueFields)
            assertEquals(liveDocCount.toLong(), pointsStatus.totalValuePoints)
            assertTrue(output.toString(StandardCharsets.UTF_8).contains("test: points"))
            assertNull(pointsStatus.error)

            // confirm vector testing status
            val vectorValuesStatus = requireNotNull(segStatus.vectorValuesStatus)
            assertEquals((2 * liveDocCount).toLong(), vectorValuesStatus.totalVectorValues)
            assertEquals(2, vectorValuesStatus.totalKnnVectorFields)
            assertTrue(output.toString(StandardCharsets.UTF_8).contains("test: vectors"))
            assertNull(vectorValuesStatus.error)

            // confirm index sort testing status
            assertTrue(output.toString(StandardCharsets.UTF_8).contains("test: index sort"))
            assertNull(requireNotNull(segStatus.indexSortStatus).error)

            // confirm soft deletes testing status
            assertTrue(output.toString(StandardCharsets.UTF_8).contains("test: check soft deletes"))
            assertNull(requireNotNull(segStatus.softDeletesStatus).error)
        }
    }

    @Test
    fun testInvalidThreadCountArgument() {
        val args = arrayOf("-threadCount", "0")
        expectThrows(IllegalArgumentException::class) { CheckIndex.parseOptions(args) }
    }

    private fun randomVector(dim: Int): FloatArray {
        val v = FloatArray(dim)
        for (i in 0 until dim) {
            v[i] = random().nextFloat()
        }
        VectorUtil.l2normalize(v)
        return v
    }

    // Never deletes any commit points!  Do not use in production!!
    private class DeleteNothingIndexDeletionPolicy : IndexDeletionPolicy() {
        @Throws(IOException::class)
        override fun onInit(commits: MutableList<out IndexCommit>) {}

        @Throws(IOException::class)
        override fun onCommit(commits: MutableList<out IndexCommit>) {}

        companion object {
            val INSTANCE: IndexDeletionPolicy = DeleteNothingIndexDeletionPolicy()
        }
    }

    // https://github.com/apache/lucene/issues/7820 -- when the most recent commit point in
    // the index is OK, but older commit points are broken, CheckIndex fails to detect and
    // correct that, while opening an IndexWriter on the index will fail since IndexWriter
    // loads all commit points on init
    @Test
    @Throws(Exception::class)
    fun testPriorBrokenCommitPoint() {
        newMockDirectory().use { dir ->
            // disable this normally useful test infra feature since this test intentionally leaves broken
            // indices:
            dir.checkIndexOnClose = false

            val iwc =
                IndexWriterConfig(MockAnalyzer(random()))
                    .setMergePolicy(NoMergePolicy.INSTANCE)
                    .setIndexDeletionPolicy(DeleteNothingIndexDeletionPolicy.INSTANCE)

            IndexWriter(dir, iwc).use { iw ->
                // create first segment, and commit point referencing only segment 0
                val doc = Document()
                doc.add(StringField("id", "a", Field.Store.NO))
                iw.addDocument(doc)
                iw.commit()

                // NOTE: we are (illegally) relying on precise file naming here -- if Codec or IW's
                // behaviour changes, this may need fixing:
                assertTrue(slowFileExists(dir, "_0.si"))

                // create second segment, and another commit point referencing only segment 1
                doc.add(StringField("id", "a", Field.Store.NO))
                iw.updateDocument(Term("id", "a"), doc)
                iw.commit()

                // NOTE: we are (illegally) relying on precise file naming here -- if Codec or IW's
                // behaviour changes, this may need fixing:
                assertTrue(slowFileExists(dir, "_0.si"))
                assertTrue(slowFileExists(dir, "_1.si"))
            }

            CheckIndex(dir).use { checkers ->
                val checkIndexStatus = checkers.checkIndex()
                assertTrue(checkIndexStatus.clean)
            }

            // now corrupt segment 0, which is referenced by only the first commit point, by removing its
            // .si file (_0.si)
            dir.deleteFile("_0.si")

            CheckIndex(dir).use { checkers ->
                val checkIndexStatus = checkers.checkIndex()
                assertFalse(checkIndexStatus.clean)
            }
        }
    }

    // tests inherited from BaseTestCheckIndex
    @Test
    @Throws(IOException::class)
    override fun testDeletedDocs() = super.testDeletedDocs()

    @Test
    @Throws(IOException::class)
    override fun testChecksumsOnly() = super.testChecksumsOnly()

    @Test
    @Throws(IOException::class)
    override fun testChecksumsOnlyVerbose() = super.testChecksumsOnlyVerbose()

    @Test
    @Throws(IOException::class)
    override fun testObtainsLock() = super.testObtainsLock()
}
