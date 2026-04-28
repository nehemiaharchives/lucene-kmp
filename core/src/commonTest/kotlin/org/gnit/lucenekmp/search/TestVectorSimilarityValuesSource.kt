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

package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.codecs.KnnVectorsFormat
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.KnnByteVectorField
import org.gnit.lucenekmp.document.KnnFloatVectorField
import org.gnit.lucenekmp.document.SortedDocValuesField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.VectorSimilarityFunction
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.codecs.asserting.AssertingCodec
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOUtils
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TestVectorSimilarityValuesSource : LuceneTestCase() {
    private lateinit var dir: Directory
    private lateinit var analyzer: Analyzer
    private lateinit var reader: IndexReader
    private lateinit var searcher: IndexSearcher

    @BeforeTest
    @Throws(Exception::class)
    fun beforeClass() {
        dir = newDirectory()
        analyzer = MockAnalyzer(random())
        val iwConfig: IndexWriterConfig = newIndexWriterConfig(analyzer)
        iwConfig.setCodec(
            object : AssertingCodec() {
                override fun getKnnVectorsFormatForField(field: String): KnnVectorsFormat {
                    return TestUtil.getDefaultKnnVectorsFormat()
                }
            }
        )
        iwConfig.setMergePolicy(newLogMergePolicy())
        val iw = RandomIndexWriter(random(), dir, iwConfig)

        val document = Document()
        document.add(StringField("id", "1", Field.Store.NO))
        document.add(SortedDocValuesField("id", BytesRef("1")))
        document.add(KnnFloatVectorField("knnFloatField1", floatArrayOf(1f, 2f, 3f)))
        document.add(
            KnnFloatVectorField(
                "knnFloatField2",
                floatArrayOf(2.2f, -3.2f, -3.1f),
                VectorSimilarityFunction.DOT_PRODUCT
            )
        )
        document.add(
            KnnFloatVectorField(
                "knnFloatField3",
                floatArrayOf(4.5f, 10.3f, -7f),
                VectorSimilarityFunction.COSINE
            )
        )
        document.add(
            KnnFloatVectorField(
                "knnFloatField4",
                floatArrayOf(-1.3f, 1.0f, 1.0f),
                VectorSimilarityFunction.MAXIMUM_INNER_PRODUCT
            )
        )
        document.add(KnnFloatVectorField("knnFloatField5", floatArrayOf(-6.7f, -1.0f, -0.9f)))
        document.add(KnnByteVectorField("knnByteField1", byteArrayOf(106, 80, 127)))
        document.add(
            KnnByteVectorField(
                "knnByteField2",
                byteArrayOf(4, 2, 3),
                VectorSimilarityFunction.DOT_PRODUCT
            )
        )
        document.add(
            KnnByteVectorField(
                "knnByteField3",
                byteArrayOf(-121, -64, -1),
                VectorSimilarityFunction.COSINE
            )
        )
        document.add(
            KnnByteVectorField(
                "knnByteField4",
                byteArrayOf(-127, 127, 127),
                VectorSimilarityFunction.MAXIMUM_INNER_PRODUCT
            )
        )
        iw.addDocument(document)

        val document2 = Document()
        document2.add(StringField("id", "2", Field.Store.NO))
        document2.add(SortedDocValuesField("id", BytesRef("2")))
        document2.add(KnnFloatVectorField("knnFloatField1", floatArrayOf(1f, 2f, 3f)))
        document2.add(
            KnnFloatVectorField(
                "knnFloatField2",
                floatArrayOf(-5.2f, 8.7f, 3.1f),
                VectorSimilarityFunction.DOT_PRODUCT
            )
        )
        document2.add(
            KnnFloatVectorField(
                "knnFloatField3",
                floatArrayOf(0.2f, -3.2f, 3.1f),
                VectorSimilarityFunction.COSINE
            )
        )
        document2.add(KnnFloatVectorField("knnFloatField5", floatArrayOf(2f, 13.2f, 9.1f)))
        document2.add(KnnByteVectorField("knnByteField1", byteArrayOf(1, -2, -30)))
        document2.add(
            KnnByteVectorField(
                "knnByteField2",
                byteArrayOf(40, 21, 3),
                VectorSimilarityFunction.DOT_PRODUCT
            )
        )
        document2.add(
            KnnByteVectorField(
                "knnByteField3",
                byteArrayOf(9, 2, 3),
                VectorSimilarityFunction.COSINE
            )
        )
        document2.add(
            KnnByteVectorField(
                "knnByteField4",
                byteArrayOf(14, 29, 31),
                VectorSimilarityFunction.MAXIMUM_INNER_PRODUCT
            )
        )
        iw.addDocument(document2)

        val document3 = Document()
        document3.add(StringField("id", "3", Field.Store.NO))
        document3.add(SortedDocValuesField("id", BytesRef("3")))
        document3.add(KnnFloatVectorField("knnFloatField1", floatArrayOf(1f, 2f, 3f)))
        document3.add(
            KnnFloatVectorField(
                "knnFloatField2",
                floatArrayOf(-8f, 7f, -6f),
                VectorSimilarityFunction.DOT_PRODUCT
            )
        )
        document3.add(KnnFloatVectorField("knnFloatField5", floatArrayOf(5.2f, 3.2f, 3.1f)))
        document3.add(KnnByteVectorField("knnByteField1", byteArrayOf(-128, 0, 127)))
        document3.add(
            KnnByteVectorField(
                "knnByteField2",
                byteArrayOf(-1, -2, -3),
                VectorSimilarityFunction.DOT_PRODUCT
            )
        )
        document3.add(
            KnnByteVectorField(
                "knnByteField3",
                byteArrayOf(4, 2, 3),
                VectorSimilarityFunction.COSINE
            )
        )
        document3.add(
            KnnByteVectorField(
                "knnByteField4",
                byteArrayOf(-4, -2, -128),
                VectorSimilarityFunction.MAXIMUM_INNER_PRODUCT
            )
        )
        document3.add(KnnByteVectorField("knnByteField5", byteArrayOf(-120, -2, 3)))
        iw.addDocument(document3)
        iw.commit()
        iw.forceMerge(1)

        reader = iw.reader
        searcher = newSearcher(reader)
        iw.close()
    }

    @AfterTest
    @Throws(Exception::class)
    fun afterClass() {
        IOUtils.close(reader, dir, analyzer)
    }

    @Test
    @Throws(Exception::class)
    fun testEuclideanSimilarityValuesSource() {
        val floatQueryVector = floatArrayOf(9f, 1f, -10f)

        // Checks the computed similarity score between indexed vectors and query vector
        // using DVS is correct by passing indexed and query vector in #compare
        var dv =
            DoubleValuesSource.similarityToQueryVector(
                searcher.reader.leaves()[0],
                floatQueryVector,
                "knnFloatField1"
            )
        assertTrue(
            dv.advanceExact(0) &&
                dv.doubleValue() ==
                VectorSimilarityFunction.EUCLIDEAN.compare(
                    floatArrayOf(1f, 2f, 3f),
                    floatQueryVector
                ).toDouble()
        )
        assertTrue(
            dv.advanceExact(1) &&
                dv.doubleValue() ==
                VectorSimilarityFunction.EUCLIDEAN.compare(
                    floatArrayOf(1f, 2f, 3f),
                    floatQueryVector
                ).toDouble()
        )
        assertTrue(
            dv.advanceExact(2) &&
                dv.doubleValue() ==
                VectorSimilarityFunction.EUCLIDEAN.compare(
                    floatArrayOf(1f, 2f, 3f),
                    floatQueryVector
                ).toDouble()
        )

        dv =
            DoubleValuesSource.similarityToQueryVector(
                searcher.reader.leaves()[0],
                floatQueryVector,
                "knnFloatField5"
            )
        assertTrue(
            dv.advanceExact(0) &&
                dv.doubleValue() ==
                VectorSimilarityFunction.EUCLIDEAN.compare(
                    floatArrayOf(-6.7f, -1.0f, -0.9f),
                    floatQueryVector
                ).toDouble()
        )
        assertTrue(
            dv.advanceExact(1) &&
                dv.doubleValue() ==
                VectorSimilarityFunction.EUCLIDEAN.compare(
                    floatArrayOf(2f, 13.2f, 9.1f),
                    floatQueryVector
                ).toDouble()
        )
        assertTrue(
            dv.advanceExact(2) &&
                dv.doubleValue() ==
                VectorSimilarityFunction.EUCLIDEAN.compare(
                    floatArrayOf(5.2f, 3.2f, 3.1f),
                    floatQueryVector
                ).toDouble()
        )

        val byteQueryVector = byteArrayOf(-128, 2, 127)

        dv =
            DoubleValuesSource.similarityToQueryVector(
                searcher.reader.leaves()[0],
                byteQueryVector,
                "knnByteField1"
            )
        assertTrue(
            dv.advanceExact(0) &&
                dv.doubleValue() ==
                VectorSimilarityFunction.EUCLIDEAN.compare(
                    byteArrayOf(106, 80, 127),
                    byteQueryVector
                ).toDouble()
        )
        assertTrue(
            dv.advanceExact(1) &&
                dv.doubleValue() ==
                VectorSimilarityFunction.EUCLIDEAN.compare(
                    byteArrayOf(1, -2, -30),
                    byteQueryVector
                ).toDouble()
        )
        assertTrue(
            dv.advanceExact(2) &&
                dv.doubleValue() ==
                VectorSimilarityFunction.EUCLIDEAN.compare(
                    byteArrayOf(-128, 0, 127),
                    byteQueryVector
                ).toDouble()
        )

        dv =
            DoubleValuesSource.similarityToQueryVector(
                searcher.reader.leaves()[0],
                byteQueryVector,
                "knnByteField5"
            )
        assertFalse(dv.advanceExact(0))
        assertFalse(dv.advanceExact(1))
        assertTrue(
            dv.advanceExact(2) &&
                dv.doubleValue() ==
                VectorSimilarityFunction.EUCLIDEAN.compare(
                    byteArrayOf(-120, -2, 3),
                    byteQueryVector
                ).toDouble()
        )
    }

    @Test
    @Throws(Exception::class)
    fun testDotSimilarityValuesSource() {
        val floatQueryVector = floatArrayOf(10f, 1f, -8.5f)

        // Checks the computed similarity score between indexed vectors and query vector
        // using DVS is correct by passing indexed and query vector in #compare
        var dv =
            DoubleValuesSource.similarityToQueryVector(
                searcher.reader.leaves()[0],
                floatQueryVector,
                "knnFloatField2"
            )
        assertTrue(
            dv.advanceExact(0) &&
                dv.doubleValue() ==
                VectorSimilarityFunction.DOT_PRODUCT.compare(
                    floatArrayOf(2.2f, -3.2f, -3.1f),
                    floatQueryVector
                ).toDouble()
        )
        assertTrue(
            dv.advanceExact(1) &&
                dv.doubleValue() ==
                VectorSimilarityFunction.DOT_PRODUCT.compare(
                    floatArrayOf(-5.2f, 8.7f, 3.1f),
                    floatQueryVector
                ).toDouble()
        )
        assertTrue(
            dv.advanceExact(2) &&
                dv.doubleValue() ==
                VectorSimilarityFunction.DOT_PRODUCT.compare(
                    floatArrayOf(-8f, 7f, -6f),
                    floatQueryVector
                ).toDouble()
        )

        val byteQueryVector = byteArrayOf(-128, 2, 127)

        dv =
            DoubleValuesSource.similarityToQueryVector(
                searcher.reader.leaves()[0],
                byteQueryVector,
                "knnByteField2"
            )
        assertTrue(
            dv.advanceExact(0) &&
                dv.doubleValue() ==
                VectorSimilarityFunction.DOT_PRODUCT.compare(
                    byteArrayOf(4, 2, 3),
                    byteQueryVector
                ).toDouble()
        )
        assertTrue(
            dv.advanceExact(1) &&
                dv.doubleValue() ==
                VectorSimilarityFunction.DOT_PRODUCT.compare(
                    byteArrayOf(40, 21, 3),
                    byteQueryVector
                ).toDouble()
        )
        assertTrue(
            dv.advanceExact(2) &&
                dv.doubleValue() ==
                VectorSimilarityFunction.DOT_PRODUCT.compare(
                    byteArrayOf(-1, -2, -3),
                    byteQueryVector
                ).toDouble()
        )
    }

    @Test
    @Throws(Exception::class)
    fun testCosineSimilarityValuesSource() {
        val floatQueryVector = floatArrayOf(0.6f, -1.6f, 38.0f)

        // Checks the computed similarity score between indexed vectors and query vector
        // using DVS is correct by passing indexed and query vector in #compare
        var dv =
            DoubleValuesSource.similarityToQueryVector(
                searcher.reader.leaves()[0],
                floatQueryVector,
                "knnFloatField3"
            )
        assertTrue(
            dv.advanceExact(0) &&
                dv.doubleValue() ==
                VectorSimilarityFunction.COSINE.compare(
                    floatArrayOf(4.5f, 10.3f, -7f),
                    floatQueryVector
                ).toDouble()
        )
        assertTrue(
            dv.advanceExact(1) &&
                dv.doubleValue() ==
                VectorSimilarityFunction.COSINE.compare(
                    floatArrayOf(0.2f, -3.2f, 3.1f),
                    floatQueryVector
                ).toDouble()
        )
        assertFalse(dv.advanceExact(2))

        val byteQueryVector = byteArrayOf(-10, 8, 0)

        dv =
            DoubleValuesSource.similarityToQueryVector(
                searcher.reader.leaves()[0],
                byteQueryVector,
                "knnByteField3"
            )
        assertTrue(
            dv.advanceExact(0) &&
                dv.doubleValue() ==
                VectorSimilarityFunction.COSINE.compare(
                    byteArrayOf(-121, -64, -1),
                    byteQueryVector
                ).toDouble()
        )
        assertTrue(
            dv.advanceExact(1) &&
                dv.doubleValue() ==
                VectorSimilarityFunction.COSINE.compare(
                    byteArrayOf(9, 2, 3),
                    byteQueryVector
                ).toDouble()
        )
        assertTrue(
            dv.advanceExact(2) &&
                dv.doubleValue() ==
                VectorSimilarityFunction.COSINE.compare(
                    byteArrayOf(4, 2, 3),
                    byteQueryVector
                ).toDouble()
        )
    }

    @Test
    @Throws(Exception::class)
    fun testMaximumProductSimilarityValuesSource() {
        val floatQueryVector = floatArrayOf(1f, -6f, -10f)

        // Checks the computed similarity score between indexed vectors and query vector
        // using DVS is correct by passing indexed and query vector in #compare
        var dv =
            DoubleValuesSource.similarityToQueryVector(
                searcher.reader.leaves()[0],
                floatQueryVector,
                "knnFloatField4"
            )
        assertTrue(dv.advanceExact(0))
        assertEquals(
            VectorSimilarityFunction.MAXIMUM_INNER_PRODUCT.compare(
                floatArrayOf(-1.3f, 1.0f, 1.0f),
                floatQueryVector
            ).toDouble(),
            dv.doubleValue(),
            0.0001
        )
        assertFalse(dv.advanceExact(1))
        assertFalse(dv.advanceExact(2))

        val byteQueryVector = byteArrayOf(-127, 127, 127)

        dv =
            DoubleValuesSource.similarityToQueryVector(
                searcher.reader.leaves()[0],
                byteQueryVector,
                "knnByteField4"
            )
        assertTrue(dv.advanceExact(0))
        assertEquals(
            VectorSimilarityFunction.MAXIMUM_INNER_PRODUCT.compare(
                byteArrayOf(-127, 127, 127),
                byteQueryVector
            ).toDouble(),
            dv.doubleValue(),
            0.0001
        )
        assertTrue(dv.advanceExact(1))
        assertEquals(
            VectorSimilarityFunction.MAXIMUM_INNER_PRODUCT.compare(
                byteArrayOf(14, 29, 31),
                byteQueryVector
            ).toDouble(),
            dv.doubleValue(),
            0.0001
        )
        assertTrue(dv.advanceExact(2))
        assertEquals(
            VectorSimilarityFunction.MAXIMUM_INNER_PRODUCT.compare(
                byteArrayOf(-4, -2, -128),
                byteQueryVector
            ).toDouble(),
            dv.doubleValue(),
            0.0001
        )
    }

    @Test
    @Throws(Exception::class)
    fun testFailuresWithSimilarityValuesSource() {
        val floatQueryVector = floatArrayOf(1.1f, 2.2f, 3.3f)
        val byteQueryVector = byteArrayOf(-10, 20, 30)

        assertFailsWith<IllegalArgumentException> {
            DoubleValuesSource.similarityToQueryVector(
                searcher.reader.leaves()[0],
                floatQueryVector,
                "knnByteField1"
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DoubleValuesSource.similarityToQueryVector(
                searcher.reader.leaves()[0],
                byteQueryVector,
                "knnFloatField1"
            )
        }

        val dv =
            DoubleValuesSource.similarityToQueryVector(
                searcher.reader.leaves()[0],
                floatQueryVector,
                "knnFloatField1"
            )
        assertTrue(dv.advanceExact(0))
        assertEquals(
            VectorSimilarityFunction.EUCLIDEAN.compare(
                floatArrayOf(1f, 2f, 3f),
                floatQueryVector
            ).toDouble(),
            dv.doubleValue(),
            0.0
        )
        assertNotEquals(
            VectorSimilarityFunction.DOT_PRODUCT.compare(
                floatArrayOf(1f, 2f, 3f),
                floatQueryVector
            ).toDouble(),
            dv.doubleValue()
        )
        assertNotEquals(
            VectorSimilarityFunction.COSINE.compare(
                floatArrayOf(1f, 2f, 3f),
                floatQueryVector
            ).toDouble(),
            dv.doubleValue()
        )
        assertNotEquals(
            VectorSimilarityFunction.MAXIMUM_INNER_PRODUCT.compare(
                floatArrayOf(1f, 2f, 3f),
                floatQueryVector
            ).toDouble(),
            dv.doubleValue()
        )
    }
}

