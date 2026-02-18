package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.index.DocValuesSkipIndexType
import org.gnit.lucenekmp.index.DocValuesType
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.RandomPicks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** simple testcases for concrete impl of IndexableFieldType */
class TestFieldType : LuceneTestCase() {

    @Test
    fun testEquals() {
        val ft = FieldType()
        assertEquals(ft, ft)
        assertFalse(ft.equals(null))

        val ft2 = FieldType()
        assertEquals(ft, ft2)
        assertEquals(ft.hashCode(), ft2.hashCode())

        val ft3 = FieldType()
        ft3.setIndexOptions(IndexOptions.DOCS_AND_FREQS)
        assertFalse(ft3.equals(ft))

        val ft4 = FieldType()
        ft4.setDocValuesType(DocValuesType.BINARY)
        assertFalse(ft4.equals(ft))

        val ft5 = FieldType()
        ft5.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS)
        assertFalse(ft5.equals(ft))

        val ft6 = FieldType()
        ft6.setStored(true)
        assertFalse(ft6.equals(ft))

        val ft7 = FieldType()
        ft7.setOmitNorms(true)
        assertFalse(ft7.equals(ft))

        val ft10 = FieldType()
        ft10.setStoreTermVectors(true)
        assertFalse(ft10.equals(ft))

        val ft11 = FieldType()
        ft11.setDimensions(1, Int.SIZE_BYTES)
        assertFalse(ft11.equals(ft))
    }

    @Test
    fun testPointsToString() {
        val ft = FieldType()
        ft.setDimensions(1, Int.SIZE_BYTES)
        assertEquals("pointDimensionCount=1,pointIndexDimensionCount=1,pointNumBytes=4", ft.toString())
    }

    /** FieldType's attribute map should not be modifiable/add after freeze */
    @Test
    fun testAttributeMapFrozen() {
        val ft = FieldType()
        ft.putAttribute("dummy", "d")
        ft.freeze()
        expectThrows<IllegalStateException>(IllegalStateException::class) { ft.putAttribute("dummy", "a") }
    }

    /** FieldType's attribute map can be changed if not frozen */
    @Test
    fun testAttributeMapNotFrozen() {
        val ft = FieldType()
        ft.putAttribute("dummy", "d")
        ft.putAttribute("dummy", "a")
        assertEquals(1, ft.attributes!!.size)
        assertEquals("a", ft.attributes!!["dummy"])
    }

    private fun randomFieldType(): FieldType {
        val ft = FieldType()

        ft.setStored(random().nextBoolean())
        ft.setTokenized(random().nextBoolean())
        ft.setStoreTermVectors(random().nextBoolean())
        ft.setStoreTermVectorOffsets(random().nextBoolean())
        ft.setStoreTermVectorPositions(random().nextBoolean())
        ft.setStoreTermVectorPayloads(random().nextBoolean())
        ft.setOmitNorms(random().nextBoolean())
        ft.setIndexOptions(RandomPicks.randomFrom(random(), IndexOptions.entries.toTypedArray()))
        ft.setDocValuesType(RandomPicks.randomFrom(random(), DocValuesType.entries.toTypedArray()))
        ft.setDocValuesSkipIndexType(RandomPicks.randomFrom(random(), DocValuesSkipIndexType.entries.toTypedArray()))

        val dimensionCountA = 1 + random().nextInt(PointValues.MAX_INDEX_DIMENSIONS)
        val dimensionNumBytesA = 1 + random().nextInt(PointValues.MAX_NUM_BYTES)
        ft.setDimensions(dimensionCountA, dimensionNumBytesA)

        val dimensionCountB = 1 + random().nextInt(PointValues.MAX_DIMENSIONS)
        val indexDimensionCountB = 1 + random().nextInt(minOf(dimensionCountB, PointValues.MAX_INDEX_DIMENSIONS))
        val dimensionNumBytesB = 1 + random().nextInt(PointValues.MAX_NUM_BYTES)
        ft.setDimensions(dimensionCountB, indexDimensionCountB, dimensionNumBytesB)

        ft.setVectorAttributes(
            1 + random().nextInt(100),
            RandomPicks.randomFrom(random(), org.gnit.lucenekmp.index.VectorEncoding.entries.toTypedArray()),
            RandomPicks.randomFrom(random(), org.gnit.lucenekmp.index.VectorSimilarityFunction.entries.toTypedArray())
        )

        return ft
    }

    @Test
    fun testCopyConstructor() {
        val iters = 10
        for (i in 0..<iters) {
            val ft = randomFieldType()
            val ft2 = FieldType(ft)
            assertEquals(ft, ft2)
        }
    }
}
